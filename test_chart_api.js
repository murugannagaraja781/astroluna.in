const fetch = require('node-fetch');

async function testChartApi(url) {
    const payload = {
        "date": "2026-04-03",
        "time": "10:00",
        "lat": 13.0827,
        "lng": 80.2707,
        "timezone": 5.5
    };

    try {
        console.log(`\nTesting ${url} with payload:`, payload);
        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        });

        console.log('Response status:', response.status);
        const data = await response.json();
        
        if (data && data.success) {
            console.log('API responded successfully!');
            if (data.data && data.data.planets) {
                console.log('Planet sample:', data.data.planets[0].name, 'at', data.data.planets[0].degreeFormatted);
            }
        } else {
            console.log('API responded with failure:', data);
        }
    } catch (error) {
        console.error('API Test Failed:', error.message);
    }
}

async function runTests() {
    // 1. Test Local
    // await testChartApi('http://localhost:3000/api/rasi-eng/charts/full');
    
    // 2. Test Remote (Live)
    await testChartApi('https://astroluna.in/api/rasi-eng/charts/full');
}

runTests();
