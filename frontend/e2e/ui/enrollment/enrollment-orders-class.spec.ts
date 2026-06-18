import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test';

import { apiLogin, todayInNewYork } from '../../helpers/api';
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
  createClassroomByApi,
  createMonthlyClassProductByApi,
  createPerSessionClassProductByApi,
  createStudentByApi,
  createTeacherByApi,
  fetchOrderByNoFromApi,
} from './enrollment-api';

const MARKER = 'E2EUI_ENRCLASS';

type ApiRecord = Record<string, any>;

test.describe.serial('Enrollment Orders - Group class lifecycle E2E UI', () => {
  test.beforeAll(async () => {
    await cleanupClassData();
  });

  test.afterAll(async () => {
    await cleanupClassData();
  });

  test('按次计费班课报名覆盖按比例计价、可展开行校验、取消与数据一致性', async ({ page, request }) => {
    const token = await apiLogin(request);
    const suffix = Date.now().toString(36);
    const today = todayInNewYork();

    // ── 基础数据：教室 + 教师 + 按次计费班课产品（listPrice=1000，totalSessions=10）──
    const classroom = await createClassroomByApi(request, token, MARKER, suffix);
    const teacher = await createTeacherByApi(request, token, MARKER, suffix);
    const product = await createPerSessionClassProductByApi(request, token, MARKER, suffix, {
      classroomId: classroom.id,
      teacherId: teacher.id,
      startDate: today,
    });
    const enrollStudent = await createStudentByApi(request, token, MARKER, `${suffix}_ps`);
    const cancelStudent = await createStudentByApi(request, token, MARKER, `${suffix}_pscancel`);

    await loginAsAdmin(page);

    // ── 1. 从 UI 班课报名：购买 4 次（按比例计价 = 1000 × 4 / 10 = $400.00）──
    await createClassEnrollFromUi(page, {
      studentName: enrollStudent.studentName,
      className: product.productName,
      billing: 'PER_SESSION',
      sessions: 4,
      expectProratedAmount: '$400.00',
      handledBy: `${MARKER} Cashier`,
    });

    // UI 校验：Group Class Tab 展开后能看到该学生订单（已收 $400、Paid）
    await expectClassOrderInExpandedRow(page, product.productName, enrollStudent.studentName, {
      received: '$400.00',
      payment: 'Paid',
    });

    // API 校验：班课订单金额 / 支付状态正确
    await expectOrderInApi(request, token, enrollStudent.studentName, {
      originalAmount: 400,
      discountAmount: 0,
      receivedAmount: 400,
      paymentStatus: 'PAID',
    });

    // ── 2. 再报一名学生用于取消流程 ──
    await createClassEnrollFromUi(page, {
      studentName: cancelStudent.studentName,
      className: product.productName,
      billing: 'PER_SESSION',
      sessions: 2,
      expectProratedAmount: '$200.00',
      handledBy: `${MARKER} Cashier`,
    });

    // ── 3. 在可展开行内取消该学生的班课订单 ──
    await cancelClassOrderInExpandedRow(page, product.productName, cancelStudent.studentName);
    await expectOrderInApi(request, token, cancelStudent.studentName, {
      orderStatus: 'CANCELLED',
    });
  });

  test('按月计费班课报名覆盖月份区间选择与按比例计价、数据一致性', async ({ page, request }) => {
    const token = await apiLogin(request);
    const suffix = Date.now().toString(36);
    const today = todayInNewYork();
    const startMonth = today.slice(0, 7);
    const endMonth = monthOffset(startMonth, 3); // 共 4 个月（含首尾）

    // ── 基础数据：按月计费班课产品（listPrice=1200，totalMonths=4）──
    const classroom = await createClassroomByApi(request, token, MARKER, `${suffix}_m`);
    const teacher = await createTeacherByApi(request, token, MARKER, `${suffix}_m`);
    const product = await createMonthlyClassProductByApi(request, token, MARKER, suffix, {
      classroomId: classroom.id,
      teacherId: teacher.id,
      startMonth,
      endMonth,
    });
    const monthlyStudent = await createStudentByApi(request, token, MARKER, `${suffix}_mo`);

    await loginAsAdmin(page);

    // ── 报名 2 个月（按比例计价 = 1200 × 2 / 4 = $600.00）──
    await createClassEnrollFromUi(page, {
      studentName: monthlyStudent.studentName,
      className: product.productName,
      billing: 'MONTHLY',
      startMonth,
      endMonth: monthOffset(startMonth, 1),
      expectProratedAmount: '$600.00',
      handledBy: `${MARKER} Cashier`,
    });

    await expectClassOrderInExpandedRow(page, product.productName, monthlyStudent.studentName, {
      received: '$600.00',
      payment: 'Paid',
    });
    await expectOrderInApi(request, token, monthlyStudent.studentName, {
      originalAmount: 600,
      receivedAmount: 600,
      paymentStatus: 'PAID',
    });
  });
});

