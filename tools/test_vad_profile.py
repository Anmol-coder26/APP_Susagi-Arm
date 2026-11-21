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

with wave.open(r"C:\Users\Tanya\.gemini\antigravity\scratch\FINAL_APP\tools\participant_a_hello.wav", "rb") as wf:
    pcm_a = wf.readframes(wf.getnframes())

# Slice into 100ms (3200 bytes) chunks
chunk_size = 3200
chunks = [pcm_a[i:i+chunk_size] for i in range(0, len(pcm_a), chunk_size)]

print(f"Participant A has {len(chunks)} chunks (100ms each):")
for i, c in enumerate(chunks):
    rms = calculate_rms(c)
    print(f"Chunk {i+1} ({(i+1)*100}ms): RMS={rms:.1f} {'[SPEECH]' if rms > 400 else '[SILENCE]'}")
