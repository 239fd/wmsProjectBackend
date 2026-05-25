"""PurchaseOrder fixtures for template filler iteration.

Sizes 1/5/10/50; CLI `--mock <size>`. Counterparty/transport/signatory mocks
live here (not in filler `_context()`) so the shape matches the future API
payload — see `models.Counterparty` / `Transport` / etc.
"""
from __future__ import annotations

from datetime import date
from decimal import Decimal

from .models import (
    Counterparty,
    DeliveryContext,
    OrderItem,
    PurchaseOrder,
    Signatories,
    Transport,
)


_CATALOG = [
    ("Холодильник Атлант X-1000",              "шт.", Decimal("450.00")),
    ("Кофеварка JACOBS Aroma EX-300",          "шт.", Decimal("89.50")),
    ("Микроволновая печь LG MH-7042",          "шт.", Decimal("125.30")),
    ("Чайник электрический Bosch TWK-3A011",   "шт.", Decimal("42.10")),
    ("Утюг Tefal FV-2530",                     "шт.", Decimal("38.75")),
    ("Пылесос Samsung SC-4520",                "шт.", Decimal("215.00")),
    ("Тостер Philips HD-2581",                 "шт.", Decimal("31.99")),
    ("Блендер Braun MQ-5025",                  "шт.", Decimal("76.40")),
    ("Стиральная машина Atlant CMA-50C82",     "шт.", Decimal("520.00")),
    ("Соковыжималка Bosch MES-3500",           "шт.", Decimal("88.20")),
    ("Мультиварка Redmond RMC-M90",            "шт.", Decimal("99.99")),
    ("Кухонные весы Polaris PKS-0102",         "шт.", Decimal("18.50")),
    ("Электрогриль Tefal GC-2050",             "шт.", Decimal("110.00")),
    ("Кофемашина Delonghi ECAM-22.110",        "шт.", Decimal("780.00")),
    ("Хлебопечка Mystery MBM-1208",            "шт.", Decimal("142.00")),
    ("Йогуртница Brand 4001",                  "шт.", Decimal("45.80")),
    ("Фен Rowenta CV-5712",                    "шт.", Decimal("55.30")),
    ("Эпилятор Braun Silk-Epil 5",             "шт.", Decimal("89.00")),
    ("Машинка для стрижки Moser 1400",         "шт.", Decimal("67.20")),
    ("Электробритва Philips S5050",            "шт.", Decimal("145.60")),
]


_SUPPLIER = Counterparty(
    name='ООО «Электротехника-Бел»',
    inn="100123456",
    address="220123, г. Минск, ул. Кошевая, 12",
    phone="+375 17 123-45-67",
    email="sales@electro-bel.by",
)

_ORGANIZATION = Counterparty(
    name='ООО «Торговый дом Комплексный»',
    inn="100987654",
    address="220100, г. Минск, пр. Независимости, 4",
    phone="+375 17 222-33-44",
    email="info@td-complex.by",
)

_CARRIER = Counterparty(
    name='ООО «АвтоЛогистик»',
    inn="192345678",
    address="220070, г. Минск, ул. Стебенева, 8",
    phone="+375 17 500-10-20",
    email="dispatch@autologistic.by",
)

_TRANSPORT = Transport(
    vehicle_make="MAN TGA 18.480",
    vehicle_plate="AA 1234-7",
    trailer_plate="AB 5678-7",
    trailer_make="Schmitz SKO 24",
    loading_arrival_hour="08", loading_arrival_min="30",
    loading_departure_hour="10", loading_departure_min="15",
    unloading_arrival_hour="14", unloading_arrival_min="00",
    unloading_departure_hour="14", unloading_departure_min="45",
    waybill_number="ПЛ-2026/0518",
    driver_full_name="Сидоров Сергей Сергеевич",
    seal_number="СБ-1234567",
)

_SIGNATORIES = Signatories(
    sender="Иванов И.И., менеджер по экспорту",
    carrier="Сидоров С.С., водитель-экспедитор",
    receiver="Петров П.П., кладовщик",
    released_by="Сидорова О.А., начальник склада",
    director="Кравцов А.В.",
    director_title="Заведующий складом",
    commission_chairman="Морозова Е.П., главный бухгалтер",
    commission_members=(
        "Лебедев Д.С., нач. отдела закупок",
        "Григорьева М.А., товаровед",
    ),
)

_DELIVERY = DeliveryContext(
    loading_place="Склад поставщика, г. Минск",
    delivery_place="Основной склад, г. Минск",
    country_of_export="Республика Беларусь",
    country_of_manufacture="Республика Беларусь",
    country_of_destination="Республика Беларусь",
    next_carrier="—",
    carrier_remarks="—",
    payment_terms="Оплата по факту",
    special_terms="—",
    shipper_instructions="Доставка партии холодильников по заказу",
    transport_rate="120,00",
    transport_total="120,00",
    proxy_number="123",
    proxy_date="18.05.2026",
    proxy_issued_by='ООО «Торговый дом Комплексный»',
    accompanying_docs="Счёт-фактура, сертификат качества, упаковочный лист",
    redirection="—",
    structural_unit="Отдел снабжения",
    operation_code="01",
    insurance_company="—",
    account_corr="10/1",
    account_code="10101",
    doc_accompanying="ТТН-1 №МОСК-001",
    doc_payment="ПП №2026/0518",
    sklad_card_no="К-001",
)


def _items(n: int) -> list[OrderItem]:
    items: list[OrderItem] = []
    for i in range(n):
        product, unit, price = _CATALOG[i % len(_CATALOG)]
        qty = Decimal(str((i % 7) + 1))
        amount = (qty * price).quantize(Decimal("0.01"))
        items.append(OrderItem(
            row_number=i + 1,
            nomenclature=f"{product} (партия {i + 1})",
            unit=unit,
            quantity=qty,
            price=price,
            amount=amount,
        ))
    return items


def fixture(size: int) -> PurchaseOrder:
    if size < 1:
        raise ValueError("fixture size must be >= 1")
    items = _items(size)
    total = sum((it.amount for it in items), Decimal("0"))
    return PurchaseOrder(
        number=f"MOCK-{size:03d}",
        date=date(2026, 5, 18),
        supplier=_SUPPLIER.name,
        organization=_ORGANIZATION.name,
        warehouse="Основной склад",
        status="Готов к поступлению",
        total_amount=total,
        items=items,
        currency="BYN",
        operation="Закупка у поставщика",
        supplier_details=_SUPPLIER,
        organization_details=_ORGANIZATION,
        carrier=_CARRIER,
        transport=_TRANSPORT,
        signatories=_SIGNATORIES,
        delivery=_DELIVERY,
    )


def all_sizes() -> list[PurchaseOrder]:
    return [fixture(n) for n in (1, 5, 10, 50)]
