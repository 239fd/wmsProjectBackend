# RPA Service

HTTP-микросервис на Python для интеграции с 1С УТ 11.2 и MS Office.
Заменяет удалённую WinAppDriver-реализацию (`OfficeDocumentBot`,
`OneCWinAppExtractorImpl`) в `document-service` и `product-service`.

**Требования к хосту:** Windows + установленный MS Office (Excel/Word) +
запущенная 1С УТ 11.2 (для парсинга). Не запускается в Docker/k8s
поскольку завязан на COM-автоматизацию.

## Структура

```
rpa-service/
├── python/
│   └── rpa/
│       ├── api.py                 # FastAPI HTTP-обёртка (этот микросервис)
│       ├── excel_filler.py        # Excel-генератор (win32com)
│       ├── word_filler.py         # Word-генератор (win32com)
│       ├── onec_parser.py         # 1С UIA-парсер (pywinauto)
│       ├── supply_exporter.py     # supplies.json / sales.json export
│       ├── templates_spec.py      # маппинг типов документов в шаблоны
│       ├── models.py              # PurchaseOrder / Counterparty / etc.
│       └── fixtures.py            # тестовые данные
├── templates/                     # .xls / .doc Office-шаблоны
├── requirements.txt
└── README.md
```

## Запуск (Windows-хост)

```powershell
cd backend\rpa-service\python
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r ..\requirements.txt
uvicorn rpa.api:app --host 0.0.0.0 --port 8060
```

## HTTP API

| Метод | Путь | Назначение |
|---|---|---|
| GET | `/health` | Liveness + список поддерживаемых типов документов |
| POST | `/fill/{doc_type}` | Сгенерировать документ; возвращает bytes (`.xlsx` / `.docx`) |
| POST | `/parse/supplies` | Распарсить Заказы поставщикам из 1С → supplies.json |
| POST | `/parse/sales` | Распарсить Заказы клиентов из 1С (без закрытых) → sales.json |

`{doc_type}` ∈ `receipt-order`, `inventory-report`, `revaluation-act`,
`write-off-act`, `invoice`, `transport-note` (param `layout`),
`waybill` (param `layout`), `cmr` (param `language`), `discrepancy-act`.

`picking-list`, `placement-list`, `receipt-act`, `release-order`,
`shipment-order` → HTTP 501 (нет шаблона).

## Конфиг WMS-стороны

`document-service` и `product-service`:

```properties
rpa.python.enabled=true
rpa.python.base-url=http://win-rpa-host:8060
rpa.python.timeout-seconds=120
```

## CLI (без HTTP)

Все Python-модули можно запускать как обычно — CLI остался:

```powershell
python -m rpa --mock all --doc Инвойс
python -m rpa --parse-only
python -m rpa --sales-only
```
