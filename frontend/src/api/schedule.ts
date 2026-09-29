import http from './http';
import type { ApiResponse, PageResult } from '@/types/http';
import type {
  ClassroomOccupancyItem,
  EntityId,
  LessonOccurrenceItem,
  PendingClassroomAssignmentItem,
  RecurringScheduleRuleDetailItem,
  RecurringScheduleRuleItem,
} from '@/types/domain';

export interface ClassHolidayItem {
  id: EntityId;
  holidayName: string;
  startDate: string;
  endDate: string;
}

export interface ClassHolidaySavePayload {
  holidayName: string;
  startDate: string;
  endDate: string;
}

export interface ClassHolidayPreview {
  affectedOccurrenceCount: number;
  affectedSessionEnrollmentCount: number;
  conflicts: string[];
}

export function fetchClassHolidays(courseProductId: EntityId, gradeBand: string) {
  return http.get<ApiResponse<ClassHolidayItem[]>>(`/v1/base/course-products/${courseProductId}/holidays`, {
    params: { gradeBand },
  });
}

export function previewClassHoliday(courseProductId: EntityId, gradeBand: string, data: ClassHolidaySavePayload, holidayId?: EntityId) {
  return http.post<ApiResponse<ClassHolidayPreview>>(`/v1/base/course-products/${courseProductId}/holidays/preview`, data, {
    params: { gradeBand, ...(holidayId ? { holidayId } : {}) },
  });
}

export function createClassHoliday(courseProductId: EntityId, gradeBand: string, data: ClassHolidaySavePayload) {
  return http.post<ApiResponse<ClassHolidayItem>>(`/v1/base/course-products/${courseProductId}/holidays`, data, {
    params: { gradeBand },
  });
}

export function updateClassHoliday(courseProductId: EntityId, gradeBand: string, id: EntityId, data: ClassHolidaySavePayload) {
  return http.put<ApiResponse<ClassHolidayItem>>(`/v1/base/course-products/${courseProductId}/holidays/${id}`, data, {
    params: { gradeBand },
  });
}

export function deleteClassHoliday(courseProductId: EntityId, gradeBand: string, id: EntityId) {
  return http.delete<ApiResponse<void>>(`/v1/base/course-products/${courseProductId}/holidays/${id}`, {
    params: { gradeBand },
  });
}

export interface ScheduleStudentOption {
  id: EntityId;
  studentName: string;
  courseGrades: Array<{
    courseName: string;
    gradeBand: string;
    packageId?: EntityId;
    packageStatus?: string;
    privateLessonGroupId?: EntityId;
    participantCount?: number;
    participantNames?: string;
    participantStudentIds?: string;
    // 旧版接口尚未返回排课状态时，该课程暂列为待排课，避免误放入已排课名单。
    hasSchedule?: boolean;
  }>;
}

export function fetchOneOnOneScheduleStudents() {
  return http.get<ApiResponse<ScheduleStudentOption[]>>('/v1/schedule/students/one-on-one');
}

// ─── Recurring Schedule Rules ─────────────────────────
export interface RecurringScheduleRuleQuery {
  keyword?: string;
  ruleStatus?: string;
  teacherId?: EntityId;
  classroomId?: EntityId;
  packageId?: EntityId;
  pageNum?: number;
  pageSize?: number;
}

export function fetchRecurringScheduleRules(params: RecurringScheduleRuleQuery = {}) {
  return http.get<ApiResponse<PageResult<RecurringScheduleRuleItem>>>('/v1/schedule/rules', { params });
}

export function fetchRecurringScheduleRule(id: EntityId) {
  return http.get<ApiResponse<RecurringScheduleRuleDetailItem>>(`/v1/schedule/rules/${id}`);
}

export interface RecurringScheduleSlotPayload {
  teacherId?: EntityId;
  deliveryMode?: 'IN_PERSON' | 'ONLINE';
  classroomId?: EntityId;
  subjectName?: string;
  weekday: number;
  startTime: string;
  endTime: string;
  plannedUnits: number;
  allocatedUnits?: number;
}

export interface RecurringScheduleRuleCreatePayload {
  packageId: EntityId;
  defaultClassroomId?: EntityId;
  startDate: string;
  notes?: string;
  slots: RecurringScheduleSlotPayload[];
}

export interface ScheduleRuleCreateResult {
  ruleId: EntityId;
  slotCount: number;
  generatedOccurrenceCount: number;
  firstGeneratedStartAt?: string;
  lastGeneratedEndAt?: string;
}

