import { expect, test } from '@playwright/test';

import { loginAsAdmin } from '../ui/helpers/session';

test('student calendar clearly marks overlapping lessons', async ({ page }) => {
  await loginAsAdmin(page);
  await page.goto('/calendar');
  await page.getByRole('tab', { name: 'Student Calendar' }).click();

  await page.getByPlaceholder('Search students...').filter({ visible: true }).fill('DEMO Amy Wang');
  await page.getByText('DEMO Amy Wang', { exact: true }).filter({ visible: true }).first().click();

  await expect(page.getByText('1 schedule conflict this week')).toBeVisible();
  await expect(page.getByText('2 overlapping lessons')).toBeVisible();
  await expect(page.locator('.wts-cell--conflict')).not.toHaveCount(0);
});
