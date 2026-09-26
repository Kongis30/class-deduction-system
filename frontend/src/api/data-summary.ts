import http from './http';
import type { ApiResponse } from '@/types/http';
import type { EntityId } from '@/types/domain';

export interface DataSummaryQuery {
  keyword?: string;
  campusId?: EntityId;
  courseType?: string;
  classId?: EntityId;
  courseProductId?: EntityId;
  periodStartDate?: string;
  periodEndDate?: string;
  paymentStatus?: string;
  onlyAlerts?: boolean;
  onlyNeedsReview?: boolean;
  transportOnly?: boolean;
  includeInactiveOrders?: boolean;
  savedAtFrom?: string;
  savedAtBefore?: string;
  registrationOperatorUserId?: EntityId;
}

export interface DataSummaryGroup {
  groupKey: string;
  groupName: string;
  recordCount: number;
  studentCount: number;
  incomeAmount: number;
  outstandingAmount: number;
}

export interface DataSummaryRow {
  orderId: EntityId;
  enrollmentNo: string;
  orderItemId?: EntityId;
  studentId: EntityId;
  participantIndex?: number;
  studentName?: string;
  studentClass?: string;
  school?: string;
  grade?: string;
  /** Price-option grade/season label snapshot (legacy Saturday rows store the season here). */
  gradeBand?: string;
  signupDate?: string;
  periodStartDate?: string;
  periodEndDate?: string;
  periodLabel?: string;
  /** 上课形态：FULL_DAY 全天 / MORNING 上午 / AFTERNOON 下午 / CUSTOM 自定义时段；1 对 1 等不适用时为空。 */
  attendanceOption?: string;
  timeSlotLabel?: string;
  campusId?: EntityId;
  campusName?: string;
  classId?: EntityId;
  className?: string;
  courseProductId?: EntityId;
  courseProductName?: string;
  registerClass?: string;
  courseType?: string;
  privateLessonGroupId?: EntityId;
  privateLessonParticipantCount?: number;
  detail?: string;
  phone?: string;
  phone2?: string;
  pickupRequired: boolean;
  pickupAddress?: string;
  dropoffRequired: boolean;
  dropoffAddress?: string;
  pickupDropoffLabel?: string;
  paymentMethod?: string;
  orderStatus?: string;
  itemStatus?: string;
  stoppedConsumedAmount?: number;
  stoppedSessionCount?: number;
  registrationSavedAt?: string;
  registrationOperatorUserId?: EntityId;
  registrationOperatorName?: string;
  paymentStatus: string;
  linePriceAmount?: number;
  pickupFeeAmount?: number;
  dropoffFeeAmount?: number;
  extraFeeAmount?: number;
  discountAmount?: number;
  amountDue?: number;
  paidAmount?: number;
  balanceAmount?: number;
  totalHours?: number;
  usedHours?: number;
  remainingHours?: number;
  teacherId?: EntityId;
  teacherName?: string;
  expiryDate?: string;
  overdue: boolean;
  expiringSoon: boolean;
  lowOneOnOneHours: boolean;
  alertTypes: string[];
  reviewIssueCodes?: string[];
}

export interface DataSummaryResponse {
  totalRegistrationCount: number;
  totalIncomeAmount: number;
  totalOutstandingAmount: number;
  campusSummaries: DataSummaryGroup[];
  courseTypeSummaries: DataSummaryGroup[];
  classCourseSummaries: DataSummaryGroup[];
  paymentStatusSummaries: DataSummaryGroup[];
  rows: DataSummaryRow[];
}

export function fetchDataSummary(params: DataSummaryQuery = {}) {
  return http.get<ApiResponse<DataSummaryResponse>>('/v1/data-summary', { params });
}
