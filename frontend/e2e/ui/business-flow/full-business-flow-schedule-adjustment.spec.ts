import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test';
import type { Connection } from 'mysql2/promise';

import { API_BASE_URL, apiLogin, plusDays, todayInNewYork } from '../../helpers/api';
import {
  cleanupBusinessFlowE2EData,
  createDbConnection,
  ensureLessonRecordDualConfirmSchema,
  queryAll,
  queryOne,
} from '../../helpers/db';
import {
  fillInputByLabel,
  formItemByLabel,
  selectElementPlusOption,
  visibleDialog,
} from '../helpers/element-plus';
import { loginAsAdmin } from '../helpers/session';
import {
  adminConfirmLessonRecordByApi,
  confirmLessonRecordByApi,
  createClassroomByApi,
  createOneOnOneCourseProductByApi,
  createOneOnOneEnrollmentOrderByApi,
  createScheduleAdjustmentByApi,
  createStudentByApi,
  createTeacherAccountByApi,
  fetchCoursePackageBalance,
  fetchCoursePackageDetail,
  fetchCoursePackages,
  fetchFirstOccurrenceForPackage,
  fetchLessonRecordDetail,
  fetchLessonRecords,
  teacherConfirmOwnLessonByApi,
  type ApiRecord,
  type EntityId,
} from './business-flow-api';

const MARKER = 'E2EUI_FLOWADJ';

