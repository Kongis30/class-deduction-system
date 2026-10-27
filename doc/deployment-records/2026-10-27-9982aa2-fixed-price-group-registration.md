# JIN V2 deployment: fixed-price group registration plans

- Source commit: `9982aa2` on `main_v2` (`Add fixed-price group registration plans`).
- Release directory: `/home/class-deduction-system/releases/v2-20261027-9982aa2-fixed-price`.
- Previous release: `/home/class-deduction-system/releases/v2-20261027-48d09f1-natural-month-receipt`.
- Deployment scope: frontend and backend artifacts built from the source commit. No database migration was required.

## Change

- Adds `FIXED_PRICE` as a group registration plan type. The plan charges its configured price once and covers every scheduled class date in its optional date range (or the whole actual class when no dates are set).
- Stores the covered scheduled-session count and dates in the enrollment entitlement so registration, receipt correction, and Data Summary period display use the same range.
- Rejects invalid date ranges, missing whole-class dates, and plan periods with no scheduled class dates with user-facing validation errors.

## Verification

- Targeted backend tests passed: all `GroupCourseModelServiceImplTest` cases and the five fixed-price registration cases in `RegistrationIntakeServiceImplTest`.
- Frontend `npm run build` passed, including `vue-tsc --noEmit`.
- Broader service tests were compared with baseline commit `48d09f1`; the existing failures in `RegistrationIntakeServiceImplTest` and `EnrollmentOrderServiceImplTest` were unchanged from baseline.
- Production backend JAR SHA-256: `da38c840f6e32444e5a38a173d7845c299d0e7781da6edab35f3aa81bd6158c7`.
- Production frontend `index.html` SHA-256: `2bab452f1aba80e78d1dc75c5b08388f4e6047a34bdd2eda4a3a45c47e780b4d`; the public index matched this checksum.
- `class-deduction.service` is active and `http://127.0.0.1:8084/api/actuator/health` returned `{"status":"UP"}`.

## Rollback

- Restore `current` and `/home/class-deduction-system/front/dist` to the previous release path above.
- Restore `/etc/systemd/system/class-deduction.service.d/v2.conf` from this release's `backend/v2.conf.before`, run `systemctl daemon-reload`, and restart `class-deduction.service`.
