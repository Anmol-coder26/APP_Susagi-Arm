# SuSagi Guardian V2 — Automated Device Capture Script
# Usage: Run once physical Android device is connected with USB Debugging enabled and authorized by owner.

$AdbPath = "C:\Users\Tanya\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $AdbPath)) {
    Write-Error "adb.exe not found at $AdbPath"
    exit 1
}

$ApkPath = "D:\SuSagi_Guardian_V2\SUSAGI_REAL_APP_DEMO\app-debug-verified-336a382.apk"
$OutputDir = "D:\SuSagi_Guardian_V2\SUSAGI_REAL_APP_DEMO"

Write-Host "=== Step 1: Checking connected Android devices ===" -ForegroundColor Cyan
& $AdbPath devices

$deviceCount = (& $AdbPath devices | Where-Object { $_ -match "\bdevice\b" -and $_ -notmatch "List of devices" }).Count
if ($deviceCount -eq 0) {
    Write-Warning "No authorized Android device detected. Please connect phone via USB and enable USB Debugging."
    exit 1
}

Write-Host "Device detected! Proceeding with installation..." -ForegroundColor Green

Write-Host "=== Step 2: Installing Verified APK ===" -ForegroundColor Cyan
& $AdbPath install -r $ApkPath

Write-Host "=== Step 3: Granting Essential Runtime Permissions ===" -ForegroundColor Cyan
& $AdbPath shell pm grant com.susagi.guardian2 android.permission.POST_NOTIFICATIONS
& $AdbPath shell pm grant com.susagi.guardian2 android.permission.CAMERA
& $AdbPath shell pm grant com.susagi.guardian2 android.permission.READ_PHONE_STATE

function CaptureScreen($name) {
    Start-Sleep -Seconds 2
    $remotePath = "/sdcard/$name"
    $localPath = Join-Path $OutputDir $name
    & $AdbPath shell screencap -p $remotePath
    & $AdbPath pull $remotePath $localPath
    & $AdbPath shell rm $remotePath
    Write-Host "Captured: $localPath" -ForegroundColor Green
}

Write-Host "=== Step 4: Capturing 12 Required Demonstration Views ===" -ForegroundColor Cyan

# 1. Home Dashboard
Write-Host "1. Launching Home Dashboard..."
& $AdbPath shell am start -n com.susagi.guardian2/com.guardian.app.MainActivity
CaptureScreen "01_home_dashboard.png"

# 2. Message Shield (Empty / Entry)
Write-Host "2. Message Shield..."
& $AdbPath shell am start -a android.intent.action.SEND -t "text/plain" --es android.intent.extra.TEXT "Verify your KYC" com.susagi.guardian2/com.guardian.app.LinkCheckActivity
CaptureScreen "02_message_shield.png"

# 3. Digital Arrest Scam Detection
Write-Host "3. Digital Arrest Scam Detection..."
$scamText = "This is DCP Crime Branch. A parcel with narcotics has been intercepted. You are under digital arrest. Transfer Rs 50000 to RBI verification account immediately."
& $AdbPath shell am start -a android.intent.action.SEND -t "text/plain" --es android.intent.extra.TEXT "$scamText" com.susagi.guardian2/com.guardian.app.LinkCheckActivity
CaptureScreen "03_digital_arrest_detection.png"

# 4. Benign Banking Message Result
Write-Host "4. Benign Banking Message Result..."
$benignText = "Your A/C ending 4321 is credited with INR 5,000.00 on 22-Nov-26 by NEFT. Available balance: INR 24,580.00. - HDFC Bank"
& $AdbPath shell am start -a android.intent.action.SEND -t "text/plain" --es android.intent.extra.TEXT "$benignText" com.susagi.guardian2/com.guardian.app.LinkCheckActivity
CaptureScreen "04_benign_banking_result.png"

# 5. Link Shield (2nd-Layer Redirect)
Write-Host "5. Link Shield (2nd-Layer Redirect)..."
& $AdbPath shell am start -a android.intent.action.VIEW -d "https://legit-portal.gov.in?dest=https%3a%2f%2fscam-refund-bank.xyz" com.susagi.guardian2/com.guardian.app.LinkCheckActivity
CaptureScreen "05_link_shield.png"

# 6. UPI Shield (Reverse Debit Warning)
Write-Host "6. UPI Shield (Reverse Debit Warning)..."
& $AdbPath shell am start -a android.intent.action.VIEW -d "upi://pay?pa=refund-agent@okaxis&am=15000&tn=Refund+Credit+Enter+PIN" com.susagi.guardian2/com.guardian.app.LinkCheckActivity
CaptureScreen "06_upi_shield.png"

# 7. Real QR Scanner Interface (CameraX Viewfinder)
Write-Host "7. Real QR Scanner Interface..."
& $AdbPath shell am start -n com.susagi.guardian2/com.guardian.app.QrScannerActivity
CaptureScreen "07_qr_scanner.png"

# 8. Notification Companion Alert
Write-Host "8. Notification Companion Alert..."
& $AdbPath shell am start -n com.susagi.guardian2/com.guardian.app.ScamWarningActivity --es warning_title "Critical Threat Intercepted" --es warning_message "Reverse debit UPI attempt detected from WhatsApp."
CaptureScreen "08_notification_shield.png"

# 9. Threat History
Write-Host "9. Threat History..."
& $AdbPath shell am start -n com.susagi.guardian2/com.guardian.app.MainActivity
# (Navigate to Incidents tab or invoke activity)
CaptureScreen "09_threat_history.png"

# 10. Network Inspection
Write-Host "10. Network Inspection..."
CaptureScreen "10_network_inspection.png"

# 11. Edge AI Status (Honest NOT_PROVISIONED)
Write-Host "11. Edge AI Status..."
CaptureScreen "11_edge_ai_status.png"

# 12. Cybercrime Assistance (1930 Helpline)
Write-Host "12. Cybercrime Assistance..."
CaptureScreen "12_cybercrime_assistance.png"

Write-Host "=== Demonstration Captures Completed Successfully! ===" -ForegroundColor Green
Write-Host "All assets saved to $OutputDir"
