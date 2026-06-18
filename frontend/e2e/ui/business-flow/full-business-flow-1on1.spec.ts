import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test';
import type { Connection } from 'mysql2/promise';

import { ADMIN_PASSWORD, ADMIN_USERNAME, apiLogin, todayInNewYork } from '../../helpers/api';
import {
  cleanupBusinessFlowE2EData,
  createDbConnection,
  ensureLessonRecordDualConfirmSchema,
  queryAll,
  queryOne,
} from '../../helpers/db';
import {
  confirmMessageBox,
  fillInputByLabel,
  tableRowByText,
  visibleDialog,
} from '../helpers/element-plus';
import { loginAsTeacher, switchUser } from '../helpers/session';
import {
  createClassroomByApi,
  createOneOnOneCourseProductByApi,
  createOneOnOneEnrollmentOrderByApi,
  createStudentByApi,
  createTeacherAccountByApi,
  fetchCoursePackageBalance,
  fetchCoursePackageDetail,
  fetchCoursePackages,
  fetchFirstOccurrenceForPackage,
  fetchHourLedger,
  fetchLessonRecordDetail,
  fetchLessonRecords,
  fetchTeacherPaymentDetail,
  fetchTeacherPayments,
  fetchUnpaidTeacherLessons,
  saveTeacherPayRateByApi,
  type ApiRecord,
  type EntityId,
} from './business-flow-api';

const MARKER = 'E2EUI_FLOW1V1';
const HOURLY_RATE = 60;

test.describe.serial('Business Flow - 1 on 1 full lifecycle E2E UI', () => {
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

  test('1对1课程从报名排课到双确认、课时扣减和教师付款完整闭环', async ({ page, request }) => {
    const token = await apiLogin(request);
    const suffix = Date.now().toString(36);
    const startDate = todayInNewYork();
    const slot = {
      weekday: weekdayForApi(startDate),
      startTime: '10:00',
      endTime: '11:00',
      plannedUnits: 2,
    };

    // 基础数据通过 API 快速准备，关键用户流程仍在 UI 中覆盖。
    const student = await createStudentByApi(request, token, MARKER, suffix);
    const classroom = await createClassroomByApi(request, token, MARKER, suffix);
    const teacher = await createTeacherAccountByApi(request, token, MARKER, suffix);
    const product = await createOneOnOneCourseProductByApi(request, token, MARKER, suffix, {
      defaultTotalUnits: 8,
      defaultLessonUnits: 2,
      listPrice: 800,
    });
    await saveTeacherPayRateByApi(request, token, MARKER, {
      teacherId: teacher.profile.id,
      courseProductId: product.id,
      hourlyRate: HOURLY_RATE,
    });

    await createOneOnOneEnrollmentOrderByApi(request, token, MARKER, {
      studentId: student.id,
      courseProductId: product.id,
      primaryTeacherId: teacher.profile.id,
      defaultClassroomId: classroom.id,
      startDate,
      originalAmount: 800,
      discountAmount: 0,
      totalUnits: 8,
      defaultLessonUnits: 2,
      handledBySuffix: suffix,
      slots: [slot],
    });

    const [coursePackage] = await fetchCoursePackages(request, token, {
      studentId: student.id,
      courseProductId: product.id,
    });
    expect(coursePackage, '报名后应生成 1对1 课包').toBeTruthy();
    expect(coursePackage).toMatchObject({
      teachingMode: 'ONE_ON_ONE',
      totalUnits: 8,
      remainingUnits: 8,
      packageStatus: 'ACTIVE',
    });

    const occurrence = await fetchFirstOccurrenceForPackage(request, token, coursePackage.id);
    expect(occurrence).toMatchObject({
      packageId: coursePackage.id,
      plannedTeacherId: teacher.profile.id,
      plannedClassroomId: classroom.id,
      plannedUnits: 2,
    });

    await loginAsTeacher(page, { username: teacher.username, password: teacher.password });
    await teacherInitialConfirmFromUi(page, {
      studentName: student.studentName,
      packageName: coursePackage.packageName,
      teacherName: teacher.profile.teacherName,
    });

    const pendingRecord = await expectSingleLessonRecord(request, token, coursePackage.id, {
      occurrenceId: occurrence.id,
      confirmStage: 'PENDING_ADMIN',
      resultStatus: 'COMPLETED',
      actualTeacherId: teacher.profile.id,
    });
    await expectLessonRecordInDb(pendingRecord.id, {
      confirmStage: 'PENDING_ADMIN',
      deductedUnits: 2,
      teacherConfirmed: true,
      adminConfirmed: false,
    });
    await expectPendingAdminHasNoSettlement(request, token, {
      packageId: coursePackage.id,
      lessonRecordId: pendingRecord.id,
      teacherId: teacher.profile.id,
    });

    await switchUser(page, { username: ADMIN_USERNAME, password: ADMIN_PASSWORD });
    await adminFinalConfirmFromUi(page, {
      studentName: student.studentName,
      packageName: coursePackage.packageName,
      teacherName: teacher.profile.teacherName,
    });

    const confirmedRecord = await fetchLessonRecordDetail(request, token, pendingRecord.id);
    expect(confirmedRecord).toMatchObject({
      confirmStage: 'CONFIRMED',
      resultStatus: 'COMPLETED',
      deductedUnits: 1,
    });
    const balance = await fetchCoursePackageBalance(request, token, coursePackage.id);
    expect(balance, '课包余额接口返回内部半课时单位，应从 16 扣至 14').toBe(14);
    const packageDetail = await fetchCoursePackageDetail(request, token, coursePackage.id);
    expect(packageDetail.remainingUnits, '课包详情对外展示余额应为 7 小时').toBe(7);

    await expectPostConfirmDbState(coursePackage.id, confirmedRecord.id);
    await expectLedgerAndUnpaidApiState(request, token, {
      packageId: coursePackage.id,
      lessonRecordId: confirmedRecord.id,
      teacherId: teacher.profile.id,
      expectedAmount: HOURLY_RATE,
    });

    await completeTeacherPaymentFromUi(page, {
      teacherName: teacher.profile.teacherName,
      studentName: student.studentName,
      productName: product.productName,
      expectedAmountText: '$60.00',
    });

    await expectPaymentCompleted(request, token, teacher.profile.id, confirmedRecord.id);
  });
});

