import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test';
import type { Connection } from 'mysql2/promise';

import { API_BASE_URL, ADMIN_PASSWORD, ADMIN_USERNAME, apiLogin, todayInNewYork } from '../../helpers/api';
import {
  cleanupBusinessFlowE2EData,
  createDbConnection,
  ensureLessonRecordDualConfirmSchema,
  queryAll,
  queryOne,
} from '../../helpers/db';
import {
  fillInputByLabel,
  selectByLabel,
  tableRowByText,
  visibleDialog,
} from '../helpers/element-plus';
import { loginAsAdmin, loginAsTeacher, switchUser } from '../helpers/session';
import {
  createClassroomByApi,
  createOneOnOneCourseProductByApi,
  createOneOnOneEnrollmentOrderByApi,
  createStudentByApi,
  createTeacherAccountByApi,
  createTeacherPaymentByApi,
  fetchCoursePackageBalance,
  fetchCoursePackageDetail,
  fetchCoursePackages,
  fetchFirstOccurrenceForPackage,
  fetchHourLedger,
  fetchLessonRecordDetail,
  fetchLessonRecords,
  fetchUnpaidTeacherLessons,
  saveTeacherPayRateByApi,
  type ApiRecord,
  type EntityId,
  type LessonConfirmPayload,
} from './business-flow-api';

const MARKER = 'E2EUI_FLOWEDGE';
const HOURLY_RATE = 60;