async function cleanupClassData(): Promise<void> {
  const db = await createDbConnection();
  try {
    await cleanupE2EData(db, MARKER);
  } finally {
    await db.end();
  }
}

/** 在 'YYYY-MM' 的基础上加上若干个月。 */
function monthOffset(month: string, offset: number): string {
  const [year, mon] = month.split('-').map(Number);
  const date = new Date(Date.UTC(year, mon - 1 + offset, 1));
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}`;
}

/** 从 UI 走完整班课报名流程（按次 / 按月两种计费）。 */
async function createClassEnrollFromUi(
  page: Page,
  data: {
    studentName: string;
    className: string;
    billing: 'PER_SESSION' | 'MONTHLY';
    sessions?: number;
    startMonth?: string;
    endMonth?: string;
    expectProratedAmount: string;
    handledBy: string;
  },
): Promise<void> {
  await page.goto('/enrollment/orders');
  await page.getByRole('button', { name: 'group class enrollment' }).click();
  const dialog = visibleDialog(page, 'New class enrollment');

  await selectByLabel(page, dialog, 'Student', data.studentName);
  await selectByLabel(page, dialog, 'Class', data.className);

  if (data.billing === 'PER_SESSION') {
    await fillNumberByLabel(dialog, 'Sessions to purchase', String(data.sessions));
  } else {
    await selectByLabel(page, dialog, 'Start month', data.startMonth!);
    await selectByLabel(page, dialog, 'End month', data.endMonth!);
  }

  // 校验前端按比例计价预览（与后端公式一致）
  await expect(dialog).toContainText(data.expectProratedAmount);
  await fillInputByLabel(dialog, 'Handled by', data.handledBy);

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/enrollment/orders/class-enroll') && res.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Submit' }).click();
  expect((await response).ok(), '班课报名创建接口应成功').toBeTruthy();
  await expect(dialog).toBeHidden();
}

/** 切到 Group Class Tab，展开指定班级行，断言其中包含某学生的订单与金额/状态。 */
async function expectClassOrderInExpandedRow(
  page: Page,
  className: string,
  studentName: string,
  expected: { received: string; payment: string },
): Promise<void> {
  const panel = await openGroupClassRow(page, className);
  // 展开内容渲染在班级行的兄弟行（.el-table__expanded-cell），嵌套订单行只在该处出现学生名
  const orderRow = panel.locator('.nested-table .el-table__row').filter({ hasText: studentName }).first();
  await expect(orderRow).toContainText(expected.received);
  await expect(orderRow).toContainText(expected.payment);
}

/** 在可展开行内取消某学生的班课订单，并断言其从激活列表中消失。 */
async function cancelClassOrderInExpandedRow(page: Page, className: string, studentName: string): Promise<void> {
  const panel = await openGroupClassRow(page, className);
  const orderRow = panel.locator('.nested-table .el-table__row').filter({ hasText: studentName }).first();
  await orderRow.locator('.order-action-more').hover();
  await page.locator('.el-dropdown-menu__item:visible').filter({ hasText: 'Cancel' }).first().click();

  const response = page.waitForResponse((res) =>
    res.url().includes('/cancel') && res.request().method() === 'POST',
  );
  await page.locator('.el-message-box:visible').getByRole('button', { name: /OK|Confirm/ }).click();
  expect((await response).ok(), '班课订单取消接口应成功').toBeTruthy();

  // 取消后该订单会从展开的激活订单列表中移除
  const refreshedPanel = await openGroupClassRow(page, className);
  await expect(refreshedPanel.locator('.nested-table .el-table__row').filter({ hasText: studentName })).toHaveCount(0);
}

/** 切到 Group Class Tab、按班级名定位行并展开（返回所在 panel locator，可在其内定位嵌套订单）。 */
async function openGroupClassRow(page: Page, className: string): Promise<Locator> {
  await page.goto('/enrollment/orders');
  const panel = panelByTitle(page, 'Enrollment Orders');
  await page.getByRole('tab', { name: 'Group Class' }).click();
  const classRow = tableRowByText(panel, className);
  await expect(classRow).toBeVisible();
  // 展开行加载嵌套订单（已展开则点击会收起，因此仅在折叠态点击）
  const expandIcon = classRow.locator('.el-table__expand-icon');
  const expanded = await expandIcon
    .evaluate((el) => el.classList.contains('el-table__expand-icon--expanded'))
    .catch(() => false);
  if (!expanded) {
    await expandIcon.click();
  }
  // 等待嵌套订单表渲染完成
  await page.waitForTimeout(600);
  return panel;
}

/** 拉取订单并断言关键字段。 */
async function expectOrderInApi(
  request: APIRequestContext,
  token: string,
  studentName: string,
  expected: Record<string, unknown>,
): Promise<ApiRecord> {
  const order = await fetchOrderByNoFromApi(request, token, studentName);
  expect(order, `应能在 API 查到 ${studentName} 的订单`).toBeTruthy();
  expect(order).toMatchObject(expected);
  return order!;
}
