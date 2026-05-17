# Development Stages

## Purpose

This file gives coding agents and contributors a stage-by-stage delivery sequence for the MVP. `doc/SYSTEM_DESIGN.md` explains the business and architecture. This file explains implementation order, stage goals, deliverables, and completion criteria.

## Execution Rules

- Follow stages in order unless a documented dependency changes.
- Validate the current stage before moving to the next one.
- If product understanding conflicts with the design docs, update the docs first.
- Keep each stage in a runnable, testable, or safely extendable state.

## Global Assumptions

- The system serves internal staff for `JIN BEST TUTORING`.
- The MVP includes multiple course packages per student, recurring scheduling, classroom conflicts, lesson deduction, cash payment records, receipts, and email notifications.
- Teacher self-service is a future extension, not a first-stage delivery goal.
- The stack is Vue 3, Element Plus, Spring Boot, MyBatis-Plus, MySQL, and Redis.
- User-facing time should be shown in the US local timezone, currently defaulting to `America/New_York`.

## Stage Overview

| Stage | Name | Core Goal | Completion Signal |
| --- | --- | --- | --- |
| 1 | Scope and architecture baseline | Lock business rules, domain boundaries, and technical direction | Stable design docs exist |
| 2 | Repository and project skeleton | Build runnable frontend and backend foundations | Local project skeleton starts successfully |
| 3 | Auth and base data | Deliver login foundations and core reference data modules | Staff can maintain base data |
| 4 | Enrollment and course packages | Deliver order entry and package creation loop | A package can be created from enrollment data |
| 5 | Scheduling center | Deliver recurring schedules, conflicts, and occupancy views | Schedules can be generated and reviewed safely |
| 6 | Lesson execution and deduction | Deliver lesson confirmation and automatic unit updates | Deduction flow is reliable end to end |
| 7 | Notifications and release hardening | Deliver notifications, testing, logging, and launch prep | MVP is ready for controlled rollout |

## Stage Details

### Stage 1: Scope and Architecture Baseline

- Goal: freeze the current business and technical baseline
- Key work:
  - finalize system design
  - finalize delivery stages
  - define domain entities and core statuses
  - confirm MVP scope and non-goals
- Deliverables:
  - `doc/SYSTEM_DESIGN.md`
  - `doc/DEVELOPMENT_STAGES.md`
  - `doc/DATABASE_DESIGN.md`
- Done when:
  - the docs can be read without chat history
  - implementation can begin from the written materials alone

### Stage 2: Repository and Project Skeleton

- Goal: create runnable frontend and backend foundations
- Key work:
  - initialize Vue 3 + Vite + TypeScript frontend
  - initialize Spring Boot backend
  - wire MySQL, Redis, and local environment settings
  - add routing, layout, shared response wrappers, and exception handling
- Done when:
  - frontend and backend start locally
  - health check and admin shell are reachable

### Stage 3: Authentication and Base Data

- Goal: deliver the first core operational module set
- Key work:
  - implement login and current user flow
  - load roles and menu permissions
  - implement students, guardians, teachers, classrooms, and course products
- Done when:
  - internal staff can log in
  - base records can be created and maintained

### Stage 4: Enrollment and Course Packages

- Goal: close the loop from order entry to package creation
- Key work:
  - create enrollment orders
  - store cash payment records
  - create multiple packages per student
  - export receipts
  - assign primary teachers to packages
- Done when:
  - staff can complete a full enrollment-to-package workflow

### Stage 5: Scheduling Center

- Goal: move from static records to operational scheduling
- Key work:
  - create recurring schedule rules and slots
  - generate planned lesson occurrences
  - validate teacher, student, and classroom conflicts
  - build teacher timetable and classroom occupancy views
- Done when:
  - schedules can be created reliably
  - conflicts can be surfaced before staff commit changes

### Stage 6: Lesson Execution and Deduction

- Goal: deliver the main business value of the system
- Key work:
  - confirm actual lesson outcomes
  - apply lesson status rules
  - generate ledger records
  - update remaining units and estimated end dates
- Done when:
  - deduction behavior is consistent and auditable

### Stage 7: Notifications and Release Hardening

- Goal: make the MVP release-ready
- Key work:
  - add email notifications
  - strengthen logging and audit trails
  - add tests and release checks
  - prepare deployment and operational guidance
- Done when:
  - the MVP is stable enough for controlled production use
