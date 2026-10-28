import { expect, test } from '@playwright/test';

test('package hours explain the change, retain a rejected draft, and refresh after saving', async ({ page }) => {
  const admin = { userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'] };
  const student = { id: '2001', studentName: 'Alex Kim', courseGrades: [{
    courseName: 'Math', gradeBand: 'G5', packageId: '7001', packageStatus: 'ACTIVE', hasSchedule: true,
  }] };
  const pkg = {
    id: '7001', version: 1, packageNo: 'PKG-7001', enrollmentOrderId: '8001', studentId: '2001',
    studentName: 'Alex Kim', courseProductId: '3001', courseProductName: 'Math', packageName: 'Math 120 hours',
    teachingMode: 'ONE_ON_ONE', totalUnits: 120, startingHours: 19.5, consumedUnits: 117.5,
    openingUsedHours: 100.5, pendingDeductionUnits: 0, pendingAdminDeductionUnits: 0,
    remainingUnits: 2.5, receivedAmount: 0, packageStatus: 'ACTIVE',
  };
  const rule = {
    id: '5001', packageId: '7001', packageName: pkg.packageName, studentId: '2001', studentName: student.studentName,
    ruleStatus: 'ACTIVE', startDate: '2026-10-01', slots: [], slotCount: 0,
  };
  let saveCount = 0;
  await page.addInitScript(user => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace(/^\/api/, '');
    const ok = (data: unknown) => route.fulfill({ json: { success: true, code: 'OK', message: 'Success', data } });
    if (route.request().method() === 'PUT' && path === '/v1/enrollment/packages/7001/starting-hours') {
      saveCount += 1;
      expect(route.request().postDataJSON()).toEqual({ startingHours: saveCount === 3 ? 18.5 : 19, expectedVersion: saveCount === 3 ? 2 : 1 });
      if (saveCount === 3) return route.abort('failed');
      if (saveCount === 1) {
        return route.fulfill({ status: 400, json: { success: false, code: 'AMBIGUOUS_SLOT_LINEAGE', message: 'Technical slot error' } });
      }
      Object.assign(pkg, { version: 2, startingHours: 19, consumedUnits: 118, openingUsedHours: 101, remainingUnits: 2 });
      return ok(null);
    }
    if (path === '/v1/auth/me') return ok(admin);
    if (path === '/v1/schedule/students/one-on-one') return ok([student]);
    if (path === '/v1/enrollment/packages') return ok({ records: [pkg], total: 1, current: 1, size: 200 });
    if (path === '/v1/schedule/rules') return ok({ records: [rule], total: 1, current: 1, size: 1 });
    if (path === '/v1/schedule/rules/5001') return ok(rule);
    if (path === '/v1/schedule/occurrences') return ok({ records: [], total: 0, current: 1, size: 200 });
    return ok([]);
  });

  await page.goto('/schedule/recurring');
  await page.locator('.col-item').filter({ hasText: 'Alex Kim' }).first().click();
  await page.getByRole('button', { name: 'More', exact: true }).click();
  await page.getByText('Package hours', { exact: true }).last().click();
  const dialog = page.getByRole('dialog', { name: /Package hours/ });
  await expect(dialog).toBeVisible();
  const input = dialog.getByRole('spinbutton');
  await input.fill('19');
  await input.press('Tab');
  await expect(dialog.locator('.package-hours-card__impact')).toContainText('Starting 19.5h → 19.0h');
  await expect(dialog.locator('.package-hours-card__impact')).toContainText('Remaining 2.5h → 2.0h');
  await dialog.getByRole('button', { name: 'Save hours' }).click();
  await expect(dialog.locator('.package-hours-card__error')).toContainText('Hours were not saved');
  await expect(dialog.locator('.package-hours-card__error')).toContainText('Contact support');
  await expect(input).toHaveValue('19.0');
  await dialog.getByRole('button', { name: 'Save hours' }).click();
  await expect(page.getByText('Package hours and future schedule updated.')).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Save hours' })).toBeDisabled();
  await input.fill('18.5');
  await input.press('Tab');
  await dialog.getByRole('button', { name: 'Save hours' }).click();
  await expect(dialog.locator('.package-hours-card__error')).toContainText('Could not confirm whether the hours were saved');
  await expect(dialog.getByRole('button', { name: 'Save hours' })).toBeDisabled();
  expect(saveCount).toBe(3);
});

