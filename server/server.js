const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '.env') });
const express = require('express');
const cors = require('cors');
const crypto = require('crypto');
const { RtcTokenBuilder, RtmTokenBuilder, RtcRole } = require('agora-token');
const admin = require('firebase-admin');
const Ably = require('ably');

const ABLY_API_KEY = process.env.ABLY_API_KEY || '';
let ablyRest = null;
if (ABLY_API_KEY && ABLY_API_KEY !== 'YOUR_ABLY_API_KEY') {
    try {
        ablyRest = new Ably.Rest(ABLY_API_KEY);
        console.log('[Ably] Initialized Ably REST client successfully');
    } catch (e) {
        console.warn('[Ably] Init warning:', e.message);
    }
}

const app = express();
app.use(cors());
app.use(express.json());

// Security Headers
app.use((req, res, next) => {
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-Frame-Options', 'DENY');
    res.setHeader('X-XSS-Protection', '1; mode=block');
    res.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
    next();
});

// Rate Limiting (max 120 requests per minute per IP)
const rateLimitMap = new Map();
app.use((req, res, next) => {
    const ip = req.ip || req.socket.remoteAddress;
    const now = Date.now();
    const windowMs = 60 * 1000;
    const limit = 120;

    const record = rateLimitMap.get(ip) || { count: 0, resetTime: now + windowMs };
    if (now > record.resetTime) {
        record.count = 0;
        record.resetTime = now + windowMs;
    }
    record.count++;
    rateLimitMap.set(ip, record);

    if (record.count > limit) {
        return res.status(429).json({ error: 'Too many requests. Please try again later.' });
    }
    next();
});

let appId = process.env.APP_ID || '';
let appCertificate = process.env.APP_CERTIFICATE || '';

// If missing, attempt reading from local.properties
if (!appId || !appCertificate) {
    try {
        const fs = require('fs');
        const localPropsPath = path.join(__dirname, '..', 'local.properties');
        if (fs.existsSync(localPropsPath)) {
            const propsContent = fs.readFileSync(localPropsPath, 'utf8');
            for (const line of propsContent.split('\n')) {
                const trimmed = line.trim();
                if (!appId && trimmed.startsWith('AGORA_APP_ID=')) {
                    appId = trimmed.split('=')[1]?.trim() || '';
                }
                if (!appCertificate && (trimmed.startsWith('AGORA_APP_CERTIFICATE=') || trimmed.startsWith('APP_CERTIFICATE='))) {
                    appCertificate = trimmed.split('=')[1]?.trim() || '';
                }
            }
        }
    } catch (_err) {}
}

const APP_ID = appId || 'd575bd8b35004ad896366419f3a8a8f1';
const APP_CERTIFICATE = appCertificate;
const PORT = process.env.PORT || 3001;
const TOKEN_EXPIRY_SECONDS = 3600; // 1 hour

// Load Gemini API credentials from environment or local.properties
let geminiApiKey = process.env.GEMINI_API_KEY || '';
let geminiModel = process.env.GEMINI_MODEL || 'gemini-2.5-flash';

if (!geminiApiKey) {
    try {
        const fs = require('fs');
        const localPropsPath = path.join(__dirname, '..', 'local.properties');
        if (fs.existsSync(localPropsPath)) {
            const propsContent = fs.readFileSync(localPropsPath, 'utf8');
            for (const line of propsContent.split('\n')) {
                const trimmed = line.trim();
                if (trimmed.startsWith('GEMINI_API_KEY=')) {
                    const keyVal = trimmed.split('=')[1]?.trim();
                    if (keyVal && keyVal !== 'YOUR_GEMINI_API_KEY') {
                        geminiApiKey = keyVal;
                    }
                }
                if (trimmed.startsWith('GEMINI_MODEL=')) {
                    const modelVal = trimmed.split('=')[1]?.trim();
                    if (modelVal) geminiModel = modelVal;
                }
            }
        }
    } catch (_err) {}
}

const maskedGeminiKey = geminiApiKey
    ? `${geminiApiKey.slice(0, 4)}...${geminiApiKey.slice(-4)}`
    : '[NOT CONFIGURED]';
console.log(`[Gemini Cloud Config] Model: ${geminiModel}, Key: ${maskedGeminiKey}`);


// Initialize Firebase Admin SDK
let db = null;
let firebaseInitialized = false;

