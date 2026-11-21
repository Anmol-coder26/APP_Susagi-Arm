import wave
import io
import base64
import json
import urllib.request

ENDPOINT = "https://dhruva-api.bhashini.gov.in/services/inference/pipeline"
INFERENCE_API_KEY = "TYcnvGMZSCZnzg0QIyh-97KPNxJynJ5b4w_fY5tZa8jG_btp6zcsGQPGMmx303JC"
PIPELINE_ID = "64392f96daac500b55c543cd"

with wave.open(r"C:\Users\Tanya\.gemini\antigravity\scratch\FINAL_APP\tools\participant_b_threat.wav", "rb") as wf:
    params = wf.getparams()
    print("Wave params:", params)
    frames = wf.readframes(wf.getnframes())

print(f"Total audio duration: {len(frames) / (16000 * 2):.2f}s")

# Let's test different slice lengths using Python's standard wave library:
# 1. First 2.5 seconds
frames_2_5s = frames[:16000 * 2 * 2 + 16000] # ~2.5s = 80,000 bytes

out_io = io.BytesIO()
with wave.open(out_io, "wb") as out_wf:
    out_wf.setnchannels(1)
    out_wf.setsampwidth(2)
    out_wf.setframerate(16000)
    out_wf.writeframes(frames_2_5s)

valid_wav = out_io.getvalue()
print("Valid WAV header length:", len(valid_wav) - len(frames_2_5s))

audio_base64 = base64.b64encode(valid_wav).decode("utf-8")
payload = {
    "pipelineTasks": [{"taskType": "asr", "config": {"language": {"sourceLanguage": "en"}, "audioFormat": "wav", "samplingRate": 16000}}],
    "inputData": {"audio": [{"audioContent": audio_base64}]}
}
req = urllib.request.Request(
    ENDPOINT,
    data=json.dumps(payload).encode("utf-8"),
    headers={"Content-Type": "application/json", "Authorization": INFERENCE_API_KEY, "x-pipeline-id": PIPELINE_ID}
)

with urllib.request.urlopen(req) as resp:
    data = json.loads(resp.read().decode("utf-8"))
    text = ""
    for p in data.get("pipelineResponse", []):
        for o in p.get("output", []):
            text = o.get("source", "").strip()
    print("2.5s slice transcript:", text)
