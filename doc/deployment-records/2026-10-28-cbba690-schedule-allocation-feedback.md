# JIN V2 deployment: schedule allocation error feedback

- Source commit: `cbba690` on `main_v2` (`Improve schedule allocation error feedback`).
- Release directory: `/home/class-deduction-system/releases/v2-20261028-cbba690-schedule-allocation-feedback`.
- Previous release: `/home/class-deduction-system/releases/v2-20261028-eae168a-transport-roster`.
- Deployment scope: frontend and backend artifacts. No database migration was required.

## Change

- Schedule allocation failures now include the backend's package balance and reserve calculation so the editor can show the exact available hours and shortfall.
- Create and edit schedule flows show an inline explanation with the package total, remaining balance, pending reviews, other schedules, preserved lessons, transferred future lessons, and a correction hint.
- The error panel scrolls into view in the schedule dialog and adapts to mobile screens.

## Verification

- `RecurringScheduleRuleServiceImplTest`: passed, including structured allocation details for create and edit rejection cases.
- Playwright `schedule-allocation-error-feedback.spec.ts`: passed for insufficient allocation, reserve breakdown, mobile visibility, correction and successful retry.
- Frontend `npm run build` and backend `mvn -DskipTests package`: passed. Frontend build emitted the existing large-chunk advisory.
- `git diff --check`: passed.
- Backend JAR SHA-256: `07c469ea5de2c07d4e696b73a64e6419a1ac017f228f1e2a161c0b56afbadd3f`.
- Frontend `index.html` SHA-256: `ad53759b03ebd502baf85c2d38f23e9e531b2476c2303b7505a0332f0894fbf8`; public index matched.
- Schedule editor JS SHA-256: `ebb4a4df6ffa9c726b426265f2b9351f09739085a8e2f9ea376dc9aa46b86042`; public asset matched and contains the new allocation error copy.
- JIN `class-deduction.service` and Nginx are active; `nginx -t` passed; local and public health endpoints returned `{"status":"UP"}`.

## Rollback

- Restore `current` to `/home/class-deduction-system/releases/v2-20261028-eae168a-transport-roster` and `/home/class-deduction-system/front/dist` to that release's `front/dist`.
- Restore `/etc/systemd/system/class-deduction.service.d/v2.conf` from this release's `backend/v2.conf.before`, run `systemctl daemon-reload && systemctl restart class-deduction.service`, then `nginx -t && systemctl reload nginx`.