try {
    const fs = require('fs');
    if (fs.existsSync('./firebase-service-account.json')) {
        const serviceAccount = require('./firebase-service-account.json');
        admin.initializeApp({
            credential: admin.credential.cert(serviceAccount)
        });
        db = admin.firestore();
        firebaseInitialized = true;
        console.log('[Firebase Admin] Successfully initialized with Firestore');
    } else {
        console.warn('[Firebase Admin] firebase-service-account.json not found. Operating in fallback mode.');
    }
} catch (e) {
    console.warn('[Firebase Admin] Init warning:', e.message);
}

// In-memory fallbacks when Firestore is unconfigured
const memoryUsers = new Map();
const memoryCalls = new Map();

// Authentication Middleware
async function verifyAuthToken(req, res, next) {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
        // Fallback for development testing
        req.user = { uid: req.headers['x-mock-uid'] || 'mock_user_' + (req.body?.calleeUid ? 'caller' : 'default') };
        return next();
    }
    const idToken = authHeader.split('Bearer ')[1];
    try {
        if (firebaseInitialized) {
            const decodedToken = await admin.auth().verifyIdToken(idToken);
            req.user = decodedToken;
        } else {
            req.user = { uid: req.headers['x-mock-uid'] || 'user_' + idToken.slice(-6) };
        }
        next();
    } catch (err) {
        return res.status(401).json({ error: 'Unauthorized: Invalid Firebase ID token' });
    }
}

// Helper to generate RTC token
function buildRtcToken(channelName, uid) {
    if (!APP_CERTIFICATE) return "";
    const currentTimestamp = Math.floor(Date.now() / 1000);
    const privilegeExpiredTs = currentTimestamp + TOKEN_EXPIRY_SECONDS;
    const numericUid = typeof uid === 'number' ? uid : 0;
    return RtcTokenBuilder.buildTokenWithUid(
        APP_ID,
        APP_CERTIFICATE,
        channelName,
        numericUid,
        RtcRole.PUBLISHER,
        privilegeExpiredTs
    );
}

// Helper to send high-priority data-only FCM
async function sendDataFcm(fcmToken, dataPayload) {
    if (!fcmToken || !firebaseInitialized) {
        console.log(`[FCM Data Mock] To token ${fcmToken?.slice(0, 15)}... Payload:`, dataPayload);
        return false;
    }
    try {
        const message = {
            token: fcmToken,
            data: dataPayload,
            android: {
                priority: 'high'
            }
        };
        await admin.messaging().send(message);
        console.log(`[FCM Data Sent] Type: ${dataPayload.type} to Token: ${fcmToken.slice(0, 15)}...`);
        return true;
    } catch (err) {
        console.error('[FCM Data Error]', err.message);
        return false;
    }
}

// -------------------------------------------------------------
// 1. POST /users/register — Register/Update User Profile
// -------------------------------------------------------------
app.post('/users/register', verifyAuthToken, async (req, res) => {
    const { uid, phoneNumber, displayName, fcmToken, platform } = req.body;
    const effectiveUid = uid || req.user.uid;
    if (!effectiveUid || !phoneNumber) {
        return res.status(400).json({ error: 'uid and phoneNumber are required' });
    }

    const userData = {
        uid: effectiveUid,
        phoneNumber,
        displayName: displayName || 'Guardian User',
        fcmToken: fcmToken || '',
        platform: platform || 'android',
        updatedAt: Date.now()
    };

    if (db) {
        await db.collection('users').doc(effectiveUid).set(userData, { merge: true });
    }
    memoryUsers.set(effectiveUid, userData);
    console.log(`[Users] Registered user ${effectiveUid} (${phoneNumber})`);

    res.json({ success: true, user: userData });
});

// -------------------------------------------------------------
// 2. POST /users/lookup — Lookup Registered Contacts
// -------------------------------------------------------------
app.post('/users/lookup', verifyAuthToken, async (req, res) => {
    const { phoneNumbers } = req.body;
    if (!Array.isArray(phoneNumbers)) {
        return res.status(400).json({ error: 'phoneNumbers array is required' });
    }

    const registeredUsers = [];

    if (db && phoneNumbers.length > 0) {
        // Firestore 'in' query limit is 30
        const chunks = [];
        for (let i = 0; i < phoneNumbers.length; i += 30) {
            chunks.push(phoneNumbers.slice(i, i + 30));
        }
        for (const chunk of chunks) {
            const snap = await db.collection('users').where('phoneNumber', 'in', chunk).get();
            snap.forEach(doc => registeredUsers.push(doc.data()));
        }
    } else {
        memoryUsers.forEach(user => {
            if (phoneNumbers.includes(user.phoneNumber)) {
                registeredUsers.push(user);
            }
        });
    }

    res.json({ registeredUsers });
});

