import { expect, test } from '@playwright/test';
import { loginAsAdmin } from '../ui/helpers/session';

test('有未排课课程的学生只在待排课栏，全部排好后才进入下方课程分组', async ({ page }) => {
  await loginAsAdmin(page);
  await page.route('**/api/v1/schedule/students/one-on-one', async route => {
    await route.fulfill({
      json: {
        success: true,
        data: [
          {
            id: '1001',
            studentName: 'Alina Chen',
            courseGrades: [
              { courseName: 'Math', gradeBand: 'G6', hasSchedule: true },
              { courseName: 'English', gradeBand: 'G6', hasSchedule: false },
            ],
          },
          {
            id: '1002',
            studentName: 'Ben Li',
            courseGrades: [{ courseName: 'Math', gradeBand: 'G6' }],
          },
          {
            id: '1003',
            studentName: 'Cara Wu',
            courseGrades: [{ courseName: 'Math', gradeBand: 'G6', hasSchedule: true }],
          },
        ],
      },
    });
  });

  await page.goto('/schedule/recurring');

  const groups = page.locator('.schedule-student-group');
  await expect(page.locator('.schedule-student-section-title')).toHaveText('待排课学生');
  await expect(groups).toHaveCount(3);
  await expect(groups.nth(0)).toContainText('English');
  await expect(groups.nth(0).locator('.col-item__name')).toHaveText(['Alina Chen']);
  await expect(groups.nth(1)).toContainText('Math');
  await expect(groups.nth(1).locator('.col-item__name')).toHaveText(['Ben Li']);
  await expect(groups.nth(2)).toContainText('Math');
  await expect(groups.nth(2).locator('.col-item__name')).toHaveText(['Cara Wu']);
  await expect(page.getByText('No students found')).toHaveCount(0);
});
