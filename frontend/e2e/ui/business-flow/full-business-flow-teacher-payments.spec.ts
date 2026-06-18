import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test';
import type { Connection } from 'mysql2/promise';

import { apiLogin, plusDays, todayInNewYork } from '../../helpers/api';
import {
  cleanupBusinessFlowE2EData,
  createDbConnection,
  ensureLessonRecordDualConfirmSchema,
} from '../../helpers/db';
import {
  confirmMessageBox,
  selectElementPlusOption,
  tableRowByText,
  visibleDialog,
} from '../helpers/element-plus';
import { loginAsAdmin } from '../helpers/session';
import {
  confirmLessonRecordByApi,
  createClassroomByApi,
  createOneOnOneCourseProductByApi,
  createOneOnOneEnrollmentOrderByApi,
  createStudentByApi,
  createTeacherAccountByApi,
  fetchCoursePackages,
  fetchFirstOccurrenceForPackage,
  fetchLessonRecords,
  fetchTeacherPaymentDetail,
  fetchTeacherPayments,
  fetchUnpaidTeacherLessons,
  type ApiRecord,
  type EntityId,
  type TeacherAccountFixture,
} from './business-flow-api';

const MARKER = 'E2EUI_FLOWPAY';

test.describe.serial('Business Flow - teacher payments extended E2E UI', () => {
  test.setTimeout(240_000);

  test.beforeAll(async () => {
    await withBusinessFlowDb(async (db) => {
      await ensureLessonRecordDualConfirmSchema(db);
      await cleanupBusinessFlowE2EData(db, MARKER);
    });
  });

  test.afterAll(async () => {
    await withBusinessFlowDb(async (db) => {
      await ensureLessonRecordDualConfirmSchema(db);
      await cleanupBusinessFlowE2EData(db, MARKER);
    });
  });

  test('E2EUI_FLOWPAY Pay Rate 修改影响后续待支付金额但不反写历史付款', async ({ page, request }) => {
    const token = await apiLogin(request);
    const fixture = await createPaymentFixture(request, token, 'rate');
    const firstLesson = await createConfirmedLesson(request, token, fixture, 'rate-a', {
      slotStart: '09:00',
      slotEnd: '10:00',
      confirmedDate: todayInNewYork(),
    });

    await establishAdminSession(page);
    await setTeacherPayRateFromUi(page, fixture.teacher.profile.teacherName, fixture.product.productName, '50');
    await expectUnpaidLessonAmountFromUi(page, {
      teacherName: fixture.teacher.profile.teacherName,
      studentName: firstLesson.student.studentName,
      productName: fixture.product.productName,
      expectedAmountText: '$50.00',
    });

    await setTeacherPayRateFromUi(page, fixture.teacher.profile.teacherName, fixture.product.productName, '70');
    await expectUnpaidLessonAmountFromUi(page, {
      teacherName: fixture.teacher.profile.teacherName,
      studentName: firstLesson.student.studentName,
      productName: fixture.product.productName,
      expectedAmountText: '$70.00',
    });

    const firstPaymentId = await createPaymentFromUi(page, {
      teacherName: fixture.teacher.profile.teacherName,
      studentName: firstLesson.student.studentName,
      expectedAmountText: '$70.00',
    });
    await tagTeacherPaymentsForCleanup(fixture.teacher.profile.id);

    await setTeacherPayRateFromUi(page, fixture.teacher.profile.teacherName, fixture.product.productName, '90');
    const historicalPayment = await fetchTeacherPaymentDetail(request, token, firstPaymentId);
    expect(historicalPayment).toMatchObject({
      status: 'PENDING',
      totalHours: 1,
      totalAmount: 70,
    });
    expect(historicalPayment.items?.[0]).toMatchObject({
      lessonRecordId: firstLesson.record.id,
      hourlyRate: 70,
      amount: 70,
    });

    const secondLesson = await createConfirmedLesson(request, token, fixture, 'rate-b', {
      slotStart: '10:00',
      slotEnd: '11:00',
      confirmedDate: todayInNewYork(),
    });
    await expectUnpaidLessonAmountFromUi(page, {
      teacherName: fixture.teacher.profile.teacherName,
      studentName: secondLesson.student.studentName,
      productName: fixture.product.productName,
      expectedAmountText: '$90.00',
    });
  });

  test('E2EUI_FLOWPAY Zero Rate Warning 取消阻止付款，继续后创建 0 金额付款', async ({ page, request }) => {
    const token = await apiLogin(request);
    const fixture = await createPaymentFixture(request, token, 'zero');
    const blockedLesson = await createConfirmedLesson(request, token, fixture, 'zero-cancel', {
      slotStart: '11:00',
      slotEnd: '12:00',
      confirmedDate: todayInNewYork(),
    });
    const continuedLesson = await createConfirmedLesson(request, token, fixture, 'zero-continue', {
      slotStart: '12:00',
      slotEnd: '13:00',
      confirmedDate: todayInNewYork(),
    });

    await establishAdminSession(page);
    await openTeacherPayments(page, fixture.teacher.profile.teacherName);

    await attemptZeroRatePaymentAndCancel(page, blockedLesson.student.studentName);
    await expect(fetchTeacherPayments(request, token, { teacherId: fixture.teacher.profile.id })).resolves.toHaveLength(0);
    let unpaid = await fetchUnpaidTeacherLessons(request, token, { teacherId: fixture.teacher.profile.id });
    expect(unpaid.some((item) => String(item.lessonRecordId) === String(blockedLesson.record.id))).toBe(true);

    const paymentId = await createZeroRatePaymentAnyway(page, continuedLesson.student.studentName);
    await tagTeacherPaymentsForCleanup(fixture.teacher.profile.id);
    const payment = await fetchTeacherPaymentDetail(request, token, paymentId);
    expect(payment).toMatchObject({
      status: 'PENDING',
      totalHours: 1,
      totalAmount: 0,
    });
    expect(payment.items?.[0]).toMatchObject({
      lessonRecordId: continuedLesson.record.id,
      hourlyRate: 0,
      amount: 0,
    });

    unpaid = await fetchUnpaidTeacherLessons(request, token, { teacherId: fixture.teacher.profile.id });
    expect(unpaid.some((item) => String(item.lessonRecordId) === String(continuedLesson.record.id))).toBe(false);
  });

  test('E2EUI_FLOWPAY 跨周期按 Period 筛选并分别创建付款', async ({ page, request }) => {
    const token = await apiLogin(request);
    const fixture = await createPaymentFixture(request, token, 'period');
    const currentDate = todayInNewYork();
    const previousDate = plusDays(currentDate, -40);
    const currentLesson = await createConfirmedLesson(request, token, fixture, 'period-current', {
      slotStart: '13:00',
      slotEnd: '14:00',
      confirmedDate: currentDate,
    });
    const previousLesson = await createConfirmedLesson(request, token, fixture, 'period-previous', {
      slotStart: '14:00',
      slotEnd: '15:00',
      confirmedDate: previousDate,
    });

    await establishAdminSession(page);
    await setTeacherPayRateFromUi(page, fixture.teacher.profile.teacherName, fixture.product.productName, '80');
    await openTeacherPayments(page, fixture.teacher.profile.teacherName);
    await expect(tableRowByText(page, currentLesson.student.studentName)).toContainText('$80.00');
    await expect(page.locator('.el-table__row:visible').filter({ hasText: previousLesson.student.studentName })).toHaveCount(0);

    const currentPaymentId = await createPaymentFromVisibleRow(page, currentLesson.student.studentName, '$80.00');
    await tagTeacherPaymentsForCleanup(fixture.teacher.profile.id);
    await reloadUnpaidLessons(page);
    await expect(page.locator('.el-table__row:visible').filter({ hasText: currentLesson.student.studentName })).toHaveCount(0);

    await setPaymentPeriod(page, previousDate, previousDate);
    await reloadUnpaidLessons(page);
    await expect(tableRowByText(page, previousLesson.student.studentName)).toContainText('$80.00');
    await expect(page.locator('.el-table__row:visible').filter({ hasText: currentLesson.student.studentName })).toHaveCount(0);

    const previousPaymentId = await createPaymentFromVisibleRow(page, previousLesson.student.studentName, '$80.00');
    await tagTeacherPaymentsForCleanup(fixture.teacher.profile.id);
    const [currentPayment, previousPayment] = await Promise.all([
      fetchTeacherPaymentDetail(request, token, currentPaymentId),
      fetchTeacherPaymentDetail(request, token, previousPaymentId),
    ]);
    expect(currentPayment).toMatchObject({ periodStart: monthStart(currentDate), periodEnd: monthEnd(currentDate), totalAmount: 80 });
    expect(previousPayment).toMatchObject({ periodStart: previousDate, periodEnd: previousDate, totalAmount: 80 });
  });

  test('E2EUI_FLOWPAY Payment History 筛选、Detail、Confirm 和已付款课次排除', async ({ page, request }) => {
    const token = await apiLogin(request);
    const fixture = await createPaymentFixture(request, token, 'history');
    const pendingLesson = await createConfirmedLesson(request, token, fixture, 'history-pending', {
      slotStart: '15:00',
      slotEnd: '16:00',
      confirmedDate: todayInNewYork(),
    });
    const paidLesson = await createConfirmedLesson(request, token, fixture, 'history-paid', {
      slotStart: '16:00',
      slotEnd: '17:00',
      confirmedDate: todayInNewYork(),
    });

    await establishAdminSession(page);
    await setTeacherPayRateFromUi(page, fixture.teacher.profile.teacherName, fixture.product.productName, '65');
    await openTeacherPayments(page, fixture.teacher.profile.teacherName);
    const pendingPaymentId = await createPaymentFromVisibleRow(page, pendingLesson.student.studentName, '$65.00');
    await tagTeacherPaymentsForCleanup(fixture.teacher.profile.id);
    await reloadUnpaidLessons(page);
    const paidPaymentId = await createPaymentFromVisibleRow(page, paidLesson.student.studentName, '$65.00');
    await tagTeacherPaymentsForCleanup(fixture.teacher.profile.id);
    const pendingPaymentNo = (await fetchTeacherPaymentDetail(request, token, pendingPaymentId)).paymentNo;

    await filterHistoryByStatus(page, 'Pending');
    const pendingRow = paymentHistoryRow(page, pendingPaymentNo).filter({ hasText: 'PENDING' }).first();
    await expect(pendingRow).toContainText('PENDING');
    await expect(paymentHistoryRow(page, 'PAID')).toHaveCount(0);

    await openPaymentDetailFromRow(page, pendingRow);
    const detail = visibleDialog(page, 'Payment Detail');
    await expect(detail).toContainText(fixture.teacher.profile.teacherName);
    await expect(detail).toContainText(fixture.product.productName);
    await expect(detail).toContainText('$65.00');
    await detail.getByRole('button', { name: 'Close', exact: true }).click();
    await expect(detail).toBeHidden();

    await confirmPaymentFromRow(page, pendingRow);
    await filterHistoryByStatus(page, 'Paid');
    await expect(paymentHistoryRow(page, '$65.00').filter({ hasText: 'PAID' }).first()).toBeVisible();

    const confirmedPayment = await fetchTeacherPaymentDetail(request, token, pendingPaymentId);
    expect(confirmedPayment).toMatchObject({ status: 'PAID', totalAmount: 65 });
    const secondPayment = await fetchTeacherPaymentDetail(request, token, paidPaymentId);
    expect(secondPayment).toMatchObject({ status: 'PENDING', totalAmount: 65 });

    await reloadUnpaidLessons(page);
    await expect(page.locator('.el-table__row:visible').filter({ hasText: pendingLesson.student.studentName })).toHaveCount(0);
    await expect(page.locator('.el-table__row:visible').filter({ hasText: paidLesson.student.studentName })).toHaveCount(0);
  });
});

