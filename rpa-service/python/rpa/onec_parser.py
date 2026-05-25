from __future__ import annotations

import logging
import re
import subprocess
import time
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from typing import Optional

from pywinauto import Application, Desktop, mouse
from pywinauto.findwindows import ElementNotFoundError
from pywinauto.timings import TimeoutError as PywaTimeout

from .config import ONEC
from .models import OrderItem, PurchaseOrder


log = logging.getLogger(__name__)

_ITEM_COLUMNS = (
    "N", "№", "Номенклатура", "Характеристика", "Количество", "Упаковок",
    "Ед. изм.", "Ед.", "Цена", "Сумма", "% НДС", "Сумма НДС", "Всего",
)

_AMOUNT_CLEAN = re.compile(r"[^\d,.\-]")


@dataclass(frozen=True, slots=True)
class _JournalConfig:
    section: str
    journal: str
    document_pane_title: str
    counterparty_column: str
    list_columns: tuple[str, ...]
    status_filter: tuple[str, ...] | None
    status_exclude: tuple[str, ...] = ()


_SUPPLY_CFG = _JournalConfig(
    section="Закупки",
    journal="Заказы поставщикам",
    document_pane_title="Заказ поставщику",
    counterparty_column="Поставщик",
    list_columns=ONEC.list_columns,
    status_filter=ONEC.target_statuses,
)

_SALES_CFG = _JournalConfig(
    section="Продажи",
    journal="Заказы клиентов",
    document_pane_title="Заказ клиента",
    counterparty_column="Клиент",
    list_columns=(
        "Номер", "Дата", "Сумма", "Клиент", "Текущее состояние",
        "Срок выполнения", "% оплаты", "% отгрузки", "% долга",
        "Валюта", "Операция",
    ),
    status_filter=None,
    status_exclude=("Закрыт", "Готов к закрытию"),
)


def fetch_orders() -> list[PurchaseOrder]:
    _, main = _connect_or_launch()
    log.info("Connected to 1С window: %s", main.window_text())
    return _fetch_journal(main, _SUPPLY_CFG)


def fetch_sales_orders() -> list[PurchaseOrder]:
    _, main = _connect_or_launch()
    log.info("Connected to 1С window: %s", main.window_text())
    return _fetch_journal(main, _SALES_CFG)


def _fetch_journal(main, cfg: _JournalConfig) -> list[PurchaseOrder]:
    _close_stale_panes(main, cfg.document_pane_title)
    _navigate(main, (cfg.section, cfg.journal))
    _scroll_journal_to_top(main)

    orders: list[PurchaseOrder] = []
    processed: set[str] = set()
    seen_status: dict[str, str] = {}

    include_norm = tuple(_norm(s) for s in cfg.status_filter) if cfg.status_filter else None
    exclude_norm = tuple(_norm(s) for s in cfg.status_exclude) if cfg.status_exclude else ()

    def _passes(row_dict) -> bool:
        s = _norm(row_dict.get(ONEC.status_column))
        if include_norm is not None and not any(t in s for t in include_norm):
            return False
        if exclude_norm and any(t in s for t in exclude_norm):
            return False
        return True

    if cfg.status_filter:
        log.info("  include filter: %s", list(cfg.status_filter))
    if cfg.status_exclude:
        log.info("  exclude filter: %s", list(cfg.status_exclude))

    no_new_scrolls = 0
    first_pass = True
    while len(orders) < ONEC.max_orders:
        rows = _collect_journal_rows(main, cfg.list_columns, key_column="Номер")
        for r, _ in rows:
            num = r.get("Номер")
            if num and num not in seen_status:
                seen_status[num] = r.get(ONEC.status_column) or "—"
        if first_pass:
            first_pass = False
            sample = [(r.get("Номер"), r.get("Дата"), r.get(ONEC.status_column))
                      for r, _ in rows[:3]]
            log.info("First visible page (%d rows). Sample: %s", len(rows), sample)

        candidates = [(r, a) for r, a in rows
                      if r.get("Номер") and r["Номер"] not in processed and _passes(r)]

        if candidates:
            no_new_scrolls = 0
            row_dict, anchor = candidates[0]
            number = row_dict["Номер"]
            log.info("[%d] Opening %s, %s=%s",
                     len(orders) + 1, number, cfg.counterparty_column,
                     row_dict.get(cfg.counterparty_column))
            try:
                _open_via_anchor(main, anchor, expected_number=number,
                                 pane_title=cfg.document_pane_title)
                organization = _read_form_field(main, ("Организация:", "Организация"))
                items = _read_items_table(main)
                orders.append(_build_order(row_dict, items,
                                           organization=organization,
                                           counterparty_key=cfg.counterparty_column))
            except Exception as e:
                log.warning("Skipping %s: %s", number, e, exc_info=True)
            finally:
                processed.add(number)
                _close_active_document(main)
                time.sleep(0.6)
            continue

        newly_skipped = 0
        for r, _ in rows:
            num = r.get("Номер")
            if num and num not in processed:
                processed.add(num)
                newly_skipped += 1
        if newly_skipped:
            log.info("Visible page exhausted: %d row(s) filtered out, scrolling…",
                     newly_skipped)
            no_new_scrolls = 0
        else:
            no_new_scrolls += 1
            if no_new_scrolls >= 2:
                log.info("End of journal: PgDn did not reveal new rows")
                break
        try:
            main.set_focus()
            if rows:
                mouse.click(button="left", coords=rows[0][1])
                time.sleep(0.15)
            main.type_keys("{PGDN}")
            time.sleep(0.6)
        except Exception as e:
            log.warning("PgDn failed: %s — stopping", e)
            break

    if seen_status:
        dist = Counter(seen_status.values())
        log.info("Journal %r: scanned %d unique row(s), status distribution: %s",
                 cfg.journal, len(seen_status),
                 ", ".join(f"{n}× {st!r}" for st, n in dist.most_common()))
    return orders


