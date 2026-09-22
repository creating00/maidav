# Financing plan parity

## Objective

Make financing installment amounts consistent across product quick view, sales, quotes, and PDF/Excel price-list exports. Remove the cash installment alternative from the 144-day daily plan.

## Problem and rationale

Browser calculators round up to the next $50, while the export rounds to the nearest $50. The difference causes customer-visible inconsistencies for certain product values and terms. Daily cash installments are no longer offered because of due-date constraints.

## Authorized scope

- Financing calculation and display paths for products, sales, quotes, and price-list exports.
- Daily installment persistence validation.
- Focused automated tests.

## Constraints

- Keep weekly and monthly cash alternatives unchanged.
- Do not alter historical sales or installments.
- Follow the existing MVC/service/template architecture.

## Delivery

- Strategy: ask-on-risk.
- Forecast: one cohesive bug-fix work unit; expected below 400 authored changed lines.

## Tasks

- [x] FIN-01 — Align the export installment rounding with the application pricing rule and add parity coverage. Route: delegated; trigger: implementation spans backend and tests.
- [x] FIN-02 — Remove 144-day cash installment calculation, display, sharing text, and persistence from active product/sale/quote flows. Route: delegated; trigger: implementation spans multiple templates and service paths.
- [x] FIN-03 — Run focused checks and commit the completed work unit. Route: delegated; trigger: execution and multi-file change.

## Acceptance criteria

- [x] A common base/configuration yields identical 144-day, 13-week, 4-month, and 8-month installment values in application flows and PDF/Excel exports.
- [x] No active UI, shared quote, or price-list export presents cash pricing for the 144-day daily plan.
- [x] New daily installments do not persist a cash amount; weekly and monthly behavior remains available.
- [x] Focused tests pass and evidence is recorded below.

## Progress and evidence

Completed 2026-09-22.

- FIN-01: financing export installments and their cash alternatives now reuse `CreditPaymentPricingSupport.roundUpToFifty`; parity coverage compares 144 days, 13 weeks, 4 months, and 8 months against `QuoteCalculator` using a non-product-specific fixture.
- FIN-02: product quick-view, shared WhatsApp text, and sales form no longer calculate or display a daily cash installment; daily schedule creation discards supplied manual cash values before persistence. Weekly and monthly cash values remain unchanged.
- FIN-03 original verification: `mvn "-Dtest=ExportDocumentServiceTest,SaleServiceImplTest" test` passed: ExportDocumentServiceTest (5 tests) and SaleServiceImplTest (14 tests), 0 failures/errors.
- Independent re-run: the same Maven command exited 1 before test discovery or execution because it could not resolve `spring-boot-starter-parent:3.5.9` from Maven Central (`Permission denied`). This is a network/permission limitation, not a test failure; no test totals were produced on that re-run.

## Next step

No implementation steps remain. Work-unit commit recorded in Git history: `fix(financing): align export plans and remove daily cash`.
