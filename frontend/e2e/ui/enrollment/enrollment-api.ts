import { type APIRequestContext } from '@playwright/test';

import { apiGet, apiPost, type PageResult } from '../../helpers/api';

type ApiRecord = Record<string, any>;

/**
 * 报名相关 E2E 的基础数据准备：复用后端 API 直接创建测试所需的师生、教室、课程产品，
 * 避免每个 UI 用例都要从头点出全部前置数据。命名统一带上 marker，便于 db cleanup 物理清理。
 */

export async function createTeacherByApi(
  request: APIRequestContext,
  token: string,
  marker: string,
  suffix: string,
): Promise<ApiRecord> {
  const teacherName = `${marker} Teacher ${suffix}`;
  const id = await apiPost<string>(request, token, '/v1/base/teachers', {
    teacherName,
    mobile: '626-555-0101',
    email: `${marker.toLowerCase()}.${suffix}.teacher@example.com`,
    subjectTags: ['Math'],
    status: 'ACTIVE',
    notes: marker,
  });
  return { id, teacherName };
}

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
    guardianPhone: '626-555-0102',
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
    capacity: 8,
    status: 'ACTIVE',
    notes: marker,
  });
  return { id, roomName };
}

/**
 * 创建 1对1 课程产品（报名时按课时计费）。
 */
export async function createOneOnOneProductByApi(
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

/**
 * 创建按次计费（PER_SESSION）的班课产品，后端会随之生成班级（class_group）。
 */
export async function createPerSessionClassProductByApi(
  request: APIRequestContext,
  token: string,
  marker: string,
  suffix: string,
  options: { classroomId: string | number; teacherId: string | number; startDate: string },
): Promise<ApiRecord> {
  const productName = `${marker} Per-Session Class ${suffix}`;
  const id = await apiPost<string>(request, token, '/v1/base/course-products', {
    productCode: '',
    productName,
    subjectName: 'Math',
    teachingMode: 'GROUP',
    billingType: 'PER_SESSION',
    startDate: options.startDate,
    totalSessions: 10,
    defaultClassroomId: options.classroomId,
    defaultTeacherId: options.teacherId,
    listPrice: 1000,
    status: 'ACTIVE',
    notes: marker,
    slots: [{ weekday: 2, startTime: '15:00', endTime: '16:30' }],
  });
  return { id, productName, totalSessions: 10, listPrice: 1000 };
}

/**
 * 创建按月计费（MONTHLY）的班课产品。startMonth/endMonth 决定可报名的月份区间。
 */
export async function createMonthlyClassProductByApi(
  request: APIRequestContext,
  token: string,
  marker: string,
  suffix: string,
  options: { classroomId: string | number; teacherId: string | number; startMonth: string; endMonth: string },
): Promise<ApiRecord> {
  const productName = `${marker} Monthly Class ${suffix}`;
  const id = await apiPost<string>(request, token, '/v1/base/course-products', {
    productCode: '',
    productName,
    subjectName: 'Math',
    teachingMode: 'GROUP',
    billingType: 'MONTHLY',
    startMonth: options.startMonth,
    endMonth: options.endMonth,
    defaultClassroomId: options.classroomId,
    defaultTeacherId: options.teacherId,
    listPrice: 1200,
    status: 'ACTIVE',
    notes: marker,
    slots: [{ weekday: 4, startTime: '17:00', endTime: '18:30' }],
  });
  return { id, productName, listPrice: 1200, startMonth: options.startMonth, endMonth: options.endMonth };
}

/**
 * 创建支付方式（用于支付/退款选择渠道）。
 */
export async function createPaymentMethodByApi(
  request: APIRequestContext,
  token: string,
  marker: string,
  suffix: string,
): Promise<ApiRecord> {
  const methodName = `${marker} Cash ${suffix}`;
  const methodCode = `${marker}_${suffix}`.toUpperCase().replace(/[^A-Z0-9_]/g, '_');
  const id = await apiPost<string>(request, token, '/v1/enrollment/payment-methods', {
    methodCode,
    methodName,
    sortOrder: 99,
    status: 'ACTIVE',
  });
  return { id, methodName, methodCode };
}

/**
 * 通过 API 直接创建一个 1对1 报名订单（含周排课时段），用于需要预置订单的用例
 * （例如支付/退款/取消等只关注后续流程、不必每次都从 UI 走完整创建的场景）。
 */
export async function createOneOnOneOrderByApi(
  request: APIRequestContext,
  token: string,
  marker: string,
  options: {
    studentId: string | number;
    courseProductId: string | number;
    primaryTeacherId?: string | number;
    startDate: string;
    originalAmount: number;
    discountAmount?: number;
    handledBySuffix: string;
    slots?: { weekday: number; startTime: string; endTime: string; plannedUnits: number }[];
  },
): Promise<string> {
  return apiPost<string>(request, token, '/v1/enrollment/orders', {
    studentId: options.studentId,
    courseProductId: options.courseProductId,
    primaryTeacherId: options.primaryTeacherId,
    handledByName: `${marker} Handler ${options.handledBySuffix}`,
    startDate: options.startDate,
    originalAmount: options.originalAmount,
    discountAmount: options.discountAmount ?? 0,
    receivedAmount: 0,
    issueReceipt: false,
    remark: marker,
    slots: options.slots ?? [{ weekday: 1, startTime: '10:00', endTime: '11:00', plannedUnits: 2 }],
  });
}

/**
 * 通过 API 直接创建一个按次计费的班课报名订单。
 */
export async function createPerSessionClassOrderByApi(
  request: APIRequestContext,
  token: string,
  marker: string,
  options: { studentId: string | number; classId: string | number; sessions: number; handledBySuffix: string },
): Promise<string> {
  return apiPost<string>(request, token, '/v1/enrollment/orders/class-enroll', {
    studentId: options.studentId,
    productId: options.classId,
    sessions: options.sessions,
    discountAmount: 0,
    receivedAmount: 0,
    handledByName: `${marker} Handler ${options.handledBySuffix}`,
    remark: marker,
  });
}

/**
 * 按订单号/关键字查询报名订单，做 API 层数据一致性校验。
 */
export async function fetchOrderByNoFromApi(
  request: APIRequestContext,
  token: string,
  keyword: string,
): Promise<ApiRecord | undefined> {
  const orders = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/enrollment/orders', {
    keyword,
    pageNum: 1,
    pageSize: 50,
  });
  return orders.records[0];
}

