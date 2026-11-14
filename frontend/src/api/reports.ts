import http from './http';
import type { ApiResponse } from '@/types/http';
import type { EntityId } from '@/types/domain';

export interface MonthlyRevenueQuery {
  fromMonth?: string;
  toMonth?: string;
}

export interface MonthlyRevenueMonthColumn {
  key: string;
  label: string;
}

export interface MonthlyRevenueRow {
  productId: EntityId | null;
  productName: string;
  amounts: number[];
}

export interface MonthlyRevenueSection {
  key: string;
  title: string;
  oneOnOne: boolean;
  rows: MonthlyRevenueRow[];
}

export interface MonthlyRevenueReport {
  months: MonthlyRevenueMonthColumn[];
  sections: MonthlyRevenueSection[];
  /** 无法匹配到课程产品、以 Unassigned product 展示的流水笔数。 */
  unassignedPaymentCount: number;
}

export function fetchMonthlyRevenue(params: MonthlyRevenueQuery) {
  return http.get<ApiResponse<MonthlyRevenueReport>>('/v1/reports/monthly-revenue', { params });
}
