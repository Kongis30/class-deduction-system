import http from './http';
import type { ApiResponse, PageResult } from '@/types/http';
import type {
  CoursePackageItem,
  EntityId,
  EnrollmentOrderDetailItem,
  EnrollmentOrderItem,
  PaymentMethodItem,
  PaymentRecordItem,
  ReceiptRecordItem,
} from '@/types/domain';

// ─── Enrollment Orders ───────────────────────────────
export interface EnrollmentOrderQuery {
  keyword?: string;
  orderStatus?: string;
  paymentStatus?: string;
  studentId?: string | number;
  classId?: string | number;
  excludeClassOrders?: boolean;
  pageNum?: number;
  pageSize?: number;
}

export function fetchEnrollmentOrders(params: EnrollmentOrderQuery = {}) {
  return http.get<ApiResponse<PageResult<EnrollmentOrderItem>>>('/v1/enrollment/orders', { params });
}

export function fetchEnrollmentOrder(id: string | number) {
  return http.get<ApiResponse<EnrollmentOrderDetailItem>>(`/v1/enrollment/orders/${id}`);
}

export interface SlotPayload {
  weekday: number;
  startTime: string;
  endTime: string;
  plannedUnits: number;
}

export interface EnrollmentOrderSavePayload {
  studentId: string | number;
  courseProductId: string | number;
  primaryTeacherId?: string | number;
  cashierUserId?: EntityId;
  handledByName: string;
  currencyCode?: string;
  packageName?: string;
  startDate?: string;
  estimatedEndDate?: string;
  paidAt?: string;
  issueReceipt?: boolean;
  templateCode?: string;
  originalAmount: number;
  discountAmount: number;
  receivedAmount: number;
  totalUnits?: number;
  defaultLessonUnits?: number;
  remark?: string;
  slots?: SlotPayload[];
  defaultClassroomId?: string | number;
}

export function createEnrollmentOrder(data: EnrollmentOrderSavePayload) {
  return http.post('/v1/enrollment/orders', data);
}

export function updateEnrollmentOrder(id: string | number, data: EnrollmentOrderSavePayload) {
  return http.put(`/v1/enrollment/orders/${id}`, data);
}

export function cancelEnrollmentOrder(id: string | number) {
  return http.post(`/v1/enrollment/orders/${id}/cancel`);
}

export function deleteEnrollmentOrder(id: string | number) {
  return http.delete(`/v1/enrollment/orders/${id}`);
}

export interface ClassEnrollmentCreatePayload {
  studentId: string | number;
  classId?: string | number;
  productId?: string | number;
  enrollmentDate: string;
  expiryDate: string;
  handledByName?: string;
  originalAmount: number;
  discountAmount: number;
  receivedAmount: number;
  remark?: string;
}

export function createClassEnrollmentOrder(data: ClassEnrollmentCreatePayload) {
  return http.post('/v1/enrollment/orders/class-enroll', data);
}

// ─── Course Packages ─────────────────────────────────
export interface CoursePackageQuery {
  keyword?: string;
  studentId?: string | number;
  packageStatus?: string;
  teacherId?: EntityId;
  sortBy?: string;
  sortOrder?: string;
  pageNum?: number;
  pageSize?: number;
}

export function fetchCoursePackages(params: CoursePackageQuery = {}) {
  return http.get<ApiResponse<PageResult<CoursePackageItem>>>('/v1/enrollment/packages', { params });
}

export function fetchCoursePackage(id: string | number) {
  return http.get<ApiResponse<CoursePackageItem>>(`/v1/enrollment/packages/${id}`);
}

export interface CoursePackageUpdatePayload {
  packageStatus?: string;
  startDate?: string;
  estimatedEndDate?: string;
  remark?: string;
}

export function updateCoursePackage(id: string | number, data: CoursePackageUpdatePayload) {
  return http.put(`/v1/enrollment/packages/${id}`, data);
}

export function closeCoursePackage(id: string | number) {
  return http.post(`/v1/enrollment/packages/${id}/close`);
}

export function getCoursePackageBalance(id: string | number) {
  return http.get<ApiResponse<number>>(`/v1/enrollment/packages/${id}/balance`);
}

// ─── Payment Records ─────────────────────────────────
export function fetchPaymentsByOrder(enrollmentOrderId: EntityId) {
  return http.get<ApiResponse<PaymentRecordItem[]>>(`/v1/enrollment/payments/order/${enrollmentOrderId}`);
}

export function fetchPayment(id: EntityId) {
  return http.get<ApiResponse<PaymentRecordItem>>(`/v1/enrollment/payments/${id}`);
}

export interface PaymentSplit {
  paymentMethod: string;
  amount: number;
}

export interface PaymentRecordSavePayload {
  enrollmentOrderId: EntityId;
  splits: PaymentSplit[];
  paidAt?: string;
  handledByName: string;
  remark?: string;
}

export function createPaymentRecord(data: PaymentRecordSavePayload) {
  return http.post('/v1/enrollment/payments', data);
}

// ─── Payment Methods ─────────────────────────────────
export function fetchPaymentMethods() {
  return http.get<ApiResponse<PaymentMethodItem[]>>('/v1/enrollment/payment-methods');
}

export function fetchActivePaymentMethods() {
  return http.get<ApiResponse<PaymentMethodItem[]>>('/v1/enrollment/payment-methods/active');
}

export function fetchPaymentMethod(id: EntityId) {
  return http.get<ApiResponse<PaymentMethodItem>>(`/v1/enrollment/payment-methods/${id}`);
}

export interface PaymentMethodSavePayload {
  methodCode: string;
  methodName: string;
  sortOrder?: number;
  status?: string;
}

export function createPaymentMethod(data: PaymentMethodSavePayload) {
  return http.post('/v1/enrollment/payment-methods', data);
}

export function updatePaymentMethod(id: EntityId, data: PaymentMethodSavePayload) {
  return http.put(`/v1/enrollment/payment-methods/${id}`, data);
}

export function deletePaymentMethod(id: EntityId) {
  return http.delete(`/v1/enrollment/payment-methods/${id}`);
}

// ─── Receipt Records ─────────────────────────────────
export interface ReceiptQuery {
  keyword?: string;
  studentId?: EntityId;
  enrollmentOrderId?: EntityId;
  pageNum?: number;
  pageSize?: number;
}

export function fetchReceipts(params: ReceiptQuery = {}) {
  return http.get<ApiResponse<PageResult<ReceiptRecordItem>>>('/v1/enrollment/receipts', { params });
}

export function fetchReceipt(id: EntityId) {
  return http.get<ApiResponse<ReceiptRecordItem>>(`/v1/enrollment/receipts/${id}`);
}
