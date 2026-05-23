import http from './http';
import type { ApiResponse, PageResult } from '@/types/http';
import type {
  ClassroomOccupancyItem,
  LessonOccurrenceItem,
  RecurringScheduleRuleDetailItem,
  RecurringScheduleRuleItem,
} from '@/types/domain';

// ─── Recurring Schedule Rules ─────────────────────────
export interface RecurringScheduleRuleQuery {
  keyword?: string;
  ruleStatus?: string;
  teacherId?: number;
  classroomId?: number;
  packageId?: number;
  pageNum?: number;
  pageSize?: number;
}

export function fetchRecurringScheduleRules(params: RecurringScheduleRuleQuery = {}) {
  return http.get<ApiResponse<PageResult<RecurringScheduleRuleItem>>>('/v1/schedule/rules', { params });
}

export function fetchRecurringScheduleRule(id: number) {
  return http.get<ApiResponse<RecurringScheduleRuleDetailItem>>(`/v1/schedule/rules/${id}`);
}

export interface RecurringScheduleSlotPayload {
  weekday: number;
  startTime: string;
  endTime: string;
  plannedUnits: number;
}

export interface RecurringScheduleRuleCreatePayload {
  packageId: number;
  primaryTeacherId?: number;
  defaultClassroomId?: number;
  startDate: string;
  notes?: string;
  slots: RecurringScheduleSlotPayload[];
}

export interface ScheduleRuleCreateResult {
  ruleId: number;
  slotCount: number;
  generatedOccurrenceCount: number;
  firstGeneratedStartAt?: string;
  lastGeneratedEndAt?: string;
}

export function createRecurringScheduleRule(data: RecurringScheduleRuleCreatePayload) {
  return http.post<ApiResponse<ScheduleRuleCreateResult>>('/v1/schedule/rules', data);
}

export interface RecurringScheduleRuleUpdatePayload {
  primaryTeacherId?: number;
  defaultClassroomId?: number;
  ruleStatus?: string;
  startDate: string;
  notes?: string;
  slots: RecurringScheduleSlotPayload[];
}

export interface ScheduleRuleUpdateResult {
  ruleId: number;
  newSlotCount: number;
  preservedOccurrenceCount: number;
  deletedOccurrenceCount: number;
  generatedOccurrenceCount: number;
  confirmedOccurrenceCount: number;
  firstGeneratedStartAt?: string;
  lastGeneratedEndAt?: string;
}

export function updateRecurringScheduleRule(id: number, data: RecurringScheduleRuleUpdatePayload) {
  return http.put<ApiResponse<ScheduleRuleUpdateResult>>(`/v1/schedule/rules/${id}`, data);
}

// ─── Lesson Occurrences ────────────────────────────────
export interface LessonOccurrenceQuery {
  keyword?: string;
  packageId?: number;
  studentId?: number;
  teacherId?: number;
  classroomId?: number;
  classGroupId?: number;
  planningStatus?: string;
  startAt?: string;
  endAt?: string;
  pageNum?: number;
  pageSize?: number;
}

export function fetchLessonOccurrences(params: LessonOccurrenceQuery = {}) {
  return http.get<ApiResponse<PageResult<LessonOccurrenceItem>>>('/v1/schedule/occurrences', { params });
}

export function fetchLessonOccurrence(id: number) {
  return http.get<ApiResponse<LessonOccurrenceItem>>(`/v1/schedule/occurrences/${id}`);
}

// ─── Schedule Adjustments ──────────────────────────────
export interface ScheduleAdjustmentPayload {
  occurrenceId: number;
  adjustmentType: string;
  plannedTeacherId?: number;
  plannedClassroomId?: number;
  plannedStartAt?: string;
  plannedEndAt?: string;
  plannedUnits?: number;
  reason?: string;
}

export interface ScheduleAdjustmentResult {
  adjustmentLogId: number;
  affectedOccurrenceId: number;
  createdOccurrenceId?: number;
  adjustmentType: string;
}

export function createScheduleAdjustment(data: ScheduleAdjustmentPayload) {
  return http.post<ApiResponse<ScheduleAdjustmentResult>>('/v1/schedule/adjustments', data);
}

// ─── Classroom Occupancy ──────────────────────────────
export interface ClassroomOccupancyQuery {
  classroomId?: number;
  teacherId?: number;
  startAt?: string;
  endAt?: string;
  pageNum?: number;
  pageSize?: number;
}

export function fetchClassroomOccupancy(params: ClassroomOccupancyQuery = {}) {
  return http.get<ApiResponse<PageResult<ClassroomOccupancyItem>>>('/v1/schedule/classrooms/occupancy', { params });
}

// ─── Student Schedule ─────────────────────────────────
export interface StudentScheduleSlot {
  occurrenceId: number;
  weekday: number;
  startTime: string;
  endTime: string;
  plannedUnits: number;
  courseLabel: string;
  packageId: number;
  planningStatus: string;
}

export function fetchActiveStudentSchedule(studentId: number) {
  return http.get<ApiResponse<StudentScheduleSlot[]>>(`/v1/schedule/occurrences/student/${studentId}/active`);
}

// ─── Class Groups ──────────────────────────────────────
export interface ClassGroupConfirmPayload {
  actualTeacherId?: number;
  actualClassroomId?: number;
  actualStartAt?: string;
  actualEndAt?: string;
  actualUnits?: number;
  notes?: string;
}

export function fetchClassGroups() {
  return http.get<ApiResponse<import('@/types/domain').ClassGroupItem[]>>('/v1/classes');
}

export function fetchClassDetail(classId: number) {
  return http.get<ApiResponse<Record<string, any>>>(`/v1/classes/${classId}`);
}

export function fetchClassStudents(classId: number) {
  return http.get<ApiResponse<import('@/types/domain').ClassEnrolledStudentItem[]>>(`/v1/classes/${classId}/students`);
}

export function fetchClassOccurrences(classId: number) {
  return http.get<ApiResponse<import('@/types/domain').ClassOccurrenceItem[]>>(`/v1/classes/${classId}/occurrences`);
}

export function confirmClassLesson(classId: number, occurrenceId: number, data: ClassGroupConfirmPayload = {}) {
  return http.post<ApiResponse<Record<string, any>>>(`/v1/classes/${classId}/occurrences/${occurrenceId}/confirm`, data);
}

export function sendClassExpiryReminders(classId: number) {
  return http.post<ApiResponse<Record<string, any>>>(`/v1/classes/${classId}/send-expiry-reminders`);
}

export function enrollClassStudent(classId: number, data: {
  studentId: number;
  enrollmentDate: string;
  expiryDate: string;
}) {
  return http.post<ApiResponse<Record<string, any>>>(`/v1/classes/${classId}/enroll`, data);
}

export function fetchEnrolledClassGroups(studentId: number) {
  return http.get<ApiResponse<any[]>>(`/v1/classes/enrolled/${studentId}`);
}

export function fetchClassGroupsByTeacher(teacherId: number) {
  return http.get<ApiResponse<any[]>>(`/v1/classes/teacher/${teacherId}`);
}

export function fetchClassGroupsByClassroom(classroomId: number) {
  return http.get<ApiResponse<any[]>>(`/v1/classes/classroom/${classroomId}`);
}