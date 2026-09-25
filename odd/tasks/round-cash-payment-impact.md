# Round cash payment impact

## Objective
Keep partial, non-expired cash payments eligible for the cash discount while recording financed debt impacts in whole $50 increments and reconciling the remainder at installment settlement.

## Problem and rationale
The existing proportional conversion can persist cents (for C-000191, $12,000 cash produces $15,092.31 financed impact). The business requires readable rounded balances without changing the total cash price for the installment.

## Authorized scope
- Production data correction limited to account `C-000191`.
- Related application code and regression tests.

## Constraints
- Apply cash pricing only while the payment date is not past the installment due date.
- Preserve the full installment financed amount and full cash amount when the installment is settled.
- Use the existing `fix-24-09` feature branch.
- TDD mode: unknown; run the projectÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢s focused applicable tests.

## Delivery strategy
- `ask-on-risk`; forecast under 400 authored lines.

## Tasks
- [x] RCI-1 Ã¢â‚¬â€ Delegated: update cash-payment allocation and projections to round partial impacts to $50 and reconcile the final allocation; add regression coverage. Acceptance: $12,000 against $32,700/$26,000 records $15,100 impact and $17,600 balance, with total settlement preserved. Checks: `mvn "-Dtest=CreditAccountServiceImplTest" test` Ã¢â‚¬â€ PASS, 23 tests, 0 failures/errors. Evidence: `CreditPaymentPricingSupport` rounds only partial valid CASH impacts to the nearest $50; full settlement retains the exact remaining financed amount. Commit: `30a4d47` (`fix(payments): round partial cash impacts`).
- [ ] RCI-2 Ã¢â‚¬â€ Delegated: correct only production account C-000191 in one guarded, read-back transaction, reconciling payment, allocation, installment, and account aggregate records. Acceptance: payment impact is $15,100.00 and the first installment balance is $17,600.00; no unrelated rows change. Checks: before/after SELECT and rollback-safe validation. Evidence: blocked Ã¢â‚¬â€ an existing `psql`/SSH tunnel process is detectable, but no accessible terminal/session or non-secret connection entry point is available. No credentials were inspected and no production query or update was executed.

## Next step
Provide an authenticated terminal/session for the already-authorized production tunnel, then perform RCI-2 with the guarded transaction and read-back verification.
