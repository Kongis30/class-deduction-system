import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test';

import { apiDelete, apiGet, apiLogin, nextWeekday, todayInNewYork, type PageResult } from '../../helpers/api';
import { cleanupE2EData, createDbConnection } from '../../helpers/db';
import {
  confirmMessageBox,
  expectNoActiveTableRow,
  fillInputByLabel,
  fillNumberByLabel,
  formItemByLabel,
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

test.describe.serial('Base Data - Course Products and Subjects E2E UI', () => {
  test.beforeAll(async ({ request }) => {
    await cleanupBaseData(request);
  });

  test.afterAll(async ({ request }) => {
    await cleanupBaseData(request);
  });

  test('课程产品页面覆盖科目管理、1v1 产品、Group 产品、编辑、筛选、删除和数据一致性', async ({ page, request }) => {
    const token = await apiLogin(request);
    const suffix = Date.now().toString(36);
    const campus = await createCampusByApi(request, token, MARKER, `${suffix}_product`);
    const classroom = await createClassroomByApi(request, token, MARKER, `${suffix}_product`, campus.campusName);
    const teacher = await createTeacherAccountByApi(request, token, MARKER, `${suffix}_product`);
    const subjectName = `${MARKER} Subject ${suffix}`;
    const editedSubjectName = `${subjectName} Edited`;
    const privateProduct = `${MARKER} Private Product ${suffix}`;
    const editedPrivateProduct = `${privateProduct} Edited`;
    const groupProduct = `${MARKER} Group Product ${suffix}`;
    const editedGroupProduct = `${groupProduct} Edited`;

    await loginAsAdmin(page);

    await createAndEditSubjectFromUi(page, subjectName, editedSubjectName);
    await expectSubjectInApi(request, token, editedSubjectName, 'ACTIVE');

    await createPrivateProductFromUi(page, {
      productName: privateProduct,
      subjectName: editedSubjectName,
      classroomName: classroom.roomName,
      teacherName: teacher.teacherName,
    });
    await expectPrivateProductVisible(page, privateProduct, editedSubjectName, '$888.00', 'Active');
    await expectProductInApi(request, token, privateProduct, {
      subjectName: editedSubjectName,
      teachingMode: 'ONE_ON_ONE',
      defaultTotalUnits: 16,
      listPrice: 888,
      status: 'ACTIVE',
    });

    await editPrivateProductFromUi(page, privateProduct, editedPrivateProduct);
    await expectPrivateProductVisible(page, editedPrivateProduct, editedSubjectName, '$999.00', 'Inactive');
    await expectProductInApi(request, token, editedPrivateProduct, {
      teachingMode: 'ONE_ON_ONE',
      defaultTotalUnits: 20,
      listPrice: 999,
      status: 'INACTIVE',
    });

    await createGroupProductFromUi(page, {
      productName: groupProduct,
      subjectName: editedSubjectName,
      classroomName: classroom.roomName,
      teacherName: teacher.teacherName,
      startDate: nextWeekday(todayInNewYork(), 1),
    });
    await expectGroupProductVisible(page, groupProduct, editedSubjectName, teacher.teacherName, '12 sessions', '$1,200.00', 'Active');
    await expectProductInApi(request, token, groupProduct, {
      subjectName: editedSubjectName,
      teachingMode: 'GROUP',
      billingType: 'PER_SESSION',
      totalSessions: 12,
      listPrice: 1200,
      status: 'ACTIVE',
    });

    await editGroupProductFromUi(page, groupProduct, editedGroupProduct);
    await expectGroupProductVisible(page, editedGroupProduct, editedSubjectName, teacher.teacherName, '16 sessions', '$1,300.00', 'Inactive');
    await expectProductInApi(request, token, editedGroupProduct, {
      teachingMode: 'GROUP',
      totalSessions: 16,
      listPrice: 1300,
      status: 'INACTIVE',
    });

    await filterProductBySubjectAndStatus(page, editedPrivateProduct, editedSubjectName, 'Inactive', '1:1 Private');
    await filterProductBySubjectAndStatus(page, editedGroupProduct, editedSubjectName, 'Inactive', 'Group Class');

    await deleteProductFromUi(page, editedPrivateProduct, '1:1 Private');
    await expectProductDeleted(page, request, token, editedPrivateProduct);
    await deleteProductFromUi(page, editedGroupProduct, 'Group Class');
    await expectProductDeleted(page, request, token, editedGroupProduct);

    await deleteSubjectFromUi(page, editedSubjectName);
    await expectSubjectDeleted(request, token, editedSubjectName);
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

async function createAndEditSubjectFromUi(page: Page, subjectName: string, editedSubjectName: string): Promise<void> {
  await page.goto('/base-data/course-products');
  await page.getByRole('button', { name: 'Manage subjects' }).click();
  const dialog = visibleDialog(page, 'Manage subjects');
  await fillInputByLabel(dialog, 'Subject name', subjectName);
  await fillNumberByLabel(dialog, 'Display order', '88');
  await selectByLabel(page, dialog, 'Status', 'Active');
  await fillInputByLabel(dialog, 'Notes', 'Initial subject note');

  const createResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/subjects') && res.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Add' }).click();
  expect((await createResponse).ok(), '科目新增接口应成功').toBeTruthy();
  await expect(tableRowByText(dialog, subjectName)).toContainText('Initial subject note');

  await tableRowByText(dialog, subjectName).getByRole('button', { name: 'Edit' }).click();
  await fillInputByLabel(dialog, 'Subject name', editedSubjectName);
  await selectByLabel(page, dialog, 'Status', 'Active');
  await fillInputByLabel(dialog, 'Notes', 'Edited subject note');
  const updateResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/subjects/') && res.request().method() === 'PUT',
  );
  await dialog.getByRole('button', { name: 'Update' }).click();
  expect((await updateResponse).ok(), '科目编辑接口应成功').toBeTruthy();
  await expect(tableRowByText(dialog, editedSubjectName)).toContainText('Edited subject note');
  await dialog.getByLabel('Close this dialog').click();
}

async function createPrivateProductFromUi(
  page: Page,
  data: { productName: string; subjectName: string; classroomName: string; teacherName: string },
): Promise<void> {
  await page.goto('/base-data/course-products');
  await page.getByRole('button', { name: 'Add course product' }).click();
  const dialog = visibleDialog(page, 'New course product');
  await fillInputByLabel(dialog, 'Product name', data.productName);
  await selectByLabel(page, dialog, 'Subject', data.subjectName);
  await selectByLabel(page, dialog, 'Teaching format', '1:1 private');
  await fillNumberByLabel(dialog, 'Total hours', '16');
  await selectByLabel(page, dialog, 'Default classroom', data.classroomName);
  await selectByLabel(page, dialog, 'Teacher', data.teacherName);
  await fillNumberByLabel(dialog, 'Original price ($)', '888');
  await selectByLabel(page, dialog, 'Status', 'Active');
  await fillInputByLabel(dialog, 'Notes', 'Private product note');

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/course-products') && res.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Save' }).click();
  expect((await response).ok(), '1v1 课程产品新增接口应成功').toBeTruthy();
}

