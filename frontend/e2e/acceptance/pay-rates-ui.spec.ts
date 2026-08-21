import { expect, test, type Page } from '@playwright/test';

import { apiGet, apiLogin, type PageResult } from '../helpers/api';
import { cleanupAcceptanceData, createTeacherAccountByApi, type ApiRecord } from './helpers';
import { loginAsAdmin } from '../ui/helpers/session';

test.describe('Acceptance - Pay rates UI', () => {
  test.beforeEach(async () => {
    await cleanupAcceptanceData();
  });

  test.afterEach(async () => {
    await cleanupAcceptanceData();
  });

  test('admin manages teacher rates from the single Pay Rates model', async ({ page, request }) => {
    const token = await apiLogin(request);
    const suffix = Date.now().toString(36);
    const teacher = await createTeacherAccountByApi(request, token, suffix);
    const teacherId = teacher.profile.id;
    const teacherName = String(teacher.profile.teacherName);

    await loginAsAdmin(page);
    await page.goto('/admin/config/teacher-pay-rates');

    await expect(page.getByText('1-on-1 package rates', { exact: true })).toHaveCount(0);
    await expect(page.getByText('Group class slot rates', { exact: true })).toHaveCount(0);
    await expect(page.getByText('Package Rate', { exact: true })).toHaveCount(0);
    await expect(page.getByText('Slot Rate', { exact: true })).toHaveCount(0);
    await expect(page.getByText('Pay / hr', { exact: true })).toHaveCount(0);

    await page.getByPlaceholder('Search teacher...').fill(teacherName);
    await page.locator('.pr-sidebar__item').filter({ hasText: teacherName }).click();
    await expect(page.getByText('Teacher rates', { exact: true })).toBeVisible();
    await expect(page.getByText('Product default rate')).toBeVisible();
    await expect(page.getByText('Grade, delivery, or student')).toBeVisible();

    await page.getByRole('button', { name: 'Set standard rate' }).click();
    const dialog = page.locator('.pr-dialog');
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText('Standard hourly rate');
    await fillInputNumberByLabel(dialog, 'Hourly Rate ($)', '42.5');
    await page.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByText('Standard rate saved.')).toBeVisible();
    await expect(dialog).toBeHidden();
    await expect(page.getByText('$42.50 standard rate')).toBeVisible();

    const rates = await apiGet<ApiRecord[]>(request, token, '/v1/finance/pay-rates', { teacherId });
    const standardRate = rates.find((rate) =>
      rate.courseProductId == null
        && rate.studentId == null
        && !rate.gradeBand
        && !rate.deliveryMode,
    );
    expect(standardRate?.hourlyRate).toBe(42.5);
  });
});

async function fillInputNumberByLabel(scope: ReturnType<Page['locator']>, label: string, value: string): Promise<void> {
  const field = scope.locator('.el-form-item').filter({ hasText: label }).first();
  const input = field.locator('input').first();
  await input.fill(value);
  await input.blur();
}
