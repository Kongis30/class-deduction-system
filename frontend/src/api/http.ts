import axios, { type AxiosError } from 'axios';
import axiosRetry from 'axios-retry';

import { clearStoredAuthSession, getStoredAuthToken } from '@/utils/auth-storage';

/**
 * Safely converts large numeric IDs to strings BEFORE JSON.parse.
 * JavaScript's Number loses precision for values > 2^53 (MyBatis-Plus snowflake IDs).
 */
const BIG_ID_REGEX = /"(\w*[iI][dD])":\s*(\d{15,})/g;

function preprocessLargeIds(jsonString: string): string {
  return jsonString.replace(BIG_ID_REGEX, '"$1":"$2"');
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 15000,
  transformResponse: [
    (data) => {
      if (!data || typeof data !== 'string') return data;
      const safeJson = preprocessLargeIds(data);
      return JSON.parse(safeJson);
    },
  ],
});

// Retry strategy: retry on 5xx and network errors (ECONNABORTED, ERR_NETWORK)
// Retry up to 2 times with exponential backoff (200ms, 400ms)
axiosRetry(http, {
  retries: 2,
  retryDelay: (retryCount) => retryCount * 200,
  retryCondition: (error) => {
    if (!error.response) {
      // Network error or timeout — always retry
      return true;
    }
    // Retry server-side errors only
    return error.response.status >= 500;
  },
  shouldResetTimeout: true,
  onRetry: (retryCount, error) => {
    console.warn(`[http] Request failed, retry #${retryCount}:`, error.message);
  },
});

http.interceptors.request.use((config) => {
  const token = getStoredAuthToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const status = error.response?.status;
    if (status === 401) {
      clearStoredAuthSession();
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
    // Log 5xx errors for observability (do not surface to user — retry already handled)
    if (status && status >= 500) {
      console.error(`[http] Server error ${status}:`, error.request?.url);
    }
    return Promise.reject(error);
  }
);

export default http;

/**
 * Extract a user-friendly error message from an Axios error.
 * Falls back to generic messages grouped by failure category.
 */
export function extractErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as Record<string, unknown> | undefined;
    if (typeof data?.message === 'string' && data.message) {
      return data.message;
    }
    switch (error.code) {
      case 'ECONNABORTED':
        return '请求超时，请检查网络后重试。';
      case 'ERR_NETWORK':
        return '网络连接失败，请检查网络是否正常。';
      default:
        break;
    }
    switch (error.response?.status) {
      case 400: return '请求参数有误，请检查输入。';
      case 401: return '登录已过期，请重新登录。';
      case 403: return '无权限执行此操作。';
      case 404: return '请求的资源不存在。';
      case 429: return '请求过于频繁，请稍后重试。';
      default:
        break;
    }
  }
  if (error instanceof Error) {
    return error.message || '操作失败，请稍后重试。';
  }
  return '操作失败，请稍后重试。';
}