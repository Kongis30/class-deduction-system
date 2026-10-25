import http from './http';
import type { ApiResponse } from '@/types/http';
import type { EntityId } from '@/types/domain';

export interface DailyRegistrationQuery {
  fromDate: string;
  toDate: string;
  operatorUserId?: EntityId;
}

export interface DailyRegistrationRow {
  orderId: EntityId;
  orderItemId?: EntityId;
  studentId?: EntityId;
  studentName?: string;
  courseName?: string;
  courseType?: string;
  className?: string;
  grade?: string;
  campusName?: string;
  signupDate?: string;
  savedAt?: string;
  operatorUserId?: EntityId;
  operatorName?: string;
  paymentMethod?: string;
  paymentStatus?: string;
  orderStatus?: string;
  amountDue?: number;
  amountCollected?: number;
  outstandingAmount?: number;
}

export interface DailyRegistrationOperatorSummary {
  operatorUserId?: EntityId;
  operatorName?: string;
  courseRegistrationCount: number;
  orderCount: number;
  uniqueStudentCount: number;
  amountDue: number;
  amountCollected: number;
}

export interface DailyRegistrationReport {
  fromDate: string;
  toDate: string;
  courseRegistrationCount: number;
  orderCount: number;
  uniqueStudentCount: number;
  amountDue: number;
  amountCollected: number;
  outstandingAmount: number;
  operators: DailyRegistrationOperatorSummary[];
  rows: DailyRegistrationRow[];
}

export interface DailyCashFlowRow {
  transactionId: string;
  transactionType: 'PAYMENT' | 'REFUND' | 'TEACHER_PAYMENT' | string;
  direction: 'IN' | 'OUT' | string;
  happenedAt?: string;
  handledByUserId?: EntityId;
  handledByName?: string;
  counterpartyName?: string;
  paymentMethod?: string;
  amount?: number;
  status?: string;
  referenceNo?: string;
  note?: string;
  internalTransfer: boolean;
  voided: boolean;
}

export interface DailyCashFlowReport {
  fromDate: string;
  toDate: string;
  transactionCount: number;
  incomingAmount: number;
  outgoingAmount: number;
  netAmount: number;
  internalTransferAmount: number;
  rows: DailyCashFlowRow[];
}

export function fetchDailyRegistrations(params: DailyRegistrationQuery) {
  return http.get<ApiResponse<DailyRegistrationReport>>('/v1/daily-registrations', { params });
}

export function fetchDailyCashFlow(params: DailyRegistrationQuery) {
  return http.get<ApiResponse<DailyCashFlowReport>>('/v1/daily-registrations/cash-flow', { params });
}
