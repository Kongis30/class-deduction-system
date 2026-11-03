import { expect, test } from '@playwright/test';

const admin = { userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'] };
const product = { id: '8001', productName: 'After School', sortOrder: 1, status: 'ACTIVE', teachingMode: 'GROUP' };
test.use({ timezoneId: 'Asia/Shanghai' });

// 跨订单续报和不同学生的相同课程需分别判断。
test('renewal warning persists after expiry and follows linked renewals', async ({ page }) => {
  // 上海已是 11 月 3 日，纽约仍是 11 月 2 日。
  await page.clock.setFixedTime(new Date('2026-11-03T02:00:00Z'));
  await page.addInitScript((user) => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);
  const row = (id: string, name: string, end: string, extra: Record<string, unknown> = {}) => ({
    orderId: id, orderItemId: id, studentId: id, studentName: name,
    courseProductId: product.id, courseProductName: product.productName, courseType: 'GROUP',
    campusName: 'North Campus', className: 'G1', signupDate: '2026-09-01',
    periodStartDate: '2026-09-01', periodEndDate: end, itemStatus: 'ACTIVE',
    amountDue: 100, paidAmount: 100, balanceAmount: 0, ...extra,
  });
  const rows = [
    row('1', 'Eleven days', '2026-11-13'), row('2', 'Ten days', '2026-11-12'),
    row('3', 'Today', '2026-11-02'), row('4', 'Expired', '2026-10-01'),
    row('5', 'Discontinued', '2026-10-01', { discontinued: true }),
    row('6', 'Refunded', '2026-10-01', { refunded: true }),
    row('7', 'One on one', '2026-10-01', { courseType: 'ONE_ON_ONE' }),
    row('8', 'Renewed old', '2026-10-01', { studentId: '88', logicalCourseId: '42' }),
    row('9', 'Renewed new', '2027-01-31', { studentId: '88', logicalCourseId: '42' }),
    row('10', 'Different student', '2026-10-01', { studentId: '99', logicalCourseId: '42' }),
  ];
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) return route.continue();
    const data = path.endsWith('/v1/auth/me') ? admin : path.endsWith('/v1/base/course-products/all') ? [product]
      : path.endsWith('/v1/data-summary') ? { rows } : [];
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });
  await page.goto('/data-summary');
  const rowFor = (name: string) => page.locator('tr.workbook-data-row').filter({ hasText: name });
  for (const name of ['Ten days', 'Today', 'Expired', 'Different student']) {
    await expect(rowFor(name)).toHaveClass(/workbook-data-row--period-due/);
  }
  for (const name of ['Eleven days', 'Renewed old', 'Renewed new', 'One on one', 'Discontinued', 'Refunded']) {
    await expect(rowFor(name)).not.toHaveClass(/workbook-data-row--period-due/);
  }
});