test('saving a zero balance explains removal and confirms the completed package', async ({ page }) => {
  const admin = { userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'] };
  const student = { id: '2001', studentName: 'Alex Kim', courseGrades: [{
    courseName: 'Math', gradeBand: 'G5', packageId: '7001', packageStatus: 'ACTIVE', hasSchedule: true,
  }] };
  const pkg = {
    id: '7001', version: 1, packageNo: 'PKG-7001', enrollmentOrderId: '8001', studentId: '2001',
    studentName: 'Alex Kim', courseProductId: '3001', courseProductName: 'Math', packageName: 'Math 120 hours',
    teachingMode: 'ONE_ON_ONE', totalUnits: 120, startingHours: 19.5, consumedUnits: 117.5,
    openingUsedHours: 100.5, pendingDeductionUnits: 0, pendingAdminDeductionUnits: 0,
    remainingUnits: 2.5, receivedAmount: 0, packageStatus: 'ACTIVE',
  };
  const rule = { id: '5001', packageId: '7001', packageName: pkg.packageName, studentId: '2001',
    studentName: student.studentName, ruleStatus: 'ACTIVE', startDate: '2026-10-01', slots: [], slotCount: 0 };
  let saved = false;
  let attempts = 0;
  await page.addInitScript(user => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace(/^\/api/, '');
    const ok = (data: unknown) => route.fulfill({ json: { success: true, code: 'OK', message: 'Success', data } });
    if (route.request().method() === 'PUT' && path === '/v1/enrollment/packages/7001/starting-hours') {
      expect(route.request().postDataJSON()).toEqual({ startingHours: 17, expectedVersion: 1 });
      attempts += 1;
      if (attempts === 1) {
        return route.fulfill({ status: 400, json: { success: false, code: 'ZERO_BALANCE_LESSON_REVIEW_REQUIRED',
          message: 'Review the lesson on 10/28/2026 8:00 PM in 1-on-1 Lessons before setting remaining hours to 0.' } });
      }
      if (attempts === 2) {
        return route.fulfill({ status: 400, json: { success: false, code: 'ZERO_BALANCE_SHARED_SCHEDULE',
          message: 'This student has lessons with other students. Review their shared schedule before setting remaining hours to 0.' } });
      }
      if (attempts === 3) {
        return route.fulfill({ status: 400, json: { success: false, code: 'UNKNOWN_ERROR', message: 'Internal slot mapping failure' } });
      }
      saved = true;
      return ok(null);
    }
    if (path === '/v1/auth/me') return ok(admin);
    if (path === '/v1/schedule/students/one-on-one') return ok([student]);
    if (path === '/v1/enrollment/packages') return ok({ records: saved ? [] : [pkg], total: saved ? 0 : 1, current: 1, size: 200 });
    if (path === '/v1/schedule/rules') return ok({ records: [rule], total: 1, current: 1, size: 1 });
    if (path === '/v1/schedule/rules/5001') return ok(rule);
    if (path === '/v1/schedule/occurrences') return ok({ records: [], total: 0, current: 1, size: 200 });
    return ok([]);
  });
  await page.goto('/schedule/recurring');
  await page.locator('.col-item').filter({ hasText: 'Alex Kim' }).first().click();
  await page.getByRole('button', { name: 'More', exact: true }).click();
  await page.getByText('Package hours', { exact: true }).last().click();
  const dialog = page.getByRole('dialog', { name: /Package hours/ });
  const input = dialog.getByRole('spinbutton');
  await input.fill('17');
  await input.press('Tab');
  await expect(dialog.locator('.package-hours-card__impact')).toContainText('Remaining 2.5h → 0.0h');
  await expect(dialog.locator('.package-hours-card__impact')).toContainText('Upcoming regular lessons will be removed');
  await page.setViewportSize({ width: 390, height: 844 });
  await dialog.getByRole('button', { name: 'Save hours' }).click();
  const error = dialog.locator('.package-hours-card__error');
  await expect(error).toContainText('Hours were not saved.');
  await expect(error).toContainText('10/28/2026 8:00 PM in 1-on-1 Lessons');
  await error.scrollIntoViewIfNeeded();
  await expect(error).toBeVisible();
  await expect.poll(() => error.evaluate(element => {
    const rect = element.getBoundingClientRect();
    return rect.left >= 0 && rect.right <= window.innerWidth && element.scrollWidth <= element.clientWidth + 1;
  })).toBe(true);
  await input.fill('17.5');
  await input.press('Tab');
  await expect(error).toHaveCount(0);
  await input.fill('17');
  await input.press('Tab');
  await dialog.getByRole('button', { name: 'Save hours' }).click();
  await expect(error).toContainText('This student has lessons with other students.');
  await expect(error).toContainText('Review their shared schedule');
  await dialog.getByRole('button', { name: 'Save hours' }).click();
  await expect(error).toContainText('Please try again. If the problem continues, contact support for help.');
  await expect(error).not.toContainText('Internal slot');
  await dialog.getByRole('button', { name: 'Save hours' }).click();
  await expect(dialog).toContainText('The package has no remaining hours and is now completed.');
});