interface PaymentFixture {
  teacher: TeacherAccountFixture;
  product: ApiRecord;
}

interface ConfirmedLessonFixture {
  student: ApiRecord;
  classroom: ApiRecord;
  coursePackage: ApiRecord;
  occurrence: ApiRecord;
  record: ApiRecord;
}

async function withBusinessFlowDb(action: (db: Connection) => Promise<void>): Promise<void> {
  const db = await createDbConnection();
  try {
    await action(db);
  } finally {
    await db.end();
  }
}

async function establishAdminSession(page: Page): Promise<void> {
  await loginAsAdmin(page);
}

async function createPaymentFixture(
  request: APIRequestContext,
  token: string,
  label: string,
): Promise<PaymentFixture> {
  const suffix = `${Date.now().toString(36)}_${label}`;
  const teacher = await createTeacherAccountByApi(request, token, MARKER, suffix);
  const product = await createOneOnOneCourseProductByApi(request, token, MARKER, suffix, {
    defaultTotalUnits: 8,
    defaultLessonUnits: 2,
    listPrice: 800,
  });
  return { teacher, product };
}

async function createConfirmedLesson(
  request: APIRequestContext,
  token: string,
  fixture: PaymentFixture,
  label: string,
  options: { slotStart: string; slotEnd: string; confirmedDate: string },
): Promise<ConfirmedLessonFixture> {
  const suffix = `${Date.now().toString(36)}_${label}`;
  const startDate = todayInNewYork();
  const student = await createStudentByApi(request, token, MARKER, suffix);
  const classroom = await createClassroomByApi(request, token, MARKER, suffix);

  await createOneOnOneEnrollmentOrderByApi(request, token, MARKER, {
    studentId: student.id,
    courseProductId: fixture.product.id,
    primaryTeacherId: fixture.teacher.profile.id,
    defaultClassroomId: classroom.id,
    startDate,
    originalAmount: 800,
    totalUnits: 8,
    defaultLessonUnits: 2,
    handledBySuffix: suffix,
    slots: [{
      weekday: weekdayForApi(startDate),
      startTime: options.slotStart,
      endTime: options.slotEnd,
      plannedUnits: 2,
    }],
  });

  const [coursePackage] = await fetchCoursePackages(request, token, {
    studentId: student.id,
    courseProductId: fixture.product.id,
  });
  expect(coursePackage, '报名后应生成待确认课包').toBeTruthy();
  const occurrence = await fetchFirstOccurrenceForPackage(request, token, coursePackage.id);
  await confirmLessonRecordByApi(request, token, {
    occurrenceId: occurrence.id,
    actualTeacherId: fixture.teacher.profile.id,
    actualClassroomId: classroom.id,
    notes: `${MARKER} ${label}`,
  });

  const [record] = await fetchLessonRecords(request, token, { packageId: coursePackage.id });
  expect(record).toMatchObject({
    confirmStage: 'CONFIRMED',
    resultStatus: 'COMPLETED',
    actualTeacherId: fixture.teacher.profile.id,
  });
  await setLessonConfirmedAt(record.id, options.confirmedDate);
  return { student, classroom, coursePackage, occurrence, record };
}

