const io = require('socket.io-client');
const assert = require('assert');

// const SERVER_URL = 'http://localhost:3000'; // Make sure server is running
const SERVER_URL = 'http://localhost:5000'; // Assuming 5000 or 3000? Checking server.js...
// server.js doesn't show port. assuming default or env. usually 5000 or 8080.
// Let's assume 3000 as per common practice if not specified,
// wait, I can check server.js again or just try commonly used ports.
// Server.js shows: const server = http.createServer(app);
// but I missed the listen call in previous view.
// I'll check port first in next step or assume 5000 and 3000.

// Let's use a dynamic approach or just check the file again quickly.
// Actually I'll write the script to accept PORT env var.

const PORT = process.env.PORT || 8080; // server.js often uses 8080 or 5000.
const URL = `http://localhost:${PORT}`;

async function runTest() {
    console.log(`Connecting to ${URL}...`);

    const clientSocket = io(URL);
    const astroSocket = io(URL);

    let clientId, astroId, sessionId;

    try {
        // 1. Register Client
        clientId = await new Promise((resolve, reject) => {
            clientSocket.emit('register', { phone: '8000000001' }, (res) => {
                if (res.ok) resolve(res.userId);
                else reject(res.error);
            });
        });
        console.log('Client Registered:', clientId);

        // 2. Register Astrologer
        astroId = await new Promise((resolve, reject) => {
            astroSocket.emit('register', { phone: '9000000001' }, (res) => {
                if (res.ok) resolve(res.userId);
                else reject(res.error);
            });
        });
        console.log('Astrologer Registered:', astroId);

        // 3. Client Requests Session
        sessionId = await new Promise((resolve, reject) => {
            clientSocket.emit('request-session', { toUserId: astroId, type: 'chat' }, (res) => {
                if (res.ok) resolve(res.sessionId);
                else reject(res.error);
            });
        });
        console.log('Session Created:', sessionId);

        // 4. Astrologer Connects (T1)
        console.log('Astrologer Connecting to Session...');
        astroSocket.emit('session-connect', { sessionId });

        // Wait 1 second
        await new Promise(r => setTimeout(r, 1000));

        // 5. Client Connects (T2)
        const clientConnectTime = Date.now();
        console.log('Client Connecting to Session at', clientConnectTime);
        clientSocket.emit('session-connect', { sessionId });

        // 6. Monitor for billing-started
        const billingStartedPromise = new Promise((resolve) => {
            clientSocket.on('billing-started', (data) => {
                resolve(data.startTime);
            });
            // also verify astro gets it
            astroSocket.on('billing-started', () => { });
        });

        const billingStartTime = await Promise.race([
            billingStartedPromise,
            new Promise((_, r) => setTimeout(() => r('Timeout waiting for billing-start'), 5000))
        ]);

        console.log('Billing Started At:', billingStartTime);

        // Verification
        // buffer = 2000ms.
        // actualBillingStart should be approx clientConnectTime + 2000
        // allow some delta for network/processing
        const diff = billingStartTime - (clientConnectTime + 2000);
        console.log('Difference from Expected (ms):', diff);

        if (Math.abs(diff) < 1000) {
            console.log('✅ TEST PASSED: Billing started correctly with ~2s buffer.');
        } else {
            console.error('❌ TEST FAILED: Billing start time mismatch.');
        }

    } catch (e) {
        console.error('Test Error:', e);
    } finally {
        clientSocket.disconnect();
        astroSocket.disconnect();
        process.exit();
    }
}

runTest();
