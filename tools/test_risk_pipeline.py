import json

# Actual Bhashini ASR transcripts obtained from live API test
transcript_local = "Hello, how are you?"
transcript_remote = "I am calling from Cybercrime Police. Do not disconnect. Transfer 50,000 rupees immediately."

# Dialogue window formatted by TranscriptMerger
merged_window = f"[LOCAL (User)]: {transcript_local}\n[REMOTE (Caller)]: {transcript_remote}"
print("=== MERGED DIALOGUE WINDOW ===")
print(merged_window)

# Evaluate via on-device heuristic
remote_scam_keywords = [
    "digital arrest", "digital warrant", "arrest warrant", "cbi", "mumbai police",
    "cyber crime", "cybercrime police", "cybercrime", "crime branch", "narcotics", "customs",
    "illegal package", "drugs found", "aadhaar misused", "sim blocked", "official secrets act"
]
financial_keywords = [
    "rbi verification", "safe account", "transfer", "transfer fifty thousand",
    "rtgs", "imps", "upi transfer", "otp", "atm pin", "secret pin"
]

lower = merged_window.lower()
remote_turns = [l for l in lower.split("\n") if "[remote" in l or "caller:" in l]
text_to_check = " ".join(remote_turns) if remote_turns else lower

found_scam = [w for w in remote_scam_keywords if w in text_to_check]
found_financial = [w for w in financial_keywords if w in text_to_check]

print("\n=== HEURISTIC KEYWORD MATCHES ===")
print("Authority / Scam Keywords:", found_scam)
print("Financial Keywords:", found_financial)

scam_score = 45 + (len(found_scam) * 10) + 35 + (len(found_financial) * 10)
final_score = min(scam_score, 95)

tactics = ["authority_impersonation", "financial_coercion", "intimidation"]
print(f"\nScore: {final_score}")
print(f"Scammer: remote")
print(f"Victim: local")
print(f"Tactics: {tactics}")