test.describe.serial('Business Flow - schedule adjustment, makeup and extension E2E UI', () => {
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

  test('E2EUI_FLOWADJ 本周临时调课通过 UI 修改时间、老师、教室和课时，不改 recurring rule 并写调整日志', async ({ page, request }) => {
    const token = await apiLogin(request);
    const fixture = await createOneOnOneFlowFixture(request, token, 'weekly', {
      slotStart: '08:00',
      slotEnd: '09:00',
      lessonUnits: 2,
      withAlternateResources: true,
    });
    const ruleSnapshot = await readRecurringRuleSnapshot(fixture.coursePackage.id);

    await loginAsAdmin(page);
    await openRecurringPackage(page, fixture);
    await page.getByRole('button', { name: 'Adjust This Week' }).click();
    const dialog = visibleDialog(page, 'Adjust This Week');
    await expect(dialog).toContainText('The recurring weekly schedule remains unchanged.');
    await dialog.locator('.weekly-list__item').filter({ hasText: fixture.teacher.profile.teacherName }).first().click();

    // 先只改时间、老师、教室，校验“课时不变”影响提示；随后分别覆盖“少上”和“多上”提示。
    await selectWeeklyEditorOption(page, dialog, 'Start', '09:00');
    await fillWeeklyEditorNumber(dialog, 'Hours', '1.0');
    await selectWeeklyEditorOption(page, dialog, 'Teacher', fixture.altTeacher!.profile.teacherName);
    await selectWeeklyEditorOption(page, dialog, 'Classroom', fixture.altClassroom!.roomName);
    await dialog.getByRole('button', { name: 'Save This Week' }).click();
    await expectWeeklyImpactMessage(page, 'This week timing changes only.');
    await page.locator('.el-message-box:visible').getByRole('button', { name: 'Cancel' }).click();
    await expect(page.locator('.el-message-box:visible')).toHaveCount(0);

    await fillWeeklyEditorNumber(dialog, 'Hours', '0.5');
    await dialog.getByRole('button', { name: 'Save This Week' }).click();
    await expectWeeklyImpactMessage(page, 'This week will use fewer package hours.');
    await page.locator('.el-message-box:visible').getByRole('button', { name: 'Cancel' }).click();
    await expect(page.locator('.el-message-box:visible')).toHaveCount(0);

    await fillWeeklyEditorNumber(dialog, 'Hours', '1.5');
    await dialog.getByRole('button', { name: 'Save This Week' }).click();
    await expectWeeklyImpactMessage(page, 'This week will use more package hours.');
    const weeklyResponse = page.waitForResponse((res) =>
      res.url().includes('/api/v1/schedule/adjustments/weekly') && res.request().method() === 'POST',
    );
    await page.locator('.el-message-box:visible').getByRole('button', { name: 'Save This Week' }).click();
    expect((await weeklyResponse).ok(), '本周临时调课接口应成功').toBeTruthy();
    await expect(dialog).toBeHidden();

    const adjusted = await fetchFirstOccurrenceForPackage(request, token, fixture.coursePackage.id);
    expect(adjusted).toMatchObject({
      plannedTeacherId: fixture.altTeacher!.profile.id,
      plannedClassroomId: fixture.altClassroom!.id,
      plannedUnits: 3,
      planningStatus: 'ADJUSTED',
    });
    expect(timePart(adjusted.plannedStartAt)).toBe('09:00');
    await expectRecurringRuleSnapshot(fixture.coursePackage.id, ruleSnapshot);
    await expectAdjustmentLogs(fixture.coursePackage.id, ['WEEKLY_TEMP_UPDATE']);
  });

  test('E2EUI_FLOWADJ 请假后 UI 终确认触发顺延补课；API 支持显式补课，UI 取消补课入口缺口被锁定', async ({ page, request }) => {
    const token = await apiLogin(request);
    const fixture = await createOneOnOneFlowFixture(request, token, 'makeup', {
      slotStart: '10:00',
      slotEnd: '11:00',
      lessonUnits: 2,
    });
    const beforePackage = await fetchCoursePackageDetail(request, token, fixture.coursePackage.id);

    await loginAsAdmin(page);
    await openOneOnOnePackage(page, fixture);
    const row = lessonRow(page, fixture.teacher.profile.teacherName);
    await row.getByRole('button', { name: 'Confirm' }).click();
    const confirmDialog = visibleDialog(page, 'Confirm Lesson');
    await assertCancellationIsNotUiConfirmOption(page, confirmDialog);
    await selectElementPlusOption(page, formItemByLabel(confirmDialog, 'Result Status').locator('.el-select'), 'Formal Leave');
    await fillInputByLabel(confirmDialog, 'Notes', `${MARKER} formal leave with extension`);
    const confirmResponse = page.waitForResponse((res) =>
      res.url().includes('/api/v1/lessons/records') && res.request().method() === 'POST',
    );
    await confirmDialog.getByRole('button', { name: 'Confirm', exact: true }).click();
    expect((await confirmResponse).ok(), 'Formal Leave UI 确认接口应成功').toBeTruthy();
    await expect(confirmDialog).toBeHidden();

    const [record] = await fetchLessonRecords(request, token, { packageId: fixture.coursePackage.id });
    expect(record).toMatchObject({
      confirmStage: 'CONFIRMED',
      resultStatus: 'FORMAL_LEAVE',
      deductedUnits: 0,
      shouldExtendEndDate: true,
    });
    const autoMakeups = await readMakeupOccurrences(fixture.occurrence.id);
    expect(autoMakeups).toHaveLength(1);
    expect(autoMakeups[0]).toMatchObject({ occurrence_type: 'MAKEUP', planning_status: 'SCHEDULED' });
    expect(await fetchCoursePackageBalance(request, token, fixture.coursePackage.id), '请假顺延不应扣课包余额').toBe(16);
    const afterPackage = await fetchCoursePackageDetail(request, token, fixture.coursePackage.id);
    expect(afterPackage.remainingUnits).toBe(beforePackage.remainingUnits);

    const manualMakeupDate = plusDays(todayInNewYork(), 3);
    const manualMakeupStart = `${manualMakeupDate}T18:00:00`;
    const manualMakeup = await createScheduleAdjustmentByApi(request, token, {
      occurrenceId: fixture.occurrence.id,
      adjustmentType: 'MAKEUP_CREATE',
      plannedTeacherId: fixture.teacher.profile.id,
      plannedClassroomId: fixture.classroom.id,
      plannedStartAt: manualMakeupStart,
      plannedEndAt: `${manualMakeupDate}T19:00:00`,
      plannedUnits: 2,
      reason: `${MARKER} explicit API makeup`,
    });
    expect(manualMakeup).toMatchObject({ adjustmentType: 'MAKEUP_CREATE' });
    await expectAdjustmentLogs(fixture.coursePackage.id, ['MAKEUP_CREATE']);
  });

  test('E2EUI_FLOWADJ 调课/补课冲突与已核销不可修改均返回错误且数据不变', async ({ page, request }) => {
    const token = await apiLogin(request);
    const conflictFixture = await createOneOnOneFlowFixture(request, token, 'conflict', {
      slotStart: '12:00',
      slotEnd: '13:00',
      lessonUnits: 2,
    });
    const conflictSnapshot = await readOccurrenceSnapshot(conflictFixture.coursePackage.id);

    const blockedMakeup = await postApiRaw(request, token, '/v1/schedule/adjustments', {
      occurrenceId: conflictFixture.occurrence.id,
      adjustmentType: 'MAKEUP_CREATE',
      plannedTeacherId: conflictFixture.teacher.profile.id,
      plannedClassroomId: conflictFixture.classroom.id,
      plannedStartAt: conflictFixture.occurrence.plannedStartAt,
      plannedEndAt: conflictFixture.occurrence.plannedEndAt,
      plannedUnits: conflictFixture.occurrence.plannedUnits,
      reason: `${MARKER} conflicting makeup`,
    });
    expect(blockedMakeup.ok, blockedMakeup.text).toBe(false);
    expect(blockedMakeup.text).toContain('SCHEDULE_CONFLICT');
    await expectOccurrenceSnapshot(conflictFixture.coursePackage.id, conflictSnapshot);

    const lockedFixture = await createOneOnOneFlowFixture(request, token, 'locked', {
      slotStart: '14:00',
      slotEnd: '15:00',
      lessonUnits: 2,
    });
    const pending = await confirmLessonRecordByApi(request, token, {
      occurrenceId: lockedFixture.occurrence.id,
      resultStatus: 'COMPLETED',
      actualTeacherId: lockedFixture.teacher.profile.id,
      actualClassroomId: lockedFixture.classroom.id,
      actualStartAt: lockedFixture.occurrence.plannedStartAt,
      actualEndAt: lockedFixture.occurrence.plannedEndAt,
      actualUnits: lockedFixture.occurrence.plannedUnits,
      notes: `${MARKER} lock before weekly adjustment`,
    });
    const records = await fetchLessonRecords(request, token, { packageId: lockedFixture.coursePackage.id });
    let confirmedRecord = records.find((record) => String(record.occurrenceId) === String(lockedFixture.occurrence.id));
    expect(confirmedRecord, '锁定课次前应已生成消课记录').toBeTruthy();
    if (confirmedRecord!.confirmStage === 'PENDING_ADMIN') {
      await adminConfirmLessonRecordByApi(request, token, confirmedRecord!.id);
      confirmedRecord = await fetchLessonRecordDetail(request, token, confirmedRecord!.id);
    }
    expect(confirmedRecord!.confirmStage).toBe('CONFIRMED');

    await loginAsAdmin(page);
    await openRecurringPackage(page, lockedFixture);
    await page.getByRole('button', { name: 'Adjust This Week' }).click();
    const weeklyDialog = visibleDialog(page, 'Adjust This Week');
    await expect(weeklyDialog.locator('.weekly-list__item')).toHaveCount(0);
    await weeklyDialog.getByRole('button', { name: 'Cancel' }).click();

    const lockedSnapshot = await readOccurrenceSnapshot(lockedFixture.coursePackage.id);
    const blockedWeekly = await postApiRaw(request, token, '/v1/schedule/adjustments/weekly', {
      packageId: lockedFixture.coursePackage.id,
      weekStartDate: currentNaturalWeekStart(),
      reason: `${MARKER} redeemed occurrence update`,
      items: [{
        action: 'UPDATE',
        occurrenceId: lockedFixture.occurrence.id,
        plannedTeacherId: lockedFixture.teacher.profile.id,
        plannedClassroomId: lockedFixture.classroom.id,
        plannedStartAt: shiftTime(lockedFixture.occurrence.plannedStartAt, 30),
        plannedEndAt: shiftTime(lockedFixture.occurrence.plannedEndAt, 30),
        plannedUnits: lockedFixture.occurrence.plannedUnits,
      }],
    });
    expect(blockedWeekly.ok, blockedWeekly.text).toBe(false);
    expect(blockedWeekly.text).toContain('LESSON_OCCURRENCE_REDEEMED');
    await expectOccurrenceSnapshot(lockedFixture.coursePackage.id, lockedSnapshot);
  });

  test('E2EUI_FLOWADJ 老师/管理员修改 hours 后后续课次自动平衡', async ({ request }) => {
    const token = await apiLogin(request);

    const teacherChanged = await createOneOnOneFlowFixture(request, token, 'teacher-hours', {
      slotStart: '08:00',
      slotEnd: '09:00',
      lessonUnits: 2,
    });
    const teacherToken = await loginByApi(request, teacherChanged.teacher.username, teacherChanged.teacher.password);
    const beforeTeacherConfirm = await readOccurrenceSnapshot(teacherChanged.coursePackage.id);
    const teacherPending = await teacherConfirmOwnLessonByApi(request, teacherToken, {
      occurrenceId: teacherChanged.occurrence.id,
      resultStatus: 'COMPLETED',
      actualTeacherId: teacherChanged.teacher.profile.id,
      actualClassroomId: teacherChanged.classroom.id,
      actualStartAt: teacherChanged.occurrence.plannedStartAt,
      actualEndAt: shiftTime(teacherChanged.occurrence.plannedStartAt, 30),
      actualUnits: 1,
      notes: `${MARKER} teacher reduced hours`,
    });
    const pendingRecord = await readLessonRecordByOccurrence(teacherChanged.occurrence.id);
    expect(pendingRecord).toMatchObject({ confirm_stage: 'PENDING_ADMIN', actual_units: 1, deducted_units: 1 });

    await adminConfirmLessonRecordByApi(request, token, teacherPending.lessonRecordId);
    const afterTeacherConfirm = await readOccurrenceSnapshot(teacherChanged.coursePackage.id);
    expectPlannedUnitTotal(afterTeacherConfirm, plannedUnitTotal(beforeTeacherConfirm));
    expect(expectOccurrence(afterTeacherConfirm, teacherChanged.occurrence.id)).toMatchObject({
      planned_units: 1,
      planning_status: 'MERGED',
    });
    const extendedTail = lastScheduledOccurrence(afterTeacherConfirm);
    expect(extendedTail.planned_units).toBe(3);
    expect(extendedTail.planned_end_at).toBe(shiftTime(extendedTail.planned_start_at, 90));

    const adminChanged = await createOneOnOneFlowFixture(request, token, 'admin-hours', {
      slotStart: '08:00',
      slotEnd: '09:00',
      lessonUnits: 2,
    });
    const adminTeacherToken = await loginByApi(request, adminChanged.teacher.username, adminChanged.teacher.password);
    const beforeAdminConfirm = await readOccurrenceSnapshot(adminChanged.coursePackage.id);
    const adminPending = await teacherConfirmOwnLessonByApi(request, adminTeacherToken, {
      occurrenceId: adminChanged.occurrence.id,
      resultStatus: 'COMPLETED',
      actualTeacherId: adminChanged.teacher.profile.id,
      actualClassroomId: adminChanged.classroom.id,
      actualStartAt: adminChanged.occurrence.plannedStartAt,
      actualEndAt: shiftTime(adminChanged.occurrence.plannedStartAt, 30),
      actualUnits: 1,
      notes: `${MARKER} teacher reduced before admin review`,
    });

    const reviewResponse = await request.post(`${API_BASE_URL}/v1/lessons/records/${adminPending.lessonRecordId}/admin-confirm`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        occurrenceId: adminChanged.occurrence.id,
        resultStatus: 'COMPLETED',
        actualTeacherId: adminChanged.teacher.profile.id,
        actualClassroomId: adminChanged.classroom.id,
        actualStartAt: adminChanged.occurrence.plannedStartAt,
        actualEndAt: shiftTime(adminChanged.occurrence.plannedStartAt, 90),
        actualUnits: 3,
        notes: `${MARKER} admin increased hours`,
      },
    });
    expect(reviewResponse.ok(), await reviewResponse.text()).toBeTruthy();
    const adminRecord = await readLessonRecordByOccurrence(adminChanged.occurrence.id);
    expect(adminRecord).toMatchObject({ confirm_stage: 'CONFIRMED', actual_units: 3, deducted_units: 3 });

    const afterAdminConfirm = await readOccurrenceSnapshot(adminChanged.coursePackage.id);
    expectPlannedUnitTotal(afterAdminConfirm, plannedUnitTotal(beforeAdminConfirm));
    expect(expectOccurrence(afterAdminConfirm, adminChanged.occurrence.id)).toMatchObject({
      planned_units: 3,
      planning_status: 'MERGED',
    });
    const shortenedTail = lastScheduledOccurrence(afterAdminConfirm);
    expect(shortenedTail.planned_units).toBe(1);
    expect(shortenedTail.planned_end_at).toBe(shiftTime(shortenedTail.planned_start_at, 30));
  });
});

