import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test';

import { apiLogin, nextWeekday, todayInNewYork } from '../../helpers/api';
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
  createOneOnOneOrderByApi,
  createOneOnOneProductByApi,
  createPaymentMethodByApi,
  createStudentByApi,
  createTeacherByApi,
  fetchOrderByNoFromApi,
  fetchPackagesByStudentFromApi,
} from './enrollment-api';

const MARKER = 'E2EUI_ENRORDER';

type ApiRecord = Record<string, any>;

test.describe.serial('Enrollment Orders - 1 on 1 lifecycle E2E UI', () => {
  test.beforeAll(async () => {
    await cleanupEnrollmentData();
  });

  test.afterAll(async () => {
    await cleanupEnrollmentData();
  });

  test('1对1 报名覆盖创建(含周排课/折扣)、详情、支付收据、退款、取消、删除与数据一致性', async ({ page, request }) => {
    const token = await apiLogin(request);
    const suffix = Date.now().toString(36);
    const startDate = nextWeekday(todayInNewYork(), 1); // 周一，确保日历周一列对齐

    // ── 基础数据：教师 + 产品 + 支付方式 + 多个学生（每个场景用独立学生避免行歧义） ──
    const teacher = await createTeacherByApi(request, token, MARKER, suffix);
    const product = await createOneOnOneProductByApi(request, token, MARKER, suffix, { listPrice: 800 });
    const method = await createPaymentMethodByApi(request, token, MARKER, suffix);
    const payStudent = await createStudentByApi(request, token, MARKER, `${suffix}_pay`);
    const cancelStudent = await createStudentByApi(request, token, MARKER, `${suffix}_cancel`);
    const deleteStudent = await createStudentByApi(request, token, MARKER, `${suffix}_delete`);

    await loginAsAdmin(page);

    // ── 1. 从 UI 创建 1对1 报名（带自定义折扣 + 周排课时段） ──
    await createOneOnOneFromUi(page, {
      studentName: payStudent.studentName,
      productName: product.productName,
      subjectName: 'Math',
      teacherName: teacher.teacherName,
      handledBy: `${MARKER} Cashier`,
      startDate,
      discountAmount: '100',
    });

    // UI 校验：列表行金额/状态正确（原价 800、折扣 100、确认、未支付）
    const payRow = await locateOrderRow(page, payStudent.studentName);
    await expect(payRow).toContainText('$800.00');
    await expect(payRow).toContainText('-$100.00');
    await expect(payRow).toContainText('Confirmed');
    await expect(payRow).toContainText('Unpaid');

    // API 校验：订单金额/状态 + 课包已联动生成
    const createdOrder = await expectOrderInApi(request, token, payStudent.studentName, {
      originalAmount: 800,
      discountAmount: 100,
      receivedAmount: 0,
      orderStatus: 'CONFIRMED',
      paymentStatus: 'UNPAID',
    });
    const packages = await fetchPackagesByStudentFromApi(request, token, payStudent.id);
    expect(packages.length, '1对1 报名应生成对应课包').toBeGreaterThanOrEqual(1);

    // ── 2. 查看订单详情：核对订单号、待收金额 ──
    await openOrderAction(page, payRow, 'Detail');
    const detailDialog = visibleDialog(page, 'Enrollment detail');
    await expect(detailDialog).toContainText(createdOrder.enrollmentNo);
    await expect(detailDialog).toContainText(payStudent.studentName);
    await expect(detailDialog).toContainText('$700.00'); // Outstanding = 800 - 100 - 0
    await expect(detailDialog).toContainText('No payment records.');
    await detailDialog.getByRole('button', { name: 'Close', exact: true }).click();

    // ── 3. 记录支付（应付 700 全额、开具收据 → 新窗口） ──
    await payOrderFromUi(page, payStudent.studentName, {
      methodName: method.methodName,
      handledBy: `${MARKER} Cashier`,
      expectReceiptText: createdOrder.enrollmentNo,
    });

    const paidRow = await locateOrderRow(page, payStudent.studentName);
    await expect(paidRow).toContainText('$700.00'); // 已收金额
    await expect(paidRow).toContainText('Paid');
    await expectOrderInApi(request, token, payStudent.studentName, {
      receivedAmount: 700,
      paymentStatus: 'PAID',
    });

    // ── 4. 全额退款 ──
    await refundOrderFromUi(page, payStudent.studentName, {
      methodName: method.methodName,
      handledBy: `${MARKER} Cashier`,
    });
    const refundedRow = await locateOrderRow(page, payStudent.studentName);
    await expect(refundedRow).toContainText('Refunded');
    await expectOrderInApi(request, token, payStudent.studentName, {
      paymentStatus: 'REFUNDED',
    });

    // ── 5. 取消订单（用独立学生的预置订单） ──
    await createOneOnOneOrderByApi(request, token, MARKER, {
      studentId: cancelStudent.id,
      courseProductId: product.id,
      primaryTeacherId: teacher.id,
      startDate,
      originalAmount: 800,
      handledBySuffix: `${suffix}_cancel`,
    });
    await cancelOrderFromUi(page, cancelStudent.studentName);
    const cancelledRow = await locateOrderRow(page, cancelStudent.studentName);
    await expect(cancelledRow).toContainText('Cancelled');
    await expectOrderInApi(request, token, cancelStudent.studentName, {
      orderStatus: 'CANCELLED',
    });

    // ── 6. 删除未支付订单（含 10 秒冷却确认） ──
    await createOneOnOneOrderByApi(request, token, MARKER, {
      studentId: deleteStudent.id,
      courseProductId: product.id,
      primaryTeacherId: teacher.id,
      startDate,
      originalAmount: 800,
      handledBySuffix: `${suffix}_delete`,
    });
    await deleteOrderFromUi(page, deleteStudent.studentName);
    // API 校验：订单已被删除（逻辑删除后列表查不到）
    const deletedOrder = await fetchOrderByNoFromApi(request, token, deleteStudent.studentName);
    expect(deletedOrder, '删除后订单不应再出现在列表中').toBeUndefined();
  });
});

