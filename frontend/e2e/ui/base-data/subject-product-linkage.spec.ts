import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

import { apiGet, apiLogin, type PageResult } from '../../helpers/api';
import { cleanupE2EData, createDbConnection } from '../../helpers/db';
import {
  confirmMessageBox,
  expectNoActiveTableRow,
  expectSelectOptionPresence,
  fillInputByLabel,
  fillNumberByLabel,
  panelByTitle,
  selectByLabel,
  tableRowByText,
  visibleDialog,
} from '../helpers/element-plus';
import { loginAsAdmin } from '../helpers/session';
import {
  createCampusByApi,
  createClassroomByApi,
  createTeacherAccountByApi,
  deleteCampusesByMarker,
  deleteSubjectsByMarker,
} from './base-data-api';

const MARKER = 'E2EUI_BASE';

type ApiRecord = Record<string, any>;

/**
 * 站在真实使用视角，验证「科目」与「课程产品」之间的联动：
 * 1. 新建科目后，创建课程产品时下拉里能选到该科目（含编辑表单 Cancel 重置按钮）；
 * 2. 用新科目创建课程产品，产品正确记录该科目；
 * 3. 删除科目后，不级联影响已引用它的课程产品（产品保留科目名快照），且下拉/API 中均消失；
 * 4. 停用科目后，下拉里选不到（仅显示 ACTIVE），Manage subjects 列表也不再展示。
 */
test.describe.serial('Base Data - Subject & Course Product Linkage E2E UI', () => {
  test.beforeAll(async ({ request }) => {
    await cleanupBaseData(request);
  });

  test.afterAll(async ({ request }) => {
    await cleanupBaseData(request);
  });

  test('删除科目不级联影响已有课程产品，新建科目可被产品选用', async ({ page, request }) => {
    const token = await apiLogin(request);
    const suffix = Date.now().toString(36);
    const campus = await createCampusByApi(request, token, MARKER, `${suffix}_link`);
    const classroom = await createClassroomByApi(request, token, MARKER, `${suffix}_link`, campus.campusName);
    const teacher = await createTeacherAccountByApi(request, token, MARKER, `${suffix}_link`);
    const subjectName = `${MARKER} Linkage Subject ${suffix}`;
    const productName = `${MARKER} Linkage Product ${suffix}`;

    await loginAsAdmin(page);

    // 1. 新建科目，并验证编辑表单的 Cancel 重置按钮
    await createSubjectFromUi(page, subjectName);
    await exerciseSubjectEditCancel(page, subjectName);
    await expectSubjectInApi(request, token, subjectName, 'ACTIVE');

    // 2. 新建科目后，创建课程产品时下拉能选到它
    await expectSubjectSelectableInProductDialog(page, subjectName, true);
    await createPrivateProductWithSubject(page, {
      productName,
      subjectName,
      classroomName: classroom.roomName,
      teacherName: teacher.teacherName,
    });
    await expectProductSubjectVisible(page, productName, subjectName);
    await expectProductSubjectInApi(request, token, productName, subjectName);

    // 3. 删除科目：不级联影响已引用它的产品（产品仍保留科目名快照），下拉/API 均消失
    await deleteSubjectFromUi(page, subjectName);
    await expectSubjectAbsentInApi(request, token, subjectName);
    await expectSubjectSelectableInProductDialog(page, subjectName, false);
    await expectProductSubjectVisible(page, productName, subjectName);
    await expectProductSubjectInApi(request, token, productName, subjectName);
  });

  test('停用科目后下拉与 Manage subjects 列表均不再展示', async ({ page, request }) => {
    const token = await apiLogin(request);
    const suffix = `${Date.now().toString(36)}d`;
    const subjectName = `${MARKER} Deactivate Subject ${suffix}`;

    await loginAsAdmin(page);

    await createSubjectFromUi(page, subjectName);
    await expectSubjectSelectableInProductDialog(page, subjectName, true);

    // 停用科目：列表只展示 ACTIVE，停用后该行从 Manage subjects 中消失
    await updateSubjectStatusFromUi(page, subjectName, 'Inactive');
    await expectSubjectAbsentInManageDialog(page, subjectName);
    await expectSubjectSelectableInProductDialog(page, subjectName, false);
  });
});

async function cleanupBaseData(request: APIRequestContext): Promise<void> {
  const db = await createDbConnection();
  try {
    await cleanupE2EData(db, MARKER);
  } finally {
    await db.end();
  }
  const token = await apiLogin(request);
  await deleteSubjectsByMarker(request, token, MARKER);
  await deleteCampusesByMarker(request, token, MARKER);
}

async function createSubjectFromUi(page: Page, subjectName: string): Promise<void> {
  await page.goto('/base-data/course-products');
  await page.getByRole('button', { name: 'Manage subjects' }).click();
  const dialog = visibleDialog(page, 'Manage subjects');
  await fillInputByLabel(dialog, 'Subject name', subjectName);
  await fillNumberByLabel(dialog, 'Display order', '88');
  await selectByLabel(page, dialog, 'Status', 'Active');
  await fillInputByLabel(dialog, 'Notes', 'Linkage subject note');

  const createResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/subjects') && res.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Add' }).click();
  expect((await createResponse).ok(), '科目新增接口应成功').toBeTruthy();
  await expect(tableRowByText(dialog, subjectName)).toContainText('Linkage subject note');
  await dialog.getByLabel('Close this dialog').click();
}

/**
 * 进入科目编辑态后点击 Cancel，应重置表单（按钮从 Update 退回到 Add），且不发生更新。
 */
