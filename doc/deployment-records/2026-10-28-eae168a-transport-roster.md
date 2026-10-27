# JIN V2 deployment: transport roster eligibility

- Source commit: `eae168a` on `main_v2` (`Fix transport roster eligibility rules`).
- Release directory: `/home/class-deduction-system/releases/v2-20261028-eae168a-transport-roster`.
- Previous release: `/home/class-deduction-system/releases/v2-20261027-9982aa2-fixed-price`.
- Deployment scope: frontend and backend artifacts built from the source commit. No database migration was required.

## Change

- The transport roster now follows active course service entitlement instead of excluding every partially refunded row.
- Discontinued classification does not remove an otherwise active student from transportation.
- Stopped and expired group registrations are excluded; private course packages are included only while active with remaining units.

## Verification

- `DataSummaryServiceImplTest`: passed.
- Frontend `npm run build`: passed.
- Backend production package build with tests skipped: passed; the targeted backend test was run separately.
- JIN service is active and `http://127.0.0.1:8084/api/actuator/health` returned `{"status":"UP"}`.
- Backend JAR SHA-256: `9c51f2995c3c9abd61993c586e8691f99cfaa4ebe332dc655e03cd6ed693b7a4`.
- Frontend `index.html` SHA-256: `2bab452f1aba80e78d1dc75c5b08388f4e6047a34bdd2eda4a3a45c47e780b4d`; the public index matched this checksum.

## Rollback

- Restore `current` and `/home/class-deduction-system/front/dist` to the previous release path above.
- Restore `/etc/systemd/system/class-deduction.service.d/v2.conf` from this release's `backend/v2.conf.before`, run `systemctl daemon-reload`, and restart `class-deduction.service`.
