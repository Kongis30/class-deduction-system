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
