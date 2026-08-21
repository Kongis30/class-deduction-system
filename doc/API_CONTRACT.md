# API Contract Draft

## Goal

This document aligns the first-stage frontend and backend API structure so both sides can move in parallel.

Shared conventions:

- Backend context path: `/api`
- Version prefix: `/v1`
- Default user-facing timezone: `America/New_York`
- Standard response wrapper:

```json
{
  "success": true,
  "code": "OK",
  "message": "Success",
  "data": {},
  "timestamp": "2026-04-05T09:00:00-04:00"
}
```

- Standard paging payload:

```json
{
  "records": [],
  "total": 0,
  "pageNum": 1,
  "pageSize": 10
}
```

## Aligned API Groups

### Authentication

- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `GET /api/v1/auth/teacher-accounts`
- `POST /api/v1/auth/teacher-accounts`

### Dashboard

- `GET /api/v1/dashboard/summary`

### Base Data

- `GET /api/v1/base/students`
- `GET /api/v1/base/students/{id}`
- `POST /api/v1/base/students`
- `PUT /api/v1/base/students/{id}`
- `DELETE /api/v1/base/students/{id}`
- `GET /api/v1/base/teachers`
- `GET /api/v1/base/teachers/{id}`
- `POST /api/v1/base/teachers`
- `PUT /api/v1/base/teachers/{id}`
- `DELETE /api/v1/base/teachers/{id}`
- `GET /api/v1/base/classrooms`
- `GET /api/v1/base/classrooms/{id}`
- `POST /api/v1/base/classrooms`
- `PUT /api/v1/base/classrooms/{id}`
- `DELETE /api/v1/base/classrooms/{id}`
- `GET /api/v1/base/course-products`
- `GET /api/v1/base/course-products/{id}`
- `POST /api/v1/base/course-products`
- `PUT /api/v1/base/course-products/{id}`
- `DELETE /api/v1/base/course-products/{id}`

### Enrollment and Course Packages

- `GET /api/v1/enrollment/orders`
- `GET /api/v1/enrollment/orders/{id}`
- `POST /api/v1/enrollment/orders`
- `GET /api/v1/enrollment/packages`
- `GET /api/v1/enrollment/packages/{id}`
- `GET /api/v1/enrollment/receipts`
- `GET /api/v1/enrollment/receipts/{id}`

### Scheduling

- `GET /api/v1/schedule/rules`
- `GET /api/v1/schedule/rules/{id}`
- `POST /api/v1/schedule/rules`
- `GET /api/v1/schedule/occurrences`
- `POST /api/v1/schedule/adjustments`
- `GET /api/v1/schedule/classrooms/occupancy`
- `GET /api/v1/teacher/schedule/occurrences`

### Lessons and Deduction

- `GET /api/v1/lessons/records`
- `GET /api/v1/lessons/records/{id}`
- `POST /api/v1/lessons/records`
- `POST /api/v1/teacher/lessons/records`
- `GET /api/v1/lessons/ledger`
- `GET /api/v1/lessons/balance-snapshots/{packageId}`

### System Settings

- `GET /api/v1/system/lesson-status-rules`
- `GET /api/v1/system/lesson-status-rules/{id}`
- `PUT /api/v1/system/lesson-status-rules/{id}`
- `GET /api/v1/system/brand-config/current`
- `PUT /api/v1/system/brand-config/current`
- `GET /api/v1/system/notification-templates`
- `GET /api/v1/system/notification-templates/{id}`
- `PUT /api/v1/system/notification-templates/{id}`
- `GET /api/v1/system/notification-logs`
- `GET /api/v1/system/notification-logs/{id}`
- `POST /api/v1/system/notification-logs/{id}/requeue`
- `POST /api/v1/system/notification-logs/dispatch`

## Current Implementation Locations

Frontend:

- API adapters: `frontend/src/api/`
- domain types: `frontend/src/types/`

Backend:

- controller skeletons: `backend/src/main/java/com/classdeduction/system/modules/`
- common response models: `backend/src/main/java/com/classdeduction/system/common/model/`

## Enrollment Request and Response Notes

### `POST /api/v1/enrollment/orders`

Creates the enrollment order and the linked downstream records in one transaction:

- one `enrollment_order`
- one `course_package`
- zero or one `payment_record`
- zero or one `receipt_record`
- zero or one `package_teacher_assignment`

Key request fields:

- `studentId`
- `courseProductId`
- `handledByName`
- `originalAmount`
- `discountAmount`
- `receivedAmount`
- `totalUnits`
- `issueReceipt`

For 1-on-1 registration, `totalUnits` is calculated from the selected price
option's purchased hours. Teacher, lesson name, lesson duration, and allocated
hours are configured later on each recurring schedule slot; the enrollment
form does not allocate teachers or lesson hours.

Key response fields:

- `enrollmentOrderId`
- `enrollmentNo`
- `packageIds`
- `packageNos`
- `paymentIds`
- `receiptIds`

### `GET /api/v1/enrollment/orders`

Supports filtering by:

- `keyword`
- `studentId`
- `orderStatus`
- `paymentStatus`

