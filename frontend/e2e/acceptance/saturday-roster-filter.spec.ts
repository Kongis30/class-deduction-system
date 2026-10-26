import { expect, test } from '@playwright/test';

const admin = {
  userId: '1',
  username: 'admin',
  displayName: 'Admin',
  userType: 'ADMIN',
  roleCodes: ['ADMIN'],
};

const product = {
  id: '8201',
  productName: '2026-2027 Saturday Class',
  courseType: 'SATURDAY',
  sortOrder: 1,
  status: 'ACTIVE',
  teachingMode: 'GROUP',
};

function row(id: string, studentName: string, registrationTermCode?: string, periodLabel?: string) {
  return {
    orderId: `order-${id}`,
    enrollmentNo: `E-${id}`,
    orderItemId: `item-${id}`,
    studentId: `student-${id}`,
    studentName,
    school: 'PS 1',
    grade: '3',
    gradeBand: registrationTermCode ?? '',
    signupDate: '2026-09-10',
    className: 'G3',
    registerClass: 'G3',
    courseProductId: product.id,
    courseProductName: product.productName,
    campusName: 'North Campus',
    courseType: 'SATURDAY',
    registrationTermCode,
    periodLabel,
    pickupRequired: false,
    dropoffRequired: false,
    paymentMethod: 'Cash',
    paymentStatus: 'PAID',
    orderPaymentStatus: 'PAID',
    orderStatus: 'CONFIRMED',
    itemStatus: 'ACTIVE',
    linePriceAmount: 100,
    pickupFeeAmount: 0,
    dropoffFeeAmount: 0,
    extraFeeAmount: 0,
    discountAmount: 0,
    amountDue: 100,
    paidAmount: 100,
    balanceAmount: 0,
    overdue: false,
    expiringSoon: false,
    lowOneOnOneHours: false,
    alertTypes: [],
  };
}

const rows = [
  row('fall', 'Fall Student', 'FALL'),
  row('spring', 'Spring Student', 'SPRING'),
  row('year', 'Full Year Student', 'FULL_YEAR'),
  row('unknown', 'Unclassified Student', undefined, '2026-2027'),
];

async function mockSaturdaySummary(page: import('@playwright/test').Page) {
  await page.addInitScript((user) => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) return route.continue();
    const data = path.endsWith('/v1/auth/me') ? admin
      : path.endsWith('/v1/base/course-products/all') ? [product]
        : path.endsWith('/v1/data-summary') ? { rows }
          : path.endsWith('/v1/system/messages') ? { unreadCount: 0, messages: [] }
            : [];
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });
}

test('Saturday season controls filter Fall, Spring, and All registrations', async ({ page }) => {
  await mockSaturdaySummary(page);
  await page.goto('/data-summary');

  const workbook = page.getByTestId('data-summary-workbook');
  await expect(page.getByRole('button', { name: 'Fall', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Spring', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'All', exact: true })).toBeVisible();
  await expect(workbook.locator('.workbook-data-row')).toHaveCount(4);

  await page.getByRole('button', { name: 'Fall', exact: true }).click();
  await expect(workbook.locator('.workbook-data-row')).toHaveCount(2);
  await expect(workbook.getByText('Fall Student')).toBeVisible();
  await expect(workbook.getByText('Full Year Student')).toBeVisible();
  await expect(workbook.getByText('Spring Student')).toHaveCount(0);
  await expect(workbook.getByText('Unclassified Student')).toHaveCount(0);

  await page.getByRole('button', { name: 'Spring', exact: true }).click();
  await expect(workbook.locator('.workbook-data-row')).toHaveCount(2);
  await expect(workbook.getByText('Spring Student')).toBeVisible();
  await expect(workbook.getByText('Full Year Student')).toBeVisible();
  await expect(workbook.getByText('Fall Student')).toHaveCount(0);

  await page.getByRole('button', { name: 'All', exact: true }).click();
  await expect(workbook.locator('.workbook-data-row')).toHaveCount(4);
  await expect(workbook.getByText('Unclassified Student')).toBeVisible();
});

test('Saturday season filtering is available from the mobile overflow menu', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await mockSaturdaySummary(page);
  await page.goto('/data-summary');

  await page.getByRole('button', { name: 'More actions' }).click();
  await page.getByRole('menuitem', { name: 'Spring roster' }).click();

  const workbook = page.getByTestId('data-summary-workbook');
  await expect(workbook.locator('.workbook-data-row')).toHaveCount(2);
  await expect(workbook.getByText('Spring Student')).toBeVisible();
  await expect(workbook.getByText('Fall Student')).toHaveCount(0);

  await page.getByRole('button', { name: 'More actions' }).click();
  await page.getByRole('menuitem', { name: 'All roster', exact: true }).click();
  await expect(workbook.locator('.workbook-data-row')).toHaveCount(4);
});