test.describe.serial('Business Flow - edge cases E2E UI', () => {
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

  test('E2EUI_FLOWEDGE Formal Leave 不扣课包且不进入老师待结算', async ({ page, request }) => {
    const token = await apiLogin(request);
    const fixture = await createOneOnOneFlowFixture(request, token, 'leave', {
      totalUnits: 8,
      lessonUnits: 2,
      slotStart: '00:00',
      slotEnd: '01:00',
    });

    await loginAsTeacher(page, { username: fixture.teacher.username, password: fixture.teacher.password });
    await teacherInitialConfirmFromUi(page, fixture, {
      resultStatus: 'Formal Leave',
      notes: `${MARKER} formal leave`,
    });

    const pendingRecord = await expectSingleLessonRecord(request, token, fixture.coursePackage.id, {
      confirmStage: 'PENDING_ADMIN',
      resultStatus: 'FORMAL_LEAVE',
      deductedUnits: 0,
    });

    await switchUser(page, { username: ADMIN_USERNAME, password: ADMIN_PASSWORD });
    await adminFinalConfirmFromUi(page, fixture);

    const record = await fetchLessonRecordDetail(request, token, pendingRecord.id);
    expect(record).toMatchObject({
      confirmStage: 'CONFIRMED',
      resultStatus: 'FORMAL_LEAVE',
      deductedUnits: 0,
    });

    const balance = await fetchCoursePackageBalance(request, token, fixture.coursePackage.id);
    expect(balance, 'Formal Leave 不应扣减内部半课时余额').toBe(16);
    const packageDetail = await fetchCoursePackageDetail(request, token, fixture.coursePackage.id);
    expect(packageDetail.remainingUnits, '课包对外余额应保持 8 小时').toBe(8);

    await withBusinessFlowDb(async (db) => {
      const packageRow = await queryOne<{ remaining_units: number; consumed_units: number }>(
        db,
        'SELECT remaining_units, consumed_units FROM course_package WHERE id = ?',
        [fixture.coursePackage.id],
      );
      expect(packageRow).toMatchObject({ remaining_units: 16, consumed_units: 0 });

      const ledgerRows = await queryAll<{ ledger_type: string; change_units: number; balance_after_units: number; reason_code: string }>(
        db,
        'SELECT ledger_type, change_units, balance_after_units, reason_code FROM hour_ledger WHERE package_id = ? AND lesson_record_id = ?',
        [fixture.coursePackage.id, record.id],
      );
      expect(ledgerRows).toHaveLength(1);
      expect(ledgerRows[0]).toMatchObject({
        ledger_type: 'NO_DEDUCT_AUDIT',
        change_units: 0,
        balance_after_units: 16,
        reason_code: 'FORMAL_LEAVE',
      });
    });

    // 当前工资模块仅结算实际完成类状态；Formal Leave 只保留核销审计和补课，不产生待结算工资。
    const unpaid = await fetchUnpaidTeacherLessons(request, token, { teacherId: fixture.teacher.profile.id });
    expect(unpaid.some((item) => String(item.lessonRecordId) === String(record.id))).toBe(false);
  });

  test('E2EUI_FLOWEDGE No Show 扣课写台账但不进入老师待结算', async ({ page, request }) => {
    const token = await apiLogin(request);
    const fixture = await createOneOnOneFlowFixture(request, token, 'noshow', {
      totalUnits: 8,
      lessonUnits: 2,
      slotStart: '02:00',
      slotEnd: '03:00',
    });

    await loginAsAdmin(page);
    await adminDirectConfirmFromUi(page, fixture, {
      resultStatus: 'No Show',
      notes: `${MARKER} no show`,
    });

    const [record] = await fetchLessonRecords(request, token, { packageId: fixture.coursePackage.id });
    expect(record).toMatchObject({
      confirmStage: 'CONFIRMED',
      resultStatus: 'NO_SHOW',
      deductedUnits: 1,
    });

    const balance = await fetchCoursePackageBalance(request, token, fixture.coursePackage.id);
    expect(balance, 'No Show 应按计划课时扣减内部半课时余额').toBe(14);
    const packageDetail = await fetchCoursePackageDetail(request, token, fixture.coursePackage.id);
    expect(packageDetail.remainingUnits, 'No Show 后课包对外余额应为 7 小时').toBe(7);

    await withBusinessFlowDb(async (db) => {
      const packageRow = await queryOne<{ remaining_units: number; consumed_units: number }>(
        db,
        'SELECT remaining_units, consumed_units FROM course_package WHERE id = ?',
        [fixture.coursePackage.id],
      );
      expect(packageRow).toMatchObject({ remaining_units: 14, consumed_units: 2 });

      const ledgerRows = await queryAll<{ ledger_type: string; change_units: number; balance_after_units: number; reason_code: string }>(
        db,
        'SELECT ledger_type, change_units, balance_after_units, reason_code FROM hour_ledger WHERE package_id = ? AND lesson_record_id = ?',
        [fixture.coursePackage.id, record.id],
      );
      expect(ledgerRows).toHaveLength(1);
      expect(ledgerRows[0]).toMatchObject({
        ledger_type: 'DEDUCT',
        change_units: -2,
        balance_after_units: 14,
        reason_code: 'NO_SHOW',
      });
    });

    // No Show 对学生扣课，但工资模块只结算实际授课完成类状态。
    const unpaid = await fetchUnpaidTeacherLessons(request, token, { teacherId: fixture.teacher.profile.id });
    expect(unpaid.some((item) => String(item.lessonRecordId) === String(record.id))).toBe(false);
  });

  test('E2EUI_FLOWEDGE 管理员驳回老师初确认后可重新处理且不扣课不计薪', async ({ page, request }) => {
    const token = await apiLogin(request);
    const fixture = await createOneOnOneFlowFixture(request, token, 'reject', {
      totalUnits: 8,
      lessonUnits: 2,
      slotStart: '00:00',
      slotEnd: '01:00',
    });
    const rejectReason = `${MARKER} actual time needs correction`;

    await loginAsTeacher(page, { username: fixture.teacher.username, password: fixture.teacher.password });
    await teacherInitialConfirmFromUi(page, fixture, {
      resultStatus: 'Completed',
      notes: `${MARKER} pending reject`,
    });

    const pendingRecord = await expectSingleLessonRecord(request, token, fixture.coursePackage.id, {
      confirmStage: 'PENDING_ADMIN',
      resultStatus: 'COMPLETED',
    });

    await switchUser(page, { username: ADMIN_USERNAME, password: ADMIN_PASSWORD });
    await adminRejectFromUi(page, fixture, rejectReason);

    const rejectedRecord = await fetchLessonRecordDetail(request, token, pendingRecord.id);
    expect(rejectedRecord).toMatchObject({
      confirmStage: 'REJECTED',
      rejectReason,
      resultStatus: 'COMPLETED',
    });

    await expectNoDeductionAndNoPayroll(request, token, {
      packageId: fixture.coursePackage.id,
      teacherId: fixture.teacher.profile.id,
      lessonRecordId: pendingRecord.id,
      expectedBalanceUnits: 16,
    });

    // 驳回记录会被列表合并逻辑过滤，课次应回到可重新确认状态。
    test.fail(true, '已知缺陷：管理员驳回后排课页仍显示 Completed 且缺少重新确认入口。');
    await openOneOnOnePackage(page, fixture);
    const row = lessonRow(page, fixture.teacher.profile.teacherName);
    await expect(row).toContainText('Pending');
    await expect(row.getByRole('button', { name: /Confirm|Teacher Confirm/ })).toBeVisible();
  });

  test('E2EUI_FLOWEDGE 重复确认被拒绝且课包、流水、待结算不重复', async ({ page, request }) => {
    const token = await apiLogin(request);
    const fixture = await createOneOnOneFlowFixture(request, token, 'dupconfirm', {
      totalUnits: 8,
      lessonUnits: 2,
      slotStart: '13:00',
      slotEnd: '14:00',
    });

    await loginAsAdmin(page);
    await adminDirectConfirmFromUi(page, fixture, {
      resultStatus: 'Completed',
      notes: `${MARKER} first confirm`,
    });

    const [confirmedRecord] = await fetchLessonRecords(request, token, { packageId: fixture.coursePackage.id });
    expect(confirmedRecord).toMatchObject({
      confirmStage: 'CONFIRMED',
      resultStatus: 'COMPLETED',
    });
    const snapshot = await readPackageSettlementSnapshot(fixture.coursePackage.id, confirmedRecord.id);

    // UI 先证明用户路径上已无二次确认入口，再用 API 触发非法重复确认分支。
    await openOneOnOnePackage(page, fixture);
    const row = lessonRow(page, fixture.teacher.profile.teacherName);
    await expect(row).toContainText('Completed');
    await expect(row.getByRole('button', { name: /Confirm|Teacher Confirm|Admin Confirm/ })).toHaveCount(0);
    await expect(row.getByRole('button', { name: 'Detail' })).toBeVisible();

    const duplicate = await postApiRaw(request, token, '/v1/lessons/records', {
      occurrenceId: fixture.occurrence.id,
      resultStatus: 'COMPLETED',
      actualTeacherId: fixture.teacher.profile.id,
      actualClassroomId: fixture.classroom.id,
      notes: `${MARKER} duplicate confirm`,
    });
    expect(duplicate.ok, duplicate.text).toBe(false);
    // 已终确认的课次可能先被 planningStatus 拦截，也可能被 lesson_record 去重拦截；两者都应拒绝重复扣课。
    expect(duplicate.text).toMatch(/LESSON_ALREADY_CONFIRMED|LESSON_OCCURRENCE_NOT_CONFIRMABLE/);

    await expectPackageSettlementUnchanged(fixture.coursePackage.id, confirmedRecord.id, snapshot);
    const unpaid = await fetchUnpaidTeacherLessons(request, token, { teacherId: fixture.teacher.profile.id });
    expect(unpaid.filter((item) => String(item.lessonRecordId) === String(confirmedRecord.id))).toHaveLength(1);
  });

  test('E2EUI_FLOWEDGE 重复付款被拒绝且付款台账不重复', async ({ page, request }) => {
    const token = await apiLogin(request);
    const fixture = await createOneOnOneFlowFixture(request, token, 'duppay', {
      totalUnits: 8,
      lessonUnits: 2,
      slotStart: '14:00',
      slotEnd: '15:00',
    });

    await loginAsAdmin(page);
    await adminDirectConfirmFromUi(page, fixture, {
      resultStatus: 'Completed',
      notes: `${MARKER} payable lesson`,
    });

    const [confirmedRecord] = await fetchLessonRecords(request, token, { packageId: fixture.coursePackage.id });
    expect(confirmedRecord).toMatchObject({
      confirmStage: 'CONFIRMED',
      resultStatus: 'COMPLETED',
    });
    await createTeacherPaymentByApi(request, token, MARKER, {
      teacherId: fixture.teacher.profile.id,
      periodStart: todayInNewYork(),
      periodEnd: todayInNewYork(),
      lessonRecordIds: [confirmedRecord.id],
    });
    const snapshot = await readPaymentSnapshot(confirmedRecord.id);

    const duplicate = await postApiRaw(request, token, '/v1/finance/payments', {
      teacherId: fixture.teacher.profile.id,
      periodStart: todayInNewYork(),
      periodEnd: todayInNewYork(),
      lessonRecordIds: [confirmedRecord.id],
      notes: `${MARKER} duplicate payment`,
    });
    expect(duplicate.ok, duplicate.text).toBe(false);
    expect(duplicate.text).toContain('ALREADY_PAID');
    expect(await readPaymentSnapshot(confirmedRecord.id)).toEqual(snapshot);
  });

  test('E2EUI_FLOWEDGE 关闭课包后继续消课被拒绝且数据不变', async ({ page, request }) => {
    const token = await apiLogin(request);
    const fixture = await createOneOnOneFlowFixture(request, token, 'closedpkg', {
      totalUnits: 8,
      lessonUnits: 2,
      slotStart: '15:00',
      slotEnd: '16:00',
    });

    await loginAsAdmin(page);
    await closePackageFromUi(page, fixture);

    const closedPackage = await fetchCoursePackageDetail(request, token, fixture.coursePackage.id);
    expect(closedPackage).toMatchObject({
      packageStatus: 'CLOSED',
      remainingUnits: 8,
    });
    const snapshot = await readPackageSnapshot(fixture.coursePackage.id);

    const blocked = await postApiRaw(request, token, '/v1/lessons/records', {
      occurrenceId: fixture.occurrence.id,
      resultStatus: 'COMPLETED',
      actualTeacherId: fixture.teacher.profile.id,
      actualClassroomId: fixture.classroom.id,
      notes: `${MARKER} confirm after close`,
    });
    expect(blocked.ok, blocked.text).toBe(false);
    expect(blocked.text).toContain('PACKAGE_NOT_CONFIRMABLE');

    await expectPackageSnapshotUnchanged(fixture.coursePackage.id, snapshot);
    await expectNoDeductionAndNoPayroll(request, token, {
      packageId: fixture.coursePackage.id,
      teacherId: fixture.teacher.profile.id,
      lessonRecordId: undefined,
      expectedBalanceUnits: 16,
    });
  });

  test('E2EUI_FLOWEDGE 余额不足消课被拒绝且课包、流水、待结算不变', async ({ page, request }) => {
    const token = await apiLogin(request);
    const fixture = await createOneOnOneFlowFixture(request, token, 'lowbalance', {
      totalUnits: 2,
      lessonUnits: 2,
      slotStart: '16:00',
      slotEnd: '17:00',
    });
    await setPackageInternalBalance(fixture.coursePackage.id, {
      remainingUnits: 1,
      consumedUnits: 3,
    });
    const snapshot = await readPackageSnapshot(fixture.coursePackage.id);

    await loginAsAdmin(page);
    const blocked = await postApiRaw(request, token, '/v1/lessons/records', {
      occurrenceId: fixture.occurrence.id,
      resultStatus: 'COMPLETED',
      actualTeacherId: fixture.teacher.profile.id,
      actualClassroomId: fixture.classroom.id,
      notes: `${MARKER} insufficient balance`,
    });
    expect(blocked.ok, blocked.text).toBe(false);
    expect(blocked.text).toContain('INSUFFICIENT_REMAINING_UNITS');

    await expectPackageSnapshotUnchanged(fixture.coursePackage.id, snapshot);
    await expectNoDeductionAndNoPayroll(request, token, {
      packageId: fixture.coursePackage.id,
      teacherId: fixture.teacher.profile.id,
      lessonRecordId: undefined,
      expectedBalanceUnits: 1,
    });
  });

  test('E2EUI_FLOWEDGE 重叠排课被拒绝且不生成第二个课包', async ({ request }) => {
    const token = await apiLogin(request);
    const fixture = await createOneOnOneFlowFixture(request, token, 'conflict', {
      totalUnits: 8,
      lessonUnits: 2,
      slotStart: '19:00',
      slotEnd: '20:00',
    });
    const snapshot = await readPackageSnapshot(fixture.coursePackage.id);

    const blocked = await postApiRaw(request, token, '/v1/enrollment/orders', {
      studentId: fixture.student.id,
      courseProductId: fixture.product.id,
      primaryTeacherId: fixture.teacher.profile.id,
      defaultClassroomId: fixture.classroom.id,
      handledByName: `${MARKER} Handler conflict duplicate`,
      startDate: todayInNewYork(),
      originalAmount: 800,
      discountAmount: 0,
      receivedAmount: 0,
      totalUnits: 8,
      defaultLessonUnits: 2,
      issueReceipt: false,
      remark: MARKER,
      slots: [{
        weekday: weekdayForApi(todayInNewYork()),
        startTime: '19:30',
        endTime: '20:30',
        plannedUnits: 2,
      }],
    });
    expect(blocked.ok, blocked.text).toBe(false);
    expect(blocked.text).toContain('SCHEDULE_CONFLICT');

    await expectPackageSnapshotUnchanged(fixture.coursePackage.id, snapshot);
    const packages = await fetchCoursePackages(request, token, {
      studentId: fixture.student.id,
      courseProductId: fixture.product.id,
    });
    expect(packages).toHaveLength(1);
  });
});

