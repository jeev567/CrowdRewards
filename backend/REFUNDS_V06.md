# CrowdRewards v0.6 — Automatic partial refunds (Sandbox)

When a successfully captured payment brings a campaign to its buyer goal, CrowdRewards creates one durable refund work item per completed capture. Each refund is the original captured amount multiplied by the campaign discount percentage, rounded down to cents. A scheduled worker processes up to 25 items every minute.

- PayPal endpoint: POST /v2/payments/captures/{capture_id}/refund with a USD amount.
- The worker persists a unique refund request ID per capture, and uses that ID for retries.
- Completed and pending PayPal refund IDs are stored in SQLite. Pending refunds are polled using GET /v2/payments/refunds/{refund_id}.
- GET /api/campaigns/{campaignId}/refunds returns statuses (QUEUED, PROCESSING, RETRY, PENDING, COMPLETED, FAILED or CANCELLED).
- Campaigns with any checkout orders cannot be edited or deleted, preserving discount and price terms.
- On startup, the migration queues qualifying completed captures that were not previously enqueued.

## Manual Sandbox test

1. Pull main, start backend with the local profile and the React frontend.
2. Create a **new** campaign with price $10.00, discount 20%, and goal 2.
3. Complete two separate PayPal Sandbox purchases.
4. Verify the campaign reads 2 joined and check GET /api/campaigns/{id}/refunds. Two $2.00 refunds should appear, initially QUEUED or PROCESSING and then COMPLETED (or PENDING depending on PayPal).
5. Confirm refund transactions in the Sandbox merchant account. Restart the backend and verify no additional refunds are created.

## Limitations before production

Sandbox/demo only. There is no merchant onboarding or buyer authentication, webhook reconciliation, administrator authorization, dead-letter queue, exponential backoff, or capacity reservation for simultaneously approved orders. The status endpoint exposes capture IDs and should be authenticated before production. PayPal API idempotency retention is finite; ambiguous failures require reconciliation before retrying beyond that window. Refund failures are recorded but need operator handling. PayPal may reject refunds due to account balance, permissions, or payment state. Do not use with live credentials.
