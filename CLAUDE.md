# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an internal admin system for **JIN BEST TUTORING**, a US-based tutoring brand. The system manages student enrollment, course packages, recurring scheduling, lesson confirmation, and automatic unit deduction.

The MVP is staff-facing only — parent/teacher portals are out of scope for the first release.

## Project Structure

```
doc/             — Business docs (system design, DB schema, API contract, page design)
frontend/        — Vue 3 + Vite + TypeScript + Element Plus admin UI
backend/         — Spring Boot 3.5 + MyBatis-Plus + MySQL + Redis
```

## Common Commands

### Frontend (Node.js 20.19+)

```bash
cd frontend
npm install
npm run dev          # dev server
npm run build        # production build (runs typecheck first)
npm run typecheck    # TypeScript type checking only
```

### Backend (Java 17+)

```bash
cd backend
# If openjdk@17 is installed via Homebrew, JAVA_HOME must be set explicitly:
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn spring-boot:run

# Run a single test class:
mvn test -Dtest=LessonRecordServiceImplTest

# Run all tests:
mvn test
```

## Architecture

### Backend Modules

The backend is organized into domain modules under `modules/`:
- `auth/` — login, token management, teacher account CRUD
- `base/` — students, guardians, teachers, classrooms, course products (Stage 3)
- `enrollment/` — orders, course packages, payment records, receipts (Stage 4)
- `schedule/` — recurring rules, lesson occurrences, schedule adjustments, conflict checks (Stage 5)
- `lesson/` — lesson records, hour ledger, balance snapshots, status rules (Stage 6)
- `systemconfig/` — brand config, notification templates, notification logs (Stage 7)

Each module follows the standard Spring Boot layer: `controller → service (interface + impl) → mapper → entity`.

### Frontend Pages

Frontend uses a schema-driven workspace pattern. Each module has:
- `src/api/<module>.ts` — API calls via Axios, with Bearer token auth
- `src/modules/<module>/page-schemas.ts` — page config (list/filter/form fields, columns, dialog schemas)
- `src/views/<module>/*.vue` — Vue page components consuming the schemas

The `WorkspaceInfoBlocks`, `WorkspaceMetricGrid`, `ListWorkspace`, and `FormWorkspace` components are shared building blocks. `src/components/layout/` provides the app shell (header, sidebar, main layout).

### Key Business Rules

**Timezone**: All user-facing dates/times use `America/New_York`. API timestamps are explicit and timezone-safe.

**Unit system**: Lesson durations are measured in 0.5-hour units. The minimum deduction is 0.5 units.

**Deduction logic**: `LessonStatusRule` controls whether a lesson status deducts units and whether it extends the estimated end date. Statuses include `COMPLETED`, `FORMAL_LEAVE`, `CANCELLED`, `NO_SHOW`, `TEACHER_CANCEL`, `MAKEUP_COMPLETED`, `SUBSTITUTED_COMPLETED`.

**Balance architecture**: `hour_ledger` is the source of truth for unit changes. `package_balance_snapshot` is a derived convenience. A lesson confirmation writes to `lesson_record`, `hour_ledger`, and `package_balance_snapshot` in one transaction.

**Planned vs actual**: `lesson_occurrence` (planned) is separate from `lesson_record` (actual). This separation handles leave, reschedules, makeups, and substitutions without mutating history.

**Commercial snapshots**: `course_package` stores pricing/descriptive snapshots from the order moment, not live `course_product` data.

**Schedule conflict checking**: Teacher, student, and classroom conflicts are checked before any `lesson_occurrence` is committed.

## API Conventions

- Context path: `/api`
- Version prefix: `/v1`
- Response wrapper: `{ "success": bool, "code": string, "message": string, "data": any, "timestamp": string }`
- Paging: `{ "records": [], "total": int, "pageNum": int, "pageSize": int }`

See `doc/API_CONTRACT.md` for the full list of endpoints.

## Development Stages

The MVP delivery follows 7 ordered stages documented in `doc/DEVELOPMENT_STAGES.md`. All 7 stages are marked complete; current work is frontend-backend integration. When implementing a new feature, follow the stage order and update the design docs first if requirements change.

<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan
<!-- SPECKIT END -->
