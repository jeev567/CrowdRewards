# PayPal Sandbox checkout — v0.5

Configure the ignored `src/main/resources/application-local.yml`:

```yaml
paypal:
  sandbox:
    client-id: "YOUR_SANDBOX_CLIENT_ID"
    client-secret: "YOUR_SANDBOX_CLIENT_SECRET"
  return-url: "http://localhost:5173/"
```

Run `gradle bootRun --args='--spring.profiles.active=local'` in backend and `npm run dev` in frontend.

Create a campaign with a price, then click **Join with PayPal**. Log in using a **PayPal Sandbox personal buyer account** (not the merchant/business account), approve the payment, and return to the app. The app captures the order server-side and increments the participant count after checking PayPal's completed capture amount and currency.

Existing campaigns are migrated with `price_cents=0` and must be edited to set a price before checkout. `checkout_orders` tracks PayPal order IDs and capture IDs; repeated successful capture callbacks don't increment the counter twice.

**Scope and caveats:** Sandbox only. No actual refunds yet (v0.6). This MVP does not implement PayPal webhooks, buyer authentication, merchant PayPal onboarding, or production reconciliation. The configured PayPal account receives Sandbox payments; it is not yet dynamically tied to the campaign merchant. Never commit credentials. Automated end-to-end Sandbox tests have not been run.
