@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

:: ====================================================================
:: WMS Project - Code Quality Helper Script
:: ====================================================================

:menu
cls
echo.
echo ╔════════════════════════════════════════════════════════════════╗
echo ║       WMS Project - Code Quality Management Tool              ║
echo ╚════════════════════════════════════════════════════════════════╝
echo.
echo Выберите действие:
echo.
echo  [1] Запустить все проверки качества (allCodeQuality)
echo  [2] Автоматически исправить PMD ошибки (pmdFix)
echo  [3] Исправить форматирование (spotlessApply)
echo  [4] Полное исправление (spotless + pmd) и проверка
echo  [5] Посмотреть отчеты PMD
echo  [6] Посмотреть отчеты SpotBugs
echo  [7] Только PMD проверка
echo  [8] Только SpotBugs проверка
echo  [9] Проверить форматирование
echo  [0] Выход
echo.
echo ────────────────────────────────────────────────────────────────
set /p choice="Введите номер (0-9): "

if "%choice%"=="1" goto run_all_quality
if "%choice%"=="2" goto run_pmd_fix
if "%choice%"=="3" goto run_spotless_apply
if "%choice%"=="4" goto run_full_fix
if "%choice%"=="5" goto view_pmd_reports
if "%choice%"=="6" goto view_spotbugs_reports
if "%choice%"=="7" goto run_pmd_only
if "%choice%"=="8" goto run_spotbugs_only
if "%choice%"=="9" goto run_spotless_check
if "%choice%"=="0" goto end
goto menu

:run_all_quality
cls
echo.
echo ╔════════════════════════════════════════════════════════════════╗
echo ║          Запуск всех проверок качества...                     ║
echo ╚════════════════════════════════════════════════════════════════╝
echo.
call gradlew.bat allCodeQuality
echo.
echo Проверка завершена!
pause
goto menu

:run_pmd_fix
cls
echo.
echo ╔════════════════════════════════════════════════════════════════╗
echo ║          Автоматическое исправление PMD ошибок...             ║
echo ╚════════════════════════════════════════════════════════════════╝
echo.
echo Выберите сервис:
echo  [1] document-service
echo  [2] product-service
echo  [3] warehouse-service
echo  [4] organization-service
echo  [5] SSOService
echo  [6] Все сервисы
echo  [0] Назад
echo.
set /p service="Введите номер: "

if "%service%"=="1" call gradlew.bat :document-service:pmdFix
if "%service%"=="2" call gradlew.bat :product-service:pmdFix
if "%service%"=="3" call gradlew.bat :warehouse-service:pmdFix
if "%service%"=="4" call gradlew.bat :organization-service:pmdFix
if "%service%"=="5" call gradlew.bat :SSOService:pmdFix
if "%service%"=="6" (
    call gradlew.bat document-service:pmdFix product-service:pmdFix warehouse-service:pmdFix organization-service:pmdFix SSOService:pmdFix
)
if "%service%"=="0" goto menu

echo.
echo Исправление завершено!
pause
goto menu

:run_spotless_apply
cls
echo.
echo ╔════════════════════════════════════════════════════════════════╗
echo ║          Исправление форматирования кода...                   ║
echo ╚════════════════════════════════════════════════════════════════╝
echo.
call gradlew.bat allSpotlessApply
echo.
echo Форматирование применено!
pause
goto menu

:run_full_fix
cls
echo.
echo ╔════════════════════════════════════════════════════════════════╗
echo ║   Полное исправление: форматирование + PMD + проверка         ║
echo ╚════════════════════════════════════════════════════════════════╝
echo.
echo Шаг 1/3: Исправление форматирования...
call gradlew.bat allSpotlessApply
echo.
echo Шаг 2/3: Исправление PMD ошибок...
call gradlew.bat document-service:pmdFix product-service:pmdFix warehouse-service:pmdFix organization-service:pmdFix SSOService:pmdFix
echo.
echo Шаг 3/3: Запуск проверок...
call gradlew.bat allCodeQuality
echo.
echo ╔════════════════════════════════════════════════════════════════╗
echo ║              Полное исправление завершено! ✓                  ║
echo ╚════════════════════════════════════════════════════════════════╝
pause
goto menu

