import http from './http';
import type { ApiResponse, PageResult } from '@/types/http';
import type {
  CoursePackageItem,
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
  studentId?: number;
  classId?: number;
  excludeClassOrders?: boolean;
  pageNum?: number;
  pageSize?: number;
}

export function fetchEnrollmentOrders(params: EnrollmentOrderQuery = {}) {
  return http.get<ApiResponse<PageResult<EnrollmentOrderItem>>>('/v1/enrollment/orders', { params });
}

export function fetchEnrollmentOrder(id: number) {
  return http.get<ApiResponse<EnrollmentOrderItem>>(`/v1/enrollment/orders/${id}`);
}

export interface SlotPayload {
  weekday: number;
  startTime: string;
  endTime: string;
  plannedUnits: number;
}

export interface EnrollmentOrderSavePayload {
  studentId: number;
  courseProductId: number;
  primaryTeacherId?: number;
  cashierUserId?: number;
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
  defaultClassroomId?: number;
}

export function createEnrollmentOrder(data: EnrollmentOrderSavePayload) {
  return http.post('/v1/enrollment/orders', data);
}

export function updateEnrollmentOrder(id: number, data: EnrollmentOrderSavePayload) {
  return http.put(`/v1/enrollment/orders/${id}`, data);
}

export function cancelEnrollmentOrder(id: number) {
  return http.post(`/v1/enrollment/orders/${id}/cancel`);
}

export function deleteEnrollmentOrder(id: number) {
  return http.delete(`/v1/enrollment/orders/${id}`);
}

export interface ClassEnrollmentCreatePayload {
  studentId: number;
  classId: number;
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
  studentId?: number;
  packageStatus?: string;
  teacherId?: number;
  pageNum?: number;
  pageSize?: number;
}

export function fetchCoursePackages(params: CoursePackageQuery = {}) {
  return http.get<ApiResponse<PageResult<CoursePackageItem>>>('/v1/enrollment/packages', { params });
}

export function fetchCoursePackage(id: number) {
  return http.get<ApiResponse<CoursePackageItem>>(`/v1/enrollment/packages/${id}`);
}

export interface CoursePackageUpdatePayload {
  packageStatus?: string;
  startDate?: string;
  estimatedEndDate?: string;
  remark?: string;
}

export function updateCoursePackage(id: number, data: CoursePackageUpdatePayload) {
  return http.put(`/v1/enrollment/packages/${id}`, data);
}

export function closeCoursePackage(id: number) {
  return http.post(`/v1/enrollment/packages/${id}/close`);
}

export function getCoursePackageBalance(id: number) {
  return http.get<ApiResponse<number>>(`/v1/enrollment/packages/${id}/balance`);
}

// ─── Payment Records ─────────────────────────────────
export function fetchPaymentsByOrder(enrollmentOrderId: number) {
  return http.get<ApiResponse<PaymentRecordItem[]>>(`/v1/enrollment/payments/order/${enrollmentOrderId}`);
}

export function fetchPayment(id: number) {
  return http.get<ApiResponse<PaymentRecordItem>>(`/v1/enrollment/payments/${id}`);
}

export interface PaymentSplit {
  paymentMethod: string;
  amount: number;
}

export interface PaymentRecordSavePayload {
  enrollmentOrderId: number;
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

export function fetchPaymentMethod(id: number) {
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

export function updatePaymentMethod(id: number, data: PaymentMethodSavePayload) {
  return http.put(`/v1/enrollment/payment-methods/${id}`, data);
}

export function deletePaymentMethod(id: number) {
  return http.delete(`/v1/enrollment/payment-methods/${id}`);
}

// ─── Receipt Records ─────────────────────────────────
export interface ReceiptQuery {
  keyword?: string;
  studentId?: number;
  enrollmentOrderId?: number;
  pageNum?: number;
  pageSize?: number;
}

export function fetchReceipts(params: ReceiptQuery = {}) {
  return http.get<ApiResponse<PageResult<ReceiptRecordItem>>>('/v1/enrollment/receipts', { params });
}

export function fetchReceipt(id: number) {
  return http.get<ApiResponse<ReceiptRecordItem>>(`/v1/enrollment/receipts/${id}`);
}
