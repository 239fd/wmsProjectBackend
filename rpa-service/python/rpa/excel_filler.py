from __future__ import annotations

import logging
import re
import shutil
import tempfile
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

import pythoncom
import win32com.client as win32
from num2words import num2words

from .config import OFFICE
from .models import (
    Counterparty,
    DeliveryContext,
    PurchaseOrder,
    Signatories,
    Transport,
)
from .templates_spec import ExcelSpec


_DEFAULT_VAT_RATE = Decimal("20")

_NARROW_HEADER_FIELDS: dict[str, int] = {
    "handed_over_by":    9,
    "carrier_signer":    9,
    "proxy_number_date": 9,
    "proxy_issued_by":   9,
}

_MONTH_RU_GEN = (
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)

_XL_ALIGN_LEFT  = -4131
_XL_ALIGN_RIGHT = -4152

XL_OPENXML_WORKBOOK = 51
XL_SHIFT_DOWN = -4121
XL_FORMAT_FROM_LEFT_OR_ABOVE = 0

log = logging.getLogger(__name__)


def _shrink_font(ws, address: str, pt: int) -> None:
    try:
        cell = ws.Range(address)
        if cell.MergeCells:
            cell = cell.MergeArea.Cells(1, 1)
        cell.Font.Size = pt
    except Exception:
        pass


def _shift_row(address: str, offset: int) -> str:
    """`"J23" + 28` → `"J51"`. For addressing cells whose row moves after
    in-place Rows.Insert expansion."""
    m = re.match(r"([A-Za-z]+)(\d+)$", address)
    if not m:
        return address
    return f"{m.group(1)}{int(m.group(2)) + offset}"


def _normalize_row_style(ws, columns_map: dict[str, str], row: int,
                         align_right: bool = False) -> None:
    """Drop bold + align item cells. First column LEFT by default, rest keep
    template defaults; align_right=True forces RIGHT on every column."""
    cols = list(columns_map.values())
    if not cols:
        return
    name_col = cols[0]
    for col_letter in cols:
        addr = f"{col_letter}{row}"
        try:
            cell = ws.Range(addr)
            if cell.MergeCells:
                cell = cell.MergeArea.Cells(1, 1)
            cell.Font.Bold = False
            if align_right:
                cell.HorizontalAlignment = _XL_ALIGN_RIGHT
            elif col_letter == name_col:
                cell.HorizontalAlignment = _XL_ALIGN_LEFT
        except Exception:
            pass


def _ru_plural(n: int, one: str, few: str, many: str) -> str:
    """1 рубль / 2 рубля / 5 рублей."""
    n = abs(n)
    if 11 <= n % 100 <= 14:
        return many
    last = n % 10
    if last == 1:
        return one
    if 2 <= last <= 4:
        return few
    return many


def _amount_words_ru(amount: Decimal) -> str:
    """`120.55` → `Сто двадцать рублей пятьдесят пять копеек`."""
    if amount is None:
        amount = Decimal("0")
    rub = int(amount)
    kop = int((Decimal(amount) - Decimal(rub)) * 100)
    rub_words = num2words(rub, lang="ru").capitalize()
    rub_unit = _ru_plural(rub, "рубль", "рубля", "рублей")
    kop_words = num2words(kop, lang="ru") if kop != 0 else "ноль"
    kop_unit = _ru_plural(kop, "копейка", "копейки", "копеек")
    return f"{rub_words} {rub_unit} {kop_words} {kop_unit}"


def _amount_digits_ru(amount: Decimal) -> str:
    """`5997.68` → `5997 руб. 68 коп.`"""
    if amount is None:
        amount = Decimal("0")
    rub = int(amount)
    kop = int((Decimal(amount) - Decimal(rub)) * 100)
    return f"{rub} руб. {kop:02d} коп."


def _weight_words_ru(kg: Decimal) -> str:
    if kg is None:
        kg = Decimal("0")
    n = int(kg)
    words = num2words(n, lang="ru").capitalize()
    unit = _ru_plural(n, "килограмм", "килограмма", "килограммов")
    return f"{words} {unit}"


