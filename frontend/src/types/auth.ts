import type { EntityId } from './domain';

export interface AuthUser {
  userId: EntityId;
  username: string;
  displayName: string;
  userType: string;
  roleCodes: string[];
  teacherId?: EntityId;
  studentId?: EntityId;
}

export function isTeacherUser(user: AuthUser | null): boolean {
  if (!user) return false;
  return user.userType === 'TEACHER' || (user.roleCodes ?? []).includes('TEACHER');
}

export function isStudentUser(user: AuthUser | null): boolean {
  if (!user) return false;
  return user.userType === 'STUDENT' || (user.roleCodes ?? []).includes('STUDENT');
}

export function isAdminUser(user: AuthUser | null): boolean {
  if (!user) return false;
  return user.userType === 'ADMIN' || (user.roleCodes ?? []).includes('ADMIN');
}

export function isStaffUser(user: AuthUser | null): boolean {
  if (!user) return false;
  return user.userType === 'STAFF' || (user.roleCodes ?? []).includes('STAFF');
}

export function isAdminOrStaffUser(user: AuthUser | null): boolean {
  return isAdminUser(user) || isStaffUser(user);
}

export function getDefaultLandingPath(user: AuthUser | null): string {
  if (isStudentUser(user) || isTeacherUser(user)) return '/calendar';
  return '/registration';
}

export interface LoginPayload {
  username: string;
  password: string;
}

export interface LoginResult {
  token: string;
  user: AuthUser;
}
