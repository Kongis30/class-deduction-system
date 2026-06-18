import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test';

import { ADMIN_PASSWORD, ADMIN_USERNAME, apiGet, apiLogin, apiPost, plusDays, todayInNewYork } from '../../helpers/api';
import {
  cleanupBusinessFlowE2EData,
  createDbConnection,
  ensureLessonRecordDualConfirmSchema,
  queryAll,
  queryOne,
} from '../../helpers/db';
import {
  fillInputByLabel,
  fillNumberByLabel,
  panelByTitle,
  selectByLabel,
  tableRowByText,
  visibleDialog,
} from '../helpers/element-plus';
import { loginAsAdmin, switchUser } from '../helpers/session';
import {
  createClassroomByApi,
  createPerSessionClassCourseProductByApi,
  createStudentByApi,
  createTeacherAccountByApi,
  fetchUnpaidTeacherLessons,
  saveTeacherPayRateByApi,
  type ApiRecord,
  type EntityId,
} from './business-flow-api';

const MARKER = 'E2EUI_FLOWCLASS';

test.describe.serial('Business Flow - Group class full lifecycle E2E UI', () => {
  test.setTimeout(240_000);

  test.beforeAll(async () => {
    await cleanupClassFlowData();
  });

  test.afterAll(async () => {
    await cleanupClassFlowData();
  });

  test('E2EUI_FLOWCLASS 班课报名、课包展开、老师初确认、管理员终确认与计薪资格链路', async ({ page, request }) => {
    const token = await apiLogin(request);
    const suffix = Date.now().toString(36);
    const today = todayInNewYork();
    const startDate = today;
    const endDate = plusDays(today, 90);

    // ── 前置数据只用 API 准备，后续报名/消课关键动作走 UI。班课产品固定周时段、按次计费。──
    const classroom = await createClassroomByApi(request, token, MARKER, suffix);
    const teacher = await createTeacherAccountByApi(request, token, MARKER, suffix);
    const product = await createPerSessionClassCourseProductByApi(request, token, MARKER, suffix, {
      teacherId: teacher.profile.id,
      classroomId: classroom.id,
      startDate,
      totalSessions: 8,
      listPrice: 800,
      slots: [{ weekday: weekdayOf(startDate), startTime: '09:00', endTime: '10:00', plannedUnits: 2 }],
    });
    const student = await createStudentByApi(request, token, MARKER, suffix);

    await loginAsAdmin(page);

    await createClassEnrollmentFromUi(page, {
      studentName: student.studentName,
      className: product.productName,
      sessions: 4,
      expectedAmount: '$400.00',
      handledBy: `${MARKER} Handler ${suffix}`,
    });

    const classProgressBefore = await expectGroupClassProgress(request, token, product.productName, {
      totalUnits: 8,
      consumedUnits: 0,
      remainingUnits: 8,
      activeStudentCount: 1,
      status: 'ACTIVE',
    });
    const classId = classProgressBefore.classId;

    await expectClassEnrollmentInDb({
      classId,
      studentId: student.id,
      billingType: 'PER_SESSION',
      purchasedQuantity: 4,
      status: 'ACTIVE',
    });

    await expectCoursePackagesGroupClassExpansion(page, request, token, {
      productName: product.productName,
      classId,
      studentName: student.studentName,
      expectedPurchasedText: '4 sessions',
      expectedUsedText: '0',
      expectedRemainingText: '4',
      expectedProgressText: '0%',
    });

    const occurrence = await fetchFirstClassOccurrence(request, token, classId);
    await saveTeacherPayRateByApi(request, token, MARKER, {
      teacherId: teacher.profile.id,
      courseProductId: product.id,
      hourlyRate: 80,
    });

    // ── 老师视角 UI 初确认：班课确认进入待管理员复核，尚未进入计薪终态。──
    await switchUser(page, { username: teacher.username, password: teacher.password });
    await teacherConfirmClassOccurrenceFromUi(page, {
      classId,
      className: product.productName,
      occurrenceId: occurrence.id,
      notes: `${MARKER} teacher initial confirm ${suffix}`,
    });

    const pendingRecord = await expectPendingClassRecordInDb({
      occurrenceId: occurrence.id,
      studentId: student.id,
      actualTeacherId: teacher.profile.id,
    });
    const progressAfterTeacherConfirm = await fetchGroupClassStudentProgress(request, token, classId, student.id);
    expect(progressAfterTeacherConfirm).toMatchObject({
      totalUnits: 4,
      consumedUnits: 1,
      remainingUnits: 3,
    });

    // 班课管理员终确认 UI 当前会走 1v1 的 admin-confirm，班课 package_id 为空时有后端风险；
    // 这里保留 UI 待复核断言，使用班课专用 confirm API 推进终态，避免把已知风险伪装成稳定 UI 路径。
    await switchUser(page, { username: ADMIN_USERNAME, password: ADMIN_PASSWORD });
    await expectPendingAdminReviewVisible(page, {
      className: product.productName,
      occurrenceId: occurrence.id,
    });

    const finalConfirm = await adminFinalizeClassLessonByApi(request, token, {
      classId,
      occurrenceId: occurrence.id,
      actualTeacherId: teacher.profile.id,
      actualClassroomId: classroom.id,
      actualStartAt: occurrence.plannedStartAt,
      actualEndAt: occurrence.plannedEndAt,
      actualUnits: 2,
      notes: `${MARKER} admin final confirm ${suffix}`,
    });
    expect(finalConfirm).toMatchObject({
      confirmStage: 'CONFIRMED',
      confirmedCount: 1,
    });

    await expectClassOccurrenceConfirmedInUi(page, {
      className: product.productName,
      occurrenceId: occurrence.id,
    });

    const finalRecord = await expectFinalClassRecordInDb({
      recordId: pendingRecord.id,
      occurrenceId: occurrence.id,
      studentId: student.id,
    });
    expect(finalRecord.record_source).toBe('TEACHER');

    const progressAfterFinalConfirm = await fetchGroupClassStudentProgress(request, token, classId, student.id);
    expect(progressAfterFinalConfirm).toMatchObject({
      totalUnits: 4,
      consumedUnits: 1,
      remainingUnits: 3,
      enrollmentStatus: 'ACTIVE',
    });
    const classProgressAfterFinal = await expectGroupClassProgress(request, token, product.productName, {
      totalUnits: 8,
      consumedUnits: 1,
      remainingUnits: 7,
      activeStudentCount: 1,
      status: 'ACTIVE',
    });
    expect(classProgressAfterFinal.classId).toBe(classId);

    await expectCoursePackagesGroupClassExpansion(page, request, token, {
      productName: product.productName,
      classId,
      studentName: student.studentName,
      expectedPurchasedText: '4 sessions',
      expectedUsedText: '1',
      expectedRemainingText: '3',
      expectedProgressText: '25%',
    });

    // 终确认后的班课应进入老师未结算课次，且按班课产品计薪规则产生金额。
    const unpaidLessons = await fetchUnpaidTeacherLessons(request, token, {
      teacherId: teacher.profile.id,
      startDate: today,
      endDate,
    });
    const unpaidClassLesson = unpaidLessons.find((item) => String(item.lessonRecordId) === String(finalRecord.id));
    expect(unpaidClassLesson, '班课终确认后应进入 teacher unpaid lessons').toBeTruthy();
    expect(unpaidClassLesson).toMatchObject({
      className: product.productName,
      productName: product.productName,
      hours: 1,
      hourlyRate: 80,
      amount: 80,
    });
  });
});