### `GET /api/v1/enrollment/packages`

Supports filtering by:

- `keyword`
- `studentId`
- `packageStatus`
- `teacherId`

### `GET /api/v1/enrollment/receipts`

Supports filtering by:

- `keyword`
- `studentId`
- `templateCode`

## Scheduling Request and Response Notes

### `POST /api/v1/schedule/rules`

Creates:

- one `recurring_schedule_rule`
- one or more `recurring_schedule_slot`
- a generated batch of `lesson_occurrence`

Key request fields:

- `packageId`
- `primaryTeacherId`
- `defaultClassroomId`
- `startDate`
- `endDate`
- `generationHorizonDays`
- `slots[]`

The backend currently applies several production safeguards:

- slot duration must equal `plannedUnits * 30 minutes`
- same-weekday slots cannot overlap
- generation window is capped at 180 days
- teacher, student, and classroom conflicts are checked before insert

### `GET /api/v1/schedule/occurrences`

Supports filtering by:

- `keyword`
- `packageId`
- `studentId`
- `teacherId`
- `classroomId`
- `planningStatus`
- `startAt`
- `endAt`

If no window is provided, the backend defaults to a bounded future window to avoid accidental wide scans.

### `GET /api/v1/teacher/schedule/occurrences`

Teacher-only endpoint for viewing the logged-in teacher's own schedule.

Key constraints:

- `teacherId` is always taken from the login session, not from the request
- supports the same bounded window semantics as the staff occurrence API
- intended for the future teacher portal and mobile views

### `GET /api/v1/schedule/classrooms/occupancy`

Supports filtering by:

- `classroomId`
- `teacherId`
- `startAt`
- `endAt`

### `POST /api/v1/schedule/adjustments`

Supports:

- `RESCHEDULE`
- `CHANGE_ROOM`
- `CHANGE_TEACHER`
- `MAKEUP_CREATE`

Each adjustment writes a `lesson_adjustment_log` snapshot so later deduction and audit flows can trace what changed.

## Lesson and Deduction Notes

### `POST /api/v1/lessons/records`

Confirms what actually happened for one planned lesson and writes downstream state in one transaction:

- one `lesson_record`
- one `hour_ledger`
- one `package_balance_snapshot` insert or update
- one `course_package` balance update

Key request fields:

- `occurrenceId`
- `resultStatus`
- `actualTeacherId`
- `actualClassroomId`
- `actualStartAt`
- `actualEndAt`
- `actualUnits`
- `overrideShouldDeduct`
- `overrideDeductedUnits`
- `overrideShouldExtendEndDate`

Production safeguards currently applied:

- one occurrence can only be confirmed once
- deduction can never make package balance negative
- actual duration must match the `0.5h` unit system
- manual overrides are only allowed when the status rule allows them
- estimated end date is recalculated from future usable planned lessons instead of fixed day offsets
- a pending guardian email notification log is queued after successful confirmation when active guardian emails and an active template are available

### `POST /api/v1/teacher/lessons/records`

Teacher-only endpoint for self-confirming a lesson result.

Allowed result statuses:

- `COMPLETED`
- `NO_SHOW`
- `FORMAL_LEAVE`

Teacher-side safeguards:

- the account must be linked to one active teacher profile
- the occurrence must belong to that teacher
- the lesson must fall within the teacher self-confirmation time window
- teachers cannot pass manual deduction overrides or arbitrary actual teacher IDs

### `GET /api/v1/lessons/records`

Supports filtering by:

- `keyword`
- `packageId`
- `studentId`
- `teacherId`
- `resultStatus`
- `recordSource`
- `confirmedFrom`
- `confirmedTo`

### `GET /api/v1/lessons/ledger`

Supports filtering by:

- `packageId`
- `ledgerType`
- `businessDateFrom`
- `businessDateTo`

### `GET /api/v1/system/lesson-status-rules`

Returns the active/default rule set used by lesson confirmation.

### `GET /api/v1/system/lesson-status-rules/{id}`

Returns one rule for admin review before editing.

### `PUT /api/v1/system/lesson-status-rules/{id}`

Updates one existing lesson status rule.

Safeguards applied:

- `statusCode` is immutable
- core rules used by the lesson pipeline must remain `ACTIVE`
- rules with `defaultShouldDeduct=false` must use `deductMode=NONE`
- `FIXED_UNITS` requires a positive `fixedUnits`
- non-fixed modes must leave `fixedUnits` empty

## Notification Notes

- Remaining-unit notification logs are currently written to `notification_log` as `PENDING` outbox records.
- The backend still does not send mail inside the lesson confirmation transaction.
- Email dispatch is admin-triggered, not automatic.
- Email dispatch is disabled by default and protected by a configurable allowlist.
- Logs are claimed as `PROCESSING` before send to reduce duplicate delivery during repeated manual dispatch calls.
- Failed logs can be requeued manually after configuration is fixed.
- This design keeps deduction stable even if email configuration is missing or temporarily unhealthy.

## Next Contract Steps

- Publish generated OpenAPI output after the live enrollment APIs settle.
- Expand system rule management APIs.
