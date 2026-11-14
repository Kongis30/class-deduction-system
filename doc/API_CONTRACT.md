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
- `GET /api/v1/base/course-products/all`
- `GET /api/v1/base/course-products/{id}`
- `POST /api/v1/base/course-products`
- `PUT /api/v1/base/course-products/{id}`
- `PUT /api/v1/base/course-products/tab-order`
- `DELETE /api/v1/base/course-products/{id}`

### Enrollment and Course Packages

- `GET /api/v1/enrollment/orders`
- `GET /api/v1/enrollment/orders/{id}`
- `DELETE /api/v1/enrollment/orders/{id}/credit-top-up-receipt?receiptId={receiptId}` (ADMIN/STAFF only)
- `POST /api/v1/enrollment/orders`
- `GET /api/v1/enrollment/packages`
- `GET /api/v1/enrollment/packages/{id}`
- `GET /api/v1/enrollment/receipts`
- `GET /api/v1/enrollment/receipts/{id}`
- `DELETE /api/v1/enrollment/receipts/{id}` (refund undo; ADMIN/STAFF only)

#### Data Summary

- `GET /api/v1/data-summary` (MANAGER receives a response without monetary fields)
- `GET /api/v1/data-summary/refund-candidates?orderId=&orderItemId=&studentId=&participantIndex=` (ADMIN/STAFF only)

#### Monthly revenue report

- `GET /api/v1/reports/monthly-revenue?fromMonth=yyyy-MM&toMonth=yyyy-MM` (ADMIN/STAFF only; MANAGER is rejected). Months default to the last six months ending with the current month, and the range may not exceed 36 months.
- Revenue is the net of `payment_record` rows: `REFUND` rows already carry negative amounts and are counted in the month they were paid, so a refund reduces the month it happened instead of the original payment month. `VOIDED` and soft-deleted rows are excluded.
- Soft-deleted enrollment orders are excluded entirely, so this report matches the Data Summary income total. Student Credit is treated like any other payment method: using Credit counts as collected revenue and a refund converted to Credit reduces it, with no special-casing.
- Attribution: month from `paid_at` (falling back to `created_at`) in `America/New_York`, campus from the receipt's `enrollment_order`, and product from `payment_allocation` → `enrollment_order_item.course_product_id`. Reporting granularity is the course product.
- Response shape: `{ months: [{key, label}], sections: [{key, title, oneOnOne, rows: [{productId, productName, amounts[]}]}], unassignedPaymentCount }`. `amounts` aligns with `months`, one section per campus in `campus.display_order`, plus a trailing `1on1` section.
- Legacy payments with no `payment_allocation` are split across the order's course lines by `price_amount` weight. Payments whose order no longer has any course line are reported as `Unassigned product` so the column totals still equal the payment ledger; `unassignedPaymentCount` reports how many.

#### Course-level discounts and payments

- `POST /api/v1/registration/intake` accepts `items[].discountAmount` and `discounts[].orderItemLineNo` so each discount can be tied to a course line. Each initial `payments[]` entry can include `allocations[]` with `orderItemLineNo` and `amount`; allocations must total that payment amount.
- `GET /api/v1/daily-registrations?fromDate=YYYY-MM-DD&toDate=YYYY-MM-DD` returns student/course entries grouped by the order's saved timestamp in `America/New_York`, with collection totals and counts grouped by the saved operator.
- `GET|POST /api/v1/auth/manager-accounts` and `PUT /api/v1/auth/manager-accounts/{id}` are administrator-only manager-account operations. The account list includes all-time registration order totals and distinct student totals per manager.
- `MANAGER` may save registrations and positive incoming payments. Refund, saved-receipt correction, student-credit balance adjustment, teacher-payment creation, and teacher-payment confirmation remain restricted to `ADMIN`/`STAFF`.
- The `MANAGER` Data Summary response omits all monetary numeric fields; page rendering and workbook exports follow the same restriction.
- `PUT /api/v1/enrollment/orders/{id}/correction` uses the same course line and initial-payment allocation fields when correcting a registration.
- Deleting a standalone Credit top-up receipt validates the order, participant, payment, receipt snapshot, and original Credit ledger; the student's available Credit must cover the full top-up. The operation appends a `TOP_UP_REVERSAL` entry and soft-deletes the order, receipt, payments, and participant snapshot atomically. It retains the original ledger entry; refunded, adjusted, linked, mismatched, or underfunded top-ups require manual review.
- `POST /api/v1/enrollment/payments` accepts per-split `allocations[]` containing `enrollmentOrderItemId` and `amount`. A participant-specific payment cannot also use course allocations.
- `GET /api/v1/enrollment/orders/{id}` returns each item’s `discountAmount`, `amountDue`, `paidAmount`, and `balanceAmount`, plus each payment’s course allocations.
- Legacy clients may omit allocations; the backend keeps the existing automatic allocation behavior for those requests.