// -------------------------------------------------------------
// 3. POST /calls — Initiate Call (Mint tokens, create call doc, send FCM)
// -------------------------------------------------------------
app.post('/calls', verifyAuthToken, async (req, res) => {
    const { calleeUid } = req.body;
    const callerUid = req.user.uid;

    if (!calleeUid) {
        return res.status(400).json({ error: 'calleeUid is required' });
    }

    let calleeUser = null;
    let callerUser = null;

    if (db) {
        const calleeSnap = await db.collection('users').doc(calleeUid).get();
        if (calleeSnap.exists) calleeUser = calleeSnap.data();
        const callerSnap = await db.collection('users').doc(callerUid).get();
        if (callerSnap.exists) callerUser = callerSnap.data();
    } else {
        calleeUser = memoryUsers.get(calleeUid);
        callerUser = memoryUsers.get(callerUid);
    }

    const channel = 'call_' + crypto.randomUUID().replace(/-/g, '').slice(0, 16);
    const callId = 'call_' + Date.now();
    const createdAt = Date.now();
    const expiresAt = createdAt + 45000; // 45s ring timeout

    const callerRtcToken = buildRtcToken(channel, 0);
    const calleeRtcToken = buildRtcToken(channel, 0);

    const callDoc = {
        callId,
        callerUid,
        calleeUid,
        channel,
        state: 'RINGING',
        createdAt,
        expiresAt
    };

    if (db) {
        await db.collection('calls').doc(callId).set(callDoc);
    }
    memoryCalls.set(callId, callDoc);

    console.log(`[Calls] Call initiated: ${callId} on channel ${channel} from ${callerUid} to ${calleeUid}`);

    // Send high-priority data-only FCM push to callee
    if (calleeUser && calleeUser.fcmToken) {
        await sendDataFcm(calleeUser.fcmToken, {
            type: 'incoming_call',
            callId,
            channel,
            callerUid,
            callerName: callerUser?.displayName || 'Guardian User',
            callerNumber: callerUser?.phoneNumber || '',
            rtcToken: calleeRtcToken,
            expiresAt: String(expiresAt)
        });
    }

    res.json({
        callId,
        channel,
        rtcToken: callerRtcToken,
        uid: 0,
        expiresAt
    });
});

// Helper to handle call state transitions
async function handleCallStateTransition(callId, requesterUid, targetState, allowedCurrentState, fcmSignalType) {
    let callDoc = null;
    if (db) {
        const snap = await db.collection('calls').doc(callId).get();
        if (snap.exists) callDoc = snap.data();
    } else {
        callDoc = memoryCalls.get(callId);
    }

    if (!callDoc) {
        return { error: 'Call not found', code: 404 };
    }

    if (callDoc.callerUid !== requesterUid && callDoc.calleeUid !== requesterUid) {
        return { error: 'Unauthorized: Not a call participant', code: 403 };
    }

    if (callDoc.state !== allowedCurrentState) {
        return { error: `Invalid transition from ${callDoc.state} to ${targetState}`, code: 400 };
    }

    callDoc.state = targetState;
    callDoc.updatedAt = Date.now();

    if (db) {
        await db.collection('calls').doc(callId).update({ state: targetState, updatedAt: Date.now() });
    }
    memoryCalls.set(callId, callDoc);

    const otherUid = (requesterUid === callDoc.callerUid) ? callDoc.calleeUid : callDoc.callerUid;
    let otherUser = null;
    if (db) {
        const snap = await db.collection('users').doc(otherUid).get();
        if (snap.exists) otherUser = snap.data();
    } else {
        otherUser = memoryUsers.get(otherUid);
    }

    if (otherUser && otherUser.fcmToken && fcmSignalType) {
        await sendDataFcm(otherUser.fcmToken, {
            type: fcmSignalType,
            callId,
            channel: callDoc.channel,
            updatedAt: String(Date.now())
        });
    }

    return { success: true, call: callDoc };
}