interface OneOnOneFixture {
  student: ApiRecord;
  classroom: ApiRecord;
  teacher: Awaited<ReturnType<typeof createTeacherAccountByApi>>;
  product: ApiRecord;
  coursePackage: ApiRecord;
  occurrence: ApiRecord;
}

async function withBusinessFlowDb(action: (db: Connection) => Promise<void>): Promise<void> {
  const db = await createDbConnection();
  try {
    await action(db);
  } finally {
    await db.end();
  }
}

async function createOneOnOneFlowFixture(
  request: APIRequestContext,
  token: string,
  label: string,
  options: { totalUnits: number; lessonUnits: number; slotStart: string; slotEnd: string },
): Promise<OneOnOneFixture> {
  const suffix = `${Date.now().toString(36)}_${label}`;
  const startDate = todayInNewYork();
  const student = await createStudentByApi(request, token, MARKER, suffix);
  const classroom = await createClassroomByApi(request, token, MARKER, suffix);
  const teacher = await createTeacherAccountByApi(request, token, MARKER, suffix);
  const product = await createOneOnOneCourseProductByApi(request, token, MARKER, suffix, {
    defaultTotalUnits: options.totalUnits,
    defaultLessonUnits: options.lessonUnits,
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
    totalUnits: options.totalUnits,
    defaultLessonUnits: options.lessonUnits,
    handledBySuffix: suffix,
    slots: [{
      weekday: weekdayForApi(startDate),
      startTime: options.slotStart,
      endTime: options.slotEnd,
      plannedUnits: options.lessonUnits,
    }],
  });

  const [coursePackage] = await fetchCoursePackages(request, token, {
    studentId: student.id,
    courseProductId: product.id,
  });
  expect(coursePackage, '报名后应生成 1v1 课包').toBeTruthy();
  const occurrence = await fetchFirstOccurrenceForPackage(request, token, coursePackage.id);
  return { student, classroom, teacher, product, coursePackage, occurrence };
}

