@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

:: ============================================
:: Скрипт полного цикла: Сборка -> Установка -> Запуск
:: ============================================

:: --- НАСТРОЙКИ SDK (Если скрипт не находит ADB) ---
:: Оставьте пустым для автопоиска или укажите путь к папке SDK вручную
:: Пример: set "MANUAL_SDK_PATH=C:\Users\Kirill\AppData\Local\Android\Sdk"
set "MANUAL_SDK_PATH=C:\adb_run\bin"

:: --- НАСТРОЙКИ ПОДПИСИ (Заполните своими данными) ---
:: 1. Создание ключа (Android Studio на русском):
::    Новая версия: Меню "Сборка" -> "Создать подписанный пакет / APK" -> APK
::    Старая версия: Меню "Сборка" -> "Generate Signed APK" (Создать подписанный APK)
:: 2. Положите файл ключа (например, keystore.jks) в папку проекта
set "KEYSTORE_PATH=keystore.jks"
set "KEYSTORE_PASS=123456"
set "KEY_ALIAS=key0"
set "KEY_PASS=123456"
:: ----------------------------------------------------

echo.
echo === Полный цикл: Сборка, Установка и Запуск ===
echo.

REM Проверка наличия Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ОШИБКА] Java не найдена! Убедитесь, что JDK установлен и добавлен в PATH.
    pause
    exit /b 1
)

:: 1. Поиск ADB (Приоритет: Android Studio)
echo [1/5] Поиск ADB...

set "ADB_PATH="

:: 0. Ручной путь
if defined MANUAL_SDK_PATH (
    if exist "%MANUAL_SDK_PATH%\platform-tools\adb.exe" (
        set "ADB_PATH=%MANUAL_SDK_PATH%\platform-tools\adb.exe"
    ) else if exist "%MANUAL_SDK_PATH%\adb.exe" (
        set "ADB_PATH=%MANUAL_SDK_PATH%\adb.exe"
    )
)

:: 1. Поиск в системном PATH
if not defined ADB_PATH (
    where adb >nul 2>&1
    if !ERRORLEVEL! EQU 0 set "ADB_PATH=adb"
)

:: 2. Стандартные пути
if not defined ADB_PATH (
    if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set "ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    ) else if exist "%LOCALAPPDATA%\Android\android-sdk\platform-tools\adb.exe" (
        set "ADB_PATH=%LOCALAPPDATA%\Android\android-sdk\platform-tools\adb.exe"
    ) else if exist "%ANDROID_HOME%\platform-tools\adb.exe" (
    set "ADB_PATH=%ANDROID_HOME%\platform-tools\adb.exe"
    ) else if exist "C:\Android\Sdk\platform-tools\adb.exe" (
    set "ADB_PATH=C:\Android\Sdk\platform-tools\adb.exe"
    ) else if exist "C:\Program Files (x86)\Android\android-sdk\platform-tools\adb.exe" (
    set "ADB_PATH=C:\Program Files (x86)\Android\android-sdk\platform-tools\adb.exe"
    )
)

if not defined ADB_PATH (
    echo [ОШИБКА] ADB не найден!
    echo Скрипт не нашел adb.exe в стандартных папках.
    echo.
    echo Попробуйте найти папку "platform-tools" вручную:
    echo 1. Найдите, куда установлена Android Studio или SDK.
    echo 2. Откройте этот файл (build_and_run.bat) в блокноте.
    echo 3. Впишите путь в переменную MANUAL_SDK_PATH в начале файла.
    pause
    exit /b 1
)

echo [OK] ADB найден: %ADB_PATH%
echo.

:: 2. Сборка проекта
echo [2/5] Сборка проекта...
echo Лог сборки записывается в build_log.txt...

if not exist "gradlew.bat" (
    echo [ОШИБКА] Не найден файл gradlew.bat! Проверьте, что вы в папке проекта.
    pause
    exit /b 1
)

set "KEYSTORE_FULL_PATH=%~dp0%KEYSTORE_PATH%"

if exist "%KEYSTORE_PATH%" (
    echo [INFO] Найден ключ подписи. Собираем Release версию...
    call gradlew clean assembleRelease -Pandroid.injected.signing.store.file="%KEYSTORE_FULL_PATH%" -Pandroid.injected.signing.store.password="%KEYSTORE_PASS%" -Pandroid.injected.signing.key.alias="%KEY_ALIAS%" -Pandroid.injected.signing.key.password="%KEY_PASS%" > build_log.txt 2>&1
    set "APK_PATH=app\build\outputs\apk\release\app-release.apk"
) else (
    echo [INFO] Ключ подписи не найден. Собираем Debug версию...
    call gradlew clean assembleDebug > build_log.txt 2>&1
    set "APK_PATH=app\build\outputs\apk\debug\app-debug.apk"
)

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ОШИБКА] Не удалось собрать проект!
    echo.
    echo === ЛОГ ОШИБКИ ===
    type build_log.txt
    pause
    exit /b 1
)

echo [OK] Сборка успешно завершена.
echo.

if not exist "%APK_PATH%" (
    echo [ОШИБКА] Файл APK не найден после сборки!
    pause
    exit /b 1
)

:: 3. Проверка устройств
echo [3/5] Проверка подключенных устройств...

"%ADB_PATH%" devices > temp_devices.txt 2>&1

set "DEVICE_COUNT=0"
for /f "skip=1 tokens=2" %%i in (temp_devices.txt) do (
    if "%%i"=="device" set /a DEVICE_COUNT+=1
)

if !DEVICE_COUNT! EQU 0 (
    echo [ОШИБКА] Устройства не найдены!
    echo Подключите устройство и включите отладку по USB.
    del temp_devices.txt
    pause
    exit /b 1
)

echo [OK] Найдено устройств: !DEVICE_COUNT!
del temp_devices.txt
echo.

:: 4. Установка APK
echo [4/5] Установка APK...

"%ADB_PATH%" install -r "%APK_PATH%"

if %ERRORLEVEL% NEQ 0 (
    echo [ОШИБКА] Ошибка установки!
    pause
    exit /b 1
)

echo [OK] APK успешно установлен!
echo.

:: 5. Запуск приложения
echo [5/5] Запуск приложения...
"%ADB_PATH%" shell am start -n com.vkbot.manager/.MainActivity

echo.
pause