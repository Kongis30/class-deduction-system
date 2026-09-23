import axios, { type AxiosError } from 'axios';
import axiosRetry from 'axios-retry';

import { clearStoredAuthSession, getStoredAuthToken } from '@/utils/auth-storage';

interface ScheduleConflictDetail {
  resourceType?: string;
  resourceName?: string;
  conflictStartAt?: string;
  conflictEndAt?: string;
  existingLessonNo?: string;
}

/**
 * Safely converts JSON id fields to strings BEFORE JSON.parse.
 * MyBatis-Plus Snowflake IDs exceed JavaScript's safe integer range; converting
 * every id-like response field keeps frontend ID handling consistent.
 */
const ID_FIELD_REGEX = /"(\w*[iI][dD])":\s*(\d+)/g;

export function preprocessLargeIds(jsonString: string): string {
  return jsonString.replace(ID_FIELD_REGEX, '"$1":"$2"');
}

function resolveAppPath(path: string): string {
  const base = import.meta.env.BASE_URL;
  return `${base}${path.replace(/^\//, '')}`;
}

// 本地页面即使被直接以 file:// 打开，也必须只访问本机后端，避免误触线上数据。
const apiBaseURL =
  import.meta.env.VITE_API_BASE_URL ??
  (window.location.protocol === 'file:' ? 'http://127.0.0.1:8084/api' : '/api');

const http = axios.create({
  baseURL: apiBaseURL,
  timeout: 15000,
  transformResponse: [
    (data) => {
      if (!data || typeof data !== 'string') return data;
      const safeJson = preprocessLargeIds(data);
      return JSON.parse(safeJson);
    },
  ],
});

// Methods safe to auto-retry: only idempotent reads.
// Write operations (POST/PUT/PATCH/DELETE) are NOT retried automatically —
// enrollment, payment, refund and lesson confirmation have no idempotency key,
// so a lost response after a successful write could cause duplicate side effects.
const IDEMPOTENT_METHODS = new Set(['get', 'head', 'options']);

// Retry strategy: retry on 5xx and network errors (ECONNABORTED, ERR_NETWORK)
// only for idempotent methods. Retry up to 2 times with backoff (200ms, 400ms).
axiosRetry(http, {
  retries: 2,
  retryDelay: (retryCount) => retryCount * 200,
  retryCondition: (error) => {
    const method = error.config?.method?.toLowerCase();
    // Never auto-retry write operations to avoid duplicate enrollment/payment/refund.
    if (!method || !IDEMPOTENT_METHODS.has(method)) {
      return false;
    }
    if (!error.response) {
      // Network error or timeout — retry is safe for idempotent reads
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
      const loginPath = resolveAppPath('/login');
      if (window.location.pathname !== loginPath) {
        window.location.href = loginPath;
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
      return formatStructuredErrorMessage(data);
    }
    switch (error.code) {
      case 'ECONNABORTED':
        return 'Request timed out. Please check your network and try again.';
      case 'ERR_NETWORK':
        return 'Network connection failed. Please check your connection.';
      default:
        break;
    }
    switch (error.response?.status) {
      case 400: return 'Invalid request. Please check your input.';
      case 401: return 'Your session has expired. Please log in again.';
      case 403: return 'You do not have permission to perform this action.';
      case 404: return 'The requested resource was not found.';
      case 429: return 'Too many requests. Please try again later.';
      default:
        break;
    }
  }
  if (error instanceof Error) {
    return error.message || 'Operation failed. Please try again later.';
  }
  return 'Operation failed. Please try again later.';
}

function formatStructuredErrorMessage(data: Record<string, unknown>): string {
  const message = String(data.message);
  if (data.code !== 'SCHEDULE_CONFLICT' || !Array.isArray(data.data)) {
    return message;
  }
  const details = data.data
    .map(formatScheduleConflictDetail)
    .filter((line): line is string => Boolean(line));
  return details.length > 0 ? `${message} ${details.join('; ')}` : message;
}

function formatScheduleConflictDetail(detail: unknown): string | null {
  if (!detail || typeof detail !== 'object') {
    return null;
  }
  const conflict = detail as ScheduleConflictDetail;
  const resource = [conflict.resourceType, conflict.resourceName].filter(Boolean).join(' ');
  const time = formatConflictTimeRange(conflict.conflictStartAt, conflict.conflictEndAt);
  const lesson = conflict.existingLessonNo ? `existing lesson ${conflict.existingLessonNo}` : 'existing lesson';
  return [resource || 'Resource', time, lesson].filter(Boolean).join(' · ');
}

function formatConflictTimeRange(startAt?: string, endAt?: string): string {
  if (!startAt || !endAt) {
    return '';
  }
  return `${startAt.replace('T', ' ')}-${endAt.replace('T', ' ')}`;
}
