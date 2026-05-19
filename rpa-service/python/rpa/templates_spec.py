from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

from .config import TEMPLATES_DIR


@dataclass(frozen=True, slots=True)
class ExcelSpec:
    name: str
    output_suffix: str
    header: dict[str, str]
    items_start_row: int | None = None
    items_capacity: int = 1
    items_max_rows: int = 200
    items_columns: dict[str, str] = field(default_factory=dict)
    print_title_rows: str = ""
    appendix_sheet: str = ""
    appendix_items_start_row: int = 6
    appendix_items_capacity: int = 0
    appendix_items_columns: dict[str, str] = field(default_factory=dict)
    appendix_header: dict[str, str] = field(default_factory=dict)
    appendix_totals: dict[str, str] = field(default_factory=dict)
    appendix_parent_kind: str = "ТН-2"
    items_align_right: bool = False
    default_font_size: int = 0
    column_widths: dict[str, float] = field(default_factory=dict)
    cells_to_clear: tuple[str, ...] = ()
    appendix_cells_to_clear: tuple[str, ...] = ()
    secondary_sheet: str = ""
    secondary_header: dict[str, str] = field(default_factory=dict)
    secondary_cells_to_clear: tuple[str, ...] = ()
    continuation_sheet: str = ""
    continuation_items_start_row: int = 0
    continuation_items_capacity: int = 0
    continuation_items_columns: dict[str, str] = field(default_factory=dict)
    continuation_totals: dict[str, str] = field(default_factory=dict)
    continuation_header: dict[str, str] = field(default_factory=dict)

    @property
    def path(self) -> Path:
        return TEMPLATES_DIR / self.name


@dataclass(frozen=True, slots=True)
class WordTableWrite:
    table_index: int
    row: int
    col: int
    template: str


@dataclass(frozen=True, slots=True)
class CellRule:
    # post_items=True shifts row by the number of items rows inserted (for
    # totals/footer rows that move after items area expands).
    table_index: int
    row: int
    col: int
    find: str
    replace_template: str
    post_items: bool = False


@dataclass(frozen=True, slots=True)
class WordItemsTable:
    table_index: int
    start_row: int
    columns: dict[str, int]
    max_rows: int = 6
    insert_rows_if_overflow: bool = True


@dataclass(frozen=True, slots=True)
class CellAppend:
    # Used instead of F&R when the replacement needs a paragraph break: Word's
    # Find.Replacement silently strips `\r`, so we Range.InsertAfter the value.
    table_index: int
    row: int
    col: int
    template: str
    post_items: bool = False


@dataclass(frozen=True, slots=True)
class WordSpec:
    name: str
    output_suffix: str
    rules: list[tuple[str, str]]
    table_writes: list[WordTableWrite] = field(default_factory=list)
    items_table: WordItemsTable | None = None
    cell_rules: list[CellRule] = field(default_factory=list)
    cell_appends: list[CellAppend] = field(default_factory=list)
    # Sequential placeholder fills: same find-text appearing N times, each
    # replaced by the next value (e.g. chairman + 3 commission members).
    ordered_rules: list[tuple[str, list[str]]] = field(default_factory=list)
    font_size: float | None = None

    @property
    def path(self) -> Path:
        return TEMPLATES_DIR / self.name