def _count_words_ru(count) -> str:
    n = int(count or 0)
    words = num2words(n, lang="ru").capitalize()
    unit = _ru_plural(n, "место", "места", "мест")
    return f"{words} {unit}"


def _vat_split(amount_with_vat: Decimal, rate_pct: Decimal = _DEFAULT_VAT_RATE) -> tuple[Decimal, Decimal]:
    """VAT-inclusive amount + rate% → (net, vat)."""
    if amount_with_vat is None:
        return Decimal("0"), Decimal("0")
    rate_frac = Decimal(rate_pct) / Decimal(100)
    vat = (amount_with_vat * rate_frac / (Decimal(1) + rate_frac)).quantize(Decimal("0.01"), ROUND_HALF_UP)
    net = (amount_with_vat - vat).quantize(Decimal("0.01"), ROUND_HALF_UP)
    return net, vat


def fill_excel(spec: ExcelSpec, order: PurchaseOrder, output_dir: Path,
               *, visible: bool = OFFICE.keep_office_visible) -> Path:
    if not spec.path.exists():
        raise FileNotFoundError(f"Excel template not found: {spec.path}")

    target = output_dir / f"{_safe(f'Заказ-{order.number}')}_{spec.output_suffix}.xlsx"
    target.parent.mkdir(parents=True, exist_ok=True)

    ctx = _context(order)
    log.info("Excel [%s] ← %s", spec.output_suffix, spec.path.name)

    tmp = Path(tempfile.gettempdir()) / f"rpa-{spec.path.stem}-{id(order)}{spec.path.suffix}"
    shutil.copy(spec.path, tmp)

    pythoncom.CoInitialize()
    app = win32.DispatchEx("Excel.Application")
    app.Visible = bool(visible)
    app.DisplayAlerts = False
    app.ScreenUpdating = False
    try:
        wb = app.Workbooks.Open(str(tmp), False, False)
        try:
            ws = wb.Worksheets(1)

            for col_letter, width in spec.column_widths.items():
                try:
                    ws.Range(f"{col_letter}1").EntireColumn.ColumnWidth = width
                except Exception as e:
                    log.warning("  could not widen column %s: %s", col_letter, e)

            for addr in spec.cells_to_clear:
                try:
                    _set_cell(ws, addr, "")
                except Exception as e:
                    log.warning("  could not clear %s: %s", addr, e)

            for field_name, address in spec.header.items():
                value = ctx.get(field_name, "")
                if value != "" and value is not None:
                    _set_cell(ws, address, value)
                    if field_name in _NARROW_HEADER_FIELDS:
                        _shrink_font(ws, address, _NARROW_HEADER_FIELDS[field_name])
                    elif spec.default_font_size:
                        _shrink_font(ws, address, spec.default_font_size)

            if spec.print_title_rows:
                try:
                    _set_print_titles(wb, ws, spec.print_title_rows)
                except Exception as e:
                    log.warning("  could not set PrintTitleRows=%r: %s",
                                spec.print_title_rows, e)

            if spec.items_start_row is not None and spec.items_columns:
                needed = min(len(order.items), spec.items_max_rows)
                _expand_items_capacity(ws, spec, needed)
                for i in range(needed):
                    item = order.items[i]
                    row = spec.items_start_row + i
                    for field_name, col_letter in spec.items_columns.items():
                        value = _item_value(item, field_name, order=order)
                        if value is not None and value != "":
                            addr = f"{col_letter}{row}"
                            _set_cell(ws, addr, value)
                            if spec.default_font_size:
                                _shrink_font(ws, addr, spec.default_font_size)
                    _normalize_row_style(ws, spec.items_columns, row,
                                         align_right=spec.items_align_right)

            if spec.secondary_sheet and spec.secondary_header:
                try:
                    ws2 = wb.Worksheets(spec.secondary_sheet)
                except Exception as e:
                    log.warning("  secondary sheet %r not found: %s", spec.secondary_sheet, e)
                else:
                    for addr in spec.secondary_cells_to_clear:
                        try: _set_cell(ws2, addr, "")
                        except Exception: pass
                    for field_name, address in spec.secondary_header.items():
                        value = ctx.get(field_name, "")
                        if value != "" and value is not None:
                            _set_cell(ws2, address, value)
                            if field_name in _NARROW_HEADER_FIELDS:
                                _shrink_font(ws2, address, _NARROW_HEADER_FIELDS[field_name])

            if spec.appendix_sheet and len(order.items) > spec.items_capacity:
                _fill_appendix_sheets(wb, spec, order, ctx)
            elif spec.appendix_sheet:
                _delete_unused_appendix(wb, spec.appendix_sheet)

            if spec.continuation_sheet and len(order.items) > spec.items_capacity:
                _fill_continuation_sheet(wb, spec, order, ctx)

            log.info("  saving → %s", target.name)
            wb.SaveAs(str(target), FileFormat=XL_OPENXML_WORKBOOK)
        finally:
            wb.Close(SaveChanges=False)
        return target
    finally:
        app.ScreenUpdating = True
        app.Quit()
        pythoncom.CoUninitialize()
        try:
            tmp.unlink()
        except OSError:
            pass