async function withBusinessFlowDb(action: (db: Connection) => Promise<void>): Promise<void> {
  const db = await createDbConnection();
  try {
    await action(db);
  } finally {
    await db.end();
  }
}

function weekdayForApi(dateText: string): number {
  const day = new Date(`${dateText}T12:00:00Z`).getUTCDay();
  return day === 0 ? 7 : day;
}

async function teacherInitialConfirmFromUi(
  page: Page,
  data: { studentName: string; packageName: string; teacherName: string },
): Promise<void> {
  await openOneOnOnePackage(page, data);
  const row = lessonRow(page, data.teacherName);
  await row.getByRole('button', { name: 'Teacher Confirm' }).click();
  const dialog = visibleDialog(page, 'Confirm Lesson');

  // 老师初确认必须经 UI 提交，后续管理员终确认才会实际扣减课时。
  await fillInputByLabel(dialog, 'Notes', MARKER);
  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/lessons/records') && res.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Confirm', exact: true }).click();
  expect((await response).ok(), '老师初确认接口应成功').toBeTruthy();
  await expect(dialog).toBeHidden();
  await expect(lessonRow(page, data.teacherName)).toContainText('Pending Admin Review');
}

async function adminFinalConfirmFromUi(
  page: Page,
  data: { studentName: string; packageName: string; teacherName: string },
): Promise<void> {
  await openOneOnOnePackage(page, data);
  const row = lessonRow(page, data.teacherName);
  await row.getByRole('button', { name: 'Admin Confirm' }).click();
  const dialog = visibleDialog(page, 'Confirm Lesson');

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/lessons/records/') &&
    res.url().includes('/admin-confirm') &&
    res.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Confirm', exact: true }).click();
  expect((await response).ok(), '管理员终确认接口应成功').toBeTruthy();
  await expect(dialog).toBeHidden();
  await expect(lessonRow(page, data.teacherName)).toContainText('Completed');
}

async function openOneOnOnePackage(
  page: Page,
  data: { studentName: string; packageName: string; teacherName: string },
): Promise<void> {
  const studentsResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/students') && res.request().method() === 'GET',
  );
  await page.goto('/schedule/adjustments');
  expect((await studentsResponse).ok(), '学生列表接口应成功').toBeTruthy();
  await page.getByPlaceholder('Search student...').fill(data.studentName);

  const packagesResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/enrollment/packages') && res.request().method() === 'GET',
  );
  await page.locator('.adj-sidebar__item').filter({ hasText: data.studentName }).first().click();
  expect((await packagesResponse).ok(), '课包列表接口应成功').toBeTruthy();

  const occurrencesResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/schedule/occurrences') && res.request().method() === 'GET',
  );
  await page.locator('.pkg-card').filter({ hasText: data.packageName }).first().click();
  expect((await occurrencesResponse).ok(), '课次列表接口应成功').toBeTruthy();
  await expect(lessonRow(page, data.teacherName)).toBeVisible();
}

function lessonRow(page: Page, teacherName: string): Locator {
  return page.locator('.pkg-occ-row').filter({ hasText: teacherName }).first();
}

