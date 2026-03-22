const fs = require('fs');
const path = require('path');

try {
    const filePath = path.join(__dirname, 'firebase-service-account.json');
    console.log('Checking file at:', filePath);

    if (!fs.existsSync(filePath)) {
        console.error('File does NOT exist.');
        process.exit(1);
    }

    const content = fs.readFileSync(filePath, 'utf8');
    const json = JSON.parse(content);

    const required = ['type', 'project_id', 'private_key_id', 'private_key', 'client_email', 'client_id', 'auth_uri', 'token_uri', 'auth_provider_x509_cert_url', 'client_x509_cert_url'];
    const missing = required.filter(k => !json[k]);

    if (missing.length > 0) {
        console.error('Missing keys:', missing.join(', '));
        process.exit(1);
    }

    console.log('JSON structure is VALID.');
    console.log('Project ID:', json.project_id);
    console.log('Client Email:', json.client_email);

} catch (err) {
    console.error('Validation Error:', err.message);
    process.exit(1);
}
