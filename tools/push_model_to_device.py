import os
import sys
import subprocess

ADB = r"C:\Users\Tanya\AppData\Local\Android\Sdk\platform-tools\adb.exe"
MODELS_DIR = r"C:\Users\Tanya\.gemini\antigravity\scratch\FINAL_APP\dist\models"

def main():
    print("=" * 60)
    print("SuSagi Guardian — ExecuTorch Llama Model Provisioner")
    print("=" * 60)

    # 1. Check ADB devices
    res = subprocess.run([ADB, "devices"], capture_output=True, text=True)
    lines = [line.strip() for line in res.stdout.strip().split("\n") if line.strip()]
    devices = [line.split()[0] for line in lines[1:] if "device" in line and not "offline" in line]

    if not devices:
        print("\n[ERROR] No Android device attached via ADB!")
        print("Please enable 'USB Debugging' in Android Developer Options, connect via USB, and try again.")
        print("Run: adb devices to verify.")
        sys.exit(1)

    print(f"\n[OK] Found {len(devices)} device(s): {devices}")

    # 2. Check model files
    model_file = os.path.join(MODELS_DIR, "llama_3_2_1b_xnnpack_spinquant.pte")
    tok_file = os.path.join(MODELS_DIR, "tokenizer.model")

    if not os.path.exists(model_file) or os.path.getsize(model_file) < 50_000_000:
        print(f"\n[ERROR] Model file missing or incomplete: {model_file}")
        sys.exit(1)

    if not os.path.exists(tok_file) or os.path.getsize(tok_file) < 1024:
        print(f"\n[ERROR] Tokenizer missing: {tok_file}")
        sys.exit(1)

    for dev in devices:
        print(f"\n--- Provisioning Model on Device: {dev} ---")

        # 1. Create standard Llama directory
        subprocess.run([ADB, "-s", dev, "shell", "mkdir", "-p", "/data/local/tmp/llama"], check=True)

        # 2. Push Tokenizer (~2.2MB)
        print(f"Pushing tokenizer.model ({os.path.getsize(tok_file)} bytes)...")
        subprocess.run([ADB, "-s", dev, "push", tok_file, "/data/local/tmp/llama/tokenizer.model"], check=True)

        # 3. Push Model (~1.08GB)
        print(f"Pushing llama_3_2_1b_xnnpack_spinquant.pte ({os.path.getsize(model_file) / (1024*1024):.1f} MB)...")
        subprocess.run([ADB, "-s", dev, "push", model_file, "/data/local/tmp/llama/llama_3_2_1b_xnnpack_spinquant.pte"], check=True)

        # 4. Set permissions
        subprocess.run([ADB, "-s", dev, "shell", "chmod", "644", "/data/local/tmp/llama/*"], check=True)

        # 5. Also try copying into app-private storage if debuggable
        print("Attempting to mirror into app-private files directory (/data/data/com.guardian.app/files/models/)...")
        cmd = "run-as com.guardian.app mkdir -p /data/data/com.guardian.app/files/models && run-as com.guardian.app cp /data/local/tmp/llama/* /data/data/com.guardian.app/files/models/"
        subprocess.run([ADB, "-s", dev, "shell", cmd])

        print(f"\n[SUCCESS] Llama 3.2 1B Instruct model provisioned on {dev}!")

    print("\nVerification command:")
    print("adb shell ls -lh /data/local/tmp/llama")

if __name__ == "__main__":
    main()
