const axios = require('axios');

async function testPayment() {
    try {
        const response = await axios.post('http://localhost:3000/api/phonepe/init', {
            amount: 1,
            userId: 'test_user_123'
        });
        console.log(response.data);
    } catch (error) {
        console.error(error.response ? error.response.data : error.message);
    }
}
testPayment();
