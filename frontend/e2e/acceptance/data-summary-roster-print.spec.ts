import { expect, test } from '@playwright/test';

const admin = {
  userId: '1',
  username: 'admin',
  displayName: 'Admin',
  userType: 'ADMIN',
  roleCodes: ['ADMIN'],
};

test('Data Summary prints the selected class plan roster on one page', async ({ page }) => {
  await page.addInitScript((user) => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
    Object.defineProperty(window, 'print', { configurable: true, value: () => undefined });
  }, admin);

  const rosterRows = Array.from({ length: 70 }, (_, index) => ({
    orderId: String(1000 + index),
    orderItemId: String(2000 + index),
    studentId: String(3000 + index),
    studentName: `Student ${String(index + 1).padStart(2, '0')}`,
    school: 'North School',
    courseProductId: '8001',
    courseProductName: 'After School',
    classId: '9001',
    className: 'G1',
    campusId: '7001',
    campusName: 'North Campus',
    registrationPlanId: '501',
    registrationPlanName: 'Fall Plan',
    courseType: 'GROUP',
    orderStatus: 'CONFIRMED',
    orderPaymentStatus: 'PAID',
    paymentStatus: 'PAID',
    itemStatus: 'ACTIVE',
    signupDate: index % 2 ? '2026-09-10' : '2026-10-10',
  }));
  rosterRows.push({
    ...rosterRows[0],
    orderId: '2001', orderItemId: '3001', studentId: '4001', studentName: 'Spring Student',
    classId: '9001', registrationPlanId: '502', registrationPlanName: 'Spring Plan',
  });
  rosterRows.push({
    ...rosterRows[0],
    orderId: '2002', orderItemId: '3002', studentId: '4002', studentName: 'Other Class Student',
    classId: '9002', className: 'G2', registrationPlanId: '503', registrationPlanName: 'G2 Plan',
  });

  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) return route.continue();
    const data = path.endsWith('/v1/auth/me') ? admin
      : path.endsWith('/v1/base/course-products/all')
        ? [{ id: '8001', productName: 'After School', sortOrder: 1, status: 'ACTIVE', teachingMode: 'GROUP' }]
        : path.endsWith('/v1/data-summary')
          ? { rows: rosterRows }
          : path.endsWith('/v1/system/messages')
            ? { unreadCount: 0, messages: [] }
            : [];
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });

  await page.goto('/data-summary');
  await page.getByRole('button', { name: 'Print class roster' }).click();
  const dialog = page.getByRole('dialog', { name: 'Print class roster' });
  const selectors = dialog.locator('.el-select__wrapper');
  await selectors.nth(0).click();
  await page.getByRole('option', { name: /G1 · North Campus · After School/ }).click();
  await selectors.nth(1).click();
  await expect(page.getByRole('option', { name: 'Fall Plan · 70 students' })).toBeVisible();
  await expect(page.getByRole('option', { name: 'Spring Plan · 1 student' })).toBeVisible();
  await selectors.nth(0).click();
  await page.getByRole('option', { name: /G2 · North Campus · After School/ }).click();
  await selectors.nth(1).click();
  await expect(page.getByRole('option', { name: 'G2 Plan · 1 student' })).toBeVisible();
  await expect(page.getByRole('option', { name: 'Fall Plan · 70 students' })).not.toBeAttached();
  await selectors.nth(0).click();
  await page.getByRole('option', { name: /G1 · North Campus · After School/ }).click();
  await selectors.nth(1).click();
  await page.getByRole('option', { name: 'Fall Plan · 70 students' }).click();
  await expect(dialog.getByText('70 students · one page')).toBeVisible();

  // 列表覆盖两个报名月份，打印名单不得随当前月份筛选缩小。
  await dialog.getByRole('button', { name: 'Print', exact: true }).click();
  const printRoot = page.locator('#print-document-root .roster-print__page');
  await expect(printRoot).toHaveCount(1);
  await expect(printRoot.locator('tbody tr')).toHaveCount(35);
  await expect(printRoot).toContainText('Student 70');
  await expect(printRoot).not.toContainText('Spring Student');
  await expect(printRoot).not.toContainText('Other Class Student');

  await page.emulateMedia({ media: 'print' });
  const pdf = await page.pdf({ format: 'Letter', preferCSSPageSize: true });
  const pageCount = (pdf.toString('latin1').match(/\/Type\s*\/Page\b/g) ?? []).length;
  expect(pageCount).toBe(1);
});
