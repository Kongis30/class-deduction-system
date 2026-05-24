export interface AuthUser {
  userId: number;
  username: string;
  displayName: string;
  userType: string;
  roleCodes: string[];
  teacherId?: number;
  studentId?: number;
}

export function isTeacherUser(user: AuthUser | null): boolean {
  if (!user) return false;
  return user.userType === 'TEACHER' || user.roleCodes.includes('TEACHER');
}

export function isStudentUser(user: AuthUser | null): boolean {
  if (!user) return false;
  return user.userType === 'STUDENT' || user.roleCodes.includes('STUDENT');
}

export interface LoginPayload {
  username: string;
  password: string;
}

export interface LoginResult {
  token: string;
  user: AuthUser;
}
