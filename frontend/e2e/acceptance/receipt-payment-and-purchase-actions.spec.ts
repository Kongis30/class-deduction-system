import { expect, test, type Page } from '@playwright/test';

const admin = { userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'] };

async function useMockAdmin(page: Page) {
  await page.addInitScript((user) => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);
}

test('Data Summary opens the same order when one course has multiple purchase rows', async ({ page }) => {
  await useMockAdmin(page);
  const purchases = [
    { orderId: '102', orderItemId: '502', enrollmentNo: 'EO-SINGLE', studentId: '201', studentName: 'Single Student',
      courseType: 'GROUP', courseProductId: '10', courseProductName: 'After School', logicalCourseId: '601',
      signupDate: '2026-09-07', periodStartDate: '2026-09-10', periodEndDate: '2026-10-31',
      amountDue: 800, paidAmount: 800, balanceAmount: 0, orderStatus: 'CONFIRMED', orderPaymentStatus: 'PAID' },
    { orderId: '102', orderItemId: '503', enrollmentNo: 'EO-SINGLE', studentId: '201', studentName: 'Single Student',
      courseType: 'GROUP', courseProductId: '10', courseProductName: 'After School', logicalCourseId: '601',
      signupDate: '2026-10-23', periodStartDate: '2026-11-01', periodEndDate: '2026-12-31',
      amountDue: 800, paidAmount: 800, balanceAmount: 0, orderStatus: 'CONFIRMED', orderPaymentStatus: 'PAID' },
  ];
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) return route.continue();
    const data = path.endsWith('/v1/auth/me') ? admin
      : path.endsWith('/v1/base/course-products/all')
        ? [{ id: '10', productName: 'After School', teachingMode: 'GROUP', status: 'ACTIVE', sortOrder: 1 }]
          : path.endsWith('/v1/data-summary')
          ? { rows: purchases, totalRegistrationCount: 1, totalIncomeAmount: 1600, totalOutstandingAmount: 0 }
          : path.match(/\/v1\/enrollment\/orders\/102$/)
            ? { id: '102', studentNameSnapshot: 'Single Student',
              studentId: '200', orderStatus: 'CONFIRMED', items: [], payments: [], participants: [], packages: [], receipts: [] }
            : [];
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });

  await page.goto('/data-summary');
  const row = page.locator('.workbook-data-row').filter({ hasText: 'Single Student' });
  await expect(row).toHaveCount(1);
  await row.click({ button: 'right' });
  await expect(page.getByRole('dialog', { name: 'Course purchases' })).toHaveCount(0);
  await expect(page.getByRole('menuitem', { name: 'View receipt' })).toBeVisible();
  await page.getByRole('menuitem', { name: 'View receipt' }).click();
  await expect(page).toHaveURL(/\/registration\?orderId=102/);
});

test('Edit Receipt adds a payment to the current order and keeps its receipt route', async ({ page }) => {
  await useMockAdmin(page);
  let school = 'PS 244';
  let correctionPayload: Record<string, any> | undefined;
  let payments: Array<Record<string, any>> = [];
  const item = { id: '500', lineNo: 1, courseType: 'GROUP', courseProductId: '10', groupProjectId: '20',
    groupRegistrationPlanId: '30', classId: '40', className: 'G1', packageUnit: 'MONTH', packageQuantity: 2,
    purchasedMonths: 2, priceAmount: 800, startDate: '2026-09-14', itemStatus: 'ACTIVE' };
  const setup = { productId: '10', productName: 'After School', projects: [{ id: '20', projectName: 'G1-G5', status: 'ACTIVE',
    plans: [{ id: '30', planName: '2 months', packageUnit: 'MONTH', packageQuantity: 2, fullDayPrice: 800, status: 'ACTIVE' }],
    classes: [{ id: '40', className: 'G1', actualClass: 'G1', campusId: '1', campusName: 'North Campus', status: 'ACTIVE' }] }] };
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) return route.continue();
    if (path.endsWith('/v1/enrollment/orders/100/correction') && route.request().method() === 'PUT') {
      correctionPayload = route.request().postDataJSON();
      school = correctionPayload.school;
      payments = correctionPayload.payments;
      await route.fulfill({ json: { success: true, code: 'OK', data: null } });
      return;
    }
    const order = { id: '100', enrollmentNo: 'EO-100', studentId: '200', studentNameSnapshot: 'Yuqi Chen',
      signupDate: '2026-09-14', orderStatus: 'CONFIRMED', paymentStatus: payments.length ? 'PARTIAL' : 'UNPAID',
      amountDue: 800, receivedAmount: payments.reduce((total, payment) => total + Number(payment.amount), 0),
      balanceAmount: 800 - payments.reduce((total, payment) => total + Number(payment.amount), 0), campusId: '1',
      campusNameSnapshot: 'North Campus', schoolSnapshot: school, items: [item], packages: [], payments, participants: [],
      receipts: [{ id: '300', templateCode: 'DEFAULT_RECEIPT', receiptPayload: JSON.stringify({
        studentName: 'Yuqi Chen', signupDate: '2026-09-14', items: [item], payments,
      }) }] };
    const data = path.endsWith('/v1/auth/me') ? admin
      : path.endsWith('/v1/enrollment/orders/100') ? order
        : path.endsWith('/v2/group-course-model/products/10') ? setup
          : path.endsWith('/v1/enrollment/payment-methods/active') ? [{ id: '1', methodCode: 'CASH', methodName: 'Cash', status: 'ACTIVE' }]
          : path.endsWith('/v1/base/campuses') ? [{ id: '1', campusName: 'North Campus', status: 'ACTIVE' }]
            : path.endsWith('/v1/base/course-products') ? { records: [], total: 0 }
              : [];
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });

  await page.goto('/registration?orderId=100');
  await page.getByRole('button', { name: 'Edit Receipt' }).click();
  const addPayment = page.getByRole('button', { name: 'Add payment' });
  await expect(addPayment).toBeVisible();
  await expect(page.getByRole('button', { name: 'Save changes' })).toBeVisible();
  await page.locator('label').filter({ hasText: 'School:' }).locator('input').fill('New School');
  await addPayment.click();
  const payment = page.locator('.course-payment-entry').last();
  await payment.locator('.el-select__wrapper').click();
  await page.getByRole('option', { name: 'Cash' }).click();
  await payment.locator('.payment-amount-field input').fill('125');
  await page.getByRole('button', { name: 'Save changes' }).click();
  const reasonDialog = page.getByRole('dialog', { name: 'Payment correction reason' });
  await reasonDialog.getByRole('textbox').fill('Adding payment to the current order');
  await reasonDialog.getByRole('button', { name: 'Continue' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Save correction' }).click();
  await expect.poll(() => correctionPayload?.payments?.length).toBe(1);
  expect(correctionPayload?.payments[0]).toMatchObject({ amount: 125, paymentMethod: 'CASH' });
  expect(correctionPayload?.payments[0].paymentId).toBeUndefined();
  await expect(page).toHaveURL(/\/registration\?orderId=100/);
});

