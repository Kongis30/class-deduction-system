# E2E UI Tests

This folder stores Playwright scripts that exercise the frontend from a user's point of view.

## Organization

- `helpers/`: reusable UI helpers, such as login, Element Plus interactions, and table section locators.
- `account-profile-sync/`: account, student, and teacher profile consistency flows.
- `base-data/`: Base Data page flows, organized by business area.

## Account / Profile Sync Business Logic

The current system creates student and teacher profiles from the Account page:

1. Create a student account from `Settings > Accounts & Permissions`.
2. The account table should show the new login account.
3. The Students page should show the linked student profile with the same student name and guardian information.
4. Deleting the student account should remove the account and synchronously remove the linked student profile from the active Students list.
5. Deleted account/profile dialogs should still show the deleted data for audit visibility.
6. Create a teacher account from `Settings > Accounts & Permissions`.
7. The account table should show the new login account.
8. The Instructors page should show the linked teacher profile with the same teacher name, phone, email, and subject tags.
9. Deleting the teacher account should remove the account and synchronously remove the linked teacher profile from the active Instructors list.
10. Deleted account/profile dialogs should still show the deleted data for audit visibility.

The tests in this folder perform the operations through the UI, then verify both the UI state and backend list APIs so data consistency problems are caught even when the page renders stale data.

## Base Data Coverage

- `base-data/student-teacher-profiles.spec.ts`: edits student and teacher profiles, verifies filters, opens student form field settings, checks deleted profile dialogs after account deletion, and validates API data consistency.
- `base-data/campuses-classrooms.spec.ts`: creates, edits, filters, and deletes campuses/classrooms, then verifies active list APIs no longer return deleted rows.
- `base-data/course-products-subjects.spec.ts`: manages subjects from the Course Products page, creates/edits/deletes 1:1 and group course products, selects weekly group schedule slots, tests filters, and verifies API data consistency.

## Enrollment Coverage

- `enrollment/enrollment-orders-1on1.spec.ts`: covers the 1 on 1 enrollment lifecycle, including weekly slot selection, custom discount, detail view, payment receipt popup, refund, cancel, delete cooldown, and order/package API consistency.
- `enrollment/enrollment-orders-class.spec.ts`: covers group class enrollment for per-session and monthly products, prorated amount previews, expanded class order rows, cancellation, and API amount/status checks.
- `enrollment/enrollment-settings.spec.ts`: covers Discount presets and Payment methods dialogs, including create, edit, inactive status, delete, list refresh, and API consistency.
- `enrollment/course-packages.spec.ts`: covers Course Packages filters, 1 on 1 product expansion, package detail, lesson history, closing packages, group class progress expansion, header sort/filter actions, and API consistency.

## Business Flow Coverage

- `business-flow/full-business-flow-1on1.spec.ts`: covers the full 1 on 1 lifecycle from API-prepared base data, enrollment order and package generation, teacher initial confirmation in the scheduling UI, the PENDING_ADMIN no-settlement state, admin final confirmation, package balance and ledger updates, unpaid lesson generation, and teacher payment completion.
- `business-flow/full-business-flow-class.spec.ts`: covers per-session group class enrollment through the UI, class package/progress expansion, teacher initial class confirmation, admin final class confirmation via the class-specific API, group progress updates, and teacher unpaid lesson eligibility.
- `business-flow/full-business-flow-edge-cases.spec.ts`: covers Formal Leave no-deduct audit behavior, No Show deduction without teacher payroll eligibility, admin reject behavior without deduction or payroll side effects, duplicate confirmation rejection without duplicate settlement, duplicate payment blocking, closed-package confirmation blocking, insufficient-balance protection, and overlapping schedule rejection.
- `business-flow/full-business-flow-reject-reconfirm.spec.ts`: covers the reject recovery path from teacher initial confirmation, admin rejection, no-deduction/no-payroll validation, re-confirmation, admin final confirmation, ledger update, and unpaid lesson eligibility. If the current UI cannot expose the re-confirm entry after rejection, the spec records the known UI gap and continues through the API to verify backend responsibilities.
- `business-flow/full-business-flow-schedule-adjustment.spec.ts`: covers weekly temporary adjustments, unchanged/more/fewer hour impact messages, recurring rule stability, adjustment logs, makeup creation after leave/cancellation-style flows, conflict rejection, and locked/confirmed lesson protection.
- `business-flow/full-business-flow-teacher-payments.spec.ts`: covers Pay Rate setup and updates, zero-rate warning handling, cross-period teacher payment filtering, Payment History pending/paid/detail/confirm flows, historical payment snapshot stability, and paid lesson exclusion from future unpaid lists.

The business-flow suite intentionally spans enrollment, scheduling, lesson deduction, package balance, ledger, and finance/payroll boundaries. Test setup uses API helpers only for prerequisite data; the user-facing critical path is still exercised through Playwright UI operations, with API/DB checks used to verify cross-module consistency.

Current locked risk: after an admin rejects a teacher initial confirmation, backend state correctly restores the occurrence and keeps package balance, ledger, and payroll unchanged, but the scheduling UI still renders the rejected occurrence as `Completed` without a visible re-confirm entry. The edge-case spec keeps the passing business assertions and marks that UI path as a known risk until the product behavior is fixed.
