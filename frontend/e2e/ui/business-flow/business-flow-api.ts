import { expect, type APIRequestContext } from '@playwright/test';

import { apiGet, apiPost, type PageResult } from '../../helpers/api';

export type ApiRecord = Record<string, any>;
export type EntityId = string | number;

export interface TeacherAccountFixture {
  userId: string;
  username: string;
  password: string;
  profile: ApiRecord;
}

export interface CourseSlotPayload {
  weekday: number;
  startTime: string;
  endTime: string;
  plannedUnits?: number;
}

export interface LessonConfirmPayload {
  occurrenceId: EntityId;
  resultStatus?: string;
  actualTeacherId?: EntityId;
  actualClassroomId?: EntityId;
  actualStartAt?: string;
  actualEndAt?: string;
  actualUnits?: number;
  overrideShouldDeduct?: boolean;
  overrideDeductedUnits?: number;
  overrideShouldExtendEndDate?: boolean;
  notes?: string;
}

export interface ScheduleAdjustmentPayload {
  occurrenceId: EntityId;
  adjustmentType: 'RESCHEDULE' | 'CHANGE_ROOM' | 'CHANGE_TEACHER' | 'MAKEUP_CREATE';
  plannedTeacherId?: EntityId;
  plannedClassroomId?: EntityId;
  plannedStartAt?: string;
  plannedEndAt?: string;
  plannedUnits?: number;
  reason?: string;
}

export interface WeeklyScheduleAdjustmentItemPayload {
  action: 'UPDATE' | 'CREATE' | 'DELETE';
  occurrenceId?: EntityId;
  plannedTeacherId?: EntityId;
  plannedClassroomId?: EntityId;
  plannedStartAt?: string;
  plannedEndAt?: string;
  plannedUnits?: number;
  itemReason?: string;
}

export interface WeeklyScheduleAdjustmentPayload {
  packageId: EntityId;
  weekStartDate: string;
  reason?: string;
  items: WeeklyScheduleAdjustmentItemPayload[];
}

/**
 * 跨页面业务链路的 API helper：只准备前置数据和做状态查询，关键路径仍由 UI spec 驱动。
 * 所有新建数据都写入 marker，便于 DB cleanup 物理清理。
 */

export async function createStudentByApi(
  request: APIRequestContext,
  token: string,
  marker: string,
  suffix: string,
): Promise<ApiRecord> {
  const studentName = `${marker} Student ${suffix}`;
  const id = await apiPost<string>(request, token, '/v1/base/students', {
    studentName,
    gender: 'FEMALE',
    guardianName: `${marker} Guardian`,
    guardianPhone: '626-555-1201',
    guardianEmail: `${marker.toLowerCase()}.${suffix}.guardian@example.com`,
    guardianRelationType: 'MOTHER',
    status: 'ACTIVE',
    notes: marker,
  });
  return { id, studentName };
}

export async function createClassroomByApi(
  request: APIRequestContext,
  token: string,
  marker: string,
  suffix: string,
): Promise<ApiRecord> {
  const roomName = `${marker} Room ${suffix}`;
  const id = await apiPost<string>(request, token, '/v1/base/classrooms', {
    roomName,
    campus: 'Main Campus',
    capacity: 10,
    status: 'ACTIVE',
    notes: marker,
  });
  return { id, roomName };
}

export async function createTeacherAccountByApi(
  request: APIRequestContext,
  token: string,
  marker: string,
  suffix: string,
  password = 'Test@123456',
): Promise<TeacherAccountFixture> {
  const teacherName = `${marker} Teacher ${suffix}`;
  const username = `${marker}_${suffix}_teacher`.toLowerCase().replace(/[^a-z0-9_]/g, '_');
  const userId = await apiPost<string>(request, token, '/v1/auth/teacher-accounts', {
    teacherName,
    username,
    password,
    displayName: teacherName,
    mobile: '626-555-1202',
    email: `${marker.toLowerCase()}.${suffix}.teacher@example.com`,
    subjectTags: ['Math'],
    status: 'ACTIVE',
    remark: marker,
  });
  const teachers = await fetchTeachersByKeyword(request, token, teacherName);
  expect(teachers[0], 'API setup should create teacher profile').toBeTruthy();
  return { userId, username, password, profile: teachers[0] };
}