async function cleanupClassFlowData(): Promise<void> {
  const db = await createDbConnection();
  try {
    await ensureLessonRecordDualConfirmSchema(db);
    await cleanupBusinessFlowE2EData(db, MARKER);
  } finally {
    await db.end();
  }
}

/** 按次班课报名走 UI，覆盖前端按比例计价和订单创建接口。 */
async function createClassEnrollmentFromUi(
  page: Page,
  data: {
    studentName: string;
    className: string;
    sessions: number;
    expectedAmount: string;
    handledBy: string;
  },
): Promise<void> {
  await page.goto('/enrollment/orders');
  await expect(panelByTitle(page, 'Enrollment Orders')).toBeVisible();
  await page.getByRole('button', { name: 'group class enrollment' }).click();
  const dialog = visibleDialog(page, 'New class enrollment');

  await selectByLabel(page, dialog, 'Student', data.studentName);
  await selectByLabel(page, dialog, 'Class', data.className);
  await fillNumberByLabel(dialog, 'Sessions to purchase', String(data.sessions));
  await expect(dialog).toContainText(data.expectedAmount);
  await fillInputByLabel(dialog, 'Handled by', data.handledBy);

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/enrollment/orders/class-enroll') && res.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Submit' }).click();
  expect((await response).ok(), '班课报名接口应成功').toBeTruthy();
  await expect(dialog).toBeHidden();
}

