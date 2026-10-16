import { expect, test } from '@playwright/test';

test('lesson history separates schedule versions without counting the divider as a lesson', async ({ page }) => {
  const admin = {
    userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'],
  };
  await page.addInitScript(user => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);

  const lesson = (id: string, date: string, ruleId: string, planningStatus = 'SCHEDULED') => ({
    occurrenceId: id,
    lessonNo: `L${id}`,
    packageId: '7001',
    scheduleRuleId: ruleId,
    plannedTeacherId: '9001',
    plannedTeacherName: 'Teacher One',
    subjectName: 'Math',
    plannedStartAt: `${date}T14:00:00`,
    plannedEndAt: `${date}T16:00:00`,
    plannedUnits: 2,
    planningStatus,
  });
  const pkg = {
    packageId: '7001', packageNo: 'PKG-7001', packageName: 'Math 40 hours',
    courseProductName: 'Math', gradeBand: 'G5', totalUnits: 40,
    consumedUnits: 0, remainingUnits: 40, renewalReminder: false, packageStatus: 'ACTIVE',
    lessons: [
      lesson('1', '2026-09-20', '5001'),
      lesson('2', '2026-10-07', '5001', 'ADJUSTED'),
      // A past-week adjustment can create a new-period occurrence dated before the edit timestamp.
      lesson('4', '2026-10-02', '5002'),
      lesson('3', '2026-10-10', '5002'),
    ],
    scheduleChanges: [{ ruleId: '5002', changedAt: '2026-10-03T10:00:00' }],
  };
  const teacher = {
    teacherId: '9001', teacherName: 'Teacher One', totalUnits: 40,
    consumedUnits: 0, remainingUnits: 40, studentCount: 1, packageCount: 1,
    students: [{
      studentId: '4001', studentName: 'Schedule History Student', grade: 'G5',
      totalUnits: 40, consumedUnits: 0, remainingUnits: 40,
      renewalReminder: false, packages: [pkg],
    }],
  };
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) {
      await route.continue();
      return;
    }
    let data: unknown = [];
    if (path.endsWith('/v1/auth/me')) data = admin;
    if (path.endsWith('/v1/schedule/occurrences/one-on-one-deductions')) data = [teacher];
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });

  await page.goto('/one-on-one-deduction?focus=deduction&studentId=4001&packageId=7001');
  const rows = page.locator('.oo-table tbody tr');
  await expect(rows).toHaveCount(5);
  await expect(rows.nth(1)).toContainText('Kept from previous schedule');
  await expect(rows.nth(2)).toContainText('Schedule adjusted · Oct 3, 2026 10:00');
  await expect(rows.nth(3)).not.toContainText('Kept from previous schedule');
  await expect(rows.nth(4)).not.toContainText('Kept from previous schedule');
  await expect(page.locator('.oo-table-title')).toContainText('4 records');

  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator('.oo-mobile-lessons .oo-schedule-change')).toHaveCount(1);
  await expect(page.locator('.oo-mobile-lessons .oo-mobile-lesson')).toHaveCount(4);
});
