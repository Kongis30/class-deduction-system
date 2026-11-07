import { expect, test } from '@playwright/test';

test('reschedule can assign multiple previous slot histories to one new slot', async ({ page }) => {
  const admin = { userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'] };
  const student = { id: '2001', studentName: 'Alex Kim', courseGrades: [{
    courseName: 'Math', gradeBand: 'G5', packageId: '7001', packageStatus: 'ACTIVE', hasSchedule: true,
  }] };
  const pkg = {
    id: '7001', packageNo: 'PKG-7001', studentId: '2001', studentName: 'Alex Kim',
    courseProductName: 'Math', packageName: 'Math 60 hours', teachingMode: 'ONE_ON_ONE',
    totalUnits: 120, startingHours: 60, consumedUnits: 0, remainingUnits: 120,
    receivedAmount: 0, packageStatus: 'ACTIVE',
  };
  const rule = {
    id: '5001', packageId: '7001', packageNo: 'PKG-7001', studentId: '2001', studentName: 'Alex Kim',
    packageName: pkg.packageName, ruleStatus: 'ACTIVE', startDate: '2026-09-01',
    slotCount: 3, generatedOccurrenceCount: 0, deductedHours: 0, pendingHours: 0,
    otherReservedHours: 0, preservedReservedHours: 0, startingHours: 60, pendingReclaimLessons: [],
    slots: [
      { id: '6001', teacherId: '9001', subjectName: 'Math', deliveryMode: 'ONLINE', weekday: 1,
        startTime: '14:00:00', endTime: '15:00:00', plannedUnits: 2, allocatedUnits: 40, slotStatus: 'ACTIVE' },
      { id: '6002', teacherId: '9001', subjectName: 'Math', deliveryMode: 'ONLINE', weekday: 3,
        startTime: '15:00:00', endTime: '16:00:00', plannedUnits: 2, allocatedUnits: 40, slotStatus: 'ACTIVE' },
      { id: '6003', teacherId: '9001', subjectName: 'Math', deliveryMode: 'ONLINE', weekday: 5,
        startTime: '16:00:00', endTime: '17:00:00', plannedUnits: 2, allocatedUnits: 40, slotStatus: 'ACTIVE' },
    ],
  };
  let submittedSlots: Array<{ inheritedSlotIds?: string[] }> = [];

  await page.addInitScript(user => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace(/^\/api/, '');
    const ok = (data: unknown) => route.fulfill({ json: { success: true, code: 'OK', message: 'Success', data } });
    if (route.request().method() === 'PUT' && path === '/v1/schedule/rules/5001') {
      const payload = route.request().postDataJSON();
      expect(payload.editOperation).toBe('REPLAN');
      submittedSlots = payload.slots;
      return ok({ ruleId: '5002', newSlotCount: 3, preservedOccurrenceCount: 0,
        deletedOccurrenceCount: 0, generatedOccurrenceCount: 0, confirmedOccurrenceCount: 0 });
    }
    if (path === '/v1/auth/me') return ok(admin);
    if (path === '/v1/schedule/students/one-on-one') return ok([student]);
    if (path === '/v1/enrollment/packages') return ok({ records: [pkg], total: 1, current: 1, size: 200 });
    if (path === '/v1/schedule/rules') return ok({ records: [rule], total: 1, current: 1, size: 1 });
    if (path === '/v1/schedule/rules/5001') return ok(rule);
    if (path === '/v1/schedule/occurrences/one-on-one-deductions') return ok([]);
    if (path === '/v1/schedule/occurrences') return ok({ records: [], total: 0, current: 1, size: 200 });
    if (path === '/v1/base/teachers/active') return ok([{ id: '9001', teacherName: 'Ms. Lau', status: 'ACTIVE' }]);
    if (path === '/v1/base/classrooms/active' || path === '/v1/base/campuses') return ok([]);
    return ok([]);
  });

  await page.goto('/schedule/recurring');
  await page.locator('.col-item').filter({ hasText: 'Alex Kim' }).first().click();
  await page.getByRole('button', { name: 'Edit Schedule' }).click();
  const dialog = page.getByRole('dialog', { name: 'Edit Schedule' });
  await expect(dialog).toBeVisible();
  await dialog.getByText('Reschedule Lesson').click();
  await expect(dialog.locator('.edit-schedule__sources')).toContainText('Each previous slot can be assigned only once');
  await dialog.locator('.wts-summary .wts-tag').nth(2).locator('.el-tag__close').click();
  await expect(dialog.locator('.wts-summary__title')).toContainText('Selected class slots (2)');

  const firstNewSlot = dialog.locator('.slot-teacher-row__time .el-select').nth(0);
  await firstNewSlot.click();
  await page.getByRole('option', { name: /Monday 14:00/ }).click();
  await page.getByRole('option', { name: /Wednesday 15:00/ }).click();
  await page.keyboard.press('Escape');
  const secondNewSlot = dialog.locator('.slot-teacher-row__time .el-select').nth(1);
  await secondNewSlot.click();
  await expect(page.getByRole('option', { name: /Monday 14:00/ })).toHaveCount(0);
  await page.getByRole('option', { name: /Friday 16:00/ }).click();
  await page.keyboard.press('Escape');

  await dialog.getByRole('button', { name: 'Save Changes' }).click();
  await expect(page.getByText(/Schedule updated\./)).toBeVisible();
  expect(submittedSlots).toHaveLength(2);
  expect(submittedSlots[0].inheritedSlotIds).toEqual(['6001', '6002']);
  expect(submittedSlots[1].inheritedSlotIds).toEqual(['6003']);
});
