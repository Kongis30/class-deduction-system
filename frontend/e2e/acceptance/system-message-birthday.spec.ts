import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';

const admin = {
  userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'],
};

function birthdayInbox(readAt: string | null = null) {
  return {
    unreadCount: readAt ? 0 : 1,
    messages: [{
      id: '8001', messageType: 'WEEKLY_BIRTHDAY', title: 'Birthdays this week',
      weekStartDate: '2026-10-19', weekEndDate: '2026-10-25', itemCount: 2,
      students: [
        { studentId: '501', studentName: 'Ava Lin', grade: 'G5', campusName: 'North Campus', birthdayDate: '2026-10-21' },
        { studentId: '502', studentName: 'Ben Wu', grade: 'G3', campusName: 'South Campus', birthdayDate: '2026-10-24' },
      ],
      createdAt: '2026-10-19T09:00:00', readAt,
    }],
  };
}

async function mockAdminAndMessages(page: Page) {
  await page.addInitScript((user: typeof admin) => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);

  let readAt: string | null = null;
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/v1/system/messages') && route.request().method() === 'PUT') {
      readAt = '2026-10-24T12:00:00Z';
      await route.fulfill({ json: { success: true, code: 'OK', data: null } });
      return;
    }
    const data = path.endsWith('/v1/auth/me') ? admin
      : path.endsWith('/v1/system/messages') ? birthdayInbox(readAt)
        : [];
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });
}

test('admin can read the weekly birthday message and open a student account', async ({ page }) => {
  const pageErrors: string[] = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  await page.setViewportSize({ width: 1280, height: 900 });
  await mockAdminAndMessages(page);
  await page.goto('/registration');

  const bell = page.getByRole('button', { name: '1 unread system messages' });
  await expect(bell).toBeVisible();
  await bell.click();
  const dialog = page.getByRole('dialog', { name: 'System messages' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText('Oct 19 – Oct 25')).toBeVisible();
  await expect(dialog.getByText('2 students have birthdays this week')).toBeVisible();
  await expect(dialog.getByRole('columnheader')).toHaveText(['Student', 'Grade', 'Campus', 'Birthday']);
  await expect(dialog.getByRole('button', { name: 'Ava Lin' })).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Ben Wu' })).toBeVisible();
  const printPagePromise = page.waitForEvent('popup');
  await dialog.getByRole('button', { name: 'Print birthday list' }).click();
  const printPage = await printPagePromise;
  await expect(printPage.locator('h1')).toHaveText('Birthdays this week');
  await expect(printPage.getByRole('columnheader')).toHaveText(['Student', 'Grade', 'Campus', 'Birthday']);
  await expect(printPage.getByRole('row').nth(1)).toHaveText(/Ava Lin\s+G5\s+North Campus\s+Wed, Oct 21/);
  await printPage.close();
  await expect(page.getByRole('button', { name: 'System messages', exact: true })).toBeVisible();
  await page.waitForTimeout(350);
  await page.screenshot({ path: '/tmp/birthday-dialog-desktop.png', fullPage: false });

  await dialog.getByRole('button', { name: 'Ava Lin' }).click();
  await expect(page).toHaveURL(/tab=students.*studentId=501|studentId=501.*tab=students/);
  expect(pageErrors).toEqual([]);
});

test('mobile admin can open the message dialog and see the fixed birthday table', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await mockAdminAndMessages(page);
  await page.goto('/registration');

  const bell = page.getByRole('button', { name: '1 unread system messages' });
  await expect(bell).toBeVisible();
  await bell.click();
  const dialog = page.getByRole('dialog', { name: 'System messages' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('columnheader')).toHaveCount(4);
  await expect(dialog.getByRole('button', { name: 'Ava Lin' })).toBeVisible();
  await expect(dialog.getByText('Wed, Oct 21')).toBeVisible();
  await page.waitForTimeout(350);

  const dialogBox = await dialog.boundingBox();
  expect(dialogBox).not.toBeNull();
  expect(dialogBox!.x).toBeGreaterThanOrEqual(0);
  expect(dialogBox!.x + dialogBox!.width).toBeLessThanOrEqual(390);
  expect(dialogBox!.y).toBeGreaterThanOrEqual(0);
  expect(dialogBox!.y + dialogBox!.height).toBeLessThanOrEqual(844);
  await page.screenshot({ path: '/tmp/birthday-dialog-mobile.png', fullPage: false });
});

test('a week with no birthdays still shows a clear, readable weekly message', async ({ page }) => {
  await page.addInitScript((user: typeof admin) => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    const data = path.endsWith('/v1/auth/me') ? admin
      : path.endsWith('/v1/system/messages')
        ? { unreadCount: 1, messages: [{
          id: '8002', messageType: 'WEEKLY_BIRTHDAY', title: 'Birthdays this week',
          weekStartDate: '2026-10-19', weekEndDate: '2026-10-25', itemCount: 0,
          students: [], createdAt: '2026-10-19T09:00:00', readAt: null,
        }] }
        : [];
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });
  await page.setViewportSize({ width: 320, height: 740 });
  await page.goto('/registration');

  const bell = page.getByRole('button', { name: '1 unread system messages' });
  await expect(bell).toBeVisible();
  await bell.click();
  const dialog = page.getByRole('dialog', { name: 'System messages' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText('0 students have birthdays this week')).toBeVisible();
  await expect(dialog.getByText('No students have birthdays this week.')).toBeVisible();
  const dialogBox = await dialog.boundingBox();
  expect(dialogBox).not.toBeNull();
  expect(dialogBox!.x).toBeGreaterThanOrEqual(0);
  expect(dialogBox!.x + dialogBox!.width).toBeLessThanOrEqual(320);
});

test('long birthday rosters scroll inside a dialog capped to the viewport', async ({ page }) => {
  await page.addInitScript((user: typeof admin) => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    const students = Array.from({ length: 36 }, (_, index) => ({
      studentId: String(600 + index), studentName: `Student ${index + 1}`,
      grade: 'G5', campusName: 'North Campus', birthdayDate: '2026-10-21',
    }));
    const data = path.endsWith('/v1/auth/me') ? admin
      : path.endsWith('/v1/system/messages')
        ? { unreadCount: 1, messages: [{
          id: '8003', messageType: 'WEEKLY_BIRTHDAY', title: 'Birthdays this week',
          weekStartDate: '2026-10-19', weekEndDate: '2026-10-25', itemCount: students.length,
          students, createdAt: '2026-10-19T09:00:00', readAt: null,
        }] }
        : [];
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });
  await page.setViewportSize({ width: 390, height: 640 });
  await page.goto('/registration');

  await page.getByRole('button', { name: '1 unread system messages' }).click();
  const dialog = page.getByRole('dialog', { name: 'System messages' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('row')).toHaveCount(37);
  const dialogBox = await dialog.boundingBox();
  expect(dialogBox).not.toBeNull();
  expect(dialogBox!.height).toBeLessThanOrEqual(641);

  const content = dialog.locator('.system-message-dialog__body');
  const scrollMetrics = await content.evaluate(element => ({
    clientHeight: element.clientHeight,
    scrollHeight: element.scrollHeight,
  }));
  expect(scrollMetrics.scrollHeight).toBeGreaterThan(scrollMetrics.clientHeight);
});
