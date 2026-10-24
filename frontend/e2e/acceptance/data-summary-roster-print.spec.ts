import { expect, test } from '@playwright/test';

const admin = {
  userId: '1',
  username: 'admin',
  displayName: 'Admin',
  userType: 'ADMIN',
  roleCodes: ['ADMIN'],
};

test('Data Summary prints a deduplicated whole-class roster without requiring a registration plan', async ({ page }) => {
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
    groupProjectName: 'After School Project',
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
  // 同一学生在两个 plan 下重复报名，整班名单只能出现一次。
  rosterRows.push({
    ...rosterRows[0],
    orderId: '2002', orderItemId: '3002', studentId: '3000', studentName: 'Student 01',
    classId: '9001', registrationPlanId: '502', registrationPlanName: 'Spring Plan',
  });
  rosterRows.push({
    ...rosterRows[0],
    orderId: '2003', orderItemId: '3003', studentId: '4002', studentName: 'Other Class Student',
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
  const printButton = dialog.getByRole('button', { name: 'Print', exact: true });

  await expect(printButton).toBeDisabled();
  await selectors.nth(0).click();
  await page.getByRole('option', { name: /G1 · North Campus · After School · After School Project/ }).click();

  // 只选班级即可打印，默认覆盖该班全部 plan。
  await expect(printButton).toBeEnabled();
  await expect(dialog.getByText('71 students · one page')).toBeVisible();

  await selectors.nth(1).click();
  await expect(page.getByRole('option', { name: 'All plans · 71 students' })).toBeVisible();
  await expect(page.getByRole('option', { name: 'Fall Plan · 70 students' })).toBeVisible();
  await expect(page.getByRole('option', { name: 'Spring Plan · 2 students' })).toBeVisible();
  await page.getByRole('option', { name: 'All plans · 71 students' }).click();
  await expect(dialog.getByText('71 students · one page')).toBeVisible();

  await printButton.click();
  const printRoot = page.locator('#print-document-root .roster-print__page');
  await expect(printRoot).toHaveCount(1);
  await expect(printRoot.locator('h1')).toHaveText('After School');
  await expect(printRoot.locator('h2')).toContainText('After School Project · G1');
  await expect(printRoot.locator('.roster-print__campus')).toHaveText('North Campus');
  // 71 人超过单页 25 行，应自动扩展为 3 个学生列（6 个表头单元格）。
  await expect(printRoot.locator('thead th')).toHaveCount(6);
  const columnWidths = await printRoot.locator('colgroup col').evaluateAll((columns) => (
    columns.map((column) => (column as HTMLElement).style.width)
  ));
  expect(columnWidths).toHaveLength(6);
  expect(new Set(columnWidths).size).toBe(1);
  await expect(printRoot.locator('tbody tr')).toHaveCount(24);
  await expect(printRoot).toContainText('Student 70');
  await expect(printRoot).toContainText('Spring Student');
  await expect(printRoot.getByText('Student 01', { exact: true })).toHaveCount(1);
  await expect(printRoot).not.toContainText('Other Class Student');

  await page.emulateMedia({ media: 'print' });
  const pdf = await page.pdf({ format: 'Letter', preferCSSPageSize: true });
  const pageCount = (pdf.toString('latin1').match(/\/Type\s*\/Page\b/g) ?? []).length;
  expect(pageCount).toBe(1);

  // plan 下拉框仍保留，选择单个 plan 时只打印该 plan。
  await page.emulateMedia({ media: 'screen' });
  await page.getByRole('button', { name: 'Print class roster' }).click();
  const planDialog = page.getByRole('dialog', { name: 'Print class roster' });
  const planSelectors = planDialog.locator('.el-select__wrapper');
  await planSelectors.nth(0).click();
  await page.getByRole('option', { name: /G1 · North Campus · After School · After School Project/ }).click();
  await planSelectors.nth(1).click();
  await page.getByRole('option', { name: 'Fall Plan · 70 students' }).click();
  await expect(planDialog.getByText('70 students · one page')).toBeVisible();
  await planDialog.getByRole('button', { name: 'Print', exact: true }).click();

  const planPrintRoot = page.locator('#print-document-root .roster-print__page');
  await expect(planPrintRoot).toHaveCount(1);
  await expect(planPrintRoot).toContainText('Student 70');
  await expect(planPrintRoot).not.toContainText('Spring Student');
  await expect(planPrintRoot).not.toContainText('Other Class Student');
});
