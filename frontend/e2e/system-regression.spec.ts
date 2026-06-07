import { expect, test, type Request } from '@playwright/test';
import {
  ADMIN_PASSWORD,
  ADMIN_USERNAME,
  apiDelete,
  apiGet,
  apiLogin,
  apiPost,
  apiPut,
  nextWeekday,
  plusDays,
  todayInNewYork,
  type PageResult,
} from './helpers/api';
import { cleanupE2EData, createDbConnection, queryAll, queryOne } from './helpers/db';

const MARKER = 'E2E_AUTO';

type IdResult = string | number | null;
type ListItem = Record<string, any>;

test.describe.serial('系统自动化回归测试', () => {
  test.beforeAll(async () => {
    const db = await createDbConnection();
    await cleanupE2EData(db, MARKER);
    await db.end();
  });

  test('前端真实登录、菜单页面巡检、账号表单请求参数对齐', async ({ page }) => {
    await page.goto('/login');
    await page.getByPlaceholder('Enter your username').fill(ADMIN_USERNAME);
    await page.getByPlaceholder('Enter password').fill(ADMIN_PASSWORD);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);

    const routes = [
      ['/dashboard', 'Dashboard'],
      ['/base-data/students', 'Students'],
      ['/base-data/teachers', 'Instructors'],
      ['/base-data/classrooms', 'Classrooms'],
      ['/base-data/course-products', 'Course Products'],
      ['/enrollment/orders', 'Enrollment Orders'],
      ['/enrollment/packages', 'Course Packages'],
      ['/schedule/recurring', 'Recurring Schedule'],
      ['/schedule/adjustments', '1v1 Lesson Write-off'],
      ['/schedule/teacher', 'Teacher Calendar'],
      ['/schedule/classrooms', 'Classroom Occupancy'],
      ['/schedule/student', /Student Schedule|My Schedule/],
      ['/finance/pay-rates', 'Teacher Pay Rates'],
      ['/finance/payments', 'Teacher Payments'],
      ['/system/notifications', 'Notification Templates'],
      ['/system/brand', 'Brand Settings'],
      ['/system/accounts', 'Teacher Accounts'],
    ] as const;

    for (const [route, heading] of routes) {
      const failed: string[] = [];
      const onRequestFailed = (request: Request) => {
        const errorText = request.failure()?.errorText ?? '';
        // 页面跳转时浏览器会主动取消上一页仍在飞行中的请求，这不是业务/API 错误。
        if (errorText.includes('ERR_ABORTED')) return;
        failed.push(`${request.method()} ${request.url()} ${errorText}`);
      };
      page.on('requestfailed', onRequestFailed);
      await page.goto(route);
      if (typeof heading === 'string') {
        await expect(page.getByText(heading).first()).toBeVisible();
      } else {
        await expect(page.getByText(heading).first()).toBeVisible();
      }
      expect(failed, `页面 ${route} 存在失败请求`).toEqual([]);
      page.off('requestfailed', onRequestFailed);
    }

    await page.goto('/system/accounts');
    const username = `${MARKER.toLowerCase()}_${Date.now()}_ui_teacher`;
    const requestPromise = page.waitForRequest((request) =>
      request.url().includes('/api/v1/auth/teacher-accounts') && request.method() === 'POST',
    );
    const responsePromise = page.waitForResponse((response) =>
      response.url().includes('/api/v1/auth/teacher-accounts') && response.request().method() === 'POST',
    );

    const teacherSection = page.locator('.settings-panel').filter({ hasText: 'Teacher Accounts' });
    await teacherSection.getByRole('button', { name: 'Add account' }).click();
    const dialog = page.locator('.el-dialog').filter({ hasText: 'New Account' });
    await dialog.getByPlaceholder('e.g. Emily Chen').fill(`${MARKER} UI Teacher`);
    await dialog.getByPlaceholder('(626) 555-0101').fill('626-555-9191');
    await dialog.getByPlaceholder('teacher@example.com').fill(`${username}@example.com`);
    await dialog.getByPlaceholder('Login username').fill(username);
    await dialog.getByPlaceholder('Min 8 characters').fill('Teacher@123456');
    await dialog.getByPlaceholder('Re-enter password').fill('Teacher@123456');
    await dialog.getByRole('button', { name: 'Create' }).click();

    const accountRequest = await requestPromise;
    const payload = accountRequest.postDataJSON();
    expect(payload).toMatchObject({
      teacherName: `${MARKER} UI Teacher`,
      mobile: '626-555-9191',
      email: `${username}@example.com`,
      username,
      password: 'Teacher@123456',
    });
    const accountResponse = await responsePromise;
    expect(accountResponse.ok(), await accountResponse.text()).toBeTruthy();
    await expect(page.getByText(username).first()).toBeVisible();
  });

  test('完整业务链路：主数据、报名支付、排课确认、课时扣减、班课、通知、财务和数据库一致性', async ({ request }) => {
    const token = await apiLogin(request);
    const suffix = Date.now().toString(36);
    const today = todayInNewYork();
    const monday = nextWeekday(today, 1);
    const expiry = plusDays(today, 90);

    const teacherId = await apiPost<IdResult>(request, token, '/v1/base/teachers', {
      teacherName: `${MARKER} Teacher ${suffix}`,
      mobile: '626-555-0101',
      email: `${MARKER.toLowerCase()}.${suffix}.teacher@example.com`,
      subjectTags: ['Math'],
      status: 'ACTIVE',
      notes: MARKER,
    });
    const studentId = await apiPost<IdResult>(request, token, '/v1/base/students', {
      studentName: `${MARKER} Student ${suffix}`,
      gender: 'FEMALE',
      guardianName: `${MARKER} Guardian`,
      guardianPhone: '626-555-0102',
      guardianEmail: `${MARKER.toLowerCase()}.${suffix}.guardian@example.com`,
      guardianRelationType: 'MOTHER',
      status: 'ACTIVE',
      notes: MARKER,
    });
    const classroomId = await apiPost<IdResult>(request, token, '/v1/base/classrooms', {
      roomName: `${MARKER} Room ${suffix}`,
      campus: 'Main Campus',
      capacity: 8,
      status: 'ACTIVE',
      notes: MARKER,
    });
    const oneOnOneProductId = await apiPost<IdResult>(request, token, '/v1/base/course-products', {
      productCode: '',
      productName: `${MARKER} 1v1 Product ${suffix}`,
      subjectName: 'Math',
      teachingMode: 'ONE_ON_ONE',
      defaultTotalUnits: 8,
      defaultLessonUnits: 2,
      listPrice: 800,
      status: 'ACTIVE',
      notes: MARKER,
    });
    const groupProductId = await apiPost<IdResult>(request, token, '/v1/base/course-products', {
      productCode: '',
      productName: `${MARKER} Group Product ${suffix}`,
      subjectName: 'Math',
      teachingMode: 'GROUP',
      expiryDurationDays: 90,
      defaultClassroomId: classroomId,
      defaultTeacherId: teacherId,
      listPrice: 500,
      status: 'ACTIVE',
      notes: MARKER,
      slots: [{ weekday: 1, startTime: '11:00', endTime: '12:00' }],
    });

    await apiPost<IdResult>(request, token, '/v1/base/discount-presets', {
      presetName: `${MARKER} Discount ${suffix}`,
      discountType: 'FIXED',
      discountValue: 20,
      sortOrder: 99,
      status: 'ACTIVE',
    });
    const paymentMethodId = await apiPost<IdResult>(request, token, '/v1/enrollment/payment-methods', {
      methodCode: `${MARKER}_${suffix}`.toUpperCase(),
      methodName: `${MARKER} Cash ${suffix}`,
      sortOrder: 99,
      status: 'ACTIVE',
    });
    expect(paymentMethodId).toBeTruthy();

    const enrollmentResult = await apiPost<Record<string, any>>(request, token, '/v1/enrollment/orders', {
      studentId,
      courseProductId: oneOnOneProductId,
      primaryTeacherId: teacherId,
      handledByName: `${MARKER} Operator`,
      startDate: monday,
      originalAmount: 800,
      discountAmount: 100,
      receivedAmount: 0,
      issueReceipt: false,
      totalUnits: 8,
      defaultLessonUnits: 2,
      defaultClassroomId: classroomId,
      remark: MARKER,
      slots: [{ weekday: 1, startTime: '09:00', endTime: '10:00', plannedUnits: 2 }],
    });
    const orderId = enrollmentResult.enrollmentOrderId;
    const packageId = enrollmentResult.packageIds[0];
    expect(orderId).toBeTruthy();
    expect(packageId).toBeTruthy();
    expect(enrollmentResult.generatedOccurrenceCount).toBeGreaterThan(0);

    await apiPost<unknown>(request, token, '/v1/enrollment/payments', {
      enrollmentOrderId: orderId,
      splits: [{ paymentMethod: `${MARKER} Cash ${suffix}`, amount: 700 }],
      handledByName: `${MARKER} Operator`,
      remark: MARKER,
    });

    const occurrences = await apiGet<PageResult<ListItem>>(request, token, '/v1/schedule/occurrences', {
      packageId,
      planningStatus: 'SCHEDULED',
      pageNum: 1,
      pageSize: 20,
    });
    expect(occurrences.records.length).toBeGreaterThan(0);
    const occurrence = occurrences.records[0];
    const confirmResult = await apiPost<Record<string, any>>(request, token, '/v1/lessons/records', {
      occurrenceId: occurrence.id,
      resultStatus: 'COMPLETED',
      actualTeacherId: teacherId,
      actualClassroomId: classroomId,
      actualStartAt: `${monday}T09:00:00`,
      actualEndAt: `${monday}T10:00:00`,
      actualUnits: 2,
      notes: MARKER,
    });
    expect(confirmResult.lessonRecordId).toBeTruthy();
    expect(confirmResult.remainingUnits).toBe(7);

    await apiPost<IdResult>(request, token, '/v1/finance/pay-rates', {
      teacherId,
      courseProductId: oneOnOneProductId,
      hourlyRate: 60,
      notes: MARKER,
    });
    const unpaid = await apiGet<ListItem[]>(request, token, '/v1/finance/payments/unpaid-lessons', {
      teacherId,
      startDate: today,
      endDate: plusDays(today, 120),
    });
    expect(unpaid.some((lesson) => String(lesson.lessonRecordId) === String(confirmResult.lessonRecordId))).toBeTruthy();
    const paymentId = await apiPost<IdResult>(request, token, '/v1/finance/payments', {
      teacherId,
      periodStart: today,
      periodEnd: plusDays(today, 120),
      lessonRecordIds: [confirmResult.lessonRecordId],
      notes: MARKER,
    });
    await apiPost<string>(request, token, `/v1/finance/payments/${paymentId}/confirm`);

    await apiPost<unknown>(request, token, '/v1/enrollment/orders/class-enroll', {
      studentId,
      productId: groupProductId,
      enrollmentDate: today,
      expiryDate: expiry,
      originalAmount: 500,
      discountAmount: 0,
      receivedAmount: 500,
      handledByName: `${MARKER} Operator`,
      remark: MARKER,
    });
    const classGroups = await apiGet<ListItem[]>(request, token, '/v1/classes');
    const classGroup = classGroups.find((item) => String(item.productId) === String(groupProductId) || item.className?.includes(`${MARKER} Group Product ${suffix}`));
    expect(classGroup).toBeTruthy();
    const classOccurrences = await apiGet<ListItem[]>(request, token, `/v1/classes/${classGroup!.id}/occurrences`);
    expect(classOccurrences.length).toBeGreaterThan(0);
    const classConfirm = await apiPost<Record<string, any>>(request, token, `/v1/classes/${classGroup!.id}/occurrences/${classOccurrences[0].id}/confirm`, {
      actualTeacherId: teacherId,
      actualClassroomId: classroomId,
      actualUnits: 2,
      notes: MARKER,
    });
    expect(classConfirm.confirmedCount).toBeGreaterThanOrEqual(1);

    const notificationConfig = await apiGet<Record<string, any>>(request, token, '/v1/system/notification-config');
    await apiPut<Record<string, any>>(request, token, '/v1/system/notification-config', {
      enabled: false,
      fromAddress: `${MARKER.toLowerCase()}@example.com`,
      fromName: 'JIN BEST TUTORING',
      authCode: '',
      strictAllowlist: false,
      allowedRecipients: JSON.stringify([`${MARKER.toLowerCase()}.${suffix}@example.com`]),
      lowBalanceThreshold: 2,
      lowBalanceRecipients: JSON.stringify([`${MARKER.toLowerCase()}.low.${suffix}@example.com`]),
      smtpHost: 'smtp.example.com',
      smtpPort: 587,
    });
    await apiPut<Record<string, any>>(request, token, '/v1/system/notification-config', notificationConfig);
    const broadcast = await apiPost<Record<string, any>>(request, token, '/v1/system/notifications/broadcast', {
      recipientPreset: 'CUSTOM',
      recipients: [`${MARKER.toLowerCase()}.${suffix}@example.com`],
      subject: `${MARKER} Broadcast ${suffix}`,
      body: `${MARKER} broadcast body`,
    });
    expect(broadcast.queuedCount).toBeGreaterThanOrEqual(1);

    const db = await createDbConnection();
    try {
      const snapshot = await queryOne<{ total_units: number; consumed_units: number; remaining_units: number }>(
        db,
        'SELECT total_units, consumed_units, remaining_units FROM package_balance_snapshot WHERE package_id=?',
        [packageId],
      );
      expect(snapshot).toMatchObject({ total_units: 16, consumed_units: 2, remaining_units: 14 });

      const ledger = await queryOne<{ change_units: number; balance_after_units: number; reason_code: string }>(
        db,
        'SELECT change_units, balance_after_units, reason_code FROM hour_ledger WHERE lesson_record_id=?',
        [confirmResult.lessonRecordId],
      );
      expect(ledger).toMatchObject({ change_units: -2, balance_after_units: 14, reason_code: 'COMPLETED' });

      const packageRow = await queryOne<{ consumed_units: number; remaining_units: number; package_status: string }>(
        db,
        'SELECT consumed_units, remaining_units, package_status FROM course_package WHERE id=?',
        [packageId],
      );
      expect(packageRow).toMatchObject({ consumed_units: 2, remaining_units: 14, package_status: 'ACTIVE' });

      const teacherPayment = await queryOne<{ status: string; total_amount: string }>(
        db,
        'SELECT status, total_amount FROM teacher_payment WHERE id=?',
        [paymentId],
      );
      expect(teacherPayment?.status).toBe('PAID');
      expect(Number(teacherPayment?.total_amount)).toBeGreaterThan(0);

      const classRecords = await queryAll<{ id: string }>(
        db,
        'SELECT id FROM lesson_record WHERE occurrence_id=? AND record_source=?',
        [classOccurrences[0].id, 'CLASS_CONFIRM'],
      );
      expect(classRecords.length).toBeGreaterThanOrEqual(1);

      const notificationRows = await queryAll<{ id: string }>(
        db,
        'SELECT id FROM notification_log WHERE request_payload LIKE ? OR error_message LIKE ?',
        [`%${suffix}%`, `%${suffix}%`],
      );
      expect(notificationRows.length).toBeGreaterThanOrEqual(1);
    } finally {
      await db.end();
    }

    await apiDelete(request, token, `/v1/enrollment/payment-methods/${paymentMethodId}`);
  });

  test('复杂交叉业务：课包/班课创建、报名支付、报名取消、调课补课、确认请假取消与课时计算', async ({ request }) => {
    const token = await apiLogin(request);
    const suffix = Date.now().toString(36);
    const today = todayInNewYork();
    const monday = nextWeekday(today, 1);
    const expiry = plusDays(today, 90);

    const teacherId = await apiPost<IdResult>(request, token, '/v1/base/teachers', {
      teacherName: `${MARKER} Flow Teacher A ${suffix}`,
      mobile: '626-555-0201',
      email: `${MARKER.toLowerCase()}.${suffix}.teacher.a@example.com`,
      subjectTags: ['Math'],
      status: 'ACTIVE',
      notes: MARKER,
    });
    const substituteTeacherId = await apiPost<IdResult>(request, token, '/v1/base/teachers', {
      teacherName: `${MARKER} Flow Teacher B ${suffix}`,
      mobile: '626-555-0202',
      email: `${MARKER.toLowerCase()}.${suffix}.teacher.b@example.com`,
      subjectTags: ['Math'],
      status: 'ACTIVE',
      notes: MARKER,
    });
    const studentId = await apiPost<IdResult>(request, token, '/v1/base/students', {
      studentName: `${MARKER} Flow Student A ${suffix}`,
      gender: 'FEMALE',
      guardianName: `${MARKER} Guardian A`,
      guardianPhone: '626-555-0203',
      guardianEmail: `${MARKER.toLowerCase()}.${suffix}.guardian.a@example.com`,
      guardianRelationType: 'MOTHER',
      status: 'ACTIVE',
      notes: MARKER,
    });
    const secondStudentId = await apiPost<IdResult>(request, token, '/v1/base/students', {
      studentName: `${MARKER} Flow Student B ${suffix}`,
      gender: 'MALE',
      guardianName: `${MARKER} Guardian B`,
      guardianPhone: '626-555-0204',
      guardianEmail: `${MARKER.toLowerCase()}.${suffix}.guardian.b@example.com`,
      guardianRelationType: 'FATHER',
      status: 'ACTIVE',
      notes: MARKER,
    });
    const classroomId = await apiPost<IdResult>(request, token, '/v1/base/classrooms', {
      roomName: `${MARKER} Flow Room A ${suffix}`,
      campus: 'Main Campus',
      capacity: 8,
      status: 'ACTIVE',
      notes: MARKER,
    });
    const overflowClassroomId = await apiPost<IdResult>(request, token, '/v1/base/classrooms', {
      roomName: `${MARKER} Flow Room B ${suffix}`,
      campus: 'Main Campus',
      capacity: 8,
      status: 'ACTIVE',
      notes: MARKER,
    });

    const oneOnOneProductId = await apiPost<IdResult>(request, token, '/v1/base/course-products', {
      productCode: '',
      productName: `${MARKER} Complex 1v1 Product ${suffix}`,
      subjectName: 'Math',
      teachingMode: 'ONE_ON_ONE',
      defaultTotalUnits: 8,
      defaultLessonUnits: 2,
      listPrice: 800,
      status: 'ACTIVE',
      notes: MARKER,
    });
    const groupProductId = await apiPost<IdResult>(request, token, '/v1/base/course-products', {
      productCode: '',
      productName: `${MARKER} Complex Group Product ${suffix}`,
      subjectName: 'Math',
      teachingMode: 'GROUP',
      expiryDurationDays: 30,
      defaultClassroomId: classroomId,
      defaultTeacherId: teacherId,
      listPrice: 500,
      status: 'ACTIVE',
      notes: MARKER,
      slots: [{ weekday: 4, startTime: '10:00', endTime: '11:00' }],
    });
    const paymentMethodId = await apiPost<IdResult>(request, token, '/v1/enrollment/payment-methods', {
      methodCode: `${MARKER}_FLOW_${suffix}`.toUpperCase(),
      methodName: `${MARKER} Flow Method ${suffix}`,
      sortOrder: 88,
      status: 'ACTIVE',
    });

    const enrollmentResult = await apiPost<Record<string, any>>(request, token, '/v1/enrollment/orders', {
      studentId,
      courseProductId: oneOnOneProductId,
      primaryTeacherId: teacherId,
      handledByName: `${MARKER} Flow Operator ${suffix}`,
      startDate: monday,
      originalAmount: 800,
      discountAmount: 100,
      receivedAmount: 0,
      issueReceipt: false,
      totalUnits: 8,
      defaultLessonUnits: 2,
      defaultClassroomId: classroomId,
      remark: `${MARKER} complex active order ${suffix}`,
      slots: [{ weekday: 1, startTime: '09:00', endTime: '10:00', plannedUnits: 2 }],
    });
    const orderId = enrollmentResult.enrollmentOrderId;
    const packageId = enrollmentResult.packageIds[0];
    expect(enrollmentResult.generatedOccurrenceCount).toBeGreaterThanOrEqual(8);

    const createdPackage = await apiGet<Record<string, any>>(request, token, `/v1/enrollment/packages/${packageId}`);
    expect(createdPackage).toMatchObject({ id: packageId, totalUnits: 8, remainingUnits: 8, packageStatus: 'ACTIVE' });

    await apiPost<unknown>(request, token, '/v1/enrollment/payments', {
      enrollmentOrderId: orderId,
      splits: [{ paymentMethod: `${MARKER} Flow Method ${suffix}`, amount: 300 }],
      handledByName: `${MARKER} Flow Operator ${suffix}`,
      remark: `${MARKER} partial payment ${suffix}`,
    });
    const partialOrder = await apiGet<Record<string, any>>(request, token, `/v1/enrollment/orders/${orderId}`);
    expect(partialOrder.paymentStatus).toBe('PARTIAL');
    expect(partialOrder.receivedAmount).toBe(300);

    await apiPost<unknown>(request, token, '/v1/enrollment/payments', {
      enrollmentOrderId: orderId,
      splits: [{ paymentMethod: `${MARKER} Flow Method ${suffix}`, amount: 400 }],
      handledByName: `${MARKER} Flow Operator ${suffix}`,
      remark: `${MARKER} final payment ${suffix}`,
    });
    const paidOrder = await apiGet<Record<string, any>>(request, token, `/v1/enrollment/orders/${orderId}`);
    expect(paidOrder.paymentStatus).toBe('PAID');
    expect(paidOrder.receivedAmount).toBe(700);

    const scheduleRules = await apiGet<PageResult<ListItem>>(request, token, '/v1/schedule/rules', {
      packageId,
      pageNum: 1,
      pageSize: 10,
    });
    expect(scheduleRules.records.length).toBe(1);
    const scheduleRule = scheduleRules.records[0];
    const scheduleRuleDetail = await apiGet<Record<string, any>>(request, token, `/v1/schedule/rules/${scheduleRule.id}`);
    expect(scheduleRuleDetail.slots).toHaveLength(1);
    expect(scheduleRuleDetail.generatedOccurrenceCount).toBeGreaterThanOrEqual(8);

    const occurrences = await apiGet<PageResult<ListItem>>(request, token, '/v1/schedule/occurrences', {
      packageId,
      planningStatus: 'SCHEDULED',
      pageNum: 1,
      pageSize: 20,
    });
    expect(occurrences.records.length).toBeGreaterThanOrEqual(4);
    const [firstOccurrence, leaveOccurrence, noShowOccurrence, cancelSourceOccurrence] = occurrences.records;

    const rescheduleResult = await apiPost<Record<string, any>>(request, token, '/v1/schedule/adjustments', {
      occurrenceId: firstOccurrence.id,
      adjustmentType: 'RESCHEDULE',
      plannedTeacherId: substituteTeacherId,
      plannedClassroomId: overflowClassroomId,
      plannedStartAt: `${plusDays(monday, 1)}T14:00:00`,
      plannedEndAt: `${plusDays(monday, 1)}T15:00:00`,
      plannedUnits: 2,
      reason: `${MARKER} reschedule ${suffix}`,
    });
    expect(rescheduleResult.adjustmentType).toBe('RESCHEDULE');

    const changeTeacherResult = await apiPost<Record<string, any>>(request, token, '/v1/schedule/adjustments', {
      occurrenceId: leaveOccurrence.id,
      adjustmentType: 'CHANGE_TEACHER',
      plannedTeacherId: substituteTeacherId,
      reason: `${MARKER} substitute teacher ${suffix}`,
    });
    expect(changeTeacherResult.adjustmentType).toBe('CHANGE_TEACHER');

    const changeRoomResult = await apiPost<Record<string, any>>(request, token, '/v1/schedule/adjustments', {
      occurrenceId: noShowOccurrence.id,
      adjustmentType: 'CHANGE_ROOM',
      plannedClassroomId: overflowClassroomId,
      reason: `${MARKER} change room ${suffix}`,
    });
    expect(changeRoomResult.adjustmentType).toBe('CHANGE_ROOM');

    const explicitMakeupResult = await apiPost<Record<string, any>>(request, token, '/v1/schedule/adjustments', {
      occurrenceId: cancelSourceOccurrence.id,
      adjustmentType: 'MAKEUP_CREATE',
      plannedTeacherId: teacherId,
      plannedClassroomId: classroomId,
      plannedStartAt: `${plusDays(monday, 1)}T15:00:00`,
      plannedEndAt: `${plusDays(monday, 1)}T16:00:00`,
      plannedUnits: 2,
      reason: `${MARKER} explicit makeup ${suffix}`,
    });
    expect(explicitMakeupResult.createdOccurrenceId).toBeTruthy();

    const completed = await apiPost<Record<string, any>>(request, token, '/v1/lessons/records', {
      occurrenceId: firstOccurrence.id,
      resultStatus: 'COMPLETED',
      actualTeacherId: substituteTeacherId,
      actualClassroomId: overflowClassroomId,
      actualStartAt: `${plusDays(monday, 1)}T14:00:00`,
      actualEndAt: `${plusDays(monday, 1)}T15:00:00`,
      actualUnits: 2,
      notes: `${MARKER} completed ${suffix}`,
    });
    expect(completed).toMatchObject({ deductedUnits: 1, remainingUnits: 7, packageCompleted: false });

    const formalLeave = await apiPost<Record<string, any>>(request, token, '/v1/lessons/records', {
      occurrenceId: leaveOccurrence.id,
      resultStatus: 'FORMAL_LEAVE',
      notes: `${MARKER} formal leave ${suffix}`,
    });
    expect(formalLeave).toMatchObject({ deductedUnits: 0, remainingUnits: 7, packageCompleted: false });

    const noShow = await apiPost<Record<string, any>>(request, token, '/v1/lessons/records', {
      occurrenceId: noShowOccurrence.id,
      resultStatus: 'NO_SHOW',
      notes: `${MARKER} no show ${suffix}`,
    });
    expect(noShow).toMatchObject({ deductedUnits: 1, remainingUnits: 6, packageCompleted: false });

    const temporaryCancel = await apiPost<Record<string, any>>(request, token, '/v1/lessons/records', {
      occurrenceId: cancelSourceOccurrence.id,
      resultStatus: 'TEMP_CANCEL',
      notes: `${MARKER} temporary cancellation ${suffix}`,
    });
    expect(temporaryCancel).toMatchObject({ deductedUnits: 0, remainingUnits: 6, packageCompleted: false });

    const makeupCompleted = await apiPost<Record<string, any>>(request, token, '/v1/lessons/records', {
      occurrenceId: explicitMakeupResult.createdOccurrenceId,
      resultStatus: 'MAKEUP_COMPLETED',
      actualTeacherId: teacherId,
      actualClassroomId: classroomId,
      actualStartAt: `${plusDays(monday, 1)}T15:00:00`,
      actualEndAt: `${plusDays(monday, 1)}T16:00:00`,
      actualUnits: 2,
      notes: `${MARKER} makeup completed ${suffix}`,
    });
    expect(makeupCompleted).toMatchObject({ deductedUnits: 1, remainingUnits: 5, packageCompleted: false });

    const history = await apiGet<PageResult<ListItem>>(request, token, '/v1/lessons/records', {
      packageId,
      pageNum: 1,
      pageSize: 20,
    });
    expect(history.records.map((item) => item.resultStatus)).toEqual(expect.arrayContaining(['COMPLETED', 'FORMAL_LEAVE', 'NO_SHOW', 'TEMP_CANCEL', 'MAKEUP_COMPLETED']));
    const ledgerRows = await apiGet<PageResult<ListItem>>(request, token, '/v1/lessons/ledger', {
      packageId,
      pageNum: 1,
      pageSize: 20,
    });
    expect(ledgerRows.records.map((item) => item.reasonCode)).toEqual(expect.arrayContaining(['COMPLETED', 'FORMAL_LEAVE', 'NO_SHOW', 'TEMP_CANCEL', 'MAKEUP_COMPLETED']));

    const cancelEnrollmentResult = await apiPost<Record<string, any>>(request, token, '/v1/enrollment/orders', {
      studentId: secondStudentId,
      courseProductId: oneOnOneProductId,
      primaryTeacherId: teacherId,
      handledByName: `${MARKER} Cancel Operator ${suffix}`,
      startDate: plusDays(monday, 2),
      originalAmount: 200,
      discountAmount: 0,
      receivedAmount: 0,
      issueReceipt: false,
      totalUnits: 2,
      defaultLessonUnits: 2,
      defaultClassroomId: classroomId,
      remark: `${MARKER} cancel order ${suffix}`,
      slots: [{ weekday: 3, startTime: '18:00', endTime: '19:00', plannedUnits: 2 }],
    });
    await apiPost<unknown>(request, token, `/v1/enrollment/orders/${cancelEnrollmentResult.enrollmentOrderId}/cancel`);
    const cancelledOrder = await apiGet<Record<string, any>>(request, token, `/v1/enrollment/orders/${cancelEnrollmentResult.enrollmentOrderId}`);
    expect(cancelledOrder.orderStatus).toBe('CANCELLED');

    await apiPost<unknown>(request, token, '/v1/enrollment/orders/class-enroll', {
      studentId,
      productId: groupProductId,
      enrollmentDate: today,
      expiryDate: plusDays(today, 20),
      originalAmount: 500,
      discountAmount: 50,
      receivedAmount: 450,
      handledByName: `${MARKER} Class Operator ${suffix}`,
      remark: `${MARKER} class enrollment ${suffix}`,
    });
    const classGroups = await apiGet<ListItem[]>(request, token, '/v1/classes');
    const classGroup = classGroups.find((item) => String(item.productId) === String(groupProductId) || item.className?.includes(`${MARKER} Complex Group Product ${suffix}`));
    expect(classGroup).toBeTruthy();
    const classDetail = await apiGet<Record<string, any>>(request, token, `/v1/classes/${classGroup!.id}`);
    expect(classDetail.id).toBe(classGroup!.id);

    const directEnroll = await apiPost<Record<string, any>>(request, token, `/v1/classes/${classGroup!.id}/enroll`, {
      studentId: secondStudentId,
      enrollmentDate: today,
      expiryDate: plusDays(today, 20),
    });
    expect(directEnroll.studentId).toBe(secondStudentId);
    const classStudents = await apiGet<ListItem[]>(request, token, `/v1/classes/${classGroup!.id}/students`);
    expect(classStudents.filter((item) => item.enrollmentStatus === 'ACTIVE')).toHaveLength(2);

    const classOccurrences = await apiGet<ListItem[]>(request, token, `/v1/classes/${classGroup!.id}/occurrences`);
    expect(classOccurrences.length).toBeGreaterThan(0);
    const classConfirm = await apiPost<Record<string, any>>(request, token, `/v1/classes/${classGroup!.id}/occurrences/${classOccurrences[0].id}/confirm`, {
      actualTeacherId: teacherId,
      actualClassroomId: classroomId,
      actualUnits: 2,
      notes: `${MARKER} class confirmed ${suffix}`,
    });
    expect(classConfirm.confirmedCount).toBe(2);

    const reminders = await apiPost<Record<string, any>>(request, token, `/v1/classes/${classGroup!.id}/send-expiry-reminders`);
    expect(reminders.sentCount).toBeGreaterThanOrEqual(2);
    const studentClasses = await apiGet<ListItem[]>(request, token, `/v1/classes/enrolled/${studentId}`);
    expect(studentClasses.some((item) => String(item.id) === String(classGroup!.id))).toBeTruthy();
    const teacherClasses = await apiGet<ListItem[]>(request, token, `/v1/classes/teacher/${teacherId}`);
    expect(teacherClasses.some((item) => String(item.id) === String(classGroup!.id))).toBeTruthy();
    const classroomClasses = await apiGet<ListItem[]>(request, token, `/v1/classes/classroom/${classroomId}`);
    expect(classroomClasses.some((item) => String(item.id) === String(classGroup!.id))).toBeTruthy();

    const db = await createDbConnection();
    try {
      const packageSnapshot = await queryOne<{ total_units: number; consumed_units: number; remaining_units: number }>(
        db,
        'SELECT total_units, consumed_units, remaining_units FROM package_balance_snapshot WHERE package_id=?',
        [packageId],
      );
      expect(packageSnapshot).toMatchObject({ total_units: 16, consumed_units: 6, remaining_units: 10 });

      const statusLedgers = await queryAll<{ reason_code: string; change_units: number; balance_after_units: number }>(
        db,
        'SELECT reason_code, change_units, balance_after_units FROM hour_ledger WHERE package_id=? ORDER BY operated_at ASC',
        [packageId],
      );
      expect(statusLedgers.map((item) => item.reason_code)).toEqual(expect.arrayContaining(['COMPLETED', 'FORMAL_LEAVE', 'NO_SHOW', 'TEMP_CANCEL', 'MAKEUP_COMPLETED']));
      expect(statusLedgers.filter((item) => item.change_units === 0).length).toBeGreaterThanOrEqual(2);
      expect(statusLedgers.at(-1)?.balance_after_units).toBe(10);

      const adjustmentLogs = await queryAll<{ adjustment_type: string }>(
        db,
        'SELECT adjustment_type FROM lesson_adjustment_log WHERE occurrence_id IN (SELECT id FROM lesson_occurrence WHERE package_id=?)',
        [packageId],
      );
      expect(adjustmentLogs.map((item) => item.adjustment_type)).toEqual(expect.arrayContaining(['RESCHEDULE', 'CHANGE_TEACHER', 'CHANGE_ROOM', 'MAKEUP_CREATE']));

      const cancelledPackage = await queryOne<{ package_status: string }>(
        db,
        'SELECT package_status FROM course_package WHERE id=?',
        [cancelEnrollmentResult.packageIds[0]],
      );
      expect(cancelledPackage?.package_status).toBe('CLOSED');
      const cancelledOccurrences = await queryAll<{ planning_status: string }>(
        db,
        'SELECT planning_status FROM lesson_occurrence WHERE package_id=?',
        [cancelEnrollmentResult.packageIds[0]],
      );
      expect(cancelledOccurrences.length).toBeGreaterThan(0);
      expect(cancelledOccurrences.every((item) => item.planning_status === 'CANCELLED')).toBeTruthy();

      const classRecords = await queryAll<{ id: string; should_deduct: number; deducted_units: number }>(
        db,
        'SELECT id, should_deduct, deducted_units FROM lesson_record WHERE occurrence_id=? AND record_source=?',
        [classOccurrences[0].id, 'CLASS_CONFIRM'],
      );
      expect(classRecords).toHaveLength(2);
      expect(classRecords.every((item) => item.should_deduct === 0 && item.deducted_units === 0)).toBeTruthy();

      const classOrder = await queryOne<{ id: string }>(
        db,
        'SELECT id FROM enrollment_order WHERE class_id=? AND student_id=? AND handled_by_name=?',
        [classGroup!.id, studentId, `${MARKER} Class Operator ${suffix}`],
      );
      expect(classOrder?.id).toBeTruthy();
      await apiPost<unknown>(request, token, `/v1/enrollment/orders/${classOrder!.id}/cancel`);
      const withdrawn = await queryOne<{ status: string }>(
        db,
        'SELECT status FROM class_enrollment WHERE class_id=? AND student_id=? ORDER BY created_at DESC LIMIT 1',
        [classGroup!.id, studentId],
      );
      expect(withdrawn?.status).toBe('WITHDRAWN');
    } finally {
      await db.end();
    }

    await apiPut<unknown>(request, token, `/v1/enrollment/packages/${packageId}`, {
      packageName: `${MARKER} Complex 1v1 Package Renamed ${suffix}`,
      startDate: today,
      estimatedEndDate: expiry,
      remark: `${MARKER} package updated ${suffix}`,
    });
    await apiPost<unknown>(request, token, `/v1/enrollment/packages/${packageId}/close`);
    const closedPackage = await apiGet<Record<string, any>>(request, token, `/v1/enrollment/packages/${packageId}`);
    expect(closedPackage.packageStatus).toBe('CLOSED');

    await apiDelete(request, token, `/v1/classes/${classGroup!.id}`);
    const activeClassesAfterDelete = await apiGet<ListItem[]>(request, token, '/v1/classes');
    expect(activeClassesAfterDelete.some((item) => String(item.id) === String(classGroup!.id))).toBeFalsy();
    await apiDelete(request, token, `/v1/enrollment/payment-methods/${paymentMethodId}`);
  });
});
