import { expect, test } from '@playwright/test';

import { apiGet, apiLogin, apiPost, todayInNewYork, type PageResult } from '../helpers/api';
import { queryAll, queryOne } from '../helpers/db';
import { loginAsAdmin } from '../ui/helpers/session';
import {
  ACCEPTANCE_MARKER,
  assertRegistrationDbState,
  cleanupAcceptanceData,
  createClassroomByApi,
  createGroupProductAndPlanByApi,
  createOneOnOneProductAndPriceByApi,
  createTeacherAccountByApi,
  ensureTransferPaymentMethod,
  readReceiptPayload,
  submitGroupRegistrationByApi,
  withAcceptanceDb,
} from './helpers';

test.describe.serial('Acceptance - Registration intake', () => {
  test.beforeAll(async () => {
    await cleanupAcceptanceData();
  });

  test.afterAll(async () => {
    await cleanupAcceptanceData();
  });

  test('报名保存入口应有前端校验，提交后应创建订单、课包、付款流水和收据快照', async ({ page, request }) => {
    const token = await apiLogin(request);
    const suffix = Date.now().toString(36);
    await ensureTransferPaymentMethod(request, token);
    const { product, option } = await createOneOnOneProductAndPriceByApi(request, token, suffix, 900);

    await loginAsAdmin(page);
    const productsLoaded = page.waitForResponse((res) =>
      res.url().includes('/api/v1/base/course-products') && res.request().method() === 'GET',
    );
    const pricesLoaded = page.waitForResponse((res) =>
      res.url().includes('/api/v1/base/course-price-options/active') && res.request().method() === 'GET',
    );
    await page.goto('/registration');
    expect((await productsLoaded).ok(), 'registration should load active course products').toBeTruthy();
    expect((await pricesLoaded).ok(), 'registration should load active course price options').toBeTruthy();

    await page.getByRole('button', { name: 'Save' }).click();
    await expect(page.locator('.validation-list li').filter({ hasText: 'First name and last name are required.' })).toBeVisible();

    const result = await apiPost<Record<string, any>>(request, token, '/v1/registration/intake', {
      student: {
        studentName: `Acceptance Student ${suffix}`,
        grade: 'G4',
        school: 'Acceptance School',
        guardianPhone: `626555${suffix.slice(-4).padStart(4, '0')}`,
      },
      campusName: 'Online Class',
      signupDate: todayInNewYork(),
      periodStartDate: todayInNewYork(),
      periodEndDate: todayInNewYork(),
      handledByName: `${suffix} front desk`,
      items: [{
        lineNo: 1,
        registerClass: '1-on-1',
        courseType: 'ONE_ON_ONE',
        courseProductId: product.id,
        coursePriceOptionId: option.id,
        detail: option.detailText,
        gradeBand: option.gradeBand,
        periodLabel: option.periodLabel,
        priceAmount: 900,
        originalPriceAmount: 900,
        oneOnOneHours: 40,
        oneOnOneUnits: 80,
      }],
      payments: [{ paymentMethod: 'TRANSFER', amount: 300, paidAt: `${todayInNewYork()}T00:00:00` }],
      receiptOptions: { issueReceipt: true },
      remark: `E2E_ACCEPTANCE registration ${suffix}`,
    });

    expect(result.amountDue).toBe(900);
    expect(result.paidAmount).toBe(300);
    expect(result.balanceAmount).toBe(600);
    expect(result.paymentStatus).toBe('PARTIAL');
    expect(result.receiptId).toBeTruthy();

    await withAcceptanceDb((db) =>
      assertRegistrationDbState(db, result, {
        paymentMethod: 'TRANSFER',
        paidAmount: '300.00',
        balanceAmount: '600.00',
      }),
    );
  });

  test('班课报名应按真实前端请求创建订单、班课报名、付款流水和收据', async ({ request }) => {
    const token = await apiLogin(request);
    const suffix = `group-${Date.now().toString(36)}`;
    await ensureTransferPaymentMethod(request, token);
    const teacher = await createTeacherAccountByApi(request, token, suffix);
    const classroom = await createClassroomByApi(request, token, suffix);
    const { product, option, classId } = await createGroupProductAndPlanByApi(
      request,
      token,
      suffix,
      teacher.profile.id,
      classroom.id,
    );

    const result = await submitGroupRegistrationByApi(request, token, {
      suffix,
      productId: product.id,
      option,
      classId,
      paidAmount: 150,
    });

    expect(result.amountDue).toBe(800);
    expect(result.paidAmount).toBe(150);
    expect(result.balanceAmount).toBe(650);
    expect(result.paymentStatus).toBe('PARTIAL');
    expect(result.courseArtifacts).toHaveLength(1);
    expect(result.courseArtifacts[0].classEnrollmentId).toBeTruthy();
    expect(result.courseArtifacts[0].classId).toBeTruthy();
    expect(result.courseArtifacts[0].packageId).toBeFalsy();
    expect(result.receiptId).toBeTruthy();

    await withAcceptanceDb(async (db) => {
      const order = await queryOne<Record<string, any>>(db, 'SELECT * FROM enrollment_order WHERE id = ?', [result.enrollmentOrderId]);
      expect(order).toBeTruthy();
      expect(order!.payment_status).toBe('PARTIAL');
      expect(Number(order!.amount_due).toFixed(2)).toBe('800.00');
      expect(Number(order!.received_amount).toFixed(2)).toBe('150.00');
      expect(Number(order!.balance_amount).toFixed(2)).toBe('650.00');

      const item = await queryOne<Record<string, any>>(
        db,
        'SELECT * FROM enrollment_order_item WHERE enrollment_order_id = ?',
        [result.enrollmentOrderId],
      );
      expect(item).toBeTruthy();
      expect(item!.course_type).toBe('SATURDAY');
      expect(item!.register_class).toBe('Saturday Class');
      expect(String(item!.class_id)).toBe(String(classId));
      expect(Number(item!.one_on_one_units)).toBe(0);
      expect(Number(item!.price_amount).toFixed(2)).toBe('800.00');

      const enrollment = await queryOne<Record<string, any>>(
        db,
        'SELECT * FROM class_enrollment WHERE enrollment_order_item_id = ?',
        [item!.id],
      );
      expect(enrollment).toBeTruthy();
      expect(String(enrollment!.class_id)).toBe(String(classId));
      expect(String(enrollment!.student_id)).toBe(String(order!.student_id));
      expect(enrollment!.billing_type).toBe('PER_SESSION');
      expect(Number(enrollment!.purchased_quantity)).toBe(8);
      expect(enrollment!.status).toBe('ACTIVE');

      const packages = await queryAll<Record<string, any>>(
        db,
        'SELECT * FROM course_package WHERE enrollment_order_item_id = ?',
        [item!.id],
      );
      expect(packages).toHaveLength(0);

      const payment = await queryOne<Record<string, any>>(
        db,
        'SELECT * FROM payment_record WHERE enrollment_order_id = ?',
        [result.enrollmentOrderId],
      );
      expect(payment).toBeTruthy();
      expect(payment!.payment_method).toBe('TRANSFER');
      expect(Number(payment!.amount).toFixed(2)).toBe('150.00');

      const receipt = await queryOne<Record<string, any>>(
        db,
        'SELECT * FROM receipt_record WHERE enrollment_order_id = ? AND template_code = "DEFAULT_RECEIPT"',
        [result.enrollmentOrderId],
      );
      expect(receipt).toBeTruthy();
      const receiptPayload = readReceiptPayload(receipt!.receipt_payload);
      expect(receiptPayload.items[0].oneOnOneUnits).toBe(0);
      expect(receiptPayload.items[0].sessions).toBe(8);
      expect(receiptPayload.items[0].priceAmount).toBe(800);
    });

    const summary = await apiGet<Record<string, any>>(request, token, '/v1/data-summary', {
      keyword: `${ACCEPTANCE_MARKER} Group Student ${suffix}`,
      pageNum: 1,
      pageSize: 20,
    });
    const row = summary.rows.find((item: Record<string, any>) =>
      String(item.orderId) === String(result.enrollmentOrderId),
    );
    expect(row, 'data summary should include the group registration order').toBeTruthy();
    expect(String(row.classId)).toBe(String(classId));
    expect(Number(row.balanceAmount)).toBeLessThan(0);

    const classStudents = await apiGet<Record<string, any>[]>(
      request,
      token,
      `/v1/enrollment/packages/group-classes/${classId}/students`,
    );
    expect(classStudents.some((student) => String(student.enrollmentId) === String(result.courseArtifacts[0].classEnrollmentId))).toBe(true);

    const classList = await apiGet<PageResult<Record<string, any>>>(request, token, '/v1/enrollment/packages/group-classes', {
      keyword: suffix,
      pageNum: 1,
      pageSize: 20,
    });
    expect(classList.records.some((item) => String(item.classId) === String(classId))).toBe(true);
  });

  test('班课课次数超过可排日期时阻止报名，不静默减少权益', async ({ request }) => {
    const token = await apiLogin(request);
    const suffix = `unscheduled-group-${Date.now().toString(36)}`;
    const teacher = await createTeacherAccountByApi(request, token, suffix);
    const classroom = await createClassroomByApi(request, token, suffix);
    const { product, option, classId } = await createGroupProductAndPlanByApi(
      request,
      token,
      suffix,
      teacher.profile.id,
      classroom.id,
      { withSchedule: false },
    );

    await expect(submitGroupRegistrationByApi(request, token, {
      suffix,
      productId: product.id,
      option,
      classId,
      sessionCount: 8,
    })).rejects.toThrow('GROUP_PLAN_SCHEDULE_INSUFFICIENT');
    await withAcceptanceDb(async (db) => {
      const enrollments = await queryAll<Record<string, any>>(
        db,
        'SELECT id FROM class_enrollment WHERE class_id = ?',
        [classId],
      );
      expect(enrollments).toHaveLength(0);

      const occurrenceRows = await queryAll<Record<string, any>>(
        db,
        'SELECT id FROM lesson_occurrence WHERE class_group_id = ? AND planning_status <> "CANCELLED"',
        [classId],
      );
      expect(occurrenceRows).toHaveLength(0);
    });
  });
});
