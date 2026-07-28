import { expect, type APIRequestContext, type Locator, type Page } from '@playwright/test';
import type { Connection } from 'mysql2/promise';

import { apiGet, apiPost, apiPut, type PageResult, todayInNewYork } from '../helpers/api';
import { cleanupBusinessFlowE2EData, createDbConnection, queryAll, queryOne } from '../helpers/db';

export type EntityId = string | number;
export type ApiRecord = Record<string, any>;

export const ACCEPTANCE_MARKER = 'E2E_ACCEPTANCE';

export async function withAcceptanceDb<T>(action: (db: Connection) => Promise<T>): Promise<T> {
  const db = await createDbConnection();
  try {
    return await action(db);
  } finally {
    await db.end();
  }
}

export async function cleanupAcceptanceData(): Promise<void> {
  await withAcceptanceDb((db) => cleanupBusinessFlowE2EData(db, ACCEPTANCE_MARKER));
}

export async function ensureTransferPaymentMethod(request: APIRequestContext, token: string): Promise<void> {
  const methods = await apiGet<ApiRecord[]>(request, token, '/v1/enrollment/payment-methods');
  const existing = methods.find((item) => item.methodCode === 'TRANSFER');
  if (!existing) {
    await apiPost<string>(request, token, '/v1/enrollment/payment-methods', {
      methodCode: 'TRANSFER',
      methodName: 'Transfer',
      sortOrder: 30,
      status: 'ACTIVE',
    });
    return;
  }
  if (existing.status !== 'ACTIVE') {
    await apiPut<void>(request, token, `/v1/enrollment/payment-methods/${existing.id}`, {
      methodCode: 'TRANSFER',
      methodName: 'Transfer',
      sortOrder: existing.sortOrder ?? 30,
      status: 'ACTIVE',
    });
  }
}

export async function createClassroomByApi(
  request: APIRequestContext,
  token: string,
  suffix: string,
): Promise<ApiRecord> {
  const roomName = `${ACCEPTANCE_MARKER} Room ${suffix}`;
  const id = await apiPost<string>(request, token, '/v1/base/classrooms', {
    roomName,
    campus: 'Online',
    capacity: 8,
    status: 'ACTIVE',
    notes: ACCEPTANCE_MARKER,
  });
  return { id, roomName };
}

export async function createTeacherAccountByApi(
  request: APIRequestContext,
  token: string,
  suffix: string,
): Promise<{ username: string; password: string; profile: ApiRecord }> {
  const teacherName = `${ACCEPTANCE_MARKER} Teacher ${suffix}`;
  const username = `${ACCEPTANCE_MARKER}_${suffix}_teacher`.toLowerCase().replace(/[^a-z0-9_]/g, '_');
  const password = 'Test@123456';
  await apiPost<string>(request, token, '/v1/auth/teacher-accounts', {
    teacherName,
    username,
    password,
    displayName: teacherName,
    mobile: '626-555-3301',
    email: `${username}@example.com`,
    subjectTags: ['Math'],
    status: 'ACTIVE',
    remark: ACCEPTANCE_MARKER,
  });
  const teachers = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/base/teachers', {
    keyword: teacherName,
    pageNum: 1,
    pageSize: 10,
  });
  expect(teachers.records[0], 'teacher account creation should create a linked teacher profile').toBeTruthy();
  return { username, password, profile: teachers.records[0] };
}

export async function createOneOnOneProductAndPriceByApi(
  request: APIRequestContext,
  token: string,
  suffix: string,
  priceAmount = 900,
): Promise<{ product: ApiRecord; option: ApiRecord }> {
  const productName = `${ACCEPTANCE_MARKER} 1v1 ${suffix}`;
  const productId = await apiPost<string>(request, token, '/v1/base/course-products', {
    productCode: '',
    productName,
    courseType: 'ONE_ON_ONE',
    registerClass: '1-on-1',
    subjectName: 'Math',
    teachingMode: 'ONE_ON_ONE',
    defaultTotalUnits: 80,
    defaultLessonUnits: 4,
    listPrice: priceAmount,
    status: 'ACTIVE',
    notes: ACCEPTANCE_MARKER,
  });
  const optionCode = `ACC_O2O_${suffix}`.toUpperCase().replace(/[^A-Z0-9_]/g, '_');
  const optionId = await apiPost<string>(request, token, '/v1/base/course-price-options', {
    optionCode,
    courseType: 'ONE_ON_ONE',
    registerClass: '1-on-1',
    gradeBand: 'G1-G5',
    periodLabel: '40 hours',
    durationLabel: '40 hours',
    detailText: 'G1-G5 | 40 hours',
    oneOnOneHours: 40,
    priceAmount,
    currencyCode: 'USD',
    courseProductId: productId,
    status: 'ACTIVE',
    notes: ACCEPTANCE_MARKER,
  });
  const option = await findCoursePriceOption(request, token, productId, optionId);
  return { product: { id: productId, productName }, option };
}

