import { expect, test } from '@playwright/test';
for (const destination of ['1-on-1 Lessons', 'Schedule']) {
  test(`daily lesson participant opens ${destination}`, async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('jbt_auth_token', 'test-token');
      localStorage.setItem('jbt_auth_user', JSON.stringify({ userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'] }));
    });
    await page.route('**/api/**', async route => {
      const path = new URL(route.request().url()).pathname;
      if (!path.startsWith('/api/')) return route.continue();
      const data = path.endsWith('/auth/me') ? { userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'] }
        : path.endsWith('/daily-private-lessons') ? { studentCount: 2, lessons: [{ id: '1', plannedStartAt: '2026-11-12T10:00:00', plannedEndAt: '2026-11-12T12:00:00', participantNames: ['Alice', 'Bob'], participants: [{ studentId: '10', packageId: '100', studentName: 'Alice' }, { studentId: '20', packageId: '200', studentName: 'Bob' }], subjectName: 'Math' }] }
        : path.endsWith('/students/one-on-one') || path.endsWith('/classrooms/active') || path.endsWith('/teachers/active') ? []
        : { records: [], total: 0 };
      await route.fulfill({ json: { success: true, code: 'OK', data } });
    });
    await page.goto('/calendar?tab=student');
    await page.getByRole('button', { name: 'Daily 1-on-1' }).click();
    const dialog = page.getByRole('dialog', { name: 'Daily 1-on-1 lessons' });
    await dialog.getByRole('button', { name: 'Bob' }).click();
    await page.locator('.el-dropdown-menu:visible').getByRole('menuitem', { name: destination }).click();
    await expect(page).toHaveURL(destination === 'Schedule' ? /schedule\/recurring\?.*studentId=20.*packageId=200/ : /one-on-one-deduction\?.*studentId=20.*packageId=200/);
  });
}
