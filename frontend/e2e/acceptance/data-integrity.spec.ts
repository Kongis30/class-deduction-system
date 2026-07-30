import { expect, test, type APIRequestContext } from '@playwright/test';

import { API_BASE_URL, apiGet, apiLogin, apiPost } from '../helpers/api';
import { queryOne } from '../helpers/db';
import {
  ACCEPTANCE_MARKER,
  cleanupAcceptanceData,
  createClassroomByApi,
  createOneOnOneProductAndPriceByApi,
  createRecurringScheduleByApi,
  createTeacherAccountByApi,
  firstOccurrenceForPackage,
  submitOneOnOneRegistrationByApi,
  withAcceptanceDb,
} from './helpers';

test.describe.serial('Acceptance - Generated schedule and lesson data integrity', () => {
  test.beforeAll(async () => {
    await cleanupAcceptanceData();
  });

  test.afterAll(async () => {
    await cleanupAcceptanceData();
  });

  test('1 对 1 报名、课表、双确认、课包余额和数据整理应一致', async ({ request }) => {
    const adminToken = await apiLogin(request);
    const suffix = Date.now().toString(36);
    const teacher = await createTeacherAccountByApi(request, adminToken, suffix);
    const classroom = await createClassroomByApi(request, adminToken, suffix);
    const { product, option } = await createOneOnOneProductAndPriceByApi(request, adminToken, suffix, 900);
    const registration = await submitOneOnOneRegistrationByApi(request, adminToken, {
      suffix,
      productId: product.id,
      optionId: option.id,
      teacherId: teacher.profile.id,
      priceAmount: 900,
    });
    const packageId = registration.courseArtifacts[0].packageId;
    expect(packageId, 'registration should generate an active 1-on-1 package').toBeTruthy();

    await createRecurringScheduleByApi(request, adminToken, packageId, teacher.profile.id, classroom.id);
    const occurrence = await withAcceptanceDb((db) => firstOccurrenceForPackage(db, packageId));
    expect(String(occurrence.planned_teacher_id)).toBe(String(teacher.profile.id));
    expect(String(occurrence.planned_classroom_id)).toBe(String(classroom.id));

    const teacherToken = await loginWithCredentials(request, teacher.username, teacher.password);
    await apiPost(request, teacherToken, '/v1/lessons/records', {
      occurrenceId: occurrence.id,
      resultStatus: 'COMPLETED',
      actualTeacherId: teacher.profile.id,
      actualClassroomId: classroom.id,
      actualStartAt: occurrence.planned_start_at,
      actualEndAt: occurrence.planned_end_at,
      actualUnits: 4,
      notes: ACCEPTANCE_MARKER,
    });

    const pendingRecord = await withAcceptanceDb((db) =>
      queryOne<Record<string, any>>(
        db,
        'SELECT * FROM lesson_record WHERE occurrence_id = ? AND package_id = ?',
        [occurrence.id, packageId],
      ),
    );
    expect(pendingRecord).toBeTruthy();
    expect(pendingRecord!.confirm_stage).toBe('PENDING_ADMIN');
    expect(Number(pendingRecord!.deducted_units)).toBe(4);

    await apiPost(request, adminToken, `/v1/lessons/records/${pendingRecord!.id}/admin-confirm`, {
      occurrenceId: occurrence.id,
      resultStatus: 'COMPLETED',
      actualUnits: 4,
      notes: ACCEPTANCE_MARKER,
    });

    await withAcceptanceDb(async (db) => {
      const finalRecord = await queryOne<Record<string, any>>(db, 'SELECT * FROM lesson_record WHERE id = ?', [pendingRecord!.id]);
      expect(finalRecord!.confirm_stage).toBe('CONFIRMED');
      expect(finalRecord!.admin_confirmed_at).toBeTruthy();

      const pkg = await queryOne<Record<string, any>>(db, 'SELECT * FROM course_package WHERE id = ?', [packageId]);
      expect(Number(pkg!.total_units)).toBe(80);
      expect(Number(pkg!.remaining_units)).toBe(76);

      const ledger = await queryOne<Record<string, any>>(
        db,
        'SELECT * FROM hour_ledger WHERE package_id = ? AND lesson_record_id = ?',
        [packageId, pendingRecord!.id],
      );
      expect(ledger).toBeTruthy();
      expect(Number(ledger!.change_units)).toBe(-4);
      expect(Number(ledger!.balance_after_units)).toBe(76);
    });

    const summary = await apiGet<Record<string, any>>(request, adminToken, '/v1/data-summary', {
      keyword: `${ACCEPTANCE_MARKER} Student ${suffix}`,
      pageNum: 1,
      pageSize: 20,
    });
    expect(summary.rows.length).toBeGreaterThan(0);
    const row = summary.rows.find((item: Record<string, any>) =>
      String(item.studentName).includes(`${ACCEPTANCE_MARKER} Student ${suffix}`),
    );
    expect(row, 'data summary should include the registration').toBeTruthy();
    expect(Number(row.balanceAmount)).toBeLessThan(0);
    expect(Number(row.remainingHours)).toBe(38);
  });

  test('请假应保留课时并自动补排，取消应扣除计划课时且不补排', async ({ request }) => {
    const adminToken = await apiLogin(request);
    const suffix = `absence-${Date.now().toString(36)}`;
    const teacher = await createTeacherAccountByApi(request, adminToken, suffix);
    const classroom = await createClassroomByApi(request, adminToken, suffix);
    const cancelTeacher = await createTeacherAccountByApi(request, adminToken, `${suffix}-cancel`);
    const cancelClassroom = await createClassroomByApi(request, adminToken, `${suffix}-cancel`);
    const { product, option } = await createOneOnOneProductAndPriceByApi(request, adminToken, suffix, 900);

    const leaveRegistration = await submitOneOnOneRegistrationByApi(request, adminToken, {
      suffix: `${suffix}-leave`,
      productId: product.id,
      optionId: option.id,
      teacherId: teacher.profile.id,
    });
    const leavePackageId = leaveRegistration.courseArtifacts[0].packageId;
    await createRecurringScheduleByApi(request, adminToken, leavePackageId, teacher.profile.id, classroom.id);
    const leaveOccurrence = await withAcceptanceDb((db) => firstOccurrenceForPackage(db, leavePackageId));

    await apiPost(request, adminToken, '/v1/lessons/records', {
      occurrenceId: leaveOccurrence.id,
      resultStatus: 'FORMAL_LEAVE',
      notes: `${ACCEPTANCE_MARKER} formal leave`,
    });

    await withAcceptanceDb(async (db) => {
      const pkg = await queryOne<Record<string, any>>(db, 'SELECT * FROM course_package WHERE id = ?', [leavePackageId]);
      expect(Number(pkg!.remaining_units)).toBe(80);

      const record = await queryOne<Record<string, any>>(
        db,
        'SELECT * FROM lesson_record WHERE occurrence_id = ? AND package_id = ?',
        [leaveOccurrence.id, leavePackageId],
      );
      expect(record!.result_status).toBe('FORMAL_LEAVE');
      expect(Number(record!.deducted_units)).toBe(0);

      const ledger = await queryOne<Record<string, any>>(
        db,
        'SELECT * FROM hour_ledger WHERE lesson_record_id = ?',
        [record!.id],
      );
      expect(ledger!.ledger_type).toBe('NO_DEDUCT_AUDIT');
      expect(Number(ledger!.change_units)).toBe(0);

      const makeup = await queryOne<Record<string, any>>(
        db,
        'SELECT * FROM lesson_occurrence WHERE source_occurrence_id = ? AND occurrence_type = "MAKEUP" AND planning_status = "SCHEDULED"',
        [leaveOccurrence.id],
      );
      expect(makeup).toBeTruthy();
      expect(Number(makeup!.planned_units)).toBe(Number(leaveOccurrence.planned_units));
    });

    const cancelRegistration = await submitOneOnOneRegistrationByApi(request, adminToken, {
      suffix: `${suffix}-cancel`,
      productId: product.id,
      optionId: option.id,
      teacherId: cancelTeacher.profile.id,
    });
    const cancelPackageId = cancelRegistration.courseArtifacts[0].packageId;
    await createRecurringScheduleByApi(
      request,
      adminToken,
      cancelPackageId,
      cancelTeacher.profile.id,
      cancelClassroom.id,
    );
    const cancelOccurrence = await withAcceptanceDb((db) => firstOccurrenceForPackage(db, cancelPackageId));

    await apiPost(request, adminToken, '/v1/lessons/records', {
      occurrenceId: cancelOccurrence.id,
      resultStatus: 'CANCELLED',
      notes: `${ACCEPTANCE_MARKER} cancellation`,
    });

    await withAcceptanceDb(async (db) => {
      const pkg = await queryOne<Record<string, any>>(db, 'SELECT * FROM course_package WHERE id = ?', [cancelPackageId]);
      expect(Number(pkg!.remaining_units)).toBe(76);

      const record = await queryOne<Record<string, any>>(
        db,
        'SELECT * FROM lesson_record WHERE occurrence_id = ? AND package_id = ?',
        [cancelOccurrence.id, cancelPackageId],
      );
      expect(record!.result_status).toBe('CANCELLED');
      expect(Number(record!.deducted_units)).toBe(4);

      const ledger = await queryOne<Record<string, any>>(
        db,
        'SELECT * FROM hour_ledger WHERE lesson_record_id = ?',
        [record!.id],
      );
      expect(ledger!.ledger_type).toBe('DEDUCT');
      expect(Number(ledger!.change_units)).toBe(-4);

      const makeup = await queryOne<Record<string, any>>(
        db,
        'SELECT * FROM lesson_occurrence WHERE source_occurrence_id = ? AND occurrence_type = "MAKEUP" AND planning_status <> "CANCELLED"',
        [cancelOccurrence.id],
      );
      expect(makeup).toBeNull();
    });
  });
});

async function loginWithCredentials(request: APIRequestContext, username: string, password: string): Promise<string> {
  const response = await request.post(`${API_BASE_URL}/v1/auth/login`, {
    data: { username, password },
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  const body = JSON.parse((await response.text()).replace(/"(\w*[iI][dD])":\s*(\d{15,})/g, '"$1":"$2"')) as {
    success: boolean;
    data: { token: string };
  };
  expect(body.success).toBe(true);
  return body.data.token;
}
