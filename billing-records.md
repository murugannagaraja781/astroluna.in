---
description: How to audit and manage Billing Records in the Super Admin Dashboard
---

# Billing Records Audit Workflow

This workflow describes how to verify, filter, and audit transaction records in the Astro5Star platform.

## 1. Accessing the Ledger
1. Log into the **Super Admin Dashboard**.
2. Click on the **Billing Records** (or Ledger) tab in the sidebar.
3. You will see a platform-wide transaction audit trail sorted by date.

## 2. Filtering Records
- **Search**: Use the search bar to find specific `sessionId` or `reason` (e.g., "slab_2_payout", "insufficient_funds").
- **Date Range**: Use the start and end date pickers to view transactions for a specific period (e.g., current month).
- **Sort**: Click on column headers (e.g., "Time") to toggle sorting order.

## 3. Data Definitions
- **Total Paid**: Total amount deducted from the client's wallet (including Super Wallet).
- **Astro Profit**: The share credited to the astrologer based on the slab rates.
- **Admin Margin**: The net profit kept by the platform (`Total Paid - Astro Profit`).
- **Duration**: The actual billable time recorded for the session.

## 4. Manual Adjustment Prompt (Example)
> [!TIP]
> Use this prompt if you need a detailed CSV or custom report of these records.

**Prompt for Assistant:**
"Analyze the billing ledger for the date range [START_DATE] to [END_DATE]. Identify any sessions with a duration longer than 30 minutes where the Admin Margin is less than 50%. Provide a summary of the top 5 highest-earning astrologers in this period."

## 5. Troubleshooting
- If a session name shows as "Unknown User", verify the `clientId` in the `sessions` collection matches a valid `User` record.
- If duration is 0, it indicates a call was requested but not successfully connected or billed.
