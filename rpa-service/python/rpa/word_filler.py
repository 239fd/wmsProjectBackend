from __future__ import annotations

import logging
import shutil
import tempfile
from decimal import Decimal
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
from .templates_spec import CellAppend, CellRule, WordItemsTable, WordSpec, WordTableWrite


log = logging.getLogger(__name__)

WD_REPLACE_ALL = 2
WD_REPLACE_ONE = 1
WD_FIND_STOP = 0
WD_FORMAT_XML_DOCUMENT = 12


def fill_word(spec: WordSpec, order: PurchaseOrder, output_dir: Path,
              *, visible: bool = OFFICE.keep_office_visible) -> Path:
    if not spec.path.exists():
        raise FileNotFoundError(f"Word template not found: {spec.path}")

    target = output_dir / f"{_safe(f'Заказ-{order.number}')}_{spec.output_suffix}.docx"
    target.parent.mkdir(parents=True, exist_ok=True)

    ctx = _context(order)
    log.info("Word [%s] ← %s", spec.output_suffix, spec.path.name)

    tmp = Path(tempfile.gettempdir()) / f"rpa-{spec.path.stem}-{id(order)}{spec.path.suffix}"
    shutil.copy(spec.path, tmp)

    pythoncom.CoInitialize()
    app = win32.DispatchEx("Word.Application")
    app.Visible = bool(visible)
    app.DisplayAlerts = 0
    try:
        doc = app.Documents.Open(str(tmp), ConfirmConversions=False, ReadOnly=False)
        try:
            for anchor, repl_template in spec.rules:
                replacement = repl_template.format(**ctx)
                hits = _find_replace_all(doc, anchor, replacement)
                log.info("  replace %r → %r : %d hit(s)",
                         anchor[:40], replacement[:40], hits)

            font_size = spec.font_size
            for tw in spec.table_writes:
                _apply_table_write(doc, tw, ctx, font_size)

            inserted_rows = 0
            if spec.items_table is not None:
                inserted_rows = _fill_items_table(doc, spec.items_table, order, font_size)

            for cr in spec.cell_rules:
                _apply_cell_rule(doc, cr, ctx, inserted_rows, font_size)

            for ca in spec.cell_appends:
                _apply_cell_append(doc, ca, ctx, inserted_rows, font_size)

            for find_text, values in spec.ordered_rules:
                _apply_ordered_replace(doc, find_text, values, ctx)

            log.info("  saving → %s", target.name)
            doc.SaveAs2(str(target), FileFormat=WD_FORMAT_XML_DOCUMENT)
        finally:
            doc.Close(SaveChanges=False)
        return target
    finally:
        app.Quit()
        pythoncom.CoUninitialize()
        try:
            tmp.unlink()
        except OSError:
            pass


def _apply_table_write(doc, tw: WordTableWrite, ctx: dict[str, str],
                       font_size: float | None) -> None:
    try:
        cell = doc.Tables(tw.table_index).Cell(tw.row, tw.col)
        text = tw.template.format(**ctx)
        cell.Range.Text = text
        if font_size is not None:
            cell.Range.Font.Size = font_size
    except Exception as e:
        log.warning("  table_write Table(%d).Cell(%d,%d) failed: %s",
                    tw.table_index, tw.row, tw.col, e)


def _apply_ordered_replace(doc, find_text: str, values: list[str], ctx: dict[str, str]) -> None:
    """Replace each occurrence of `find_text` with successive `values`.
    A FRESH `doc.Content` is used per iteration — Find.Execute shrinks the
    range to the match, so reusing it would search inside the just-written
    replacement and never advance to the next occurrence."""
    for raw_val in values:
        try:
            replacement = raw_val.format(**ctx)
        except (KeyError, IndexError):
            replacement = raw_val
        rng = doc.Content
        find = rng.Find
        find.ClearFormatting()
        find.Replacement.ClearFormatting()
        try:
            ok = find.Execute(
                find_text, False, False, False, False, False,
                True, WD_FIND_STOP, False, replacement, WD_REPLACE_ONE,
            )
            if not ok:
                log.info("  ordered_replace: %r → exhausted after %d replacement(s)",
                         find_text[:30], values.index(raw_val))
                return
        except Exception as e:
            log.warning("  ordered_replace failed for %r: %s", find_text[:30], e)
            return


