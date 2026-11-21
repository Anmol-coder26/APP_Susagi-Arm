import urllib.request
import urllib.parse
import json

BASE_URL = "http://192.168.29.62:3001"

def test_health():
    print("1. Testing GET /health ...")
    req = urllib.request.Request(f"{BASE_URL}/health")
    with urllib.request.urlopen(req, timeout=5) as resp:
        data = json.loads(resp.read().decode())
        print("   STATUS:", resp.status, data.get("status"))
        print("   SERVICE:", data.get("service"))
        print("   AGORA MODE:", data.get("agora", {}).get("mode"))
        assert resp.status == 200

def test_agora_token():
    print("\n2. Testing GET /agora/token ...")
    req = urllib.request.Request(f"{BASE_URL}/agora/token?channelName=test_room_hackathon")
    with urllib.request.urlopen(req, timeout=5) as resp:
        data = json.loads(resp.read().decode())
        print("   STATUS:", resp.status)
        print("   CHANNEL:", data.get("channelName"))
        print("   TOKEN:", repr(data.get("token")))
        assert resp.status == 200

def test_call_lifecycle():
    print("\n3. Testing Call Hosting, Joining, and Termination ...")
    # A) Host Call
    host_payload = json.dumps({"calleeUid": "device_b_user"}).encode()
    host_req = urllib.request.Request(
        f"{BASE_URL}/calls",
        data=host_payload,
        headers={"Content-Type": "application/json", "x-mock-uid": "device_a_user"}
    )
    with urllib.request.urlopen(host_req, timeout=5) as resp:
        call = json.loads(resp.read().decode())
        call_id = call.get("callId")
        channel = call.get("channel")
        print("   HOSTED CALL:", call_id, "on channel:", channel)
        assert call_id is not None

    # B) Join / Accept Call
    join_req = urllib.request.Request(
        f"{BASE_URL}/calls/{call_id}/accept",
        data=b"{}",
        headers={"Content-Type": "application/json", "x-mock-uid": "device_b_user"}
    )
    with urllib.request.urlopen(join_req, timeout=5) as resp:
        join_res = json.loads(resp.read().decode())
        state = join_res.get("call", {}).get("state")
        print("   JOINED / ACCEPTED CALL:", call_id, "State:", state)
        assert state == "ACCEPTED"

    # C) End Call
    end_req = urllib.request.Request(
        f"{BASE_URL}/calls/{call_id}/end",
        data=b"{}",
        headers={"Content-Type": "application/json", "x-mock-uid": "device_a_user"}
    )
    with urllib.request.urlopen(end_req, timeout=5) as resp:
        end_res = json.loads(resp.read().decode())
        end_state = end_res.get("call", {}).get("state")
        print("   ENDED CALL:", call_id, "State:", end_state)
        assert end_state == "ENDED"

def test_risk_evaluate():
    print("\n4. Testing POST /risk/evaluate ...")
    dialogue = """
[LOCAL (User)]: Hello, how are you?
[REMOTE (Caller)]: I am calling from cybercrime police. Do not disconnect. Transfer 50,000 rupees immediately.
"""
    payload = json.dumps({"transcript": dialogue}).encode()
    req = urllib.request.Request(
        f"{BASE_URL}/risk/evaluate",
        data=payload,
        headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        result = json.loads(resp.read().decode())
        print("   EVALUATION RESULT:")
        print("   Score:", result.get("risk_score"))
        print("   Scammer:", result.get("scammer_role"))
        print("   Victim:", result.get("victim_role"))
        print("   Source:", result.get("source"))
        print("   Tactics:", result.get("tactics"))
        print("   Explanation:", result.get("explanation"))
        assert result.get("risk_score") >= 60

if __name__ == "__main__":
    print("=== SUSAGI GUARDIAN BACKEND SERVICE VERIFICATION ===")
    test_health()
    test_agora_token()
    test_call_lifecycle()
    test_risk_evaluate()
    print("\nALL BACKEND SERVICES VERIFIED SUCCESSFULLY!")
