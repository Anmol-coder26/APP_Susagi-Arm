import urllib.request
import urllib.error
import json
import os
import sys

def get_gemini_key():
    # 1. Check local.properties
    props_path = r"C:\Users\Tanya\.gemini\antigravity\scratch\FINAL_APP\local.properties"
    if os.path.exists(props_path):
        with open(props_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line.startswith("GEMINI_API_KEY="):
                    val = line.split("=", 1)[1].strip()
                    if val and val != "YOUR_GEMINI_API_KEY":
                        return val, "local.properties"

    # 2. Check environment variable
    env_val = os.environ.get("GEMINI_API_KEY", "").strip()
    if env_val and env_val != "YOUR_GEMINI_API_KEY":
        return env_val, "environment"

    # NOTE: Firebase project key fallback explicitly removed per Phase 2 security policy.
    return "", "none"

def mask_key(k):
    if not k:
        return "[NOT CONFIGURED / EMPTY]"
    if len(k) <= 8:
        return "****"
    return k[:4] + "..." + k[-4:]

def test_gemini_direct():
    api_key, source = get_gemini_key()
    print("=== GEMINI DIRECT CLOUD API DIAGNOSTIC ===")
    print(f"Key Source: {source}")
    print(f"Masked Key: {mask_key(api_key)}")

    if not api_key:
        print("STATUS: Direct Gemini API key is not configured in local.properties.")
        print("ACTION REQUIRED: To enable direct Google Cloud AI calls, add GEMINI_API_KEY=<key> to local.properties.")
        print("NOTE: Production calls will use backend-managed credentials or on-device heuristic fallback.\n")
        return False

    url = f"https://generativelanguage.googleapis.com/v1beta/models?key={api_key}"
    try:
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, timeout=8) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            models = [m["name"] for m in data.get("models", [])]
            print(f"AUTHENTICATION: SUCCESS (HTTP 200)")
            flash_models = [m for m in models if "flash" in m]
            print(f"Accessible Flash Models ({len(flash_models)}): {flash_models[:4]}")
            return True
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8")
        print(f"AUTHENTICATION FAILED: HTTP {e.code}")
        try:
            err_json = json.loads(err_body)
            msg = err_json.get("error", {}).get("message", err_body)
            print(f"Error Message: {msg}")
        except Exception:
            print(f"Raw: {err_body[:150]}")
        return False
    except Exception as e:
        print(f"NETWORK ERROR: {e}")
        return False

def test_backend_risk_evaluate():
    print("=== BACKEND RISK EVALUATION & SHIELD TEST ===")
    dialogue = """
[LOCAL (User)]: Hello, how are you?
[REMOTE (Caller)]: I am calling from cybercrime police. Do not disconnect. Transfer 50,000 rupees immediately.
"""
    backend_url = "http://192.168.29.62:3001/risk/evaluate"
    payload = json.dumps({"transcript": dialogue}).encode("utf-8")
    req = urllib.request.Request(
        backend_url,
        data=payload,
        headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            print("BACKEND RESPONSE:")
            print(f"  Risk Score:    {data.get('risk_score')}/100")
            print(f"  Scammer Role:  {data.get('scammer_role')}")
            print(f"  Victim Role:   {data.get('victim_role')}")
            print(f"  Tactics:       {data.get('tactics')}")
            print(f"  Source:        {data.get('source')}")
            print(f"  Is Fallback:   {data.get('is_fallback')}")
            print(f"  Explanation:   {data.get('explanation')}")
            return True
    except Exception as e:
        print(f"Backend evaluate failed: {e}")
        return False

if __name__ == "__main__":
    test_gemini_direct()
    test_backend_risk_evaluate()