// -------------------------------------------------------------
// 4. POST /calls/:id/accept — Accept Call
// -------------------------------------------------------------
app.post('/calls/:id/accept', verifyAuthToken, async (req, res) => {
    const result = await handleCallStateTransition(req.params.id, req.user.uid, 'ACCEPTED', 'RINGING', 'call_accepted');
    if (result.error) return res.status(result.code).json({ error: result.error });
    res.json(result);
});

// -------------------------------------------------------------
// 5. POST /calls/:id/reject — Reject Call
// -------------------------------------------------------------
app.post('/calls/:id/reject', verifyAuthToken, async (req, res) => {
    const result = await handleCallStateTransition(req.params.id, req.user.uid, 'REJECTED', 'RINGING', 'call_rejected');
    if (result.error) return res.status(result.code).json({ error: result.error });
    res.json(result);
});

// -------------------------------------------------------------
// 6. POST /calls/:id/cancel — Cancel Call
// -------------------------------------------------------------
app.post('/calls/:id/cancel', verifyAuthToken, async (req, res) => {
    const result = await handleCallStateTransition(req.params.id, req.user.uid, 'CANCELLED', 'RINGING', 'call_cancelled');
    if (result.error) return res.status(result.code).json({ error: result.error });
    res.json(result);
});

// -------------------------------------------------------------
// 7. POST /calls/:id/end — End Call
// -------------------------------------------------------------
app.post('/calls/:id/end', verifyAuthToken, async (req, res) => {
    const result = await handleCallStateTransition(req.params.id, req.user.uid, 'ENDED', 'ACCEPTED', 'call_ended');
    if (result.error) return res.status(result.code).json({ error: result.error });
    res.json(result);
});

// -------------------------------------------------------------
// 8. GET /calls/:id/token — Re-mint Token for Active Call
// -------------------------------------------------------------
app.get('/calls/:id/token', verifyAuthToken, async (req, res) => {
    const callId = req.params.id;
    let callDoc = null;
    if (db) {
        const snap = await db.collection('calls').doc(callId).get();
        if (snap.exists) callDoc = snap.data();
    } else {
        callDoc = memoryCalls.get(callId);
    }

    if (!callDoc) return res.status(404).json({ error: 'Call not found' });
    if (callDoc.callerUid !== req.user.uid && callDoc.calleeUid !== req.user.uid) {
        return res.status(403).json({ error: 'Unauthorized: Not a call participant' });
    }

    const token = buildRtcToken(callDoc.channel, 0);
    res.json({ token, channel: callDoc.channel, expiresAt: Date.now() + 3600000 });
});

// Server-side 45s Ring Timeout Checker
setInterval(async () => {
    const now = Date.now();
    const expiredCalls = [];

    if (db) {
        try {
            const snap = await db.collection('calls').where('state', '==', 'RINGING').where('expiresAt', '<=', now).get();
            snap.forEach(doc => expiredCalls.push(doc.data()));
        } catch (_e) {}
    } else {
        memoryCalls.forEach(call => {
            if (call.state === 'RINGING' && call.expiresAt <= now) {
                expiredCalls.push(call);
            }
        });
    }

    for (const call of expiredCalls) {
        call.state = 'MISSED';
        if (db) {
            await db.collection('calls').doc(call.callId).update({ state: 'MISSED', updatedAt: now });
        }
        memoryCalls.set(call.callId, call);
        console.log(`[Calls Timeout] Call ${call.callId} expired to MISSED`);

        const caller = memoryUsers.get(call.callerUid);
        const callee = memoryUsers.get(call.calleeUid);

        if (caller?.fcmToken) sendDataFcm(caller.fcmToken, { type: 'call_timeout', callId: call.callId });
        if (callee?.fcmToken) sendDataFcm(callee.fcmToken, { type: 'call_timeout', callId: call.callId });
    }
}, 5000);

app.get('/agora/token', (req, res) => {
    const channelName = req.query.channelName || ('gx_' + Math.floor(100000 + Math.random() * 900000));
    const token = buildRtcToken(channelName, 0);
    res.json({ token, channelName });
});

// -------------------------------------------------------------
// 9. POST /family/register — Register device for FCM + Ably Push
// -------------------------------------------------------------
const familyTokens = new Map(); // userId -> fcmToken

