# CrowdRewards backend v0.2

Java 21, Spring Boot 3.4, Gradle, JDBC and SQLite.

## Run

Requires JDK 21 and Gradle installed (a Gradle wrapper will be added later).

```bash
cd backend
gradle bootRun
```

SQLite database file `crowdrewards.db` is created in the backend working directory.

## Endpoints

- `POST /api/merchants` — JSON `{"name":"Demo Merchant","email":"demo@example.com"}`
- `GET /api/merchants`
- `POST /api/campaigns` — JSON `{"merchantId":1,"title":"Coffee","description":"Group deal","discount":15,"goal":25}`
- `GET /api/campaigns`

For local development Vite proxies `/api` to `localhost:8080`. This is a development prototype: there is no authentication, merchant ownership verification, payment integration, or rate limiting. Do not expose these endpoints publicly yet.
