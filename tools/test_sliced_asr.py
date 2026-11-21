import base64
import json
import urllib.request
import time

ENDPOINT = "https://dhruva-api.bhashini.gov.in/services/inference/pipeline"
INFERENCE_API_KEY = "TYcnvGMZSCZnzg0QIyh-97KPNxJynJ5b4w_fY5tZa8jG_btp6zcsGQPGMmx303JC"
PIPELINE_ID = "64392f96daac500b55c543cd"

def send_wav_bytes(wav_bytes, tag="chunk", lang="en"):
    audio_base64 = base64.b64encode(wav_bytes).decode("utf-8")
    payload = {
        "pipelineTasks": [{"taskType": "asr", "config": {"language": {"sourceLanguage": lang}, "audioFormat": "wav", "samplingRate": 16000}}],
        "inputData": {"audio": [{"audioContent": audio_base64}]}
    }
    req = urllib.request.Request(
        ENDPOINT,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json", "Authorization": INFERENCE_API_KEY, "x-pipeline-id": PIPELINE_ID}
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            text = ""
            for p in data.get("pipelineResponse", []):
                for o in p.get("output", []):
                    text = o.get("source", "").strip()
            print(f"[{tag}] ({len(wav_bytes)} bytes): '{text}'")
            return text
    except Exception as e:
        print(f"[{tag}] Error: {e}")
        return ""

# Read participant_b_threat.wav (has 44 bytes header + raw PCM)
with open(r"C:\Users\Tanya\.gemini\antigravity\scratch\FINAL_APP\tools\participant_b_threat.wav", "rb") as f:
    full_wav = f.read()

header = full_wav[:44]
pcm = full_wav[44:]

print(f"Total PCM bytes: {len(pcm)} (~{len(pcm)/32000:.2f}s)")

# Test 1.5s (48,000 bytes) slices
import struct
chunk_size = 48000
for i in range(0, len(pcm), chunk_size):
    slice_pcm = pcm[i:i+chunk_size]
    # Build valid WAV header for slice
    total_data_len = len(slice_pcm)
    slice_header = bytearray(header)
    struct.pack_into("<I", slice_header, 4, total_data_len + 36)
    struct.pack_into("<I", slice_header, 40, total_data_len)
    slice_wav = bytes(slice_header) + slice_pcm
    send_wav_bytes(slice_wav, tag=f"Slice {i//chunk_size + 1} ({len(slice_pcm)/32000:.1f}s)")
