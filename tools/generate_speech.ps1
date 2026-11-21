Add-Type -AssemblyName System.Speech
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer

# Configure audio format to 16kHz 16-bit Mono PCM
$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)

# Participant A: "Hello, how are you?"
$synth.SetOutputToWaveFile("C:\Users\Tanya\.gemini\antigravity\scratch\FINAL_APP\tools\participant_a_hello.wav", $format)
$synth.Speak("Hello, how are you?")

# Participant B: "I am calling from cybercrime police. Do not disconnect. Transfer fifty thousand rupees immediately."
$synth.SetOutputToWaveFile("C:\Users\Tanya\.gemini\antigravity\scratch\FINAL_APP\tools\participant_b_threat.wav", $format)
$synth.Speak("I am calling from cybercrime police. Do not disconnect. Transfer fifty thousand rupees immediately.")

$synth.Dispose()

Write-Output "Generated speech files:"
Get-Item "C:\Users\Tanya\.gemini\antigravity\scratch\FINAL_APP\tools\participant_*.wav" | Select-Object Name, Length
