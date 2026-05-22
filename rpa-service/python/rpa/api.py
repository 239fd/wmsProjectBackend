"""FastAPI HTTP-обёртка над excel_filler / word_filler / onec_parser.

Запускается на Windows-хосте с установленными MS Office + 1С UT 11.2:

    uvicorn rpa.api:app --host 0.0.0.0 --port 8060

Эндпоинты:
    GET  /health                  → статус
    POST /fill/{doc_type}         → генерация документа, возвращает bytes
    POST /parse/supplies          → парсинг Заказов поставщикам из 1С → JSON
    POST /parse/sales             → парсинг Заказов клиентов из 1С → JSON

Принимает payload в WMS-схеме (Map<String,Object>); адаптер `wms_payload_to_order`
маппит её на наш `PurchaseOrder` и делегирует существующим fill_excel/fill_word.
"""
from __future__ import annotations

import logging
import os
import re
import shutil
import tempfile
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any
from urllib.parse import quote

from fastapi import FastAPI, HTTPException, Response
from fastapi.responses import JSONResponse

from .excel_filler import fill_excel
from .models import (
    Counterparty,
    DeliveryContext,
    OrderItem,
    PurchaseOrder,
    Signatories,
    Transport,
)
from .templates_spec import EXCEL_SPECS, WORD_SPECS, ExcelSpec, WordSpec
from .word_filler import fill_word


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)-5s [%(name)s] %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("rpa.api")


# WMS document type → наш output_suffix. Для типов с layout/language вариантами
# выбор делается в `_resolve_spec` по подсказкам из payload.
_TYPE_MAP: dict[str, str] = {
    "receipt-order":         "ПриходныйОрдер",
    "inventory-report":      "ИнвентаризационнаяОпись",
    "revaluation-act":       "АктПереоценки",
    "write-off-act":         "Списание",
    "invoice":               "Инвойс",
    "discrepancy-act":       "АктРасхождения",
    # type'ы с layout / language см. _resolve_spec
}

_UNSUPPORTED: set[str] = {"picking-list", "placement-list", "release-order", "shipment-order"}


def _resolve_spec(doc_type: str, payload: dict[str, Any]) -> ExcelSpec | WordSpec:
    """Найти ExcelSpec / WordSpec по WMS-типу документа."""
    if doc_type in _UNSUPPORTED:
        raise HTTPException(status_code=501, detail=f"document type {doc_type!r} not implemented in Python RPA")

    if doc_type == "transport-note":
        layout = (payload.get("layout") or "horizontal").lower()
        suffix = "ТН-вертикаль" if layout == "vertical" else "ТН-горизонт"
    elif doc_type == "waybill":
        layout = (payload.get("layout") or "horizontal").lower()
        suffix = "ТТН-вертикаль" if layout == "vertical" else "ТТН-горизонт"
    elif doc_type == "cmr":
        lang = (payload.get("language") or "ru").lower()
        suffix = {"en": "CMR-EN", "ru-only": "CMR-RU", "ru": "CMR"}.get(lang, "CMR")
    elif doc_type == "receipt-act":
        discrepancies = payload.get("discrepancies") or []
        suffix = "АктРасхождения" if discrepancies else "АктПриемки"
    else:
        suffix = _TYPE_MAP.get(doc_type)
        if not suffix:
            raise HTTPException(status_code=400, detail=f"unknown document type: {doc_type!r}")

    for spec in EXCEL_SPECS:
        if spec.output_suffix == suffix:
            return spec
    for spec in WORD_SPECS:
        if spec.output_suffix == suffix:
            return spec
    raise HTTPException(status_code=500, detail=f"spec for suffix {suffix!r} not registered")


def _dec(v: Any, default: str = "0") -> Decimal:
    if v is None or v == "":
        return Decimal(default)
    try:
        return Decimal(str(v))
    except (InvalidOperation, ValueError):
        return Decimal(default)


def _parse_iso_date(v: Any) -> date | None:
    if not v:
        return None
    s = str(v).strip()
    for fmt in ("%Y-%m-%d", "%d.%m.%Y"):
        try:
            return datetime.strptime(s, fmt).date()
        except ValueError:
            continue
    return None


def _first(*candidates: Any) -> str:
    """Первое непустое значение."""
    for c in candidates:
        if c not in (None, ""):
            return str(c)
    return ""


