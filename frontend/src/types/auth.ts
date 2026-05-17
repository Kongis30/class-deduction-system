export interface AuthUser {
  userId: number;
  username: string;
  displayName: string;
  userType: string;
  roleCodes: string[];
}

export function isTeacherUser(user: AuthUser | null): boolean {
  return user?.userType === 'TEACHER' || user?.roleCodes.includes('TEACHER');
}

export interface LoginPayload {
  username: string;
  password: string;
}

export interface LoginResult {
  token: string;
  user: AuthUser;
}