async function expectCoursePackagesGroupClassExpansion(
  page: Page,
  request: APIRequestContext,
  token: string,
  data: {
    productName: string;
    classId: EntityId;
    studentName: string;
    expectedPurchasedText: string;
    expectedUsedText: string;
    expectedRemainingText: string;
    expectedProgressText: string;
  },
): Promise<void> {
  await page.goto('/enrollment/packages');
  await expect(panelByTitle(page, 'Course Packages')).toBeVisible();
  await page.getByRole('tab', { name: 'Group Class' }).click();
  const panel = panelByTitle(page, 'Course Packages');
  const listResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/enrollment/packages/group-classes') && res.request().method() === 'GET',
  );
  await panel.getByPlaceholder('Search by package name or no.').fill(data.productName);
  expect((await listResponse).ok(), '班课课包聚合列表接口应成功').toBeTruthy();

  const studentsResponse = page.waitForResponse((res) =>
    res.url().includes(`/api/v1/enrollment/packages/group-classes/${data.classId}/students`) &&
    res.request().method() === 'GET',
  );
  const classRow = tableRowByText(panel, data.productName);
  await expect(classRow).toBeVisible();
  await expandRowIfNeeded(classRow);
  expect((await studentsResponse).ok(), '班课学生进度展开接口应成功').toBeTruthy();

  const studentRow = panel.locator('.nested-table .el-table__row').filter({ hasText: data.studentName }).first();
  const cells = studentRow.locator('.el-table__cell');
  await expect(cells.nth(1)).toContainText(data.expectedPurchasedText);
  await expect(cells.nth(2)).toContainText(data.expectedUsedText);
  await expect(cells.nth(3)).toContainText(data.expectedRemainingText);
  await expect(cells.nth(4)).toContainText(data.expectedProgressText);
  await expect(studentRow).toContainText('Active');

  const apiProgress = await fetchGroupClassStudentProgress(request, token, data.classId, undefined, data.studentName);
  expect(apiProgress).toMatchObject({
    studentName: data.studentName,
    enrollmentStatus: 'ACTIVE',
  });
}

