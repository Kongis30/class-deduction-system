import { expect, test } from '@playwright/test';

const admin = {
  userId: '1',
  username: 'admin',
  displayName: 'Admin',
  userType: 'ADMIN',
  roleCodes: ['ADMIN'],
};

test('1-on-1 print still includes the schedule when signature history is unavailable', async ({ page }) => {
  await page.addInitScript((user) => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
    window.print = () => {
      document.documentElement.dataset.printInvoked = 'yes';
    };
  }, admin);

  const lessonRows = Array.from({ length: 3 }, (_, index) => ({
    occurrenceId: String(index + 1),
    lessonNo: `L${index + 1}`,
    packageId: '7001',
    plannedTeacherId: '9001',
    plannedTeacherName: 'Teacher One',
    subjectName: 'Math',
    plannedStartAt: `2026-10-0${index + 1}T14:00:00-04:00`,
    plannedEndAt: `2026-10-0${index + 1}T16:00:00-04:00`,
    plannedUnits: 4,
    planningStatus: 'PLANNED',
  }));
  const coursePackage = {
    packageId: '7001',
    packageNo: 'PKG-7001',
    packageName: 'Math 40 hours',
    courseProductName: 'Math',
    gradeBand: 'G5',
    totalUnits: 40,
    purchasedUnits: 40,
    bonusUnits: 0,
    consumedUnits: 0,
    remainingUnits: 40,
    renewalReminder: false,
    packageStatus: 'ACTIVE',
    lessons: lessonRows,
  };
  const teacher = {
    teacherId: '9001',
    teacherName: 'Teacher One',
    totalUnits: 40,
    consumedUnits: 0,
    remainingUnits: 40,
    studentCount: 1,
    packageCount: 1,
    students: [{
      studentId: '4001',
      studentName: 'Print Regression Student',
      grade: 'G5',
      totalUnits: 40,
      consumedUnits: 0,
      remainingUnits: 40,
      renewalReminder: false,
      packages: [coursePackage],
    }],
  };

  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) {
      await route.continue();
      return;
    }

    if (path.endsWith('/v1/schedule/occurrences/private-signature-history/7001')) {
      await route.fulfill({ status: 500, json: { success: false, message: 'History table unavailable' } });
      return;
    }

    let data: unknown = [];
    if (path.endsWith('/v1/auth/me')) data = admin;
    if (path.endsWith('/v1/schedule/occurrences/one-on-one-deductions')) data = [teacher];
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });

  await page.goto('/one-on-one-deduction?studentId=4001&packageId=7001&printSheet=parent');
  await expect(page.locator('html')).toHaveAttribute('data-print-invoked', 'yes');
  await expect(page.getByText('Historical signature entries could not be loaded. The sheet will include scheduled lessons only.')).toBeVisible();

  await page.emulateMedia({ media: 'print' });
  const printedSheet = page.locator('.oo-parent-signature-sheet');
  await expect(printedSheet).toBeVisible();
  await expect(printedSheet.locator('tbody tr')).toHaveCount(3);
});
