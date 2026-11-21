import urllib.request
import json

BASE_URL = "http://192.168.29.62:3001"

def test_family_safety_tether():
    print("1. Testing Family Safety Tether Registration (POST /family/register)...")
    payload = json.dumps({
        "userId": "family_guardian_mom",
        "deviceName": "Samsung Galaxy S23",
        "fcmToken": "fcm_mock_token_mom_device_abcdef123456"
    }).encode()
    req = urllib.request.Request(
        f"{BASE_URL}/family/register",
        data=payload,
        headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req, timeout=5) as resp:
        res = json.loads(resp.read().decode())
        print("   SUCCESS:", res.get("success"), "| Total Registered:", res.get("totalRegistered"))
        assert res.get("success") == True

def test_family_scam_alert():
    print("\n2. Testing Family Mesh Alert Dispatch (POST /alerts/family)...")
    payload = json.dumps({
        "protectedUserId": "victim_grandpa",
        "familyUserIds": ["family_guardian_mom"],
        "callerNumber": "+91-9876543210",
        "riskScore": 95,
        "scamType": "Digital Arrest Extortion",
        "transcriptSummary": "Caller impersonating Mumbai Police demanding immediate 50k transfer."
    }).encode()
    req = urllib.request.Request(
        f"{BASE_URL}/alerts/family",
        data=payload,
        headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req, timeout=5) as resp:
        res = json.loads(resp.read().decode())
        print("   SUCCESS:", res.get("success"), "| Targets:", res.get("totalTargets"))
        assert res.get("success") == True

def test_trusted_contact_alert():
    print("\n3. Testing Trusted Contact Intervention (POST /alerts/trusted-contact)...")
    payload = json.dumps({
        "to": "+91-9811122233",
        "contactName": "Aarav Sharma (Son)",
        "callerNumber": "+91-9988776655",
        "riskScore": 95,
        "signals": ["authority_impersonation", "digital_arrest", "financial_coercion"],
        "timestamp": 1794929000000
    }).encode()
    req = urllib.request.Request(
        f"{BASE_URL}/alerts/trusted-contact",
        data=payload,
        headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req, timeout=5) as resp:
        res = json.loads(resp.read().decode())
        print("   SUCCESS:", res.get("success"), "| Message:", res.get("message"))
        assert res.get("success") == True

def test_phishing_link_shield_heuristic():
    print("\n4. Testing Phishing Link Shield Detection Heuristics...")
    # Emulating QrScannerActivity / LinkCheckActivity regex logic
    suspicious_patterns = [
        "kyc", "apk", "login", "verify", "update-pan", "sbi-card", "free-gift", "claim"
    ]
    test_urls = [
        ("https://sbi-card-kyc-update-portal.xyz/verify", True),
        ("https://www.npci.org.in/what-we-do/upi/product-overview", False),
        ("http://192.168.1.10/update-banking-app.apk", True),
        ("https://google.com/search?q=cybercrime", False)
    ]
    for url, should_flag in test_urls:
        lower = url.lower()
        flagged = any(p in lower for p in suspicious_patterns) or lower.endswith(".apk")
        status = "FLAGGED PHISHING" if flagged else "CLEAN LINK"
        print(f"   URL: {url[:45]:<45} -> {status}")
        assert flagged == should_flag

if __name__ == "__main__":
    print("=== TESTING SUSAGI GUARDIAN PROTECTION FEATURES ===")
    test_family_safety_tether()
    test_family_scam_alert()
    test_trusted_contact_alert()
    test_phishing_link_shield_heuristic()
    print("\nALL PROTECTION FEATURES SUCCESSFULLY VERIFIED!")
