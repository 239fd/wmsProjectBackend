Param(
    [int]$Port = 8060,
    [string]$BindAddress = "0.0.0.0"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Definition
Set-Location "$root\python"

function Test-PythonExe {
    Param([string]$Exe)
    if (-not $Exe) { return $false }
    try {
        $out = & $Exe --version 2>&1
        if ($LASTEXITCODE -ne 0) { return $false }
        if ($out -match 'Python\s+\d+\.\d+\.\d+') { return $true }
        return $false
    } catch {
        return $false
    }
}

function Find-Python {
    # 1) py launcher (preferred on Windows) - try py -3.12, py -3.11, py -3
    foreach ($v in @("-3.12", "-3.11", "-3")) {
        $cmd = Get-Command py -ErrorAction SilentlyContinue
        if ($cmd) {
            try {
                $out = & py $v --version 2>&1
                if ($LASTEXITCODE -eq 0 -and $out -match 'Python\s+\d+\.\d+\.\d+') {
                    return @("py", $v)
                }
            } catch {}
        }
    }

    # 2) python.exe on PATH (skip Microsoft Store stub which lives in WindowsApps)
    $candidates = Get-Command python -All -ErrorAction SilentlyContinue
    foreach ($c in $candidates) {
        if ($c.Source -like "*\WindowsApps\*") { continue }   # skip MS Store proxy
        if (Test-PythonExe $c.Source) {
            return @($c.Source)
        }
    }

    return $null
}

if (-not (Test-Path ".\.venv\Scripts\python.exe")) {
    # Wipe any partial venv left from a previous failed run
    if (Test-Path ".\.venv") {
        Write-Host "Removing broken .venv (no python.exe inside)..."
        Remove-Item -Recurse -Force ".\.venv"
    }

    $py = Find-Python
    if ($null -eq $py) {
        Write-Error @"
Python 3.11+ не найден.
- 'python' на PATH сейчас указывает на Microsoft Store stub - его надо отключить:
  Settings -> Apps -> App execution aliases -> выключить python.exe и python3.exe.
- 'py' launcher сломан (указывает на удалённую установку).
Поставь Python 3.12 с https://www.python.org/downloads/windows/
с галочками 'Add python.exe to PATH' и 'py launcher', потом открой НОВУЮ консоль.
"@
        exit 1
    }

    Write-Host ("Creating venv with: {0}" -f ($py -join " "))
    & $py[0] $py[1..($py.Length - 1)] -m venv .venv
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path ".\.venv\Scripts\python.exe")) {
        Write-Error "python -m venv .venv failed (exit=$LASTEXITCODE). Удали .venv и проверь python вручную."
        exit 1
    }

    Write-Host "Installing requirements..."
    & .\.venv\Scripts\python.exe -m pip install --upgrade pip
    if ($LASTEXITCODE -ne 0) { Write-Error "pip upgrade failed"; exit 1 }
    & .\.venv\Scripts\python.exe -m pip install -r ..\requirements.txt
    if ($LASTEXITCODE -ne 0) { Write-Error "pip install -r requirements.txt failed"; exit 1 }
}

$url = "http://{0}:{1}" -f $BindAddress, $Port
Write-Host "Starting RPA service on $url"
& .\.venv\Scripts\python.exe -m uvicorn rpa.api:app --host $BindAddress --port $Port
