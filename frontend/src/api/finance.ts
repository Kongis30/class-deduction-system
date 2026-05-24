import http from './http';
import type { ApiResponse, PageResult } from '@/types/http';
import type { TeacherPayRateItem, TeacherPaymentItem, UnpaidLessonItem, TeacherPaymentItemDetail } from '@/types/domain';

// ─── Teacher Pay Rates ──────────────────────────────────

export interface TeacherPayRateSavePayload {
  teacherId: number;
  courseProductId: number;
  hourlyRate: number;
  notes?: string;
}

export function fetchPayRates(teacherId?: number, courseProductId?: number) {
  return http.get<ApiResponse<TeacherPayRateItem[]>>('/v1/finance/pay-rates', {
    params: { teacherId, courseProductId },
  });
}

export function savePayRate(data: TeacherPayRateSavePayload) {
  return http.post<ApiResponse<number>>('/v1/finance/pay-rates', data);
}

export function deletePayRate(id: number) {
  return http.delete<ApiResponse<string>>(`/v1/finance/pay-rates/${id}`);
}

// ─── Teacher Payments ───────────────────────────────────

export interface UnpaidLessonQuery {
  teacherId: number;
  startDate?: string;
  endDate?: string;
}

export function fetchUnpaidLessons(params: UnpaidLessonQuery) {
  return http.get<ApiResponse<UnpaidLessonItem[]>>('/v1/finance/payments/unpaid-lessons', { params });
}

export interface PaymentCreatePayload {
  teacherId: number;
  periodStart: string;
  periodEnd: string;
  lessonRecordIds: number[];
  notes?: string;
}

export function previewPayment(lessonRecordIds: number[]) {
  return http.post<ApiResponse<Record<string, any>>>('/v1/finance/payments/preview', lessonRecordIds);
}

export function createPayment(data: PaymentCreatePayload) {
  return http.post<ApiResponse<number>>('/v1/finance/payments', data);
}

export function fetchPayments(params: {
  teacherId?: number;
  status?: string;
  pageNum?: number;
  pageSize?: number;
}) {
  return http.get<ApiResponse<PageResult<TeacherPaymentItem>>>('/v1/finance/payments', { params });
}

export function fetchPaymentDetail(id: number) {
  return http.get<ApiResponse<TeacherPaymentItem>>(`/v1/finance/payments/${id}`);
}

export function confirmPayment(id: number) {
  return http.post<ApiResponse<string>>(`/v1/finance/payments/${id}/confirm`);
}