interface OneOnOneFixture {
  student: ApiRecord;
  classroom: ApiRecord;
  altClassroom?: ApiRecord;
  teacher: Awaited<ReturnType<typeof createTeacherAccountByApi>>;
  altTeacher?: Awaited<ReturnType<typeof createTeacherAccountByApi>>;
  product: ApiRecord;
  coursePackage: ApiRecord;
  occurrence: ApiRecord;
}

async function withBusinessFlowDb<T>(action: (db: Connection) => Promise<T>): Promise<T> {
  const db = await createDbConnection();
  try {
    return await action(db);
  } finally {
    await db.end();
  }
}

async function createOneOnOneFlowFixture(
  request: APIRequestContext,
  token: string,
  label: string,
  options: { slotStart: string; slotEnd: string; lessonUnits: number; withAlternateResources?: boolean },
): Promise<OneOnOneFixture> {
  const suffix = `${Date.now().toString(36)}_${label}`;
  const startDate = todayInNewYork();
  const student = await createStudentByApi(request, token, MARKER, suffix);
  const classroom = await createClassroomByApi(request, token, MARKER, suffix);
  const teacher = await createTeacherAccountByApi(request, token, MARKER, suffix);
  const altClassroom = options.withAlternateResources
    ? await createClassroomByApi(request, token, MARKER, `${suffix}_alt`)
    : undefined;
  const altTeacher = options.withAlternateResources
    ? await createTeacherAccountByApi(request, token, MARKER, `${suffix}_alt`)
    : undefined;
  const product = await createOneOnOneCourseProductByApi(request, token, MARKER, suffix, {
    defaultTotalUnits: 8,
    defaultLessonUnits: options.lessonUnits,
    listPrice: 800,
  });

  await createOneOnOneEnrollmentOrderByApi(request, token, MARKER, {
    studentId: student.id,
    courseProductId: product.id,
    primaryTeacherId: teacher.profile.id,
    defaultClassroomId: classroom.id,
    startDate,
    originalAmount: 800,
    totalUnits: 8,
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
  return { student, classroom, altClassroom, teacher, altTeacher, product, coursePackage, occurrence };
}

function weekdayForApi(dateText: string): number {
  const day = new Date(`${dateText}T12:00:00Z`).getUTCDay();
  return day === 0 ? 7 : day;
}

async function openRecurringPackage(page: Page, fixture: OneOnOneFixture): Promise<void> {
  const studentsResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/students') && res.request().method() === 'GET',
  );
  await page.goto('/schedule/recurring');
  expect((await studentsResponse).ok(), '周期排课学生列表接口应成功').toBeTruthy();
  await page.getByPlaceholder('Search students...').fill(fixture.student.studentName);

  const packagesResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/enrollment/packages') && res.request().method() === 'GET',
  );
  const rulesResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/schedule/rules') && res.request().method() === 'GET',
  );
  await page.locator('.col-item').filter({ hasText: fixture.student.studentName }).first().click();
  expect((await packagesResponse).ok(), '周期排课课包列表接口应成功').toBeTruthy();
  expect((await rulesResponse).ok(), '周期排课规则接口应成功').toBeTruthy();
  await page.locator('.col-item').filter({ hasText: fixture.coursePackage.packageName }).first().click();
  await expect(page.locator('.schedule-info')).toContainText(fixture.coursePackage.packageName);
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

