import { expect, type Page } from '@playwright/test';

import { ADMIN_PASSWORD, ADMIN_USERNAME, API_BASE_URL } from '../../helpers/api';

export interface LoginCredentials {
  username: string;
  password: string;
}

const routedPages = new WeakSet<Page>();

export async function loginAsAdmin(page: Page): Promise<void> {
  await loginWithCredentials(page, { username: ADMIN_USERNAME, password: ADMIN_PASSWORD });
}

export async function loginAsTeacher(page: Page, credentials: LoginCredentials): Promise<void> {
  await loginWithCredentials(page, credentials);
}

export async function switchUser(page: Page, credentials: LoginCredentials): Promise<void> {
  await clearAuthSession(page);
  await loginWithCredentials(page, credentials);
}

export async function clearAuthSession(page: Page): Promise<void> {
  // 同一用例切换角色时，先清理前端会话，避免旧 token 被路由守卫复用。
  await page.goto('/login');
  await page.evaluate(() => {
    window.localStorage.removeItem('jbt_auth_token');
    window.localStorage.removeItem('jbt_auth_user');
  });
}

export async function loginWithCredentials(
  page: Page,
  credentials: LoginCredentials,
  expectedUrl?: string | RegExp,
): Promise<void> {
  await ensureE2EApiRouting(page);
  const session = await loginByApi(page, credentials);

  await page.goto('/login');
  await page.evaluate(({ token, user }) => {
    window.localStorage.setItem('jbt_auth_token', token);
    window.localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, session);

  // 业务 E2E 只需要稳定建立角色会话；登录页本身由独立用例覆盖。
  const targetUrl = typeof expectedUrl === 'string' ? expectedUrl : defaultLandingPath(session.user.userType);
  await page.goto(targetUrl);
  if (expectedUrl instanceof RegExp) {
    await expect(page).toHaveURL(expectedUrl);
    return;
  }
  await expect(page).not.toHaveURL(/\/login\/?(?:[?#].*)?$/);
}

async function loginByApi(
  page: Page,
  credentials: LoginCredentials,
): Promise<{ token: string; user: Record<string, any> }> {
  const response = await page.request.post(`${API_BASE_URL}/v1/auth/login`, { data: credentials });
  const raw = await response.text();
  expect(response.ok(), raw).toBeTruthy();
  const body = JSON.parse(preprocessLargeIds(raw)) as {
    success: boolean;
    message: string;
    data: { token: string; user: Record<string, any> };
  };
  expect(body.success, body.message).toBe(true);
  expect(body.data.token).toBeTruthy();
  return body.data;
}

function defaultLandingPath(userType: string): string {
  if (userType === 'TEACHER') return '/schedule/teacher';
  if (userType === 'STUDENT') return '/schedule/student';
  return '/dashboard';
}

function preprocessLargeIds(jsonString: string): string {
  return jsonString
    .replace(/"(\w*[iI][dD])":\s*(\d{15,})/g, '"$1":"$2"')
    .replace(/"data":\s*(\d{15,})/g, '"data":"$1"');
}

async function ensureE2EApiRouting(page: Page): Promise<void> {
  if (routedPages.has(page)) return;

  const apiBase = new URL(API_BASE_URL);
  const apiBasePath = apiBase.pathname.replace(/\/$/, '');
  await page.route('**/api/**', async (route) => {
    const originalUrl = new URL(route.request().url());
    if (!originalUrl.pathname.startsWith('/api/')) {
      await route.continue();
      return;
    }

    const apiPath = originalUrl.pathname.replace(/^\/api/, '');
    const headers = { ...route.request().headers() };
    delete headers.origin;
    delete headers.referer;
    delete headers['access-control-request-headers'];
    delete headers['access-control-request-method'];

    const response = await route.fetch({
      url: `${apiBase.origin}${apiBasePath}${apiPath}${originalUrl.search}`,
      headers,
    });
    await route.fulfill({ response });
  });
  routedPages.add(page);
}
