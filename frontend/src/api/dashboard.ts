import http from './http';
import type { ApiResponse } from '@/types/http';
import type { DashboardSummary } from '@/types/domain';

export function fetchDashboardSummary() {
  return http.get<ApiResponse<DashboardSummary>>('/v1/dashboard/summary');
}

