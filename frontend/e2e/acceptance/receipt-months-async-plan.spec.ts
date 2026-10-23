import { expect, test } from '@playwright/test';

const admin = {
  userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'],
};

test('Edit Receipt preserves purchased months while group plan options load', async ({ page }) => {
  await page.addInitScript((user) => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);

  const courseItem = {
    id: '500', lineNo: 1, courseType: 'GROUP', courseProductId: '10',
    courseProductName: '2026-2027 After School', groupProjectId: '20',
    groupRegistrationPlanId: '30', classId: '40', className: 'G1', gradeBand: 'G1-G5',
    detail: 'G1-G5 · 2 months', periodLabel: '2 months', packageUnit: 'MONTH',
    packageQuantity: 2, purchasedMonths: 2, packageTotalPrice: 800,
    attendanceOption: 'FULL_DAY', priceAmount: 800, originalPriceAmount: 800,
    manualPriceOverride: false, startDate: '2026-09-14', itemStatus: 'ACTIVE',
  };
  const receiptPayload = {
    studentName: 'Yichen Qin', grade: '1', school: 'PS 244', campusId: '1',
    campusName: 'North Campus', signupDate: '2026-09-14', amountDue: 1000,
    items: [{ ...courseItem }], payments: [{ paymentMethod: 'CASH', amount: 1000, paidAt: '2026-09-14' }],
  };
  const order = {
    id: '100', studentId: '200', studentNameSnapshot: 'Yichen Qin', signupDate: '2026-09-14',
    orderStatus: 'CONFIRMED', campusId: '1', campusNameSnapshot: 'North Campus',
    gradeSnapshot: '1', schoolSnapshot: 'PS 244', items: [courseItem], packages: [],
    payments: [], participants: [],
    receipts: [{ id: '300', templateCode: 'DEFAULT_RECEIPT', receiptPayload: JSON.stringify(receiptPayload) }],
  };
  const setup = {
    productId: '10', productName: '2026-2027 After School',
    projects: [{
      id: '20', projectCode: 'G1-G5', projectName: 'G1-G5', status: 'ACTIVE', displayOrder: 1,
      plans: [{
        id: '30', planCode: '2M', planName: '2 months', coverageType: 'ROLLING_MONTHS',
        packageUnit: 'MONTH', packageQuantity: 2, fullDayPrice: 800, status: 'ACTIVE', displayOrder: 1,
      }],
      classes: [{
        id: '40', className: 'G1', actualClass: 'G1', campusId: '1', campusName: 'North Campus',
        startDate: '2026-09-01', endDate: '2027-06-30', status: 'ACTIVE',
      }],
    }],
  };

  let releaseSetup!: () => void;
  let notifySetupRequested!: () => void;
  const setupGate = new Promise<void>((resolve) => { releaseSetup = resolve; });
  const setupRequested = new Promise<void>((resolve) => { notifySetupRequested = resolve; });

  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) return route.continue();
    if (path.endsWith('/v2/group-course-model/products/10')) {
      notifySetupRequested();
      await setupGate;
    }
    const data = path.endsWith('/v1/auth/me') ? admin
      : path.endsWith('/v1/enrollment/orders/100') ? order
        : path.endsWith('/v1/base/course-products') ? { records: [], total: 0, pageNum: 1, pageSize: 200 }
          : path.endsWith('/v2/group-course-model/products/10') ? setup
            : path.endsWith('/v1/base/campuses') ? [{ id: '1', campusName: 'North Campus', status: 'ACTIVE' }]
              : [];
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });

  await page.goto('/registration?orderId=100');
  await expect(page.locator('#app .paper-form--readonly')).toContainText('Yichen Qin');
  await page.getByRole('button', { name: 'Edit Receipt' }).click();
  await setupRequested;

  const monthsInput = page.locator('label.course-control').filter({ hasText: 'Months' }).locator('input[type="number"]');
  await expect(monthsInput).toHaveValue('2');
  releaseSetup();
  await expect(monthsInput).toHaveValue('2');
});
