# Local development setup

These instructions run a loopback-only sandbox with synthetic financial data. They do not connect to external payment providers or move real money.

## Prerequisites

- Docker Desktop with Docker Compose v2
- JDK 21 and Maven 3.9+ (for running API tests outside Docker)
- Node.js 22 LTS and npm
- Flutter 3.47 stable when mobile work begins

## Start PostgreSQL, Redis, and the API

From the repository root in PowerShell:

```powershell
Copy-Item .env.example .env
docker compose up -d --build
```

The database, Redis, and API ports bind only to `127.0.0.1`. The API starts with the sandbox profile and runs Flyway migrations, including a synthetic demo customer. The example database password is for disposable local development only. Use a unique local value if the machine is shared.

## Open the customer app

In a second terminal:

```powershell
cd apps/web
npm install
npm run dev
```

Open `http://127.0.0.1:3000`. Use demo mobile `00000000000` and OTP `246810`; no SMS is sent. Choose a result in checkout to inspect payment success, failure, and timeout behavior. Switch the demo role to review credit, authorize refunds, or open the owner scenario screen.

## Run the backend financial tests

```powershell
cd apps/api
mvn test
```

The PostgreSQL integration suite uses Testcontainers and runs all 20 financial scenarios, checks ledger/funding/installment invariants, and verifies a forbidden credit-status request. Docker must be running for this suite.

## Run browser journey tests

With the API and database running, use another terminal:

```powershell
cd apps/web
npm install
npm run test:e2e
```

These checks cover Persian RTL and accessibility, form validation, mobile navigation, checkout and early settlement, credit review, full cancellation, and the visible 20-scenario runner.

## Stop local infrastructure

```powershell
docker compose down
```

Named volumes remain so local data and scenario-run records are retained. Remove them only when disposable by explicitly running `docker compose down --volumes`.
