import { expect, type APIRequestContext } from '@playwright/test';

export const API_BASE_URL = process.env.E2E_API_BASE_URL ?? 'http://127.0.0.1:8084/api';
export const ADMIN_USERNAME = process.env.E2E_ADMIN_USERNAME ?? 'admin';
export const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'Jin2025';

export interface ApiResponse<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
  timestamp: string;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  pageNum: number;
  pageSize: number;
}

export async function apiLogin(request: APIRequestContext): Promise<string> {
  const response = await request.post(`${API_BASE_URL}/v1/auth/login`, {
    data: { username: ADMIN_USERNAME, password: ADMIN_PASSWORD },
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  const body = await parseApiResponse<{ token: string }>(response);
  expect(body.success, body.message).toBe(true);
  expect(body.data.token).toBeTruthy();
  return body.data.token;
}

export async function apiGet<T>(request: APIRequestContext, token: string, path: string, params?: Record<string, unknown>): Promise<T> {
  const response = await request.get(`${API_BASE_URL}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
    params: params as Record<string, string | number | boolean> | undefined,
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  const body = await parseApiResponse<T>(response);
  expect(body.success, body.message).toBe(true);
  return body.data;
}

export async function apiPost<T>(request: APIRequestContext, token: string, path: string, data?: unknown): Promise<T> {
  const response = await request.post(`${API_BASE_URL}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
    data,
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  const body = await parseApiResponse<T>(response);
  expect(body.success, body.message).toBe(true);
  return body.data;
}

export async function apiPut<T>(request: APIRequestContext, token: string, path: string, data?: unknown): Promise<T> {
  const response = await request.put(`${API_BASE_URL}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
    data,
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  const body = await parseApiResponse<T>(response);
  expect(body.success, body.message).toBe(true);
  return body.data;
}

export async function apiDelete(request: APIRequestContext, token: string, path: string): Promise<void> {
  const response = await request.delete(`${API_BASE_URL}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  const body = await parseApiResponse<unknown>(response);
  expect(body.success, body.message).toBe(true);
}

async function parseApiResponse<T>(response: { text(): Promise<string> }): Promise<ApiResponse<T>> {
  const raw = await response.text();
  const safeJson = raw
    .replace(/"(\w*[iI][dD])":\s*(\d{15,})/g, '"$1":"$2"')
    .replace(/"data":\s*(\d{15,})/g, '"data":"$1"')
    .replace(/"packageIds":\s*\[(\d{15,})\]/g, '"packageIds":["$1"]')
    .replace(/"paymentIds":\s*\[(\d{15,})\]/g, '"paymentIds":["$1"]')
    .replace(/"receiptIds":\s*\[(\d{15,})\]/g, '"receiptIds":["$1"]');
  return JSON.parse(safeJson) as ApiResponse<T>;
}

export function todayInNewYork(): string {
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/New_York',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
  return formatter.format(new Date());
}

export function plusDays(dateText: string, days: number): string {
  const date = new Date(`${dateText}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

export function nextWeekday(fromDateText: string, weekday: number): string {
  const date = new Date(`${fromDateText}T12:00:00Z`);
  const jsDay = date.getUTCDay() || 7;
  const delta = (weekday - jsDay + 7) % 7;
  date.setUTCDate(date.getUTCDate() + delta);
  return date.toISOString().slice(0, 10);
}
