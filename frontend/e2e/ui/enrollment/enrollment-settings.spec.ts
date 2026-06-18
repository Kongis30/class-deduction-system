import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test';

import { apiLogin } from '../../helpers/api';
import { cleanupE2EData, createDbConnection } from '../../helpers/db';
import {
  fillInputByLabel,
  fillNumberByLabel,
  panelByTitle,
  selectByLabel,
  tableRowByText,
  visibleDialog,
} from '../helpers/element-plus';
import { loginAsAdmin } from '../helpers/session';
import {
  fetchDiscountPresetsFromApi,
  fetchPaymentMethodsFromApi,
} from './enrollment-api';

const MARKER = 'E2EUI_ENRSET';

type ApiRecord = Record<string, any>;

test.describe.serial('Enrollment Orders - settings dialogs E2E UI', () => {
  test.beforeAll(async () => {
    await cleanupSettingsData();
  });

  test.afterAll(async () => {
    await cleanupSettingsData();
  });

  test('折扣预设覆盖新增、编辑、停用、删除与 API 数据一致性', async ({ page, request }) => {
    const token = await apiLogin(request);
    const suffix = Date.now().toString(36);
    const presetName = `${MARKER} Sibling ${suffix}`;
    const editedPresetName = `${MARKER} Fixed ${suffix}`;

    await loginAsAdmin(page);
    await openDiscountPresetDialog(page);

    // ── 新增百分比折扣预设 ──
    await createDiscountPresetFromUi(page, {
      presetName,
      discountType: 'Percentage (%)',
      discountValue: '12.5',
      sortOrder: '7',
    });
    let row = tableRowByText(visibleDialog(page, 'Discount presets'), presetName);
    await expect(row).toContainText('12.5% off');
    await expect(row).toContainText('ACTIVE');
    let preset = await expectPresetInApi(request, token, presetName, {
      presetName,
      discountType: 'PERCENTAGE',
      discountValue: 12.5,
      sortOrder: 7,
      status: 'ACTIVE',
    });

    // ── 编辑为固定金额并停用，验证表单回填和列表刷新 ──
    await editDiscountPresetFromUi(page, row, {
      presetName: editedPresetName,
      discountType: 'Fixed amount ($)',
      discountValue: '45',
      sortOrder: '3',
      status: 'Inactive',
    });
    row = tableRowByText(visibleDialog(page, 'Discount presets'), editedPresetName);
    await expect(row).toContainText('$45 off');
    await expect(row).toContainText('INACTIVE');
    preset = await expectPresetInApi(request, token, editedPresetName, {
      id: preset.id,
      presetName: editedPresetName,
      discountType: 'FIXED',
      discountValue: 45,
      sortOrder: 3,
      status: 'INACTIVE',
    });

    // ── 删除并确认 API 不再返回该预设 ──
    await deleteRowFromUi(page, row, '/api/v1/base/discount-presets/');
    await expect(visibleDialog(page, 'Discount presets').locator('.el-table__row').filter({ hasText: editedPresetName })).toHaveCount(0);
    expect(await findPresetByName(request, token, editedPresetName), '删除后折扣预设不应再出现在 API 列表中').toBeUndefined();
    expect(preset.id).toBeTruthy();
  });

  test('支付方式覆盖新增、编辑、停用、删除与 API 数据一致性', async ({ page, request }) => {
    const token = await apiLogin(request);
    const suffix = Date.now().toString(36);
    const methodName = `${MARKER} Card ${suffix}`;
    const editedMethodName = `${MARKER} ACH ${suffix}`;
    const expectedCode = `${MARKER}_CARD_${suffix}`.toUpperCase().replace(/[^A-Z0-9_]/g, '_');

    await loginAsAdmin(page);
    await openPaymentMethodDialog(page);

    // ── 新增支付方式：后端应按名称生成 methodCode ──
    await createPaymentMethodFromUi(page, {
      methodName,
      sortOrder: '11',
    });
    let row = tableRowByText(visibleDialog(page, 'Payment methods'), methodName);
    await expect(row).toContainText(expectedCode);
    await expect(row).toContainText('ACTIVE');
    let method = await expectMethodInApi(request, token, methodName, {
      methodCode: expectedCode,
      methodName,
      sortOrder: 11,
      status: 'ACTIVE',
    });

    // ── 编辑名称和状态；methodCode 应保持不变，保证历史支付记录可追溯 ──
    await editPaymentMethodFromUi(page, row, {
      methodName: editedMethodName,
      sortOrder: '2',
      status: 'Inactive',
    });
    row = tableRowByText(visibleDialog(page, 'Payment methods'), editedMethodName);
    await expect(row).toContainText(expectedCode);
    await expect(row).toContainText('INACTIVE');
    method = await expectMethodInApi(request, token, editedMethodName, {
      id: method.id,
      methodCode: expectedCode,
      methodName: editedMethodName,
      sortOrder: 2,
      status: 'INACTIVE',
    });

    // ── 删除并确认列表/API 同步消失 ──
    await deleteRowFromUi(page, row, '/api/v1/enrollment/payment-methods/');
    await expect(visibleDialog(page, 'Payment methods').locator('.el-table__row').filter({ hasText: editedMethodName })).toHaveCount(0);
    expect(await findMethodByName(request, token, editedMethodName), '删除后支付方式不应再出现在 API 列表中').toBeUndefined();
    expect(method.id).toBeTruthy();
  });
});

async function cleanupSettingsData(): Promise<void> {
  const db = await createDbConnection();
  try {
    await cleanupE2EData(db, MARKER);
  } finally {
    await db.end();
  }
}