async function exerciseSubjectEditCancel(page: Page, subjectName: string): Promise<void> {
  await page.goto('/base-data/course-products');
  await page.getByRole('button', { name: 'Manage subjects' }).click();
  const dialog = visibleDialog(page, 'Manage subjects');
  await tableRowByText(dialog, subjectName).getByRole('button', { name: 'Edit' }).click();
  await expect(dialog.getByText('Edit subject')).toBeVisible();
  await fillInputByLabel(dialog, 'Subject name', `${subjectName} DISCARDED`);
  await dialog.getByRole('button', { name: 'Cancel' }).click();
  // 取消后回到新增态，表单被清空
  await expect(dialog.getByText('Add new subject')).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Add' })).toBeVisible();
  await dialog.getByLabel('Close this dialog').click();
}

async function updateSubjectStatusFromUi(page: Page, subjectName: string, statusLabel: string): Promise<void> {
  await page.goto('/base-data/course-products');
  await page.getByRole('button', { name: 'Manage subjects' }).click();
  const dialog = visibleDialog(page, 'Manage subjects');
  await tableRowByText(dialog, subjectName).getByRole('button', { name: 'Edit' }).click();
  await selectByLabel(page, dialog, 'Status', statusLabel);

  const updateResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/subjects/') && res.request().method() === 'PUT',
  );
  await dialog.getByRole('button', { name: 'Update' }).click();
  expect((await updateResponse).ok(), '科目状态更新接口应成功').toBeTruthy();
  await dialog.getByLabel('Close this dialog').click();
}

async function deleteSubjectFromUi(page: Page, subjectName: string): Promise<void> {
  await page.goto('/base-data/course-products');
  await page.getByRole('button', { name: 'Manage subjects' }).click();
  const dialog = visibleDialog(page, 'Manage subjects');
  await tableRowByText(dialog, subjectName).getByRole('button', { name: 'Delete' }).click();
  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/subjects/') && res.request().method() === 'DELETE',
  );
  await confirmMessageBox(page);
  expect((await response).ok(), '科目删除接口应成功').toBeTruthy();
  await dialog.getByLabel('Close this dialog').click();
}

/**
 * 重新进入页面后打开新建课程产品弹窗，校验科目下拉里是否能选到目标科目。
 * 重新导航是为了让下拉获取到最新的 ACTIVE 科目列表（页面初始化时加载）。
 */
async function expectSubjectSelectableInProductDialog(
  page: Page,
  subjectName: string,
  shouldExist: boolean,
): Promise<void> {
  await page.goto('/base-data/course-products');
  await page.getByRole('button', { name: 'Add course product' }).click();
  const dialog = visibleDialog(page, 'New course product');
  await expectSelectOptionPresence(page, dialog, 'Subject', subjectName, shouldExist);
  await dialog.getByRole('button', { name: 'Cancel' }).click();
}

async function createPrivateProductWithSubject(
  page: Page,
  data: { productName: string; subjectName: string; classroomName: string; teacherName: string },
): Promise<void> {
  await page.goto('/base-data/course-products');
  await page.getByRole('button', { name: 'Add course product' }).click();
  const dialog = visibleDialog(page, 'New course product');
  await fillInputByLabel(dialog, 'Product name', data.productName);
  await selectByLabel(page, dialog, 'Subject', data.subjectName);
  await selectByLabel(page, dialog, 'Teaching format', '1:1 private');
  await fillNumberByLabel(dialog, 'Total hours', '12');
  await selectByLabel(page, dialog, 'Default classroom', data.classroomName);
  await selectByLabel(page, dialog, 'Teacher', data.teacherName);
  await fillNumberByLabel(dialog, 'Original price ($)', '777');
  await selectByLabel(page, dialog, 'Status', 'Active');
  await fillInputByLabel(dialog, 'Notes', 'Linkage product note');

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/course-products') && res.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Save' }).click();
  expect((await response).ok(), '课程产品新增接口应成功').toBeTruthy();
}

async function expectProductSubjectVisible(page: Page, productName: string, subjectName: string): Promise<void> {
  await page.goto('/base-data/course-products');
  await page.getByPlaceholder('Search by name').fill(productName);
  await page.getByRole('tab', { name: '1:1 Private' }).click();
  const row = tableRowByText(panelByTitle(page, 'Course Products'), productName);
  await expect(row).toContainText(subjectName);
}

/**
 * 校验 Manage subjects 列表中不再展示某科目（停用后该科目从 ACTIVE 列表移除）。
 */
async function expectSubjectAbsentInManageDialog(page: Page, subjectName: string): Promise<void> {
  await page.goto('/base-data/course-products');
  await page.getByRole('button', { name: 'Manage subjects' }).click();
  const dialog = visibleDialog(page, 'Manage subjects');
  await expectNoActiveTableRow(dialog, subjectName);
  await dialog.getByLabel('Close this dialog').click();
}

async function expectSubjectInApi(
  request: APIRequestContext,
  token: string,
  subjectName: string,
  status: string,
): Promise<void> {
  const subjects = await apiGet<ApiRecord[]>(request, token, '/v1/base/subjects');
  expect(subjects).toContainEqual(expect.objectContaining({ subjectName, status }));
}

async function expectSubjectAbsentInApi(
  request: APIRequestContext,
  token: string,
  subjectName: string,
): Promise<void> {
  const subjects = await apiGet<ApiRecord[]>(request, token, '/v1/base/subjects');
  expect(subjects.some((item) => item.subjectName === subjectName)).toBe(false);
}

async function expectProductSubjectInApi(
  request: APIRequestContext,
  token: string,
  productName: string,
  subjectName: string,
): Promise<void> {
  const products = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/base/course-products', {
    keyword: productName,
    pageNum: 1,
    pageSize: 20,
  });
  expect(products.records).toContainEqual(expect.objectContaining({ productName, subjectName }));
}