def _apply_cell_append(doc, ca: CellAppend, ctx: dict[str, str], inserted_rows: int,
                       font_size: float | None) -> None:
    row = ca.row + (inserted_rows if ca.post_items else 0)
    try:
        cell = doc.Tables(ca.table_index).Cell(row, ca.col)
    except Exception as e:
        log.warning("  cell_append Cell(%d,%d) of table %d not accessible: %s",
                    row, ca.col, ca.table_index, e)
        return
    text = ca.template.format(**ctx)
    try:
        cell.Range.InsertAfter(text)
        if font_size is not None:
            cell.Range.Font.Size = font_size
        log.info("  cell_append[%d,%d] += %r", row, ca.col, text[:60])
    except Exception as e:
        log.warning("  cell_append[%d,%d] failed: %s", row, ca.col, e)


def _apply_cell_rule(doc, cr: CellRule, ctx: dict[str, str], inserted_rows: int,
                     font_size: float | None) -> None:
    row = cr.row + (inserted_rows if cr.post_items else 0)
    try:
        cell = doc.Tables(cr.table_index).Cell(row, cr.col)
    except Exception as e:
        log.warning("  cell_rule Cell(%d,%d) of table %d not accessible: %s",
                    row, cr.col, cr.table_index, e)
        return
    replacement = cr.replace_template.format(**ctx)
    hits = _do_find_replace(cell.Range, cr.find, replacement)
    if font_size is not None and hits:
        try:
            cell.Range.Font.Size = font_size
        except Exception:
            pass
    log.info("  cell[%d,%d] %r → %r : %d hit(s)",
             row, cr.col, cr.find[:40], replacement[:40], hits)


def _fill_items_table(doc, spec: WordItemsTable, order: PurchaseOrder,
                      font_size: float | None) -> int:
    """Fill items rows; return number of rows inserted (>= 0)."""
    try:
        tbl = doc.Tables(spec.table_index)
    except Exception as e:
        log.warning("  items_table: Table(%d) not found: %s", spec.table_index, e)
        return 0

    needed = min(len(order.items), 200)
    capacity = max(spec.max_rows, 0)
    inserted = 0
    if spec.insert_rows_if_overflow and needed > capacity:
        inserted = needed - capacity
        last_template_row = spec.start_row + capacity - 1
        log.info("  items_table: inserting %d extra Word row(s) after row %d",
                 inserted, last_template_row)
        try:
            app = doc.Application
            tbl.Cell(last_template_row, 1).Select()
            app.Selection.InsertRowsBelow(inserted)
        except Exception as e:
            log.info("  items_table: InsertRowsBelow failed (%s); falling back to Rows.Add", e)
            try:
                for _ in range(inserted):
                    tbl.Rows.Add(BeforeRow=tbl.Rows(last_template_row + 1))
            except Exception as e2:
                log.warning("  items_table: row insertion fallback also failed: %s", e2)
                inserted = 0

    for i in range(needed):
        item = order.items[i]
        row = spec.start_row + i
        for field_name, col in spec.columns.items():
            value = _item_cell_text(item, field_name, i)
            if value is None or value == "":
                continue
            try:
                cell_range = tbl.Cell(row, col).Range
                cell_range.Text = str(value)
                cell_range.Font.Size = font_size if font_size is not None else 9
                cell_range.ParagraphFormat.Alignment = 0
            except Exception as e:
                log.warning("  items_table cell(%d,%d) failed: %s", row, col, e)
    return inserted


