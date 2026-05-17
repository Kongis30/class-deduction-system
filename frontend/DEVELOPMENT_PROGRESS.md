# Frontend Development Progress

## Document Notes

- Project: `JIN BEST TUTORING` admin frontend
- Maintained under: `frontend/`
- Last updated: `2026-04-05`
- Working principle: update this file whenever a frontend milestone becomes independently explainable

## Current Stage

The frontend is currently in the "page skeleton completed, waiting for visual refinement and live API integration" stage.

Current goals:

- Finish the information architecture, routing, module boundaries, and page shells first
- Keep the UI powered by local mock data until the page style is locked
- Connect real APIs after the visual direction is approved and the backend contract is stable

## Completed Work

### 0. Base Data Page Cleanup

- Reworked the student, teacher, and course pages into leaner management views
- Removed the oversized top summary card pattern from these pages
- Shifted the page structure to compact tabs, dense filters, and table-first layouts
- Updated the student page tabs to:
  - `Active students`
  - `History students`
  - `Paused records`
- Updated the course page tabs to:
  - `Course management`
  - `Subject categories`
- Added basic create and edit entry points for subject categories
- Refined the teacher page into a compact list-driven roster view with filters and status tabs

### 1. Student Enrollment Flow Mock

- Restyled the student filters into fixed-width inline controls with stronger labels for faster scanning
- Added a working `Add student` dialog with form validation and local mock persistence
- Upgraded the student entry dialog with a richer admin-style layout, gradient header, profile area, and grouped form sections
- Added a working `Edit student` dialog that reuses the same student form flow
- Wired the `Enroll` action from the student list into a dedicated enrollment order page
- Rebuilt the enrollment order page into a real mock workflow with:
  - selected student summary
  - package selection dialog
  - pricing summary
  - confirm action placeholder for later API integration

### 2. Project Structure and Routing

- Split views by business domain:
  - `src/views/base-data/`
  - `src/views/enrollment/`
  - `src/views/schedule/`
  - `src/views/lessons/`
  - `src/views/system/`
- Replaced the single shared placeholder route with module-specific pages
- Added route-level lazy loading to reduce first-load pressure

### 3. Shared Workspace Components

- Built the list workspace shell: `src/components/workspace/ListWorkspace.vue`
- Built the form workspace shell: `src/components/workspace/FormWorkspace.vue`
- Built the schedule workspace shell: `src/components/workspace/ScheduleWorkspace.vue`
- Built the settings workspace shell: `src/components/workspace/SettingsWorkspace.vue`
- Added shared hero blocks, metric cards, and side information panels

### 4. Modular Page Configuration

- Added shared workspace schema types: `src/modules/shared/workspace-types.ts`
- Added workspace schema builders: `src/modules/shared/workspace-builders.ts`
- Added page schema files for every major business area:
  - `src/modules/base-data/page-schemas.ts`
  - `src/modules/enrollment/page-schemas.ts`
  - `src/modules/schedule/page-schemas.ts`
  - `src/modules/lesson/page-schemas.ts`
  - `src/modules/system/page-schemas.ts`

### 5. Covered Pages

The frontend page skeleton is already in place for:

- Dashboard
- Student profiles
- Teacher profiles
- Classroom management
- Course products
- Enrollment orders
- Course package management
- Receipt management
- Recurring scheduling
- Reschedules and makeup lessons
- Teacher timetable
- Classroom occupancy
- Lesson records
- Exception records
- Deduction ledger
- Remaining unit lookup
- Lesson status rules
- Notification settings
- Brand settings
- Accounts and permissions

### 6. Engineering Validation

- Passed `npm run typecheck`
- Passed `npm run build`
- Added Vite manual chunk settings to keep the build output structure healthier

## Remaining Work

### 1. Visual Refinement

Keep improving the page design based on product feedback:

- spacing and layout rhythm
- card hierarchy
- table styling
- form layout
- scheduling canvas presentation
- responsive behavior details

### 2. Deeper Interaction Design

- Replace placeholder action buttons with real workflows
- Add secondary interactions such as drawers, dialogs, and side panels
- Improve the linkage between filters, toolbars, content areas, and side summaries

### 3. Real API Integration

Planned API groups:

- `/api/v1/dashboard/summary`
- `/api/v1/base/*`
- `/api/v1/enrollment/*`
- `/api/v1/schedule/*`
- `/api/v1/lessons/*`
- `/api/v1/system/*`

## Suggested Next Steps

1. Lock the visual direction with the dashboard and a few representative operational pages.
2. Connect the highest-frequency screens first: dashboard, student profiles, enrollment orders, and recurring scheduling.
3. Add drawers, dialogs, and submission flows after the first live APIs are connected.
4. Close the data loop across scheduling, lesson records, and deduction ledger pages.

## Update Checklist

After each frontend milestone, update at least these items:

- what was completed
- what is currently blocked
- what will be tackled next
- whether type checking or build validation was completed