function lessonRow(page: Page, teacherName: string): Locator {
  return page.locator('.pkg-occ-row').filter({ hasText: teacherName }).first();
}

function weeklyEditorField(dialog: Locator, label: string): Locator {
  return dialog.locator('.weekly-editor__field').filter({ hasText: label }).first();
}

async function selectWeeklyEditorOption(page: Page, dialog: Locator, label: string, optionName: string): Promise<void> {
  await selectElementPlusOption(page, weeklyEditorField(dialog, label).locator('.el-select').first(), optionName);
}

async function fillWeeklyEditorNumber(dialog: Locator, label: string, value: string): Promise<void> {
  const input = weeklyEditorField(dialog, label).locator('input:visible').first();
  await input.fill(value);
  await input.blur();
}

async function expectWeeklyImpactMessage(page: Page, text: string): Promise<void> {
  const messageBox = page.locator('.el-message-box:visible').first();
  await expect(messageBox).toContainText('This Week Adjustment Impact');
  await expect(messageBox).toContainText(text);
}

async function assertCancellationIsNotUiConfirmOption(page: Page, dialog: Locator): Promise<void> {
  await formItemByLabel(dialog, 'Result Status').locator('.el-select').click();
  const dropdown = page.locator('.el-select-dropdown:visible').last();
  await expect(dropdown.getByRole('option', { name: 'Cancelled', exact: true })).toHaveCount(0);
  await expect(dropdown.getByRole('option', { name: 'Temporary Cancel', exact: true })).toHaveCount(0);
  await page.keyboard.press('Escape');
}