async function openDiscountPresetDialog(page: Page): Promise<void> {
  await page.goto('/enrollment/orders');
  await panelByTitle(page, 'Enrollment Orders').getByRole('button', { name: 'Discount presets' }).click();
  await expect(visibleDialog(page, 'Discount presets')).toBeVisible();
}

async function openPaymentMethodDialog(page: Page): Promise<void> {
  await page.goto('/enrollment/orders');
  await panelByTitle(page, 'Enrollment Orders').getByRole('button', { name: 'Payment methods' }).click();
  await expect(visibleDialog(page, 'Payment methods')).toBeVisible();
}

async function createDiscountPresetFromUi(
  page: Page,
  data: { presetName: string; discountType: string; discountValue: string; sortOrder: string },
): Promise<void> {
  visibleDialog(page, 'Discount presets').getByRole('button', { name: 'Add preset' }).click();
  const dialog = presetEditDialog(page);
  await fillInputByLabel(dialog, 'Preset name', data.presetName);
  await selectByLabel(page, dialog, 'Discount type', data.discountType);
  await fillNumberByLabel(dialog, 'Value', data.discountValue);
  await fillNumberByLabel(dialog, 'Sort order', data.sortOrder);

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/discount-presets') && res.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Save' }).click();
  expect((await response).ok(), '折扣预设创建接口应成功').toBeTruthy();
}

async function editDiscountPresetFromUi(
  page: Page,
  row: Locator,
  data: { presetName: string; discountType: string; discountValue: string; sortOrder: string; status: string },
): Promise<void> {
  await row.getByRole('button', { name: 'Edit' }).click();
  const dialog = presetEditDialog(page);
  await fillInputByLabel(dialog, 'Preset name', data.presetName);
  await selectByLabel(page, dialog, 'Discount type', data.discountType);
  await fillNumberByLabel(dialog, 'Value', data.discountValue);
  await fillNumberByLabel(dialog, 'Sort order', data.sortOrder);
  await selectByLabel(page, dialog, 'Status', data.status);

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/discount-presets/') && res.request().method() === 'PUT',
  );
  await dialog.getByRole('button', { name: 'Save' }).click();
  expect((await response).ok(), '折扣预设更新接口应成功').toBeTruthy();
}

async function createPaymentMethodFromUi(
  page: Page,
  data: { methodName: string; sortOrder: string },
): Promise<void> {
  visibleDialog(page, 'Payment methods').getByRole('button', { name: 'Add method' }).click();
  const dialog = methodEditDialog(page);
  await fillInputByLabel(dialog, 'Method name', data.methodName);
  await fillNumberByLabel(dialog, 'Sort order', data.sortOrder);

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/enrollment/payment-methods') && res.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Save' }).click();
  expect((await response).ok(), '支付方式创建接口应成功').toBeTruthy();
}

async function editPaymentMethodFromUi(
  page: Page,
  row: Locator,
  data: { methodName: string; sortOrder: string; status: string },
): Promise<void> {
  await row.getByRole('button', { name: 'Edit' }).click();
  const dialog = methodEditDialog(page);
  await fillInputByLabel(dialog, 'Method name', data.methodName);
  await fillNumberByLabel(dialog, 'Sort order', data.sortOrder);
  await selectByLabel(page, dialog, 'Status', data.status);

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/enrollment/payment-methods/') && res.request().method() === 'PUT',
  );
  await dialog.getByRole('button', { name: 'Save' }).click();
  expect((await response).ok(), '支付方式更新接口应成功').toBeTruthy();
}

async function deleteRowFromUi(page: Page, row: Locator, apiPath: string): Promise<void> {
  await row.getByRole('button', { name: 'Delete' }).click();
  const response = page.waitForResponse((res) =>
    res.url().includes(apiPath) && res.request().method() === 'DELETE',
  );
  await page.locator('.el-message-box:visible').getByRole('button', { name: /OK|Confirm/ }).click();
  expect((await response).ok(), '删除接口应成功').toBeTruthy();
}

/** 内层编辑弹窗没有稳定唯一标题，使用字段定位可避免命中外层管理弹窗。 */
function presetEditDialog(page: Page): Locator {
  return page.locator('.el-dialog:visible').filter({ hasText: 'Preset name' }).last();
}

function methodEditDialog(page: Page): Locator {
  return page.locator('.el-dialog:visible').filter({ hasText: 'Method name' }).last();
}

async function expectPresetInApi(
  request: APIRequestContext,
  token: string,
  presetName: string,
  expected: Record<string, unknown>,
): Promise<ApiRecord> {
  const preset = await findPresetByName(request, token, presetName);
  expect(preset, `应能在 API 查到折扣预设 ${presetName}`).toBeTruthy();
  expect(preset).toMatchObject(expected);
  return preset!;
}

async function expectMethodInApi(
  request: APIRequestContext,
  token: string,
  methodName: string,
  expected: Record<string, unknown>,
): Promise<ApiRecord> {
  const method = await findMethodByName(request, token, methodName);
  expect(method, `应能在 API 查到支付方式 ${methodName}`).toBeTruthy();
  expect(method).toMatchObject(expected);
  return method!;
}

async function findPresetByName(
  request: APIRequestContext,
  token: string,
  presetName: string,
): Promise<ApiRecord | undefined> {
  const presets = await fetchDiscountPresetsFromApi(request, token);
  return presets.find((item) => item.presetName === presetName);
}

async function findMethodByName(
  request: APIRequestContext,
  token: string,
  methodName: string,
): Promise<ApiRecord | undefined> {
  const methods = await fetchPaymentMethodsFromApi(request, token);
  return methods.find((item) => item.methodName === methodName);
}
