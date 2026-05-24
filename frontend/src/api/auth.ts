import http from './http';
import type { LoginPayload, LoginResult, AuthUser } from '@/types/auth';
import type { ApiResponse, PageResult } from '@/types/http';
import type { StudentAccountItem, TeacherAccountItem } from '@/types/domain';

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
  teacherName: string;
  username: string;
  password: string;
  displayName?: string;
  mobile?: string;
  email?: string;
  subjectTags?: string[];
  status?: string;
  remark?: string;
}

export function createTeacherAccount(data: TeacherAccountCreatePayload) {
  return http.post<ApiResponse<number>>('/v1/auth/teacher-accounts', data);
}

// ─── Student Accounts ───────────────────────────────

export interface StudentAccountQuery {
  keyword?: string;
  studentId?: string;
  status?: string;
  pageNum?: number;
  pageSize?: number;
}

export function fetchStudentAccounts(params: StudentAccountQuery = {}) {
  return http.get<ApiResponse<PageResult<StudentAccountItem>>>('/v1/auth/student-accounts', { params });
}

export interface StudentAccountCreatePayload {
  studentName: string;
  guardianName?: string;
  guardianPhone?: string;
  guardianEmail?: string;
  username: string;
  password: string;
  displayName?: string;
  email?: string;
  status?: string;
  remark?: string;
}

export function createStudentAccount(data: StudentAccountCreatePayload) {
  return http.post<ApiResponse<number>>('/v1/auth/student-accounts', data);
}

// ─── Account Updates ────────────────────────────────

export interface AccountUpdatePayload {
  username?: string;
  password?: string;
  displayName?: string;
  email?: string;
  mobile?: string;
  status?: string;
}

export function updateTeacherAccount(id: number, data: AccountUpdatePayload) {
  return http.put(`/v1/auth/teacher-accounts/${id}`, data);
}

export function updateStudentAccount(id: number, data: AccountUpdatePayload) {
  return http.put(`/v1/auth/student-accounts/${id}`, data);
}

// ─── Self-service Password Change ──────────────────

export function changePassword(data: { currentPassword: string; newPassword: string }) {
  return http.post<ApiResponse<null>>('/v1/auth/change-password', data);
}