def wms_payload_to_order(payload: dict[str, Any]) -> PurchaseOrder:
    """Адаптер: WMS-payload (плоский Map с camelCase-ключами) → наш PurchaseOrder.

    Поля собираются с фолбэками между разными типами документов (для invoice —
    sellerName / buyerName; для transport-note / cmr — shipperName / consigneeName;
    для receipt-order — supplierName / organizationName).
    """
    items: list[OrderItem] = []
    raw_items = payload.get("items") or []
    for i, it in enumerate(raw_items, 1):
        if not isinstance(it, dict):
            continue
        qty = _dec(it.get("quantity"))
        price = _dec(it.get("price") or it.get("unitPrice"))
        amount = _dec(it.get("amount") or it.get("totalPrice")) or (qty * price).quantize(Decimal("0.01"))
        items.append(OrderItem(
            row_number=i,
            nomenclature=str(it.get("productName") or it.get("name") or ""),
            unit=str(it.get("unit") or "шт."),
            quantity=qty,
            price=price,
            amount=amount,
        ))

    supplier = Counterparty(
        name=_first(payload.get("supplierName"), payload.get("shipperName"), payload.get("sellerName")),
        inn=_first(payload.get("supplierInn"), payload.get("shipperInn"), payload.get("sellerInn")),
        address=_first(payload.get("supplierAddress"), payload.get("shipperAddress"), payload.get("sellerAddress")),
        phone=_first(payload.get("supplierPhone"), payload.get("shipperPhone")),
        email=_first(payload.get("supplierEmail")),
    )
    organization = Counterparty(
        name=_first(payload.get("organizationName"), payload.get("consigneeName"), payload.get("buyerName")),
        inn=_first(payload.get("inn"), payload.get("consigneeInn"), payload.get("buyerInn")),
        address=_first(payload.get("warehouseAddress"), payload.get("consigneeAddress"), payload.get("buyerAddress")),
        phone=_first(payload.get("organizationPhone"), payload.get("consigneePhone")),
        email=_first(payload.get("organizationEmail")),
    )
    carrier = Counterparty(
        name=_first(payload.get("carrierName")),
        inn=_first(payload.get("carrierInn"), payload.get("carrierUnp")),
        address=_first(payload.get("carrierAddress")),
        phone=_first(payload.get("carrierPhone")),
    )
    transport = Transport(
        vehicle_make=_first(payload.get("vehicleMake")),
        vehicle_plate=_first(payload.get("vehicleNumber"), payload.get("vehiclePlate")),
        trailer_make=_first(payload.get("trailerMake")),
        trailer_plate=_first(payload.get("trailerNumber"), payload.get("trailerPlate")),
        driver_full_name=_first(payload.get("driverName"), payload.get("driverFullName")),
        waybill_number=_first(payload.get("waybillNumber"), payload.get("invoiceNumber")),
        seal_number=_first(payload.get("sealNumber")),
    )
    members = payload.get("commissionMembers") or []
    sig = Signatories(
        sender=_first(payload.get("shipperSignedBy"), payload.get("releasedBy")),
        carrier=_first(payload.get("carrierSignedBy")),
        receiver=_first(payload.get("consigneeSignedBy"), payload.get("acceptedBy")),
        released_by=_first(payload.get("releasedBy")),
        director=_first(payload.get("directorName"), payload.get("approvedBy")),
        director_title=_first(payload.get("directorTitle"), payload.get("directorPosition")),
        commission_chairman=_first(payload.get("chairmanName")),
        commission_members=tuple(str(m) for m in members if m),
    )
    delivery = DeliveryContext(
        loading_place=_first(payload.get("placeOfLoading"), payload.get("loadingPoint")),
        delivery_place=_first(payload.get("placeOfDelivery"), payload.get("unloadingPoint")),
        country_of_export=_first(payload.get("shipperCountry"), payload.get("countryOfExport")),
        country_of_manufacture=_first(payload.get("countryOfManufacture")),
        country_of_destination=_first(payload.get("consigneeCountry"), payload.get("countryOfDestination")),
        next_carrier=_first(payload.get("nextCarrier")),
        carrier_remarks=_first(payload.get("carrierRemarks"), payload.get("notes")),
        payment_terms=_first(payload.get("paymentTerms"), payload.get("paymentInstructions")),
        special_terms=_first(payload.get("specialInstructions"), payload.get("specialTerms")),
        shipper_instructions=_first(payload.get("shipperInstructions")),
        transport_rate=_first(payload.get("transportRate")),
        transport_total=_first(payload.get("transportTotal"), payload.get("totalAmount")),
        proxy_number=_first(payload.get("proxyNumber")),
        proxy_date=_first(payload.get("proxyDate")),
        proxy_issued_by=_first(payload.get("proxyIssuedBy")),
        accompanying_docs=_first(payload.get("accompanyingDocs")),
        redirection=_first(payload.get("redirection")),
        structural_unit=_first(payload.get("structuralUnit")),
        operation_code=_first(payload.get("operationCode")),
        insurance_company=_first(payload.get("insuranceCompany")),
        account_corr=_first(payload.get("accountCorr")),
        account_code=_first(payload.get("accountCode")),
        doc_accompanying=_first(payload.get("docAccompanying"), payload.get("waybillReference")),
        doc_payment=_first(payload.get("docPayment")),
        sklad_card_no=_first(payload.get("skladCardNo")),
    )
    total = _dec(payload.get("totalAmount")) or sum((it.amount for it in items), Decimal("0"))
    order_date = _parse_iso_date(payload.get("documentDate")) or date.today()

    return PurchaseOrder(
        number=str(payload.get("documentNumber") or ""),
        date=order_date,
        supplier=supplier.name,
        organization=organization.name,
        warehouse=str(payload.get("warehouseName") or ""),
        status=str(payload.get("status") or ""),
        total_amount=total,
        items=items,
        currency=str(payload.get("currency") or "BYN"),
        operation=str(payload.get("operation") or ""),
        supplier_details=supplier,
        organization_details=organization,
        carrier=carrier,
        transport=transport,
        signatories=sig,
        delivery=delivery,
    )


