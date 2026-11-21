import struct
import base64
import json
import urllib.request
import wave

ENDPOINT = "https://dhruva-api.bhashini.gov.in/services/inference/pipeline"
INFERENCE_API_KEY = "TYcnvGMZSCZnzg0QIyh-97KPNxJynJ5b4w_fY5tZa8jG_btp6zcsGQPGMmx303JC"
PIPELINE_ID = "64392f96daac500b55c543cd"

with wave.open(r"C:\Users\Tanya\.gemini\antigravity\scratch\FINAL_APP\tools\participant_b_threat.wav", "rb") as wf:
    pcm = wf.readframes(wf.getnframes())

# Test our exact wrapPcmInWav implementation
pcm_segment = pcm[:80000] # 2.5s
total_data_len = len(pcm_segment)
total_audio_len = total_data_len + 36

header = bytearray(44)
header[0:4] = b"RIFF"
struct.pack_into("<I", header, 4, total_audio_len)
header[8:12] = b"WAVE"
header[12:16] = b"fmt "
struct.pack_into("<I", header, 16, 16)
struct.pack_into("<H", header, 20, 1) # PCM
struct.pack_into("<H", header, 22, 1) # mono
struct.pack_into("<I", header, 24, 16000) # rate
struct.pack_into("<I", header, 28, 32000) # byterate
struct.pack_into("<H", header, 32, 2) # block align
struct.pack_into("<H", header, 34, 16) # bits per sample
header[36:40] = b"data"
struct.pack_into("<I", header, 40, total_data_len)

wav_bytes = bytes(header) + pcm_segment

audio_base64 = base64.b64encode(wav_bytes).decode("utf-8")
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
    for p in data.get("pipelineResponse", []):
        for o in p.get("output", []):
            print("wrapPcmInWav transcript:", o.get("source"))