async function expectSingleLessonRecord(
  request: APIRequestContext,
  token: string,
  packageId: EntityId,
  expected: Record<string, unknown>,
): Promise<ApiRecord> {
  const records = await fetchLessonRecords(request, token, { packageId });
  expect(records, '课包应只有一条已确认中的 lesson_record').toHaveLength(1);
  expect(records[0]).toMatchObject(expected);
  return records[0];
}

async function expectLessonRecordInDb(
  lessonRecordId: EntityId,
  expected: {
    confirmStage: string;
    deductedUnits: number;
    teacherConfirmed: boolean;
    adminConfirmed: boolean;
  },
): Promise<void> {
  await withBusinessFlowDb(async (db) => {
    const row = await queryOne<{
      confirm_stage: string;
      deducted_units: number;
      teacher_confirmed_at: Date | null;
      admin_confirmed_at: Date | null;
    }>(
      db,
      `
        SELECT confirm_stage, deducted_units, teacher_confirmed_at, admin_confirmed_at
        FROM lesson_record
        WHERE id = ?
      `,
      [lessonRecordId],
    );
    expect(row).toBeTruthy();
    expect(row!.confirm_stage).toBe(expected.confirmStage);
    expect(Number(row!.deducted_units)).toBe(expected.deductedUnits);
    expect(Boolean(row!.teacher_confirmed_at)).toBe(expected.teacherConfirmed);
    expect(Boolean(row!.admin_confirmed_at)).toBe(expected.adminConfirmed);
  });
}

async function expectPostConfirmDbState(packageId: EntityId, lessonRecordId: EntityId): Promise<void> {
  await withBusinessFlowDb(async (db) => {
    const packageRow = await queryOne<{ remaining_units: number; consumed_units: number }>(
      db,
      'SELECT remaining_units, consumed_units FROM course_package WHERE id = ?',
      [packageId],
    );
    expect(packageRow).toMatchObject({ remaining_units: 14, consumed_units: 2 });

    const recordRow = await queryOne<{
      confirm_stage: string;
      deducted_units: number;
      admin_confirmed_at: Date | null;
    }>(
      db,
      'SELECT confirm_stage, deducted_units, admin_confirmed_at FROM lesson_record WHERE id = ?',
      [lessonRecordId],
    );
    expect(recordRow).toBeTruthy();
    expect(recordRow!.confirm_stage).toBe('CONFIRMED');
    expect(Number(recordRow!.deducted_units)).toBe(2);
    expect(recordRow!.admin_confirmed_at).toBeTruthy();

    const ledgerRows = await queryAll<{ change_units: number; balance_after_units: number }>(
      db,
      'SELECT change_units, balance_after_units FROM hour_ledger WHERE package_id = ? AND lesson_record_id = ?',
      [packageId, lessonRecordId],
    );
    expect(ledgerRows).toHaveLength(1);
    expect(Number(ledgerRows[0].change_units)).toBe(-2);
    expect(Number(ledgerRows[0].balance_after_units)).toBe(14);
  });
}

async function expectPendingAdminHasNoSettlement(
  request: APIRequestContext,
  token: string,
  data: { packageId: EntityId; lessonRecordId: EntityId; teacherId: EntityId },
): Promise<void> {
  const balance = await fetchCoursePackageBalance(request, token, data.packageId);
  expect(balance, '老师初确认后内部半课时余额应保持 16').toBe(16);
  const packageDetail = await fetchCoursePackageDetail(request, token, data.packageId);
  expect(packageDetail.remainingUnits, '老师初确认后课包展示余额应保持 8 小时').toBe(8);

  const ledger = await fetchHourLedger(request, token, { packageId: data.packageId });
  expect(
    ledger.some((item) => String(item.lessonRecordId) === String(data.lessonRecordId)),
    'PENDING_ADMIN 阶段不应写课时台账',
  ).toBe(false);

  const unpaid = await fetchUnpaidTeacherLessons(request, token, { teacherId: data.teacherId });
  expect(
    unpaid.some((item) => String(item.lessonRecordId) === String(data.lessonRecordId)),
    'PENDING_ADMIN 阶段不应进入老师待支付列表',
  ).toBe(false);
}

async function expectLedgerAndUnpaidApiState(
  request: APIRequestContext,
  token: string,
  data: { packageId: EntityId; lessonRecordId: EntityId; teacherId: EntityId; expectedAmount: number },
): Promise<void> {
  const ledger = await fetchHourLedger(request, token, { packageId: data.packageId });
  expect(ledger.some((item) => String(item.lessonRecordId) === String(data.lessonRecordId))).toBeTruthy();

  const unpaid = await fetchUnpaidTeacherLessons(request, token, { teacherId: data.teacherId });
  const unpaidLesson = unpaid.find((item) => String(item.lessonRecordId) === String(data.lessonRecordId));
  expect(unpaidLesson, '终确认后应出现在教师待支付课次列表').toBeTruthy();
  expect(unpaidLesson).toMatchObject({
    hours: 1,
    hourlyRate: HOURLY_RATE,
    amount: data.expectedAmount,
  });
}

