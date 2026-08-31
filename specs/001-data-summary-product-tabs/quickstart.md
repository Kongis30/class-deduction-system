# Quickstart: Data Summary Product Tabs

## Prerequisites

- Start the Spring Boot backend with the project MySQL database available.
- Start the Vue frontend and sign in as an administrator.
- Have at least two non-deleted Products, with at least one registration associated with each Product. Include one inactive Product if validating historical visibility.

## Manual Verification

1. Open `/data-summary` and verify the tab strip is immediately below the workbook table.
2. Verify one tab exists for each non-deleted Product and that the initial order follows Product display order.
3. Select Product A and set a month filter, a column filter, a non-default page size, a page number, and a sort direction.
4. Select Product B and verify only Product B rows are visible.
5. Select Product A again and verify all previously selected state is restored.
6. Drag Product B before Product A. Verify the tab order changes without a full-page reload.
7. Refresh the page and verify the new order remains.
8. Sign out, sign in again, and open Data Summary. Verify the same shared order remains.
9. Use browser developer tools or a mocked failed request to force the order write to fail. Verify an error is shown and the last successful order is restored.
10. Verify balance, payment, expiry, low-hour, and empty-state behavior remains unchanged inside a Product Tab.

## Automated Verification

```bash
cd frontend
npm run typecheck
npm run build
npm run test:e2e -- e2e/acceptance/data-summary-workbook.spec.ts
```

```bash
cd backend
mvn test -Dtest=CourseProductServiceImplTest
mvn test -Dtest=DataSummaryServiceImplTest
```