export async function createGroupProductAndPriceByApi(
  request: APIRequestContext,
  token: string,
  suffix: string,
  teacherId: EntityId,
  classroomId: EntityId,
): Promise<{ product: ApiRecord; option: ApiRecord; classId: EntityId }> {
  const productName = `${ACCEPTANCE_MARKER} Saturday ${suffix}`;
  const today = todayInNewYork();
  const weekday = weekdayForApi(today);
  const productId = await apiPost<string>(request, token, '/v1/base/course-products', {
    productCode: '',
    productName,
    courseType: 'SATURDAY',
    registerClass: 'Saturday Class',
    subjectName: 'Math',
    teachingMode: 'GROUP',
    billingType: 'PER_SESSION',
    startDate: today,
    totalSessions: 8,
    defaultTeacherId: teacherId,
    defaultClassroomId: classroomId,
    listPrice: 800,
    status: 'ACTIVE',
    notes: ACCEPTANCE_MARKER,
  });
  const optionCode = `ACC_GROUP_${suffix}`.toUpperCase().replace(/[^A-Z0-9_]/g, '_');
  const optionId = await apiPost<string>(request, token, '/v1/base/course-price-options', {
    optionCode,
    courseType: 'SATURDAY',
    registerClass: 'Saturday Class',
    gradeBand: 'G3-G6',
    detailText: 'G3-G6 | 8 sessions | 9am-10am',
    priceAmount: 100,
    currencyCode: 'USD',
    courseProductId: productId,
    groupTeacherId: teacherId,
    groupClassroomId: classroomId,
    groupScheduleSlots: [{ weekday, startTime: '09:00', endTime: '10:00' }],
    groupBillingType: 'PER_SESSION',
    groupStartDate: today,
    groupTotalSessions: 8,
    status: 'ACTIVE',
    notes: ACCEPTANCE_MARKER,
  });
  const option = await findCoursePriceOption(request, token, productId, optionId);
  expect(option.classId, 'group price option should create a grade class').toBeTruthy();
  return { product: { id: productId, productName }, option, classId: option.classId };
}

export async function submitOneOnOneRegistrationByApi(
  request: APIRequestContext,
  token: string,
  data: {
    suffix: string;
    productId: EntityId;
    optionId: EntityId;
    teacherId?: EntityId;
    priceAmount?: number;
    paidAmount?: number;
  },
): Promise<ApiRecord> {
  const studentName = `${ACCEPTANCE_MARKER} Student ${data.suffix}`;
  return apiPost<ApiRecord>(request, token, '/v1/registration/intake', {
    student: {
      studentName,
      grade: 'G4',
      school: 'Acceptance School',
      guardianName: `${ACCEPTANCE_MARKER} Guardian`,
      guardianPhone: `626555${String(Date.now()).slice(-4)}`,
    },
    campusName: 'Online Class',
    signupDate: todayInNewYork(),
    periodStartDate: todayInNewYork(),
    periodEndDate: todayInNewYork(),
    handledByName: `${ACCEPTANCE_MARKER} Handler ${data.suffix}`,
    items: [{
      lineNo: 1,
      registerClass: '1-on-1',
      courseType: 'ONE_ON_ONE',
      courseProductId: data.productId,
      coursePriceOptionId: data.optionId,
      detail: 'G1-G5 | 40 hours',
      gradeBand: 'G1-G5',
      periodLabel: '40 hours',
      priceAmount: data.priceAmount ?? 900,
      originalPriceAmount: data.priceAmount ?? 900,
      oneOnOneHours: 40,
      oneOnOneUnits: 80,
      teacherId: data.teacherId,
    }],
    payments: data.paidAmount
      ? [{ paymentMethod: 'TRANSFER', amount: data.paidAmount, paidAt: `${todayInNewYork()}T00:00:00` }]
      : [],
    receiptOptions: { issueReceipt: true },
    remark: ACCEPTANCE_MARKER,
  });
}

