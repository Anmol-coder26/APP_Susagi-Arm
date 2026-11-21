@echo off
setlocal enabledelayedexpansion

echo ===============================================================================
echo                SUSAGI GUARDIAN - AUTOMATED SERVICE LAUNCHER
echo ===============================================================================

cd /d "%~dp0"

echo [1/5] Checking prerequisites...
where node >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Node.js is not found on PATH. Please install Node.js v18+.
    pause
    exit /b 1
)

where python >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Python is not found on PATH. Please install Python 3.8+.
    pause
    exit /b 1
)

echo [2/5] Detecting LAN IP address and generating QR codes...
python generate_qr.py
if %ERRORLEVEL% NEQ 0 (
    echo [WARN] generate_qr.py failed, defaulting to local IP.
)

echo [3/5] Starting Node.js Backend Server (Port 3001)...
netstat -ano | findstr :3001 >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [INFO] Port 3001 is already active.
) else (
    start "SuSagi Backend Server (Port 3001)" /b node server\server.js
    timeout /t 2 /nobreak >nul
)

echo [4/5] Starting APK Distribution Server (Port 8080)...
if not exist "dist" mkdir dist
netstat -ano | findstr :8080 >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [INFO] Port 8080 is already active.
) else (
    start "SuSagi APK Distribution (Port 8080)" /b python -m http.server 8080 -d dist
    timeout /t 2 /nobreak >nul
)

echo [5/5] Running Service Health Verification...
python tools\test_services.py
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ===============================================================================
    echo                     ALL SUSAGI SERVICES ARE OPERATIONAL
    echo ===============================================================================
    echo Backend API:       http://localhost:3001/health
    echo APK Distribution:  http://localhost:8080/
    echo QR Code File:      dist\susagi_apk_qr.png
    echo ===============================================================================
) else (
    echo [WARN] Some health checks failed. Check terminal output for details.
)

pause
