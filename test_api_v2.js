const fetch = require('node-fetch');

async function testApi() {
    const payload = {
        date: "1990-01-08",
        time: "21:50",
        lat: 10.047,
        lng: 78.0903,
        timezone: 5.5
    };

    console.log("Testing POST /api/rasi-eng/charts/full ...");
    try {
        const response = await fetch('https://astroluna.in/api/rasi-eng/charts/full', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        console.log("Status Code:", response.status);
        const data = await response.json();
        
        if (data.success) {
            console.log("✅ Success! Data received.");
            console.log("Planets count:", data.data.planets.length);
            console.log("Dasha periods:", data.data.dasha.length);
        } else {
            console.log("❌ Failed! Server returned success:false", data);
        }
    } catch (err) {
        console.error("❌ Connection Error:", err.message);
    }
}

testApi();