async function readRecurringRuleSnapshot(packageId: EntityId): Promise<ApiRecord> {
  return withBusinessFlowDb(async (db) => {
    const rule = await queryOne<ApiRecord>(
      db,
      `
        SELECT id, package_id, primary_teacher_id, default_classroom_id, rule_status,
               DATE_FORMAT(start_date, '%Y-%m-%d') AS start_date,
               DATE_FORMAT(end_date, '%Y-%m-%d') AS end_date,
               generation_horizon_days, notes
        FROM recurring_schedule_rule
        WHERE package_id = ?
      `,
      [packageId],
    );
    expect(rule).toBeTruthy();
    const slots = await queryAll<ApiRecord>(
      db,
      `
        SELECT weekday, TIME_FORMAT(start_time, '%H:%i:%s') AS start_time,
               TIME_FORMAT(end_time, '%H:%i:%s') AS end_time,
               planned_units, slot_status
        FROM recurring_schedule_slot
        WHERE rule_id = ?
        ORDER BY weekday, start_time
      `,
      [rule!.id],
    );
    return { rule, slots };
  });
}

async function expectRecurringRuleSnapshot(packageId: EntityId, expected: ApiRecord): Promise<void> {
  expect(await readRecurringRuleSnapshot(packageId)).toEqual(expected);
}

