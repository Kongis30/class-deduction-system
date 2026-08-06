import { expect, test } from '@playwright/test';

import { apiLogin } from '../helpers/api';
import { loginAsAdmin } from '../ui/helpers/session';
import {
  cleanupAcceptanceData,
  createOverlappingOneOnOneSchedule,
} from './helpers';

test('student calendar clearly marks overlapping lessons', async ({ page }) => {
  await cleanupAcceptanceData();
  const token = await apiLogin(page.request);
  const suffix = `student-conflict-${Date.now().toString(36)}`;
  const { student } = await createOverlappingOneOnOneSchedule(page.request, token, suffix);

  await loginAsAdmin(page);
  await page.goto('/calendar');
  await page.getByRole('tab', { name: 'Student Calendar' }).click();

  await page.getByPlaceholder('Search students...').filter({ visible: true }).fill(student.studentName);
  await page.getByText(student.studentName, { exact: true }).filter({ visible: true }).first().click();

  await expect(page.getByText('1 schedule conflict this week')).toBeVisible();
  await expect(page.getByText('2 overlapping lessons')).toBeVisible();
  await expect(page.locator('.wts-cell--conflict')).not.toHaveCount(0);

  await cleanupAcceptanceData();
});