export function createRecurringScheduleRule(data: RecurringScheduleRuleCreatePayload) {
  return http.post<ApiResponse<ScheduleRuleCreateResult>>('/v1/schedule/rules', data);
}

export interface RecurringScheduleRuleUpdatePayload {
  defaultClassroomId?: EntityId;
  ruleStatus?: string;
  startDate: string;
  notes?: string;
  slots: RecurringScheduleSlotPayload[];
}

export interface ScheduleRuleUpdateResult {
  ruleId: EntityId;
  newSlotCount: number;
  preservedOccurrenceCount: number;
  deletedOccurrenceCount: number;
  generatedOccurrenceCount: number;
  confirmedOccurrenceCount: number;
  firstGeneratedStartAt?: string;
  lastGeneratedEndAt?: string;
}

export function updateRecurringScheduleRule(id: EntityId, data: RecurringScheduleRuleUpdatePayload) {
  return http.put<ApiResponse<ScheduleRuleUpdateResult>>(`/v1/schedule/rules/${id}`, data);
}

// ─── Lesson Occurrences ────────────────────────────────
export interface LessonOccurrenceQuery {
  keyword?: string;
  packageId?: string | number;
  studentId?: string | number;
  teacherId?: string | number;
  classroomId?: string | number;
  classGroupId?: string | number;
  planningStatus?: string;
  startAt?: string;
  endAt?: string;
  pageNum?: number;
  pageSize?: number;
}

export function fetchLessonOccurrences(params: LessonOccurrenceQuery = {}) {
  return http.get<ApiResponse<PageResult<LessonOccurrenceItem>>>('/v1/schedule/occurrences', {
    params: {
      ...params,
      pageSize: params.pageSize ? Math.min(params.pageSize, 200) : params.pageSize,
    },
  });
}

export function fetchLessonOccurrence(id: EntityId) {
  return http.get<ApiResponse<LessonOccurrenceItem>>(`/v1/schedule/occurrences/${id}`);
}

// ─── Schedule Adjustments ──────────────────────────────
export interface ScheduleAdjustmentPayload {
  occurrenceId: string | number;
  adjustmentType: 'RESCHEDULE' | 'CHANGE_ROOM' | 'CHANGE_TEACHER' | 'ASSIGN_RESOURCES' | 'MAKEUP_CREATE';
  plannedTeacherId?: string | number;
  plannedClassroomId?: string | number;
  plannedStartAt?: string;
  plannedEndAt?: string;
  plannedUnits?: number;
  reason?: string;
}

export interface ScheduleAdjustmentResult {
  adjustmentLogId: EntityId;
  affectedOccurrenceId: EntityId;
  createdOccurrenceId?: EntityId;
  adjustmentType: string;
}

export function createScheduleAdjustment(data: ScheduleAdjustmentPayload) {
  return http.post<ApiResponse<ScheduleAdjustmentResult>>('/v1/schedule/adjustments', data);
}


export interface WeeklyScheduleAdjustmentItemPayload {
  action: 'UPDATE' | 'CREATE' | 'DELETE';
  occurrenceId?: string | number;
  plannedStartAt?: string;
  plannedEndAt?: string;
  plannedUnits?: number;
  plannedTeacherId?: string | number;
  plannedClassroomId?: string | number;
  itemReason?: string;
}

export interface WeeklyScheduleAdjustmentPayload {
  packageId: string | number;
  weekStartDate: string;
  reason?: string;
  items: WeeklyScheduleAdjustmentItemPayload[];
}

export interface WeeklyScheduleAdjustmentResult {
  packageId: EntityId;
  weekStartDate: string;
  originalWeeklyUnits: number;
  adjustedWeeklyUnits: number;
  unitDelta: number;
  adjustedOccurrenceCount?: number;
  modifiedOccurrenceIds?: EntityId[];

  createdCount?: number;
  updatedCount?: number;
  deletedCount?: number;
}

export function adjustWeeklySchedule(data: WeeklyScheduleAdjustmentPayload) {
  return http.post<ApiResponse<WeeklyScheduleAdjustmentResult>>('/v1/schedule/adjustments/weekly', data);
}

// ─── Classroom Occupancy ──────────────────────────────
export interface ClassroomOccupancyQuery {
  classroomId?: string | number;
  teacherId?: string | number;
  startAt?: string;
  endAt?: string;
  pageNum?: number;
  pageSize?: number;
}

