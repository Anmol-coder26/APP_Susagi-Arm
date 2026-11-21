import wave
import io
import base64
import json
import urllib.request

ENDPOINT = "https://dhruva-api.bhashini.gov.in/services/inference/pipeline"
INFERENCE_API_KEY = "TYcnvGMZSCZnzg0QIyh-97KPNxJynJ5b4w_fY5tZa8jG_btp6zcsGQPGMmx303JC"
PIPELINE_ID = "64392f96daac500b55c543cd"

with wave.open(r"C:\Users\Tanya\.gemini\antigravity\scratch\FINAL_APP\tools\participant_b_threat.wav", "rb") as wf:
    frames = wf.readframes(wf.getnframes())

# Remaining frames from 2.5s onwards
frames_rem = frames[16000 * 2 * 2 + 16000:]

out_io = io.BytesIO()
with wave.open(out_io, "wb") as out_wf:
    out_wf.setnchannels(1)
    out_wf.setsampwidth(2)
    out_wf.setframerate(16000)
    out_wf.writeframes(frames_rem)

valid_wav = out_io.getvalue()
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
    print("Remaining slice transcript:", text)