async function expectAdjustmentLogs(packageId: EntityId, expectedTypes: string[]): Promise<void> {
  await withBusinessFlowDb(async (db) => {
    const rows = await queryAll<{ adjustment_type: string }>(
      db,
      `
        SELECT adjustment_type
        FROM lesson_adjustment_log
        WHERE occurrence_id IN (SELECT id FROM lesson_occurrence WHERE package_id = ?)
        ORDER BY adjusted_at ASC
      `,
      [packageId],
    );
    expect(rows.map((row) => row.adjustment_type)).toEqual(expect.arrayContaining(expectedTypes));
  });
}

async function readMakeupOccurrences(sourceOccurrenceId: EntityId): Promise<ApiRecord[]> {
  return withBusinessFlowDb((db) =>
    queryAll<ApiRecord>(
      db,
      `
        SELECT id, occurrence_type, planning_status, source_occurrence_id
        FROM lesson_occurrence
        WHERE source_occurrence_id = ?
        ORDER BY planned_start_at ASC
      `,
      [sourceOccurrenceId],
    ),
  );
}

async function readOccurrenceSnapshot(packageId: EntityId): Promise<{ occurrenceCount: number; logCount: number; rows: ApiRecord[] }> {
  return withBusinessFlowDb(async (db) => {
    const rows = await queryAll<ApiRecord>(
      db,
      `
        SELECT id, planned_teacher_id, planned_classroom_id,
               DATE_FORMAT(planned_start_at, '%Y-%m-%dT%H:%i:%s') AS planned_start_at,
               DATE_FORMAT(planned_end_at, '%Y-%m-%dT%H:%i:%s') AS planned_end_at,
               planned_units, planning_status, occurrence_type, source_occurrence_id
        FROM lesson_occurrence
        WHERE package_id = ?
        ORDER BY planned_start_at ASC, id ASC
      `,
      [packageId],
    );
    const logRow = await queryOne<{ count: number }>(
      db,
      `
        SELECT COUNT(*) AS count
        FROM lesson_adjustment_log
        WHERE occurrence_id IN (SELECT id FROM lesson_occurrence WHERE package_id = ?)
      `,
      [packageId],
    );
    return {
      occurrenceCount: rows.length,
      logCount: Number(logRow?.count ?? 0),
      rows,
    };
  });
}