app.post('/family/register', async (req, res) => {
    const { fcmToken, token, userId, deviceName } = req.body;
    const effectiveToken = fcmToken || token;
    const effectiveUserId = userId || 'family_member_' + (familyTokens.size + 1);

    if (!effectiveToken) {
        return res.status(400).json({ error: 'fcmToken or token is required' });
    }

    familyTokens.set(effectiveUserId, effectiveToken);
    console.log(`[FamilyAlert] Registered device "${deviceName || effectiveUserId}" with token: ${effectiveToken.substring(0, 15)}... (Total: ${familyTokens.size})`);

    // Register device with Ably for push if configured
    if (ablyRest) {
        try {
            await ablyRest.push.admin.deviceRegistrations.save({
                id: effectiveToken,
                clientId: effectiveUserId,
                platform: 'android',
                formFactor: 'phone',
                push: {
                    recipient: {
                        transportType: 'fcm',
                        registrationToken: effectiveToken
                    }
                }
            });
            console.log(`[Ably Push] Device registered for push: ${effectiveUserId}`);
        } catch (err) {
            console.error('[Ably] Device registration failed:', err.message);
        }
    }

    res.json({ success: true, totalRegistered: familyTokens.size });
});

// -------------------------------------------------------------
// 10. POST /alerts/family — Publish Scam Alert via Ably + FCM
// -------------------------------------------------------------
app.post('/alerts/family', async (req, res) => {
    const {
        protectedUserId,
        familyUserIds,
        callerNumber,
        riskScore,
        scamType,
        transcriptSummary
    } = req.body;

    const targets = Array.isArray(familyUserIds) && familyUserIds.length > 0
        ? familyUserIds
        : Array.from(familyTokens.keys());

    console.log(`[FamilyAlert] Scam alert triggered (Risk: ${riskScore}%, Caller: ${callerNumber}, Targets: ${targets.length})`);

    let ablyDelivered = 0;
    let fcmDelivered = 0;

    for (const familyUserId of targets) {
        // 1. Publish via Ably for instant delivery
        if (ablyRest) {
            try {
                const channel = ablyRest.channels.get(`family:${familyUserId}`);
                await channel.publish('alert', {
                    callerNumber: callerNumber || 'Unknown',
                    riskScore: Number(riskScore) || 0,
                    scamType: scamType || 'Scam Call',
                    transcript: transcriptSummary || ''
                });
                ablyDelivered++;
                console.log(`[Ably Alert Published] Instant alert sent to channel family:${familyUserId}`);
            } catch (err) {
                console.error(`[Ably Alert Error] Failed for ${familyUserId}:`, err.message);
            }
        }

        // 2. Send high-priority FCM push as wake-up + fallback
        const fcmToken = familyTokens.get(familyUserId);
        if (fcmToken) {
            const pushSent = await sendDataFcm(fcmToken, {
                type: 'family_alert',
                alert_type: 'scam_detected',
                protected_user: protectedUserId || 'Family Member',
                caller_number: callerNumber || 'Unknown',
                risk_score: String(riskScore || 0),
                scam_type: scamType || 'Suspicious Call',
                transcript_summary: transcriptSummary || ''
            });
            if (pushSent) fcmDelivered++;
        }
    }

    res.json({
        success: true,
        ablyDelivered,
        fcmDelivered,
        totalTargets: targets.length
    });
});

// -------------------------------------------------------------
// 11. POST /alerts/trusted-contact — Dispatch Alert to Saved Contact
// -------------------------------------------------------------
app.post('/alerts/trusted-contact', async (req, res) => {
    const { to, contactName, callerNumber, riskScore, signals, timestamp } = req.body;
    console.log(`[TrustedContactAlert] 🚨 High-risk scam alert dispatched to saved contact: ${contactName} (${to}), Risk: ${riskScore}%, Signals: ${JSON.stringify(signals)}`);

    let ablyDelivered = false;
    let fcmDelivered = false;

    // 1. Broadcast over Ably for live dashboard listeners
    if (ablyRest) {
        try {
            const channel = ablyRest.channels.get('alerts:trusted_contact');
            await channel.publish('scam_alert', {
                targetName: contactName || 'Trusted Contact',
                targetPhone: to || '',
                callerNumber: callerNumber || 'Unknown Caller',
                riskScore: Number(riskScore) || 0,
                signals: signals || [],
                timestamp: timestamp || Date.now()
            });
            ablyDelivered = true;
            console.log(`[Ably] Trusted contact alert published to channel alerts:trusted_contact`);
        } catch (err) {
            console.error(`[Ably] Error publishing trusted contact alert:`, err.message);
        }
    }

    // 2. Broadcast to registered family FCM tokens
    for (const [userId, fcmToken] of familyTokens.entries()) {
        const pushSent = await sendDataFcm(fcmToken, {
            type: 'trusted_contact_alert',
            alert_type: 'scam_detected',
            contact_name: contactName || 'Emergency Contact',
            contact_phone: to || '',
            caller_number: callerNumber || 'Unknown',
            risk_score: String(riskScore || 0),
            signals: JSON.stringify(signals || [])
        });
        if (pushSent) fcmDelivered = true;
    }

    res.json({
        success: true,
        message: `Alert dispatched to ${contactName || 'Trusted Contact'} (${to || 'SMS'})`,
        ablyDelivered,
        fcmDelivered
    });
});

