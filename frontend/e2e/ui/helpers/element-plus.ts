import { expect, type Locator, type Page } from '@playwright/test';

export function panelByTitle(page: Page, title: string): Locator {
  return page.locator('section').filter({ hasText: title }).first();
}

export function tableRowByText(scope: Page | Locator, text: string): Locator {
  return scope.locator('.el-table__row:visible').filter({ hasText: text }).first();
}

export async function expectNoActiveTableRow(scope: Page | Locator, text: string): Promise<void> {
  await expect(scope.locator('.el-table__row:visible').filter({ hasText: text })).toHaveCount(0);
}

export async function selectElementPlusOption(page: Page, trigger: Locator, optionName: string): Promise<void> {
  await trigger.click();
  // 多个 el-select 可能渲染出同名选项（如 Start month / End month 共用月份列表），
  // 直接 getByRole('option') 会命中多个触发 strict mode。这里仅在“当前可见的下拉”中定位选项。
  const visibleDropdown = page.locator('.el-select-dropdown:visible').last();
  await visibleDropdown.getByRole('option', { name: optionName, exact: true }).click();
  // 多选下拉选完后仍保持展开，需要主动收起；但单选下拉会自动收起，此时若再按 Escape
  // 会冒泡到外层 dialog 把弹窗一起关掉。等待单选下拉收起后，仅在下拉仍可见时才按 Escape。
  await page.waitForTimeout(200);
  const openDropdown = page.locator('.el-select-dropdown:visible').first();
  if (await openDropdown.isVisible().catch(() => false)) {
    await page.keyboard.press('Escape');
  }
  // 等待下拉彻底关闭：单选下拉关闭存在淡出动画，若不等待，紧接着打开下一个 select
  // （如 Start month → End month）时会出现两个可见下拉，.last() 可能命中正在关闭的旧下拉，
  // 导致其中的同名 option 不可点击而超时。
  await expect(page.locator('.el-select-dropdown:visible')).toHaveCount(0, { timeout: 5_000 }).catch(() => {});
}

export function visibleDialog(page: Page, title?: string): Locator {
  const dialog = page.locator('.el-dialog:visible');
  return title ? dialog.filter({ hasText: title }).first() : dialog.first();
}

export function formItemByLabel(scope: Page | Locator, label: string): Locator {
  // 通过 label 元素精确匹配，避免选中值/占位文本恰好包含某个字段名时（如学生名里含 "Class"）
  // 用整项 hasText 误命中其它表单项。
  const page = 'page' in scope && typeof scope.page === 'function' ? scope.page() : (scope as Page);
  return scope
    .locator('.el-form-item')
    .filter({ has: page.locator('.el-form-item__label', { hasText: label }) })
    .first();
}

export async function fillInputByLabel(scope: Page | Locator, label: string, value: string): Promise<void> {
  const input = formItemByLabel(scope, label).locator('input:visible, textarea:visible').first();
  await input.fill(value);
}

export async function fillNumberByLabel(scope: Page | Locator, label: string, value: string): Promise<void> {
  const input = formItemByLabel(scope, label).locator('input:visible').first();
  await input.fill(value);
}

export async function selectByLabel(page: Page, scope: Page | Locator, label: string, optionName: string): Promise<void> {
  await selectElementPlusOption(page, formItemByLabel(scope, label).locator('.el-select').first(), optionName);
}

/**
 * 断言某个 el-select 下拉中是否存在指定选项（用于校验科目停用/删除后是否还能被选择）。
 */
export async function expectSelectOptionPresence(
  page: Page,
  scope: Page | Locator,
  label: string,
  optionName: string,
  shouldExist: boolean,
): Promise<void> {
  await formItemByLabel(scope, label).locator('.el-select').first().click();
  const option = page.getByRole('option', { name: optionName, exact: true });
  if (shouldExist) {
    await expect(option).toBeVisible();
  } else {
    await expect(option).toHaveCount(0);
  }
  // 关闭下拉，避免影响后续操作
  await page.keyboard.press('Escape');
}

export async function confirmMessageBox(page: Page): Promise<void> {
  const messageBox = page.locator('.el-message-box:visible').first();
  await messageBox.getByRole('button', { name: /OK|Confirm|Delete/ }).click();
}
