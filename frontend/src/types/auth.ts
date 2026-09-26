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

/**
 * A login account has exactly one workspace identity.  This must come from
 * `userType`; role codes authorize actions but never change the workspace.
 */
export type UserWorkspace = 'TEACHER' | 'STUDENT' | 'STAFF';

export function getUserWorkspace(user: AuthUser | null): UserWorkspace {
  switch (user?.userType) {
    case 'TEACHER':
      return 'TEACHER';
    case 'STUDENT':
      return 'STUDENT';
    default:
      return 'STAFF';
  }
}

export function isTeacherUser(user: AuthUser | null): boolean {
  return getUserWorkspace(user) === 'TEACHER';
}

export function isStudentUser(user: AuthUser | null): boolean {
  return getUserWorkspace(user) === 'STUDENT';
}

export function isAdminUser(user: AuthUser | null): boolean {
  if (!user) return false;
  return user.userType === 'ADMIN' || (user.roleCodes ?? []).includes('ADMIN');
}

export function isStaffUser(user: AuthUser | null): boolean {
  if (!user) return false;
  return user.userType === 'STAFF' || (user.roleCodes ?? []).includes('STAFF');
}

export function isManagerUser(user: AuthUser | null): boolean {
  return Boolean(user && (user.roleCodes ?? []).includes('MANAGER'));
}

export function isAdminOrStaffUser(user: AuthUser | null): boolean {
  return isAdminUser(user) || isStaffUser(user);
}

export function getDefaultLandingPath(user: AuthUser | null): string {
  switch (getUserWorkspace(user)) {
    case 'TEACHER':
    case 'STUDENT':
      return '/calendar';
    default:
      return '/registration';
  }
}

export interface LoginPayload {
  username: string;
  password: string;
}

export interface LoginResult {
  token: string;
  user: AuthUser;
}
