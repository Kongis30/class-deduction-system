import { expect, test } from '@playwright/test';

// 不同数据页及乱序的课程关系都应按名称显示。
test('students and each level of groups are sorted by name', async ({ page }) => {
  const admin = { userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'] };
  await page.addInitScript((user) => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);
  const students = [
    { id: '1', studentName: 'zoe', status: 'ACTIVE' },
    { id: '2', studentName: 'Amy', status: 'ACTIVE' },
    { id: '3', studentName: 'bob', status: 'ACTIVE' },
    { id: '4', studentName: 'Unassigned', status: 'ACTIVE' },
  ];
  const memberships = [
    { studentId: '1', productId: 'z', productName: 'Zebra', projectId: 'z', projectName: 'Z project', classId: 'z', className: 'Z class' },
    ...['1', '2', '3'].map(studentId => ({ studentId, productId: 'a', productName: 'alpha', projectId: 'a', projectName: 'A project', classId: 'a', className: 'A class' })),
    { studentId: '1', productId: 'a', productName: 'alpha', projectId: 'a', projectName: 'A project', classId: 'b', className: 'B class' },
    { studentId: '1', productId: 'a', productName: 'alpha', projectId: 'b', projectName: 'B project', classId: 'z', className: 'Z class' },
  ];
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (!url.pathname.startsWith('/api/')) return route.continue();
    let data: unknown = [];
    if (url.pathname.endsWith('/v1/auth/me')) data = admin;
    else if (url.pathname.endsWith('/v1/base/students/course-memberships')) data = memberships;
    else if (url.pathname.endsWith('/v1/base/students')) {
      const second = url.searchParams.get('pageNum') === '2';
      data = { records: second ? students.slice(2) : students.slice(0, 2), total: 4 };
    } else if (url.pathname.endsWith('/v1/auth/student-accounts')) data = { records: [], total: 0 };
    else if (url.pathname.endsWith('/v1/system/messages')) data = { messages: [], unreadCount: 0 };
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });
  await page.goto('/admin/config/people');
  const products = page.locator('details.student-product');
  await expect(products.locator(':scope > summary')).toHaveText([/alpha/, /Zebra/, /No course/]);
  await products.nth(0).locator(':scope > summary').click();
  const projects = products.nth(0).locator(':scope > details.student-project');
  await expect(projects.locator(':scope > summary')).toHaveText([/A project/, /B project/]);
  await projects.nth(0).locator(':scope > summary').click();
  const classes = projects.nth(0).locator(':scope > details.student-group');
  await expect(classes.locator(':scope > summary')).toHaveText([/A class/, /B class/]);
  await classes.nth(0).locator(':scope > summary').click();
  await expect(classes.nth(0).locator('tbody tr td:first-child strong')).toHaveText(['Amy', 'bob', 'zoe']);
});
