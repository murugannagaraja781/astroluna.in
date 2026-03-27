# Astro5Star | Super Admin Dashboard Master Guide

This guide provides a 100% comprehensive overview of all 15 menus in the Astro5Star Super Admin Dashboard, including their deep technical workflows and business logic.

---

## 🏗 System Architecture Overview
- **Frontend**: Tailwind CSS + FontAwesome + Sortable.js (Vanilla JS)
- **Real-time**: Socket.io for all live updates (online/busy status, notifications)
- **Database**: MongoDB (Mongoose) with capped collections for logs.
- **Broadcast**: Firebase Cloud Messaging (FCM) v1.

---

## 📂 Navigation Menus & Workflows

### 1. Overview (Dashboard)
- **Description**: High-level real-time KPI overview.
- **Logic**: 
  - Aggregates "Total Revenue", "Astro Payouts", and "Admin Profit" from the `BillingLedger`.
  - Shows real-time counts of Online Clients vs. Online Astrologers using `presenceHandler`.
  - Calculates "Earnings Today" by filtering ledger records with `Date.now()` start.

### 2. Join Requests (Requests)
- **Description**: Onboarding gate for new astrologers.
- **Deep Workflow**:
  - `admin-approve-astrologer` socket event.
  - **Duplicate Check**: The system MUST check if any *approved* astrologer already has this phone number before approving a new one.
  - On approval, it clears `approvalStatus='pending'`, sets `isVerified=true`, and sends a FCM `PROFILE_UPDATED` pulse to the astrologer's app.

### 3. Photo Approvals (Photo Approvals)
- **Description**: Moderation queue for profile picture changes.
- **Logic**:
  - When an astrologer updates their photo in the app, it's saved in `pendingImage` (photoStatus='pending').
  - Admin clicks "Approve" -> `pendingImage` is moved to `image`, and the record is cleared.

### 4. Astrologers (Astrologers)
- **Description**: Global registry of all approved astrologers.
- **Workflows**:
  - **Pricing**: Update `price` per minute for audio/video/chat.
  - **Ranking**: Update `displayOrder`. Higher number = top-ranked in the mobile app list.
  - **Availability**: Manually toggle "Online/Offline" if an astrologer forgets.

### 5. Clients (Users)
- **Description**: Management of the platform's user base.
- **Workflows**:
  - **Wallet Recharge**: Admin can manually edit `walletBalance` or `superWalletBalance`.
  - **Activity Log**: View "Session History" for a specific user to handle complaints.
  - **Security**: Block/Unblock users from the platform.

### 6. Withdrawals (Withdrawals)
- **Description**: Payout processing module.
- **Workflow**:
  - Astrologers request withdrawals from their app.
  - Admin views "Pending" -> Validates bank/UPI details -> "Mark as Paid".
  - System deducts the amount from `walletBalance` in DB (if not already deducted during request).

### 7. Performance (Performance)
- **Description**: Advanced analytics and revenue trends.
- **Logic**: Uses MongoDB `$group` and `$sum` on `BillingLedger` to provide weekly/monthly charts.

### 8. History (Ledger)
- **Description**: Detailed financial audit trail.
- **Deep Workflow**:
  - Shows every transaction with exact "Client Phone", "Astro Name", "Billable Seconds", and "Admin Margin".
  - **Reason Filtering**: Allows auditing "insufficient_funds", "slab_payouts", etc.

### 9. Commission (Slabs)
- **Description**: The platform's profit-sharing engine.
- **Deep Workflow**:
  - **Tiered Sharing**: 
    - Slab 1 (0-5 min): 30% to Astro.
    - Slab 2 (5-10 min): 35% to Astro.
    - Slab 3 (10-15 min): 40% to Astro.
  - Admin can update these percentages in `GlobalSettings`.

### 10. Reviews (Reviews)
- **Description**: User feedback moderation.
- **Logic**: Admin can delete reviews that contain abusive language or fake feedback.

### 11. Academy (Academy)
- **Description**: Educational portal for astrologers.
- **Workflow**: Admin uploads Video Titles + YouTube URLs. These appear in the "Academy" section of the Astrologer's app.

### 12. Broadcast (Offers)
- **Description**: Marketing and Push Notification hub.
- **Deep Workflow**:
  - Supports "Bulk Push" to all users or "Targeted Push" to selected IDs.
  - Uses `sendFcmV1Push` with custom payload fields (`offer_title`, `offer_image`).

### 13. Server Logs (Server Logs)
- **Description**: Real-time observability for DevOps.
- **Logic**:
  - Fetches from `SystemLog` collection (TTL of 15 days).
  - Displays "CRITICAL", "ERROR", and "DEBUG" logs with a terminal animation.

### 14. Banners (Banners)
- **Description**: Home-screen carousel management.
- **Workflow**:
  - Upload Creative Assets.
  - Set `order` for sequence.
  - Set `expiryDate` (Banners automatically hide after this date).

### 15. Notifications (Notifications)
- **Description**: Audit trail for system-level events.
- **Logic**: Records "Missed Calls" (when an astro doesn't answer) and "Failed Transactions" for internal monitoring.

---

## 📈 Developer Prompt 100% Logic Clone
"Clone the Astro5Star Super Admin Dashboard. Implement a modular Vanilla JS frontend with 15 tabs. Technical requirements: Tiered Commission System (Slabs), Real-time socket status updates, WebSocket-based Server Log terminal, and persistent Astrologer approval check for unique phone numbers."