:view_pmd_reports
cls
echo.
echo ╔════════════════════════════════════════════════════════════════╗
echo ║                    Отчеты PMD                                  ║
echo ╚════════════════════════════════════════════════════════════════╝
echo.
echo Выберите сервис:
echo  [1] document-service
echo  [2] product-service
echo  [3] warehouse-service
echo  [4] organization-service
echo  [5] SSOService
echo  [0] Назад
echo.
set /p service="Введите номер: "

if "%service%"=="1" set "report_path=document-service\build\reports\pmd\main.html"
if "%service%"=="2" set "report_path=product-service\build\reports\pmd\main.html"
if "%service%"=="3" set "report_path=warehouse-service\build\reports\pmd\main.html"
if "%service%"=="4" set "report_path=organization-service\build\reports\pmd\main.html"
if "%service%"=="5" set "report_path=SSOService\build\reports\pmd\main.html"
if "%service%"=="0" goto menu

if exist "%report_path%" (
    echo Открываю отчет: %report_path%
    start "" "%report_path%"
) else (
    echo.
    echo ⚠ Отчет не найден! Запустите проверки сначала:
    echo   gradlew.bat allCodeQuality
    pause
)
goto menu

:view_spotbugs_reports
cls
echo.
echo ╔════════════════════════════════════════════════════════════════╗
echo ║                  Отчеты SpotBugs                               ║
echo ╚════════════════════════════════════════════════════════════════╝
echo.
echo Выберите сервис:
echo  [1] document-service
echo  [2] product-service
echo  [3] warehouse-service
echo  [4] organization-service
echo  [5] SSOService
echo  [0] Назад
echo.
set /p service="Введите номер: "

if "%service%"=="1" set "report_path=document-service\build\reports\spotbugs\main\spotbugs.html"
if "%service%"=="2" set "report_path=product-service\build\reports\spotbugs\main\spotbugs.html"
if "%service%"=="3" set "report_path=warehouse-service\build\reports\spotbugs\main\spotbugs.html"
if "%service%"=="4" set "report_path=organization-service\build\reports\spotbugs\main\spotbugs.html"
if "%service%"=="5" set "report_path=SSOService\build\reports\spotbugs\main\spotbugs.html"
if "%service%"=="0" goto menu

if exist "%report_path%" (
    echo Открываю отчет: %report_path%
    start "" "%report_path%"
) else (
    echo.
    echo ⚠ Отчет не найден! Запустите проверки сначала:
    echo   gradlew.bat allCodeQuality
    pause
)
goto menu

:run_pmd_only
cls
echo.
echo ╔════════════════════════════════════════════════════════════════╗
echo ║              Запуск только PMD проверки...                     ║
echo ╚════════════════════════════════════════════════════════════════╝
echo.
call gradlew.bat pmdMain pmdTest
echo.
echo PMD проверка завершена!
pause
goto menu

:run_spotbugs_only
cls
echo.
echo ╔════════════════════════════════════════════════════════════════╗
echo ║            Запуск только SpotBugs проверки...                  ║
echo ╚════════════════════════════════════════════════════════════════╝
echo.
call gradlew.bat spotbugsMain
echo.
echo SpotBugs проверка завершена!
pause
goto menu

:run_spotless_check
cls
echo.
echo ╔════════════════════════════════════════════════════════════════╗
echo ║          Проверка форматирования кода...                       ║
echo ╚════════════════════════════════════════════════════════════════╝
echo.
call gradlew.bat allSpotlessCheck
echo.
echo Проверка завершена!
pause
goto menu

:end
cls
echo.
echo ╔════════════════════════════════════════════════════════════════╗
echo ║                    До свидания! 👋                             ║
echo ╚════════════════════════════════════════════════════════════════╝
echo.
exit /b 0

