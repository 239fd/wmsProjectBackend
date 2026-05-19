from __future__ import annotations

import logging
from collections.abc import Iterable
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path

from .config import OUTPUT_DIR
from .excel_filler import fill_excel
from .models import PurchaseOrder
from .templates_spec import EXCEL_SPECS, WORD_SPECS
from .word_filler import fill_word


log = logging.getLogger(__name__)


@dataclass(slots=True)
class RunReport:
    output_dir: Path
    files: list[Path] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)

    @property
    def files_count(self) -> int:
        return len(self.files)

    @property
    def errors_count(self) -> int:
        return len(self.errors)


def fill_all(
    orders: Iterable[PurchaseOrder],
    *,
    only: Iterable[str] | None = None,
) -> RunReport:
    """Fill all configured templates for each order. `only` filters by
    case-insensitive output_suffix; unknown names are reported as errors."""
    orders = list(orders)
    if not orders:
        log.warning("No orders to fill — nothing to do")
        return RunReport(output_dir=OUTPUT_DIR)

    excel_specs, word_specs, unknown = _filter_specs(only)

    run_dir = OUTPUT_DIR / datetime.now().strftime("%Y-%m-%d_%H%M%S")
    log.info("Output directory for this run: %s", run_dir)
    run_dir.mkdir(parents=True, exist_ok=True)

    report = RunReport(output_dir=run_dir)
    for name in unknown:
        msg = f"Unknown document name in --doc filter: {name!r}"
        log.warning(msg)
        report.errors.append(msg)

    if not excel_specs and not word_specs:
        log.warning("Nothing to fill after filtering — check --doc names against --list-docs")
        return report

    for i, order in enumerate(orders, start=1):
        if not order.number:
            msg = f"Order #{i} has empty `number` — skipping all templates for it"
            log.warning(msg)
            report.errors.append(msg)
            continue

        log.info("=== Order №%s — %d Excel + %d Word templates ===",
                 order.number, len(excel_specs), len(word_specs))
        for spec in excel_specs:
            try:
                report.files.append(fill_excel(spec, order, run_dir))
            except Exception as e:
                msg = f"Excel [{spec.output_suffix}] for {order.number}: {e}"
                log.error(msg, exc_info=True)
                report.errors.append(msg)
        for spec in word_specs:
            try:
                report.files.append(fill_word(spec, order, run_dir))
            except Exception as e:
                msg = f"Word [{spec.output_suffix}] for {order.number}: {e}"
                log.error(msg, exc_info=True)
                report.errors.append(msg)

    log.info("Done. %d file(s) created, %d error(s). Output: %s",
             report.files_count, report.errors_count, run_dir)
    return report


def list_doc_names() -> list[tuple[str, str]]:
    """Return [(category, output_suffix)] for every spec — for --list-docs."""
    return ([("Excel", s.output_suffix) for s in EXCEL_SPECS]
            + [("Word", s.output_suffix) for s in WORD_SPECS])


def _filter_specs(only: Iterable[str] | None):
    if only is None:
        return list(EXCEL_SPECS), list(WORD_SPECS), []
    wanted = {n.strip().casefold() for n in only if n and n.strip()}
    known_excel = {s.output_suffix.casefold(): s for s in EXCEL_SPECS}
    known_word = {s.output_suffix.casefold(): s for s in WORD_SPECS}
    excel = [known_excel[k] for k in wanted if k in known_excel]
    word = [known_word[k] for k in wanted if k in known_word]
    unknown = [n for n in only if n.strip().casefold() not in (known_excel | known_word)]
    return excel, word, unknown
