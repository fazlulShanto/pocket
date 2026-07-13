@echo off
setlocal EnableExtensions EnableDelayedExpansion

cd /d "%~dp0"

set "APK=%CD%\app\build\outputs\apk\release\app-release.apk"

echo.
echo [1/3] Running unit tests...
call gradlew.bat :app:testDebugUnitTest
if errorlevel 1 goto :tests_failed

echo.
echo [2/3] Building the release APK...
call gradlew.bat :app:assembleRelease
if errorlevel 1 goto :build_failed

if not exist "%APK%" goto :apk_missing

set "SDK_DIR=%ANDROID_SDK_ROOT%"
if not defined SDK_DIR set "SDK_DIR=%ANDROID_HOME%"

if not defined SDK_DIR (
    for /f "tokens=1,* delims==" %%A in ('findstr /B /C:"sdk.dir=" local.properties 2^>nul') do (
        set "SDK_DIR=%%B"
    )
)

if defined SDK_DIR set "SDK_DIR=%SDK_DIR:\:=:%"
if defined SDK_DIR set "SDK_DIR=%SDK_DIR:\\=\%"

set "APKSIGNER="
if defined SDK_DIR if exist "!SDK_DIR!\build-tools" (
    for /f "delims=" %%F in ('where /R "!SDK_DIR!\build-tools" apksigner.bat 2^>nul') do (
        set "APKSIGNER=%%F"
    )
)

if not defined APKSIGNER goto :apksigner_missing

echo.
echo [3/3] Verifying the APK signature...
call "!APKSIGNER!" verify "%APK%" >nul 2>&1
if errorlevel 1 (
    call "!APKSIGNER!" verify --verbose --print-certs "%APK%"
    goto :signature_failed
)
echo Signature verification passed.

set "APK_HASH="
for /f "usebackq delims=" %%H in (`powershell.exe -NoProfile -Command "(Get-FileHash -LiteralPath $env:APK -Algorithm SHA256).Hash"`) do (
    set "APK_HASH=%%H"
)

echo.
echo ============================================================
echo SIGNED RELEASE APK READY
echo APK:    %APK%
echo SHA256: !APK_HASH!
echo ============================================================
echo.
echo Transfer this exact file without modifying or repackaging it.
exit /b 0

:tests_failed
echo.
echo ERROR: Unit tests failed. Release APK was not generated.
exit /b 1

:build_failed
echo.
echo ERROR: Gradle release build failed.
exit /b 1

:apk_missing
echo.
echo ERROR: Gradle completed, but the expected APK was not found:
echo %APK%
exit /b 1

:apksigner_missing
echo.
echo ERROR: apksigner.bat was not found in the Android SDK build-tools directory.
echo Checked SDK: !SDK_DIR!
exit /b 1

:signature_failed
echo.
echo ERROR: APK signature verification failed. Do not distribute this APK.
exit /b 1
