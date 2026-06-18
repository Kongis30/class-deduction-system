import http from './http';
import type { ApiResponse, PageResult } from '@/types/http';
import type {
  LessonRecordConfirmResult,
  LessonRecordDetailItem,
  LessonRecordItem,
  PackageBalanceSnapshotItem,
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
  return http.get<ApiResponse<PageResult<LessonRecordItem>>>('/v1/lessons/records', { params });
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

// ─── Package Balance Snapshot ───────────────────────
export function fetchPackageBalanceSnapshot(packageId: string | number) {
  return http.get<ApiResponse<PackageBalanceSnapshotItem>>(`/v1/lessons/balance-snapshots/${packageId}`);
}