def _connect_or_launch():
    if ONEC.attach_if_running:
        try:
            app = Application(backend="uia").connect(
                title_re=ONEC.window_title_regex, timeout=2)
            return app, app.top_window()
        except (ElementNotFoundError, PywaTimeout):
            log.info("1С window not found, launching: %s", ONEC.executable)

    launch_cmd = [ONEC.executable, *ONEC.launch_args, f'/IBName="{ONEC.infobase}"']
    log.info("Launching 1С: %s", " ".join(launch_cmd))
    subprocess.Popen(launch_cmd, close_fds=True)
    deadline = time.time() + 90
    while time.time() < deadline:
        try:
            app = Application(backend="uia").connect(
                title_re=ONEC.window_title_regex, timeout=2)
            return app, app.top_window()
        except (ElementNotFoundError, PywaTimeout):
            time.sleep(1.5)
    raise RuntimeError("Failed to attach to a 1С window within 90s")


def _navigate(main, path: tuple[str, ...]) -> None:
    log.info("Navigating: %s", " → ".join(path))
    try:
        main.set_focus()
        main.type_keys("{ESC}")
        time.sleep(0.2)
    except Exception:
        pass
    preferred_per_level = (
        ("TabItem", "Button", "Hyperlink"),
        ("TreeItem", "Hyperlink", "Button", "TabItem", "MenuItem"),
    )
    for i, name in enumerate(path):
        prefer = preferred_per_level[min(i, len(preferred_per_level) - 1)]
        _click_lenient(main, name, timeout=30 if i == 0 else 20, prefer_types=prefer)
        time.sleep(0.7 if i < len(path) - 1 else 1.5)


def _click_lenient(window, name: str, timeout: int,
                   prefer_types: tuple[str, ...] = ()) -> None:
    deadline = time.time() + timeout
    last_error = None
    while time.time() < deadline:
        try:
            candidates = window.descendants(title=name)
        except Exception as e:
            last_error = e
            time.sleep(0.5)
            continue

        def score(el):
            try:
                t = el.element_info.control_type
            except Exception:
                t = ""
            return prefer_types.index(t) if t in prefer_types else len(prefer_types)
        ordered = sorted(candidates, key=score)

        for el in ordered:
            try:
                if not el.is_visible():
                    continue
                el.click_input()
                return
            except Exception as e:
                last_error = e
        time.sleep(0.5)
    raise TimeoutError(f"Could not click visible element titled '{name}': {last_error}")


