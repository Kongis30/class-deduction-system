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

export interface ScheduleStudentOption {
  id: EntityId;
  studentName: string;
  studentGrade?: string | null;
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

export interface TeacherCalendarCategoryItem {
  teacherId: EntityId;
  categoryKey: string;
  categoryName: string;
  productId?: EntityId;
  productName?: string | null;
}

export function fetchTeacherCalendarCategories() {
  return http.get<ApiResponse<TeacherCalendarCategoryItem[]>>('/v1/schedule/teachers/calendar-categories');
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

export interface SchedulePrintRevision {
  ruleId: EntityId;
  supersedesRuleId?: EntityId;
  revisionNumber: number;
  changedAt?: string;
  startDate?: string;
  endDate?: string;
  startingHours?: number;
  allocatedHours: number;
  detailsChanges?: Array<{
    changedAt?: string;
    beforeSummary: string;
    afterSummary: string;
  }>;
  slots: Array<{
    weekday: number;
    startTime: string;
    endTime: string;
    subjectName?: string;
    teacherName?: string;
    classroomName?: string;
    deliveryMode?: string;
    allocatedHours: number;
  }>;
}

export function fetchSchedulePrintHistory(packageId: EntityId) {
  return http.get<ApiResponse<SchedulePrintRevision[]>>(`/v1/schedule/rules/package/${packageId}/print-history`);
}

export interface RecurringScheduleSlotPayload {
  slotId?: EntityId;
  /** REPLAN 时可将多个旧课位的历史交给同一个新课位。 */
  inheritedSlotIds?: EntityId[];
  inheritedOccurrenceIds?: EntityId[];
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
  editOperation?: 'CURRENT' | 'REPLAN';
  defaultClassroomId?: EntityId;
  ruleStatus?: string;
  startDate: string;
  notes?: string;
  reclaimEndedUnconfirmedLessons?: boolean;
  expectedPendingLessonSnapshots?: string[];
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
  reclaimedOccurrenceCount?: number;
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

export interface DailyPrivateLesson {
  id: EntityId;
  plannedStartAt: string;
  plannedEndAt: string;
  participantNames: string[];
  participants?: { studentId: EntityId | null; packageId: EntityId | null; studentName: string }[];
  subjectName?: string;
  packageName?: string;
  plannedTeacherName?: string;
  plannedClassroomName?: string;
}

export function fetchDailyPrivateLessons(date: string) {
  return http.get<ApiResponse<{ lessons: DailyPrivateLesson[]; studentCount: number }>>(
    '/v1/schedule/occurrences/daily-private-lessons', { params: { date } },
  );
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
  subjectName?: string;
  deliveryMode?: 'IN_PERSON' | 'ONLINE';
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
  deliveryMode?: 'IN_PERSON' | 'ONLINE';
  subjectName?: string;
  itemReason?: string;
}

export interface WeeklyScheduleAdjustmentPayload {
  packageId: string | number;
  weekStartDate: string;
  reason?: string;
  releaseFutureOccurrenceIds?: Array<string | number>;
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
  slotLineageId?: string | number;
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

export interface ClassScheduleSegmentPayload {
  startDate: string;
  endDate: string;
  slots: ClassScheduleSlotPayload[];
}

export interface ClassScheduleUpdatePayload {
  /** 兼容旧页面；新页面应在每个 slot 中提交资源。 */
  teacherId?: string | number;
  classroomId?: string | number;
  slots?: ClassScheduleSlotPayload[];
  segments?: ClassScheduleSegmentPayload[];
}

export interface ClassSchedulePreview {
  classId: EntityId;
  cancelledFutureOccurrences: number;
  generatedOccurrences: number;
  futureLessonCount: number;
  futureHours: number;
  retainedFutureOccurrences: number;
  sessionEntitlementChanges: number;
  firstLessons: Array<{
    startAt: string;
    endAt: string;
    subjectName?: string;
    hours: number;
    preserved: boolean;
  }>;
}

export function previewClassSchedule(classId: EntityId, data: ClassScheduleUpdatePayload) {
  return http.post<ApiResponse<ClassSchedulePreview>>(`/v1/classes/${classId}/schedule/preview`, data);
}

export interface ClassInfoPayload {
  productId?: string | number;
  groupProjectId?: string | number;
  gradeBand?: string;
  actualClass: string;
  className?: string;
  campusId?: EntityId;
  startDate?: string;
  endDate?: string;
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