def _item_cell_text(item, field_name: str, i: int) -> str:
    weight_kg = item.weight if item.weight is not None else (item.quantity or Decimal("0")) * Decimal("5.0")
    volume_m3 = item.volume if item.volume is not None else (item.quantity or Decimal("0")) * Decimal("0.05")
    hs_code = item.hs_code or "8418102000"
    package_count = item.package_count if item.package_count is not None else item.quantity
    mapping = {
        "row_number":    str(i + 1),
        "nomenclature":  item.nomenclature or "",
        "unit":          item.unit or "",
        "qty":           _decimal(item.quantity),
        "qty_accepted":  _decimal(item.quantity),
        "package_count": _decimal(package_count),
        "packaging":     "коробка",
        "weight":        _money(weight_kg),
        "volume":        _money(volume_m3),
        "stat_no":       hs_code,
        "price":         _money(item.price),
        "amount":        _money(item.amount),
    }
    return mapping.get(field_name, "")


def _find_replace_all(doc, find_text: str, replace_text: str) -> int:
    """Replace text across story ranges and every table cell — doc.Content.Find
    alone misses text inside tables, so we walk cells explicitly."""
    hits = 0
    for story_id in (1, 2, 3, 7, 8, 9, 10, 11):
        try:
            rng = doc.StoryRanges(story_id)
        except Exception:
            continue
        while rng is not None:
            hits += _do_find_replace(rng, find_text, replace_text)
            try:
                rng = rng.NextStoryRange
            except Exception:
                rng = None

    try:
        tables_count = doc.Tables.Count
    except Exception:
        tables_count = 0
    for t in range(1, tables_count + 1):
        tbl = doc.Tables(t)
        for r in range(1, tbl.Rows.Count + 1):
            try:
                row = tbl.Rows(r)
            except Exception:
                continue
            for c in range(1, row.Cells.Count + 1):
                try:
                    cell = row.Cells(c)
                except Exception:
                    continue
                hits += _do_find_replace(cell.Range, find_text, replace_text)
    return hits


def _do_find_replace(rng, find_text: str, replace_text: str) -> int:
    find = rng.Find
    find.ClearFormatting()
    find.Replacement.ClearFormatting()
    try:
        result = find.Execute(
            find_text, False, False, False, False, False,
            True, WD_FIND_STOP, False, replace_text, WD_REPLACE_ALL,
        )
        return 1 if result else 0
    except Exception:
        return 0