def _collect_journal_rows(
    main, columns: tuple[str, ...], *, key_column: str,
) -> list[tuple[dict[str, str], tuple[int, int]]]:
    """Group flat UIA cells (titled '<value> <columnName>') into rows by Y-coord."""
    suffixes = sorted([(" " + c, c) for c in columns], key=lambda x: -len(x[0]))
    by_y: dict[int, list[tuple[int, str, str, object]]] = defaultdict(list)

    elements = main.descendants()
    log.info("Walking %d UIA descendants…", len(elements))
    for el in elements:
        try:
            text = (el.window_text() or "").strip()
        except Exception:
            continue
        if not text:
            continue
        for suffix, col in suffixes:
            if text.endswith(suffix):
                value = text[: -len(suffix)].strip()
                try:
                    rect = el.rectangle()
                except Exception:
                    break
                y_key = rect.top - (rect.top % 5)
                by_y[y_key].append((rect.left, col, value, el))
                break

    rows: list[tuple[dict[str, str], tuple[int, int]]] = []
    for y in sorted(by_y):
        cells = sorted(by_y[y], key=lambda t: t[0])
        row_dict = {col: val for _, col, val, _ in cells}
        key_cell = next(((el, x) for x, col, _, el in cells if col == key_column), None)
        if key_cell is None or key_column not in row_dict:
            continue
        el, _ = key_cell
        try:
            rect = el.rectangle()
            anchor_point = (rect.left + 8, rect.top + (rect.bottom - rect.top) // 2)
        except Exception:
            continue
        rows.append((row_dict, anchor_point))
    return rows


def _open_via_anchor(main, anchor_point: tuple[int, int],
                     expected_number: str = "", *,
                     pane_title: str = "Заказ поставщику") -> object:
    try:
        main.set_focus()
    except Exception:
        pass
    x, y = anchor_point
    log.info("Clicking row anchor at (%d, %d)", x, y)
    mouse.click(button="left", coords=(x, y))
    time.sleep(0.25)
    main.type_keys("{F2}")

    pane = _wait_for_open_document(main, expected_number, pane_title=pane_title)
    time.sleep(1.5)
    return pane


def _wait_for_open_document(main, expected_number: str = "", *,
                            pane_title: str = "Заказ поставщику",
                            timeout: int = 20) -> object:
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            for el in main.descendants():
                try:
                    title = (el.window_text() or "").strip()
                    ctype = el.element_info.control_type
                except Exception:
                    continue
                if ctype not in ("Pane", "Custom"):
                    continue
                if title == pane_title:
                    return el
                if expected_number and expected_number in title:
                    return el
        except Exception:
            pass
        time.sleep(0.5)

    log.warning("Document did not open. Visible Pane/Custom items:")
    try:
        for el in main.descendants():
            try:
                title = (el.window_text() or "").strip()
                ctype = el.element_info.control_type
            except Exception:
                continue
            if not title or len(title) > 80:
                continue
            if ctype in ("Pane", "Custom") and ("Заказ" in title or expected_number in title):
                log.warning("  [%s] %s", ctype, title)
    except Exception:
        pass
    raise TimeoutError("Document workspace pane did not appear")


def _scroll_journal_to_top(main) -> None:
    try:
        rows = _collect_journal_rows(main, ONEC.list_columns, key_column="Номер")
        if not rows:
            return
        _, anchor = rows[0]
        main.set_focus()
        mouse.click(button="left", coords=anchor)
        time.sleep(0.2)
        main.type_keys("^{HOME}")
        time.sleep(0.6)
    except Exception as e:
        log.warning("Could not scroll journal to top: %s", e)


def _close_stale_panes(main, pane_prefix: str = "Заказ поставщику") -> None:
    seen = 0
    for _ in range(10):
        try:
            stale = None
            for el in main.descendants():
                try:
                    title = (el.window_text() or "").strip()
                    ctype = el.element_info.control_type
                except Exception:
                    continue
                if ctype not in ("Pane", "Custom"):
                    continue
                if title.startswith(pane_prefix):
                    stale = el
                    break
            if not stale:
                break
            seen += 1
            log.info("Closing stale pane: %s", stale.window_text())
            try:
                main.set_focus()
            except Exception:
                pass
            main.type_keys("^{F4}")
            time.sleep(0.6)
            for btn_title in ("Нет", "No"):
                try:
                    btn = Desktop(backend="uia").window(title=btn_title, control_type="Button")
                    btn.wait("visible", timeout=1)
                    btn.click_input()
                    break
                except (ElementNotFoundError, PywaTimeout):
                    continue
        except Exception as e:
            log.warning("Stale-pane cleanup error: %s", e)
            break
    if seen:
        log.info("Closed %d stale pane(s)", seen)


def _close_active_document(main) -> None:
    try:
        main.set_focus()
    except Exception:
        pass
    try:
        main.type_keys("^{F4}")
        time.sleep(0.4)
    except Exception:
        pass
    for title in ("Нет", "No", "Не сохранять", "Don't Save"):
        try:
            btn = Desktop(backend="uia").window(title=title, control_type="Button")
            btn.wait("visible", timeout=1)
            btn.click_input()
            break
        except (ElementNotFoundError, PywaTimeout):
            continue


def _read_items_table(main) -> list[OrderItem]:
    tab_el = _find_tab_starting_with(main, "Товары")
    if tab_el is not None:
        try:
            tab_el.click_input()
            log.info("Clicked tab: %s", tab_el.window_text())
            time.sleep(1.2)
        except Exception as e:
            log.warning("Failed to click Товары tab: %s", e)
    else:
        log.warning("'Товары' tab not found in current form")

    suffixes = sorted(
        [(" " + c, c) for c in _ITEM_COLUMNS], key=lambda x: -len(x[0]))

    by_y: dict[int, list[tuple[int, str, str]]] = defaultdict(list)
    try:
        elements = main.descendants()
    except Exception:
        return []
    for el in elements:
        try:
            text = (el.window_text() or "").strip()
            if not text:
                continue
            for suf, col in suffixes:
                if text.endswith(suf):
                    value = text[: -len(suf)].strip()
                    try:
                        rect = el.rectangle()
                    except Exception:
                        break
                    y_key = rect.top - (rect.top % 5)
                    by_y[y_key].append((rect.left, col, value))
                    break
        except Exception:
            continue

    items: list[OrderItem] = []
    for i, y in enumerate(sorted(by_y), start=1):
        cells = sorted(by_y[y], key=lambda t: t[0])
        row = {col: val for _, col, val in cells}
        nomenclature = row.get("Номенклатура") or ""
        if not nomenclature:
            continue
        items.append(OrderItem(
            row_number=i,
            nomenclature=nomenclature,
            unit=row.get("Ед. изм.") or row.get("Ед.") or "",
            quantity=_parse_amount(row.get("Количество")),
            price=_parse_amount(row.get("Цена")),
            amount=_parse_amount(row.get("Сумма")),
        ))
    return items


def _find_tab_starting_with(main, prefix: str):
    for el in main.descendants():
        try:
            if el.element_info.control_type != "TabItem":
                continue
            if (el.window_text() or "").strip().startswith(prefix):
                return el
        except Exception:
            continue
    return None


def _read_form_field(main, label_candidates: tuple[str, ...]) -> str:
    label_set = set(label_candidates)
    for el in main.descendants():
        try:
            ctype = el.element_info.control_type
            if ctype not in ("ComboBox", "Edit"):
                continue
            if (el.window_text() or "").strip() not in label_set:
                continue
            value = _get_uia_value(el)
            if value:
                return value
        except Exception:
            continue
    return ""


def _get_uia_value(el) -> str:
    try:
        props = el.legacy_properties()
        v = (props.get("Value") or "").strip()
        if v:
            return v
    except Exception:
        pass
    try:
        text = (el.window_text() or "").strip()
        if text and not text.endswith(":"):
            return text
    except Exception:
        pass
    return ""


def _build_order(row_dict: dict[str, str], items: list[OrderItem], *,
                 organization: str = "",
                 counterparty_key: str = "Поставщик") -> PurchaseOrder:
    return PurchaseOrder(
        number=row_dict.get("Номер", ""),
        date=_parse_date(row_dict.get("Дата", "")),
        supplier=row_dict.get(counterparty_key, ""),
        organization=organization,
        warehouse="",
        status=row_dict.get(ONEC.status_column, ""),
        total_amount=_parse_amount(row_dict.get("Сумма", "0")),
        items=items,
        expected_date=_parse_date_optional(row_dict.get("Срок выполнения", "")),
        currency=row_dict.get("Валюта", "").strip(),
        payment_pct=_parse_pct(row_dict.get("% оплаты")),
        receipt_pct=_parse_pct(row_dict.get("% поступления") or row_dict.get("% отгрузки")),
        debt_pct=_parse_pct(row_dict.get("% долга")),
        operation=row_dict.get("Операция", "").strip(),
    )


def _parse_amount(raw: Optional[str]) -> Decimal:
    parsed = _parse_amount_optional(raw)
    return parsed if parsed is not None else Decimal("0")


def _parse_amount_optional(raw: Optional[str]) -> Decimal | None:
    if not raw or not raw.strip():
        return None
    cleaned = _AMOUNT_CLEAN.sub("", raw).replace(",", ".")
    if not cleaned or cleaned in ("-", "."):
        return None
    try:
        return Decimal(cleaned)
    except InvalidOperation:
        return None


def _parse_date(raw: str) -> date:
    parsed = _parse_date_optional(raw)
    if parsed is not None:
        return parsed
    log.warning("Could not parse order date %r — using today's date as fallback", raw)
    return date.today()


def _parse_date_optional(raw: str) -> date | None:
    raw = (raw or "").strip()
    if not raw or raw in ("—", "-"):
        return None
    candidates = (raw, raw.split()[0]) if " " in raw else (raw,)
    for candidate in candidates:
        for fmt in ("%d.%m.%Y %H:%M:%S", "%d.%m.%Y", "%Y-%m-%d"):
            try:
                return datetime.strptime(candidate, fmt).date()
            except ValueError:
                continue
    return None


def _parse_pct(raw: str | None) -> Decimal | None:
    return _parse_amount_optional(raw)


def _norm(s: Optional[str]) -> str:
    return (s or "").strip().lower()
