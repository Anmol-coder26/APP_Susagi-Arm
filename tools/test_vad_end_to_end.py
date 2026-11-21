import wave
import struct
import math
import io
import base64
import json
import urllib.request
import time

ENDPOINT = "https://dhruva-api.bhashini.gov.in/services/inference/pipeline"
INFERENCE_API_KEY = "TYcnvGMZSCZnzg0QIyh-97KPNxJynJ5b4w_fY5tZa8jG_btp6zcsGQPGMmx303JC"
PIPELINE_ID = "64392f96daac500b55c543cd"

def calculate_rms(chunk):
    count = len(chunk) // 2
    if count == 0:
        return 0.0
    shorts = struct.unpack(f"<{count}h", chunk)
    sum_sq = sum(s * s for s in shorts)
    return math.sqrt(sum_sq / count)

class SimulatedDhruvaVadTransport:
    def __init__(self, tag, lang="en"):
        self.tag = tag
        self.lang = lang
        self.speech_accumulator = bytearray()
        self.pre_buffer = []
        self.is_speech_active = False
        self.last_speech_ms = 0
        self.current_time_ms = 0
        self.transcripts = []

    def feed_chunk(self, chunk_pcm):
        self.current_time_ms += 100
        rms = calculate_rms(chunk_pcm)
        is_speech = rms > 300.0

        if is_speech:
            if not self.is_speech_active:
                self.is_speech_active = True
                print(f"[{self.tag}] Speech START at {self.current_time_ms}ms (RMS={rms:.0f})")
                for pre in self.pre_buffer:
                    self.speech_accumulator.extend(pre)
                self.pre_buffer.clear()
            self.speech_accumulator.extend(chunk_pcm)
            self.last_speech_ms = self.current_time_ms
        else:
            if self.is_speech_active:
                self.speech_accumulator.extend(chunk_pcm)
                silence_dur = self.current_time_ms - self.last_speech_ms
                if silence_dur >= 600:
                    print(f"[{self.tag}] Speech END at {self.current_time_ms}ms (silence={silence_dur}ms, dur={len(self.speech_accumulator)/32000:.2f}s)")
                    if len(self.speech_accumulator) >= 22400: # >= 0.7s
                        self.dispatch(bytes(self.speech_accumulator))
                    self.speech_accumulator.clear()
                    self.is_speech_active = False
            else:
                self.pre_buffer.append(chunk_pcm)
                if len(self.pre_buffer) > 2: # Keep 200ms
                    self.pre_buffer.pop(0)

        # Max cap
        if self.is_speech_active and len(self.speech_accumulator) >= 160000: # 5s
            print(f"[{self.tag}] Max speech window reached (5s), dispatching")
            self.dispatch(bytes(self.speech_accumulator))
            self.speech_accumulator.clear()

    def flush(self):
        if self.is_speech_active and len(self.speech_accumulator) >= 22400:
            print(f"[{self.tag}] Flushing remaining speech buffer ({len(self.speech_accumulator)/32000:.2f}s)")
            self.dispatch(bytes(self.speech_accumulator))
            self.speech_accumulator.clear()
            self.is_speech_active = False

    def dispatch(self, pcm_bytes):
        # Build WAV
        header = bytearray(44)
        total_data_len = len(pcm_bytes)
        total_audio_len = total_data_len + 36
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
        wav_bytes = bytes(header) + pcm_bytes

        audio_base64 = base64.b64encode(wav_bytes).decode("utf-8")
        payload = {
            "pipelineTasks": [{"taskType": "asr", "config": {"language": {"sourceLanguage": self.lang}, "audioFormat": "wav", "samplingRate": 16000}}],
            "inputData": {"audio": [{"audioContent": audio_base64}]}
        }
        req = urllib.request.Request(
            ENDPOINT,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json", "Authorization": INFERENCE_API_KEY, "x-pipeline-id": PIPELINE_ID}
        )
        try:
            start = time.time()
            with urllib.request.urlopen(req, timeout=10) as resp:
                lat = (time.time() - start) * 1000
                data = json.loads(resp.read().decode("utf-8"))
                text = ""
                for p in data.get("pipelineResponse", []):
                    for o in p.get("output", []):
                        text = o.get("source", "").strip()
                print(f"[{self.tag} DISPATCH SUCCESS in {lat:.1f}ms]: '{text}'")
                if text:
                    self.transcripts.append(text)
        except Exception as e:
            print(f"[{self.tag} DISPATCH ERROR]: {e}")

print("=== Testing VAD Simulation on Participant A (Hello) ===")
with wave.open(r"C:\Users\Tanya\.gemini\antigravity\scratch\FINAL_APP\tools\participant_a_hello.wav", "rb") as wf:
    pcm_a = wf.readframes(wf.getnframes())

vad_a = SimulatedDhruvaVadTransport("LOCAL", lang="en")
for i in range(0, len(pcm_a), 3200):
    vad_a.feed_chunk(pcm_a[i:i+3200])
vad_a.flush()

print("\n=== Testing VAD Simulation on Participant B (Threat) ===")
with wave.open(r"C:\Users\Tanya\.gemini\antigravity\scratch\FINAL_APP\tools\participant_b_threat.wav", "rb") as wf:
    pcm_b = wf.readframes(wf.getnframes())

vad_b = SimulatedDhruvaVadTransport("REMOTE", lang="en")
for i in range(0, len(pcm_b), 3200):
    vad_b.feed_chunk(pcm_b[i:i+3200])
vad_b.flush()

print("\n--- Final Transcripts ---")
print("LOCAL:", vad_a.transcripts)
print("REMOTE:", vad_b.transcripts)
