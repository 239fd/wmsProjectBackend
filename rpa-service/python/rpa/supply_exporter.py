from __future__ import annotations

import json
import logging
import uuid
from datetime import datetime
from decimal import Decimal
from pathlib import Path
from typing import Iterable

from .config import ONEC
from .models import OrderItem, PurchaseOrder


log = logging.getLogger(__name__)

# 1С "Текущее состояние" → supply_status enum.
_STATUS_MAP = {
    "не согласован":                 "PLANNED",
    "ожидается согласование":        "PLANNED",
    "согласован":                    "PLANNED",
    "ожидается аванс":               "PLANNED",
    "ожидается аванс (до подтверждения)": "PLANNED",
    "ожидается поступление":         "IN_PROGRESS",
    "готов к поступлению":           "IN_PROGRESS",
    "ожидается оплата":              "IN_PROGRESS",
    "ожидается оплата (после поступления)": "IN_PROGRESS",
    "закрыт":                        "ACCEPTED",
    "отменён":                       "CANCELLED",
    "отменен":                       "CANCELLED",
}


def export_supplies(
    orders: Iterable[PurchaseOrder],
    output_path: Path,
    *,
    records_found: int | None = None,
) -> Path:
    """Dump PurchaseOrders to supplies.json (supply_full schema)."""
    orders = list(orders)
    payload = {
        "supplies": [_order_to_supply(o) for o in orders],
        "extraction_log": {
            "log_id": None,
            "source": "1C-UT-utdemo",
            "extracted_at": _now(),
            "records_found": records_found if records_found is not None else len(orders),
            "records_new": len(orders),
            "success": True,
            "error_message": None,
        },
        "erp_connection": {
            "connection_id": None,
            "organization_id": None,
            "aggregator": "1C",
            "name": "1С:УТ 11.2 (utdemo)",
            "username": "",
            "base_path": ONEC.executable,
            "section_name": "Закупки",
            "journal_name": "Заказы поставщикам",
            "driver_url": None,
            "is_default": True,
            "created_at": None,
        },
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    log.info("Wrote supplies JSON: %s  (%d supply(es))", output_path, len(orders))
    return output_path


def _order_to_supply(order: PurchaseOrder) -> dict:
    supply_id = str(uuid.uuid4())
    return {
        "supply_id":         supply_id,
        "organization_id":   None,
        "supplier_id":       None,
        "warehouse_id":      None,
        "status":            _map_status(order.status),
        "expected_date":     order.expected_date.isoformat() if order.expected_date else None,
        "actual_date":       None,
        "total_items":       len(order.items),
        "notes":             "",
        "created_by":        None,
        "created_at":        _now(),
        "updated_at":        _now(),

        "external_id":       order.number or "",
        "external_source":   "1C-UT-utdemo",
        "currency":          order.currency or "",
        "total_amount":      _decimal_str(order.total_amount),
        "payment_pct":       _decimal_str(order.payment_pct),
        "receipt_pct":       _decimal_str(order.receipt_pct),
        "debt_pct":          _decimal_str(order.debt_pct),
        "operation":         order.operation or "",

        "supplier": {
            "supplier_id":     None,
            "organization_id": None,
            "name":            order.supplier or "",
            "full_name":       "",
            "unp":             "",
            "inn":             "",
            "kpp":             "",
            "contact_person":  "",
            "phone":           "",
            "email":           "",
            "address":         "",
            "is_active":       True,
            "created_at":      None,
            "updated_at":      None,
        },
        "organization": {
            "organization_id":  None,
            "name":             order.organization or "",
            "full_name":        "",
            "unp":              "",
            "inn":              "",
            "kpp":              "",
            "address":          "",
            "phone":            "",
            "email":            "",
            "director":         "",
            "chief_accountant": "",
        },
        "warehouse": {
            "warehouse_id": None,
            "name":         order.warehouse or "",
            "address":      "",
            "okpo":         "",
        },
        "transport": {
            "vehicle_make":           "",
            "vehicle_plate":          "",
            "trailer_make":           "",
            "trailer_plate":          "",
            "driver_full_name":       "",
            "carrier_name":           "",
            "carrier_address":        "",
            "carrier_unp":            "",
            "waybill_number":         "",
            "loading_point_name":     "",
            "loading_point_address":  "",
            "unloading_point_name":   "",
            "unloading_point_address":"",
            "departure_date":         None,
            "arrival_date":           None,
            "departure_time":         "",
            "arrival_time":           "",
            "distance_km":            None,
            "transport_cost":         None,
            "seal_number":            "",
            "powered_by_doc_number":  "",
            "powered_by_doc_date":    None,
        },
        "commission": {
            "chairman":               None,
            "members":                [],
            "responsible_employee":   None,
            "shipper_representative": None,
            "receiver_representative":None,
        },
        "international": {
            "is_international":            False,
            "country_of_export":           "",
            "country_of_manufacture":      "",
            "country_of_destination":      "",
            "incoterms":                   "",
            "international_waybill_number":"",
            "carrier_remarks":             "",
        },

        "supply_items": [_item_to_dict(item, supply_id) for item in order.items],

        "receipt_session":   None,
        "inventory_session": None,
        "discrepancies":     [],
        "writeoff":          None,
        "revaluation":       None,
        "generated_documents": [],
    }


def _item_to_dict(item: OrderItem, supply_id: str) -> dict:
    return {
        "item_id":      str(uuid.uuid4()),
        "supply_id":    supply_id,
        "row_number":   item.row_number,
        "expected_qty": _decimal_str(item.quantity),
        "actual_qty":   None,
        "unit_price":   _decimal_str(item.price),
        "total_amount": _decimal_str(item.amount),
        "vat_rate":     None,
        "vat_amount":   None,
        "notes":        "",
        "product": {
            "product_id":      None,
            "name":            item.nomenclature or "",
            "sku":             "",
            "barcode":         "",
            "category":        "",
            "description":     "",
            "unit_of_measure": item.unit or "",
            "weight_kg":       None,
            "volume_m3":       None,
            "price":           _decimal_str(item.price),
            "abc_class":       "C",
            "organization_id": None,
            "manufacturer":    "",
        },
        "batch": None,
    }


def _map_status(raw: str) -> str:
    key = (raw or "").strip().lower()
    for needle, value in _STATUS_MAP.items():
        if needle in key:
            return value
    return "PLANNED"


def _decimal_str(value) -> str | None:
    if value is None:
        return None
    if isinstance(value, Decimal):
        return format(value, "f")
    return str(value)


def _now() -> str:
    return datetime.now().isoformat(timespec="seconds")


def export_sales(
    orders: Iterable[PurchaseOrder],
    output_path: Path,
    *,
    records_found: int | None = None,
) -> Path:
    """Dump sales orders (Заказы клиентов) to sales.json.
    Reuses PurchaseOrder; `.supplier` holds the customer name."""
    orders = list(orders)
    payload = {
        "shipments": [_order_to_shipment(o) for o in orders],
        "extraction_log": {
            "log_id": None,
            "source": "1C-UT-utdemo",
            "extracted_at": _now(),
            "records_found": records_found if records_found is not None else len(orders),
            "records_new": len(orders),
            "success": True,
            "error_message": None,
        },
        "erp_connection": {
            "connection_id": None,
            "organization_id": None,
            "aggregator": "1C",
            "name": "1С:УТ 11.2 (utdemo)",
            "username": "",
            "base_path": ONEC.executable,
            "section_name": "Продажи",
            "journal_name": "Заказы клиентов",
            "driver_url": None,
            "is_default": True,
            "created_at": None,
        },
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    log.info("Wrote sales JSON: %s  (%d shipment(s))", output_path, len(orders))
    return output_path


def _order_to_shipment(order: PurchaseOrder) -> dict:
    shipment_id = str(uuid.uuid4())
    return {
        "shipment_id":     shipment_id,
        "external_id":     order.number or "",
        "external_source": "1C-UT-utdemo",
        "date":            order.date.isoformat() if order.date else None,
        "expected_date":   order.expected_date.isoformat() if order.expected_date else None,
        "status":          _map_status(order.status),
        "status_raw":      order.status or "",
        "currency":        order.currency or "",
        "total_amount":    _decimal_str(order.total_amount),
        "payment_pct":     _decimal_str(order.payment_pct),
        "shipment_pct":    _decimal_str(order.receipt_pct),  # «% отгрузки»
        "debt_pct":        _decimal_str(order.debt_pct),
        "operation":       order.operation or "",
        "customer": {
            "customer_id": None,
            "name":        order.supplier or "",
            "inn":         "",
            "address":     "",
            "phone":       "",
            "email":       "",
        },
        "organization": {
            "organization_id": None,
            "name":            order.organization or "",
        },
        "shipment_items": [_item_to_shipment_dict(item, shipment_id) for item in order.items],
    }


def _item_to_shipment_dict(item: OrderItem, shipment_id: str) -> dict:
    return {
        "item_id":      str(uuid.uuid4()),
        "shipment_id":  shipment_id,
        "row_number":   item.row_number,
        "name":         item.nomenclature or "",
        "unit":         item.unit or "",
        "qty":          _decimal_str(item.quantity),
        "unit_price":   _decimal_str(item.price),
        "total_amount": _decimal_str(item.amount),
    }
