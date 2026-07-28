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


   */
  async function refreshSession() {
    if (!token.value) return;
    try {
      const response = await refreshToken();
      token.value = response.data.data.token;
      storeAuthToken(token.value);
    } catch {

    }
  }

  /**



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
