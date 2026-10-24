# 2026-10-23 V2 rolling-month period repair

Production is running with the `group-course-v2` profile and the configured `class_deduction_system_v2` database. The period policy used for this repair is:

- Start at the first actual lesson date saved on the confirmed order item and entitlement.
- Count the start month as month one; the natural end is the final calendar day of month N.
- If the class program ends earlier, cap the period at that program end date.
- Do not regenerate, delete, or otherwise change schedules or lesson records.

## Changes applied

| Rows | Finding | Correction |
| ---: | --- | --- |
| 3 | Active 2-month entitlements stored `start + 2 months - 1 day`, yielding Nov 9, Dec 8, and Jan 1 instead of calendar month-end | Ethan Tang: Oct 31; Harry Lin: Nov 30; Scarlett Li: Dec 31 |
| 62 | Ten-month package month-end was June 30, after the configured class program end of June 28 | Set entitlement expiry to June 28 |
| 1 | Kevin Chen's entitlement end exceeded the June 28 program end; order/plan quantity is 10 while the entitlement snapshot says 9 | Set expiry to June 28; left quantity unchanged pending reconciliation |
| 1 | Wenhao Ma's confirmed 2-month order had a 1-month V2 entitlement snapshot | Set purchased quantity to 2 and expiry to Nov 30 |

Only `class_enrollment.expiry_date`, `class_enrollment.purchased_quantity` for the explicitly confirmed Wenhao order, and the optimistic-lock `version` were changed. The 67 repaired entitlements were guarded by their recorded IDs, source order data, status, original values, and/or an exact candidate count inside transactions. The V2 online service uses `class_deduction_system_v2`; the Wenhao repair that was already present in the legacy database had not reached this V2 row, so it was applied here separately.

## Verification

- No matching active/expired rolling-month entitlement remains with the old `start + N months - 1 day` formula.
- No order/plan/entitlement quantity-matched rolling-month row remains beyond its configured class end.
- The five individually reviewed enrollments show the corrected dates and quantities in `post-repair-verification.tsv`.
- The preexisting order/plan vs entitlement quantity discrepancy for Kevin Chen remains recorded in that verification file. It was not overwritten because the correct purchased quantity is not established by the period repair.
- Schedules and lesson records were not modified.
- The encrypted production backup job completed successfully before the remaining V2 corrections (2026-10-22 16:26:44 UTC). An earlier backup also completed before the first three expiry corrections.

## Files

- `*-before.sql` and `*-before.tsv`: read-only evidence snapshots.
- `repair-*.sql`: guarded production transactions that were applied.
- `rollback-*.sql`: guarded rollback scripts; run only when the row versions still match the post-repair state.
- `post-repair-verification.sql` and `.tsv`: remaining-anomaly counts and reviewed rows.
