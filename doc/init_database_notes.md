# Database Initialization

This folder contains the current clean database bootstrap files.

## Import Order

Run from the repository root so the `SOURCE` path inside the seed file resolves correctly:

```bash
mysql -uroot -p < doc/init_schema_current.sql
mysql -uroot -p < doc/init_seed_required.sql
```

If the SQL client does not support MySQL `SOURCE`, run the course matrix seed separately after `init_seed_required.sql`:

```bash
mysql -uroot -p class_deduction_system < doc/migrations/20260727_seed_ppt_course_matrix.sql
```

## Files

- `doc/init_schema_current.sql`: current schema-only dump, including `CREATE DATABASE`, `USE`, tables, indexes, foreign keys, checks, triggers/routines/events if present.
- `doc/init_seed_required.sql`: required baseline data only. It intentionally avoids demo students, demo teachers, orders, payments, and E2E records.
- `doc/migrations/20260727_seed_ppt_course_matrix.sql`: customer course matrix seed loaded by `init_seed_required.sql`.

## Required Seed Data

- Roles: `ADMIN`, `STAFF`, `TEACHER`, `STUDENT`.
- Permissions and role-permission mappings for the admin/staff/teacher surfaces.
- Admin account: `admin`; default local password: `Jin2025`.
- System dictionaries for user status, student status, teacher status, classroom status, package status, payment status, lesson statuses, ledger types, and teaching modes.
- Lesson status deduction rules, including current cancellation behavior: `CANCELLED` and `TEMP_CANCEL` deduct planned units.
- Brand config for `JIN BEST TUTORING`.
- Notification templates and disabled environment-neutral notification config. SMTP credentials must be configured per environment, not seeded.
- Payment methods and discount presets.
- Baseline campuses and classrooms:
  - `South Campus`, `North Campus`.
  - `Room 101`, `Room 102`, `Room 201`.
- Course products and course/grade matrix from the customer PPT:
  - `After School`: `G1-G5`, `G6-G8`, `ESL`; `2/6/10 months`; `Mon-Fri 3pm-6pm`.
  - `Saturday Class`: `G1-2`, `G3-G6`, `SHSAT`, `PSAT`, `SAT`; `Fall 13 weeks`, `Spring 20 weeks`, `Full year 33 weeks`; AM/PM/full-day slots.
  - `Summer Class`: `G1-G6`, `SHSAT`, `PSAT`, `SAT`, `ESL`; `7/6-8/21`, `7 weeks`; AM/PM/full-day slots.
  - `Sprint Class`: product placeholder only; the PPT does not define grade, period, or time matrix.
  - `1-on-1`: `G1-G5`, `G6-G8`, `G9-G12`, `AP`; `40/60/120 hours`.

Prices are seeded as `0.00` template rows. Registration requires staff to enter a manual price and reason until approved prices are configured.