async function editPrivateProductFromUi(page: Page, productName: string, editedProductName: string): Promise<void> {
  await page.goto('/base-data/course-products');
  await page.getByPlaceholder('Search by name').fill(productName);
  await page.getByRole('tab', { name: '1:1 Private' }).click();
  await tableRowByText(page, productName).getByRole('button', { name: 'Edit' }).click();
  const dialog = visibleDialog(page, 'Edit course product');
  await fillInputByLabel(dialog, 'Product name', editedProductName);
  await fillNumberByLabel(dialog, 'Total hours', '20');
  await fillNumberByLabel(dialog, 'Original price ($)', '999');
  await selectByLabel(page, dialog, 'Status', 'Inactive');
  await fillInputByLabel(dialog, 'Notes', 'Edited private product note');

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/course-products/') && res.request().method() === 'PUT',
  );
  await dialog.getByRole('button', { name: 'Save' }).click();
  expect((await response).ok(), '1v1 课程产品编辑接口应成功').toBeTruthy();
}

async function createGroupProductFromUi(
  page: Page,
  data: { productName: string; subjectName: string; classroomName: string; teacherName: string; startDate: string },
): Promise<void> {
  await page.goto('/base-data/course-products');
  await page.getByRole('button', { name: 'Add course product' }).click();
  const dialog = visibleDialog(page, 'New course product');
  await fillInputByLabel(dialog, 'Product name', data.productName);
  await selectByLabel(page, dialog, 'Subject', data.subjectName);
  await selectByLabel(page, dialog, 'Teaching format', 'Group');
  await fillInputByLabel(dialog, 'Start date', data.startDate);
  await fillNumberByLabel(dialog, 'Total sessions', '12');
  await selectByLabel(page, dialog, 'Default classroom', data.classroomName);
  await selectByLabel(page, dialog, 'Teacher', data.teacherName);
  await selectWeeklySlot(dialog, '10:00', '11:00');
  await fillNumberByLabel(dialog, 'Original price ($)', '1200');
  await selectByLabel(page, dialog, 'Status', 'Active');
  await fillInputByLabel(dialog, 'Notes', 'Group product note');

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/course-products') && res.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Save' }).click();
  expect((await response).ok(), 'Group 课程产品新增接口应成功').toBeTruthy();
}

