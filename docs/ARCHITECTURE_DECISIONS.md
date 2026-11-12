# StockWise Architecture Decisions and Open Questions

This file records Phase 0 decisions supplied or approved by the project owner and decisions intentionally left open. It does not replace the PRD, FRD, or TDD, which were provided in chat and have not yet been added to this repository.

## Confirmed for Phase 1

- Product is a responsive web application; no native Android/iOS application.
- Frontend: React, TypeScript, Vite, Tailwind CSS, React Router, Axios, Recharts, React Hook Form, Zod, and Lucide React.
- Backend: Java 21, Spring Boot, Spring Web, Spring Data JPA, Spring Security/JWT in its later phase, and Maven.
- Database: MySQL.
- Backend is a modular monolith; frontend communicates with it over REST. The browser never connects directly to MySQL.
- MVP transfers are restricted to locations belonging to the same business. Cross-business redistribution is a future enhancement and is excluded from MVP.
- Phase 1 is foundation only. It must not implement authentication, business management, products, inventory behavior, dashboards, or other later-phase features.
- Configure a database migration mechanism, but do not create the business-domain schema until its design is approved.

## Explicitly unresolved — do not assume during implementation

| Decision | Current status |
|---|---|
| Inventory valuation and potential-loss cost basis, including treatment of returns/adjustments | Unresolved |
| Inventory report export formats | Unresolved; no export format is implied |
| Serial-tracking MVP scope | Deferred and excluded from the approved Phase 5 model |

Slow/dead/overstock classification and valuation-based insights are not part of the approved Phase 6 scope unless their underlying rules are decided.

## Confirmed for Phase 3 — Business Membership and Roles

The project owner approved this policy on 2026-11-11:

- Roles are `OWNER`, `ADMIN`, and `MEMBER`.
- Business creation and assignment of exactly one active `OWNER` happen in one transaction. Ownership transfer is deferred.
- `OWNER` has full business control; `ADMIN` manages operational products, categories, locations, inventory, stock movements, and operational settings but cannot manage memberships, change roles, or transfer ownership. `MEMBER` can view products, categories, and inventory and perform approved ordinary inventory operations, including receipt, adjustment, and same-business transfer.
- Only `OWNER` may invite members, remove members, or change member roles. Owners cannot remove or demote themselves; only one active Owner is permitted per business. Multiple Admins and Members are allowed.
- Membership is granted only after the recipient authenticates as the invited email address and accepts its single-use, expiring email link. Direct user-ID membership creation is not supported. Removing membership immediately removes business access.
- Every business-scoped operation must enforce membership and role authorization on the backend. Client-supplied business IDs are never authorization.

## Confirmed for Phase 4 — Catalog Attributes and Tracking

The project owner approved these decisions on 2026-11-11:

- Attribute definitions are category-specific and scoped to one business. Each product may use only definitions belonging to its assigned category. Normalized attribute names are unique within a category.
- Persist definitions, dropdown options, and product values in relational tables. Supported types are `TEXT`, `INTEGER`, `DECIMAL`, `BOOLEAN`, `DATE`, `SINGLE_SELECT`, and `MULTI_SELECT`.
- `TEXT` values are trimmed, limited to 1,000 characters, and may have minimum/maximum length constraints. Integer values must be whole numbers and may have minimum/maximum constraints. Decimal values support at most 18 digits and scale 6, with optional minimum/maximum constraints. Boolean values are true/false only. Date values use ISO `YYYY-MM-DD` and may have minimum/maximum dates. Single- and multi-select values must reference predefined active options; multi-select rejects duplicates and may have minimum/maximum selection counts.
- Required attributes must be present and valid when a product is saved; optional attributes may be absent. The backend validates types, required status, constraints, and options without coercion.
- After a definition has product values, its type, required status, validation constraints, and dropdown options are frozen. Only display name/description edits remain allowed. Definitions with values are archived, not deleted; unused definitions may be deleted. Used options may not be removed or modified. Existing values are never silently converted, discarded, or invalidated; any future structural conversion requires explicit migration and revalidation.
- A product may change category only if it has no dynamic attribute values. Business-wide attribute definitions, JSON attributes, files/images, formulas, nested objects, and external API-backed dropdowns are out of MVP scope.
- Categories supply defaults for batch and expiry tracking. Products may override either setting; an unset product value inherits the category default. Serial tracking remains unresolved and is not included by this decision.

