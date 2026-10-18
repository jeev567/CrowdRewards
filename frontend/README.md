# CrowdRewards React frontend (v0.2)

Requires Node.js 20+ and the Spring Boot backend running on port 8080.

```bash
cd frontend
npm install
npm run dev
```

Vite forwards `/api` requests to Spring Boot through `vite.config.js`.

## Learning React
- `src/main.jsx`: application entry point
- `src/App.jsx`: components, props, `useState`, `useEffect`, form events, `fetch`, `async/await`, and API error/loading states
- `src/styles.css`: responsive styling, hover transitions and reduced-motion support

## Limitations
Development only: merchant creation and campaign creation persist to SQLite, but authentication, AI, PayPal transactions, and ownership checks are not yet implemented. Do not deploy publicly.
