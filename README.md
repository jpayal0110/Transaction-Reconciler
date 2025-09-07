# Transaction Reconciler (Free-Hosted)

Demo app to reconcile banking transactions vs reference data.

## Features
- CSV ingest (transactions + reference)
- Matching rules: 1) exact txn_id ↔ ref_txn_id, 2) amount + txn_date
- React UI to upload and view match report
- Free hosting: Render (backend + Postgres) + Vercel (frontend)
- CORS enabled for cross-domain

## CSV formats
**transactions.csv**
```
txn_id,source,amount,txn_date
T1001,bankA,100.00,2025-08-01
```
**references.csv**
```
ref_txn_id,source,amount,txn_date
T1001,ledger,100.00,2025-08-01
```
Date: `YYYY-MM-DD`. Amount numeric.

## Local dev (optional)
Backend:
```bash
cd backend
# set local Postgres or use Docker
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/reconciler
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=postgres
./mvnw spring-boot:run || mvn spring-boot:run
```

Frontend:
```bash
cd frontend
export REACT_APP_API_BASE_URL=http://localhost:8080
npm install
npm start
```

## Deploy (free)
- **Render**: Click *New → Blueprint*, use `render.yaml`. It provisions free Postgres + the backend service.
- **Vercel**: New Project from `frontend`, set `REACT_APP_API_BASE_URL` to your Render backend URL, deploy.

## Security note
This is a public demo. For production, lock down CORS, add auth (JWT/OAuth), and use `ddl-auto=validate`.
