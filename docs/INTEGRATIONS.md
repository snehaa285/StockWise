# StockWise integrations

## Email invitations

Invitations require an SMTP-compatible provider. Until `SPRING_MAIL_HOST` and
`STOCKWISE_EMAIL_FROM` are configured, invitation creation fails explicitly;
the application does not report an undelivered invitation as sent.

Configure these values in the backend process environment or a secret manager:

- `SPRING_MAIL_HOST`
- `SPRING_MAIL_PORT` (normally `587`)
- `SPRING_MAIL_USERNAME`
- `SPRING_MAIL_PASSWORD`
- `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH` (normally `true`)
- `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` (normally `true`)
- `STOCKWISE_EMAIL_FROM`
- `STOCKWISE_PUBLIC_URL` (public frontend origin used to construct invitation links)

Use the provider's TLS-enabled SMTP endpoint and credentials scoped to sending
StockWise invitation messages. Never commit real values or place them in the
frontend environment.

## Microsoft OneDrive workbook synchronization

Synchronization is one-way: StockWise remains the source of truth. The backend
creates and updates one `StockWise - <business>.xlsx` workbook in the connected
owner's OneDrive. It contains business information, products, categories,
product attributes, locations, current balances, inventory movement history,
receipts, adjustments, transfers, reorder levels, low-stock alerts, and expiry
alerts. Each worksheet has a header row and an Excel table.

Business, catalog, location, inventory, and reorder-level writes enqueue a
transactional outbox item. A background worker generates the current
business-scoped snapshot and uploads it to Microsoft Graph. Pending work is
coalesced per business, retries use capped exponential backoff, stale processing
items can be reclaimed after application restart, and exhausted failures remain
visible for manual retry. Reauthorization failures are surfaced so the owner
can reconnect. The frontend never receives Microsoft access or refresh tokens.

### Microsoft Entra app registration

1. Register a Microsoft Entra application and add a **Web** redirect URI exactly
   matching `MICROSOFT_REDIRECT_URI` (default:
   `http://localhost:8080/api/v1/excel-sync/microsoft/callback`).
2. Create a client secret and store its value only in the backend secret manager
   or process environment.
3. Configure delegated Microsoft Graph permissions `User.Read` and
   `Files.ReadWrite`. The authorization request also asks for `offline_access`
   to enable server-side refresh tokens.
4. Grant the required tenant consent and verify the app's account types include
   the Microsoft accounts StockWise owners will use.
5. Set the backend environment variables:
   - `MICROSOFT_TENANT_ID`
   - `MICROSOFT_CLIENT_ID`
   - `MICROSOFT_CLIENT_SECRET`
   - `MICROSOFT_REDIRECT_URI`
   - `STOCKWISE_FRONTEND_URL`
   - `STOCKWISE_MICROSOFT_TOKEN_ENCRYPTION_KEY`
   - optionally `STOCKWISE_EXCEL_SYNC_POLL_INTERVAL_MS` (default `5000`)
6. Generate `STOCKWISE_MICROSOFT_TOKEN_ENCRYPTION_KEY` as 32 cryptographically
   random bytes encoded with Base64. Store it outside source control and keep it
   stable; changing it makes existing encrypted tokens unreadable and requires
   owners to reconnect.
7. Start the backend with the MySQL profile and open the inventory settings page
   as the business owner to connect OneDrive. Confirm that the callback URL in
   the Microsoft app registration exactly matches the backend setting.

The database stores only AES-GCM-encrypted Microsoft tokens. OAuth state is
short-lived and stored as a hash; the PKCE verifier is encrypted. Disconnecting
removes locally stored tokens. Microsoft app registration and production
consent are operator responsibilities and are not configured by this project.

## Integration-test database

Database-backed tests require `AUTH_TEST_DB_URL`,
`AUTH_TEST_DB_USERNAME`, and `AUTH_TEST_DB_PASSWORD`. Their test guards require
the schema `stockwise_auth_test` and user `stockwise_auth_test_user` before a
connection or migration is attempted. Never point these values at `stockwise`
or a production database.

## Configuration examples

The repository root `.env.example` lists variable names and safe placeholders
only. Copy values into local environment/secret storage as appropriate; never
copy provider credentials into frontend `.env` files.