export async function createOneOnOneCourseProductByApi(
  request: APIRequestContext,
  token: string,
  marker: string,
  suffix: string,
  overrides: Partial<{ defaultTotalUnits: number; defaultLessonUnits: number; listPrice: number }> = {},
): Promise<ApiRecord> {
  const productName = `${marker} 1v1 Product ${suffix}`;
  const id = await apiPost<string>(request, token, '/v1/base/course-products', {
    productCode: '',
    productName,
    subjectName: 'Math',
    teachingMode: 'ONE_ON_ONE',
    defaultTotalUnits: overrides.defaultTotalUnits ?? 8,
    defaultLessonUnits: overrides.defaultLessonUnits ?? 2,
    listPrice: overrides.listPrice ?? 800,
    status: 'ACTIVE',
    notes: marker,
  });
  return { id, productName };
}

export async function createPerSessionClassCourseProductByApi(
  request: APIRequestContext,
  token: string,
  marker: string,
  suffix: string,
  options: {
    teacherId: EntityId;
    classroomId: EntityId;
    startDate: string;
    totalSessions?: number;
    listPrice?: number;
    slots?: CourseSlotPayload[];
  },
): Promise<ApiRecord> {
  const productName = `${marker} Class Product ${suffix}`;
  const totalSessions = options.totalSessions ?? 10;
  const listPrice = options.listPrice ?? 1000;
  const id = await apiPost<string>(request, token, '/v1/base/course-products', {
    productCode: '',
    productName,
    subjectName: 'Math',
    teachingMode: 'GROUP',
    billingType: 'PER_SESSION',
    startDate: options.startDate,
    totalSessions,
    defaultTeacherId: options.teacherId,
    defaultClassroomId: options.classroomId,
    listPrice,
    status: 'ACTIVE',
    notes: marker,
    slots: options.slots ?? [{ weekday: 2, startTime: '15:00', endTime: '16:30' }],
  });
  return { id, productName, totalSessions, listPrice };
}

export async function fetchCourseProductsByKeyword(
  request: APIRequestContext,
  token: string,
  keyword: string,
): Promise<ApiRecord[]> {
  const page = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/base/course-products', {
    keyword,
    pageNum: 1,
    pageSize: 50,
  });
  return page.records;
}

export async function createOneOnOneEnrollmentOrderByApi(
  request: APIRequestContext,
  token: string,
  marker: string,
  options: {
    studentId: EntityId;
    courseProductId: EntityId;
    primaryTeacherId?: EntityId;
    defaultClassroomId?: EntityId;
    startDate: string;
    originalAmount: number;
    discountAmount?: number;
    totalUnits?: number;
    defaultLessonUnits?: number;
    handledBySuffix: string;
    slots?: CourseSlotPayload[];
  },
): Promise<string> {
  return apiPost<string>(request, token, '/v1/enrollment/orders', {
    studentId: options.studentId,
    courseProductId: options.courseProductId,
    primaryTeacherId: options.primaryTeacherId,
    defaultClassroomId: options.defaultClassroomId,
    handledByName: `${marker} Handler ${options.handledBySuffix}`,
    startDate: options.startDate,
    originalAmount: options.originalAmount,
    discountAmount: options.discountAmount ?? 0,
    receivedAmount: 0,
    totalUnits: options.totalUnits,
    defaultLessonUnits: options.defaultLessonUnits,
    issueReceipt: false,
    remark: marker,
    slots: options.slots ?? [{ weekday: 1, startTime: '10:00', endTime: '11:00', plannedUnits: 2 }],
  });
}

export async function createClassEnrollmentOrderByApi(
  request: APIRequestContext,
  token: string,
  marker: string,
  options: {
    studentId: EntityId;
    classId?: EntityId;
    productId?: EntityId;
    sessions?: number;
    handledBySuffix: string;
  },
): Promise<string> {
  return apiPost<string>(request, token, '/v1/enrollment/orders/class-enroll', {
    studentId: options.studentId,
    classId: options.classId,
    productId: options.productId,
    sessions: options.sessions,
    discountAmount: 0,
    receivedAmount: 0,
    handledByName: `${marker} Handler ${options.handledBySuffix}`,
    remark: marker,
  });
}