async function expectOccurrenceSnapshot(packageId: EntityId, expected: Awaited<ReturnType<typeof readOccurrenceSnapshot>>): Promise<void> {
  expect(await readOccurrenceSnapshot(packageId)).toEqual(expected);
}

async function readLessonRecordByOccurrence(occurrenceId: EntityId): Promise<ApiRecord> {
  return withBusinessFlowDb(async (db) => {
    const record = await queryOne<ApiRecord>(
      db,
      `
        SELECT id, occurrence_id, confirm_stage, result_status, actual_units, deducted_units
        FROM lesson_record
        WHERE occurrence_id = ?
          AND is_deleted = 0
        ORDER BY created_at DESC, id DESC
        LIMIT 1
      `,
      [occurrenceId],
    );
    expect(record).toBeTruthy();
    return {
      ...record,
      actual_units: Number(record!.actual_units),
      deducted_units: Number(record!.deducted_units),
    };
  });
}

async function loginByApi(request: APIRequestContext, username: string, password: string): Promise<string> {
  const response = await request.post(`${API_BASE_URL}/v1/auth/login`, {
    data: { username, password },
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  const body = await response.json();
  expect(body.success, body.message).toBe(true);
  return body.data.token;
}

function plannedUnitTotal(snapshot: Awaited<ReturnType<typeof readOccurrenceSnapshot>>): number {
  return snapshot.rows
    .filter((row) => row.planning_status !== 'CANCELLED')
    .reduce((sum, row) => sum + Number(row.planned_units), 0);
}

function expectPlannedUnitTotal(snapshot: Awaited<ReturnType<typeof readOccurrenceSnapshot>>, expected: number): void {
  expect(plannedUnitTotal(snapshot)).toBe(expected);
}

function expectOccurrence(snapshot: Awaited<ReturnType<typeof readOccurrenceSnapshot>>, occurrenceId: EntityId): ApiRecord {
  const occurrence = snapshot.rows.find((row) => String(row.id) === String(occurrenceId));
  expect(occurrence).toBeTruthy();
  return occurrence!;
}

function lastScheduledOccurrence(snapshot: Awaited<ReturnType<typeof readOccurrenceSnapshot>>): ApiRecord {
  const scheduled = snapshot.rows.filter((row) => row.planning_status === 'SCHEDULED');
  expect(scheduled.length).toBeGreaterThan(0);
  return scheduled[scheduled.length - 1];
}

async function postApiRaw(
  request: APIRequestContext,
  token: string,
  path: string,
  data: Record<string, unknown>,
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

function currentNaturalWeekStart(): string {
  const date = new Date(`${todayInNewYork()}T12:00:00Z`);
  const daysFromSaturday = (date.getUTCDay() + 1) % 7;
  date.setUTCDate(date.getUTCDate() - daysFromSaturday);
  return date.toISOString().slice(0, 10);
}

function shiftTime(value: string, minutes: number): string {
  const [datePart, timePartText] = value.split('T');
  const [hour, minute, second = '00'] = timePartText.split(':').map(Number);
  const shifted = hour * 60 + minute + minutes;
  const shiftedHour = Math.floor(shifted / 60).toString().padStart(2, '0');
  const shiftedMinute = (shifted % 60).toString().padStart(2, '0');
  return `${datePart}T${shiftedHour}:${shiftedMinute}:${second.toString().padStart(2, '0')}`;
}

function timePart(value: string): string {
  return value.includes('T') ? value.split('T')[1].slice(0, 5) : value.slice(11, 16);
}
