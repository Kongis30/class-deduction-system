import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test';

import { apiLogin, nextWeekday, todayInNewYork } from '../../helpers/api';
import { cleanupE2EData, createDbConnection, ensureLessonRecordDualConfirmSchema } from '../../helpers/db';
import {
  panelByTitle,
  selectByLabel,
  tableRowByText,
  visibleDialog,
} from '../helpers/element-plus';
import { loginAsAdmin } from '../helpers/session';
import {
  createClassroomByApi,
  createOneOnOneOrderByApi,
  createOneOnOneProductByApi,
  createPerSessionClassOrderByApi,
  createPerSessionClassProductByApi,
  createStudentByApi,
  createTeacherByApi,
  fetchCoursePackageFromApi,
  fetchGroupClassProgressFromApi,
  fetchPackagesByStudentFromApi,
} from './enrollment-api';

const MARKER = 'E2EUI_ENRPKG';

type ApiRecord = Record<string, any>;

test.describe.serial('Course Packages E2E UI', () => {
  test.beforeAll(async () => {
    await cleanupPackageData();
  });

  test.afterAll(async () => {
    await cleanupPackageData();
  });

  test('1对1课包覆盖筛选、展开、详情、课时历史、关闭课包与数据一致性', async ({ page, request }) => {
    const token = await apiLogin(request);
    const suffix = Date.now().toString(36);
    const startDate = nextWeekday(todayInNewYork(), 1);

    const teacher = await createTeacherByApi(request, token, MARKER, suffix);
    const product = await createOneOnOneProductByApi(request, token, MARKER, suffix, {
      defaultTotalUnits: 12,
      defaultLessonUnits: 2,
      listPrice: 960,
    });
    const student = await createStudentByApi(request, token, MARKER, suffix);
    await createOneOnOneOrderByApi(request, token, MARKER, {
      studentId: student.id,
      courseProductId: product.id,
      primaryTeacherId: teacher.id,
      startDate,
      originalAmount: 960,
      handledBySuffix: suffix,
    });

    const [pkg] = await fetchPackagesByStudentFromApi(request, token, student.id);
    expect(pkg, '1对1报名后应生成课包').toBeTruthy();
    expect(pkg).toMatchObject({
      studentName: student.studentName,
      courseProductName: product.productName,
      totalUnits: 12,
      remainingUnits: 12,
      packageStatus: 'ACTIVE',
    });

    await loginAsAdmin(page);
    await openPackagesPage(page);

    // ── 顶部筛选：关键字 + 学生 + 状态均应参与聚合列表刷新 ──
    const panel = panelByTitle(page, 'Course Packages');
    await panel.getByPlaceholder('Search by package name or no.').fill(product.productName);
    await page.waitForTimeout(500);
    await selectByLabel(page, panel, 'Student', student.studentName);
    await selectByLabel(page, panel, 'Status', 'Active');

    // ── 展开产品行，校验学生课包进度和行内按钮 ──
    const expandedPanel = await expandOneOnOneProductRow(page, product.productName);
    const packageRow = expandedPanel.locator('.nested-table .el-table__row').filter({ hasText: student.studentName }).first();
    await expect(packageRow).toContainText('12 / 12');
    await expect(packageRow).toContainText('Active');
    await packageRow.getByRole('button', { name: 'Detail' }).click();

    // ── 详情弹窗：Overview + Lesson History + Close Package ──
    const detail = visibleDialog(page, 'Package Detail');
    await expect(detail).toContainText(product.productName);
    await expect(detail).toContainText(student.studentName);
    await expect(detail).toContainText('12');
    await expect(detail).toContainText('$0.00');

    const historyResponse = page.waitForResponse((res) =>
      res.url().includes('/api/v1/lessons/records') && res.request().method() === 'GET',
    );
    await detail.getByRole('tab', { name: /Lesson History/ }).click();
    expect((await historyResponse).ok(), '课时历史接口应成功').toBeTruthy();
    await expect(detail).toContainText('No confirmed lessons yet.');

    const closeResponse = page.waitForResponse((res) =>
      res.url().includes(`/api/v1/enrollment/packages/${pkg.id}/close`) && res.request().method() === 'POST',
    );
    await detail.getByRole('button', { name: 'Close Package' }).click();
    expect((await closeResponse).ok(), '关闭课包接口应成功').toBeTruthy();
    await expect(detail).toBeHidden();

    const closedPackage = await fetchCoursePackageFromApi(request, token, pkg.id);
    expect(closedPackage).toMatchObject({
      id: pkg.id,
      packageStatus: 'CLOSED',
      remainingUnits: 12,
    });

    // ── 关闭后切换状态筛选，列表应能查到 Closed 聚合行 ──
    await openPackagesPage(page);
    await panelByTitle(page, 'Course Packages').getByPlaceholder('Search by package name or no.').fill(product.productName);
    await selectByLabel(page, panelByTitle(page, 'Course Packages'), 'Status', 'Closed');
    const closedRow = tableRowByText(panelByTitle(page, 'Course Packages'), product.productName);
    await expect(closedRow).toContainText('Closed');
  });

  test('班课课包页覆盖 Group Class 聚合、展开学生进度、排序/状态按钮与 API 数据一致性', async ({ page, request }) => {
    const token = await apiLogin(request);
    const suffix = Date.now().toString(36);
    const today = todayInNewYork();

    const classroom = await createClassroomByApi(request, token, MARKER, suffix);
    const teacher = await createTeacherByApi(request, token, MARKER, suffix);
    const product = await createPerSessionClassProductByApi(request, token, MARKER, suffix, {
      classroomId: classroom.id,
      teacherId: teacher.id,
      startDate: today,
    });
    const student = await createStudentByApi(request, token, MARKER, `${suffix}_group`);
    await createPerSessionClassOrderByApi(request, token, MARKER, {
      studentId: student.id,
      classId: product.id,
      sessions: 5,
      handledBySuffix: suffix,
    });

    const groupProgress = await expectGroupProgressInApi(request, token, product.productName, {
      productName: product.productName,
      totalUnits: 10,
      remainingUnits: 10,
      activeStudentCount: 1,
      status: 'ACTIVE',
    });

    await loginAsAdmin(page);
    await openPackagesPage(page);
    const panel = panelByTitle(page, 'Course Packages');
    await page.getByRole('tab', { name: 'Group Class' }).click();
    await panel.getByPlaceholder('Search by package name or no.').fill(product.productName);
    await page.waitForTimeout(500);

    const expandedPanel = await expandGroupClassRow(page, product.productName);
    const studentRow = expandedPanel.locator('.nested-table .el-table__row').filter({ hasText: student.studentName }).first();
    await expect(studentRow).toContainText('5 sessions');
    await expect(studentRow).toContainText('5');
    await expect(studentRow).toContainText('Active');

    // ── 长期使用场景：用户会在展开表头里切换排序和状态过滤，按钮应可正常触发且数据保留 ──
    await expandedPanel.locator('.progress-sort-header').filter({ hasText: 'Remaining' }).click();
    await expandedPanel.locator('.progress-sort-header').filter({ hasText: 'Progress' }).click();
    await expandedPanel.locator('.header-popover-trigger').filter({ hasText: 'Period' }).hover();
    await page.locator('.package-header-popover:visible').getByRole('button', { name: 'Enrollment date Desc' }).click();
    await expandedPanel.locator('.header-popover-trigger').filter({ hasText: 'Status' }).hover();
    await page.locator('.package-header-popover:visible').getByRole('button', { name: 'All statuses' }).click();
    await expect(studentRow).toContainText(student.studentName);

    expect(groupProgress.classId, '班课聚合行应带 classId 供展开学生进度').toBeTruthy();
  });
});

