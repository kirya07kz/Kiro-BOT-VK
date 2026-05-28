@echo off
echo ========================================
echo    Kiro Bot VK - Сборка Установочного APK
echo ========================================

REM Проверка наличия Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ОШИБКА: Java не найдена! Убедитесь, что JDK установлен и добавлен в PATH.
    pause
    exit /b 1
)

REM Проверка наличия Android SDK
if not exist "%ANDROID_HOME%" (
    echo ОШИБКА: ANDROID_HOME не установлен.
    echo Установите Android SDK и настройте переменную окружения ANDROID_HOME
    pause
    exit /b 1
)

echo Очистка предыдущих сборок...
call gradlew clean > release_log.txt 2>&1

echo Сборка APK (Signed)...
echo Лог сборки записывается в release_log.txt...
call gradlew assembleDebug >> release_log.txt 2>&1

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo    СБОРКА ЗАВЕРШЕНА УСПЕШНО!
    echo ========================================
    echo.
    echo Готовый к установке APK файл находится в:
    echo app\build\outputs\apk\debug\app-debug.apk
    echo.
    echo Этот файл можно отправлять и устанавливать на телефон.
    echo.
) else (
    echo.
    echo ========================================
    echo    ОШИБКА СБОРКИ!
    echo ========================================
    echo.
    echo Подробности ошибки сохранены в файле release_log.txt
    start release_log.txt
)

pause