function weekdayForApi(dateText: string): number {
  const day = new Date(`${dateText}T12:00:00Z`).getUTCDay();
  return day === 0 ? 7 : day;
}

async function setLessonConfirmedAt(lessonRecordId: EntityId, dateText: string): Promise<void> {
  await withBusinessFlowDb(async (db) => {
    await db.execute(
      'UPDATE lesson_record SET confirmed_at = ?, admin_confirmed_at = ?, updated_at = ? WHERE id = ?',
      [`${dateText} 10:00:00.000`, `${dateText} 10:00:00.000`, `${dateText} 10:00:00.000`, lessonRecordId],
    );
  });
}

async function tagTeacherPaymentsForCleanup(teacherId: EntityId): Promise<void> {
  await withBusinessFlowDb(async (db) => {
    await db.execute(
      "UPDATE teacher_payment SET notes = ? WHERE teacher_id = ? AND (notes IS NULL OR notes = '')",
      [MARKER, teacherId],
    );
  });
}

async function setTeacherPayRateFromUi(
  page: Page,
  teacherName: string,
  productName: string,
  rate: string,
): Promise<void> {
  const teachersResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/teachers') && res.request().method() === 'GET',
  );
  await page.goto('/finance/pay-rates');
  expect((await teachersResponse).ok(), '费率页面老师列表接口应成功').toBeTruthy();

  await page.getByPlaceholder('Search teacher...').fill(teacherName);
  const ratesResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/finance/pay-rates') && res.request().method() === 'GET',
  );
  await page.locator('.pr-sidebar__item').filter({ hasText: teacherName }).first().click();
  expect((await ratesResponse).ok(), '加载老师费率接口应成功').toBeTruthy();

  const productRow = tableRowByText(page, productName);
  await productRow.getByRole('button', { name: 'Edit' }).click();
  const dialog = visibleDialog(page, 'Set Hourly Rate');
  await dialog.locator('.el-input-number input:visible').fill(rate);
  const saveResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/finance/pay-rates') && res.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Save' }).click();
  const saveResult = await saveResponse;
  expect(saveResult.ok(), `保存老师费率接口应成功：${await saveResult.text()}`).toBeTruthy();
  await expect(dialog).toBeHidden();
  await expect(productRow).toContainText(`$${Number(rate).toFixed(2)}`);
}