async function completeTeacherPaymentFromUi(
  page: Page,
  data: { teacherName: string; studentName: string; productName: string; expectedAmountText: string },
): Promise<void> {
  const teachersResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/teachers') && res.request().method() === 'GET',
  );
  await page.goto('/finance/payments');
  expect((await teachersResponse).ok(), '老师列表接口应成功').toBeTruthy();
  await page.getByPlaceholder('Search teacher...').fill(data.teacherName);
  const unpaidResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/finance/payments/unpaid-lessons') && res.request().method() === 'GET',
  );
  await page.locator('.tp-sidebar__item').filter({ hasText: data.teacherName }).first().click();
  expect((await unpaidResponse).ok(), '加载待支付课次接口应成功').toBeTruthy();

  const unpaidRow = tableRowByText(page, data.studentName);
  await expect(unpaidRow).toContainText(data.productName);
  await expect(unpaidRow).toContainText(data.expectedAmountText);
  await unpaidRow.locator('.el-checkbox').first().click();
  await expect(page.locator('.tp-summary')).toContainText(data.expectedAmountText);

  // Payments 页面先创建结算批次，再在历史列表中确认付款，确保用户关键流程走 UI。
  const createResponse = page.waitForResponse((res) =>
    res.url().endsWith('/api/v1/finance/payments') && res.request().method() === 'POST',
  );
  const historyAfterCreateResponse = waitForPaymentHistoryResponse(page);
  await page.getByRole('button', { name: 'Pay', exact: true }).click();
  await confirmMessageBox(page);
  expect((await createResponse).ok(), '创建教师付款接口应成功').toBeTruthy();
  expect((await historyAfterCreateResponse).ok(), '创建付款后历史列表应刷新').toBeTruthy();

  const reloadUnpaidResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/finance/payments/unpaid-lessons') && res.request().method() === 'GET',
  );
  await page.getByRole('button', { name: 'Load Unpaid Lessons' }).click();
  expect((await reloadUnpaidResponse).ok(), '重新加载待支付列表接口应成功').toBeTruthy();
  await expect(page.locator('.tp-empty')).toContainText('No unpaid lessons found for this period.');

  const paymentRow = tableRowByText(page, data.expectedAmountText);
  await expect(paymentRow).toContainText('PENDING');
  const confirmResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/finance/payments/') &&
    res.url().includes('/confirm') &&
    res.request().method() === 'POST',
  );
  const historyAfterConfirmResponse = waitForPaymentHistoryResponse(page);
  await paymentRow.getByRole('button', { name: 'Confirm' }).click();
  await confirmMessageBox(page);
  expect((await confirmResponse).ok(), '确认教师付款接口应成功').toBeTruthy();
  expect((await historyAfterConfirmResponse).ok(), '确认付款后历史列表应刷新').toBeTruthy();
  await expect(paymentRow).toContainText('PAID');
}

function waitForPaymentHistoryResponse(page: Page): Promise<import('@playwright/test').Response> {
  return page.waitForResponse((res) =>
    res.url().includes('/api/v1/finance/payments') &&
    !res.url().includes('/unpaid-lessons') &&
    res.request().method() === 'GET',
  );
}

async function expectPaymentCompleted(
  request: APIRequestContext,
  token: string,
  teacherId: EntityId,
  lessonRecordId: EntityId,
): Promise<void> {
  const unpaidAfterPay = await fetchUnpaidTeacherLessons(request, token, { teacherId });
  expect(unpaidAfterPay.some((item) => String(item.lessonRecordId) === String(lessonRecordId))).toBe(false);

  const payments = await fetchTeacherPayments(request, token, { teacherId, status: 'PAID' });
  const paidPayment = payments.find((item) => Number(item.totalAmount) === HOURLY_RATE);
  expect(paidPayment, '付款确认后应存在 PAID 教师结算记录').toBeTruthy();

  const payment = await fetchTeacherPaymentDetail(request, token, paidPayment!.id);
  expect(payment.items?.some((child: ApiRecord) => String(child.lessonRecordId) === String(lessonRecordId))).toBe(true);
  expect(payment, '付款确认后应存在 PAID 教师结算记录').toBeTruthy();
  expect(payment).toMatchObject({
    status: 'PAID',
    totalHours: 1,
    totalAmount: HOURLY_RATE,
  });
}
