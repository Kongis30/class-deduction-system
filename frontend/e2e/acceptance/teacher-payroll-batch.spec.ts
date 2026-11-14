import { expect, test } from '@playwright/test';

for (const failBob of [false, true]) test(`all teachers selection: ${failBob ? 'partial failure' : 'success'}`, async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify({ userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'] }));
  });
  const requests: any[] = [];
  const paidTeachers = new Set<string>();
  const lessons = [
    { lessonRecordId: '11', teacherId: '1', teacherName: 'Alice', studentName: 'A', className: null, lessonStartAt: '2026-11-02T10:00:00', lessonEndAt: '2026-11-02T12:00:00', hours: 2, hourlyRate: 20, amount: 40 },
    { lessonRecordId: '12', teacherId: '2', teacherName: 'Bob', studentName: 'B', className: 'G1', lessonStartAt: '2026-11-03T10:00:00', lessonEndAt: '2026-11-03T12:00:00', hours: 2, hourlyRate: 25, amount: 50 },
    { lessonRecordId: '13', teacherId: '2', teacherName: 'Bob', studentName: 'C', className: null, lessonStartAt: '2026-11-04T10:00:00', lessonEndAt: '2026-11-04T12:00:00', hours: 2, hourlyRate: 0, amount: 0 },
  ];
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) return route.continue();
    if (path.endsWith('/finance/payments') && route.request().method() === 'POST') {
      const payload = route.request().postDataJSON();
      requests.push(payload);
      if (failBob && String(payload.teacherId) === '2') return route.fulfill({ status: 400, json: { message: 'Lesson changed' } });
      paidTeachers.add(String(payload.teacherId));
      return route.fulfill({ json: { success: true, code: 'OK', data: '99' } });
    }
    const data = path.endsWith('/auth/me') ? { userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'] }
      : path.endsWith('/teachers/active') ? [{ id: '1', teacherName: 'Alice', teacherNo: 'T1' }, { id: '2', teacherName: 'Bob', teacherNo: 'T2' }]
      : path.endsWith('/payments/unpaid-lessons') ? lessons.filter(row => !paidTeachers.has(row.teacherId))
      : path.endsWith('/finance/payments') ? { records: [], total: 0 } : [];
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });
  await page.goto('/finance/payments');
  await page.getByRole('button', { name: 'Load Unpaid Lessons' }).click();
  const tables = page.locator('.tp-table').filter({ has: page.locator('.el-table__header') });
  await expect(tables.nth(0)).toContainText('Alice');
  await tables.nth(0).locator('.el-table__header .el-checkbox').click();
  await tables.nth(1).locator('.el-table__header .el-checkbox').click();
  await expect(page.locator('.tp-summary')).toContainText('2 lessons');
  await expect(page.locator('.tp-summary')).toContainText('$90.00');
  await page.getByRole('button', { name: 'Pay', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Cancel' }).click();
  expect(requests).toHaveLength(0);
  await page.getByRole('button', { name: 'Pay', exact: true }).click();
  await page.getByRole('dialog', { name: 'Confirm payment' }).getByRole('button', { name: /OK|Confirm/i }).click();
  if (failBob) {
    await expect(page.getByRole('dialog', { name: 'Payment results' })).toContainText('Bob: Lesson changed');
  } else {
    await expect(page.getByText('2 payment(s) created.')).toBeVisible();
  }
  expect(requests).toHaveLength(2);
  expect(requests.map(r => ({ teacherId: String(r.teacherId), ids: r.lessonRecordIds }))).toEqual([
    { teacherId: '1', ids: ['11'] }, { teacherId: '2', ids: ['12'] },
  ]);
  expect(requests[0].periodStart).toBe(requests[1].periodStart);
  expect(requests[0].periodEnd).toBe(requests[1].periodEnd);
});
