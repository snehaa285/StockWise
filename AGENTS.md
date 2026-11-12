# StockWise Project Instructions

## Approved scope and decisions

- Treat the PRD, FRD, TDD, and Master Development Prompt supplied by the project owner as the approved requirements. They are not yet checked into this workspace; do not silently recreate, modify, remove, or reinterpret them.
- Read `docs/ARCHITECTURE_DECISIONS.md` before changing architecture or persistence. It records confirmed scope and open decisions.
- Preserve the approved React/TypeScript, Java 21/Spring Boot, Maven, and MySQL stack and the modular-monolith architecture. Explain a necessary deviation and obtain approval before making it.
- MVP transfers may only be between locations belonging to the same business. Cross-business redistribution is future scope.
- Do not finalize the default inventory bucket model, dynamic attribute storage, tracking-setting precedence, FIFO/FEFO selection, or valuation rules without explicit approval.

## Engineering boundaries

- Keep business rules, inventory calculations, and authorization on the backend. The frontend must never connect directly to MySQL.
- Every tenant-scoped read and write must enforce business membership and role authorization on the backend; a client-supplied business ID is not authorization.
- Keep stock/balance changes, movement records, and audit records atomic and consistent. Do not mutate historical movement/audit records.
- Use DTOs at API boundaries, migrations for schema changes, explicit error handling, and environment-based secrets. Never commit credentials or expose them in logs/errors.
- Preserve archive/history behavior; do not destructively delete business records with history.
- Keep feature dependencies directed through public module/application interfaces. Avoid microservices and unapproved MVP features.
- Do not add business-domain schema or later-phase functionality during foundation work.

## Validation and reporting

- Add focused tests for behavior changes and run the relevant build/test checks. Report exact commands and actual outcomes; do not claim unrun checks passed.
- Keep the project runnable and update setup/documentation commands when they change.
- Record only work actually completed in the development diary; never fabricate dates, tests, or results.
