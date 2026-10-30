import { expect, test } from '@playwright/test';
import dayjs from 'dayjs';

test('Edit Schedule shows the exact allocation shortfall and clears it after correction', async ({ page }) => {
  const admin = {
    userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'],
  };
  const student = { id: '2001', studentName: 'Alex Kim', studentGrade: 'G5', courseGrades: [{
    courseName: 'Math', gradeBand: 'G5', packageId: '7001', packageStatus: 'ACTIVE', hasSchedule: true,
  }] };
  const pkg = {
    id: '7001', packageNo: 'PKG-7001', enrollmentOrderId: '8001', studentId: '2001',
    studentName: 'Alex Kim', courseProductId: '3001', courseProductName: 'Math', packageName: 'Math 40 hours',
    teachingMode: 'ONE_ON_ONE', totalUnits: 40, startingHours: 40, consumedUnits: 16,
    openingUsedHours: 0, pendingAdminDeductionUnits: 2, remainingUnits: 24,
    receivedAmount: 0, packageStatus: 'ACTIVE',
  };
  const rule = {
    id: '5001', packageId: '7001', packageNo: 'PKG-7001', studentId: '2001', studentName: 'Alex Kim',
    packageName: 'Math 40 hours', ruleStatus: 'ACTIVE', startDate: '2026-10-01',
    slotCount: 1, generatedOccurrenceCount: 10, deductedHours: 8, pendingHours: 2,
    otherReservedHours: 0, preservedReservedHours: 0, startingHours: 40,
    unlinkedHistory: [{ occurrenceId: '2104217509483360257', subjectName: 'Vocabulary', teacherName: 'Ms. Lau', plannedStartAt: '2026-09-27T20:00:00' }],
    pendingReclaimLessons: [
      { occurrenceId: '8101', snapshot: 'old-period-snapshot', subjectName: 'ELA', teacherName: 'Ms. Lau', plannedStartAt: '2026-09-27T18:00:00', plannedEndAt: '2026-09-27T20:00:00', plannedHours: 2, status: 'AWAITING_TEACHER', inProgress: false },
      { occurrenceId: '8102', snapshot: 'current-period-snapshot', subjectName: 'Math', teacherName: 'Emma Wang', plannedStartAt: '2026-10-02T18:00:00', plannedEndAt: '2026-10-02T20:00:00', plannedHours: 2, status: 'PENDING_ADMIN', inProgress: false },
    ],
    slots: [{
      id: '6001', teacherId: '9001', teacherName: 'Emma Wang', subjectName: 'Math',
      deliveryMode: 'ONLINE', weekday: 1, startTime: '18:00:00', endTime: '20:00:00',
      plannedUnits: 4, allocatedUnits: 80, rolloverAllocatedUnits: 0, slotStatus: 'ACTIVE',
    }],
  };
  const allocationDetails = {
    packageTotalHours: 40,
    packageRemainingHours: 24,
    requestedAllocationHours: 30,
    availableAllocationHours: 22,
    shortfallHours: 8,
    pendingReviewHours: 2,
    otherScheduleReservedHours: 3,
    preservedLessonHours: 1,
    transferredFutureLessonHours: 4,
    currentRuleRetainedHours: 0,
  };
  let updateCount = 0;
  const futureStart = dayjs().add(30, 'day').format('YYYY-MM-DD');
  await page.addInitScript(user => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);
  // Limit interception to backend endpoints; Vite source modules also live under /src/api/.
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace(/^\/api/, '');
    const ok = (data: unknown) => route.fulfill({
      json: { success: true, code: 'OK', message: 'Success', data, timestamp: new Date().toISOString() },
    });

    if (route.request().method() === 'PUT' && path === '/v1/schedule/rules/5001') {
      const payload = route.request().postDataJSON();
      if (payload.editOperation === 'REPLAN') {
        expect(payload.expectedPendingLessonSnapshots).toEqual(['old-period-snapshot', 'current-period-snapshot']);
        expect(payload.reclaimEndedUnconfirmedLessons).toBe(true);
        expect(payload.startDate).toBe(futureStart);
        expect(payload.slots[0].slotId).toBe('6001');
        expect(payload.slots[0].allocatedUnits).toBe(80);
        await ok({ ruleId: '5002', newSlotCount: 1, preservedOccurrenceCount: 1,
          deletedOccurrenceCount: 0, generatedOccurrenceCount: 1, confirmedOccurrenceCount: 1 });
        return;
      }
      updateCount += 1;
      if (updateCount === 1) {
        expect(payload.slots[0].inheritedOccurrenceIds).toEqual(['2104217509483360257']);
        expect(payload.slots[0].teacherId).toBe('9002');
        expect(payload.slots[0].allocatedUnits).toBe(60);
        await route.fulfill({
          status: 400,
          json: {
            success: false,
            code: 'SLOT_ALLOCATION_EXCEEDS_PACKAGE',
            message: 'Allocated hours exceed the current package balance.',
            data: allocationDetails,
          },
        });
        return;
      }
      await ok({
        ruleId: '5001', newSlotCount: 1, preservedOccurrenceCount: 0,
        deletedOccurrenceCount: 0, generatedOccurrenceCount: 1, confirmedOccurrenceCount: 0,
        reclaimedOccurrenceCount: 0,
      });
      return;
    }

    if (path === '/v1/auth/me') return ok(admin);
    if (path === '/v1/schedule/students/one-on-one') return ok([student]);
    if (path === '/v1/enrollment/packages') return ok({ records: [pkg], total: 1, current: 1, size: 200 });
    if (path === '/v1/schedule/rules') return ok({ records: [rule], total: 1, current: 1, size: 1 });
    if (path === '/v1/schedule/rules/5001') return ok(rule);
    if (path === '/v1/schedule/occurrences/one-on-one-deductions') {
      return ok([{
        teacherId: '9001', teacherName: 'Emma Wang', students: [{
          studentId: '2001', studentName: 'Alex Kim', packages: [{ packageId: '7001', allocatedUnits: 40, lessons: [] }],
        }],
      }]);
    }
    if (path === '/v1/base/teachers/active') return ok([
      { id: '9001', teacherName: 'Emma Wang', status: 'ACTIVE' },
      { id: '9002', teacherName: 'Ms. Beatrice', status: 'ACTIVE' },
    ]);
    if (path === '/v1/base/classrooms/active' || path === '/v1/base/campuses') return ok([]);
    if (path === '/v1/schedule/occurrences') return ok({ records: [], total: 0, current: 1, size: 200 });
    return ok([]);
  });

  await page.goto('/schedule/recurring');
  await page.locator('.col-item').filter({ hasText: 'Alex Kim' }).first().click();
  await expect(page.getByRole('button', { name: 'Edit Schedule' })).toBeVisible();
  await page.getByRole('button', { name: 'Edit Schedule' }).click();
  const dialog = page.getByRole('dialog', { name: 'Edit Schedule' });
  await expect(dialog).toBeVisible();
  await expect(dialog.locator('.edit-schedule__balance')).toContainText('Starting Hours 40.0h');
  await expect(dialog.locator('.edit-schedule__balance')).toContainText('Allocated Hours 40.0h');
  await expect(dialog.locator('.edit-schedule__balance')).toContainText('Remaining 24.0h');
  await expect(dialog.locator('.weekly-time-selector')).toBeVisible();
  await dialog.getByText('Reschedule Lesson').click();
  await expect(dialog).toContainText('2 previous lessons are still unconfirmed');
  await expect(dialog).toContainText('Ms. Lau');
  await expect(dialog).toContainText('Awaiting admin');
  await expect(dialog).toContainText('Total: 4.0h');
  await dialog.getByText('Withdraw these pending records and continue').click();
  await expect(dialog).toContainText('Saving ends the current recurring schedule immediately');
  await dialog.getByPlaceholder('Select start date').fill(futureStart);
  await dialog.getByPlaceholder('Select start date').press('Tab');
  await expect(dialog.locator('.edit-schedule__sources')).toContainText('choose a successor below');
  await dialog.getByRole('button', { name: 'Save Changes' }).click();
  await expect(page.getByText(/Assign the lesson history from/)).toBeVisible();
  await dialog.locator('.slot-teacher-row__time .el-select').click();
  await page.getByRole('option', { name: /Monday 18:00/ }).click();
  await expect(dialog.locator('.edit-schedule__sources')).toContainText('assigned');
  await dialog.getByRole('button', { name: 'Save Changes' }).click();
  await expect(dialog).toHaveCount(0);
  await page.getByRole('button', { name: 'Edit Schedule' }).click();
  await expect(dialog).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });

  await dialog.locator('.slot-teacher-row > label').filter({ hasText: 'Inherit previous lesson history' }).locator('.el-select').click();
  await page.getByRole('option', { name: /2026-09-27 20:00 · Vocabulary · Ms. Lau/ }).click();
  await page.keyboard.press('Escape');
  await dialog.locator('.slot-teacher-row__fields .slot-teacher-row__field').nth(0).locator('.el-select').click();
  await page.getByRole('option', { name: 'Ms. Beatrice' }).click();
  const allocationInput = dialog.locator('.slot-teacher-row__field--hours input');
  await expect(allocationInput).toHaveValue('40.0');
  await expect(dialog).toContainText('The new teacher takes over this slot’s full lesson timeline');
  await allocationInput.fill('30');
  await allocationInput.press('Tab');
  await dialog.getByRole('button', { name: 'Save Changes' }).click();

  const allocationError = dialog.locator('.edit-schedule__allocation-error');
  await expect(allocationError).toHaveCount(1);
  await expect(allocationError).toContainText('30.0h is allocated, but only 22.0h is available (8.0h over)');
  await expect(allocationError).toContainText('Pending admin review 2.0h');
  await expect(allocationError).toContainText('Other schedules 3.0h');
  await expect(allocationError).toContainText('Preserved lessons 1.0h');
  await expect(allocationError).toContainText('Transferred future lessons +4.0h');
  await expect(dialog.locator('.el-message')).toHaveCount(0);

  await expect.poll(() => allocationError.locator('.allocation-error-details').evaluate(
    element => getComputedStyle(element).gridTemplateColumns.split(' ').length,
  )).toBe(1);
  await expect.poll(() => allocationError.evaluate(element => {
    const body = element.closest('.el-dialog')?.querySelector('.el-dialog__body');
    if (!body) return false;
    const alertRect = element.getBoundingClientRect();
    const bodyRect = body.getBoundingClientRect();
    return alertRect.top >= bodyRect.top && alertRect.bottom <= bodyRect.bottom;
  })).toBe(true);
  await page.screenshot({ path: '/tmp/schedule-allocation-error-mobile.png', fullPage: false });

  await allocationInput.fill('20');
  await allocationInput.press('Tab');
  await expect(allocationError).toHaveCount(0);
  await dialog.getByRole('button', { name: 'Save Changes' }).click();
  await expect(page.getByText(/Schedule updated\./).last()).toBeVisible();
  await expect(dialog).toHaveCount(0);
  expect(updateCount).toBe(2);
});