export function fetchClassroomOccupancy(params: ClassroomOccupancyQuery = {}) {
  return http.get<ApiResponse<PageResult<ClassroomOccupancyItem>>>('/v1/schedule/classrooms/occupancy', { params });
}

export interface PendingClassroomBackfillPayload {
  targetType: string;
  targetId: string | number;
  classroomId: string | number;
}

export function fetchPendingClassroomAssignments() {
  return http.get<ApiResponse<PendingClassroomAssignmentItem[]>>('/v1/schedule/classrooms/pending');
}

export function backfillPendingClassroom(data: PendingClassroomBackfillPayload) {
  return http.put<ApiResponse<PendingClassroomAssignmentItem>>('/v1/schedule/classrooms/pending', data);
}

// ─── Class Groups ──────────────────────────────────────
export interface ClassGroupConfirmPayload {
  resultStatus?: string;
  actualTeacherId?: string | number;
  actualClassroomId?: string | number;
  actualStartAt?: string;
  actualEndAt?: string;
  actualUnits?: number;
  notes?: string;
}

export function fetchClassGroups(params: { includeInactiveProducts?: boolean } = {}) {
  return http.get<ApiResponse<import('@/types/domain').ClassGroupItem[]>>('/v1/classes', { params });
}

export function fetchClassStudents(classId: string | number) {
  return http.get<ApiResponse<import('@/types/domain').ClassEnrolledStudentItem[]>>(`/v1/classes/${classId}/students`);
}

export function fetchClassOccurrences(classId: string | number) {
  return http.get<ApiResponse<import('@/types/domain').ClassOccurrenceItem[]>>(`/v1/classes/${classId}/occurrences`);
}

export interface ClassScheduleSlotPayload {
  teacherId?: string | number;
  subjectName?: string;
  alternateTeacherId?: string | number;
  alternateSubjectName?: string;
  alternateClassroomId?: string | number;
  alternateDeliveryMode?: 'IN_PERSON' | 'ONLINE';
  rotationAnchorDate?: string;
  classroomId?: string | number;
  deliveryMode?: 'IN_PERSON' | 'ONLINE';
  weekday: number;
  startTime: string;
  endTime: string;
  plannedUnits?: number;
}

export interface ClassScheduleUpdatePayload {
  /** 兼容旧页面；新页面应在每个 slot 中提交资源。 */
  teacherId?: string | number;
  classroomId?: string | number;
  slots: ClassScheduleSlotPayload[];
}

export interface ClassInfoPayload {
  productId?: string | number;
  gradeBand?: string;
  actualClass: string;
  className?: string;
  campusId?: EntityId;
}

export function createActualClass(data: ClassInfoPayload) {
  return http.post<ApiResponse<Record<string, any>>>('/v1/classes/actual-classes', data);
}

export function updateClassInfo(classId: string | number, data: ClassInfoPayload) {
  return http.put<ApiResponse<Record<string, any>>>(`/v1/classes/${classId}/info`, data);
}

export function updateClassSchedule(classId: string | number, data: ClassScheduleUpdatePayload) {
  return http.put<ApiResponse<Record<string, any>>>(`/v1/classes/${classId}/schedule`, data);
}

export function confirmClassLesson(classId: string | number, occurrenceId: string | number, data: ClassGroupConfirmPayload = {}) {
  return http.post<ApiResponse<Record<string, any>>>(`/v1/classes/${classId}/occurrences/${occurrenceId}/confirm`, data);
}

export function rejectClassLesson(classId: string | number, occurrenceId: string | number, data: { rejectReason: string }) {
  return http.post<ApiResponse<null>>(`/v1/classes/${classId}/occurrences/${occurrenceId}/reject`, data);
}

export function sendClassExpiryReminders(classId: string | number) {
  return http.post<ApiResponse<Record<string, any>>>(`/v1/classes/${classId}/send-expiry-reminders`);
}

export function fetchEnrolledClassGroups(studentId: string | number) {
  return http.get<ApiResponse<any[]>>(`/v1/classes/enrolled/${studentId}`);
}

export function fetchClassGroupsByClassroom(classroomId: string | number) {
  return http.get<ApiResponse<any[]>>(`/v1/classes/classroom/${classroomId}`);
}

export function fetchClassGroupsByTeacher(teacherId: string | number) {
  return http.get<ApiResponse<any[]>>(`/v1/classes/teacher/${teacherId}`);
}

export function deleteClassGroup(classId: string | number) {
  return http.delete<ApiResponse<null>>(`/v1/classes/${classId}`);
}