def _fill_appendix_sheets(wb, spec: ExcelSpec, order: PurchaseOrder, ctx: dict) -> None:
    """Fill items beyond the main page into appendix sheet copies.

    Sheets are copied from the still-blank template BEFORE any writes —
    otherwise Copy() captures the first batch's values and leaks them into
    later sheets. NB: Worksheet.Copy(After=…) as a NAMED arg is a silent
    no-op in this win32com binding; Copy(Before=…) works, so each new copy
    is inserted BEFORE the template and the template ends up at the back."""
    overflow = order.items[spec.items_capacity:]
    if not overflow:
        return
    try:
        template_sheet = wb.Worksheets(spec.appendix_sheet)
    except Exception as e:
        log.warning("  appendix sheet %r not found: %s — overflow %d item(s) dropped",
                    spec.appendix_sheet, e, len(overflow))
        return

    cap = max(spec.appendix_items_capacity, 1)
    total_appendices = (len(overflow) + cap - 1) // cap
    log.info("  %d overflow item(s) → %d appendix sheet(s) (cap %d/sheet)",
             len(overflow), total_appendices, cap)

    sheets: list = []
    for _ in range(total_appendices - 1):
        before_count = wb.Worksheets.Count
        template_sheet.Copy(Before=template_sheet)
        if wb.Worksheets.Count <= before_count:
            log.warning("    Copy(Before=template) did not add a sheet — overflow capped")
            break
        new_sheet = wb.ActiveSheet
        log.info("    appendix copy created: %r (total sheets=%d)",
                 new_sheet.Name, wb.Worksheets.Count)
        sheets.append(new_sheet)
    sheets.append(template_sheet)

    date_str = order.date.strftime("%d.%m.%Y")
    for page_idx, ws in enumerate(sheets):
        batch = overflow[page_idx * cap:(page_idx + 1) * cap]
        page_no = page_idx + 1

        for addr in spec.appendix_cells_to_clear:
            try:
                _set_cell(ws, addr, "")
            except Exception:
                pass

        ah = spec.appendix_header
        if "appendix_no" in ah:
            _set_cell(ws, ah["appendix_no"], f"Приложение № {page_no}")
        if "parent_doc_no" in ah:
            _set_cell(ws, ah["parent_doc_no"], f"к {spec.appendix_parent_kind} № {order.number or ''}")
        if "parent_doc_date" in ah:
            _set_cell(ws, ah["parent_doc_date"], f"от {date_str}")
        if "sheet_no" in ah:
            _set_cell(ws, ah["sheet_no"], f"Лист {page_no}")

        for i, item in enumerate(batch):
            row = spec.appendix_items_start_row + i
            for field_name, col_letter in spec.appendix_items_columns.items():
                value = _item_value(item, field_name, order=order)
                if value is not None and value != "":
                    _set_cell(ws, f"{col_letter}{row}", value)
            _normalize_row_style(ws, spec.appendix_items_columns, row)

        page_total_qty = sum((it.quantity for it in batch), Decimal("0"))
        page_total_gross = sum(
            ((it.price or Decimal("0")) * (it.quantity or Decimal("0")) for it in batch),
            Decimal("0"),
        ).quantize(Decimal("0.01"), ROUND_HALF_UP)
        page_total_net, page_total_vat = _vat_split(page_total_gross)
        at = spec.appendix_totals
        if "total_qty" in at:           _set_cell(ws, at["total_qty"], float(page_total_qty))
        if "total_cost" in at:          _set_cell(ws, at["total_cost"], float(page_total_net))
        if "total_vat" in at:           _set_cell(ws, at["total_vat"], float(page_total_vat))
        if "total_cost_with_vat" in at: _set_cell(ws, at["total_cost_with_vat"], float(page_total_gross))


