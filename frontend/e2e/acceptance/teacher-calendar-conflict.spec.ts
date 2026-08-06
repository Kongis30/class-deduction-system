import { expect, test } from '@playwright/test';

import { apiLogin } from '../helpers/api';
import { loginAsAdmin } from '../ui/helpers/session';
import {
  cleanupAcceptanceData,
  createOverlappingOneOnOneSchedule,
} from './helpers';

test('teacher calendar clearly marks overlapping lessons', async ({ page }) => {
  await cleanupAcceptanceData();
  const token = await apiLogin(page.request);
  const suffix = `teacher-conflict-${Date.now().toString(36)}`;
  const { teacher } = await createOverlappingOneOnOneSchedule(page.request, token, suffix);

  await loginAsAdmin(page);
  await page.goto('/calendar');

  await page.getByPlaceholder('Search teachers...').filter({ visible: true }).fill(teacher.profile.teacherName);
  await page.getByText(teacher.profile.teacherName, { exact: true }).filter({ visible: true }).click();

  await expect(page.getByText('1 teaching conflict this week')).toBeVisible();
  await expect(page.getByText('2 overlapping lessons')).toBeVisible();
  await expect(page.locator('.wts-cell--conflict')).not.toHaveCount(0);

  await cleanupAcceptanceData();
});
