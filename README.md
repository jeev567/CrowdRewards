# CrowdRewards

**AI-assisted group-buying campaigns powered by PayPal checkout and threshold-based partial refunds.**

CrowdRewards is a hackathon prototype that helps merchants design group-reward campaigns: customers purchase at the advertised price, and if enough qualifying purchases are completed, eligible customers receive a partial refund. The merchant can use AI-assisted campaign planning to explore incentives and participation targets.

> **Status:** Hackathon prototype / demonstration. This README describes the product concept and intended flow; individual features and payment operations should be verified in the running environment before any production use.

## The problem

Merchants need ways to encourage customers to bring other buyers without requiring customers to coordinate a single bulk order. Traditional discounts reduce prices upfront, even if a campaign attracts little incremental demand.

## The solution

CrowdRewards ties a potential reward to collective participation:

1. A merchant configures a campaign with a product, price, purchase threshold, refund amount, and end condition.
2. An AI-assisted campaign planner helps the merchant draft campaign terms and explore incentive structures.
3. Customers participate by completing individual purchases through a PayPal checkout flow.
4. The application tracks qualifying completed purchases toward the campaign threshold.
5. Once the threshold is reached, the application can initiate the configured partial refunds for eligible purchases, subject to payment status and PayPal API constraints.
6. Customers and merchants can review campaign progress and outcomes.

**Example (illustrative, not a revenue claim):** A product costs $100. A campaign promises a $10 partial refund per eligible purchase if 50 qualifying purchases are completed. Customers pay $100 initially; if the campaign reaches its threshold and refunds succeed, eligible customers' effective price becomes $90. Refund amounts, payment fees, merchant margin, and campaign profitability must be evaluated separately.

## Why PayPal?

PayPal is part of the core transaction lifecycle, not merely a logo on the checkout screen:

- **Checkout:** Customers authorize and complete individual payments.
- **Payment verification:** Campaign participation should be counted only after an eligible, verified completed payment.
- **Refunds:** Qualifying customers can receive partial refunds against their original PayPal captures.
- **Reconciliation:** Order, capture, and refund identifiers provide an audit trail.

**Potential value for PayPal:** If these campaigns generate incremental completed purchases, they could increase PayPal payment volume. This is a hypothesis, not a measured result; it depends on merchant adoption, customer behavior, fees, and refunds.

## Where AI fits

The AI campaign assistant is intended to help merchants formulate campaigns from plain-language business goals. Example:

> "I sell a $100 product and want a campaign that rewards customers when 50 people buy."

The assistant can propose a campaign structure, explain trade-offs, and draft customer-facing terms. **AI recommendations must not directly authorize payments or refunds.** Financial constraints, thresholds, eligibility, and refund limits should be enforced by deterministic application logic.

Future opportunities include campaign simulations, margin-aware recommendations, and performance comparisons using real merchant data.

## Architecture

| Component | Technology | Responsibility |
| --- | --- | --- |
| Web app | React | Merchant and customer-facing UI |
| API | Java 21, Spring Boot, Gradle | Campaign, checkout, and refund orchestration |
| AI | OpenAI API | Campaign assistance |
| Payments | PayPal Sandbox / PayPal REST APIs | Order creation, capture, and partial refunds |
| Demo storage | SQLite | Campaign and transaction state |
| Demo deployment | AWS EC2, Nginx | Host the UI and backend |

The hackathon deployment was designed around a small EC2 instance, with Nginx serving the frontend and proxying backend requests. This is a demo-oriented architecture, not a production scalability claim.

## Payment and refund safety

A production-ready implementation should enforce all of the following:

- Verify completed PayPal captures server-side before counting purchases.
- Validate campaign threshold and deadline consistently.
- Cap each refund at the eligible captured amount and prevent duplicate refunds.
- Use persistent state and idempotent processing for retries.
- Record order, capture, and refund IDs for reconciliation.
- Protect API credentials and validate webhook authenticity where webhooks are used.
- Handle partial failures, timeouts, and refund-ineligible transactions.
- Require explicit merchant approval for campaign terms and financial exposure.

Do not treat client-side success screens as proof of captured payment.

## Suggested demo walkthrough

1. Create a merchant campaign using AI assistance.
2. Review the proposed threshold, reward, and campaign terms.
3. Complete a purchase in PayPal Sandbox.
4. Show the verified purchase reflected in campaign progress.
5. Demonstrate the threshold being reached using eligible Sandbox transactions.
6. Show a partial refund and its recorded status.
7. Explain the potential merchant and PayPal business value using transparent assumptions.

Only demonstrate steps actually supported by the deployed build; do not substitute simulated success for a real PayPal API result without labeling it.

## Running locally

The repository contains a React frontend and a Spring Boot backend. Exact scripts and configuration may evolve; check the relevant Gradle and frontend package files for the current commands.

Typical development workflow:

```bash
# Backend (from the backend directory, if the Gradle wrapper is present)
./gradlew bootRun

# Frontend (from the frontend directory)
npm install
npm run dev
```

Configure the backend with your own **PayPal Sandbox** credentials and OpenAI API key according to the application's configuration. Never commit secrets, production credentials, or customer payment data.

## Hackathon positioning

**CrowdRewards turns group participation into a purchase incentive.** It combines AI-assisted merchant campaign creation with PayPal-backed transactions and conditional rewards.

The key business hypothesis is that a merchant can acquire additional buyers through collective incentives while customers benefit when a campaign succeeds. The key technical challenge is ensuring that thresholds, captures, and partial refunds are correct, auditable, and safe.

## Roadmap

- Margin-aware AI campaign simulations
- Merchant profitability and incremental payment-volume dashboards
- Stronger refund reconciliation and retry handling
- Customer campaign sharing and conversion analytics
- Production security, compliance, observability, and scale testing

## Disclaimer

CrowdRewards is an independent hackathon prototype and is not an official PayPal product. Sandbox transactions do not move real money. Campaign examples and financial outcomes in this README are illustrative.