def _delete_unused_appendix(wb, sheet_name: str) -> None:
    try:
        wb.Worksheets(sheet_name).Delete()
    except Exception:
        pass


def _fill_continuation_sheet(wb, spec: ExcelSpec, order: PurchaseOrder, ctx: dict) -> None:
    """Fill the single continuation sheet (e.g. Приходный ордер стр2) with
    overflow items. Expands the items area via Rows.Insert if needed; signer
    and Итого rows shift down by the expansion delta."""
    overflow = order.items[spec.items_capacity:]
    if not overflow:
        return
    try:
        ws = wb.Worksheets(spec.continuation_sheet)
    except Exception as e:
        log.warning("  continuation sheet %r not found: %s — overflow %d items dropped",
                    spec.continuation_sheet, e, len(overflow))
        return

    cap = max(spec.continuation_items_capacity, 1)
    extra = max(0, len(overflow) - cap)
    if extra:
        class _Shim:
            items_start_row = spec.continuation_items_start_row
            items_capacity = cap
        _expand_items_capacity(ws, _Shim(), len(overflow))

    for i, item in enumerate(overflow):
        row = spec.continuation_items_start_row + i
        for field_name, col_letter in spec.continuation_items_columns.items():
            value = _item_value(item, field_name, order=order)
            if value is not None and value != "":
                addr = f"{col_letter}{row}"
                _set_cell(ws, addr, value)
                if spec.default_font_size:
                    _shrink_font(ws, addr, spec.default_font_size)
        _normalize_row_style(ws, spec.continuation_items_columns, row,
                             align_right=spec.items_align_right)

    for field_name, addr in spec.continuation_totals.items():
        value = ctx.get(field_name, "")
        if value is not None and value != "":
            target = _shift_row(addr, extra)
            _set_cell(ws, target, value)
            if spec.default_font_size:
                _shrink_font(ws, target, spec.default_font_size)
    for field_name, addr in spec.continuation_header.items():
        value = ctx.get(field_name, "")
        if value is not None and value != "":
            target = _shift_row(addr, extra)
            _set_cell(ws, target, value)
            if spec.default_font_size:
                _shrink_font(ws, target, spec.default_font_size)


def _set_print_titles(wb, ws, rows_range: str) -> None:
    """Repeat `rows_range` (e.g. "$35:$36") at the top of every printed page.
    Setting via the Print_Titles sheet-scoped name is more reliable than
    PageSetup.PrintTitleRows alone, which silently no-ops in some SaveAs paths."""
    sheet_name = ws.Name
    refers_to = f"='{sheet_name}'!{rows_range}"
    name = f"'{sheet_name}'!Print_Titles"
    try:
        wb.Names(name).Delete()
    except Exception:
        pass
    wb.Names.Add(Name=name, RefersTo=refers_to)
    ws.PageSetup.PrintTitleRows = rows_range


def _expand_items_capacity(ws, spec, needed: int) -> None:
    """Insert (needed - items_capacity) rows after the last items row so the
    footer slides down. Uses Copy + Insert (not Insert with CopyOrigin) because
    only the former replicates the row's MERGE structure — without merges,
    overflow rows render in narrow 0.6-wide single cells (#### / clipped text)."""
    if needed <= spec.items_capacity:
        return
    extra = needed - spec.items_capacity
    last_template_row = spec.items_start_row + spec.items_capacity - 1
    insert_at_row = last_template_row + 1
    log.info("  expanding items area: +%d row(s) at row %d", extra, insert_at_row)

    src = ws.Rows(f"{last_template_row}:{last_template_row}")
    try:
        for i in range(extra):
            src.Copy()
            ws.Rows(f"{insert_at_row + i}:{insert_at_row + i}").Insert(Shift=XL_SHIFT_DOWN)
    finally:
        ws.Application.CutCopyMode = False