EXCEL_SPECS: list[ExcelSpec] = [
    ExcelSpec(
        name="Приходной ордер.XLS",
        output_suffix="ПриходныйОрдер",
        header={
            # Header (top of стр1)
            "doc_number":         "BN5",     # «ПРИХОДНЫЙ ОРДЕР № …»
            "organization":       "M8",      # «Организация»
            "structural_unit":    "Z9",      # «Структурное подразделение»
            # Row 13 — data row beneath the two-level header (rows 11-12 labels)
            "date":               "A13",     # Дата составления
            "operation_code":     "I13",     # Код вида операции
            "warehouse":          "Q13",     # Склад
            "supplier":           "Y13",     # Поставщик — наименование (one-liner)
            "insurance_company":  "AR13",    # Страховая компания
            "account_corr":       "BC13",    # Корреспондирующий счёт — счёт, субсчёт
            "account_code":       "BL13",    # Корреспондирующий счёт — код анал. учёта
            "doc_accompanying":   "BV13",    # Номер документа — сопроводительный
            "doc_payment":        "CN13",    # Номер документа — платёжный
        },
        items_start_row=18,
        items_capacity=8,        # rows 18-25 on стр1
        items_max_rows=8,        # do NOT expand стр1 — overflow goes to стр2
        items_columns={
            "nomenclature":  "A",     # Материальные ценности — наименование
            "unit":          "AG",    # Единица измерения — наименование
            "qty_accepted":  "BB",    # Количество — принято
            "price":         "BJ",    # Цена
            "cost":          "BR",    # Сумма без учёта НДС
            "vat_amount":    "CA",    # Сумма НДС
            "cost_with_vat": "CJ",    # Всего с учётом НДС
        },
        # Continuation page стр2 — Итого row 20, signer at row 23
        continuation_sheet="стр2",
        continuation_items_start_row=6,
        continuation_items_capacity=14,    # rows 6-19; expanded via Rows.Insert if more
        continuation_items_columns={
            "nomenclature":  "A",
            "unit":          "AG",
            "qty_accepted":  "BB",
            "price":         "BJ",
            "cost":          "BR",
            "vat_amount":    "CA",
            "cost_with_vat": "CJ",
        },
        continuation_totals={
            "total_qty":           "BB20",
            "total_cost":          "BR20",
            "total_vat":           "CA20",
            "total_cost_with_vat": "CJ20",
        },
        continuation_header={
            # Pre-expansion addresses — _fill_continuation_sheet shifts them
            # down by N rows after Rows.Insert expansion.
            "receiver_position":   "J23",
            "receiver_name":       "AN23",
        },
        default_font_size=6,
    ),
    ExcelSpec(
        name="ttn-gor.xls",
        output_suffix="ТТН-горизонт",
        header={
            # Top mini-table — Грузоотправитель / Грузополучатель / Заказчик (УНП)
            "shipper_inn_top":     "AY4",
            "recipient_inn_top":   "BL4",
            "payer_inn_top":       "BY4",
            # Date row 14
            "date_day":            "C14",
            "date_month_name":     "J14",
            "date_year_2":         "AJ14",
            # Vehicle / trailer / waybill / driver
            "vehicle_make_plate":  "N15",
            "trailer_make_plate":  "CJ15",
            "waybill_no":          "EK15",
            "driver_full_name":    "K18",
            # Address block
            "payer":               "A22",
            "shipper":             "R24",
            "recipient":           "R26",
            "basis":               "Q28",
            "loading_point":       "BX28",
            "delivery_point":      "DQ28",   # Пункт разгрузки
            "redirection":         "P30",
            # ИТОГО row 38
            "total_qty":           "AW38",
            "total_cost":          "BS38",
            "total_vat":           "CO38",
            "total_cost_with_vat": "DB38",
            "total_package_count": "DP38",
            "total_weight":        "DZ38",
            # Прописью / цифрами for VAT (row 41) and Total (row 44)
            "vat_words":           "S41",
            "vat_digits":          "DD41",
            "total_words":         "W44",
            "total_digits":        "DD44",
        },
        items_start_row=37,
        items_capacity=1,
        items_max_rows=1,
        print_title_rows="$35:$36",
        items_columns={
            "nomenclature":  "A",
            "unit":          "AM",
            "qty_accepted":  "AW",
            "price":         "BG",
            "cost":          "BS",
            "vat_rate":      "CF",
            "vat_amount":    "CO",
            "cost_with_vat": "DB",
            "package_count": "DP",
            "weight":        "DZ",
        },
        # Wipe «руб.»/«коп.» labels (we write the digit form to wide DD merges).
        cells_to_clear=("CX41", "DX41", "CX44", "DX44"),
        secondary_sheet="Горизонтальная_2",
        secondary_header={
            "total_weight_words":        "R1",
            "total_package_count_words": "CW1",
            "released_by":               "R3",
            "carrier_signer":            "CV3",
            "handed_over_by":            "V6",
            "shipper_seal":              "BR6",
            "proxy_number_date":         "CN6",
            "proxy_issued_by":           "DP6",
            "receiver_seal":             "L9",
            "receiver_signer":           "CU9",
            "accompanying_docs":         "AE54",
        },
        # Appendix sheet 3
        appendix_sheet="Приложение (при необходимости)",
        appendix_items_start_row=6,
        appendix_items_capacity=25,
        appendix_items_columns={
            "nomenclature":  "A",
            "unit":          "B",
            "qty_accepted":  "C",
            "price":         "D",
            "cost":          "E",
            "vat_rate":      "F",
            "vat_amount":    "G",
            "cost_with_vat": "H",
            "package_count": "I",
            "weight":        "J",
        },
        appendix_header={
            "appendix_no":        "I1",
            "parent_doc_no":      "I2",
            "parent_doc_date":    "I3",
            "sheet_no":           "K32",
        },
        appendix_totals={
            "total_qty":           "C31",
            "total_cost":          "E31",
            "total_vat":           "G31",
            "total_cost_with_vat": "H31",
        },
        appendix_cells_to_clear=("G1", "G2", "G3", "J32"),
        appendix_parent_kind="ТТН-1",
    ),
    ExcelSpec(
        name="ttn-vert.xls",
        output_suffix="ТТН-вертикаль",
        header={
            # Top mini-table — ИНН-cells under AL1/AY1/BL1 headers
            "shipper_inn_top":     "AL4",
            "recipient_inn_top":   "AY4",
            "payer_inn_top":       "BL4",
            # Date row 16
            "date_day":            "C16",
            "date_month_name":     "J16",
            "date_year_2":         "AJ16",
            # Vehicle / trailer / waybill / driver
            "vehicle_make_plate":  "N17",
            "trailer_make_plate":  "BY17",
            "waybill_no":          "DW17",
            "driver_full_name":    "K19",
            # Address block (rows 22-31)
            "payer":               "AS22",
            "shipper":             "R25",
            "recipient":           "R27",
            "basis":               "Q29",
            "loading_point":       "BZ29",
            "delivery_point":      "DL29",
            "redirection":         "P31",
            # ИТОГО row 38
            "total_qty":           "AO38",
            "total_cost":          "BK38",
            "total_vat":           "CD38",
            "total_cost_with_vat": "CO38",
            "total_package_count": "DA38",
            "total_weight":        "DJ38",
            # Прописью / цифрами for VAT (row 40) and Total (row 43)
            "vat_words":           "S40",
            "vat_digits":          "DD40",
            "total_words":         "W43",
            "total_digits":        "DD43",
            # Row 45 — Всего масса / Всего кол-во грузовых мест
            "total_weight_words":        "O45",
            "total_package_count_words": "CI45",
            # Row 47 — Отпуск разрешил / Товар к перевозке принял
            "released_by":               "O47",
            "carrier_signer":            "CE47",
            # Row 50 — Сдал грузоотправитель / №пломбы / по доверенности / выданной
            "handed_over_by":            "S50",
            "shipper_seal":              "BG50",
            "proxy_number_date":         "BX50",
            "proxy_issued_by":           "DC50",
            # Row 53 — Принял грузополучатель + № пломбы
            "receiver_signer":           "CG53",
            "receiver_seal":             "DY53",
            # Row 96 — С товаром переданы документы
            "accompanying_docs":         "AE96",
        },
        items_start_row=37,
        items_capacity=1,
        items_max_rows=1,
        print_title_rows="$35:$36",
        items_columns={
            "nomenclature":  "A",
            "unit":          "AF",
            "qty_accepted":  "AO",
            "price":         "AX",
            "cost":          "BK",
            "vat_rate":      "BW",
            "vat_amount":    "CD",
            "cost_with_vat": "CO",
            "package_count": "DA",
            "weight":        "DJ",
        },
        cells_to_clear=("CX40", "DX40", "CX43", "DX43"),
        appendix_sheet="Приложение (при необходимости)",
        appendix_items_start_row=6,
        appendix_items_capacity=41,
        appendix_items_columns={
            "nomenclature":  "A",
            "unit":          "B",
            "qty_accepted":  "C",
            "price":         "D",
            "cost":          "E",
            "vat_rate":      "F",
            "vat_amount":    "G",
            "cost_with_vat": "H",
            "package_count": "I",
            "weight":        "J",
        },
        appendix_header={
            "appendix_no":        "I1",
            "parent_doc_no":      "I2",
            "parent_doc_date":    "I3",
            "sheet_no":           "K48",
        },
        appendix_totals={
            "total_qty":           "C47",
            "total_cost":          "E47",
            "total_vat":           "G47",
            "total_cost_with_vat": "H47",
        },
        appendix_cells_to_clear=("G1", "G2", "G3", "J48"),
        appendix_parent_kind="ТТН-1",
    ),
    ExcelSpec(
        name="tn-gor.xls",
        output_suffix="ТН-горизонт",
        header={
            # Top mini-table (рядом с шапкой бланка)
            "shipper_inn_top":     "AY4",
            "recipient_inn_top":   "BS4",
            # Date placeholder row 12: " __ " ____ 20 __ г.
            "date_day":            "BA12",
            "date_month_name":     "BH12",
            "date_year_2":         "CH12",
            # Full-width Грузоотправитель / Грузополучатель / Основание
            "shipper":             "T14",
            "recipient":           "T17",
            "basis":               "U20",
            # ИТОГО row 29 — totals computed from items
            "total_qty":           "BH29",
            "total_cost":          "CH29",
            "total_vat":           "DD29",
            "total_cost_with_vat": "DQ29",
            # «Всего сумма НДС» row 31, «Всего стоимость с НДС» row 34 —
            # digit form goes into wide DP merges, narrow single cells beside
            # them clip and are cleared via cells_to_clear.
            "vat_words":           "S31",
            "vat_digits":          "DP31",
            "total_words":         "Y34",
            "total_digits":        "DP34",
            "released_by":         "R37",       # Отпуск разрешил
            "handed_over_by":      "V40",       # Сдал грузоотправитель
            "carrier_signer":      "AV43",      # Товар к доставке принял
            "proxy_number_date":   "CY43",      # по доверенности № N от …
            "proxy_issued_by":     "DU43",      # выданной [организация]
            "receiver_signer":     "X48",       # Принял грузополучатель
            "accompanying_docs":   "AE51",      # С товаром переданы документы
        },
        items_start_row=26,
        items_capacity=3,
        items_max_rows=3,
        print_title_rows="$24:$25",
        items_columns={
            "nomenclature":  "A",
            "unit":          "AW",
            "qty_accepted":  "BH",
            "price":         "BT",
            "cost":          "CH",
            "vat_rate":      "CU",
            "vat_amount":    "DD",
            "cost_with_vat": "DQ",
        },
        appendix_sheet="Приложение (при необходимости)",
        appendix_items_start_row=6,
        appendix_items_capacity=29,
        appendix_items_columns={
            "nomenclature":  "A",
            "unit":          "B",
            "qty_accepted":  "C",
            "price":         "D",
            "cost":          "E",
            "vat_rate":      "F",
            "vat_amount":    "G",
            "cost_with_vat": "H",
        },
        appendix_header={
            "appendix_no":        "H1",
            "parent_doc_no":      "H2",
            "parent_doc_date":    "H3",
            "sheet_no":           "I36",
        },
        appendix_totals={
            "total_qty":           "C35",
            "total_cost":          "E35",
            "total_vat":           "G35",
            "total_cost_with_vat": "H35",
        },
        cells_to_clear=("DJ31", "EJ31", "DJ34", "EJ34"),
        appendix_cells_to_clear=("F1", "F2", "F3", "H36"),
    ),
    ExcelSpec(
        name="tn-vert.xls",
        output_suffix="ТН-вертикаль",
        header={
            "shipper_inn_top":     "Z5",
            "recipient_inn_top":   "AS5",
            "date_day":            "AB16",
            "date_month_name":     "AI16",
            "date_year_2":         "BI16",
            "shipper":             "T18",
            "recipient":           "T21",
            "basis":               "U24",
            "total_qty":           "AG33",
            "total_cost":          "AZ33",
            "total_vat":           "BT33",
            "total_cost_with_vat": "CC33",
            "vat_words":           "S35",
            "vat_digits":          "BX35",
            "total_words":         "W38",
            "total_digits":        "BX38",
            "released_by":         "R41",
            "handed_over_by":      "V44",
            "carrier_signer":      "X47",
            "proxy_number_date":   "BO47",
            "proxy_issued_by":     "CK47",
            "receiver_signer":     "X51",
            "accompanying_docs":   "AE56",
        },
        items_start_row=30,
        items_capacity=3,
        items_max_rows=3,
        print_title_rows="$28:$29",
        items_columns={
            "nomenclature":  "A",
            "unit":          "W",
            "qty_accepted":  "AG",
            "price":         "AP",
            "cost":          "AZ",
            "vat_rate":      "BK",
            "vat_amount":    "BT",
            "cost_with_vat": "CC",
        },
        appendix_sheet="Приложение (при необходимости)",
        appendix_items_start_row=6,
        appendix_items_capacity=42,
        appendix_items_columns={
            "nomenclature":  "A",
            "unit":          "B",
            "qty_accepted":  "C",
            "price":         "D",
            "cost":          "E",
            "vat_rate":      "F",
            "vat_amount":    "G",
            "cost_with_vat": "H",
        },
        appendix_header={
            "appendix_no":        "H1",
            "parent_doc_no":      "H2",
            "parent_doc_date":    "H3",
            "sheet_no":           "I49",
        },
        appendix_totals={
            "total_qty":           "C48",
            "total_cost":          "E48",
            "total_vat":           "G48",
            "total_cost_with_vat": "H48",
        },
        cells_to_clear=("BR35", "CR35", "BR38", "CR38"),
        appendix_cells_to_clear=("E1", "E2", "E3", "H49"),
    ),
    ExcelSpec(
        name="Акт расхождения.xls",
        output_suffix="АктРасхождения",
        header={
            "director_title":           "K2",
            "director_name":            "K3",
            "act_caption_verbose":      "D6",
            "vehicle_line":             "A10",
            "driver_line":              "E10",
            "received_at":              "A12",
            "discrepancy_line":         "C26",
            "discrepancy_amount_words": "B27",
            "supplier_rep":             "F33",
            "organization_rep":         "F34",
        },
        items_start_row=17,
        items_capacity=9,
        items_max_rows=200,
        print_title_rows="$15:$16",
        items_columns={
            "row_number":      "A",   # № п/п
            "doc_series":      "B",   # Серия накладной (mock: "ПН")
            "doc_number":      "C",   # Номер накладной (mock: order.number)
            "item_code":       "D",   # Код товара (артикул)
            "nomenclature":    "E",   # Наименование товара (merged E:F)
            "unit":            "G",   # Ед. измерения
            "qty_expected":    "H",   # Количество по документам
            "qty_actual":      "I",   # Количество фактически (qty ±1 за счёт расхождения)
            "qty_diff_plus":   "J",   # Расхождение +
            "qty_diff_minus":  "K",   # Расхождение -
            "note":            "L",   # Примечание
        },
    ),
    ExcelSpec(
        name="акт переоценки.xls",
        output_suffix="АктПереоценки",
        header={
            "director_title":      "Z5",
            "director_name":       "Z7",
            "approval_day":        "AA9",
            "approval_month":      "AC9",
            "approval_year":       "AI9",
            "as_of_date":          "T14",
            "commission_chairman": "H19",
            "commission_member1":  "H20",
            "commission_member2":  "H21",
            "order_day":           "O23",
            "order_month":         "Q23",
            "order_year":          "W23",
            "order_number":        "AA23",
            "original_cost":       "W34",
            "replacement_cost":    "S35",
            "residual_before":     "U36",
            "residual_after":      "V37",
            "commission_chairman_sig": "AA39",
            "commission_member1_sig":  "AA41",
            "commission_member2_sig":  "AA43",
        },
        items_start_row=28,
        items_capacity=6,
        items_max_rows=200,
        items_align_right=True,
        items_columns={
            "nomenclature":    "C",
            "qty_accepted":    "K",
            "amount":          "R",      # Сумма до переоценки
            "amount_revalued": "Y",      # Сумма после переоценки
        },
    ),
    ExcelSpec(
        name="Инвентаризационная опись.xls",
        output_suffix="ИнвентаризационнаяОпись",
        header={
            "organization":            "C8",
            "okud_code":               "AM11",
            "organization_activity":   "K13",
            "okpo_code":               "AM13",
            "warehouse":               "K15",
            "order_date":              "AM17",
            "order_number":            "AM19",
            "inventory_start":         "AM21",
            "inventory_end":           "AM23",
            # МО-лицо дублируется в верхнем (row 34) и нижнем (row 112) блоках.
            "mo_top_position":         "C34",
            "mo_top_name":             "W34",
            "total_qty_fact":          "X86",
            "total_amount_fact":       "AB86",
            "total_qty_accounted":     "AG86",
            "total_amount_accounted":  "AK86",
            "count_positions_words":   "M90",
            "total_units_words":       "N92",
            "total_amount_words_inv":  "K94",
            "chair_position":          "K97",
            "chair_name":              "AD97",
            "member1_position":        "K99",
            "member1_name":            "AD99",
            "member2_position":        "K101",
            "member2_name":            "AD101",
            "items_from_no":           "X106",
            "items_to_no":             "AE106",
            "mo_position":             "C112",
            "mo_name":                 "W112",
            "checker_position":        "C120",
            "checker_name":            "W120",
        },
        items_start_row=47,
        items_capacity=39,
        items_max_rows=200,
        print_title_rows="$41:$46",
        items_columns={
            "row_number":         "C",
            "nomenclature":       "E",
            "item_code":          "L",
            "unit":               "Q",
            "price":              "T",
            "qty_fact":           "X",
            "amount_fact":        "AB",
            "qty_accounted":      "AG",
            "amount_accounted":   "AK",
        },
    ),
]


