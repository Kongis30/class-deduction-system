import { expect, test } from '@playwright/test';
import dayjs from 'dayjs';

test('rescheduling displays and submits unconfirmed lessons from all periods', async ({ page }) => {
  const admin = { userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'] };
  const student = { id: '2001', studentName: 'Alex Kim', studentGrade: 'G5', courseGrades: [{
    courseName: 'Math', gradeBand: 'G5', packageId: '7001', packageStatus: 'ACTIVE', hasSchedule: true,
  }] };
  const pkg = { id: '7001', packageNo: 'PKG-7001', enrollmentOrderId: '8001', studentId: '2001',
    studentName: 'Alex Kim', courseProductId: '3001', courseProductName: 'Math', packageName: 'Math 40 hours',
    teachingMode: 'ONE_ON_ONE', totalUnits: 40, startingHours: 40, consumedUnits: 16,
    openingUsedHours: 0, pendingAdminDeductionUnits: 2, remainingUnits: 24, receivedAmount: 0, packageStatus: 'ACTIVE' };
  const rule = { id: '5001', packageId: '7001', packageNo: 'PKG-7001', studentId: '2001', studentName: 'Alex Kim',
    packageName: 'Math 40 hours', ruleStatus: 'ACTIVE', startDate: '2026-10-01', slotCount: 1,
    generatedOccurrenceCount: 10, deductedHours: 8, pendingHours: 2, otherReservedHours: 0,
    preservedReservedHours: 0, startingHours: 40,
    pendingReclaimLessons: [
      { occurrenceId: '8101', snapshot: 'older-period', subjectName: 'ELA', teacherName: 'Ms. Lau',
        plannedStartAt: '2026-09-27T18:00:00', plannedEndAt: '2026-09-27T20:00:00', plannedHours: 2,
        status: 'AWAITING_TEACHER', inProgress: false },
      { occurrenceId: '8102', snapshot: 'current-period', subjectName: 'Math', teacherName: 'Emma Wang',
        plannedStartAt: '2026-10-02T18:00:00', plannedEndAt: '2026-10-02T20:00:00', plannedHours: 2,
        status: 'PENDING_ADMIN', inProgress: false },
    ],
    slots: [{ id: '6001', teacherId: '9001', teacherName: 'Emma Wang', subjectName: 'Math', deliveryMode: 'ONLINE',
      weekday: 1, startTime: '18:00:00', endTime: '20:00:00', plannedUnits: 4, allocatedUnits: 80,
      rolloverAllocatedUnits: 0, slotStatus: 'ACTIVE' }] };
  const futureStart = dayjs().add(30, 'day').format('YYYY-MM-DD');
  let saved = false;
  await page.addInitScript(user => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace(/^\/api/, '');
    const ok = (data: unknown) => route.fulfill({ json: { success: true, code: 'OK', message: 'Success', data } });
    if (route.request().method() === 'PUT' && path === '/v1/schedule/rules/5001') {
      const payload = route.request().postDataJSON();
      expect(payload.editOperation).toBe('REPLAN');
      expect(payload.reclaimEndedUnconfirmedLessons).toBe(true);
      expect(payload.expectedPendingLessonSnapshots).toEqual(['older-period', 'current-period']);
      expect(payload.slots[0].inheritedSlotIds).toEqual(['6001']);
      saved = true;
      return ok({ ruleId: '5002', newSlotCount: 1, preservedOccurrenceCount: 0, deletedOccurrenceCount: 0,
        generatedOccurrenceCount: 1, confirmedOccurrenceCount: 0, reclaimedOccurrenceCount: 2 });
    }
    if (path === '/v1/auth/me') return ok(admin);
    if (path === '/v1/schedule/students/one-on-one') return ok([student]);
    if (path === '/v1/enrollment/packages') return ok({ records: [pkg], total: 1, current: 1, size: 200 });
    if (path === '/v1/schedule/rules') return ok({ records: [rule], total: 1, current: 1, size: 1 });
    if (path === '/v1/schedule/rules/5001') return ok(rule);
    if (path === '/v1/schedule/occurrences/one-on-one-deductions') return ok([{
      teacherId: '9001', teacherName: 'Emma Wang', students: [{ studentId: '2001', studentName: 'Alex Kim',
        packages: [{ packageId: '7001', allocatedUnits: 40, lessons: [] }] }],
    }]);
    if (path === '/v1/base/teachers/active') return ok([{ id: '9001', teacherName: 'Emma Wang', status: 'ACTIVE' }]);
    if (path === '/v1/base/classrooms/active' || path === '/v1/base/campuses') return ok([]);
    if (path === '/v1/schedule/occurrences') return ok({ records: [], total: 0, current: 1, size: 200 });
    return ok([]);
  });

  await page.goto('/schedule/recurring');
  await page.locator('.col-item').filter({ hasText: 'Alex Kim' }).first().click();
  await page.getByRole('button', { name: 'Edit Schedule' }).click();
  const dialog = page.getByRole('dialog', { name: 'Edit Schedule' });
  await dialog.getByText('Reschedule Lesson').click();
  await expect(dialog).toContainText('2 previous lessons are still unconfirmed');
  await expect(dialog).toContainText('Ms. Lau');
  await expect(dialog).toContainText('Awaiting admin');
  await expect(dialog).toContainText('Total: 4.0h');
  await dialog.getByText('Withdraw these pending records and continue').click();
  await dialog.getByPlaceholder('Select start date').fill(futureStart);
  await dialog.getByPlaceholder('Select start date').press('Tab');
  await dialog.locator('.slot-teacher-row__time .el-select').click();
  await page.getByRole('option', { name: /Monday 18:00/ }).click();
  await dialog.getByRole('button', { name: 'Save Changes' }).click();
  await expect(dialog).toHaveCount(0);
  expect(saved).toBe(true);
});
