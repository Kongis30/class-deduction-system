# JIN V2 deployment: refund receipt View

- Source commit: `c75f078` on `main_v2` (`复用原报名收据查看退款订单`).
- Release directory: `/home/class-deduction-system/releases/v2-20261026-c75f078-refund-view`.
- Deployment scope: frontend and backend artifacts built from the same clean commit. No database migration was required.
- Previous backend target: `/home/class-deduction-system/releases/v2-20261026-8a8eeab-discontinued-student`.
- Previous frontend target: `/home/class-deduction-system/releases/v2-20261026-80fe872-data-summary-order/front/dist`.

## Change

- Refund receipt `View` opens the associated enrollment order's saved registration receipt in the existing receipt page.
- Removed the separate custom refund receipt page and route.

## Build and verification

- Frontend `npm ci && npm run build`: passed.
- Backend `mvn -Dmaven.test.skip=true clean package` with Java 17: passed.
- Targeted Playwright button-flow test: passed.
- Production JAR SHA-256: `760e591500b69f937ff16a0689764f71d001fa259bbc5eaf6e6103dbf01c31aa`.
- Production `index.html` SHA-256: `832350dfb7a18540c29b90214152097f35c976c941361fadc9a0d1b74e2bb1f9`.
- Production receipt-page JS SHA-256: `9639b33aadce83c55a6575ba728521be70fcf242910a446c4875d9f28e2f1de7`.
- Public frontend files matched the build checksums; the removed custom refund receipt text is absent from the served receipt bundle.
- `nginx -t` passed; `class-deduction.service` and Nginx are active; public health endpoint returned `{"status":"UP"}`. Unauthenticated `/api/v1/auth/me` returned the expected `401` response.

## Rollback

- Restore `current` to the previous backend target above and `front/dist` to the previous frontend target above.
- Restore `backend/v2.conf.before` from this release to `/etc/systemd/system/class-deduction.service.d/v2.conf`, run `systemctl daemon-reload`, and restart `class-deduction.service`.