async function cleanupEnrollmentData(): Promise<void> {
  const db = await createDbConnection();
  try {
    await cleanupE2EData(db, MARKER);
  } finally {
    await db.end();
  }
}

/** 通过关键字筛选定位某学生的订单行（1对1 Tab）。 */
async function locateOrderRow(page: Page, studentName: string): Promise<Locator> {
  await page.goto('/enrollment/orders');
  await page.getByPlaceholder('Order no. or student name').fill(studentName);
  // 等待筛选后的列表刷新
  await page.waitForTimeout(500);
  return tableRowByText(panelByTitle(page, 'Enrollment Orders'), studentName);
}

/** 悬停行内 "…" 菜单并点击指定操作项。 */
async function openOrderAction(page: Page, row: Locator, action: string): Promise<void> {
  await row.locator('.order-action-more').hover();
  await page.locator('.el-dropdown-menu__item:visible').filter({ hasText: action }).first().click();
}

async function createOneOnOneFromUi(
  page: Page,
  data: {
    studentName: string;
    productName: string;
    subjectName: string;
    teacherName: string;
    handledBy: string;
    startDate: string;
    discountAmount: string;
  },
): Promise<void> {
  await page.goto('/enrollment/orders');
  await page.getByRole('button', { name: '1 on 1 enrollment' }).click();
  const dialog = visibleDialog(page, 'New enrollment');

  await selectByLabel(page, dialog, 'Student', data.studentName);
  await selectByLabel(page, dialog, 'Course product', `${data.productName} (${data.subjectName})`);
  await selectByLabel(page, dialog, 'Primary teacher', data.teacherName);
  await fillInputByLabel(dialog, 'Handled by', data.handledBy);
  await fillInputByLabel(dialog, 'Start date', data.startDate);

  // 自定义折扣
  await selectByLabel(page, dialog, 'Discount type', 'Custom');
  await fillNumberByLabel(dialog, 'Discount amount', data.discountAmount);

  // 周排课：拖拽选择周一 10:00–11:00
  await selectWeeklySlot(dialog, '10:00', '11:00');

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/enrollment/orders') && res.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Submit' }).click();
  expect((await response).ok(), '1对1 报名创建接口应成功').toBeTruthy();
  await expect(dialog).toBeHidden();
}

