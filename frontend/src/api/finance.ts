import http from './http';
import type { ApiResponse, PageResult } from '@/types/http';
import type { EntityId, TeacherPayRateItem, TeacherPaymentItem, UnpaidLessonItem, TeacherPaymentItemDetail } from '@/types/domain';

// ─── Teacher Pay Rates ──────────────────────────────────

export interface TeacherPayRateSavePayload {
  teacherId: EntityId;
  courseProductId?: EntityId;
  studentId?: EntityId;
  gradeBand?: string;
  deliveryMode?: string;
  hourlyRate: number;
  notes?: string;
}

export function fetchPayRates(teacherId?: EntityId, courseProductId?: EntityId) {
  return http.get<ApiResponse<TeacherPayRateItem[]>>('/v1/finance/pay-rates', {
    params: { teacherId, courseProductId },
  });
}

export function savePayRate(data: TeacherPayRateSavePayload) {
  return http.post<ApiResponse<EntityId>>('/v1/finance/pay-rates', data);
}

export function deletePayRate(id: EntityId) {
  return http.delete<ApiResponse<string>>(`/v1/finance/pay-rates/${id}`);
}

// ─── Teacher Payments ───────────────────────────────────

export interface UnpaidLessonQuery {
  teacherId: EntityId;
  startDate?: string;
  endDate?: string;
}

export function fetchUnpaidLessons(params: UnpaidLessonQuery) {
  return http.get<ApiResponse<UnpaidLessonItem[]>>('/v1/finance/payments/unpaid-lessons', { params });
}

export interface PaymentCreatePayload {
  teacherId: EntityId;
  periodStart: string;
  periodEnd: string;
  lessonRecordIds: EntityId[];
  notes?: string;
}

export function createPayment(data: PaymentCreatePayload) {
  return http.post<ApiResponse<EntityId>>('/v1/finance/payments', data);
}

export function fetchPayments(params: {
  teacherId?: EntityId;
  status?: string;
  pageNum?: number;
  pageSize?: number;
}) {
  return http.get<ApiResponse<PageResult<TeacherPaymentItem>>>('/v1/finance/payments', { params });
}

export function fetchPaymentDetail(id: EntityId) {
  return http.get<ApiResponse<TeacherPaymentItem>>(`/v1/finance/payments/${id}`);
}

export function fetchMyPayroll(params: {
  status?: string;
  pageNum?: number;
  pageSize?: number;
}) {
  return http.get<ApiResponse<PageResult<TeacherPaymentItem>>>('/v1/finance/my-payroll', { params });
}

export function fetchMyPayrollDetail(id: EntityId) {
  return http.get<ApiResponse<TeacherPaymentItem>>(`/v1/finance/my-payroll/${id}`);
}

export function confirmPayment(id: EntityId) {
  return http.post<ApiResponse<string>>(`/v1/finance/payments/${id}/confirm`);
}
