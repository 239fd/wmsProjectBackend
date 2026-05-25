from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
TEMPLATES_DIR = Path(os.environ.get("RPA_TEMPLATES_DIR", PROJECT_ROOT / "templates"))
OUTPUT_DIR = Path(os.environ.get("RPA_OUTPUT_DIR", PROJECT_ROOT / "output"))


def _env(name: str, default: str) -> str:
    value = os.environ.get(name)
    return (value if value is not None else default).strip()


def _env_bool(name: str, default: bool) -> bool:
    raw = os.environ.get(name)
    if raw is None:
        return default
    return raw.strip().lower() in ("1", "true", "yes", "on")


def _env_int(name: str, default: int) -> int:
    raw = os.environ.get(name)
    if raw is None or not raw.strip():
        return default
    try:
        return int(raw.strip())
    except ValueError:
        return default


_DEFAULT_ONEC_PATHS = (
    r"C:\Program Files\1cv8\common\1cestart.exe",
    r"C:\Program Files (x86)\1cv8\common\1cestart.exe",
)


def _default_onec_executable() -> str:
    for candidate in _DEFAULT_ONEC_PATHS:
        if Path(candidate).is_file():
            return candidate
    for root in (Path(r"D:\1C"), Path(r"C:\1C"), Path(r"C:\Program Files\1cv8")):
        if not root.exists():
            continue
        for version_dir in sorted(root.iterdir(), reverse=True):
            bin_dir = version_dir / "bin"
            if not bin_dir.is_dir():
                continue
            for exe_name in ("1cv8st.exe", "1cv8ct.exe", "1cv8t.exe", "1cv8.exe"):
                candidate = bin_dir / exe_name
                if candidate.is_file():
                    return str(candidate)
    return _DEFAULT_ONEC_PATHS[0]


def _default_onec_launch_args(executable: str) -> tuple[str, ...]:
    if Path(executable).name.lower().startswith("1cv8"):
        return ("ENTERPRISE",)
    return ()


_RESOLVED_EXECUTABLE = _env("RPA_ONEC_EXECUTABLE", _default_onec_executable())


@dataclass(frozen=True, slots=True)
class OneCConfig:
    executable: str = _RESOLVED_EXECUTABLE
    launch_args: tuple[str, ...] = _default_onec_launch_args(_RESOLVED_EXECUTABLE)
    infobase: str = _env("RPA_ONEC_INFOBASE", "Демонстрационная база")
    window_title_regex: str = _env(
        "RPA_ONEC_WINDOW_REGEX",
        r".*(Управление торговлей|1С:Предприятие|Демонстрационная база).*",
    )
    attach_if_running: bool = _env_bool("RPA_ONEC_ATTACH_IF_RUNNING", True)
    status_column: str = "Текущее состояние"
    target_statuses: tuple[str, ...] = (
        "Готов к поступлению",
        "Ожидается поступление",
        "Ожидается оплата",
    )
    list_columns: tuple = (
        "Номер", "Дата", "Сумма", "Поставщик", "Текущее состояние",
        "Срок выполнения", "% оплаты", "% поступления", "% долга",
        "Валюта", "Операция",
    )
    max_orders: int = _env_int("RPA_MAX_ORDERS", 20)


@dataclass(frozen=True, slots=True)
class OfficeConfig:
    keep_office_visible: bool = _env_bool("RPA_OFFICE_VISIBLE", True)


ONEC = OneCConfig()
OFFICE = OfficeConfig()
