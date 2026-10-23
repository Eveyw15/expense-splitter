# Expense Splitter

Group expense tracking and splitting, extracted from the ELEC5619 Collaborative Trip Planner.
Each "trip" in the code is a shared ledger: members join by invite code, record expenses
(equal / custom / by-item splits, multi-currency, compressed receipt photos), and settle up.

- `backend/` - Spring Boot 4, Java 17, Postgres (H2 for tests)
- `frontend/` - React 19 + Vite

## Run locally

```bash
cd backend && ./mvnw test          # 138 tests, H2 in-memory
cd frontend && npm ci && npm test && npm run dev
```

The backend needs `DATABASE_URL_UNPOOLED` (or `DATABASE_URL`), `JWT_SECRET` and optionally `CORS_ORIGINS`.
`neon deploy` in the repo root writes the Neon variables to `.env.local` (project linked in `neon.ts` / `.neon`).

## Deploy (free tier)

1. **Neon** (database): already provisioned; copy `DATABASE_URL_UNPOOLED` from `.env.local`
   (or the Neon console, direct connection string).
2. **Render** (backend): New > Web Service from this repo, root directory `backend`, runtime Docker.
   Set the env vars above; `JWT_SECRET` must be at least 32 random characters.
   `CORS_ORIGINS` is the Vercel URL. Free instances sleep after 15 minutes idle.
3. **Vercel** (frontend): import this repo, root directory `frontend`,
   env var `VITE_API_URL=https://<render-service>.onrender.com/api`.
