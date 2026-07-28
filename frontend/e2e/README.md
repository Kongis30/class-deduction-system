# Acceptance E2E Tests

This folder contains Playwright tests for customer-level acceptance.

## Current Rule

Use UI for user-facing operations:

- navigation buttons
- registration save
- confirmation buttons
- schedule save
- payment/refund actions

Use API or DB only for:

- prerequisite data setup
- cleanup
- assertions that generated records are correct

This keeps UI regressions visible while making data checks precise.

## Suites

- `acceptance/navigation-buttons.spec.ts`
  - Verifies the four daily entries and Back office route buttons.
- `acceptance/registration-intake.spec.ts`
  - Saves a registration through the UI.
  - Checks order, course line, package, payment, and receipt data.
- `acceptance/data-integrity.spec.ts`
  - Builds a 1-on-1 package and schedule.
  - Confirms a lesson through teacher/admin APIs.
  - Checks occurrence, lesson record, ledger, package balance, and data summary.

## Run

```bash
cd frontend
npm run test:e2e
```

Debug with a visible browser:

```bash
npm run test:e2e:headed
```

Open the Playwright report:

```bash
npx playwright show-report
```

## Environment

Defaults:

- Frontend: `http://127.0.0.1:5173`
- Backend: `http://127.0.0.1:8084/api`
- DB: `127.0.0.1:3306/class_deduction_system`
- Admin: `admin / Jin2025`

Override with:

- `E2E_BASE_URL`
- `E2E_API_BASE_URL`
- `E2E_DB_HOST`
- `E2E_DB_PORT`
- `E2E_DB_USER`
- `E2E_DB_PASSWORD`
- `E2E_DB_NAME`
- `E2E_ADMIN_USERNAME`
- `E2E_ADMIN_PASSWORD`

All generated test data uses marker `E2E_ACCEPTANCE` and is cleaned before and after serial acceptance suites.