def _set_cell(ws, address: str, value) -> None:
    """Write a value, redirecting to the merge anchor if needed — Excel
    silently drops writes to non-anchor cells of a merged range."""
    cell = ws.Range(address)
    try:
        if cell.MergeCells:
            cell = cell.MergeArea.Cells(1, 1)
    except Exception:
        pass
    cell.Value = value


def _context(order: PurchaseOrder) -> dict[str, object]:
    """Flatten order + detail dataclasses into a cell-value dict."""
    sup = order.supplier_details or Counterparty()
    org = order.organization_details or Counterparty()
    car = order.carrier or Counterparty()
    tr = order.transport or Transport()
    sig = order.signatories or Signatories()
    dlv = order.delivery or DeliveryContext()

    date_str = order.date.strftime("%d.%m.%Y")
    warehouse = order.warehouse or "склад приёмки"

    total_net = Decimal("0")
    total_vat = Decimal("0")
    total_with_vat = Decimal("0")
    for it in order.items:
        n, v, g, _ = _line_amounts(it)
        total_net += n
        total_vat += v
        total_with_vat += g
    if not order.items:
        total_with_vat = order.total_amount or Decimal("0")
        total_net, total_vat = _vat_split(total_with_vat)
    total_qty = sum((it.quantity for it in order.items), Decimal("0"))

    discrepant = [it for it in order.items
                  if it.qty_actual is not None and it.qty_expected is not None
                  and it.qty_actual != it.qty_expected]
    if discrepant:
        diff_count = len(discrepant)
        diff_amount = sum(
            (abs(it.qty_actual - it.qty_expected) * (it.price or Decimal("0")) for it in discrepant),
            Decimal("0")).quantize(Decimal("0.01"), ROUND_HALF_UP)
    else:
        diff_count = len(order.items)
        diff_amount = sum(
            (it.price or Decimal("0")) for it in order.items
        ).quantize(Decimal("0.01"), ROUND_HALF_UP) if order.items else Decimal("0")
    diff_count_words = num2words(diff_count, lang="ru") if diff_count else "ноль"
    diff_count_unit = _ru_plural(diff_count, "штука", "штуки", "штук")

    chair_name, chair_position = _split_name_position(sig.commission_chairman)
    m1_name, m1_position = _split_name_position(sig.commission_members[0] if len(sig.commission_members) > 0 else "")
    m2_name, m2_position = _split_name_position(sig.commission_members[1] if len(sig.commission_members) > 1 else "")
    mo_name, mo_position = _split_name_position(sig.released_by)
    qty_total_int = int(total_qty)
    pos_count = len(order.items)
    total_weight = sum(
        ((it.weight if it.weight is not None else (it.quantity or Decimal("0")) * Decimal("5.0"))
         for it in order.items), Decimal("0"))
    total_package_count = sum(
        ((it.package_count if it.package_count is not None else (it.quantity or Decimal("0")))
         for it in order.items), Decimal("0"))

    def _qa(it):
        return it.qty_actual if it.qty_actual is not None else (it.quantity or Decimal("0"))

    def _qe(it):
        return it.qty_expected if it.qty_expected is not None else (it.quantity or Decimal("0"))

    inv_qty_fact = sum((_qa(it) for it in order.items), Decimal("0"))
    inv_qty_acc = sum((_qe(it) for it in order.items), Decimal("0"))
    inv_amount_fact = sum(((it.price or Decimal("0")) * _qa(it) for it in order.items),
                          Decimal("0")).quantize(Decimal("0.01"), ROUND_HALF_UP)
    inv_amount_acc = sum(((it.price or Decimal("0")) * _qe(it) for it in order.items),
                         Decimal("0")).quantize(Decimal("0.01"), ROUND_HALF_UP)

    return {
        "doc_number":      order.number or "",
        "date":            date_str,
        "date_day":        f"{order.date.day:02d}",
        "date_month_name": _MONTH_RU_GEN[order.date.month - 1],
        "date_year_2":     f"{order.date.year % 100:02d}",
        "supplier":        sup.name or order.supplier or "",
        "organization":    org.name or order.organization or "",
        "warehouse":       order.warehouse or "",
        "shipper":         _one_line(sup, order.supplier),
        "recipient":       _one_line(org, order.organization),
        "payer":           _one_line(org, order.organization),
        "carrier_line":    _one_line(car, ""),
        "basis":           f"Заказ поставщику №{order.number} от {date_str}",
        "loading_point":   dlv.loading_place,
        "delivery_point":  dlv.delivery_place,
        "total_amount":    _money(total_with_vat),
        "act_caption":     f"АКТ № {order.number} от {date_str}",
        "act_caption_verbose": (
            f'АКТ № {order.number} от "{order.date.day:02d}" '
            f'{_MONTH_RU_GEN[order.date.month - 1]} {order.date.year} г.'
        ),
        "act_number":      order.number or "",
        "act_date":        date_str,
        "vehicle_line":    f"Автомобиль {tr.vehicle_make} {tr.vehicle_plate}".strip(),
        "driver_line":     f"водитель: {tr.driver_full_name}" if tr.driver_full_name else "водитель: —",
        "received_at":     f"Товар поступил в {warehouse} от {sup.name or order.supplier or 'поставщика'}",
        "discrepancy_line":         (
            f"в количестве {diff_count} ({diff_count_words} {diff_count_unit}) "
            f"на сумму: {_amount_digits_ru(diff_amount)}"
        ),
        "discrepancy_amount_words": f"({_amount_words_ru(diff_amount)})",
        "supplier_rep":     f"({sig.sender})" if sig.sender else "",
        "organization_rep": f"({sig.receiver})" if sig.receiver else "",
        "inventory_start": date_str,
        "inventory_end":   date_str,
        "currency":            order.currency or "BYN",
        "shipper_inn_top":     sup.inn,
        "recipient_inn_top":   org.inn,
        "supplier_address":    sup.address,
        "supplier_inn":        sup.inn,
        "supplier_phone":      sup.phone,
        "organization_address":org.address,
        "organization_inn":    org.inn,
        "organization_phone":  org.phone,
        "carrier_name":        car.name,
        "carrier_address":     car.address,
        "carrier_inn":         car.inn,
        "carrier_phone":       car.phone,
        "vehicle_make":        tr.vehicle_make,
        "vehicle_plate":       tr.vehicle_plate,
        "trailer_make":        tr.trailer_make,
        "trailer_plate":       tr.trailer_plate,
        "total_qty":           float(total_qty),
        "total_cost":          float(total_net),
        "total_vat":           float(total_vat),
        "total_cost_with_vat": float(total_with_vat),
        "vat_words":           _amount_words_ru(total_vat),
        "total_words":         _amount_words_ru(total_with_vat),
        "vat_digits":          _amount_digits_ru(total_vat),
        "total_digits":        _amount_digits_ru(total_with_vat),
        "payer_inn_top":       org.inn,
        "vehicle_make_plate":  f"{tr.vehicle_make} {tr.vehicle_plate}".strip(),
        "trailer_make_plate":  f"{tr.trailer_make} {tr.trailer_plate}".strip(),
        "waybill_no":          tr.waybill_number,
        "driver_full_name":    tr.driver_full_name,
        "redirection":         dlv.redirection,
        "total_weight":        float(total_weight),
        "total_weight_words":  _weight_words_ru(total_weight),
        "total_package_count": float(total_package_count),
        "total_package_count_words": _count_words_ru(total_package_count),
        "shipper_seal":        tr.seal_number,
        "receiver_seal":       tr.seal_number,
        "released_by":         sig.released_by,
        "handed_over_by":      sig.sender,
        "carrier_signer":      sig.carrier,
        "receiver_signer":     sig.receiver,
        "proxy_number_date":   (f"№ {dlv.proxy_number} от {dlv.proxy_date}"
                                if dlv.proxy_number or dlv.proxy_date else ""),
        "proxy_issued_by":     dlv.proxy_issued_by,
        "accompanying_docs":   dlv.accompanying_docs,
        "structural_unit":     dlv.structural_unit,
        "operation_code":      dlv.operation_code,
        "insurance_company":   dlv.insurance_company,
        "account_corr":        dlv.account_corr,
        "account_code":        dlv.account_code,
        "doc_accompanying":    dlv.doc_accompanying,
        "doc_payment":         dlv.doc_payment,
        "sklad_card_no":       dlv.sklad_card_no,
        "received_by":         sig.receiver,
        "receiver_name":       sig.receiver.split(",", 1)[0].strip() if sig.receiver else "",
        "receiver_position":   sig.receiver.split(",", 1)[1].strip() if "," in (sig.receiver or "") else "",
        "director_title":      sig.director_title,
        "director_name":       sig.director,
        "approval_day":        f"{order.date.day:02d}",
        "approval_month":      _MONTH_RU_GEN[order.date.month - 1],
        "approval_year":       f"{order.date.year % 100:02d}",
        "as_of_date":          date_str,
        "commission_chairman": sig.commission_chairman,
        "commission_member1":  sig.commission_members[0] if len(sig.commission_members) > 0 else "",
        "commission_member2":  sig.commission_members[1] if len(sig.commission_members) > 1 else "",
        "commission_chairman_sig": sig.commission_chairman,
        "commission_member1_sig":  sig.commission_members[0] if len(sig.commission_members) > 0 else "",
        "commission_member2_sig":  sig.commission_members[1] if len(sig.commission_members) > 1 else "",
        "order_day":           f"{order.date.day:02d}",
        "order_month":         _MONTH_RU_GEN[order.date.month - 1],
        "order_year":          f"{order.date.year % 100:02d}",
        "order_number":        order.number or "",
        "original_cost":       float(total_with_vat),
        "replacement_cost":    float((total_with_vat * Decimal("1.1")).quantize(Decimal("0.01"), ROUND_HALF_UP)),
        "residual_before":     float((total_with_vat * Decimal("0.7")).quantize(Decimal("0.01"), ROUND_HALF_UP)),
        "residual_after":      float((total_with_vat * Decimal("0.77")).quantize(Decimal("0.01"), ROUND_HALF_UP)),
        "okud_code":           "0309018",
        "organization_activity": "оптовая торговля бытовой электроникой",
        "okpo_code":           "12345678",
        "order_date":          date_str,
        "order_number":        order.number or "",
        "total_qty_fact":         float(inv_qty_fact),
        "total_amount_fact":      float(inv_amount_fact),
        "total_qty_accounted":    float(inv_qty_acc),
        "total_amount_accounted": float(inv_amount_acc),
        "count_positions_words": (
            f"{num2words(pos_count, lang='ru').capitalize()} "
            f"{_ru_plural(pos_count, 'порядковый номер', 'порядковых номера', 'порядковых номеров')}"
        ) if pos_count else "Ноль порядковых номеров",
        "total_units_words": (
            f"{num2words(qty_total_int, lang='ru').capitalize()} "
            f"{_ru_plural(qty_total_int, 'штука', 'штуки', 'штук')}"
        ) if qty_total_int else "Ноль штук",
        "total_amount_words_inv": _amount_words_ru(total_with_vat),
        "chair_position":        chair_position,
        "chair_name":            chair_name,
        "member1_position":      m1_position,
        "member1_name":          m1_name,
        "member2_position":      m2_position,
        "member2_name":          m2_name,
        "items_from_no":         1 if pos_count else "",
        "items_to_no":           pos_count if pos_count else "",
        "mo_position":           mo_position,
        "mo_name":               mo_name,
        "mo_top_position":       mo_position,
        "mo_top_name":           mo_name,
        "checker_position":      sig.director_title,
        "checker_name":          sig.director,
    }


