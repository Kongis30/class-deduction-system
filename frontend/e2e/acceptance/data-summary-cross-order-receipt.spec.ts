import { expect, test } from '@playwright/test';

test('Data Summary lets a renewed course open either historical receipt', async ({ page }) => {
  const admin = { userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'] };
  const pageErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  await page.addInitScript((user) => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);

  const course = {
    studentId: '201', studentName: 'Renewed Student', courseType: 'GROUP',
    courseProductId: '10', courseProductName: '2026-2027 After School',
    logicalCourseId: '601', orderStatus: 'CONFIRMED', orderPaymentStatus: 'PAID',
    amountDue: 800, paidAmount: 800, balanceAmount: 0, className: 'G1', campusName: 'North Campus',
  };
  const rows = [
    { ...course, orderId: '101', orderItemId: '501',
      enrollmentNo: 'EO-ORIGINAL', signupDate: '2026-09-07',
      periodStartDate: '2026-09-10', periodEndDate: '2026-10-31' },
    { ...course, orderId: '102', orderItemId: '502',
      enrollmentNo: 'EO-RENEWAL', signupDate: '2026-10-23',
      periodStartDate: '2026-11-02', periodEndDate: '2026-12-31' },
    { ...course, studentId: '100', studentName: 'Normal Student', logicalCourseId: '200',
      orderId: '300', orderItemId: '400', enrollmentNo: 'EO-NORMAL', signupDate: '2026-10-01' },
  ];

  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) return route.continue();
    const data = path.endsWith('/v1/auth/me') ? admin
      : path.endsWith('/v1/base/course-products/all')
        ? [{ id: '10', productName: '2026-2027 After School', teachingMode: 'GROUP', status: 'ACTIVE', sortOrder: 1 }]
        : path.endsWith('/v1/data-summary')
          ? { rows, totalRegistrationCount: 3, totalIncomeAmount: 2400, totalOutstandingAmount: 0 }
          : [];
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });

  await page.goto('/data-summary');
  const normal = page.locator('.workbook-data-row').filter({ hasText: 'Normal Student' });
  const renewed = page.locator('.workbook-data-row').filter({ hasText: 'Renewed Student' });
  await expect(normal).toHaveCount(1);
  await expect(renewed).toHaveCount(1);

  await normal.locator('.workbook-student-cell').click({ button: 'right' });
  await expect(page.getByRole('menuitem', { name: 'View receipt' })).toBeVisible();
  await page.keyboard.press('Escape');

  await renewed.locator('.workbook-student-cell').click({ button: 'right' });
  expect(pageErrors).toEqual([]);
  const dialog = page.getByRole('dialog', { name: 'Course purchases' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'View receipt' })).toHaveCount(2);
  await dialog.getByRole('button', { name: 'View receipt' }).last().click();
  await expect(page).toHaveURL(/\/registration\?orderId=102/);
});
