# Aurevanta

Web application for planning work using **certainty interval estimations** (P10/P50/P90)
instead of single-point estimates. The domain centres on three-point estimates,
aggregation across tasks, and confidence bands.

Licensed GPL-3.0.

`docs/product-concept.md` holds the domain concepts and planned features — read it before
designing schema or domain logic. It is design intent, not a description of existing code.

## Layout

- `backend/` — Spring Boot 4.1 REST API (Java 25, Maven), PostgreSQL 18 + Flyway.
  Package root `eu.sonetas.aurevanta`.
- `frontend/` — React 19 + TypeScript SPA built with Vite 8.

## Commands

Backend (from `backend/`):

```bash
./mvnw -B test          # runs tests; spins up PostgreSQL via Testcontainers (needs Docker)
./mvnw spring-boot:run  # starts on :8080; docker-compose support boots compose.yaml automatically
```

Frontend (from `frontend/`):

```bash
npm run dev     # Vite dev server on :5173, proxies /api -> localhost:8080
npm run build   # tsc -b && vite build
npm run lint    # oxlint
```

Both Docker and a JDK 25+ are required for the backend test suite.

## Conventions

- **Schema is owned by Flyway.** Hibernate is set to `ddl-auto=validate`, so every schema
  change is a new versioned migration in `backend/src/main/resources/db/migration/`.
  `V1__baseline.sql` is an empty baseline; the domain schema starts at `V2`.
- **Backend tests use Testcontainers**, not an in-memory database — `TestcontainersConfiguration`
  provides a real `postgres:18` container via `@ServiceConnection`.
- `spring.jpa.open-in-view=false` — load what a request needs inside the service layer;
  do not rely on lazy loading in controllers or serialization.
- The frontend talks to the backend through the `/api` prefix and the Vite dev proxy,
  which keeps the browser same-origin so there is no CORS configuration in dev.
- Actuator exposes only `health` and `info`.
