import http from './http';
import type { ApiResponse } from '@/types/http';
import type { EntityId } from '@/types/domain';

export interface StudentCreditBalanceItem {
  studentId: EntityId;
  studentNo?: string;
  studentName: string;
  grade?: string;
  studentClass?: string;
  guardianPhone?: string;
  creditBalance: number;
  lastOccurredAt?: string;
}

export function fetchStudentCreditBalances(keyword?: string) {
  return http.get<ApiResponse<StudentCreditBalanceItem[]>>('/v1/student-credits', {
    params: keyword ? { keyword } : undefined,
  });
}

export function fetchStudentCreditBalance(studentId: EntityId) {
  return http.get<ApiResponse<number>>(`/v1/student-credits/student/${studentId}/balance`);
}

export interface StudentCreditAdjustmentPayload {
  creditBalance: number;
  remark?: string;
}

export function adjustStudentCredit(studentId: EntityId, data: StudentCreditAdjustmentPayload) {
  return http.post<ApiResponse<void>>(`/v1/student-credits/student/${studentId}/adjust`, data);
}