async function openTeacherPayments(page: Page, teacherName: string): Promise<void> {
  const teachersResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/teachers') && res.request().method() === 'GET',
  );
  await page.goto('/finance/payments');
  expect((await teachersResponse).ok(), '付款页面老师列表接口应成功').toBeTruthy();
  await page.getByPlaceholder('Search teacher...').fill(teacherName);

  const unpaidResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/finance/payments/unpaid-lessons') && res.request().method() === 'GET',
  );
  await page.locator('.tp-sidebar__item').filter({ hasText: teacherName }).first().click();
  expect((await unpaidResponse).ok(), '待支付课次接口应成功').toBeTruthy();
}

async function expectUnpaidLessonAmountFromUi(
  page: Page,
  data: { teacherName: string; studentName: string; productName: string; expectedAmountText: string },
): Promise<void> {
  await openTeacherPayments(page, data.teacherName);
  const row = tableRowByText(page, data.studentName);
  await expect(row).toContainText(data.productName);
  await expect(row).toContainText(data.expectedAmountText);
}

async function createPaymentFromUi(
  page: Page,
  data: { teacherName: string; studentName: string; expectedAmountText: string },
): Promise<EntityId> {
  await openTeacherPayments(page, data.teacherName);
  return createPaymentFromVisibleRow(page, data.studentName, data.expectedAmountText);
}