## Confirmed for Phase 5 — Inventory Core

The project owner approved these inventory policies before Phase 5 implementation:

- Locations are flat, named resources owned by one business. Inventory transfers are restricted to locations in the same business.
- Quantity storage uses decimal quantities with at most three fractional digits and a maximum of 15 integer digits (`DECIMAL(18,3)`). Negative balances and stock-outs exceeding the available balance are rejected.
- A non-batch-tracked product has one implicit stock bucket per product/location. A batch-tracked product has one stock bucket per product batch/location.
- A batch code identifies a product batch shared across its locations. Receiving the same product batch at another location increases that location's balance for the same batch. When expiry tracking is enabled, every batch requires an expiry date.
- Batch selection for stock-outs is manual. FIFO/FEFO selection is not performed.
- Receipt, adjustment, and transfer operations require an idempotency key unique within the business. Keys are retained indefinitely. Replaying a key with the same normalized request returns the original result; reusing it for a different request is a conflict.
- Transfers are immediate atomic operations: decrease the source and increase the destination in one transaction, preserve product and batch identity, and write immutable movement records. There is no in-transit transfer state.
- Valuation and cost calculation remain unresolved and are excluded from Phase 5 and Phase 6 implementation. Serial tracking is excluded.

## Confirmed for Phase 6 — Inventory Intelligence and Alerts

The project owner approved these alert policies on 2026-11-11:

- Reorder levels are configured per business, product, and location. Compare each location's available quantity independently; do not aggregate stock across locations for low-stock alerts.
- Low-stock alerts are computed from current persisted inventory and the configured reorder level. An alert is active when available stock is less than or equal to the reorder level, updates with inventory changes, disappears when stock rises above the threshold, and reappears if stock later falls to/below it. Do not persist alert incidents, acknowledgements, or manual resolution history.
- Near-expiry alerts include expiry dates from today through the next 30 days, inclusive. Expired batches are reported separately and are not included in upcoming near-expiry alerts.
- The near-expiry horizon is independently configurable per business, with a default of 30 days. `OWNER` and `ADMIN` may change it; accepted values are 1–3650 days. Changes immediately affect computed alerts and reports.
- Inventory valuation/cost and FIFO/FEFO-derived insights remain excluded until separately approved.

For the current implementation, expiry-window date boundaries use UTC because the business model has no timezone setting. Business-specific timezones would require a separate approved change.

## Phase 1 implementation decisions

- The default `local` profile starts the API without requiring MySQL, so health/API integration can be verified independently.
- The opt-in `mysql` profile requires DB environment variables and enables the MySQL driver, JPA, and Flyway. No domain migration is included yet.
- The local CORS origin defaults to `http://localhost:5173`; production origins must be explicitly configured.
- The health endpoint is infrastructure-only and does not imply any business functionality.
- Spring Boot 3.5.16 is selected for the foundation because the TDD specified Spring Boot but did not pin its version.

## Follow-up clarification

- Phase 5 implemented flat business-owned locations and immediate atomic same-business transfers. Cross-business redistribution remains future scope.
- Phase 6 policy decisions are now confirmed. Use UTC as the canonical date basis for expiry-window calculations unless a business timezone is added through a later approved change.

## Confirmed for the invitations and OneDrive feature phase

- Business invitations are email-address based, single use, hash-only at rest, and expire after seven days. Only the authenticated recipient controlling the invited normalized email may accept; membership is not granted before acceptance. Email delivery is provider-backed and reports an error when SMTP is unavailable.
- A category referenced by products is archived rather than deleted. An unused category may be permanently deleted; archived categories remain available to history but cannot be selected for new products.
- OneDrive synchronization is one-way from StockWise to Excel. MySQL remains authoritative; Excel edits never update StockWise. Workbook changes are generated asynchronously from a durable transactional outbox and scoped to one business.
- Microsoft tokens are held only by the backend and encrypted at rest. Owners control connection, reconnection, disconnection, and manual synchronization. Microsoft app registration and SMTP credentials remain external configuration.
