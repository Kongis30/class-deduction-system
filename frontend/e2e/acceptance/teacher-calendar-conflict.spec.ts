import { expect, test } from '@playwright/test';

import { loginAsAdmin } from '../ui/helpers/session';

test('teacher calendar clearly marks overlapping lessons', async ({ page }) => {
  await loginAsAdmin(page);
  await page.goto('/calendar');

  await page.getByPlaceholder('Search teachers...').filter({ visible: true }).fill('DEMO Mr. RoomConflict');
  await page.getByText('DEMO Mr. RoomConflict', { exact: true }).filter({ visible: true }).click();

  await expect(page.getByText('1 teaching conflict this week')).toBeVisible();
  await expect(page.getByText('2 overlapping lessons')).toBeVisible();
  await expect(page.locator('.wts-cell--conflict')).not.toHaveCount(0);
});
