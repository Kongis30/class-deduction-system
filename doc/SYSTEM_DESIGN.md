# System Design

## Project Background

`JIN BEST TUTORING` currently relies on spreadsheets, manual coordination, and informal follow-up to manage student enrollment, teacher assignment, recurring schedules, classroom usage, lesson confirmation, and remaining lesson units. That workflow becomes harder to maintain as the number of students, teachers, and schedule exceptions grows.

## Problem Statement

The current manual process creates several recurring issues:

- scheduling information is fragmented across people and files
- conflicts between teachers, students, and classrooms are easy to miss
- leave, makeup lessons, and substitutions require manual balance updates
- remaining lesson units and projected end dates are error-prone when maintained by hand
- receipts, schedules, and follow-up notices take extra manual effort
- rule interpretation depends too much on individual staff experience

## Product Scope

This system is an internal admin product for a single tutoring brand in the United States. The first release is focused on the staff workflow rather than parent or teacher portals.

Confirmed MVP scope:

- student, guardian, teacher, classroom, and course product management
- enrollment orders and course package creation
- cash payment records and receipt export
- primary teacher assignment per package
- recurring scheduling and one-off lesson adjustments
- teacher, student, and classroom conflict checks
- lesson confirmation with exception handling
- unit ledger generation and remaining balance tracking
- estimated package end-date calculation
- email notifications after lesson confirmation
- configurable lesson status rules and brand settings

Out of scope for the first release:

- parent portal
- full teacher portal delivery
- online payment
- refunds and complex finance workflows
- payroll calculation
- multi-brand or multi-tenant platform behavior
- native mobile apps

## Users and Roles

### Administrator

Responsible for system settings, branding, rule maintenance, accounts, and permissions.

### Staff

Responsible for enrollment, scheduling, lesson confirmation, exports, and day-to-day operations.

### Teacher

Reserved for a future role with restricted self-service access such as viewing a personal timetable and confirming personal lessons.

## Core Domain Concepts

- `Student`: the learner record
- `GuardianContact`: parent or guardian contact information
- `Teacher`: instructional staff record
- `Classroom`: a physical teaching room
- `CourseProduct`: reusable course and pricing template
- `EnrollmentOrder`: the commercial order record
- `CoursePackage`: the student-specific balance account created from an order
- `PackageTeacherAssignment`: primary teacher history for a package
- `RecurringScheduleRule`: the recurring scheduling rule for a package
- `RecurringScheduleSlot`: the weekly time slot under a schedule rule
- `LessonOccurrence`: the planned lesson entry
- `LessonRecord`: the actual lesson outcome record
- `HourLedger`: the unit change ledger
- `NotificationTemplate` and `NotificationLog`: communication settings and delivery history
- `LessonStatusRule`: deduction and extension behavior per lesson status
- `BrandConfig`: brand-level settings for `JIN BEST TUTORING`

## Key Business Rules

### Course Package Rules

- One student can hold multiple course packages at the same time.
- Each package has its own total units, default lesson units, pricing snapshot, and status.
- Unit calculations use `0.5` hours as the minimum unit.
- Actual lesson duration drives deduction behavior. Planned schedule data alone is not enough.

### Scheduling Rules

- Recurring schedules can include multiple weekly sessions.
- Different weekdays can use different start and end times.
- Every planned lesson must be checked against teacher, student, and classroom conflicts.
- Planned lessons and actual lesson records stay separate to handle exceptions safely.

### Lesson Status Rules

The system must distinguish statuses such as:

- completed
- formal leave
- temporary cancellation
- no-show
- teacher cancellation
- makeup completed
- substituted lesson completed

Each status controls whether units are deducted and whether the estimated end date should be extended.

### Timezone Rules

- User-facing dates and times should use the center's US local timezone.
- Until a more specific campus timezone is confirmed, use `America/New_York`.
- API timestamps should remain explicit and timezone-safe.

## High-Level Architecture

The recommended architecture is a frontend-backend split with a modular monolith backend.

### Frontend

- Vue 3
- Vite
- TypeScript
- Element Plus

### Backend

- Java 17+
- Spring Boot 3.5.x
- Spring Security
- MyBatis-Plus

### Data and Integration

- MySQL as the primary relational store
- Redis for caching, session support, and short-lived scheduling coordination
- email integration for notifications
- export support for receipts and schedules

## Module Breakdown

- Authentication and authorization
- Base data
- Enrollment and course packages
- Scheduling and classroom occupancy
- Lesson execution and deduction ledger
- Notifications and exports
- System configuration

## Design Priorities

- keep the first release operationally focused and lightweight
- make rules explicit instead of hiding them in manual habits
- preserve an audit trail for critical actions
- keep the codebase reusable for future tutoring center projects
- make brand and wording changes easy without reworking the architecture

## Success Criteria

The MVP is successful when internal staff can move from enrollment to scheduling to lesson confirmation with less manual reconciliation, fewer scheduling conflicts, and a clearer view of remaining lesson units.