export async function createRecurringScheduleByApi(
  request: APIRequestContext,
  token: string,
  packageId: EntityId,
  teacherId: EntityId,
  classroomId: EntityId,
): Promise<ApiRecord> {
  return apiPost<ApiRecord>(request, token, '/v1/schedule/rules', {
    packageId,
    primaryTeacherId: teacherId,
    defaultClassroomId: classroomId,
    startDate: todayInNewYork(),
    notes: ACCEPTANCE_MARKER,
    slots: [{ weekday: weekdayForApi(todayInNewYork()), startTime: '10:00', endTime: '12:00', plannedUnits: 4 }],
  });
}

export async function findCoursePriceOption(
  request: APIRequestContext,
  token: string,
  courseProductId: EntityId,
  optionId: EntityId,
): Promise<ApiRecord> {
  const page = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/base/course-price-options', {
    courseProductId,
    pageNum: 1,
    pageSize: 50,
  });
  const option = page.records.find((item) => String(item.id) === String(optionId));
  expect(option, `course price option ${optionId} should exist`).toBeTruthy();
  return option!;
}

export async function firstOccurrenceForPackage(db: Connection, packageId: EntityId): Promise<ApiRecord> {
  const row = await queryOne<ApiRecord>(
    db,
    'SELECT * FROM lesson_occurrence WHERE package_id = ? AND planning_status <> "CANCELLED" ORDER BY planned_start_at LIMIT 1',
    [packageId],
  );
  expect(row, 'expected a planned lesson occurrence for package').toBeTruthy();
  return row!;
}

export async function assertRegistrationDbState(
  db: Connection,
  result: ApiRecord,
  expected: { paymentMethod: string; paidAmount: string; balanceAmount: string },
): Promise<void> {
  const orderRow = await queryOne<ApiRecord>(db, 'SELECT * FROM enrollment_order WHERE id = ?', [result.enrollmentOrderId]);
  expect(orderRow).toMatchObject({
    payment_status: result.paymentStatus,
  });
  expect(Number(orderRow!.received_amount).toFixed(2)).toBe(expected.paidAmount);
  expect(Number(orderRow!.balance_amount).toFixed(2)).toBe(expected.balanceAmount);

  const itemRows = await queryAll<ApiRecord>(db, 'SELECT * FROM enrollment_order_item WHERE enrollment_order_id = ?', [result.enrollmentOrderId]);
  expect(itemRows).toHaveLength(1);
  expect(Number(itemRows[0].price_amount)).toBe(Number(result.amountDue));

  const packageRow = await queryOne<ApiRecord>(db, 'SELECT * FROM course_package WHERE enrollment_order_id = ?', [result.enrollmentOrderId]);
  expect(packageRow).toBeTruthy();
  expect(Number(packageRow!.total_units)).toBeGreaterThan(0);
  expect(String(packageRow!.total_units)).toBe(String(packageRow!.remaining_units));

  const paymentRow = await queryOne<ApiRecord>(db, 'SELECT * FROM payment_record WHERE enrollment_order_id = ?', [result.enrollmentOrderId]);
  expect(paymentRow).toBeTruthy();
  expect(paymentRow!.payment_method).toBe(expected.paymentMethod);
  expect(Number(paymentRow!.amount).toFixed(2)).toBe(expected.paidAmount);
}

export function courseRow(page: Page): Locator {
  return page.locator('.course-entry-row').first();
}

export async function chooseVisibleOption(page: Page, text: string): Promise<void> {
  const dropdown = page.locator('.el-select-dropdown:visible').last();
  await dropdown.locator('.el-select-dropdown__item').filter({ hasText: text }).first().click();
}

export function weekdayForApi(dateText: string): number {
  const day = new Date(`${dateText}T12:00:00Z`).getUTCDay();
  return day === 0 ? 7 : day;
}
