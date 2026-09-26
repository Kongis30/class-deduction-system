import http from './http';
import type { ApiResponse, PageResult } from '@/types/http';
import type { EntityId } from '@/types/domain';

export interface ManagerAccount {
  userId: EntityId;
  username: string;
  displayName: string;
  mobile?: string;
  email?: string;
  status: string;
  lastLoginAt?: string;
  remark?: string;
  registrationCount: number;
  uniqueStudentCount: number;
}

export interface ManagerAccountPayload {
  username: string;
  password: string;
  displayName: string;
  mobile?: string;
  email?: string;
  remark?: string;
}

export interface ManagerAccountUpdatePayload {
  password?: string;
  displayName?: string;
  mobile?: string;
  email?: string;
  status?: string;
  remark?: string;
}

export function fetchManagerAccounts(params: { keyword?: string; status?: string; pageNum?: number; pageSize?: number } = {}) {
  return http.get<ApiResponse<PageResult<ManagerAccount>>>('/v1/auth/manager-accounts', { params });
}

export function createManagerAccount(payload: ManagerAccountPayload) {
  return http.post<ApiResponse<EntityId>>('/v1/auth/manager-accounts', payload);
}

export function updateManagerAccount(id: EntityId, payload: ManagerAccountUpdatePayload) {
  return http.put<ApiResponse<void>>(`/v1/auth/manager-accounts/${id}`, payload);
}
