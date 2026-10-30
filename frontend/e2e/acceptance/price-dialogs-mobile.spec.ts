import { expect, test, type Locator, type TestInfo } from '@playwright/test';

import { ADMIN_PASSWORD, ADMIN_USERNAME } from '../helpers/api';

test.describe('price page mobile dialogs', () => {
  test.use({
    viewport: { width: 390, height: 844 },
    isMobile: true,
    hasTouch: true,
  });

  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByPlaceholder('Enter your username').fill(ADMIN_USERNAME);
    await page.getByPlaceholder('Enter password').fill(ADMIN_PASSWORD);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.locator('.app-shell')).toBeVisible();
  });

  test('project and plan dialogs use compact mobile proportions', async ({ page }, testInfo) => {
    await page.addInitScript(() => {
      localStorage.setItem('jin.course-prices.selected-mode', 'GROUP');
    });
    await page.goto('/admin/config/course-prices');
    await expect(page.locator('.group-model-layout')).toBeVisible();

    const productSelect = page.locator('.mobile-product-switcher .el-select');
    if (await productSelect.isVisible()) {
      const courseInput = productSelect.locator('input');
      if (await courseInput.count()) {
        await expect(courseInput).toHaveAttribute('readonly', '');
      }
      await productSelect.click();
      const courseDropdown = page.locator('.mobile-course-select-popper .el-select-dropdown__wrap');
      await expect(courseDropdown).toHaveCSS('overflow-y', 'auto');
      const maxHeight = await courseDropdown.evaluate(element => getComputedStyle(element).maxHeight);
      expect(maxHeight, 'Course dropdown should have a fixed maximum height').not.toBe('none');
      await page.locator('.el-select-dropdown__item:visible').first().click();
    }
    await expect(page.locator('.group-project-card').first()).toBeVisible();

    await page.locator('.mobile-project-actions').first().getByRole('button', { name: 'Add plan' }).click();
    await assertCompactDialog(page.locator('.group-model-dialog--plan'), testInfo, 'plan');
    await page.locator('.group-model-dialog--plan').getByRole('button', { name: 'Cancel' }).click();

    await page.locator('.group-model-header').getByRole('button', { name: 'Add project' }).click();
    await assertCompactDialog(page.locator('.group-model-dialog--project'), testInfo, 'project');
    await page.locator('.group-model-dialog--project').getByRole('button', { name: 'Cancel' }).click();

    await page.locator('.mobile-project-actions').first().getByRole('button', { name: 'Add class' }).click();
    await assertCompactDialog(page.locator('.group-model-dialog--class'), testInfo, 'class');
    await page.locator('.group-model-dialog--class').getByRole('button', { name: 'Cancel' }).click();

    await page.locator('.mobile-project-actions__more').first().click();
    await page.locator('.el-dropdown-menu__item:visible').filter({ hasText: 'Manage holidays' }).click();
    const holidayDialog = page.locator('.group-model-dialog--holiday');
    await assertCompactDialog(holidayDialog, testInfo, 'holiday');

    for (const width of [320, 360, 430]) {
      await page.setViewportSize({ width, height: 844 });
      const layout = await holidayDialog.evaluate((element: HTMLElement) => ({
        right: element.getBoundingClientRect().right,
        documentWidth: document.documentElement.scrollWidth,
        viewportWidth: document.documentElement.clientWidth,
      }));
      expect(layout.documentWidth, `Holiday dialog should not overflow a ${width}px viewport`).toBeLessThanOrEqual(layout.viewportWidth + 1);
      expect(layout.right, `Holiday dialog should stay inside a ${width}px viewport`).toBeLessThanOrEqual(width);
    }
  });
});

test.describe('price page desktop dialogs', () => {
  test.use({ viewport: { width: 1280, height: 900 } });

  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByPlaceholder('Enter your username').fill(ADMIN_USERNAME);
    await page.getByPlaceholder('Enter password').fill(ADMIN_PASSWORD);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.locator('.app-shell')).toBeVisible();
  });

  test('desktop dialog sizing is not affected by the mobile overrides', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('jin.course-prices.selected-mode', 'GROUP');
    });
    await page.goto('/admin/config/course-prices');
    await expect(page.locator('.group-model-layout')).toBeVisible();
    await page.locator('.group-model-product').first().click();
    await page.locator('.group-model-header').getByRole('button', { name: 'Add teaching project' }).click();

    const dialog = page.locator('.group-model-dialog--project');
    await expect(dialog).toBeVisible();
    const metrics = await dialog.evaluate((element: HTMLElement) => {
      const input = element.querySelector('.el-input__wrapper') as HTMLElement | null;
      const label = element.querySelector('.el-form-item__label') as HTMLElement | null;
      const footerButton = element.querySelector('.el-dialog__footer .el-button') as HTMLElement | null;
      return {
        width: element.getBoundingClientRect().width,
        inputHeight: input?.getBoundingClientRect().height ?? 0,
        labelFontSize: label ? Number.parseFloat(getComputedStyle(label).fontSize) : 0,
        footerButtonHeight: footerButton?.getBoundingClientRect().height ?? 0,
      };
    });

    expect(metrics.width).toBeGreaterThan(400);
    expect(metrics.inputHeight).toBeLessThanOrEqual(34);
    expect(metrics.footerButtonHeight).toBeLessThanOrEqual(34);
    expect(metrics.labelFontSize).toBeGreaterThanOrEqual(13.5);
  });
});

async function assertCompactDialog(dialog: Locator, testInfo: TestInfo, name: string) {
  await expect(dialog).toBeVisible();
  const metrics = await dialog.evaluate((element: HTMLElement) => {
    const dialogRect = element.getBoundingClientRect();
    const input = element.querySelector('.el-input__wrapper') as HTMLElement | null;
    const label = element.querySelector('.el-form-item__label') as HTMLElement | null;
    const footerButton = element.querySelector('.el-dialog__footer .el-button') as HTMLElement | null;
    const footer = element.querySelector('.el-dialog__footer') as HTMLElement | null;
    return {
      width: dialogRect.width,
      right: dialogRect.right,
      inputHeight: input?.getBoundingClientRect().height ?? 0,
      labelFontSize: label ? Number.parseFloat(getComputedStyle(label).fontSize) : 0,
      footerButtonHeight: footerButton?.getBoundingClientRect().height ?? 0,
      footerButtonWidth: footerButton?.getBoundingClientRect().width ?? 0,
      footerJustify: footer ? getComputedStyle(footer).justifyContent : '',
    };
  });

  expect(metrics.width, `${name} dialog should fit the mobile viewport`).toBeLessThanOrEqual(390);
  expect(metrics.right, `${name} dialog should stay inside the viewport`).toBeLessThanOrEqual(390);
  expect(metrics.inputHeight, `${name} inputs should be compact`).toBeLessThanOrEqual(40);
  expect(metrics.labelFontSize, `${name} labels should be compact`).toBeLessThanOrEqual(13.5);
  expect(metrics.footerButtonHeight, `${name} footer buttons should be compact`).toBeLessThanOrEqual(40);
  expect(metrics.footerButtonWidth, `${name} footer buttons should not stretch across the dialog`).toBeLessThanOrEqual(130);
  expect(metrics.footerJustify, `${name} footer should align actions to the right`).toBe('flex-end');

  await dialog.page().waitForTimeout(350);
  await dialog.screenshot({ path: testInfo.outputPath(`${name}-dialog-mobile.png`) });
}
