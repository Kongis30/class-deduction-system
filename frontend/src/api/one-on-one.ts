import http from './http';
import type { ApiResponse } from '@/types/http';
import type { EntityId } from '@/types/domain';

export interface OneOnOneDeductionQuery {
  keyword?: string;
  teacherId?: EntityId;
  studentId?: EntityId;
  packageId?: EntityId;
  startAt?: string;
  endAt?: string;
}

export interface OneOnOneLesson {
  occurrenceId: EntityId;
  lessonRecordId?: EntityId;
  lessonNo: string;
  packageId: EntityId;
  plannedTeacherId?: EntityId;
  plannedTeacherName?: string;
  subjectName?: string;
  deliveryMode?: 'IN_PERSON' | 'ONLINE' | string;
  plannedClassroomId?: EntityId;
  plannedClassroomName?: string;
  plannedStartAt: string;
  plannedEndAt: string;
  plannedUnits: number;
  planningStatus: string;
  resultStatus?: string;
  resultStatusName?: string;
  confirmStage?: string;
  actualTeacherId?: EntityId;
  actualTeacherName?: string;
  actualClassroomId?: EntityId;
  actualClassroomName?: string;
  actualStartAt?: string;
  actualEndAt?: string;
  actualUnits?: number;
  deductedUnits?: number;
  teacherConfirmedAt?: string;
  adminConfirmedAt?: string;
  rejectReason?: string;
  notes?: string;
}

export interface OneOnOneParticipant {
  studentId: EntityId;
  studentNo?: string;
  studentName?: string;
  packageId: EntityId;
  priceMultiplier?: number;
  remainingUnits?: number;
}

export interface OneOnOnePackage {
  packageId: EntityId;
  packageNo: string;
  courseProductId?: EntityId;
  courseProductName?: string;
  subjectName?: string;
  gradeBand?: string;
  packageName: string;
  totalUnits: number;
  purchasedUnits?: number;
  bonusUnits?: number;
  consumedUnits: number;
  remainingUnits: number;
  renewalReminder: boolean;
  packageStatus: string;
  startDate?: string;
  estimatedEndDate?: string;
  privateLessonGroupId?: EntityId;
  participantCount?: number;
  privateLessonGroupPrimary?: boolean;
  participants?: OneOnOneParticipant[];
  lessons: OneOnOneLesson[];
}

export interface OneOnOneStudent {
  studentId: EntityId;
  studentNo?: string;
  studentName: string;
  grade?: string;
  school?: string;
  totalUnits: number;
  consumedUnits: number;
  remainingUnits: number;
  renewalReminder: boolean;
  packages: OneOnOnePackage[];
}

export interface OneOnOneTeacher {
  teacherId?: EntityId;
  teacherNo?: string;
  teacherName: string;
  totalUnits: number;
  consumedUnits: number;
  remainingUnits: number;
  studentCount: number;
  packageCount: number;
  students: OneOnOneStudent[];
}

export function fetchOneOnOneDeductions(params: OneOnOneDeductionQuery = {}) {
  return http.get<ApiResponse<OneOnOneTeacher[]>>('/v1/schedule/occurrences/one-on-one-deductions', { params });
}