test('a stale package prompts the user to refresh and prevents another save', async ({ page }) => {
  const admin = { userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'] };
  const student = { id: '2001', studentName: 'Alex Kim', courseGrades: [{
    courseName: 'Math', gradeBand: 'G5', packageId: '7001', packageStatus: 'ACTIVE', hasSchedule: true,
  }] };
  const pkg = { id: '7001', version: 7, packageNo: 'PKG-7001', enrollmentOrderId: '8001', studentId: '2001',
    studentName: 'Alex Kim', courseProductId: '3001', courseProductName: 'Math', packageName: 'Math 120 hours',
    teachingMode: 'ONE_ON_ONE', totalUnits: 120, startingHours: 19.5, consumedUnits: 117.5,
    openingUsedHours: 100.5, pendingDeductionUnits: 0, pendingAdminDeductionUnits: 0,
    remainingUnits: 2.5, receivedAmount: 0, packageStatus: 'ACTIVE' };
  const rule = { id: '5001', packageId: '7001', packageName: pkg.packageName, studentId: '2001',
    studentName: student.studentName, ruleStatus: 'ACTIVE', startDate: '2026-10-01', slots: [], slotCount: 0 };
  await page.addInitScript(user => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace(/^\/api/, '');
    const ok = (data: unknown) => route.fulfill({ json: { success: true, code: 'OK', message: 'Success', data } });
    if (route.request().method() === 'PUT' && path === '/v1/enrollment/packages/7001/starting-hours') {
      expect(route.request().postDataJSON()).toEqual({ startingHours: 19, expectedVersion: 7 });
      return route.fulfill({ status: 400, json: { success: false, code: 'PACKAGE_CONCURRENT_UPDATE', message: 'stale' } });
    }
    if (path === '/v1/auth/me') return ok(admin);
    if (path === '/v1/schedule/students/one-on-one') return ok([student]);
    if (path === '/v1/enrollment/packages') return ok({ records: [pkg], total: 1, current: 1, size: 200 });
    if (path === '/v1/schedule/rules') return ok({ records: [rule], total: 1, current: 1, size: 1 });
    if (path === '/v1/schedule/rules/5001') return ok(rule);
    if (path === '/v1/schedule/occurrences') return ok({ records: [], total: 0, current: 1, size: 200 });
    return ok([]);
  });
  await page.goto('/schedule/recurring');
  await page.locator('.col-item').filter({ hasText: 'Alex Kim' }).first().click();
  await page.getByRole('button', { name: 'More', exact: true }).click();
  await page.getByText('Package hours', { exact: true }).last().click();
  const dialog = page.getByRole('dialog', { name: /Package hours/ });
  await dialog.getByRole('spinbutton').fill('19');
  await dialog.getByRole('spinbutton').press('Tab');
  await dialog.getByRole('button', { name: 'Save hours' }).click();
  await expect(dialog.locator('.package-hours-card__error')).toContainText('The hours or lessons changed while you were editing. Refresh the page and try again.');
  await expect(dialog.getByRole('button', { name: 'Save hours' })).toBeDisabled();
});
