import { expect, test } from '@playwright/test';

const admin = {
  userId: '1',
  username: 'admin',
  displayName: 'Admin',
  userType: 'ADMIN',
  roleCodes: ['ADMIN'],
};

const product = {
  id: '8801',
  productName: '2026-2027 After School',
  sortOrder: 1,
  status: 'ACTIVE',
  teachingMode: 'GROUP',
};

function purchase(orderItemId: string, signupDate: string, amount: number) {
  return {
    orderId: '9901',
    orderItemId,
    logicalCourseId: '7701',
    studentId: '6601',
    participantIndex: 1,
    studentName: 'Renewal Test Student',
    school: 'PS 10',
    grade: '3',
    signupDate,
    periodStartDate: signupDate,
    periodEndDate: '2027-06-20',
    className: 'G3',
    registerClass: 'G3',
    courseType: 'GROUP',
    courseProductId: product.id,
    courseProductName: product.productName,
    campusName: 'North Campus',
    pickupRequired: false,
    dropoffRequired: false,
    paymentMethod: 'Cash',
    paymentStatus: 'PAID',
    orderPaymentStatus: 'PAID',
    orderStatus: 'CONFIRMED',
    itemStatus: 'ACTIVE',
    linePriceAmount: amount,
    pickupFeeAmount: 0,
    dropoffFeeAmount: 0,
    extraFeeAmount: 0,
    discountAmount: 0,
    amountDue: amount,
    paidAmount: amount,
    balanceAmount: 0,
    overdue: false,
    expiringSoon: false,
    lowOneOnOneHours: false,
    alertTypes: [],
  };
}

test('Data Summary moves a renewal row as one record, restores it, and persists the classification', async ({ page }) => {
  let rows = [
    purchase('5501', '2026-09-10', 800),
    purchase('5502', '2026-11-10', 500),
    {
      ...purchase('5599', '2026-09-12', 0),
      orderId: '9902',
      logicalCourseId: '7702',
      studentId: '6602',
      studentName: 'Refund Control Student',
      refunded: true,
    },
  ];
  const classificationRequests: Array<{ orderItemIds: string[]; discontinued: boolean }> = [];

  await page.addInitScript((user) => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);

  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (!path.startsWith('/api/')) return route.continue();

    let data: unknown = [];
    if (path.endsWith('/v1/auth/me')) {
      data = admin;
    } else if (path.endsWith('/v1/base/course-products/all')) {
      data = [product];
    } else if (path.endsWith('/v1/data-summary/classification') && request.method() === 'POST') {
      const body = request.postDataJSON() as {
        orderId: string;
        studentId: string;
        orderItemIds: string[];
        discontinued: boolean;
      };
      classificationRequests.push({ orderItemIds: body.orderItemIds, discontinued: body.discontinued });
      rows = rows.map((row) => row.orderId === body.orderId && row.studentId === body.studentId
        && body.orderItemIds.includes(row.orderItemId)
        ? { ...row, discontinued: body.discontinued }
        : row);
    } else if (path.endsWith('/v1/data-summary')) {
      data = { rows };
    }
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });

  await page.goto('/data-summary');
  const workbook = page.getByTestId('data-summary-workbook');
  await expect(page.locator('.data-summary-page .el-loading-mask')).toBeHidden();
  const registrationStat = page.locator('.workbook-toolbar__stat').filter({ hasText: 'Registrations' });
  const paidStat = page.locator('.workbook-toolbar__stat').filter({ hasText: 'Paid' });
  const refundControl = workbook.locator('tr.workbook-data-row--refund').filter({ hasText: 'Refund Control Student' });

  // 两笔续课购买在页面中合并为一个逻辑课程行。
  const activeRow = workbook.locator('tr.workbook-data-row').filter({ hasText: 'Renewal Test Student' });
  await expect(activeRow).toHaveCount(1);
  await expect(activeRow.locator('.workbook-cell--money').first()).toContainText('1,300');
  await expect(registrationStat).toContainText('2');
  await expect(paidStat).toContainText('1,300');
  await expect(refundControl).toHaveCount(1);
  await expect(workbook.getByTestId('discontinued-section-row')).toHaveCount(0);

  await activeRow.click({ button: 'right' });
  await page.getByRole('menuitem', { name: 'Discontinue student' }).click();

  const discontinuedSection = workbook.getByTestId('discontinued-section-row');
  await expect(discontinuedSection).toBeVisible();
  const discontinuedRow = workbook.locator('tr.workbook-data-row--discontinued').filter({ hasText: 'Renewal Test Student' });
  await expect(discontinuedRow).toHaveCount(1);
  await expect(discontinuedRow.locator('.workbook-cell--money').first()).toContainText('1,300');
  await expect(workbook.locator('tr.workbook-data-row:not(.workbook-data-row--discontinued)').filter({ hasText: 'Renewal Test Student' })).toHaveCount(0);
  await expect(registrationStat).toContainText('2');
  await expect(paidStat).toContainText('1,300');
  await expect(refundControl).toHaveCount(1);
  expect(classificationRequests[0]).toEqual({ orderItemIds: ['5501', '5502'], discontinued: true });

  await discontinuedRow.click({ button: 'right' });
  await expect(page.getByRole('menuitem', { name: 'Continue', exact: true })).toBeVisible();
  await page.getByRole('menuitem', { name: 'Continue', exact: true }).click();
  await expect(workbook.getByTestId('discontinued-section-row')).toHaveCount(0);
  await expect(workbook.locator('tr.workbook-data-row:not(.workbook-data-row--discontinued)').filter({ hasText: 'Renewal Test Student' })).toHaveCount(1);
  expect(classificationRequests[1]).toEqual({ orderItemIds: ['5501', '5502'], discontinued: false });

  // 再次归类后刷新，服务端返回的独立状态仍决定记录所在区域。
  await workbook.locator('tr.workbook-data-row:not(.workbook-data-row--discontinued)').filter({ hasText: 'Renewal Test Student' }).click({ button: 'right' });
  await page.getByRole('menuitem', { name: 'Discontinue student' }).click();
  await page.reload();
  await expect(page.locator('.data-summary-page .el-loading-mask')).toBeHidden();
  await expect(page.getByTestId('discontinued-section-row')).toBeVisible();
  await expect(page.locator('tr.workbook-data-row--discontinued').filter({ hasText: 'Renewal Test Student' })).toHaveCount(1);
  await expect(page.locator('tr.workbook-data-row:not(.workbook-data-row--discontinued)').filter({ hasText: 'Renewal Test Student' })).toHaveCount(0);
});