async function teacherConfirmClassOccurrenceFromUi(
  page: Page,
  data: {
    classId: EntityId;
    className: string;
    occurrenceId: EntityId;
    notes: string;
  },
): Promise<void> {
  await openClassManagementOccurrence(page, data.className, data.occurrenceId);
  const row = firstClassOccurrenceRow(page);
  await expect(row).toContainText('Pending');
  await row.getByRole('button', { name: /Confirm|Teacher Confirm/ }).click();

  const dialog = visibleDialog(page, 'Confirm Lesson');
  await fillInputByLabel(dialog, 'Notes', data.notes);
  const response = page.waitForResponse((res) =>
    res.url().includes(`/api/v1/classes/${data.classId}/occurrences/${data.occurrenceId}/confirm`) &&
    res.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Confirm' }).click();
  expect((await response).ok(), '老师班课初确认接口应成功').toBeTruthy();
  await expect(dialog).toBeHidden();
  await expect(firstClassOccurrenceRow(page)).toContainText('Pending Admin Review');
}

async function expectPendingAdminReviewVisible(
  page: Page,
  data: { className: string; occurrenceId: EntityId },
): Promise<void> {
  await openClassManagementOccurrence(page, data.className, data.occurrenceId);
  const row = firstClassOccurrenceRow(page);
  await expect(row).toContainText('Pending Admin Review');
  await expect(row.getByRole('button', { name: 'Admin Confirm' })).toBeVisible();
}

async function expectClassOccurrenceConfirmedInUi(
  page: Page,
  data: { className: string; occurrenceId: EntityId },
): Promise<void> {
  await openClassManagementOccurrence(page, data.className, data.occurrenceId);
  await expect(firstClassOccurrenceRow(page)).toContainText('Completed');
}

async function openClassManagementOccurrence(page: Page, className: string, occurrenceId: EntityId): Promise<void> {
  await page.goto('/schedule/adjustments');
  await page.getByRole('button', { name: 'Class Management' }).click();
  await page.getByPlaceholder('Search class...').fill(className);
  await expect(page.locator('.adj-sidebar__item').filter({ hasText: className }).first()).toBeVisible();

  const studentsResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/classes/') && res.url().includes('/students') && res.request().method() === 'GET',
  );
  const occurrencesResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/classes/') && res.url().includes('/occurrences') && res.request().method() === 'GET',
  );
  await page.locator('.adj-sidebar__item').filter({ hasText: className }).first().click();
  expect((await studentsResponse).ok(), '班课学生接口应成功').toBeTruthy();
  expect((await occurrencesResponse).ok(), '班课课次接口应成功').toBeTruthy();
  expect(occurrenceId, 'UI 课次行不展示 id；id 仅用于 API/DB 精确断言').toBeTruthy();
  await expect(firstClassOccurrenceRow(page)).toBeVisible();
}

function firstClassOccurrenceRow(page: Page): Locator {
  return page.locator('.class-occ-list .pkg-occ-row').first();
}

async function adminFinalizeClassLessonByApi(
  request: APIRequestContext,
  token: string,
  data: {
    classId: EntityId;
    occurrenceId: EntityId;
    actualTeacherId: EntityId;
    actualClassroomId: EntityId;
    actualStartAt: string;
    actualEndAt: string;
    actualUnits: number;
    notes: string;
  },
): Promise<ApiRecord> {
  return apiPost<ApiRecord>(request, token, `/v1/classes/${data.classId}/occurrences/${data.occurrenceId}/confirm`, {
    actualTeacherId: data.actualTeacherId,
    actualClassroomId: data.actualClassroomId,
    actualStartAt: data.actualStartAt,
    actualEndAt: data.actualEndAt,
    actualUnits: data.actualUnits,
    notes: data.notes,
  });
}

async function fetchFirstClassOccurrence(request: APIRequestContext, token: string, classId: EntityId): Promise<ApiRecord> {
  const occurrences = await apiGet<ApiRecord[]>(request, token, `/v1/classes/${classId}/occurrences`);
  expect(occurrences[0], '班课应生成固定时段课次').toBeTruthy();
  return occurrences[0];
}

async function expectGroupClassProgress(
  request: APIRequestContext,
  token: string,
  keyword: string,
  expected: Record<string, unknown>,
): Promise<ApiRecord> {
  const page = await apiGet<{ records: ApiRecord[] }>(request, token, '/v1/enrollment/packages/group-classes', {
    keyword,
    pageNum: 1,
    pageSize: 50,
  });
  const progress = page.records.find((item) => item.productName === keyword || item.className === keyword);
  expect(progress, `应能查到班课聚合 ${keyword}`).toBeTruthy();
  expect(progress).toMatchObject(expected);
  return progress!;
}

