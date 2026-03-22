const fs = require('fs');
const assert = require('assert');
const path = require('path');

console.log('🧪 Starting Unit Test for FCM High Priority Logic...');

// 1. Read the server.js file
const serverPath = path.join(__dirname, '../../server.js');
const serverCode = fs.readFileSync(serverPath, 'utf8');

// 2. Extract the sendFcmV1Push function body
// Regex to capture the function: async function sendFcmV1Push(...) { ... }
const functionRegex = /async function sendFcmV1Push\s*\(([^)]+)\)\s*{([\s\S]*?)\n}/;
const match = serverCode.match(functionRegex);

if (!match) {
    console.error('❌ Failed to find sendFcmV1Push function in server.js');
    process.exit(1);
}

const args = match[1]; // fcmToken, data, notification
const body = match[2]; // function body

console.log('✅ Found sendFcmV1Push function source code.');

// 3. Create a SANDBOXED version of the function to test
// We need to verify that it constructs 'messagePayload' with priority: high
// We will inject a mock 'fcmAuth' and 'fetch' into the scope.

// We will modify the body to RETURN the payload instead of fetching, or mock fetch to intercept.
// But we need to handle the closure variable `fcmAuth`.
// We will wrap the body in a new function that accepts fcmAuth and FCM_PROJECT_ID.

const mockFcmAuth = {
    getAccessToken: async () => ({ token: 'mock-token' })
};

const mockFetch = async (url, options) => {
    // THIS IS THE INTERCEPTION POINT
    const body = JSON.parse(options.body);
    const payload = body.message;

    console.log('--- INTERCEPTED PAYLOAD ---');
    console.log(JSON.stringify(payload, null, 2));

    try {
        assert.strictEqual(payload.android.priority, 'high', 'ERROR: android.priority IS NOT HIGH');
        assert.strictEqual(payload.android.ttl, '0s', 'ERROR: android.ttl IS NOT 0s');
        assert.strictEqual(payload.data.callType, 'chat', 'ERROR: Data missing callType');
        console.log('✅ ASSERTION PASSED: Priority is High, TTL is 0s.');
    } catch (e) {
        console.log('❌ ASSERTION FAILED: ' + e.message);
        throw e;
    }

    return {
        ok: true,
        json: async () => ({ name: 'projects/test/messages/mock-id' })
    };
};

// Construct the executable function
// We wrap it in an async IIFE that defines the globals it needs
const testRunner = async () => {
    const fcmAuth = mockFcmAuth;
    const FCM_PROJECT_ID = 'test-project';
    const fetch = mockFetch;

    // Evaluate the extracted body
    // We create a function with the same signature
    const sendFcmV1Push = new Function('fcmAuth', 'FCM_PROJECT_ID', 'fetch',
        `return async function(${args}) { ${body} }`
    )(fcmAuth, FCM_PROJECT_ID, fetch);

    // Run it
    console.log('▶️ Executing function with Mock Data...');
    await sendFcmV1Push('token-123', { callType: 'chat' }, { title: 'Test' });
};

testRunner().then(() => {
    console.log('🎉 UNIT TEST COMPLETED SUCCESSFULLY');
}).catch(err => {
    console.error('💥 UNIT TEST FAILED');
    console.error(err);
    process.exit(1);
});
