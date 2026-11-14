import { expect, test } from '@playwright/test';

for (const failure of ['invalid', 'network']) {
  test(`calendar handles ${failure} responses and retries the relevant tab`, async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('jbt_auth_token', 'test-token');
      localStorage.setItem('jbt_auth_user', JSON.stringify({ userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'] }));
    });
    let studentRequests = 0;
    let roomRequests = 0;
    await page.route('**/api/**', async route => {
      const path = new URL(route.request().url()).pathname;
      if (!path.startsWith('/api/')) return route.continue();
      if (path.endsWith('/students/one-on-one') || path.endsWith('/classrooms/active')) {
        const isStudent = path.endsWith('/students/one-on-one');
        const count = isStudent ? ++studentRequests : ++roomRequests;
        if (count <= 3 && failure === 'network') return route.fulfill({ status: 500, json: { message: 'internal failure .map is not a function' } });
        return route.fulfill({ json: { success: true, code: 'OK', data: count === 1 ? { records: [] } : [] } });
      }
      const data = path.endsWith('/auth/me') ? { userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'] } : [];
      await route.fulfill({ json: { success: true, code: 'OK', data } });
    });
    await page.goto('/calendar?tab=student');
    await expect(page.getByText('Couldn’t load students. Please try again.')).toBeVisible();
    expect(roomRequests).toBe(0);
    await page.locator('.student-list-panel').getByRole('button', { name: 'Retry' }).click();
    await expect(page.getByText('No active 1-on-1 students').first()).toBeVisible();
    expect(studentRequests).toBe(failure === 'network' ? 4 : 2);
    await page.getByRole('tab', { name: /Room Calendar/ }).click();
    await expect(page.getByText('Couldn’t load classrooms. Please try again.').first()).toBeVisible();
    await expect(page.locator('body')).not.toContainText('map is not a function');
    await page.locator('.el-alert').filter({ hasText: 'Couldn’t load classrooms' }).getByRole('button', { name: 'Retry' }).click();
    await expect(page.getByText('No classrooms yet. Add rooms when schedules are ready to backfill.')).toBeVisible();
    expect(roomRequests).toBe(failure === 'network' ? 4 : 2);
    await page.getByRole('tab', { name: /Student Calendar/ }).click();
    expect(studentRequests).toBe(failure === 'network' ? 4 : 2);
  });
}
