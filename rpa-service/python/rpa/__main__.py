from __future__ import annotations

import argparse
import logging
import sys

from . import fixtures, office_filler


def _setup_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)-5s [%(name)s] %(message)s",
        datefmt="%H:%M:%S",
        stream=sys.stdout,
    )


def _parse_mock(value: str) -> list[int]:
    if value == "all":
        return [1, 5, 10, 50]
    try:
        n = int(value)
    except ValueError as e:
        raise argparse.ArgumentTypeError(f"--mock expects int or 'all', got {value!r}") from e
    if n < 1:
        raise argparse.ArgumentTypeError("--mock size must be >= 1")
    return [n]


def main() -> int:
    _setup_logging()
    log = logging.getLogger("app")

    parser = argparse.ArgumentParser(prog="rpa")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--smoke", action="store_true",
                       help="legacy: 1-item fixture (alias for --mock 1)")
    group.add_argument("--mock", type=_parse_mock, metavar="N",
                       help="use mock fixture(s): N items, or 'all' for 1/5/10/50")
    parser.add_argument("--doc", action="append", metavar="NAME",
                        help="fill only this document (output_suffix); "
                             "repeat for multiple. Case-insensitive. "
                             "Comma-separated values also accepted.")
    parser.add_argument("--list-docs", action="store_true",
                        help="print all available document names and exit")
    parser.add_argument("--parse-only", action="store_true",
                        help="parse 1С orders + export JSON, skip Office template fill")
    parser.add_argument("--sales", action="store_true",
                        help="also fetch Заказы клиентов (Продажи) and write sales.json")
    parser.add_argument("--sales-only", action="store_true",
                        help="fetch ONLY Заказы клиентов; skip supply orders and templates")
    args = parser.parse_args()

    if args.list_docs:
        print(f"{'Category':<6}  Name")
        print(f"{'-' * 6}  {'-' * 30}")
        for category, name in office_filler.list_doc_names():
            print(f"{category:<6}  {name}")
        return 0

    only: list[str] | None = None
    if args.doc:
        only = []
        for raw in args.doc:
            only.extend(part for part in raw.split(",") if part.strip())
        log.info("Filter: --doc %s", only)

    if args.smoke:
        sizes = [1]
    elif args.mock:
        sizes = args.mock
    else:
        sizes = None

    from . import supply_exporter
    from .config import OUTPUT_DIR
    from datetime import datetime

    if args.sales_only:
        from .onec_parser import fetch_sales_orders
        log.info("=== SALES-ONLY MODE: parsing 1С Заказы клиентов ===")
        sales = fetch_sales_orders()
        run_dir = OUTPUT_DIR / datetime.now().strftime("%Y-%m-%d_%H%M%S")
        run_dir.mkdir(parents=True, exist_ok=True)
        sales_path = run_dir / "sales.json"
        supply_exporter.export_sales(sales, sales_path)
        log.info("Sales-only run. %d order(s). JSON: %s", len(sales), sales_path)
        return 0

    if sizes is not None:
        log.info("=== MOCK MODE: sizes=%s ===", sizes)
        orders = [fixtures.fixture(n) for n in sizes]
    else:
        from .onec_parser import fetch_orders
        log.info("=== Module 1: parsing 1С orders ===")
        orders = fetch_orders()
        log.info("Module 1 done: %d order(s) extracted", len(orders))

    if args.parse_only:
        run_dir = OUTPUT_DIR / datetime.now().strftime("%Y-%m-%d_%H%M%S")
        run_dir.mkdir(parents=True, exist_ok=True)
        json_path = run_dir / "supplies.json"
        supply_exporter.export_supplies(orders, json_path)
        log.info("Parse-only run. %d order(s) extracted. JSON: %s", len(orders), json_path)
        if args.sales:
            _fetch_and_export_sales(run_dir, supply_exporter, log)
        return 0

    log.info("=== Module 2: filling Office templates ===")
    report = office_filler.fill_all(orders, only=only)
    json_path = report.output_dir / "supplies.json"
    supply_exporter.export_supplies(orders, json_path)
    if args.sales:
        _fetch_and_export_sales(report.output_dir, supply_exporter, log)

    log.info("All done. %d file(s), %d error(s). JSON: %s",
             report.files_count, report.errors_count, json_path)
    return 0 if report.errors_count == 0 else 1


def _fetch_and_export_sales(run_dir, supply_exporter, log) -> None:
    """Sidecar: failures are logged and swallowed so they don't tank the supply flow."""
    from .onec_parser import fetch_sales_orders
    try:
        log.info("=== Sales sidecar: parsing 1С Заказы клиентов ===")
        sales = fetch_sales_orders()
        sales_path = run_dir / "sales.json"
        supply_exporter.export_sales(sales, sales_path)
    except Exception as e:
        log.warning("Sales sidecar failed: %s", e, exc_info=True)


if __name__ == "__main__":
    sys.exit(main())