WORD_SPECS: list[WordSpec] = [
    WordSpec(
        name="blank-invojs.doc",
        output_suffix="Инвойс",
        rules=[
            ("Дата отправки",
             "Дата отправки    {date}"),
            ("Export Reference(i.e. order no., invoice no., etc):",
             "Export Reference(i.e. order no., invoice no., etc): {doc_number}"),
            ("Получатель (полное имя и адрес):",
             "Получатель (полное имя и адрес):  {organization}"),
            ("Страна отправления:",
             "Страна отправления:  {country_of_export}"),
            ("Импортер – если отличается от",
             "Импортер – если отличается от Получателя:  {organization}"),
            ("Страна производства:",
             "Страна производства:  {country_of_manufacture}"),
            ("Страна назначения:",
             "Страна назначения:  {country_of_destination}"),
            ("Номер накладной:",
             "Номер накладной:  {doc_number}"),
            ("Валюта:",
             "Валюта:  {currency_or_default}"),
        ],
        cell_rules=[
            # Cell-scoped so the "ООО" find doesn't double-match after a
            # neighbouring "Получатель ООO …" replacement.
            CellRule(1, 2, 1, "ООО",   "{supplier_in_quotes}"),
            CellRule(1, 2, 1, "VAT №", "VAT №   {supplier_vat}"),
            CellRule(1, 9, 2, "кол-во",          "кол-во:   {total_qty} {primary_unit}",   post_items=True),
            CellRule(1, 9, 5, "Общий вес",       "Общий вес:   —",                          post_items=True),
            CellRule(1, 9, 7, "Общая стоимость", "Общая стоимость:   {total_amount}",       post_items=True),
            CellRule(1, 10, 1,
                     "_____________________________________",
                     "{signer_line}",
                     post_items=True),
            CellRule(1, 10, 1,
                     "Date/Дата: ___________________________",
                     "Date/Дата: {date}",
                     post_items=True),
        ],
        items_table=WordItemsTable(
            table_index=1,
            start_row=8,
            max_rows=1,
            insert_rows_if_overflow=True,
            columns={
                "row_number":    1,
                "package_count": 2,
                "packaging":     3,
                "nomenclature":  4,
                "qty_accepted":  5,
                "unit":          6,
                "weight":        7,
                "price":         8,
                "amount":        9,
            },
        ),
    ),
    # CMR: same data writes apply to all 3 language variants (RU+DE, RU+EN, RU).
    *(WordSpec(
        name=name,
        output_suffix=suffix,
        rules=[],
        # Layout: rows 2-6/8-12/22-23 have 2 cells (LEFT/RIGHT halves);
        # rows 14, 18-20 have 3 cells (sub-label, LEFT, RIGHT).
        table_writes=[
            # § 1 Отправитель — data cells [2..6, 1] (LEFT half under the label)
            WordTableWrite(1, 2,  1, "{supplier_in_quotes}"),
            WordTableWrite(1, 3,  1, "{supplier_address}"),
            WordTableWrite(1, 4,  1, "ИНН {supplier_vat}"),
            WordTableWrite(1, 5,  1, "тел.: {supplier_phone}"),
            WordTableWrite(1, 6,  1, "{country_of_export}"),
            # § 2 Получатель — data cells [8..12, 1] (LEFT half)
            WordTableWrite(1, 8,  1, "{organization}"),
            WordTableWrite(1, 9,  1, "{organization_address}"),
            WordTableWrite(1, 10, 1, "ИНН {organization_unp}"),
            WordTableWrite(1, 11, 1, "тел.: {organization_phone}"),
            WordTableWrite(1, 12, 1, "{country_of_destination}"),
            # § 16 Перевозчик — data cells [8..12, 2] (RIGHT half, mirrors §2)
            WordTableWrite(1, 8,  2, "{carrier}"),
            WordTableWrite(1, 9,  2, "{carrier_address}"),
            WordTableWrite(1, 10, 2, "ИНН {carrier_unp}"),
            WordTableWrite(1, 11, 2, "тел.: {carrier_phone}"),
            WordTableWrite(1, 12, 2, "{country_of_export}"),
            # § 3 Место разгрузки — [14,2], [15,2]  (row has 3 cells, col 2 = LEFT data)
            WordTableWrite(1, 14, 2, "{delivery_place}"),
            WordTableWrite(1, 15, 2, "{country_of_destination}"),
            # § 17 Последующий перевозчик — [16,2]
            WordTableWrite(1, 16, 2, "{next_carrier}"),
            # § 4 Место и дата погрузки — [18,2] / [19,2] / [20,2]
            WordTableWrite(1, 18, 2, "{loading_place}"),
            WordTableWrite(1, 19, 2, "{country_of_export}"),
            WordTableWrite(1, 20, 2, "{date}"),
            # § 5 Прилагаемые документы — [22,1], [23,1] (LEFT half, 2-cell rows)
            WordTableWrite(1, 22, 1, "Заказ поставщику №{doc_number} от {date}"),
            WordTableWrite(1, 23, 1, "Счёт-фактура"),
            # § 18 Оговорки и замечания перевозчика — [20,3]
            WordTableWrite(1, 20, 3, "{carrier_remarks}"),
            # § 19 ставка/итого — Валюта column (col 6, width 34) instead of the
            # narrow filler col 4 (width 16) which wraps "120,00" into "12 / 0, / 00".
            WordTableWrite(1, 34, 6, "{transport_rate}"),
            WordTableWrite(1, 40, 6, "{transport_total}"),
            # § 13 Указания отправителя — [34,1]
            WordTableWrite(1, 34, 1, "{shipper_instructions}"),
            # § 15 Условия оплаты — [43,2] (under "Франко")
            WordTableWrite(1, 43, 2, "{payment_terms}"),
            # § 20 Особые условия — [43,3]
            WordTableWrite(1, 43, 3, "{special_terms}"),
            # § 21 Составлен в — [45,3] place; [45,5] date
            WordTableWrite(1, 45, 3, "{loading_place}"),
            WordTableWrite(1, 45, 5, "{date}"),
            # § 25-26 Регистрационный номер / Марка ТС — row 52 has 3 cells:
            # [52,1] under §25 (truck + trailer plates, 2 lines), [52,2] under §26
            # (truck make only — trailer_make not exposed in mock), [52,3] right side.
            WordTableWrite(1, 52, 1, "{vehicle_plate}\r{trailer_plate}"),
            WordTableWrite(1, 52, 2, "{vehicle_make}"),
        ],
        items_table=WordItemsTable(
            table_index=1,
            start_row=25,
            max_rows=7,
            insert_rows_if_overflow=True,
            columns={
                "row_number":    1,   # § 6  Знаки и номера
                "package_count": 2,   # § 7  Количество мест
                "packaging":     3,   # § 8  Род упаковки
                "nomenclature":  4,   # § 9  Наименование груза
                "stat_no":       5,   # § 10 Статист №
                "weight":        6,   # § 11 Вес брутто, кг
                "volume":        7,   # § 12 Объём, м³
            },
        ),
        # § 22/23/24 time placeholders. Cell-scoped so the same Russian/DE/EN
        # label found in multiple blocks resolves to the correct side.
        cell_rules=[
            # § 22 Отправитель — arrival + departure at loading point [47,1]
            CellRule(1, 47, 1,
                     "Прибытие под погрузку                    час                  мин.",
                     "Прибытие под погрузку  {loading_arrival_hour} час  {loading_arrival_min} мин.",
                     post_items=True),
            CellRule(1, 47, 1,
                     "Убытие                         час                  мин.",
                     "Убытие  {loading_departure_hour} час  {loading_departure_min} мин.",
                     post_items=True),
            # § 24 Получатель — arrival in [47,3], departure in [48,3]
            CellRule(1, 47, 3,
                     "Прибытие под погрузку                    час                  мин.",
                     "Прибытие под погрузку  {unloading_arrival_hour} час  {unloading_arrival_min} мин.",
                     post_items=True),
            CellRule(1, 48, 3,
                     "Убытие                         час                  мин.",
                     "Убытие  {unloading_departure_hour} час  {unloading_departure_min} мин.",
                     post_items=True),
            # § 24 received-date — substring works for RU + EN variants.
            CellRule(1, 46, 3,
                     "„ _____ “ ______________20",
                     "{received_date}",
                     post_items=True),

            # Bilingual time labels (DE original, EN after variant gen).
            # Each variant's other-language label is a no-op (0 hits).
            # § 22 DE [47,1]
            CellRule(1, 47, 1,
                     "Ankunft für Beladung                         Uhr                  Min.",
                     "Ankunft für Beladung  {loading_arrival_hour} Uhr  {loading_arrival_min} Min.",
                     post_items=True),
            CellRule(1, 47, 1,
                     "Abfahrt                         Uhr                  Min.",
                     "Abfahrt  {loading_departure_hour} Uhr  {loading_departure_min} Min.",
                     post_items=True),
            # § 22 EN [47,1]
            CellRule(1, 47, 1,
                     "Arrival for loading                         H                  Min.",
                     "Arrival for loading  {loading_arrival_hour} H  {loading_arrival_min} Min.",
                     post_items=True),
            CellRule(1, 47, 1,
                     "Departure                         H                  Min.",
                     "Departure  {loading_departure_hour} H  {loading_departure_min} Min.",
                     post_items=True),
            # § 24 DE [47,3] + [48,3]
            CellRule(1, 47, 3,
                     "Ankunft für Beladung                         Uhr                  Min.",
                     "Ankunft für Beladung  {unloading_arrival_hour} Uhr  {unloading_arrival_min} Min.",
                     post_items=True),
            CellRule(1, 48, 3,
                     "Abfahrt                         Uhr                  Min.",
                     "Abfahrt  {unloading_departure_hour} Uhr  {unloading_departure_min} Min.",
                     post_items=True),
            # § 24 EN [47,3] + [48,3]
            CellRule(1, 47, 3,
                     "Arrival for loading                         H                  Min.",
                     "Arrival for loading  {unloading_arrival_hour} H  {unloading_arrival_min} Min.",
                     post_items=True),
            CellRule(1, 48, 3,
                     "Departure                         H                  Min.",
                     "Departure  {unloading_departure_hour} H  {unloading_departure_min} Min.",
                     post_items=True),
        ],
        # Signatory lines appended (not replaced) so the bilingual sibling
        # label (EN / DE) stays intact above.
        cell_appends=[
            CellAppend(1, 50, 1, "\r/{signer_sender}/",   post_items=True),
            CellAppend(1, 50, 2, "\r/{signer_carrier}/",  post_items=True),
            CellAppend(1, 50, 3, "\r/{signer_receiver}/", post_items=True),
        ],
        font_size=5.5,
    ) for name, suffix in (
        ("CMR Международная товарно-транспортная накладная.doc", "CMR"),     # RU + DE (original)
        ("CMR_EN.doc",                                            "CMR-EN"),  # RU + EN
        ("CMR_RU.doc",                                            "CMR-RU"),  # RU only
    )),
    WordSpec(
        name="списание.docx",
        output_suffix="Списание",
        rules=[
            ("ООО «_________________»",                "{organization_in_quotes}"),
            ("АКТ №___",                                "АКТ №{doc_number}"),
            ('"___" _________ _____ г.',                '"{date_day}" {date_month_name} 20{date_year_2} г.'),
            ('от "___" _________ ____ г.',              'от "{date_day}" {date_month_name} 20{date_year_2} г.'),
            ("установила, что для ________________________ _________ в _______ ____ г.",
             "установила, что для производственной деятельности на основном складе в {date_month_name} 20{date_year_2} г."),
            ("расходы в сумме   ______ руб. ___ коп.",
             "расходы в сумме {total_amount_rub} руб. {total_amount_kop} коп."),
            ("(_____________________ руб. ___ коп.)",
             "({total_amount_words_rub} руб. {total_amount_kop} коп.)"),
            ("следует отнести на затраты по реализации ___________________",
             "следует отнести на затраты по реализации {organization}"),
            ('"___" __________ ____ г.',                '"{date_day}" {date_month_name} 20{date_year_2} г.'),
        ],
        # Order: LONGEST first — REPLACE_ONE on the 31-underscore director
        # line BEFORE the 29-rule, otherwise the 29-rule eats the first 29
        # chars of the 31 and corrupts the 55-runs further down.
        ordered_rules=[
            ("_______________________________", ["{director_name}"]),
            ("_____________________________", [
                "{commission_chairman}",
                "{commission_member1}",
                "{commission_member2}",
                "",
            ]),
            ("_______________________________________________________", [
                "{commission_chairman}",
                "{commission_member1}",
                "{commission_member2}",
                "",
            ]),
        ],
        items_table=WordItemsTable(
            table_index=2,
            start_row=2,
            max_rows=6,
            insert_rows_if_overflow=True,
            columns={
                "row_number":   1,
                "nomenclature": 2,
                "unit":         3,
                "qty_accepted": 4,
                "price":        5,
                "amount":       6,
            },
        ),
    ),
]
