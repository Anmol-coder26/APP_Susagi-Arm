import wave
import struct
import math

def calculate_rms(chunk):
    count = len(chunk) // 2
    if count == 0:
        return 0.0
    shorts = struct.unpack(f"<{count}h", chunk)
    sum_sq = sum(s * s for s in shorts)
    return math.sqrt(sum_sq / count)

with wave.open(r"C:\Users\Tanya\.gemini\antigravity\scratch\FINAL_APP\tools\participant_b_threat.wav", "rb") as wf:
    pcm_b = wf.readframes(wf.getnframes())

chunk_size = 3200
chunks = [pcm_b[i:i+chunk_size] for i in range(0, len(pcm_b), chunk_size)]

print(f"Participant B has {len(chunks)} chunks (100ms each):")
speech_runs = []
current_run = 0
silence_count = 0

for i, c in enumerate(chunks):
    rms = calculate_rms(c)
    is_speech = rms > 300
    if is_speech:
        current_run += 1
        silence_count = 0
    else:
        silence_count += 1
        if silence_count > 6 and current_run > 0:
            speech_runs.append((i - silence_count - current_run, i - silence_count, current_run * 100))
            current_run = 0

if current_run > 0:
    speech_runs.append((len(chunks) - current_run, len(chunks), current_run * 100))

print(f"Detected speech segments (with 600ms hangover):")
for start, end, dur in speech_runs:
    print(f" - From {start*100}ms to {end*100}ms (duration={dur}ms)")
