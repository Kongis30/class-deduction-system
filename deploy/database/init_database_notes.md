# Database Initialization

This folder contains the current clean database bootstrap files.

## Import Order

Run from the repository root so the `SOURCE` path inside the seed file resolves correctly:

```bash
mysql -uroot -p < doc/init_schema_current.sql
mysql -uroot -p < doc/init_seed_required.sql
mysql -uroot -p class_deduction_system < doc/migrations/20260919_payment_allocation.sql
mysql -uroot -p class_deduction_system < doc/migrations/20260924_sys_user_active_unique_keys.sql
mysql -uroot -p class_deduction_system < doc/migrations/20260928_class_group_slot_classroom.sql
```

If the SQL client does not support MySQL `SOURCE`, run the course matrix seed separately after `init_seed_required.sql`:

```bash
mysql -uroot -p class_deduction_system < doc/migrations/20260727_seed_ppt_course_matrix.sql
```

## Files

- `doc/init_schema_current.sql`: current schema-only dump, including `CREATE DATABASE`, `USE`, tables, indexes, foreign keys, checks, triggers/routines/events if present.
- `doc/init_seed_required.sql`: required baseline data only. It intentionally avoids demo students, demo teachers, orders, payments, and E2E records.
- `doc/migrations/20260727_seed_ppt_course_matrix.sql`: customer course matrix seed loaded by `init_seed_required.sql`.
- `doc/migrations/20260919_payment_allocation.sql`: adds course-level discount/payment snapshots and their allocation ledger; run after initializing the base schema.
- `doc/migrations/20260924_sys_user_active_unique_keys.sql`: limits account uniqueness to active users and allows repeated soft deletion while preserving account history.
- `doc/migrations/20260928_class_group_slot_classroom.sql`: adds independent classroom storage for each group-class weekly schedule slot.
- `doc/migrations/20260924_recurring_schedule_two_year_horizon.sql`: historical migration retained for existing installs; current scheduler ignores this legacy column.

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
- Course products from the customer PPT:
  - `After School`
  - `Saturday Class`
  - `Summer Class`
  - `Sprint Class`
  - `1-on-1`

Price rows are not seeded. Staff should add grade, billing, schedule, and price rows manually in Course Pricing Matrix. The UI provides PPT grade suggestions while keeping the actual price rows user-created.