export async function fetchEnrollmentOrdersByKeyword(
  request: APIRequestContext,
  token: string,
  keyword: string,
): Promise<ApiRecord[]> {
  const page = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/enrollment/orders', {
    keyword,
    pageNum: 1,
    pageSize: 50,
  });
  return page.records;
}

export async function fetchEnrollmentOrderDetail(request: APIRequestContext, token: string, id: EntityId): Promise<ApiRecord> {
  return apiGet<ApiRecord>(request, token, `/v1/enrollment/orders/${id}`);
}

export async function fetchCoursePackages(
  request: APIRequestContext,
  token: string,
  params: Record<string, unknown> = {},
): Promise<ApiRecord[]> {
  const page = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/enrollment/packages', {
    pageNum: 1,
    pageSize: 50,
    ...params,
  });
  return page.records;
}

export async function fetchCoursePackageDetail(request: APIRequestContext, token: string, id: EntityId): Promise<ApiRecord> {
  return apiGet<ApiRecord>(request, token, `/v1/enrollment/packages/${id}`);
}

export async function fetchCoursePackageBalance(request: APIRequestContext, token: string, id: EntityId): Promise<number> {
  return apiGet<number>(request, token, `/v1/enrollment/packages/${id}/balance`);
}

export async function fetchLessonOccurrences(
  request: APIRequestContext,
  token: string,
  params: Record<string, unknown> = {},
): Promise<ApiRecord[]> {
  const page = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/schedule/occurrences', {
    pageNum: 1,
    pageSize: 100,
    ...params,
  });
  return page.records;
}

export async function fetchFirstOccurrenceForPackage(
  request: APIRequestContext,
  token: string,
  packageId: EntityId,
): Promise<ApiRecord> {
  const occurrences = await fetchLessonOccurrences(request, token, { packageId });
  expect(occurrences[0], 'Package should have at least one occurrence').toBeTruthy();
  return occurrences[0];
}

export async function fetchLessonRecords(
  request: APIRequestContext,
  token: string,
  params: Record<string, unknown> = {},
): Promise<ApiRecord[]> {
  const page = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/lessons/records', {
    pageNum: 1,
    pageSize: 50,
    ...params,
  });
  return page.records;
}

export async function fetchLessonRecordDetail(request: APIRequestContext, token: string, id: EntityId): Promise<ApiRecord> {
  return apiGet<ApiRecord>(request, token, `/v1/lessons/records/${id}`);
}

export async function confirmLessonRecordByApi(
  request: APIRequestContext,
  token: string,
  payload: LessonConfirmPayload,
): Promise<ApiRecord> {
  return apiPost<ApiRecord>(request, token, '/v1/lessons/records', {
    resultStatus: 'COMPLETED',
    ...payload,
  });
}

export async function teacherConfirmOwnLessonByApi(
  request: APIRequestContext,
  teacherToken: string,
  payload: LessonConfirmPayload,
): Promise<ApiRecord> {
  // 老师初确认复用通用入口，后端按 token 角色分流。
  return apiPost<ApiRecord>(request, teacherToken, '/v1/lessons/records', {
    resultStatus: 'COMPLETED',
    ...payload,
  });
}

export async function adminConfirmLessonRecordByApi(
  request: APIRequestContext,
  token: string,
  lessonRecordId: EntityId,
): Promise<ApiRecord> {
  return apiPost<ApiRecord>(request, token, `/v1/lessons/records/${lessonRecordId}/admin-confirm`);
}

export async function createScheduleAdjustmentByApi(
  request: APIRequestContext,
  token: string,
  payload: ScheduleAdjustmentPayload,
): Promise<ApiRecord> {
  return apiPost<ApiRecord>(request, token, '/v1/schedule/adjustments', payload);
}

