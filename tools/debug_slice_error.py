import base64
import json
import urllib.request
import urllib.error
import struct

ENDPOINT = "https://dhruva-api.bhashini.gov.in/services/inference/pipeline"
INFERENCE_API_KEY = "TYcnvGMZSCZnzg0QIyh-97KPNxJynJ5b4w_fY5tZa8jG_btp6zcsGQPGMmx303JC"
PIPELINE_ID = "64392f96daac500b55c543cd"

with open(r"C:\Users\Tanya\.gemini\antigravity\scratch\FINAL_APP\tools\participant_b_threat.wav", "rb") as f:
    full_wav = f.read()

header = full_wav[:44]
pcm = full_wav[44:]

slice_pcm = pcm[:48000]
total_data_len = len(slice_pcm)
slice_header = bytearray(header)
struct.pack_into("<I", slice_header, 4, total_data_len + 36)
struct.pack_into("<I", slice_header, 40, total_data_len)
slice_wav = bytes(slice_header) + slice_pcm

audio_base64 = base64.b64encode(slice_wav).decode("utf-8")
payload = {
    "pipelineTasks": [{"taskType": "asr", "config": {"language": {"sourceLanguage": "en"}, "audioFormat": "wav", "samplingRate": 16000}}],
    "inputData": {"audio": [{"audioContent": audio_base64}]}
}
req = urllib.request.Request(
    ENDPOINT,
    data=json.dumps(payload).encode("utf-8"),
    headers={"Content-Type": "application/json", "Authorization": INFERENCE_API_KEY, "x-pipeline-id": PIPELINE_ID}
)

try:
    with urllib.request.urlopen(req) as resp:
        print("Success:", resp.read().decode())
except urllib.error.HTTPError as e:
    print(f"HTTPError {e.code}: {e.read().decode('utf-8')}")
