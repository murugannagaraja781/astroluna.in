const fetch = require('node-fetch');

async function testChartApi() {
    const payload = {
        "date": "2026-04-03",
        "time": "10:00",
        "lat": 13.0827,
        "lng": 80.2707,
        "timezone": 5.5
    };

    try {
        console.log('Testing /api/rasi-eng/charts/full with payload:', payload);
        const response = await fetch('http://localhost:3000/api/rasi-eng/charts/full', {
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
            console.log('Planet sample:', data.data.planets[0]);
        } else {
            console.log('API responded with failure:', data);
        }
    } catch (error) {
        console.error('API Test Failed:', error.message);
    }
}

testChartApi();