def _split_name_position(value: str) -> tuple[str, str]:
    """`"Морозова Е.П., главный бухгалтер"` → `("Морозова Е.П.", "главный бухгалтер")`."""
    if not value:
        return "", ""
    if "," in value:
        name, position = value.split(",", 1)
        return name.strip(), position.strip()
    return value.strip(), ""


def _one_line(party: Counterparty, fallback_name: str) -> str:
    name = party.name or fallback_name or ""
    parts = [name]
    if party.address:
        parts.append(party.address)
    if party.inn:
        parts.append(f"ИНН {party.inn}")
    return ", ".join(p for p in parts if p)


def _line_amounts(item) -> tuple[Decimal, Decimal, Decimal, Decimal]:
    """(net, vat, gross, vat_rate) для строки. Если из WMS пришёл реальный
    vat_rate — цена трактуется как net и НДС добавляется сверху (реальные числа).
    Иначе legacy-режим: price×qty считается суммой С НДС и расщепляется 20%."""
    qty = item.quantity or Decimal("0")
    price = item.price or Decimal("0")
    if item.vat_rate is not None:
        net = (price * qty).quantize(Decimal("0.01"), ROUND_HALF_UP)
        rate = item.vat_rate
        vat = (item.vat_amount if item.vat_amount is not None
               else (net * rate / Decimal("100")).quantize(Decimal("0.01"), ROUND_HALF_UP))
        gross = (net + vat).quantize(Decimal("0.01"), ROUND_HALF_UP)
        return net, vat, gross, rate
    gross = (price * qty).quantize(Decimal("0.01"), ROUND_HALF_UP)
    net, vat = _vat_split(gross)
    return net, vat, gross, _DEFAULT_VAT_RATE


