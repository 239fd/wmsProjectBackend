from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date
from decimal import Decimal


@dataclass(frozen=True, slots=True)
class OrderItem:
    row_number: int
    nomenclature: str
    unit: str
    quantity: Decimal
    price: Decimal
    amount: Decimal
    vat_rate: Decimal | None = None
    vat_amount: Decimal | None = None
    weight: Decimal | None = None
    volume: Decimal | None = None
    hs_code: str = ""
    package_count: Decimal | None = None
    old_price: Decimal | None = None
    new_price: Decimal | None = None
    qty_expected: Decimal | None = None
    qty_actual: Decimal | None = None


@dataclass(frozen=True, slots=True)
class Counterparty:
    """One legal entity: supplier, organisation, or carrier. The 1C UIA
    parser leaves most fields empty (partner cards aren't reachable)."""
    name: str = ""
    full_name: str = ""
    inn: str = ""
    address: str = ""
    phone: str = ""
    email: str = ""


@dataclass(frozen=True, slots=True)
class Transport:
    """Vehicle + transit times (CMR §22, §24, §25, §26)."""
    vehicle_make: str = ""
    vehicle_plate: str = ""
    trailer_plate: str = ""
    trailer_make: str = ""
    loading_arrival_hour: str = ""
    loading_arrival_min: str = ""
    loading_departure_hour: str = ""
    loading_departure_min: str = ""
    unloading_arrival_hour: str = ""
    unloading_arrival_min: str = ""
    unloading_departure_hour: str = ""
    unloading_departure_min: str = ""
    waybill_number: str = ""
    driver_full_name: str = ""
    seal_number: str = ""


@dataclass(frozen=True, slots=True)
class Signatories:
    """Signatories for shipping (sender/carrier/receiver), warehouse release
    (ТН «Отпуск разрешил»), and commission roles for Акт переоценки /
    Инвентаризационная опись / Акт расхождения."""
    sender: str = ""
    carrier: str = ""
    receiver: str = ""
    released_by: str = ""
    director: str = ""
    director_title: str = ""
    commission_chairman: str = ""
    commission_members: tuple[str, ...] = ()


@dataclass(frozen=True, slots=True)
class DeliveryContext:
    """Logistics + commercial fields shared by shipping documents."""
    loading_place: str = ""
    delivery_place: str = ""
    country_of_export: str = ""
    country_of_manufacture: str = ""
    country_of_destination: str = ""
    next_carrier: str = ""
    carrier_remarks: str = ""
    payment_terms: str = ""
    special_terms: str = ""
    shipper_instructions: str = ""
    transport_rate: str = ""
    transport_total: str = ""
    proxy_number: str = ""
    proxy_date: str = ""
    proxy_issued_by: str = ""
    accompanying_docs: str = ""
    redirection: str = ""
    structural_unit: str = ""
    operation_code: str = ""
    insurance_company: str = ""
    account_corr: str = ""
    account_code: str = ""
    doc_accompanying: str = ""
    doc_payment: str = ""
    sklad_card_no: str = ""


@dataclass(frozen=True, slots=True)
class PurchaseOrder:
    number: str
    date: date
    supplier: str
    organization: str
    warehouse: str
    status: str
    total_amount: Decimal
    items: list[OrderItem] = field(default_factory=list)
    expected_date: date | None = None
    currency: str = ""
    payment_pct: Decimal | None = None
    receipt_pct: Decimal | None = None
    debt_pct: Decimal | None = None
    operation: str = ""
    supplier_details: Counterparty | None = None
    organization_details: Counterparty | None = None
    carrier: Counterparty | None = None
    transport: Transport | None = None
    signatories: Signatories | None = None
    delivery: DeliveryContext | None = None