async function fetchGroupClassStudentProgress(
  request: APIRequestContext,
  token: string,
  classId: EntityId,
  studentId?: EntityId,
  studentName?: string,
): Promise<ApiRecord> {
  const records = await apiGet<ApiRecord[]>(request, token, `/v1/enrollment/packages/group-classes/${classId}/students`);
  const progress = records.find((item) =>
    (studentId != null && String(item.studentId) === String(studentId)) ||
    (studentName != null && item.studentName === studentName),
  );
  expect(progress, '应能查到班课学生进度').toBeTruthy();
  return progress!;
}

async function expectClassEnrollmentInDb(expected: {
  classId: EntityId;
  studentId: EntityId;
  billingType: string;
  purchasedQuantity: number;
  status: string;
}): Promise<void> {
  const db = await createDbConnection();
  try {
    const row = await queryOne<{
      billing_type: string;
      purchased_quantity: number;
      status: string;
    }>(
      db,
      'SELECT billing_type, purchased_quantity, status FROM class_enrollment WHERE class_id=? AND student_id=? ORDER BY created_at DESC LIMIT 1',
      [expected.classId, expected.studentId],
    );
    expect(row).toMatchObject({
      billing_type: expected.billingType,
      purchased_quantity: expected.purchasedQuantity,
      status: expected.status,
    });
  } finally {
    await db.end();
  }
}

async function expectPendingClassRecordInDb(expected: {
  occurrenceId: EntityId;
  studentId: EntityId;
  actualTeacherId: EntityId;
}): Promise<ApiRecord> {
  const db = await createDbConnection();
  try {
    const row = await queryOne<ApiRecord>(
      db,
      `
        SELECT id, occurrence_id, student_id, actual_teacher_id, record_source,
               confirm_stage, should_deduct, deducted_units, confirmed_at, admin_confirmed_at
        FROM lesson_record
        WHERE occurrence_id=? AND student_id=?
        ORDER BY id DESC
        LIMIT 1
      `,
      [expected.occurrenceId, expected.studentId],
    );
    expect(row).toMatchObject({
      occurrence_id: String(expected.occurrenceId),
      student_id: String(expected.studentId),
      actual_teacher_id: String(expected.actualTeacherId),
      record_source: 'TEACHER',
      confirm_stage: 'PENDING_ADMIN',
      should_deduct: 0,
      deducted_units: 0,
      confirmed_at: null,
      admin_confirmed_at: null,
    });
    return row!;
  } finally {
    await db.end();
  }
}

async function expectFinalClassRecordInDb(expected: {
  recordId: EntityId;
  occurrenceId: EntityId;
  studentId: EntityId;
}): Promise<ApiRecord> {
  const db = await createDbConnection();
  try {
    const rows = await queryAll<ApiRecord>(
      db,
      `
        SELECT id, occurrence_id, student_id, record_source, confirm_stage,
               should_deduct, deducted_units, confirmed_at, admin_confirmed_at
        FROM lesson_record
        WHERE occurrence_id=? AND student_id=?
        ORDER BY id DESC
      `,
      [expected.occurrenceId, expected.studentId],
    );
    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({
      id: String(expected.recordId),
      occurrence_id: String(expected.occurrenceId),
      student_id: String(expected.studentId),
      confirm_stage: 'CONFIRMED',
      should_deduct: 0,
      deducted_units: 0,
    });
    expect(rows[0].confirmed_at).toBeTruthy();
    expect(rows[0].admin_confirmed_at).toBeTruthy();
    return rows[0];
  } finally {
    await db.end();
  }
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

function weekdayOf(dateText: string): number {
  const day = new Date(`${dateText}T12:00:00Z`).getUTCDay();
  return day === 0 ? 7 : day;
}