def _context(order: PurchaseOrder) -> dict[str, str]:
    """Flatten order + detail dataclasses into a string dict for Find/Replace.
    Missing Counterparty/Transport/etc. substitute with empty values."""
    sup = order.supplier_details or Counterparty()
    org = order.organization_details or Counterparty()
    car = order.carrier or Counterparty()
    tr = order.transport or Transport()
    sig = order.signatories or Signatories()
    dlv = order.delivery or DeliveryContext()

    total_qty = sum((it.quantity for it in order.items), start=Decimal("0"))
    total_weight = sum(
        ((it.weight if it.weight is not None else (it.quantity or Decimal("0")) * Decimal("5.0"))
         for it in order.items), start=Decimal("0"))
    items_line = "; ".join(it.nomenclature for it in order.items if it.nomenclature)
    total = order.total_amount or Decimal("0")
    rub = int(total)
    kop = int((total - Decimal(rub)) * 100)
    return {
        "doc_number":              _nz(order.number),
        "date":                    order.date.strftime("%d.%m.%Y"),
        "supplier":                _nz(sup.name or order.supplier),
        "organization":            _nz(org.name or order.organization),
        "organization_in_quotes":  _ensure_quotes(_nz(org.name or order.organization)),
        "supplier_in_quotes":      _ensure_quotes(_nz(sup.name or order.supplier)),
        "warehouse":               _nz(order.warehouse),
        "status":                  _nz(order.status),
        "total_amount":            _money(order.total_amount),
        "total_amount_rub":        str(rub),
        "total_amount_kop":        f"{kop:02d}",
        "total_qty":               _decimal(total_qty),
        "total_weight_value":      _money(total_weight),
        "items_line":              items_line,
        "primary_unit":            (order.items[0].unit if order.items else "шт."),
        "supplier_vat":            sup.inn,
        "supplier_address":        sup.address,
        "supplier_phone":          sup.phone,
        "organization_unp":        org.inn,
        "organization_address":    org.address,
        "organization_phone":      org.phone,
        "carrier":                 car.name,
        "carrier_address":         car.address,
        "carrier_unp":             car.inn,
        "carrier_phone":           car.phone,
        "next_carrier":            dlv.next_carrier,
        "carrier_remarks":         dlv.carrier_remarks,
        "delivery_place":          dlv.delivery_place or _nz(order.warehouse),
        "loading_place":           dlv.loading_place,
        "currency_or_default":     _nz(order.currency) or "BYN",
        "country_of_export":       dlv.country_of_export,
        "country_of_manufacture":  dlv.country_of_manufacture,
        "country_of_destination":  dlv.country_of_destination,
        "vehicle_make":            tr.vehicle_make,
        "vehicle_plate":           tr.vehicle_plate,
        "trailer_plate":           tr.trailer_plate,
        "trailer_make":            tr.trailer_make,
        "transport_rate":          dlv.transport_rate,
        "transport_total":         dlv.transport_total,
        "shipper_instructions":    dlv.shipper_instructions,
        "payment_terms":           dlv.payment_terms,
        "special_terms":           dlv.special_terms,
        "loading_arrival_hour":     tr.loading_arrival_hour,
        "loading_arrival_min":      tr.loading_arrival_min,
        "loading_departure_hour":   tr.loading_departure_hour,
        "loading_departure_min":    tr.loading_departure_min,
        "unloading_arrival_hour":   tr.unloading_arrival_hour,
        "unloading_arrival_min":    tr.unloading_arrival_min,
        "unloading_departure_hour": tr.unloading_departure_hour,
        "unloading_departure_min":  tr.unloading_departure_min,
        "received_date":           order.date.strftime("«%d» %m.%Y г."),
        "signer_line":             sig.sender,
        "signer_sender":           sig.sender,
        "signer_carrier":          sig.carrier,
        "signer_receiver":         sig.receiver,
        "director_name":           sig.director,
        "director_title":          sig.director_title,
        "commission_chairman":     sig.commission_chairman,
        "commission_member1":      sig.commission_members[0] if len(sig.commission_members) > 0 else "",
        "commission_member2":      sig.commission_members[1] if len(sig.commission_members) > 1 else "",
        "date_day":                f"{order.date.day:02d}",
        "date_month_name":         _MONTH_RU_GEN[order.date.month - 1],
        "date_year_2":             f"{order.date.year % 100:02d}",
        "total_amount_words_rub":  _rub_words_only(total),
    }


def _ensure_quotes(s: str) -> str:
    if not s:
        return "ООО «—»"
    if "«" in s and "»" in s:
        return s
    return f"ООО «{s}»"


def _money(value) -> str:
    if value is None:
        return "0,00"
    return f"{Decimal(value):.2f}".replace(".", ",")


def _decimal(value) -> str:
    if value is None or value == 0:
        return "0"
    return format(Decimal(value).normalize(), "f")


def _nz(s) -> str:
    return s if s else ""


def _safe(name: str) -> str:
    bad = '\\/:*?"<>|'
    return "".join("_" if c in bad else c for c in name)


_MONTH_RU_GEN = (
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)


def _rub_words_only(amount: Decimal) -> str:
    """`120.55` → `Сто двадцать` (rub part only, no unit suffix)."""
    if amount is None:
        amount = Decimal("0")
    rub = int(amount)
    return num2words(rub, lang="ru").capitalize()
