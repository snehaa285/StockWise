# StockWise

StockWise is a full-stack SaaS inventory-management web application. The stack comprises a React/TypeScript frontend (Vite, Tailwind CSS, Lucide React), a Java 21 / Spring Boot modular-monolith backend, and MySQL.

The project implements:
- **Phase 1:** Project foundation, health probes, CORS configuration.
- **Phase 2:** Custom Spring Boot JWT & session authentication, opaque refresh tokens with rotation and replay detection, session-bound CSRF, HttpOnly cookies.
- **Phase 3:** Tenant business management, OWNER / ADMIN / MEMBER role-based access control, membership audit trails.
- **Phase 4:** Business-scoped product catalog, categories, and dynamic typed category attributes (TEXT, INTEGER, DECIMAL, BOOLEAN, DATE, SINGLE_SELECT, MULTI_SELECT).
- **Phase 5:** Inventory core: locations, batches, stock balances (`DECIMAL(18,3)`), receipts, adjustments, atomic same-business transfers, immutable movement ledger, idempotent operations with replay conflict detection.
- **Phase 6:** Inventory intelligence: per-location reorder levels, live low-stock alerts, replenishment shortfalls, near-expiry horizons, movement trends, stock reports.
- **Phase 7:** Operational frontend: business switching, catalog management, inventory movements, stock adjustments/transfers, alerts, and analytics.
- **Phase 8:** Firebase Authentication / Google Sign-In, account linking, active session management, and login audit logging.
- **Phase 9:** Email-based, single-use business invitations; category archive/delete lifecycle; and durable, one-way Microsoft OneDrive workbook synchronization.

Open architecture questions and boundaries are recorded in [docs/ARCHITECTURE_DECISIONS.md](docs/ARCHITECTURE_DECISIONS.md).

## Requirements

- Node.js 22.12 or newer.
- Java 21 (JDK 21).
- Spring Boot 3.5.16.
- MySQL 8 for the `mysql` profile.

## Run locally

### Backend
Start using the local profile (no database required for health):
```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

With MySQL profile (requires MySQL 8 running):
```powershell
$env:SPRING_PROFILES_ACTIVE="mysql"
$env:DB_URL="jdbc:mysql://localhost:3306/stockwise?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:DB_USERNAME="your_username"
$env:DB_PASSWORD="your_password"
$env:STOCKWISE_JWT_SIGNING_SECRET="<Base64-encoded 32-byte key>"
$env:STOCKWISE_CSRF_HMAC_SECRET="<Base64-encoded 32-byte key>"
$env:STOCKWISE_AUTH_REFRESH_COOKIE_SECURE="false"
$env:STOCKWISE_AUTH_REFRESH_COOKIE_SAME_SITE="Lax"
cd backend
.\mvnw.cmd spring-boot:run
```

### Frontend
```powershell
cd frontend
npm ci
npm run dev
```
Open `http://localhost:5173`.

## Firebase and Google Sign-In Setup

The Spring Boot backend remains authoritative for all application sessions, authorization, and business membership. Firebase acts as an identity provider.

### 1. Firebase Project Configuration
1. In the [Firebase Console](https://console.firebase.google.com/), create a project.
2. Enable **Authentication** > **Sign-in method** > **Google**.
3. Under **Project Settings** > **Service Accounts**, click **Generate new private key** and save the JSON key file securely.
4. Under **Project Settings** > **General** > **Your apps**, add a Web App (`</>`) and copy the configuration snippet.

### 2. Backend Environment Variables
```env
FIREBASE_PROJECT_ID=your-firebase-project-id
GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
```
If `FIREBASE_PROJECT_ID` is unset, Google ID token verification will fail safely with HTTP 503 (`Google sign-in unavailable`).

### 3. Frontend Environment Variables (`frontend/.env`)
```env
VITE_API_BASE_URL=http://localhost:8080
VITE_FIREBASE_API_KEY=your-api-key
VITE_FIREBASE_AUTH_DOMAIN=your-project.firebaseapp.com
VITE_FIREBASE_PROJECT_ID=your-project-id
VITE_FIREBASE_STORAGE_BUCKET=your-project.appspot.com
VITE_FIREBASE_MESSAGING_SENDER_ID=your-sender-id
VITE_FIREBASE_APP_ID=your-app-id
```

## Build and Test

### Frontend
```powershell
cd frontend
npm run typecheck    # TypeScript compiler check
npm run lint         # Oxlint check
npm run test:run     # Vitest suite
npm run build        # Production Vite build
```

### Backend
```powershell
cd backend
.\mvnw.cmd test      # Runs all unit & integration tests
```
Database-backed tests require `AUTH_TEST_DB_URL`, `AUTH_TEST_DB_USERNAME`, and
`AUTH_TEST_DB_PASSWORD`, and reject any schema/user other than the isolated
`stockwise_auth_test` / `stockwise_auth_test_user`. Never point test variables
at `stockwise` or a production database. The guarded migration tests validate
the current migration chain through V11.

Email and Microsoft OneDrive setup requirements are documented in
[docs/INTEGRATIONS.md](docs/INTEGRATIONS.md). Neither integration claims
successful email delivery or OneDrive synchronization when the corresponding
provider/configuration is unavailable.

## Security and Architecture Boundaries

- **Authentication Authority:** Spring Boot generates and validates application access JWTs and session tokens; Firebase verifies identity only.
- **Tenant Isolation:** Every operation enforces business membership and role checks on the backend.
- **Movement Immutability:** Stock movement records and login audit history entries are append-only.
- **Database Safety:** Flyway migrations V1–V11 manage schema state. Database-backed automated tests enforce the isolated `stockwise_auth_test` database and never target the development or production databases.
- **Explicit Unresolved Decisions:**
  - Inventory valuation and cost basis calculations (deferred).
  - FIFO/FEFO automatic stock selection (deferred; manual batch selection used).
  - Serial-tracking scope (deferred).
  - Report export formats (deferred; paginated JSON provided).
