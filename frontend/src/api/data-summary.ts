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
  signupDate?: string;
  periodStartDate?: string;
  periodEndDate?: string;
  periodLabel?: string;
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
  paymentStatus: string;
  linePriceAmount?: number;
  pickupFeeAmount: number;
  extraFeeAmount: number;
  discountAmount: number;
  amountDue: number;
  paidAmount: number;
  balanceAmount: number;
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
