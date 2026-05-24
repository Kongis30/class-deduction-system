# Database Design Overview

## Purpose

This document describes the current database direction for the `JIN BEST TUTORING` class deduction and scheduling system. It focuses on domain boundaries, core table groups, and the most important data rules.

## Design Principles

- Use relational tables for core operational and financial data.
- Keep planned lesson data separate from actual lesson execution data.
- Preserve historical snapshots for key business actions such as enrollment, payment, assignment, and deduction.
- Prefer soft deletion or status-based deactivation for records that affect historical operations.
- Keep all user-facing time data compatible with the US local timezone, currently defaulting to `America/New_York`.

## Main Table Groups

### 1. Authentication and Authorization

Main tables:

- `sys_role`
- `sys_permission`
- `sys_user`
- `sys_user_role`
- `sys_role_permission`
- `sys_login_log`
- `sys_operation_log`

Purpose:

- manage users, roles, and permissions
- support menu and action-level access control
- keep login and operation audit trails

### 2. Base Data

Main tables:

- `student`
- `teacher`
- `classroom`
- `course_product`

Purpose:

- maintain the master data needed by enrollment, scheduling, and lesson confirmation

### 3. Enrollment and Course Packages

Main tables:

- `enrollment_order`
- `course_package`
- `package_teacher_assignment`
- `payment_record`
- `receipt_record`

Purpose:

- capture the commercial side of tutoring enrollment
- create student-specific package balance accounts
- preserve pricing and assignment snapshots

### 4. Scheduling

Main tables:

- `recurring_schedule_rule`
- `recurring_schedule_slot`
- `lesson_occurrence`
- `lesson_adjustment_log`

Purpose:

- model recurring lessons and generated lesson occurrences
- support reschedules, makeup lessons, and substitutions
- keep conflict-aware scheduling data

### 5. Lesson Execution and Deduction

Main tables:

- `lesson_record`
- `hour_ledger`
- `package_balance_snapshot`

Purpose:

- record actual lesson outcomes
- calculate unit changes
- maintain remaining balance visibility and an auditable ledger

### 6. Notifications and Configuration

Main tables:

- `notification_template`
- `notification_log`
- `brand_config`
- `lesson_status_rule`
- `sys_dict_type`
- `sys_dict_item`

Purpose:

- store communication templates and delivery history
- hold reusable dictionaries and brand-level settings
- keep lesson deduction rules configurable

## Relationship Summary

- A student can have many guardian contacts.
- A student can have many enrollment orders.
- An enrollment order can create one or more course packages.
- A course package can have many primary teacher assignment records over time.
- A course package can have recurring schedule rules and many lesson occurrences.
- A lesson occurrence can produce exactly one lesson record when confirmed.
- A lesson record can generate one or more ledger entries.
- A course package has one balance snapshot row representing the current derived balance.

## Critical Modeling Decisions

### Planned vs Actual Lessons

`lesson_occurrence` stores what was scheduled.

`lesson_record` stores what actually happened.

This separation is required so the system can safely support leave, reschedules, makeups, teacher substitutions, and deductions without mutating history into an unreadable state.

### Snapshot-Based Commercial Data

`course_package` stores pricing and descriptive snapshots rather than depending entirely on the current `course_product` row. That prevents later edits to product templates from rewriting historical sales context.

### Ledger-Based Deduction Logic

`hour_ledger` is the source of truth for unit changes. Balance snapshots are derived conveniences, not the primary historical record.

### Assignment History

`package_teacher_assignment` keeps the teacher timeline per package. This makes future reporting, accountability, and teacher portal support easier.

## Operational Notes

- All major business tables include audit fields such as `created_at`, `updated_at`, `created_by`, and `updated_by`.
- Most tables use soft-delete support through `is_deleted` and related audit fields.
- Optimistic locking is preserved through a `version` column.
- The SQL schema file contains English comments and English seed data so the repository remains aligned with the project-wide language rules.

## Source of Truth

Use [`doc/mysql_schema.sql`](./mysql_schema.sql) as the implementation-facing schema source. This document is the narrative companion, not the executable definition.