function weekdayForApi(dateText: string): number {
  const day = new Date(`${dateText}T12:00:00Z`).getUTCDay();
  return day === 0 ? 7 : day;
}

async function teacherInitialConfirmFromUi(
  page: Page,
  fixture: OneOnOneFixture,
  data: { resultStatus: 'Completed' | 'Formal Leave' | 'No Show'; notes: string },
): Promise<void> {
  await openOneOnOnePackage(page, fixture);
  const row = lessonRow(page, fixture.teacher.profile.teacherName);
  await row.getByRole('button', { name: 'Teacher Confirm' }).click();
  const dialog = visibleDialog(page, 'Confirm Lesson');
  await selectByLabel(page, dialog, 'Result Status', data.resultStatus);
  await fillInputByLabel(dialog, 'Notes', data.notes);

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/lessons/records') && res.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Confirm', exact: true }).click();
  expect((await response).ok(), '老师 UI 初确认接口应成功').toBeTruthy();
  await expect(dialog).toBeHidden();
  await expect(lessonRow(page, fixture.teacher.profile.teacherName)).toContainText('Pending Admin Review');
}

async function adminFinalConfirmFromUi(page: Page, fixture: OneOnOneFixture): Promise<void> {
  await openOneOnOnePackage(page, fixture);
  const row = lessonRow(page, fixture.teacher.profile.teacherName);
  await row.getByRole('button', { name: 'Admin Confirm' }).click();
  const dialog = visibleDialog(page, 'Confirm Lesson');

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/lessons/records/') &&
    res.url().includes('/admin-confirm') &&
    res.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Confirm', exact: true }).click();
  expect((await response).ok(), '管理员 UI 终确认接口应成功').toBeTruthy();
  await expect(dialog).toBeHidden();
}