app = FastAPI(title="RPA Service", version="1.0.0",
              description="Python RPA: 1С парсер (pywinauto) + Office-генератор (win32com)")


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "templates_count": len(EXCEL_SPECS) + len(WORD_SPECS),
        "supported_doc_types": sorted(
            set(_TYPE_MAP) | {"transport-note", "waybill", "cmr", "receipt-act"}),
    }


@app.post("/fill/{doc_type}")
def fill(doc_type: str, payload: dict[str, Any]) -> Response:
    """Сгенерировать документ. Возвращает bytes файла (.xlsx или .docx) с
    Content-Disposition + Content-Type соответствующего MIME."""
    spec = _resolve_spec(doc_type, payload)
    order = wms_payload_to_order(payload)
    log.info("fill[%s]: order=%s, items=%d, suffix=%s",
             doc_type, order.number or "—", len(order.items), spec.output_suffix)

    tmp_dir = Path(tempfile.mkdtemp(prefix=f"rpa-{doc_type}-"))
    try:
        if isinstance(spec, ExcelSpec):
            out = fill_excel(spec, order, tmp_dir, visible=True)
            mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        else:
            out = fill_word(spec, order, tmp_dir, visible=True)
            mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        data = out.read_bytes()
        log.info("fill[%s]: %d bytes → %s", doc_type, len(data), out.name)
        ascii_name = re.sub(r"[^\x20-\x7E]+", "_", out.name) or "document.xlsx"
        encoded_name = quote(out.name, safe="")
        return Response(
            content=data,
            media_type=mime,
            headers={
                "Content-Disposition": (
                    f'attachment; filename="{ascii_name}"; '
                    f"filename*=UTF-8''{encoded_name}"
                ),
                "X-Rpa-Channel": "python",
            },
        )
    finally:
        shutil.rmtree(tmp_dir, ignore_errors=True)


@app.post("/parse/supplies")
def parse_supplies() -> JSONResponse:
    """Распарсить Заказы поставщикам из 1С. Возвращает supplies.json (наша схема)."""
    from .onec_parser import fetch_orders
    from . import supply_exporter

    orders = fetch_orders()
    log.info("parse/supplies: %d order(s) extracted", len(orders))
    tmp_dir = Path(tempfile.mkdtemp(prefix="rpa-parse-supplies-"))
    try:
        json_path = tmp_dir / "supplies.json"
        supply_exporter.export_supplies(orders, json_path)
        payload = json_path.read_text(encoding="utf-8")
        return JSONResponse(content=__import__("json").loads(payload),
                            headers={"X-Rpa-Channel": "python", "X-Records-Count": str(len(orders))})
    finally:
        shutil.rmtree(tmp_dir, ignore_errors=True)


@app.post("/parse/sales")
def parse_sales() -> JSONResponse:
    """Распарсить Заказы клиентов из 1С (исключая закрытые). Возвращает sales.json."""
    from .onec_parser import fetch_sales_orders
    from . import supply_exporter

    orders = fetch_sales_orders()
    log.info("parse/sales: %d order(s) extracted", len(orders))
    tmp_dir = Path(tempfile.mkdtemp(prefix="rpa-parse-sales-"))
    try:
        json_path = tmp_dir / "sales.json"
        supply_exporter.export_sales(orders, json_path)
        payload = json_path.read_text(encoding="utf-8")
        return JSONResponse(content=__import__("json").loads(payload),
                            headers={"X-Rpa-Channel": "python", "X-Records-Count": str(len(orders))})
    finally:
        shutil.rmtree(tmp_dir, ignore_errors=True)
