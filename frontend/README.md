# CrowdRewards React frontend (v0.1)

## Run locally
Requires Node.js 20+.

```bash
cd frontend
npm install
npm run dev
```

Open the local URL printed by Vite (typically http://localhost:5173).

## Learning React
- `src/main.jsx`: application entry point and React root.
- `src/App.jsx`: reusable components, props, state (`useState`), event handlers, lists and forms. Comments explain each concept.
- `src/styles.css`: design tokens, responsive layout, transitions and animations.

## Scope
Merchant campaign creation is an in-memory UI demo. Refreshing the page resets campaigns. There is no backend, login, OpenAI call, or PayPal transaction yet. The PayPal name is a styled text placeholder, not an official logo asset. Use approved PayPal brand assets and follow their brand guidelines before production.