// -------------------------------------------------------------
// 12. GET /health — System Health & Diagnostics
// -------------------------------------------------------------
app.get('/health', (req, res) => {
    res.json({
        status: 'ok',
        service: 'SuSagi Guardian Signaling & AI Engine',
        version: '2.4.0',
        port: PORT,
        agora: {
            appIdConfigured: Boolean(APP_ID),
            certificateConfigured: Boolean(APP_CERTIFICATE),
            mode: APP_CERTIFICATE ? 'token_secured' : 'app_id_testing'
        },
        gemini: {
            configured: Boolean(geminiApiKey),
            model: geminiModel,
            keyStatus: geminiApiKey ? 'active' : 'unconfigured'
        },
        ably: {
            configured: Boolean(ablyRest)
        },
        firebase: {
            configured: firebaseInitialized
        },
        stats: {
            activeCalls: memoryCalls.size,
            registeredUsers: memoryUsers.size,
            registeredFamilyDevices: familyTokens.size
        },
        timestamp: Date.now()
    });
});

// Helper for backend heuristic fallback
function evaluateHeuristicBackend(transcript, failureReason = '') {
    const lower = (transcript || '').toLowerCase();
    const scamWords = [
        'police', 'cybercrime', 'cyber crime', 'cbi', 'customs', 'narcotics',
        'digital arrest', 'arrest warrant', 'arrest', 'disconnect', 'do not disconnect',
        'crime branch', 'inspector', 'officer', 'गिरफ्तार', 'वारंट',
        'पुलिस', 'साइबर', 'क्राइम', 'अपराध', 'सीबीआई', 'कस्टम्स', 'नारकोटिक्स',
        'डिजिटल अरेस्ट', 'गिरफ्तारी', 'अरेस्ट', 'काटना मत', 'फोन मत काटना', 'डस्कनेक्ट', 'डिस्कनेक्ट',
        'थाना', 'इस्पेक्टर', 'अफसर'
    ];
    const moneyWords = [
        'transfer', 'transfer money', 'rtgs', 'imps', 'upi', 'otp', 'fifty thousand',
        'rupees', 'bank account', 'safe account', 'रुपये', 'खाते में भेजो',
        'ट्रांसफर', 'रुपीस', 'पैसे', 'रुपए', 'खाता', 'खाते', 'ओटीपी', 'बैंक', 'भेजो', 'रकम',
        'फिफ्टी थाउजेंड', 'पचास हजार', 'वेरिफिकेशन', 'क्लियरेंस', 'फ्रीज'
    ];

    const foundScam = scamWords.filter(w => lower.includes(w));
    const foundMoney = moneyWords.filter(w => lower.includes(w));

    let score = 0;
    const tactics = [];

    if (foundScam.length > 0) {
        score += 45 + (foundScam.length * 10);
        tactics.push('authority_impersonation');
        if (foundScam.some(w => w.includes('arrest') || w.includes('गिरफ्तार') || w.includes('वारंट'))) {
            tactics.push('digital_arrest');
        }
        if (foundScam.some(w => w.includes('disconnect'))) {
            tactics.push('intimidation');
        }
    }
    if (foundMoney.length > 0) {
        score += 35 + (foundMoney.length * 10);
        tactics.push('financial_coercion');
    }

    const finalScore = Math.min(95, Math.max(0, score));
    const isScam = finalScore >= 60;

    return {
        risk_score: finalScore,
        victim_role: isScam ? 'local' : 'none',
        scammer_role: isScam ? 'remote' : 'none',
        role_confidence: isScam ? 0.85 : 0.40,
        tactics: tactics,
        explanation: isScam
            ? '[On-Device Shield] High-risk digital arrest / authority extortion patterns detected.'
            : (failureReason ? `[Degraded / Cloud AI Unavailable] Monitoring call with on-device heuristic (${failureReason})` : 'Normal conversation. No threat detected.'),
        source: 'LOCAL_HEURISTIC',
        is_fallback: true,
        failure_reason: failureReason,
        analyzed_at_ms: Date.now()
    };
}

