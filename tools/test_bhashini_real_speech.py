import base64
import json
import urllib.request
import urllib.error
import time

ENDPOINT = "https://dhruva-api.bhashini.gov.in/services/inference/pipeline"
INFERENCE_API_KEY = "TYcnvGMZSCZnzg0QIyh-97KPNxJynJ5b4w_fY5tZa8jG_btp6zcsGQPGMmx303JC"
PIPELINE_ID = "64392f96daac500b55c543cd"

def test_asr(wav_path, language="en"):
    print(f"\n--- Testing ASR on {wav_path} (lang={language}) ---")
    with open(wav_path, "rb") as f:
        wav_bytes = f.read()

    print(f"Loaded {len(wav_bytes)} bytes from {wav_path}")
    audio_base64 = base64.b64encode(wav_bytes).decode("utf-8")

    payload = {
        "pipelineTasks": [
            {
                "taskType": "asr",
                "config": {
                    "language": {
                        "sourceLanguage": language
                    },
                    "audioFormat": "wav",
                    "samplingRate": 16000
                }
            }
        ],
        "inputData": {
            "audio": [
                {
                    "audioContent": audio_base64
                }
            ]
        }
    }

    req_data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        ENDPOINT,
        data=req_data,
        headers={
            "Content-Type": "application/json",
            "Authorization": INFERENCE_API_KEY,
            "x-pipeline-id": PIPELINE_ID,
            "User-Agent": "SuSagi-Guardian/2.0"
        }
    )

    start_time = time.time()
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            latency = (time.time() - start_time) * 1000
            body = resp.read().decode("utf-8")
            data = json.loads(body)
            print(f"HTTP {resp.status} in {latency:.1f}ms")
            
            transcript = ""
            for pipe_task in data.get("pipelineResponse", []):
                for out in pipe_task.get("output", []):
                    transcript = out.get("source", "").strip()

            print(f"RAW TRANSCRIPT RETURNED: '{transcript}'")
            return transcript, latency
    except urllib.error.HTTPError as e:
        latency = (time.time() - start_time) * 1000
        print(f"HTTPError {e.code} in {latency:.1f}ms: {e.read().decode('utf-8')}")
        return None, latency
    except Exception as e:
        latency = (time.time() - start_time) * 1000
        print(f"Network error in {latency:.1f}ms: {e}")
        return None, latency

if __name__ == "__main__":
    t_a, lat_a = test_asr(r"C:\Users\Tanya\.gemini\antigravity\scratch\FINAL_APP\tools\participant_a_hello.wav", "en")
    t_b, lat_b = test_asr(r"C:\Users\Tanya\.gemini\antigravity\scratch\FINAL_APP\tools\participant_b_threat.wav", "en")
    t_b_hi, lat_b_hi = test_asr(r"C:\Users\Tanya\.gemini\antigravity\scratch\FINAL_APP\tools\participant_b_threat.wav", "hi")
