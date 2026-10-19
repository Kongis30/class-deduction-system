import http from './http';
import type { ApiResponse, PageResult } from '@/types/http';
import type {
  EntityId,
  LessonRecordConfirmResult,
  LessonRecordDetailItem,
  LessonRecordItem,
} from '@/types/domain';

// ─── Lesson Records ─────────────────────────────────
export interface LessonRecordQuery {
  keyword?: string;
  packageId?: string | number;
  studentId?: string | number;
  teacherId?: string | number;
  resultStatus?: string;
  recordSource?: string;
  confirmedFrom?: string;
  confirmedTo?: string;
  pageNum?: number;
  pageSize?: number;
}

export function fetchLessonRecords(params: LessonRecordQuery = {}) {
  return http.get<ApiResponse<PageResult<LessonRecordItem>>>('/v1/lessons/records', {
    params: {
      ...params,
      pageSize: params.pageSize ? Math.min(params.pageSize, 200) : params.pageSize,
    },
  });
}

export function fetchLessonRecord(id: string | number) {
  return http.get<ApiResponse<LessonRecordDetailItem>>(`/v1/lessons/records/${id}`);
}

export interface LessonConfirmPayload {
  occurrenceId: string | number;
  resultStatus: string;
  actualTeacherId?: string | number;
  actualClassroomId?: string | number;
  actualStartAt?: string;
  actualEndAt?: string;
  actualUnits?: number;
  overrideShouldDeduct?: boolean;
  overrideDeductedUnits?: number;
  overrideShouldExtendEndDate?: boolean;
  notes?: string;
}

export function confirmLesson(data: LessonConfirmPayload) {
  return http.post<ApiResponse<LessonRecordConfirmResult>>('/v1/lessons/records', data);
}

export function adminConfirmLessonRecord(id: string | number, data?: LessonConfirmPayload) {
  return http.post<ApiResponse<LessonRecordConfirmResult>>(`/v1/lessons/records/${id}/admin-confirm`, data);
}

export interface LessonRejectPayload {
  rejectReason: string;
}

export function rejectLessonRecord(id: string | number, data: LessonRejectPayload) {
  return http.post<ApiResponse<null>>(`/v1/lessons/records/${id}/reject`, data);
}

export interface LessonRevokePayload {
  reason?: string;
  expectedVersion: number;
}

export function revokeLessonRecord(id: string | number, data: LessonRevokePayload) {
  return http.post<ApiResponse<null>>(`/v1/lessons/records/${id}/revoke`, data);
}

// ─── Hour Ledger ────────────────────────────────────
export interface HourLedgerItem {
  id: EntityId;
  packageId: EntityId;
  packageNo?: string;
  lessonRecordId?: EntityId;
  occurrenceId?: EntityId;
  lessonNo?: string;
  ledgerType: string;
  changeUnits: number;
  balanceAfterUnits: number;
  businessDate?: string;
  reasonCode?: string;
  reasonDesc?: string;
  operatedAt?: string;
}

export function fetchHourLedger(packageId: EntityId) {
  return http.get<ApiResponse<PageResult<HourLedgerItem>>>('/v1/lessons/ledger', {
    params: { packageId, pageNum: 1, pageSize: 100 },
  });
}