// -------------------------------------------------------------
// 13. POST /risk/evaluate — Gemini Cloud AI Semantic Analysis
// -------------------------------------------------------------
app.post('/risk/evaluate', async (req, res) => {
    const { transcript } = req.body;
    if (!transcript || !transcript.trim()) {
        return res.json({
            risk_score: 0,
            victim_role: 'none',
            scammer_role: 'none',
            role_confidence: 1.0,
            tactics: [],
            explanation: 'Quiet channel. Monitoring for speech...',
            source: 'LOCAL_HEURISTIC',
            is_fallback: false
        });
    }

    const trimmed = transcript.trim();

    // If Gemini key is not configured, immediately use robust on-device heuristic
    if (!geminiApiKey || geminiApiKey === 'YOUR_GEMINI_API_KEY') {
        const heuristic = evaluateHeuristicBackend(trimmed, 'Gemini API key unconfigured on server');
        return res.json(heuristic);
    }

    // Call Google Generative Language API
    try {
        const url = `https://generativelanguage.googleapis.com/v1beta/models/${geminiModel}:generateContent?key=${geminiApiKey}`;
        const payload = {
            system_instruction: {
                parts: [{
                    text: 'You are a cybercrime defense analyst. Analyze dialogue between LOCAL and REMOTE. Identify predatory extortion, digital arrest, and financial coercion. Classify scammer_role ("remote"|"local"|"none") and victim_role ("local"|"remote"|"none"). Output strictly valid JSON matching the schema.'
                }]
            },
            contents: [
                { parts: [{ text: `Analyze the following phone conversation transcript:\n\n${trimmed}` }] }
            ],
            generationConfig: {
                temperature: 0.1,
                responseMimeType: 'application/json',
                responseSchema: {
                    type: 'OBJECT',
                    properties: {
                        risk_score: { type: 'INTEGER' },
                        victim_role: { type: 'STRING', enum: ['local', 'remote', 'none'] },
                        scammer_role: { type: 'STRING', enum: ['local', 'remote', 'none'] },
                        role_confidence: { type: 'NUMBER' },
                        tactics: { type: 'ARRAY', items: { type: 'STRING' } },
                        explanation: { type: 'STRING' }
                    },
                    required: ['risk_score', 'victim_role', 'scammer_role', 'role_confidence', 'tactics', 'explanation']
                }
            }
        };

        const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
            signal: AbortSignal.timeout(8000)
        });

        if (response.ok) {
            const data = await response.json();
            const rawText = data?.candidates?.[0]?.content?.parts?.[0]?.text || '{}';
            const cleanText = rawText.replace(/```json/g, '').replace(/```/g, '').trim();
            const parsed = JSON.parse(cleanText);
            console.log(`[Gemini Cloud] Evaluated score: ${parsed.risk_score}, scammer: ${parsed.scammer_role}`);
            return res.json({
                ...parsed,
                source: 'GEMINI_CLOUD',
                is_fallback: false,
                analyzed_at_ms: Date.now()
            });
        }

        const errStatus = response.status;
        const errText = await response.text();
        console.warn(`[Gemini Cloud] HTTP ${errStatus}: ${errText.slice(0, 120)}`);
        const fallback = evaluateHeuristicBackend(trimmed, `HTTP ${errStatus}`);
        return res.json(fallback);
    } catch (err) {
        console.warn(`[Gemini Cloud Network Error] ${err.message}`);
        const fallback = evaluateHeuristicBackend(trimmed, err.message);
        return res.json(fallback);
    }
});


app.listen(PORT, '0.0.0.0', () => {
    console.log(`🚀 Guardian Signaling & Call Server running on http://0.0.0.0:${PORT}`);
});
