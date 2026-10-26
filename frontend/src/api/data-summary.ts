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
  logicalCourseId?: EntityId;
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
  /** V2 班课报名方案的标准季节代码，优先于名称文本用于季节名单筛选。 */
  registrationTermCode?: string;
  registrationPlanId?: EntityId;
  registrationPlanName?: string;
  /** 上课形态：FULL_DAY 全天 / MORNING 上午 / AFTERNOON 下午 / CUSTOM 自定义时段；1 对 1 等不适用时为空。 */
  attendanceOption?: string;
  timeSlotLabel?: string;
  campusId?: EntityId;
  campusName?: string;
  classId?: EntityId;
  className?: string;
  courseProductId?: EntityId;
  courseProductName?: string;
  /** V2 教学项目名称；历史数据可能为空。 */
  groupProjectName?: string;
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
  /** 该订单或课程项目是否有退款流水。 */
  refunded?: boolean;
  stoppedConsumedAmount?: number;
  stoppedSessionCount?: number;
  registrationSavedAt?: string;
  registrationOperatorUserId?: EntityId;
  registrationOperatorName?: string;
  /** 整张收据状态，用于收款/退款入口和整单汇总。 */
  orderPaymentStatus: string;
  /** 课程行状态，口径与 Enrollments 的课程项一致。 */
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
  /** Data Summary display classification only; has no effect on enrollment or lesson balances. */
  discontinued?: boolean;
}

export interface DataSummaryClassificationRequest {
  orderId: EntityId;
  studentId: EntityId;
  participantIndex?: number;
  orderItemIds: EntityId[];
  discontinued: boolean;
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
  logicalCoursePeriods?: Array<{
    orderId: EntityId;
    studentId: EntityId;
    logicalCourseId: EntityId;
    startDate: string;
    endDate: string;
    segments: Array<{ startDate: string; endDate: string }>;
  }>;
}

export function fetchDataSummary(params: DataSummaryQuery = {}) {
  return http.get<ApiResponse<DataSummaryResponse>>('/v1/data-summary', { params });
}

export function updateDataSummaryClassification(request: DataSummaryClassificationRequest) {
  return http.post<ApiResponse<void>>('/v1/data-summary/classification', request);
}
