# Group class DBv2 migration audit

This report was prepared from the isolated local `class_deduction_system_v2` database after the pre-migration snapshot. No online database was accessed.

## Source records in scope

| Product | Actual classes | Legacy price rows | Order items | Class enrollments |
| --- | ---: | ---: | ---: | ---: |
| 2026-2027 周六班 | 36 | 6 | 75 | 81 |
| 2026-2027 课后班 | 9 | 9 | 120 | 124 |
| 2026 SHSAT 冲刺班 | 2 | 1 | 16 | 26 |
| 2026 SHSAT 冲刺加长班 | 1 | 1 | 3 | 3 |
| 2027 州考冲刺班 | 6 | 1 | 2 | 3 |

Other active GROUP products have no actual classes or order items. Their setup rows are not auto-mapped: there is no schedule or registration history to infer the intended teaching project from. They remain available for explicit setup in V2.

## Mapping rules

- Saturday term rows (`秋季班`, `春季班`, `全年班`) consolidate to the full-year actual class with the same actual-class name and campus. The curriculum project comes from the old period label: state-test/ESL classes map to `G3-6 State Tests / ESL Newcomers`; SHSAT/PSAT/SAT classes map to `SHSAT / PSAT / SAT`.
- For the Saturday product, actual classes G3–G6 map to the state-test/ESL project; SHSAT, PSAT, and SAT actual classes map to the standardized-test project. The old price-option `class_id` is only an obsolete default-class pointer, not plan ownership; it remains as inactive audit history and is not used for V2 registration.
- The Saturday plan is one of Fall, Spring, or Full year. Its date range comes from the matching legacy term class period. The two campus copies share project plans and holiday dates.
- After-school legacy grade bands (`ESL`, `G1-G5`, `G6-G8`) become teaching projects. `G1`–`G8`/`ESL` actual classes retain their existing campus-null meaning and weekly time slots; 2/6/10 month options become rolling-month plans.
- Each SHSAT crash or state-test sprint product becomes one project. Its actual classes and 4/7/16-session plan remain distinct to that product.
- Each order item and class enrollment maps its class independently. This preserves existing class transfers where the order snapshot and entitlement currently point to different classes within the same project.
- Prices, discounts, payments, refunds, receipt rows, order IDs, and lesson records are not recalculated. Historical price-option rows are retained as inactive references and connected to the V2 project and plan.
- Saturday Spring/Fall duplicate class rows are soft-archived only after order items and mappable enrollments point to the canonical full-year class. Generated seasonal occurrences are soft-archived only after preflight proves an identical annual occurrence exists at the same date and time. Cancelled enrollments attached to already-deleted legacy classes remain unchanged as audit history.
- Legacy project/grade holidays are converted to project-level date ranges. Each unique holiday date is applied to every actual class and campus in the matching teaching project.

## Preflight evidence

- No in-scope group order item is missing its price option or actual class.
- No in-scope class enrollment is missing its source order item.
- No lesson record refers to a Saturday Spring/Fall class occurrence. Every active Spring/Fall generated occurrence has an identical annual date, time, teacher, room, delivery mode, duration, status, and notes, so archiving seasonal duplicates does not remove unique schedule data or attendance history.
- The Saturday template has 72 weekly slots across all term/campus rows. Seasonal and annual class defaults and every slot configuration match, and none has a teacher or classroom assigned. The annual schedule is therefore a safe canonical template for the existing copied terms.
- The source has 81 non-deleted Saturday class-enrollment rows: 74 active and 7 cancelled. Two cancelled rows refer to already-deleted, campus-unassigned seasonal classes with no canonical match; they remain untouched. The 74 active and five other mappable cancelled rows can be linked to the V2 project/plan.
- No active Saturday entitlements overlap after consolidating Fall, Spring, and Full-year classes to their shared actual classes.
- The 18 in-scope legacy price rows all have a quantity and a price. The migration rejects any duplicate V2 plan key with inconsistent prices.

## Finance baseline before migration

For the 75 non-deleted Saturday order items, the source snapshots are: price `$197,263.76`, discount `$31,235.00`, amount due `$167,208.76`, paid `$156,722.76`, and balance `$10,486.00`. The migration only changes model links and never recalculates these fields. Re-run the same aggregate after migration and require exact equality.

## Verification after migration

- The 36 Saturday source classes map to 12 canonical classes (6 actual classes × 2 campuses). There are 8 V2 projects, 18 plans, and 30 active actual classes across the migrated group products.
- All 216 non-deleted group order items have a V2 project, plan, and actual class; product/project, project/plan, and project/class relationships all match.
- Of 237 non-deleted group class enrollments, 235 map to V2. The two remaining rows are cancelled entitlements tied to legacy classes that were already deleted and had no safe canonical match; they remain unchanged as history.
- The 75 Saturday order items retain the exact pre-migration financial totals: original price `$197,263.76`, discount `$31,235.00`, amount due `$167,208.76`, paid `$156,722.76`, and balance `$10,486.00`.
- No active legacy price option remains for any GROUP product. A separate local cleanup migration retired 34 stale active options on already soft-deleted products. None was referenced by a non-deleted order item or class. Their former status and product IDs are recorded in `group_course_model_option_retirement_audit` so the change can be reversed if required.
- Each of the two Saturday teaching projects has seven active project-level holiday dates. The 14 holiday rows with no product link are already associated with a V2 project and their active occurrences; the idempotent history repair found no remaining unscoped group holiday occurrences or group lesson records to change. Its holiday-occurrence rollback audit therefore has zero rows.
- The pre-migration DBv2 snapshot is retained at `/Users/whw/.local/share/jin-v2/class_deduction_system_v2-before-data-migration-20261001.sql.gz` (SHA-256 `57f46e465e1fe6c2b8ceb7d161a53715d1a562abdd0e645ad095d5624da75ed3`).

The verification queries inspect counts, relationship consistency, and financial snapshots only; they do not export student names or contact details.
