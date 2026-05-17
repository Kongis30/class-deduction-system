import http from './http';
import type { LoginPayload, LoginResult, AuthUser } from '@/types/auth';
import type { ApiResponse, PageResult } from '@/types/http';
import type { TeacherAccountItem } from '@/types/domain';

export function login(payload: LoginPayload) {
  return http.post<ApiResponse<LoginResult>>('/v1/auth/login', payload);
}

export function fetchCurrentUser() {
  return http.get<ApiResponse<AuthUser>>('/v1/auth/me');
}

export function logout() {
  return http.post<ApiResponse<null>>('/v1/auth/logout');
}

export function refreshToken() {
  return http.post<ApiResponse<LoginResult>>('/v1/auth/refresh');
}

// ─── Teacher Accounts ───────────────────────────────

export interface TeacherAccountQuery {
  keyword?: string;
  teacherId?: string;
  status?: string;
  pageNum?: number;
  pageSize?: number;
}

export function fetchTeacherAccounts(params: TeacherAccountQuery = {}) {
  return http.get<ApiResponse<PageResult<TeacherAccountItem>>>('/v1/auth/teacher-accounts', { params });
}

export interface TeacherAccountCreatePayload {
  teacherId: number;
  username: string;
  password: string;
  displayName?: string;
  mobile?: string;
  email?: string;
  status?: string;
  remark?: string;
}

export function createTeacherAccount(data: TeacherAccountCreatePayload) {
  return http.post<ApiResponse<number>>('/v1/auth/teacher-accounts', data);
}
