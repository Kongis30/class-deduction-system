# Backend Development Progress

## Goal

Build the backend for `JIN BEST TUTORING` step by step with `Spring Boot + MyBatis-Plus + MySQL + Redis`, focusing first on the internal staff workflow:

`Base Data -> Enrollment -> Scheduling -> Lesson Confirmation -> Deduction Ledger -> Notification Settings`

## Delivery Path

### Stage 1: Project Foundation and Database Alignment

- [x] Spring Boot backend project skeleton
- [x] Standard API wrapper, global exception handling, and baseline security config
- [x] First draft of the API contract for parallel frontend and backend work
- [x] MySQL schema and initialization script
- [x] Remote MySQL and Redis connectivity check
- [x] Initialization of the `class_deduction_system` database

### Stage 2: Authentication and Authorization

- [x] Login API
- [x] Current user API
- [x] Role and permission loading
- [x] Access boundaries for staff, admins, and future teachers
- [x] Teacher account to teacher profile one-to-one binding

### Stage 3: Base Data Module

- [x] Student profile CRUD
- [x] Guardian contact CRUD
- [x] Teacher profile CRUD
- [x] Classroom CRUD
- [x] Course product CRUD

### Stage 4: Enrollment and Course Packages

- [x] Enrollment order workflow
- [x] Cash payment records
- [x] Course package create and query flows
- [x] Receipt records
- [x] Primary teacher assignment history

### Stage 5: Scheduling Center

- [x] Recurring scheduling rules
- [x] Recurring time slots
- [x] Planned lesson generation
- [x] Teacher conflict checks
- [x] Student conflict checks
- [x] Classroom occupancy conflict checks
- [x] Reschedule, makeup, and substitution logs

### Stage 6: Lesson Execution and Deduction

- [x] Lesson records
- [x] Teacher-side own schedule query
- [x] Teacher-side self confirmation for completed / no-show / formal leave
- [x] Lesson status rule application
- [x] Unit ledger generation
- [x] Course package balance snapshots

### Stage 7: Notifications and System Settings

- [x] Email template management
- [x] Remaining-unit notification outbox logs
- [x] Brand configuration read model
- [x] Manual email dispatch safeguards
- [x] System rule configuration

## Commit Guidance

- Create one Git commit after a module becomes independently testable.
- Keep commit scope aligned with runnable modules, callable APIs, and matching schema changes.
- Recommended commit style:
  - `feat(auth): add login and permission baseline`
  - `feat(base): add students teachers classrooms and course products`
  - `feat(enrollment): add enrollment orders and course packages`
  - `feat(schedule): add scheduling center and conflict checks`
  - `feat(lesson): add lesson confirmation and deduction ledger`

## Current Status

- The backend skeleton, API contract, database schema, database initialization, authentication module, base data module, enrollment module, and scheduling module are in place.
- Student, guardian contact, teacher, classroom, and course product CRUD APIs are available.
- Login, current-user lookup, Redis session persistence, bootstrap accounts, role-based access boundaries, and teacher account binding are available.
- Enrollment order creation now creates the linked course package, cash payment record, receipt record, and primary teacher assignment in one transaction.
- Scheduling now covers recurring rules, slots, planned occurrence generation, teacher/student/classroom conflict detection, classroom occupancy queries, and adjustment logging.
- Lesson execution now covers actual lesson confirmation, teacher self-confirmation boundaries, status-rule-based deduction, no-deduct audit ledger entries, package balance snapshots, and estimated end date recalculation.
- Lesson confirmation now writes pending remaining-unit email logs for active guardian emails without letting the notification channel block the core deduction transaction.
- System settings now include brand config APIs, notification template APIs, notification log queries, failed-log requeue, and admin-only manual email dispatch with allowlist-based safety guards.
- Lesson status rules can now be queried, inspected, and updated through guarded admin APIs, with core status protection and deduct-mode validation to reduce misconfiguration risk.
- The next recommended focus is teacher-facing history UX plus account maintenance, such as password reset, disable, and safe teacher-account lifecycle management.