test('Edit Receipt adds a course row under the current order and keeps the receipt available', async ({ page }) => {
  await useMockAdmin(page);
  let correctionPayload: Record<string, any> | undefined;
  const originalItem = { id: '500', lineNo: 1, courseType: 'GROUP', courseProductId: '10', courseProductName: 'After School',
    groupProjectId: '20', groupRegistrationPlanId: '30', classId: '40', classEnrollmentId: '700', className: 'G1',
    logicalCourseId: '600', gradeBand: 'G1-G5', packageUnit: 'MONTH', packageQuantity: 2, purchasedMonths: 2,
    priceAmount: 800, startDate: '2026-09-14', entitlementExpiryDate: '2026-10-31', itemStatus: 'ACTIVE' };
  const setup = { productId: '10', productName: 'After School', projects: [{ id: '20', projectName: 'G1-G5', status: 'ACTIVE',
    plans: [{ id: '30', planName: '2 months · $800', packageUnit: 'MONTH', packageQuantity: 2, fullDayPrice: 800,
      coverageType: 'ROLLING_MONTHS', status: 'ACTIVE' }],
    classes: [{ id: '40', className: 'G1', actualClass: 'G1', campusId: '1', campusName: 'North Campus',
      startDate: '2026-09-14', endDate: '2027-06-30', status: 'ACTIVE' }] }] };
  let savedItems = [originalItem];
  let receiptItems = [originalItem];
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) return route.continue();
    if (path.endsWith('/v1/enrollment/orders/100/correction') && route.request().method() === 'PUT') {
      correctionPayload = route.request().postDataJSON();
      const added = { ...correctionPayload.items[0], orderItemId: undefined, id: '501', lineNo: 2,
        classEnrollmentId: '701', logicalCourseId: '600', entitlementExpiryDate: '2026-12-31', itemStatus: 'ACTIVE' };
      savedItems = [originalItem, added];
      receiptItems = savedItems;
      await route.fulfill({ json: { success: true, code: 'OK', data: null } });
      return;
    }
    const order = { id: '100', enrollmentNo: 'EO-100', studentId: '200', studentNameSnapshot: 'Yuqi Chen',
      signupDate: '2026-09-14', version: 1, orderStatus: 'CONFIRMED', paymentStatus: 'PAID', amountDue: 1600,
      receivedAmount: 800, balanceAmount: 800, campusId: '1', campusNameSnapshot: 'North Campus',
      items: savedItems, packages: [], payments: [], participants: [],
      receipts: [{ id: '300', templateCode: 'DEFAULT_RECEIPT', receiptPayload: JSON.stringify({
        studentName: 'Yuqi Chen', signupDate: '2026-09-14', items: receiptItems, payments: [],
      }) }] };
    const data = path.endsWith('/v1/auth/me') ? admin
      : path.endsWith('/v1/enrollment/orders/100') ? order
        : path.endsWith('/v2/group-course-model/products/10') ? setup
          : path.endsWith('/v2/group-course-model/classes/40/scheduled-dates')
            ? ['2026-09-14', '2026-10-12', '2026-11-02', '2026-11-09']
            : path.endsWith('/v1/enrollment/payment-methods/active') ? []
              : path.endsWith('/v1/base/campuses') ? [{ id: '1', campusName: 'North Campus', status: 'ACTIVE' }]
                : path.endsWith('/v1/base/course-products') ? { records: [], total: 0 }
                  : [];
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });

  await page.goto('/registration?orderId=100');
  await page.getByRole('button', { name: 'Edit Receipt' }).click();
  await page.getByRole('button', { name: 'Add course' }).click();
  await expect(page.locator('.course-entry-row')).toHaveCount(2);
  await page.getByRole('button', { name: 'Save changes' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Save correction' }).click();

  await expect.poll(() => correctionPayload?.items?.length).toBe(1);
  expect(correctionPayload?.items[0]).not.toHaveProperty('orderItemId');
  expect(correctionPayload?.items[0]).toMatchObject({
    renewalSourceOrderItemId: '500',
    courseProductId: '10',
    groupProjectId: '20',
    groupRegistrationPlanId: '30',
    packageQuantity: 2,
    priceAmount: 800,
  });
  await expect(page).toHaveURL(/\/registration\?orderId=100/);
  await expect(page.locator('.course-entry-row')).toHaveCount(2);
});
