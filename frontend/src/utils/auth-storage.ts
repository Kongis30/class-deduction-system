import type { AuthUser } from '@/types/auth';

const AUTH_TOKEN_KEY = 'jbt_auth_token';
const AUTH_USER_KEY = 'jbt_auth_user';

function isBrowser() {
  return typeof window !== 'undefined';
}

export function getStoredAuthToken() {
  if (!isBrowser()) {
    return '';
  }
  return window.localStorage.getItem(AUTH_TOKEN_KEY) ?? '';
}

export function getStoredAuthUser() {
  if (!isBrowser()) {
    return null;
  }
  const raw = window.localStorage.getItem(AUTH_USER_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as AuthUser;
  } catch {
    clearStoredAuthSession();
    return null;
  }
}

export function storeAuthSession(token: string, user: AuthUser) {
  if (!isBrowser()) {
    return;
  }
  window.localStorage.setItem(AUTH_TOKEN_KEY, token);
  window.localStorage.setItem(AUTH_USER_KEY, JSON.stringify(user));
}

export function storeAuthToken(token: string) {
  if (!isBrowser()) {
    return;
  }
  window.localStorage.setItem(AUTH_TOKEN_KEY, token);
}

export function clearStoredAuthSession() {
  if (!isBrowser()) {
    return;
  }
  window.localStorage.removeItem(AUTH_TOKEN_KEY);
  window.localStorage.removeItem(AUTH_USER_KEY);
}
