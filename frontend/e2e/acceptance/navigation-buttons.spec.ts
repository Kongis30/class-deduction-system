import { expect, test } from '@playwright/test';

import { loginAsAdmin } from '../ui/helpers/session';

test.describe('Acceptance - Navigation and button smoke', () => {
  test('四个日常入口和 Back office 下拉应可点击并进入正确页面', async ({ page }) => {
    await loginAsAdmin(page);

    await clickSidebarEntry(page, 'Registration');
    await expect(page).toHaveURL(/\/registration/);
    await expect(page.getByText('Registration & Receipt')).toBeVisible();

    await clickSidebarEntry(page, 'Calendar');
    await expect(page).toHaveURL(/\/calendar/);
    await expect(page.getByText('School Schedule')).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Room Calendar' })).toBeVisible();

    await clickSidebarEntry(page, '1-on-1 Lessons');
    await expect(page).toHaveURL(/\/one-on-one-deduction/);
    await expect(page.getByRole('button', { name: 'Print Student Sheet' })).toBeVisible();

    await clickSidebarEntry(page, 'Data Summary');
    await expect(page).toHaveURL(/\/data-summary/);
    await expect(page.getByText('Daily registration checks')).toBeVisible();

    await page.getByRole('button', { name: /Back office/ }).click();
    const backOfficeMenu = page.locator('.app-header__admin-tools-menu');
    await expect(backOfficeMenu.getByText('Courses', { exact: true })).toBeVisible();
    await backOfficeMenu.getByText('Prices', { exact: true }).click();
    await expect(page).toHaveURL(/\/admin\/config\/course-prices/);
    await expect(page.getByRole('heading', { name: 'Course Pricing Matrix' })).toBeVisible();
  });
});

async function clickSidebarEntry(page: import('@playwright/test').Page, entryName: string): Promise<void> {
  await page.locator('.sidebar__daily-menu').getByText(entryName, { exact: true }).click();
}