async function createPaymentFromVisibleRow(page: Page, studentName: string, expectedAmountText: string): Promise<EntityId> {
  const row = tableRowByText(page, studentName);
  await expect(row).toContainText(expectedAmountText);
  await row.locator('.el-checkbox').first().click();
  await expect(page.locator('.tp-summary')).toContainText(expectedAmountText);

  const createResponse = page.waitForResponse((res) =>
    res.url().endsWith('/api/v1/finance/payments') && res.request().method() === 'POST',
  );
  const historyResponse = waitForPaymentHistoryResponse(page);
  await page.getByRole('button', { name: 'Pay', exact: true }).click();
  await confirmMessageBox(page);
  const response = await createResponse;
  expect(response.ok(), '创建教师付款接口应成功').toBeTruthy();
  expect((await historyResponse).ok(), '创建付款后历史应刷新').toBeTruthy();
  return parseApiDataId(await response.text());
}

async function attemptZeroRatePaymentAndCancel(page: Page, studentName: string): Promise<void> {
  const row = tableRowByText(page, studentName);
  await expect(row).toContainText('$0.00');
  await row.locator('.el-checkbox').first().click();
  await page.getByRole('button', { name: 'Pay', exact: true }).click();

  const warning = page.locator('.el-message-box:visible').filter({ hasText: 'Zero Rate Warning' }).first();
  await expect(warning).toContainText('Pay anyway?');
  await warning.getByRole('button', { name: 'Cancel' }).click();
  await expect(warning).toBeHidden();
  await row.locator('.el-checkbox').first().click();
  await expect(row).toBeVisible();
}