async function editGroupProductFromUi(page: Page, productName: string, editedProductName: string): Promise<void> {
  await page.goto('/base-data/course-products');
  await page.getByPlaceholder('Search by name').fill(productName);
  await page.getByRole('tab', { name: 'Group Class' }).click();
  await tableRowByText(page, productName).getByRole('button', { name: 'Edit' }).click();
  const dialog = visibleDialog(page, 'Edit course product');
  await fillInputByLabel(dialog, 'Product name', editedProductName);
  await fillNumberByLabel(dialog, 'Total sessions', '16');
  await fillNumberByLabel(dialog, 'Original price ($)', '1300');
  await selectByLabel(page, dialog, 'Status', 'Inactive');
  await fillInputByLabel(dialog, 'Notes', 'Edited group product note');

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/course-products/') && res.request().method() === 'PUT',
  );
  await dialog.getByRole('button', { name: 'Save' }).click();
  expect((await response).ok(), 'Group 课程产品编辑接口应成功').toBeTruthy();
}

async function deleteProductFromUi(page: Page, productName: string, tabName: string): Promise<void> {
  await page.goto('/base-data/course-products');
  await page.getByPlaceholder('Search by name').fill(productName);
  await page.getByRole('tab', { name: tabName }).click();
  await tableRowByText(page, productName).getByRole('button', { name: 'Delete' }).click();
  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/course-products/') && res.request().method() === 'DELETE',
  );
  await confirmMessageBox(page);
  expect((await response).ok(), '课程产品删除接口应成功').toBeTruthy();
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
}

async function selectWeeklySlot(dialog: Locator, startLabel: string, endLabel: string): Promise<void> {
  const start = dialog.locator('.wts-row').filter({ hasText: startLabel }).locator('.wts-cell').first();
  const end = dialog.locator('.wts-row').filter({ hasText: endLabel }).locator('.wts-cell').first();
  await start.dragTo(end);
  await expect(dialog.getByText(`Mon ${startLabel}–${endLabel}`)).toBeVisible();
}

async function expectPrivateProductVisible(
  page: Page,
  productName: string,
  subjectName: string,
  price: string,
  status: string,
): Promise<void> {
  await page.goto('/base-data/course-products');
  await page.getByPlaceholder('Search by name').fill(productName);
  await page.getByRole('tab', { name: '1:1 Private' }).click();
  const row = tableRowByText(panelByTitle(page, 'Course Products'), productName);
  await expect(row).toContainText(subjectName);
  await expect(row).toContainText(price);
  await expect(row).toContainText(status);
}

async function expectGroupProductVisible(
  page: Page,
  productName: string,
  subjectName: string,
  teacherName: string,
  sessions: string,
  price: string,
  status: string,
): Promise<void> {
  await page.goto('/base-data/course-products');
  await page.getByPlaceholder('Search by name').fill(productName);
  await page.getByRole('tab', { name: 'Group Class' }).click();
  const row = tableRowByText(panelByTitle(page, 'Course Products'), productName);
  await expect(row).toContainText(subjectName);
  await expect(row).toContainText(sessions);
  await expect(row).toContainText('Mon 10:00-11:00');
  await expect(row).toContainText(teacherName);
  await expect(row).toContainText(price);
  await expect(row).toContainText(status);
}

async function filterProductBySubjectAndStatus(
  page: Page,
  productName: string,
  subjectName: string,
  statusLabel: string,
  tabName: string,
): Promise<void> {
  await page.goto('/base-data/course-products');
  await page.getByPlaceholder('Search by subject').fill(subjectName);
  await selectByLabel(page, page, 'Status', statusLabel);
  await page.getByRole('tab', { name: tabName }).click();
  const row = tableRowByText(page, productName);
  await expect(row).toContainText(subjectName);
  await expect(row).toContainText(statusLabel);
}

async function expectProductInApi(
  request: APIRequestContext,
  token: string,
  productName: string,
  expected: Record<string, unknown>,
): Promise<void> {
  const products = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/base/course-products', {
    keyword: productName,
    pageNum: 1,
    pageSize: 20,
  });
  expect(products.records).toContainEqual(expect.objectContaining({
    productName,
    ...expected,
  }));
}

async function expectSubjectInApi(
  request: APIRequestContext,
  token: string,
  subjectName: string,
  status: string,
): Promise<void> {
  const subjects = await apiGet<ApiRecord[]>(request, token, '/v1/base/subjects');
  expect(subjects).toContainEqual(expect.objectContaining({
    subjectName,
    status,
    notes: 'Edited subject note',
  }));
}

async function expectProductDeleted(
  page: Page,
  request: APIRequestContext,
  token: string,
  productName: string,
): Promise<void> {
  await page.goto('/base-data/course-products');
  await page.getByPlaceholder('Search by name').fill(productName);
  await expectNoActiveTableRow(page, productName);

  const products = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/base/course-products', {
    keyword: productName,
    pageNum: 1,
    pageSize: 20,
  });
  expect(products.records).toHaveLength(0);
}

async function expectSubjectDeleted(
  request: APIRequestContext,
  token: string,
  subjectName: string,
): Promise<void> {
  const subjects = await apiGet<ApiRecord[]>(request, token, '/v1/base/subjects');
  expect(subjects.some((item) => item.subjectName === subjectName)).toBe(false);
}
