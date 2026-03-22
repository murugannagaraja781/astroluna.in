

## Testing Accounts

### 1. Test Astrologer Account
- **Phone**: `8000000001`
- **OTP**: `0101`
- **Role**: Astrologer
- **Initial Wallet Balance**: ₹5,000
- **Rate Per Minute**: ₹10
- **Status**: Online & Available

### 2. Test Client Account
- **Phone**: `9000000001`
- **OTP**: `0101`
- **Role**: Client
- **Initial Wallet Balance**: ₹1,000

### 3. Super Admin Account (Existing)
- **Phone**: `9876543210`
- **OTP**: `1369`
- **Role**: Super Admin
- **Initial Wallet Balance**: ₹100,000

## Usage Instructions

1. **Login as Astrologer**:
   - Open the app
   - Enter phone: `8000000001`
   - Click "Send OTP"
   - Enter OTP: `0101`
   - You'll be logged in as a Test Astrologer

2. **Login as Client**:
   - Open the app
   - Enter phone: `9000000001`
   - Click "Send OTP"
   - Enter OTP: `0101`
   - You'll be logged in as a Test Client

3. **Testing Consultations**:
   - Login as client on one device
   - Login as astrologer on another device
   - Client can initiate chat/call/video consultation
   - Astrologer will receive the request and can accept/reject

## Features to Test

### Client Features:
- ✅ Wallet recharge
- ✅ Browse astrologers
- ✅ Initiate consultations (chat/audio/video)
- ✅ Free horoscope generation
- ✅ Daily horoscope
- ✅ Horoscope matching
- ✅ Transaction history

### Astrologer Features:
- ✅ Accept/reject consultation requests
- ✅ Conduct consultations
- ✅ View earnings
- ✅ Wallet management
- ✅ Online/offline status toggle

## Notes

- These test accounts bypass SMS OTP sending
- OTP is hardcoded to `0101` for both test accounts
- Accounts are automatically created on first login
- No SMS charges incurred for test accounts
- Test accounts persist in the database
