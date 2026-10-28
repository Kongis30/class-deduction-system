import { expect, test } from '@playwright/test';

test('Calendar starts at 06:00 and renders an early lesson in the first visible row', async ({ page }) => {
  const monday = currentMonday();
  await page.addInitScript(() => {
    window.localStorage.setItem('jbt_auth_token', 'calendar-visual-check');
    window.localStorage.setItem('jbt_auth_user', JSON.stringify({
      userId: '1',
      username: 'calendar-check',
      displayName: 'Calendar Check',
      userType: 'TEACHER',
      roleCodes: ['TEACHER'],
      teacherId: '1',
    }));
  });

  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) {
      await route.continue();
      return;
    }
    if (path.endsWith('/v1/auth/me')) {
      await route.fulfill({ json: { success: true, data: {
        userId: '1', username: 'calendar-check', displayName: 'Calendar Check',
        userType: 'TEACHER', roleCodes: ['TEACHER'], teacherId: '1',
      } } });
      return;
    }
    if (path.endsWith('/v1/schedule/occurrences')) {
      await route.fulfill({ json: { success: true, data: { records: [{
        id: 'early-lesson',
        plannedStartAt: `${monday}T06:00:00`,
        plannedEndAt: `${monday}T07:00:00`,
        planningStatus: 'SCHEDULED',
        subjectName: 'Early lesson',
        packageName: 'Early lesson package',
        plannedTeacherName: 'Calendar Check',
        plannedUnits: 2,
      }], total: 1 } } });
      return;
    }
    await route.fulfill({ json: { success: true, data: [] } });
  });

  await page.goto('/calendar');
  await expect(page.locator('.wts-row__label').first()).toHaveText('06:00');
  await expect(page.locator('.wts-event').filter({ hasText: 'Early lesson' })).toBeVisible();
});

function currentMonday(): string {
  const date = new Date();
  date.setHours(0, 0, 0, 0);
  const daysSinceMonday = (date.getDay() + 6) % 7;
  date.setDate(date.getDate() - daysSinceMonday);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
