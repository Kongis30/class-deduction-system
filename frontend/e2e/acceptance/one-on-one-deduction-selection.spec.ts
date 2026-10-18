import { expect, test } from '@playwright/test';

test('confirming a lesson keeps the same teacher when a package belongs to several teachers', async ({ page }) => {
  const admin = {
    userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'],
  };
  await page.addInitScript(user => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);

  let angelConfirmed = false;
  const teacherGroup = (teacherId: string, teacherName: string, occurrenceId: string) => ({
    teacherId, teacherName, totalUnits: 40, consumedUnits: 0, remainingUnits: 40,
    studentCount: 1, packageCount: 1,
    students: [{
      studentId: '4001', studentName: 'Gloria Deng', grade: 'G5',
      totalUnits: 40, consumedUnits: 0, remainingUnits: 40,
      renewalReminder: false,
      packages: [{
        packageId: '7001', packageNo: 'PKG-7001', packageName: 'G1-G5 40 hours',
        courseProductName: 'ELA', gradeBand: 'G5', totalUnits: 40,
        consumedUnits: 0, remainingUnits: 40, renewalReminder: false,
        packageStatus: 'ACTIVE',
        teacherHours: [{ teacherId, teacherName, allocatedUnits: 10, remainingUnits: 10, deductedUnits: 0 }],
        lessons: [{
          occurrenceId, lessonNo: `L${occurrenceId}`, packageId: '7001',
          plannedTeacherId: teacherId, plannedTeacherName: teacherName, subjectName: 'ELA',
          plannedStartAt: '2026-10-08T19:00:00', plannedEndAt: '2026-10-08T21:00:00',
          plannedUnits: 2, planningStatus: 'SCHEDULED',
          ...(teacherId === '9001' && angelConfirmed
            ? { confirmStage: 'CONFIRMED', resultStatus: 'COMPLETED', deductedUnits: 2 }
            : {}),
        }],
      }],
    }],
  });

  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) {
      await route.continue();
      return;
    }
    if (route.request().method() === 'POST' && path.endsWith('/v1/lessons/records')) {
      expect(route.request().postDataJSON().occurrenceId).toBe('2001');
      angelConfirmed = true;
      await route.fulfill({ json: { success: true, code: 'OK', data: {} } });
      return;
    }
    let data: unknown = [];
    if (path.endsWith('/v1/auth/me')) data = admin;
    if (path.endsWith('/v1/schedule/occurrences/one-on-one-deductions')) {
      // 返回顺序故意让 Jiaxin 先于 Angel，复现旧版仅凭 packageId 跳选的问题。
      data = [teacherGroup('9002', 'Ms. Jiaxin', '2002'), teacherGroup('9001', 'Mr. Angel', '2001')];
    }
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });

  await page.goto('/one-on-one-deduction');
  const angelGroup = page.locator('.oo-teacher-group').filter({ has: page.getByRole('button', { name: /Mr\. Angel/ }) });
  const jiaxinGroup = page.locator('.oo-teacher-group').filter({ has: page.getByRole('button', { name: /Ms\. Jiaxin/ }) });
  await angelGroup.locator('.oo-teacher-toggle').click();
  await angelGroup.locator('.oo-student-group__toggle').click();
  await angelGroup.locator('.oo-package-item').click();
  await expect(page.locator('.oo-student-context__metric-group--teacher')).toHaveAttribute('aria-label', 'Hours for Mr. Angel');
  await expect(angelGroup.locator('.oo-package-item')).toHaveClass(/oo-package-item--active/);

  await page.locator('.oo-table').getByRole('button', { name: 'Review & deduct' }).click();
  await page.getByRole('dialog', { name: 'Review & Deduct Lesson' }).getByRole('button', { name: 'Review & deduct' }).click();

  await expect(page.locator('.oo-student-context__metric-group--teacher')).toHaveAttribute('aria-label', 'Hours for Mr. Angel');
  await expect(angelGroup.locator('.oo-package-item')).toHaveClass(/oo-package-item--active/);
  await jiaxinGroup.locator('.oo-teacher-toggle').click();
  await jiaxinGroup.locator('.oo-student-group__toggle').click();
  await expect(jiaxinGroup.locator('.oo-package-item')).not.toHaveClass(/oo-package-item--active/);
  await expect(page.locator('.oo-table')).toContainText('Completed');
});