async function createZeroRatePaymentAnyway(page: Page, studentName: string): Promise<EntityId> {
  const row = tableRowByText(page, studentName);
  await expect(row).toContainText('$0.00');
  await row.locator('.el-checkbox').first().click();
  await page.getByRole('button', { name: 'Pay', exact: true }).click();

  const warning = page.locator('.el-message-box:visible').filter({ hasText: 'Zero Rate Warning' }).first();
  await warning.getByRole('button', { name: 'Pay Anyway' }).click();

  const createResponse = page.waitForResponse((res) =>
    res.url().endsWith('/api/v1/finance/payments') && res.request().method() === 'POST',
  );
  const historyResponse = waitForPaymentHistoryResponse(page);
  await confirmMessageBox(page);
  const response = await createResponse;
  expect(response.ok(), '继续 0 费率付款时创建接口应成功').toBeTruthy();
  expect((await historyResponse).ok(), '0 费率付款后历史应刷新').toBeTruthy();
  return parseApiDataId(await response.text());
}

async function reloadUnpaidLessons(page: Page): Promise<void> {
  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/finance/payments/unpaid-lessons') && res.request().method() === 'GET',
  );
  await page.getByRole('button', { name: 'Load Unpaid Lessons' }).click();
  expect((await response).ok(), '重新加载待支付课次接口应成功').toBeTruthy();
}

async function setPaymentPeriod(page: Page, startDate: string, endDate: string): Promise<void> {
  const inputs = page.locator('.tp-section--filters .el-date-editor input');
  await inputs.nth(0).fill(startDate);
  await inputs.nth(1).fill(endDate);
  await inputs.nth(1).press('Enter');
  await page.keyboard.press('Escape');
}

async function filterHistoryByStatus(page: Page, status: 'Pending' | 'Paid'): Promise<void> {
  const response = waitForPaymentHistoryResponse(page);
  await selectElementPlusOption(page, page.locator('.tp-history-filters .el-select').first(), status);
  expect((await response).ok(), `历史 ${status} 筛选接口应成功`).toBeTruthy();
}

async function openPaymentDetailFromRow(page: Page, row: Locator): Promise<void> {
  const response = page.waitForResponse((res) =>
    /\/api\/v1\/finance\/payments\/[^/?]+$/.test(new URL(res.url()).pathname) &&
    res.request().method() === 'GET',
  );
  await row.getByRole('button', { name: 'Detail' }).click();
  expect((await response).ok(), '付款详情接口应成功').toBeTruthy();
}

async function confirmPaymentFromRow(page: Page, row: Locator): Promise<void> {
  const confirmResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/finance/payments/') &&
    res.url().includes('/confirm') &&
    res.request().method() === 'POST',
  );
  const historyResponse = waitForPaymentHistoryResponse(page);
  await row.getByRole('button', { name: 'Confirm' }).click();
  await confirmMessageBox(page);
  expect((await confirmResponse).ok(), '确认教师付款接口应成功').toBeTruthy();
  expect((await historyResponse).ok(), '确认付款后历史应刷新').toBeTruthy();
}

function paymentHistoryRow(page: Page, text: string): Locator {
  return page.locator('.tp-section--card').filter({ hasText: 'Payment History' }).locator('.el-table__row:visible').filter({ hasText: text });
}

function waitForPaymentHistoryResponse(page: Page): Promise<import('@playwright/test').Response> {
  return page.waitForResponse((res) =>
    res.url().includes('/api/v1/finance/payments') &&
    !res.url().includes('/unpaid-lessons') &&
    res.request().method() === 'GET',
  );
}

function monthStart(dateText: string): string {
  return `${dateText.slice(0, 8)}01`;
}

function monthEnd(dateText: string): string {
  const [year, month] = dateText.split('-').map(Number);
  const lastDay = new Date(year, month, 0).getDate();
  return `${dateText.slice(0, 8)}${String(lastDay).padStart(2, '0')}`;
}

function parseApiDataId(raw: string): EntityId {
  const match = raw.match(/"data"\s*:\s*"?(\d+)"?/);
  expect(match?.[1], raw).toBeTruthy();
  return match![1];
}