/**
 * 按订单 id 拉取报名订单详情（含金额与支付记录），用于支付/退款后的精确校验。
 */
export async function fetchOrderDetailFromApi(
  request: APIRequestContext,
  token: string,
  id: string | number,
): Promise<ApiRecord> {
  return apiGet<ApiRecord>(request, token, `/v1/enrollment/orders/${id}`);
}

/**
 * 拉取全部折扣预设（含停用），用于校验预设的增删改。
 */
export async function fetchDiscountPresetsFromApi(
  request: APIRequestContext,
  token: string,
): Promise<ApiRecord[]> {
  return apiGet<ApiRecord[]>(request, token, '/v1/base/discount-presets');
}

/**
 * 拉取全部支付方式（含停用），用于校验支付方式的增删改。
 */
export async function fetchPaymentMethodsFromApi(
  request: APIRequestContext,
  token: string,
): Promise<ApiRecord[]> {
  return apiGet<ApiRecord[]>(request, token, '/v1/enrollment/payment-methods');
}

/**
 * 按课包 id 拉取课包详情，用于关闭课包后的状态校验。
 */
export async function fetchCoursePackageFromApi(
  request: APIRequestContext,
  token: string,
  id: string | number,
): Promise<ApiRecord> {
  return apiGet<ApiRecord>(request, token, `/v1/enrollment/packages/${id}`);
}

/**
 * 拉取班课产品进度聚合行，用于校验班课报名与课包页面的联动。
 */
export async function fetchGroupClassProgressFromApi(
  request: APIRequestContext,
  token: string,
  keyword: string,
): Promise<ApiRecord | undefined> {
  const page = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/enrollment/packages/group-classes', {
    keyword,
    pageNum: 1,
    pageSize: 50,
  });
  return page.records.find((item) => item.productName === keyword || item.className === keyword);
}

/**
 * 按学生查询其名下的课包（1对1 报名成功后后端会生成课包），用于校验报名 → 课包的联动。
 */
export async function fetchPackagesByStudentFromApi(
  request: APIRequestContext,
  token: string,
  studentId: string | number,
): Promise<ApiRecord[]> {
  const packages = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/enrollment/packages', {
    studentId,
    pageNum: 1,
    pageSize: 50,
  });
  return packages.records;
}
