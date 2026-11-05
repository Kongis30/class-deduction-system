import { expect, test } from '@playwright/test';

test('Data Summary ends only the selected student course after confirmation', async ({ page }) => {
  const requestBodies: unknown[] = [];
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.endsWith('/v1/base/course-products/all')) {
      await route.fulfill({ json: { success: true, data: [
        { id: '8001', productName: 'After School', sortOrder: 1, status: 'ACTIVE', teachingMode: 'GROUP' },
      ] } });
    } else if (path.endsWith('/v1/data-summary')) {
      await route.fulfill({ json: { success: true, data: { rows: [{
        orderId: '1001', orderItemId: '2001', studentId: '3001', studentName: 'Student End',
        courseProductId: '8001', courseProductName: 'After School', courseType: 'GROUP',
        campusName: 'North Campus', className: 'G1', grade: '1', school: 'PS 32',
        signupDate: '2026-09-01', periodStartDate: '2026-09-10', periodEndDate: '2027-06-28',
        paymentMethod: 'Cash', paymentStatus: 'PAID', pickupRequired: false, dropoffRequired: false,
        amountDue: 100, paidAmount: 100, balanceAmount: 0, itemStatus: 'ACTIVE',
      }] } } });
    } else if (path.endsWith('/v1/enrollment/orders/1001/refund-preview')) {
      await route.fulfill({ json: { success: true, data: { items: [], unassignedPaymentAmount: 0 } } });
    } else if (path.endsWith('/v1/enrollment/orders/1001/end-course') && request.method() === 'POST') {
      requestBodies.push(request.postDataJSON());
      await route.fulfill({ json: { success: true, data: null } });
    } else if (path.endsWith('/v1/auth/me')) {
      await route.fulfill({ json: { success: true, data: {
        userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'],
      } } });
    } else {
      await route.fulfill({ json: { success: true, data: [] } });
    }
  });
  await page.goto('/login');
  await page.evaluate(() => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify({
      userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'],
    }));
  });

  await page.goto('/data-summary');
  const studentRow = page.locator('tr.workbook-data-row').filter({ hasText: 'Student End' });
  await expect(studentRow).toBeVisible();
  await studentRow.click({ button: 'right' });
  await page.getByRole('menuitem', { name: 'End course' }).click();

  const confirmation = page.getByRole('dialog').filter({ hasText: 'End course' });
  await expect(confirmation).toBeVisible();
  await expect(confirmation).toContainText('Student End');
  await confirmation.getByRole('button', { name: 'End course' }).click();
  await expect.poll(() => requestBodies).toEqual([{ orderItemId: '2001', studentId: '3001' }]);
});
