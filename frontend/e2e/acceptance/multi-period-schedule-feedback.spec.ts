import { expect, test } from '@playwright/test';

test('adding an incomplete period points the admin to the fields to complete', async ({ page }) => {
  const product = {
    id: '100', productCode: 'TEST', productName: 'Test course', teachingMode: 'GROUP', status: 'ACTIVE',
  };
  const setup = {
    productId: '100', productName: 'Test course', projects: [{
      id: '500', projectCode: 'TEST', projectName: 'Test project', status: 'ACTIVE', displayOrder: 1,
      plans: [],
      classes: [{
        id: '900', className: 'G3', actualClass: 'G3', startDate: '2026-10-01', endDate: '2026-12-31',
        status: 'ACTIVE', hasSchedule: true,
      }],
    }],
  };
  const classGroup = {
    id: '900', className: 'G3', actualClass: 'G3', productId: '100', groupProjectId: '500',
    productName: 'Test course', groupProjectName: 'Test project', startDate: '2026-10-01', endDate: '2026-12-31',
    scheduleBaseEndDate: '2026-12-31', status: 'ACTIVE', activeStudentCount: 0,
    scheduleSlots: [{
      slotLineageId: '99', effectiveStartDate: '2026-10-01', effectiveEndDate: '2026-12-31',
      weekday: 1, startTime: '14:00:00', endTime: '16:00:00', plannedUnits: 4,
      subjectName: 'English',
    }],
  };
  const adminUser = {
    userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'],
  };
  await page.route('**/*', async (route) => {
    const url = new URL(route.request().url());
    if (!url.pathname.startsWith('/api/')) {
      await route.continue();
      return;
    }
    const path = url.pathname.replace(/^\/api/, '');
    let data: unknown = [];
    if (path === '/v1/auth/me') data = adminUser;
    else if (path === '/v1/base/course-products') data = { records: [product], total: 1, pageNum: 1, pageSize: 200 };
    else if (path === '/v1/base/course-products/all') data = [product];
    else if (path === '/v1/classes') data = [classGroup];
    else if (path === '/v1/base/teachers/active' || path === '/v1/base/classrooms/active' || path === '/v1/base/campuses') data = [];
    else if (path === '/v2/group-course-model/products/100') data = setup;
    await route.fulfill({ json: { success: true, code: 'OK', message: 'Success', data, timestamp: new Date().toISOString() } });
  });
  await page.addInitScript(() => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify({ userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'] }));
    localStorage.setItem('jin.course-prices.selected-mode', 'GROUP');
  });

  await page.goto('/admin/config/course-prices');
  await page.getByRole('button', { name: 'Edit schedule' }).click();
  await expect(page.getByRole('dialog', { name: 'Edit schedule · G3' })).toBeVisible();
  await page.getByRole('button', { name: 'Add period' }).click();

  const save = page.getByRole('button', { name: 'Save changes' });
  await expect(save).toBeEnabled();
  await save.click();

  await expect(page.getByText('Choose an end date.')).toBeVisible();
  await expect(page.getByText('Add at least one weekly class time.')).toBeVisible();
  await expect(page.locator('[data-period-field="startDate"].is-invalid')).toBeInViewport();
  await expect(page.locator('[data-period-field="endDate"].is-invalid')).toContainText('Through');
  await expect(page.locator('.class-period__calendar.is-invalid')).toBeVisible();
});
