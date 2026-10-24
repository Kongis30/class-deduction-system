import { expect, test } from '@playwright/test';

test('Data Summary keeps one course row per order and opens its original receipt', async ({ page }) => {
  const admin = { userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'] };
  const pageErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error.stack ?? error.message));
  await page.addInitScript((user) => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);

  const course = {
    studentId: '201', studentName: 'Renewed Student', courseType: 'GROUP',
    courseProductId: '10', courseProductName: '2026-2027 After School',
    orderStatus: 'CONFIRMED', orderPaymentStatus: 'PAID',
    amountDue: 800, paidAmount: 800, balanceAmount: 0, className: 'G1', campusName: 'North Campus',
  };
  const rows = [
    { ...course, orderId: '101', orderItemId: '501', logicalCourseId: '601',
      enrollmentNo: 'EO-ORIGINAL', signupDate: '2026-09-07',
      periodStartDate: '2026-09-10', periodEndDate: '2026-10-31' },
    { ...course, orderId: '101', orderItemId: '502', logicalCourseId: '601',
      enrollmentNo: 'EO-ORIGINAL', signupDate: '2026-09-07',
      periodStartDate: '2026-11-02', periodEndDate: '2026-12-31' },
    { ...course, orderId: '101', orderItemId: '503', logicalCourseId: '603',
      enrollmentNo: 'EO-ORIGINAL', signupDate: '2026-09-07',
      periodStartDate: '2026-10-01', periodEndDate: '2026-11-30' },
    { ...course, orderId: '102', orderItemId: '504', logicalCourseId: '601',
      enrollmentNo: 'EO-SEPARATE', signupDate: '2026-10-23',
      periodStartDate: '2027-01-01', periodEndDate: '2027-02-28' },
  ];

  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) return route.continue();
    const refundItems = [501, 502, 503].map((id, index) => ({
      orderItemId: String(id), lineNo: index + 1, detail: `Purchase ${index + 1}`,
      paidAmount: 800, consumedLessonCount: 0, consumedAmount: 0, automaticStopSupported: false,
    }));
    const data = path.endsWith('/v1/auth/me') ? admin
      : path.endsWith('/v1/system/messages')
        ? { unreadCount: 0, messages: [] }
      : path.endsWith('/v1/enrollment/orders/101/refund-preview')
        ? { items: refundItems, unassignedPaymentAmount: 0 }
      : path.endsWith('/v1/enrollment/orders/101')
        ? { id: '101', enrollmentNo: 'EO-ORIGINAL', studentId: '201', studentName: 'Renewed Student',
            orderStatus: 'CONFIRMED', paymentStatus: 'PAID', receivedAmount: 2400,
            amountDue: 2400, balanceAmount: 0, items: refundItems.map((item) => ({
              id: item.orderItemId, courseType: 'GROUP', classId: '31',
            })) }
      : path.endsWith('/v1/enrollment/orders')
        ? { records: [], total: 0 }
      : path.endsWith('/v1/base/course-products/all')
        ? [{ id: '10', productName: '2026-2027 After School', teachingMode: 'GROUP', status: 'ACTIVE', sortOrder: 1 }]
        : path.endsWith('/v1/data-summary')
          ? { rows, totalRegistrationCount: 2, totalIncomeAmount: 3200, totalOutstandingAmount: 0 }
          : [];
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });

  await page.goto('/data-summary');
  await expect(page.locator('.workbook-data-row').filter({ hasText: 'Renewed Student' })).toHaveCount(3);

  const renewal = page.locator('[data-student-find-key="course-101-GROUP-601-201"]');
  await renewal.locator('.workbook-student-cell').click({ button: 'right' });
  await expect(page.getByRole('menuitem', { name: 'View receipt' })).toHaveCount(1);
  await expect(page.getByRole('dialog', { name: 'Course purchases' })).toHaveCount(0);
  expect(pageErrors).toEqual([]);
  await page.getByRole('menuitem', { name: 'View receipt' }).click();
  await expect(page).toHaveURL(/\/registration\?orderId=101/);

  await page.goto('/data-summary');
  pageErrors.length = 0;
  const otherCourse = page.locator('[data-student-find-key="101-503-201-"]');
  await otherCourse.locator('.workbook-student-cell').click({ button: 'right' });
  expect(pageErrors).toEqual([]);
  await page.getByRole('menuitem', { name: 'View receipt' }).click();
  await expect(page).toHaveURL(/\/registration\?orderId=101/);

  await page.goto('/data-summary');
  await renewal.locator('.workbook-student-cell').click({ button: 'right' });
  await page.getByRole('menuitem', { name: 'Refund', exact: true }).click();
  const refundDialog = page.locator('.el-dialog').filter({ hasText: 'Course purchase to refund' });
  await expect(refundDialog.getByText('Course purchase to refund')).toBeVisible();
  await refundDialog.locator('.el-form-item').filter({ hasText: 'Course purchase to refund' }).locator('.el-select').click();
  await expect(page.getByRole('option', { name: /Course 1 · Purchase 1/ })).toBeVisible();
  await expect(page.getByRole('option', { name: /Course 2 · Purchase 2/ })).toBeVisible();
  await expect(page.getByRole('option', { name: /Course 3 · Purchase 3/ })).toHaveCount(0);
  await expect(page.getByRole('option', { name: /Entire receipt/ })).toHaveCount(0);
  expect(pageErrors).toEqual([]);
});
