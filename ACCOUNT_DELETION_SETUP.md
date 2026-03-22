# Account Deletion Feature - Setup Guide

## Overview
Complete account deletion system with user-facing request form and admin management panel.

## Files Created

### 1. User-Facing Page
**File**: `public/delete-account.html`
**URL**: `https://astroluna.in/delete-account.html`

**Features**:
- Clean, responsive UI with gradient background
- Form to submit deletion request with email/phone
- Optional reason field
- Success/error message handling
- AJAX form submission (no page reload)
- Links to privacy policy and terms

### 2. Admin Management Panel
**File**: `public/admin/deletion-requests.html`
**URL**: `https://astroluna.in/admin/deletion-requests.html`

**Features**:
- View all deletion requests
- Filter by status (pending/completed/rejected)
- Approve or reject requests
- Add admin notes
- Auto-refresh every 30 seconds
- Clean table interface

## Database Schema

### AccountDeletionRequest Model
```javascript
{
  requestId: String (unique),
  userIdentifier: String (email or phone),
  userId: String (if found in database),
  reason: String,
  status: String (pending/approved/rejected/completed),
  requestedAt: Date,
  processedAt: Date,
  processedBy: String (admin userId),
  notes: String (admin notes)
}
```

## API Endpoints

### 1. Submit Deletion Request
**Endpoint**: `POST /api/delete-account-request`

**Request Body**:
```json
{
  "user_identifier": "9000000001",
  "reason": "No longer need the service"
}
```

**Response**:
```json
{
  "ok": true,
  "message": "Account deletion request submitted successfully",
  "requestId": "uuid"
}
```

**Features**:
- Validates user identifier
- Checks for existing pending requests
- Links to user account if found
- Creates deletion request in database

### 2. Get Deletion Requests (Admin)
**Endpoint**: `GET /api/admin/deletion-requests?status=pending`

**Query Parameters**:
- `status` (optional): pending, completed, rejected

**Response**:
```json
{
  "ok": true,
  "requests": [
    {
      "requestId": "uuid",
      "userIdentifier": "9000000001",
      "userId": "user-uuid",
      "reason": "No longer need",
      "status": "pending",
      "requestedAt": "2026-02-08T...",
      "processedAt": null,
      "processedBy": null,
      "notes": null
    }
  ]
}
```

### 3. Process Deletion Request (Admin)
**Endpoint**: `POST /api/admin/process-deletion`

**Request Body**:
```json
{
  "requestId": "uuid",
  "action": "approve",
  "adminUserId": "admin-uuid",
  "notes": "Processed as requested"
}
```

**Response**:
```json
{
  "ok": true,
  "message": "Request approved and account deleted"
}
```

**Actions**:
- `approve`: Deletes user and all related data
- `reject`: Marks request as rejected

## Data Deletion Process

When a request is approved, the following data is deleted:

1. **User Account**: Main user record
2. **Sessions**: All consultation sessions
3. **Chat Messages**: All chat history
4. **Payments**: Payment records
5. **Billing Ledger**: Billing history
6. **Pair Month**: Monthly consultation data
7. **Withdrawals**: Withdrawal records (for astrologers)

## Usage Instructions

### For Users:
1. Visit `https://astroluna.in/delete-account.html`
2. Enter email or phone number
3. Optionally provide reason
4. Click "Request Account Deletion"
5. Wait for admin to process (24-48 hours)

### For Admins:
1. Visit `https://astroluna.in/admin/deletion-requests.html`
2. View pending requests
3. Click "Approve" to delete account or "Reject" to deny
4. Add notes if needed
5. Confirm action

## Security Considerations

1. **Verification**: System checks if user exists before creating request
2. **Duplicate Prevention**: Prevents multiple pending requests for same user
3. **Audit Trail**: Tracks who processed request and when
4. **Irreversible**: Approved deletions cannot be undone
5. **Admin Only**: Processing requires admin access

## Testing

### Test Deletion Request:
```bash
curl -X POST https://astroluna.in/api/delete-account-request \
  -H "Content-Type: application/json" \
  -d '{
    "user_identifier": "9000000001",
    "reason": "Testing deletion"
  }'
```

### Test with Test Accounts:
- Client: `9000000001` (OTP: 0101)
- Astrologer: `8000000001` (OTP: 0101)

## Compliance

This feature helps comply with:
- **GDPR**: Right to erasure (Article 17)
- **CCPA**: Right to deletion
- **Google Play Store**: Data deletion requirements
- **Apple App Store**: Account deletion requirements

## Next Steps

1. ✅ Database schema created
2. ✅ User-facing form created
3. ✅ Admin panel created
4. ✅ API endpoints implemented
5. ⏳ Add authentication to admin panel
6. ⏳ Add email notifications
7. ⏳ Add to app settings menu

## Notes

- Deletion requests are stored permanently for audit purposes
- Admin notes are visible in the admin panel
- Users can check status by contacting support
- Consider adding email notifications for status updates