#### Undoing a refund (line-level)

Undoing a refund reverses **one refund transaction of one student on one course purchase**. It must not touch other students, other course lines, or other refunds on the same order. A refund that covers several course lines (or a historical whole-order refund) is never undone partially.

- `GET /api/v1/data-summary/refund-candidates` (ADMIN/STAFF only) takes `orderId`, `orderItemId`, `studentId`, and `participantIndex`. It returns `{ candidates: [...], unavailableReason: string|null }`. Each candidate carries `receiptId`, `receiptNo`, `paymentId`, `paymentNo`, `refundedAt`, `amount` (positive), `paymentMethod`, `eligible`, and `ineligibleReason`. A candidate is eligible only when the refund links to exactly one of its receipts, one matching negative `payment_allocation` for this purchase line, and a complete recovery snapshot, and no later facts invalidate the undo. The preview never returns the snapshot, and it applies the same checks as the delete endpoint.
- `DELETE /api/v1/enrollment/receipts/{id}` (ADMIN/STAFF only) undoes the refund behind a `REFUND_RECEIPT` receipt, where `{id}` is the refund receipt id. In one transaction it restores the order, course item, participant, package, class entitlement, future lesson occurrences, and schedule rules/slots captured by the snapshot, reverses the refund's student Credit, then marks the refund payment `VOIDED`, soft-deletes the refund payment and its receipt, and writes an audit record. Lesson records, hour ledger entries, and package balance snapshots are never rewritten; this endpoint cannot recover money already sent back outside the system.
- The endpoint fails closed. When the refund cannot be proven safe to undo, nothing is changed and the response carries one of the error codes below; the UI must show the message and ask staff to review instead of retrying blindly.

| Code | Meaning |
| --- | --- |
| `REFUND_TRANSACTION_REQUIRED` | The refund payment is missing, not a refund, already voided, or not negative. |
| `REFUND_UNDO_LINE_REQUIRED` | The receipt is not tied to one student and one course purchase (for example a whole-order refund). |
| `REFUND_UNDO_LINE_AMBIGUOUS` | The receipt, refund payment, negative allocation, participant, or student do not map to exactly one course line. |
| `REFUND_UNDO_SNAPSHOT_REQUIRED` | No recovery snapshot was saved with the refund, so it needs manual review. |
| `REFUND_UNDO_SNAPSHOT_INVALID` | The saved snapshot is incomplete or unreadable. |
| `REFUND_UNDO_STATE_CHANGED` | Orders, packages, occurrences, or entitlements no longer match the recorded post-refund state. |
| `REFUND_UNDO_ACTIVITY` | Lessons were confirmed, scheduled, or otherwise recorded after the refund. |
| `REFUND_UNDO_SCHEDULE_CONFLICT` | Restoring a cancelled occurrence would clash with the teacher, student, classroom, or another occurrence. |
| `REFUND_UNDO_CREDIT_MISMATCH` | The refund Credit cannot be reversed: the student, the original ledger entry, or the current balance do not support it. |
| `REFUND_UNDO_RESTORE_FAILED` | A record could not be restored; the transaction is rolled back. |
| `REFUND_RECEIPT_CONCURRENT_UPDATE` / `REFUND_TRANSACTION_CONCURRENT_UPDATE` | The refund was changed concurrently; reload and review. |

### Scheduling

- `GET /api/v1/base/course-products/{courseProductId}/holidays?gradeBand=...` lists holidays for one group-class Course + Grade. All actual classes in that Grade share the holidays.
- `POST /api/v1/base/course-products/{courseProductId}/holidays/preview?gradeBand=...` validates a proposed range and reports affected lessons, SESSION enrollments, and blocking conflicts for that Grade.
- `POST /api/v1/base/course-products/{courseProductId}/holidays?gradeBand=...`, `PUT /api/v1/base/course-products/{courseProductId}/holidays/{id}?gradeBand=...`, and `DELETE /api/v1/base/course-products/{courseProductId}/holidays/{id}?gradeBand=...` maintain that Grade's holidays. Changes affect only its future group-class occurrences and recalculate its SESSION enrollment expiry dates transactionally; other Grades, Courses, and one-on-one schedules are unaffected.
- `GET /api/v1/schedule/students/one-on-one` returns active students with at least one active one-on-one course package for the Schedule student picker.
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
option's purchased hours plus any gifted bonus hours (`oneOnOneBonusHours`,
0-400 in 0.5-hour steps). Teacher, lesson name, lesson duration, and allocated
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
- `slots[]`

The backend currently applies several production safeguards:

- slot duration must equal `plannedUnits * 30 minutes`
- same-weekday slots cannot overlap
- occurrences are generated until the configured slot allocations or current package balance are exhausted, unless the rule has an explicit end date
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