async function adminRejectFromUi(page: Page, fixture: OneOnOneFixture, rejectReason: string): Promise<void> {
  await openOneOnOnePackage(page, fixture);
  const row = lessonRow(page, fixture.teacher.profile.teacherName);
  await row.getByRole('button', { name: 'Reject' }).click();
  const dialog = visibleDialog(page, 'Reject Lesson');
  await fillInputByLabel(dialog, 'Reason', rejectReason);

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/lessons/records/') &&
    res.url().includes('/reject') &&
    res.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Reject', exact: true }).click();
  expect((await response).ok(), '管理员 UI 驳回接口应成功').toBeTruthy();
  await expect(dialog).toBeHidden();
}

async function adminDirectConfirmFromUi(
  page: Page,
  fixture: OneOnOneFixture,
  data: { resultStatus: 'Completed' | 'Formal Leave' | 'No Show'; notes: string },
): Promise<void> {
  await openOneOnOnePackage(page, fixture);
  const row = lessonRow(page, fixture.teacher.profile.teacherName);
  await row.getByRole('button', { name: 'Confirm' }).click();
  const dialog = visibleDialog(page, 'Confirm Lesson');
  await selectByLabel(page, dialog, 'Result Status', data.resultStatus);
  await fillInputByLabel(dialog, 'Notes', data.notes);

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/lessons/records') && res.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Confirm', exact: true }).click();
  expect((await response).ok(), '管理员 UI 直接确认接口应成功').toBeTruthy();
  await expect(dialog).toBeHidden();
}

