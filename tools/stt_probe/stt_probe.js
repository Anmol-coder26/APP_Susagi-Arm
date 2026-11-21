/**
 * Standalone Bhashini STT Probe Script
 * Usage: node stt_probe.js [websocket_url] [language_code] [api_key]
 * Example: node stt_probe.js wss://api.bhashini.gov.in/stt hi YOUR_KEY
 */

const WebSocket = require('ws');

const url = process.argv[2] || 'ws://localhost:8080/stt';
const lang = process.argv[3] || 'hi';
const apiKey = process.argv[4] || null;

console.log(`[STT PROBE] Connecting to ${url} with language ${lang}...`);

const headers = {};
if (apiKey) {
    headers['Authorization'] = `Bearer ${apiKey}`;
}

const ws = new WebSocket(url, { headers });

ws.on('open', () => {
    console.log('[STT PROBE] WebSocket connected successfully!');
    
    // Send Start Event
    const startPayload = {
        event: "start",
        language: lang,
        useVad: true,
        inputEncoding: {
            encoding: "linear16",
            samplingRate: 16000,
            bitsPerSample: 16,
            numChannels: 1
        },
        vadConfig: {
            pStart: 0.4,
            pauseMs: 800
        },
        interimIntervalMs: 200,
        preProcessors: ["vad"]
    };
    
    ws.send(JSON.stringify(startPayload));
    console.log('[STT PROBE] Sent start event:', JSON.stringify(startPayload));

    // Send dummy PCM audio chunk (1 second of 16kHz 16-bit mono silence/tone)
    const dummyPcm = Buffer.alloc(32000);
    ws.send(dummyPcm);
    console.log('[STT PROBE] Sent 1s dummy PCM audio chunk (32,000 bytes).');
});

ws.on('message', (data) => {
    const text = data.toString();
    console.log('[STT PROBE] Received message:', text);
    try {
        const json = JSON.parse(text);
        const transcript = json.transcript || json.partial_transcript || '';
        const isFinal = json.isFinal || json.final || false;
        console.log(`[STT PROBE] Parsed -> transcript: "${transcript}", isFinal: ${isFinal}`);
    } catch (e) {
        console.log('[STT PROBE] Raw non-JSON data received:', data);
    }
});

ws.on('error', (err) => {
    console.error('[STT PROBE] WebSocket error:', err.message);
});

ws.on('close', (code, reason) => {
    console.log(`[STT PROBE] WebSocket closed (code=${code}): ${reason}`);
});
