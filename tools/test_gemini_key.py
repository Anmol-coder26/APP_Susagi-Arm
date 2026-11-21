import urllib.request
import urllib.error
import json

key = "AIzaSyCABJUA9KjCD1apHbnozky9VHfxqywU4EY"
url = f"https://generativelanguage.googleapis.com/v1beta/models?key={key}"

try:
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode())
        models = [m["name"] for m in data.get("models", [])]
        print(f"SUCCESS! Models found: {len(models)}")
        for m in models:
            if "flash" in m or "pro" in m:
                print(" -", m)
except urllib.error.HTTPError as e:
    err_body = e.read().decode()
    print(f"HTTPError {e.code}: {err_body}")
except Exception as e:
    print("Error:", e)
