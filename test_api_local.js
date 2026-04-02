const http = require('http');

const data = JSON.stringify({
    date: "1990-01-26",
    time: "16:21",
    lat: 10.37,
    lng: 78.88,
    timezone: 5.5,
    ayanamsa: 'Lahiri'
});

const options = {
    hostname: '127.0.0.1',
    port: 3000,
    path: '/api/rasi-eng/charts/full',
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        'Content-Length': data.length
    }
};

const req = http.request(options, (res) => {
    let body = '';
    res.on('data', (chunk) => body += chunk);
    res.on('end', () => {
        console.log('Status:', res.statusCode);
        try {
            const json = JSON.parse(body);
            console.log('Success:', json.success);
            if (!json.success) console.log('Error:', json.error);
        } catch (e) {
            console.log('Raw body:', body.substring(0, 100));
        }
    });
});

req.on('error', (e) => {
    console.error('Problem with request:', e.message);
});

req.write(data);
req.end();
