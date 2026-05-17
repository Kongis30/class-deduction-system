import { computed, ref } from 'vue';
import { defineStore } from 'pinia';

import { fetchCurrentUser, login, logout, refreshToken } from '@/api/auth';
import type { AuthUser, LoginPayload } from '@/types/auth';
import { clearStoredAuthSession, getStoredAuthToken, getStoredAuthUser, storeAuthSession, storeAuthToken } from '@/utils/auth-storage';

export const useAuthStore = defineStore('auth', () => {
  const token = ref(getStoredAuthToken());
  const currentUser = ref<AuthUser | null>(getStoredAuthUser());
  const loading = ref(false);

  const isAuthenticated = computed(() => Boolean(token.value));
  const displayName = computed(() => currentUser.value?.displayName ?? 'Staff user');
  const roleLabel = computed(() => currentUser.value?.roleCodes.join(', ') || 'Signed in');
  const userInitials = computed(() => {
    const source = currentUser.value?.displayName?.trim() || currentUser.value?.username?.trim() || 'JBT';
    return source
      .split(/\s+/)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase() ?? '')
      .join('');
  });

  async function bootstrap() {
    token.value = getStoredAuthToken();
    currentUser.value = getStoredAuthUser();
    if (!token.value) {
      return;
    }
    try {
      const response = await fetchCurrentUser();
      currentUser.value = response.data.data;
      storeAuthSession(token.value, response.data.data);
    } catch {
      clearSession();
    }
  }

  async function signIn(payload: LoginPayload) {
    loading.value = true;
    try {
      const response = await login(payload);
      token.value = response.data.data.token;
      currentUser.value = response.data.data.user;
      storeAuthSession(token.value, response.data.data.user);
      return currentUser.value;
    } finally {
      loading.value = false;
    }
  }

  async function signOut() {
    try {
      if (token.value) {
        await logout();
      }
    } finally {
      clearSession();
    }
  }

  function clearSession() {
    token.value = '';
    currentUser.value = null;
    clearStoredAuthSession();
  }

  /**
   * 主动刷新 token：将旧 token 替换为新 token，延长会话有效期。
   * 建议在会话剩余时间不足 2 小时时调用一次。
   */
  async function refreshSession() {
    if (!token.value) return;
    try {
      const response = await refreshToken();
      token.value = response.data.data.token;
      storeAuthToken(token.value);
    } catch {
      // 刷新失败不跳转，静默等待下次 bootstrap 自然重新登录
    }
  }

  /**
   * 启动自动刷新：每 10 小时调用一次 refreshSession，
   * 确保 session 在 12h TTL 内不会因为时间流逝而失效。
   * 若前端页面长期开着（比如一天），也能自动续期。
   */
  function startAutoRefresh() {
    setInterval(refreshSession, 10 * 60 * 60 * 1000);
  }

  return {
    token,
    currentUser,
    loading,
    isAuthenticated,
    displayName,
    roleLabel,
    userInitials,
    bootstrap,
    signIn,
    signOut,
    clearSession,
    refreshSession,
    startAutoRefresh,
  };
});