export async function adjustWeeklyScheduleByApi(
  request: APIRequestContext,
  token: string,
  payload: WeeklyScheduleAdjustmentPayload,
): Promise<ApiRecord> {
  return apiPost<ApiRecord>(request, token, '/v1/schedule/adjustments/weekly', payload);
}

export async function rejectLessonRecordByApi(
  request: APIRequestContext,
  token: string,
  lessonRecordId: EntityId,
  rejectReason: string,
): Promise<null> {
  return apiPost<null>(request, token, `/v1/lessons/records/${lessonRecordId}/reject`, { rejectReason });
}

export async function fetchHourLedger(
  request: APIRequestContext,
  token: string,
  params: Record<string, unknown> = {},
): Promise<ApiRecord[]> {
  const page = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/lessons/ledger', {
    pageNum: 1,
    pageSize: 50,
    ...params,
  });
  return page.records;
}

export async function fetchBalanceSnapshot(request: APIRequestContext, token: string, packageId: EntityId): Promise<ApiRecord> {
  return apiGet<ApiRecord>(request, token, `/v1/lessons/balance-snapshots/${packageId}`);
}

export async function saveTeacherPayRateByApi(
  request: APIRequestContext,
  token: string,
  marker: string,
  options: { teacherId: EntityId; courseProductId: EntityId; hourlyRate?: number },
): Promise<string> {
  return apiPost<string>(request, token, '/v1/finance/pay-rates', {
    teacherId: options.teacherId,
    courseProductId: options.courseProductId,
    hourlyRate: options.hourlyRate ?? 60,
    notes: marker,
  });
}

export async function fetchTeacherPayRates(
  request: APIRequestContext,
  token: string,
  params: { teacherId?: EntityId; courseProductId?: EntityId } = {},
): Promise<ApiRecord[]> {
  return apiGet<ApiRecord[]>(request, token, '/v1/finance/pay-rates', params);
}

export async function fetchUnpaidTeacherLessons(
  request: APIRequestContext,
  token: string,
  params: { teacherId: EntityId; startDate?: string; endDate?: string },
): Promise<ApiRecord[]> {
  return apiGet<ApiRecord[]>(request, token, '/v1/finance/payments/unpaid-lessons', params);
}

export async function previewTeacherPayment(
  request: APIRequestContext,
  token: string,
  lessonRecordIds: EntityId[],
): Promise<ApiRecord> {
  return apiPost<ApiRecord>(request, token, '/v1/finance/payments/preview', lessonRecordIds);
}

export async function createTeacherPaymentByApi(
  request: APIRequestContext,
  token: string,
  marker: string,
  options: { teacherId: EntityId; periodStart: string; periodEnd: string; lessonRecordIds: EntityId[] },
): Promise<string> {
  return apiPost<string>(request, token, '/v1/finance/payments', {
    teacherId: options.teacherId,
    periodStart: options.periodStart,
    periodEnd: options.periodEnd,
    lessonRecordIds: options.lessonRecordIds,
    notes: marker,
  });
}

export async function fetchTeacherPayments(
  request: APIRequestContext,
  token: string,
  params: { teacherId?: EntityId; status?: string; pageNum?: number; pageSize?: number } = {},
): Promise<ApiRecord[]> {
  const page = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/finance/payments', {
    pageNum: 1,
    pageSize: 50,
    ...params,
  });
  return page.records;
}

export async function fetchTeacherPaymentDetail(request: APIRequestContext, token: string, paymentId: EntityId): Promise<ApiRecord> {
  return apiGet<ApiRecord>(request, token, `/v1/finance/payments/${paymentId}`);
}

export async function confirmTeacherPaymentByApi(
  request: APIRequestContext,
  token: string,
  paymentId: EntityId,
): Promise<string> {
  return apiPost<string>(request, token, `/v1/finance/payments/${paymentId}/confirm`);
}

async function fetchTeachersByKeyword(request: APIRequestContext, token: string, keyword: string): Promise<ApiRecord[]> {
  const page = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/base/teachers', {
    keyword,
    pageNum: 1,
    pageSize: 20,
  });
  return page.records;
}