async function payOrderFromUi(
  page: Page,
  studentName: string,
  data: { methodName: string; handledBy: string; expectReceiptText: string },
): Promise<void> {
  const row = await locateOrderRow(page, studentName);
  await openOrderAction(page, row, 'Pay');
  const dialog = visibleDialog(page, 'Record Payment');

  // 选择支付渠道（默认金额已等于应付，保持 Balanced）
  await dialog.locator('.payment-split-row .el-select').first().click();
  await page.getByRole('option', { name: data.methodName, exact: true }).click();
  await fillInputByLabel(dialog, 'Handler', data.handledBy);

  // 开具收据会打开新窗口，需捕获 popup 做校验
  const popupPromise = page.waitForEvent('popup');
  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/enrollment/payments') && res.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Record Payment' }).click();
  expect((await response).ok(), '支付接口应成功').toBeTruthy();

  const receipt = await popupPromise;
  await expect(receipt.locator('body')).toContainText('Payment Receipt');
  await expect(receipt.locator('body')).toContainText(data.expectReceiptText);
  await receipt.close();
  await expect(dialog).toBeHidden();
}

async function refundOrderFromUi(
  page: Page,
  studentName: string,
  data: { methodName: string; handledBy: string },
): Promise<void> {
  const row = await locateOrderRow(page, studentName);
  await openOrderAction(page, row, 'Refund');
  const dialog = visibleDialog(page, 'Refund');

  await selectByLabel(page, dialog, 'Refund method', data.methodName);
  await fillInputByLabel(dialog, 'Handled by', data.handledBy);

  const response = page.waitForResponse((res) =>
    res.url().includes('/refund') && res.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Confirm Refund' }).click();
  expect((await response).ok(), '退款接口应成功').toBeTruthy();
  await expect(dialog).toBeHidden();
}

async function cancelOrderFromUi(page: Page, studentName: string): Promise<void> {
  const row = await locateOrderRow(page, studentName);
  await openOrderAction(page, row, 'Cancel');
  const response = page.waitForResponse((res) =>
    res.url().includes('/cancel') && res.request().method() === 'POST',
  );
  await page.locator('.el-message-box:visible').getByRole('button', { name: /OK|Confirm/ }).click();
  expect((await response).ok(), '取消接口应成功').toBeTruthy();
}

async function deleteOrderFromUi(page: Page, studentName: string): Promise<void> {
  const row = await locateOrderRow(page, studentName);
  await openOrderAction(page, row, 'Delete');
  const dialog = visibleDialog(page, 'Delete enrollment order');

  // 删除按钮有 10 秒冷却，需等待文案从 "Confirm in Ns" 变为 "Confirm delete"
  const confirmButton = dialog.getByRole('button', { name: /Confirm delete/ });
  await expect(confirmButton).toBeEnabled({ timeout: 15_000 });

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/enrollment/orders/') && res.request().method() === 'DELETE',
  );
  await confirmButton.click();
  expect((await response).ok(), '删除接口应成功').toBeTruthy();
  await expect(dialog).toBeHidden();
}

/** 在周排课日历中拖拽选中一个时段，并断言已生成对应的 selected slot 标签。 */
async function selectWeeklySlot(dialog: Locator, startLabel: string, endLabel: string): Promise<void> {
  const start = dialog.locator('.wts-row').filter({ hasText: startLabel }).locator('.wts-cell').first();
  const end = dialog.locator('.wts-row').filter({ hasText: endLabel }).locator('.wts-cell').first();
  await start.dragTo(end);
  await expect(dialog.getByText(`Mon ${startLabel}–${endLabel}`)).toBeVisible();
}

/** 拉取订单详情并断言关键字段，返回订单记录供后续断言订单号等。 */
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
