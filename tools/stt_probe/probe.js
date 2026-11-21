const https = require('https');

const userId = 'harshvidyabamer@gmail.com';
const ulcaApiKey = '129fe72a91-352c-4321-b577-a89c3d3c6060';
const pipelineId = '64392f96daac500b55c543d6';

const headerCombinations = [
    { name: 'userID & ulcaApiKey', headers: { 'userID': userId, 'ulcaApiKey': ulcaApiKey } },
    { name: 'userId & ulcaApiKey', headers: { 'userId': userId, 'ulcaApiKey': ulcaApiKey } },
    { name: 'userID & Authorization', headers: { 'userID': userId, 'Authorization': ulcaApiKey } },
    { name: 'userId & Authorization Bearer', headers: { 'userId': userId, 'Authorization': `Bearer ${ulcaApiKey}` } },
    { name: 'user-id & api-key', headers: { 'user-id': userId, 'api-key': ulcaApiKey } },
    { name: 'x-user-id & x-api-key', headers: { 'x-user-id': userId, 'x-api-key': ulcaApiKey } }
];

async function testHeaderVariant(combo) {
    const url = 'https://dhruva-api.bhashini.gov.in/services/inference/pipeline';
    const body = JSON.stringify({
        pipelineTasks: [
            {
                taskType: 'asr',
                config: {
                    language: {
                        sourceLanguage: 'hi'
                    }
                }
            }
        ],
        pipelineRequestConfig: {
            pipelineId: pipelineId
        }
    });

    return new Promise((resolve) => {
        const headers = Object.assign({ 'Content-Type': 'application/json' }, combo.headers);
        const req = https.request(url, { method: 'POST', headers: headers }, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                console.log(`[${combo.name}] -> HTTP ${res.statusCode}: ${data.trim()}`);
                resolve();
            });
        });
        req.on('error', err => {
            console.log(`[${combo.name}] -> ERROR: ${err.message}`);
            resolve();
        });
        req.write(body);
        req.end();
    });
}

async function runAll() {
    console.log('=== TESTING DHRUVA PIPELINE WITH harshvidyabamer@gmail.com ===\n');
    for (const combo of headerCombinations) {
        await testHeaderVariant(combo);
    }
}

runAll();