async function openOneOnOnePackage(page: Page, fixture: OneOnOneFixture): Promise<void> {
  const studentsResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/students') && res.request().method() === 'GET',
  );
  await page.goto('/schedule/adjustments');
  expect((await studentsResponse).ok(), '学生列表接口应成功').toBeTruthy();
  await page.getByPlaceholder('Search student...').fill(fixture.student.studentName);

  const packagesResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/enrollment/packages') && res.request().method() === 'GET',
  );
  await page.locator('.adj-sidebar__item').filter({ hasText: fixture.student.studentName }).first().click();
  expect((await packagesResponse).ok(), '课包列表接口应成功').toBeTruthy();

  const occurrencesResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/schedule/occurrences') && res.request().method() === 'GET',
  );
  await page.locator('.pkg-card').filter({ hasText: fixture.coursePackage.packageName }).first().click();
  expect((await occurrencesResponse).ok(), '课次列表接口应成功').toBeTruthy();
  await expect(lessonRow(page, fixture.teacher.profile.teacherName)).toBeVisible();
}

async function closePackageFromUi(page: Page, fixture: OneOnOneFixture): Promise<void> {
  await page.goto('/enrollment/packages');
  const panel = page.locator('section').filter({ hasText: 'Course Packages' }).first();
  await expect(panel).toBeVisible();
  const listResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/enrollment/packages') && res.request().method() === 'GET',
  );
  await panel.getByPlaceholder('Search by package name or no.').fill(fixture.product.productName);
  expect((await listResponse).ok(), '课包列表接口应成功').toBeTruthy();
  const productRow = tableRowByText(panel, fixture.product.productName);
  await expect(productRow).toBeVisible();
  await expandRowIfNeeded(productRow);
  const packageRow = panel.locator('.nested-table .el-table__row').filter({ hasText: fixture.student.studentName }).first();
  await packageRow.getByRole('button', { name: 'Detail' }).click();

  const dialog = visibleDialog(page, 'Package Detail');
  const closeResponse = page.waitForResponse((res) =>
    res.url().includes(`/api/v1/enrollment/packages/${fixture.coursePackage.id}/close`) &&
    res.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Close Package' }).click();
  expect((await closeResponse).ok(), '关闭课包接口应成功').toBeTruthy();
  await expect(dialog).toBeHidden();
}