async function cleanupPackageData(): Promise<void> {
  const db = await createDbConnection();
  try {
    await ensureLessonRecordDualConfirmSchema(db);
    await cleanupE2EData(db, MARKER);
  } finally {
    await db.end();
  }
}

async function openPackagesPage(page: Page): Promise<void> {
  await page.goto('/enrollment/packages');
  await expect(panelByTitle(page, 'Course Packages')).toBeVisible();
}

/** 展开 1对1产品聚合行，返回页面 panel 供定位嵌套课包行。 */
async function expandOneOnOneProductRow(page: Page, productName: string): Promise<Locator> {
  const panel = panelByTitle(page, 'Course Packages');
  const productRow = tableRowByText(panel, productName);
  await expect(productRow).toBeVisible();
  await expandRowIfNeeded(productRow);
  await expect(panel.locator('.nested-table .el-table__row').first()).toBeVisible();
  return panel;
}

/** 展开班课聚合行，等待学生进度表加载完成。 */
async function expandGroupClassRow(page: Page, productName: string): Promise<Locator> {
  const panel = panelByTitle(page, 'Course Packages');
  const productRow = tableRowByText(panel, productName);
  await expect(productRow).toBeVisible();
  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/enrollment/packages/group-classes/') && res.url().includes('/students'),
  );
  await expandRowIfNeeded(productRow);
  expect((await response).ok(), '班课学生进度接口应成功').toBeTruthy();
  await expect(panel.locator('.nested-table .el-table__row').first()).toBeVisible();
  return panel;
}

async function expandRowIfNeeded(row: Locator): Promise<void> {
  const expandIcon = row.locator('.el-table__expand-icon');
  const expanded = await expandIcon
    .evaluate((el) => el.classList.contains('el-table__expand-icon--expanded'))
    .catch(() => false);
  if (!expanded) {
    await expandIcon.click();
  }
}

async function expectGroupProgressInApi(
  request: APIRequestContext,
  token: string,
  productName: string,
  expected: Record<string, unknown>,
): Promise<ApiRecord> {
  const progress = await fetchGroupClassProgressFromApi(request, token, productName);
  expect(progress, `应能在 API 查到班课聚合 ${productName}`).toBeTruthy();
  expect(progress).toMatchObject(expected);
  return progress!;
}
