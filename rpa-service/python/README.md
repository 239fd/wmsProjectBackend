# RPA: 1С → Office

Парсит «Заказы поставщикам» из 1С:Управление торговлей 11.2 (демо-база `utdemo`) по 3 статусам (`Готов к поступлению`, `Ожидается поступление`, `Ожидается оплата`) и заполняет 8 шаблонов на каждый заказ (Excel: Приходный ордер М-4, ТТН × 2, ТН × 2; Word: Инвойс, Акт приёмки, CMR).

## Что нужно на машине

- **Windows 10/11** — pywinauto и win32com работают только тут
- **Python 3.12** (`winget install -e --id Python.Python.3.12` или с python.org). Версии 3.9–3.13 тоже должны заводиться
- **Microsoft Office** (Excel + Word, desktop, не web) — для COM-автоматизации заполнения шаблонов
- **1С:Предприятие 8.3.x** установлена, в списке инфобаз есть нужная (по умолчанию ищется «Демонстрационная база»)
- **Активная интерактивная Windows-сессия** — pywinauto двигает реальный курсор и нажимает клавиши, headless-режим невозможен

## Установка

```powershell
cd python
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

## Запуск

```powershell
# Кодировка консоли — один раз в сессии, чтобы кириллица в логах читалась
chcp 65001

# Смок-тест: без 1С, на фикстурном заказе. Проверяет что Office-цепочка работает
python -m rpa --smoke

# Полный сценарий: парсит 1С → заполняет шаблоны
python -m rpa
```

Файлы лягут в `..\output\<timestamp>\` — на каждый заказ 14 шаблонов (.xlsx + .docx), плюс **`supplies.json`** с данными всех поставок в схеме `samples/supply_full.json` (поставка → поставщик → товары).

В полном режиме скрипт сам найдёт исполняемый `1cv8st.exe` или `1cestart.exe`, запустит 1С с нужной инфобазой и подождёт окно. Если 1С уже открыта — подключится к существующему окну.

## Конфигурация через env

| Переменная | По умолчанию | Что делает |
|---|---|---|
| `RPA_ONEC_EXECUTABLE` | автодетект (`1cv8st.exe` под `D:\1C\<version>\bin`, иначе `1cestart.exe`) | Путь к запускаемому файлу 1С |
| `RPA_ONEC_INFOBASE` | `Демонстрационная база` | Имя инфобазы (передаётся как `/IBName="..."`) |
| `RPA_ONEC_WINDOW_REGEX` | `.*(Управление торговлей\|1С:Предприятие\|Демонстрационная база).*` | Регекс заголовка окна 1С |
| `RPA_ONEC_ATTACH_IF_RUNNING` | `true` | Подключаться к уже открытой 1С вместо запуска новой |
| `RPA_MAX_ORDERS` | `20` | Сколько заказов брать за прогон |
| `RPA_TEMPLATES_DIR` | `<project>/templates` | Где лежат шаблоны |
| `RPA_OUTPUT_DIR` | `<project>/output` | Куда складывать результат |
| `RPA_OFFICE_VISIBLE` | `true` | Показывать окна Excel/Word при заполнении (полезно отключить для batch-режима) |

Пример прогона в фоне с другой инфобазой:

```powershell
$env:RPA_ONEC_INFOBASE = "MyProd"
$env:RPA_OFFICE_VISIBLE = "false"
$env:RPA_OUTPUT_DIR = "D:\rpa-out"
python -m rpa
```

## Архитектура

```
rpa/
├── __main__.py         entry, --smoke режим
├── config.py           env-переменные, OneCConfig, OfficeConfig
├── models.py           PurchaseOrder, OrderItem (frozen dataclass)
├── templates_spec.py   EXCEL_SPECS, WORD_SPECS — маппинг ячеек и Find&Replace правил
├── onec_parser.py      pywinauto: навигация 1С, чтение журнала и формы заказа
├── excel_filler.py     win32com Excel: открыть шаблон → заполнить ячейки → SaveAs xlsx
├── word_filler.py      win32com Word: Find&Replace + table-write → SaveAs2 docx
├── office_filler.py    оркестратор Office, RunReport(files, errors)
└── supply_exporter.py  PurchaseOrder → JSON в схеме samples/supply_full.json
```

## Что вытаскивается из 1С

На один заказ:
- **Шапка**: `Номер`, `Дата`, `Сумма`, `Поставщик`, `Текущее состояние`, `Срок выполнения`, `Валюта`, `Операция`, `% оплаты` / `% поступления` / `% долга`
- **Из формы документа**: `Организация` (через UIA ValuePattern на ComboBox)
- **Таблица «Товары»**: `Номенклатура`, `Ед.изм.`, `Количество`, `Цена`, `Сумма` на строку

См. `rpa/models.py` для актуальной структуры `PurchaseOrder`/`OrderItem`.

## Что НЕ вытаскивается

- Расширенные данные о поставщиках (УНП, контакты, адрес) — карточка Партнёра не раскрывается стабильно через UIA в UT 11.2
- Подробности номенклатуры (SKU, штрихкод, вес, объём) — аналогично
- Склад → в Заказе поставщику UT 11.2 его нет на видимой шапке

В `supplies.json` соответствующие поля проставлены `null` / `""`. Если эти данные критичны — нужен 1С COM Connector или HTTP-сервис инфобазы (это уже не RPA).

## JSON-экспорт

Схема описана в `samples/supply_full.json` — там видно все поля верхнего уровня:
поставка (`external_id`, `status`, `currency`, `total_amount`...), поставщик (`name`,
+ опциональные `unp`/`phone`/`email`/`address`), товары (`product.name`,
`expected_qty`, `unit_price`, `total_amount`).

Парсер заполняет ровно те поля, которые видны в журнале и форме заказа. Все
остальные сохраняют `null` или пустую строку — конечный потребитель (БД-loader)
сам решает, оставлять их пустыми или докладывать из других источников.