function lessonRow(page: Page, teacherName: string): Locator {
  return page.locator('.pkg-occ-row').filter({ hasText: teacherName }).first();
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

async function expectSingleLessonRecord(
  request: APIRequestContext,
  token: string,
  packageId: EntityId,
  expected: Record<string, unknown>,
): Promise<ApiRecord> {
  const records = await fetchLessonRecords(request, token, { packageId });
  expect(records).toHaveLength(1);
  expect(records[0]).toMatchObject(expected);
  return records[0];
}

async function expectNoDeductionAndNoPayroll(
  request: APIRequestContext,
  token: string,
  data: { packageId: EntityId; teacherId: EntityId; lessonRecordId?: EntityId; expectedBalanceUnits: number },
): Promise<void> {
  const balance = await fetchCoursePackageBalance(request, token, data.packageId);
  expect(balance, '课包余额不应变化').toBe(data.expectedBalanceUnits);

  const ledger = await fetchHourLedger(request, token, { packageId: data.packageId });
  if (data.lessonRecordId == null) {
    expect(ledger).toHaveLength(0);
  } else {
    expect(ledger.some((item) => String(item.lessonRecordId) === String(data.lessonRecordId))).toBe(false);
  }

  const unpaid = await fetchUnpaidTeacherLessons(request, token, { teacherId: data.teacherId });
  if (data.lessonRecordId == null) {
    expect(unpaid).toHaveLength(0);
  } else {
    expect(unpaid.some((item) => String(item.lessonRecordId) === String(data.lessonRecordId))).toBe(false);
  }
}

async function readPackageSnapshot(packageId: EntityId): Promise<{ remainingUnits: number; consumedUnits: number; ledgerCount: number; recordCount: number }> {
  return withSnapshotDb(packageId, async (db) => {
    const packageRow = await queryOne<{ remaining_units: number; consumed_units: number }>(
      db,
      'SELECT remaining_units, consumed_units FROM course_package WHERE id = ?',
      [packageId],
    );
    const ledgerRow = await queryOne<{ count: number }>(db, 'SELECT COUNT(*) AS count FROM hour_ledger WHERE package_id = ?', [packageId]);
    const recordRow = await queryOne<{ count: number }>(db, 'SELECT COUNT(*) AS count FROM lesson_record WHERE package_id = ?', [packageId]);
    expect(packageRow).toBeTruthy();
    return {
      remainingUnits: Number(packageRow!.remaining_units),
      consumedUnits: Number(packageRow!.consumed_units),
      ledgerCount: Number(ledgerRow!.count),
      recordCount: Number(recordRow!.count),
    };
  });
}

async function readPackageSettlementSnapshot(packageId: EntityId, lessonRecordId: EntityId): Promise<{
  remainingUnits: number;
  consumedUnits: number;
  ledgerCount: number;
  lessonRecordCount: number;
}> {
  return withSnapshotDb(packageId, async (db) => {
    const packageRow = await queryOne<{ remaining_units: number; consumed_units: number }>(
      db,
      'SELECT remaining_units, consumed_units FROM course_package WHERE id = ?',
      [packageId],
    );
    const ledgerRow = await queryOne<{ count: number }>(
      db,
      'SELECT COUNT(*) AS count FROM hour_ledger WHERE package_id = ? AND lesson_record_id = ?',
      [packageId, lessonRecordId],
    );
    const recordRow = await queryOne<{ count: number }>(
      db,
      'SELECT COUNT(*) AS count FROM lesson_record WHERE package_id = ?',
      [packageId],
    );
    expect(packageRow).toBeTruthy();
    return {
      remainingUnits: Number(packageRow!.remaining_units),
      consumedUnits: Number(packageRow!.consumed_units),
      ledgerCount: Number(ledgerRow!.count),
      lessonRecordCount: Number(recordRow!.count),
    };
  });
}

async function readPaymentSnapshot(lessonRecordId: EntityId): Promise<{ itemCount: number; paymentCount: number }> {
  const db = await createDbConnection();
  try {
    const itemRow = await queryOne<{ count: number }>(
      db,
      'SELECT COUNT(*) AS count FROM teacher_payment_item WHERE lesson_record_id = ?',
      [lessonRecordId],
    );
    const paymentRow = await queryOne<{ count: number }>(
      db,
      `
        SELECT COUNT(DISTINCT p.id) AS count
        FROM teacher_payment p
        INNER JOIN teacher_payment_item item ON item.payment_id = p.id
        WHERE item.lesson_record_id = ?
      `,
      [lessonRecordId],
    );
    return {
      itemCount: Number(itemRow!.count),
      paymentCount: Number(paymentRow!.count),
    };
  } finally {
    await db.end();
  }
}

async function setPackageInternalBalance(
  packageId: EntityId,
  data: { remainingUnits: number; consumedUnits: number },
): Promise<void> {
  await withBusinessFlowDb(async (db) => {
    await db.execute(
      'UPDATE course_package SET remaining_units = ?, consumed_units = ? WHERE id = ?',
      [data.remainingUnits, data.consumedUnits, packageId],
    );
  });
}

async function expectPackageSnapshotUnchanged(
  packageId: EntityId,
  expected: Awaited<ReturnType<typeof readPackageSnapshot>>,
): Promise<void> {
  expect(await readPackageSnapshot(packageId)).toEqual(expected);
}

async function expectPackageSettlementUnchanged(
  packageId: EntityId,
  lessonRecordId: EntityId,
  expected: Awaited<ReturnType<typeof readPackageSettlementSnapshot>>,
): Promise<void> {
  expect(await readPackageSettlementSnapshot(packageId, lessonRecordId)).toEqual(expected);
}

async function withSnapshotDb<T>(packageId: EntityId, action: (db: Connection) => Promise<T>): Promise<T> {
  expect(packageId).toBeTruthy();
  const db = await createDbConnection();
  try {
    return await action(db);
  } finally {
    await db.end();
  }
}

async function postApiRaw(
  request: APIRequestContext,
  token: string,
  path: string,
  data: LessonConfirmPayload | Record<string, unknown>,
): Promise<{ ok: boolean; status: number; text: string }> {
  const response = await request.post(`${API_BASE_URL}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
    data,
  });
  return {
    ok: response.ok(),
    status: response.status(),
    text: await response.text(),
  };
}