def _item_value(item, field_name: str, *, order=None):
    net, vat, gross, vat_rate = _line_amounts(item)
    qty = item.quantity or Decimal("0")
    weight_kg = item.weight if item.weight is not None else qty * Decimal("5.0")
    package_count = item.package_count if item.package_count is not None else qty
    amount_revalued = (((item.new_price or Decimal("0")) * qty).quantize(Decimal("0.01"), ROUND_HALF_UP)
                       if item.new_price is not None
                       else (gross * Decimal("1.1")).quantize(Decimal("0.01"), ROUND_HALF_UP))
    qty_expected = item.qty_expected if item.qty_expected is not None else qty
    if item.qty_actual is not None:
        qty_actual = item.qty_actual
    else:
        is_surplus = bool(item.row_number % 2)
        qty_actual = qty + (Decimal("1") if is_surplus else Decimal("-1"))
    diff = qty_actual - qty_expected
    mapping = {
        "row_number":    item.row_number,
        "doc_series":    "ПН",
        "doc_number":    order.number if order else "",
        "item_code":     f"АРТ-{item.row_number:03d}",
        "nomenclature":  item.nomenclature,
        "unit":          item.unit,
        "qty":           _num(item.quantity),
        "qty_expected":  _num(qty_expected),
        "qty_accepted":  _num(item.quantity),
        "qty_actual":    float(qty_actual),
        "qty_diff_plus":  float(diff) if diff > 0 else "",
        "qty_diff_minus": float(-diff) if diff < 0 else "",
        "note":          "излишек" if diff > 0 else ("недостача" if diff < 0 else ""),
        "price":         _num(item.price),
        "amount":        _num(item.amount),
        "cost":          float(net),
        "vat_rate":      float(vat_rate),
        "vat_amount":    float(vat),
        "cost_with_vat": float(gross),
        "package_count": _num(package_count),
        "weight":        float(weight_kg),
        "amount_revalued": float(amount_revalued),
        "qty_fact":           float(qty_actual),
        "amount_fact":        float(((item.price or Decimal("0")) * qty_actual)
                                    .quantize(Decimal("0.01"), ROUND_HALF_UP)),
        "qty_accounted":      float(qty_expected),
        "amount_accounted":   float(((item.price or Decimal("0")) * qty_expected)
                                    .quantize(Decimal("0.01"), ROUND_HALF_UP)),
    }
    return mapping.get(field_name, "")


def _num(value) -> float:
    return float(value) if value is not None else 0.0


def _money(value) -> str:
    if value is None:
        return "0,00"
    return f"{Decimal(value):.2f}".replace(".", ",")


def _safe(name: str) -> str:
    bad = '\\/:*?"<>|'
    return "".join("_" if c in bad else c for c in name)
