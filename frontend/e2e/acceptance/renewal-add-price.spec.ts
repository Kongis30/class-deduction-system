import { expect, test, type Page } from '@playwright/test';

const admin = { userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'] };

// 复现：Edit Receipt 中 Add course 新增续课行后，Price 列显示 0，
// 切换 plan/product 后价格才正确。startCourseRenewal 设置 priceAmount=0 后
// 调用 handleGroupPlanChange 重算，但新增行的价格仍显示 0。
test('Add course row should keep configured price', async ({ page }) => {
  await page.addInitScript((user) => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);
  const setup = { productId: '10', productName: 'After School',
    projects: [{ id: '20', projectName: 'G1-G5', status: 'ACTIVE',
      plans: [{ id: '30', planName: '10 months', packageUnit: 'MONTH', packageQuantity: 10,
        fullDayPrice: 3499, status: 'ACTIVE', coverageType: 'ROLLING_MONTHS' }],
      classes: [{ id: '40', className: 'G3', actualClass: 'G3', campusId: '1', campusName: 'North Campus',
        status: 'ACTIVE', startDate: '2026-09-10' }] }] };
  const item = { id: '500', orderItemId: '500', lineNo: 1, courseType: 'GROUP', courseProductId: '10',
    groupProjectId: '20', groupRegistrationPlanId: '30', classId: '40', className: 'G3',
    packageUnit: 'MONTH', packageQuantity: 10, purchasedMonths: 10, priceAmount: 3499,
    attendanceOption: 'FULL_DAY', pickupRequiredSnapshot: false, pickupFeeAmount: 0,
    dropoffRequiredSnapshot: false, dropoffFeeAmount: 0, extraFeeAmount: 0, discountAmount: 0,
    startDate: '2026-09-10', itemStatus: 'ACTIVE', classEnrollmentId: '77', itemStatus2: undefined };
  const order = { id: '100', version: 1, studentId: '200', studentNameSnapshot: 'Cynthia Xu',
    signupDate: '2026-04-30', orderStatus: 'CONFIRMED', amountDue: 3499, receivedAmount: 3499,
    balanceAmount: 0, pickupFeeAmount: 0, dropoffFeeAmount: 0, extraFeeAmount: 0, discountAmount: 0,
    campusId: '1', campusNameSnapshot: 'North Campus',
    items: [item], packages: [], payments: [{ id: '900', paymentMethod: 'CASH', amount: 3499, paidAt: '2026-04-30T00:00:00' }],
    participants: [],
    receipts: [{ id: '300', templateCode: 'DEFAULT_RECEIPT', receiptPayload: JSON.stringify({ items: [item] }) }] };
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) return route.continue();
    const data = path.endsWith('/v1/auth/me') ? admin
      : path.endsWith('/v1/enrollment/orders/100') ? order
        : path.endsWith('/v2/group-course-model/products/10') ? setup
          : path.endsWith('/v1/enrollment/payment-methods/active') ? [{ id: '1', methodCode: 'CASH', methodName: 'Cash', status: 'ACTIVE' }]
            : path.endsWith('/v1/base/campuses') ? [{ id: '1', campusName: 'North Campus', status: 'ACTIVE' }]
              : path.endsWith('/v1/base/course-products') ? { records: [], total: 0 } : [];
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });
  await page.goto('/registration?orderId=100');
  await page.getByRole('button', { name: 'Edit Receipt' }).click();
  await page.waitForTimeout(600);
  await page.getByRole('button', { name: 'Add course' }).click();
  await page.waitForTimeout(800);
  const rows = page.locator('.course-entry-row');
  const count = await rows.count();
  console.log('course rows:', count);
  for (let i = 0; i < count; i++) {
    const price = await rows.nth(i).locator('.price-cell input').inputValue().catch(() => '(no input)');
    console.log(`row ${i} price:`, price);
  }
});
