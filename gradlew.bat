@echo off
setlocal
set GRADLE_VERSION=8.2
where gradle >nul 2>&1
if %ERRORLEVEL% EQU 0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
echo Gradle %GRADLE_VERSION% is required. On Android/AIDE use the included ./gradlew script.
exit /b 1
