import { expect, test, type Page } from '@playwright/test';

const admin = { userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'] };

test('Edit Receipt keeps zero pickup fee and payment unchanged when pickup is toggled', async ({ page }) => {
  await useMockAdmin(page);
  let correction: Record<string, any> | undefined;
  let pickupTotal = 300;
  const item = { id: '500', orderItemId: '500', lineNo: 1, courseType: 'GROUP', courseProductId: '10',
    groupProjectId: '20', groupRegistrationPlanId: '30', classId: '40', className: 'G1',
    packageUnit: 'MONTH', packageQuantity: 10, purchasedMonths: 10, priceAmount: 3499,
    pickupRequiredSnapshot: true, pickupFeeAmount: 0, dropoffRequiredSnapshot: false,
    dropoffFeeAmount: 0, extraFeeAmount: 0, discountAmount: 400, startDate: '2026-09-10', itemStatus: 'ACTIVE' };
  const payment = { id: '900', paymentMethod: 'CASH', amount: 3399, paidAt: '2026-04-30T00:00:00' };
  const setup = { productId: '10', productName: 'After School', projects: [{ id: '20', projectName: 'G1-G5', status: 'ACTIVE',
    plans: [{ id: '30', planName: '10 months', packageUnit: 'MONTH', packageQuantity: 10, fullDayPrice: 3499, status: 'ACTIVE' }],
    classes: [{ id: '40', className: 'G1', actualClass: 'G1', campusId: '1', campusName: 'North Campus', status: 'ACTIVE' }] }] };
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) return route.continue();
    if (path.endsWith('/v1/enrollment/orders/100/correction') && route.request().method() === 'PUT') {
      correction = route.request().postDataJSON();
      item.pickupRequiredSnapshot = correction?.items?.[0]?.pickupRequired ?? item.pickupRequiredSnapshot;
      item.pickupFeeAmount = correction?.pickupFeeAmount ?? item.pickupFeeAmount;
      pickupTotal = correction?.pickupFeeAmount ?? pickupTotal;
      await route.fulfill({ json: { success: true, code: 'OK', data: null } });
      return;
    }
    const order = { id: '100', version: 1, studentId: '200', studentNameSnapshot: 'Cynthia Xu',
      signupDate: '2026-04-30', orderStatus: 'CONFIRMED', amountDue: 3099 + pickupTotal, receivedAmount: 3399,
      balanceAmount: pickupTotal - 300, pickupRequiredSnapshot: true, pickupFeeAmount: pickupTotal, dropoffFeeAmount: 0,
      extraFeeAmount: 0, discountAmount: 400, campusId: '1', campusNameSnapshot: 'North Campus',
      items: [item], packages: [], payments: [payment], participants: [],
      receipts: [{ id: '300', templateCode: 'DEFAULT_RECEIPT', receiptPayload: JSON.stringify({ items: [item] }) }] };
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
  const paymentCard = page.locator('.course-adjustment-card--payment').first();
  const pickup = page.locator('label.service-charge-toggle').filter({ hasText: 'Pickup needed' }).locator('input');
  await expect(pickup).toBeChecked();
  await expect(paymentCard).toContainText('$3,099 due');
  await pickup.uncheck();
  await expect(paymentCard).toContainText('$3,099 due');
  await pickup.check();
  await expect(paymentCard).toContainText('$3,099 due');
  await pickup.uncheck();
  await expect(paymentCard).toContainText('$3,099 due');
  await expect(page.locator('.course-payment-entry').first().locator('.payment-amount-field input')).toHaveValue('3,399');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Save correction' }).click();
  await expect.poll(() => correction).toBeDefined();
  expect(correction?.pickupFeeAmount).toBe(0);
  expect(correction?.items?.[0]?.pickupRequired).toBe(false);
  expect(correction).not.toHaveProperty('payments');
  await page.getByRole('button', { name: 'Edit Receipt' }).click();
  await expect(page.locator('.course-adjustment-card--payment').first()).toContainText('$3,099 due');
  await expect(page.locator('label.service-charge-toggle').filter({ hasText: 'Pickup needed' }).locator('input')).not.toBeChecked();
  await expect(page.locator('.course-payment-entry').first().locator('.payment-amount-field input')).toHaveValue('3,399');
});

async function useMockAdmin(page: Page) {
  await page.addInitScript((user) => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);
}

test('Data Summary opens the same order when one course has multiple purchase rows', async ({ page }) => {
  await useMockAdmin(page);
  const purchases = [
    { orderId: '102', orderItemId: '502', enrollmentNo: 'EO-SINGLE', studentId: '201', studentName: 'Single Student',
      courseType: 'GROUP', courseProductId: '10', courseProductName: 'After School', logicalCourseId: '601',
      signupDate: '2026-09-07', periodStartDate: '2026-09-10', periodEndDate: '2026-10-31',
      amountDue: 800, paidAmount: 800, balanceAmount: 0, orderStatus: 'CONFIRMED', orderPaymentStatus: 'PAID' },
    { orderId: '102', orderItemId: '503', enrollmentNo: 'EO-SINGLE', studentId: '201', studentName: 'Single Student',
      courseType: 'GROUP', courseProductId: '10', courseProductName: 'After School', logicalCourseId: '601',
      signupDate: '2026-10-23', periodStartDate: '2026-11-01', periodEndDate: '2026-12-31',
      amountDue: 800, paidAmount: 800, balanceAmount: 0, orderStatus: 'CONFIRMED', orderPaymentStatus: 'PAID' },
  ];
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) return route.continue();
    const data = path.endsWith('/v1/auth/me') ? admin
      : path.endsWith('/v1/base/course-products/all')
        ? [{ id: '10', productName: 'After School', teachingMode: 'GROUP', status: 'ACTIVE', sortOrder: 1 }]
          : path.endsWith('/v1/data-summary')
          ? { rows: purchases, totalRegistrationCount: 1, totalIncomeAmount: 1600, totalOutstandingAmount: 0 }
          : path.match(/\/v1\/enrollment\/orders\/102$/)
            ? { id: '102', studentNameSnapshot: 'Single Student',
              studentId: '200', orderStatus: 'CONFIRMED', items: [], payments: [], participants: [], packages: [], receipts: [] }
            : [];
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });

  await page.goto('/data-summary');
  const row = page.locator('.workbook-data-row').filter({ hasText: 'Single Student' });
  await expect(row).toHaveCount(1);
  await row.click({ button: 'right' });
  await expect(page.getByRole('dialog', { name: 'Course purchases' })).toHaveCount(0);
  await expect(page.getByRole('menuitem', { name: 'View receipt' })).toBeVisible();
  await page.getByRole('menuitem', { name: 'View receipt' }).click();
  await expect(page).toHaveURL(/\/registration\?orderId=102/);
});

test('Edit Receipt adds a payment to the current order and keeps its receipt route', async ({ page }) => {
  await useMockAdmin(page);
  let school = 'PS 244';
  let correctionPayload: Record<string, any> | undefined;
  let payments: Array<Record<string, any>> = [];
  const item = { id: '500', lineNo: 1, courseType: 'GROUP', courseProductId: '10', groupProjectId: '20',
    groupRegistrationPlanId: '30', classId: '40', className: 'G1', packageUnit: 'MONTH', packageQuantity: 2,
    purchasedMonths: 2, priceAmount: 800, startDate: '2026-09-14', itemStatus: 'ACTIVE' };
  const setup = { productId: '10', productName: 'After School', projects: [{ id: '20', projectName: 'G1-G5', status: 'ACTIVE',
    plans: [{ id: '30', planName: '2 months', packageUnit: 'MONTH', packageQuantity: 2, fullDayPrice: 800, status: 'ACTIVE' }],
    classes: [{ id: '40', className: 'G1', actualClass: 'G1', campusId: '1', campusName: 'North Campus', status: 'ACTIVE' }] }] };
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) return route.continue();
    if (path.endsWith('/v1/enrollment/orders/100/correction') && route.request().method() === 'PUT') {
      correctionPayload = route.request().postDataJSON();
      school = correctionPayload.school;
      payments = correctionPayload.payments;
      await route.fulfill({ json: { success: true, code: 'OK', data: null } });
      return;
    }
    const order = { id: '100', enrollmentNo: 'EO-100', studentId: '200', studentNameSnapshot: 'Yuqi Chen',
      signupDate: '2026-09-14', orderStatus: 'CONFIRMED', paymentStatus: payments.length ? 'PARTIAL' : 'UNPAID',
      amountDue: 800, receivedAmount: payments.reduce((total, payment) => total + Number(payment.amount), 0),
      balanceAmount: 800 - payments.reduce((total, payment) => total + Number(payment.amount), 0), campusId: '1',
      campusNameSnapshot: 'North Campus', schoolSnapshot: school, items: [item], packages: [], payments, participants: [],
      receipts: [{ id: '300', templateCode: 'DEFAULT_RECEIPT', receiptPayload: JSON.stringify({
        studentName: 'Yuqi Chen', signupDate: '2026-09-14', items: [item], payments,
      }) }] };
    const data = path.endsWith('/v1/auth/me') ? admin
      : path.endsWith('/v1/enrollment/orders/100') ? order
        : path.endsWith('/v2/group-course-model/products/10') ? setup
          : path.endsWith('/v1/enrollment/payment-methods/active') ? [{ id: '1', methodCode: 'CASH', methodName: 'Cash', status: 'ACTIVE' }]
          : path.endsWith('/v1/base/campuses') ? [{ id: '1', campusName: 'North Campus', status: 'ACTIVE' }]
            : path.endsWith('/v1/base/course-products') ? { records: [], total: 0 }
              : [];
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });

  await page.goto('/registration?orderId=100');
  await page.getByRole('button', { name: 'Edit Receipt' }).click();
  const addPayment = page.getByRole('button', { name: 'Add payment' });
  await expect(addPayment).toBeVisible();
  await expect(page.getByRole('button', { name: 'Save changes' })).toBeVisible();
  await page.locator('label').filter({ hasText: 'School:' }).locator('input').fill('New School');
  await addPayment.click();
  const payment = page.locator('.course-payment-entry').last();
  await payment.locator('.el-select__wrapper').click();
  await page.getByRole('option', { name: 'Cash' }).click();
  await payment.locator('.payment-amount-field input').fill('125');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Save correction' }).click();
  await expect.poll(() => correctionPayload?.payments?.length).toBe(1);
  expect(correctionPayload?.payments[0]).toMatchObject({ amount: 125, paymentMethod: 'CASH' });
  expect(correctionPayload?.payments[0].paymentId).toBeUndefined();
  expect(correctionPayload?.correctionReason).toBeUndefined();
  await expect(page).toHaveURL(/\/registration\?orderId=100/);
});

test('Edit Receipt adds a course row under the current order and keeps the receipt available', async ({ page }) => {
  await useMockAdmin(page);
  let correctionPayload: Record<string, any> | undefined;
  const originalItem = { id: '500', lineNo: 1, courseType: 'GROUP', courseProductId: '10', courseProductName: 'After School',
    groupProjectId: '20', groupRegistrationPlanId: '30', classId: '40', classEnrollmentId: '700', className: 'G1',
    logicalCourseId: '600', gradeBand: 'G1-G5', packageUnit: 'MONTH', packageQuantity: 2, purchasedMonths: 2,
    priceAmount: 800, startDate: '2026-09-14', entitlementExpiryDate: '2026-10-31', itemStatus: 'ACTIVE' };
  const setup = { productId: '10', productName: 'After School', projects: [{ id: '20', projectName: 'G1-G5', status: 'ACTIVE',
    plans: [{ id: '30', planName: '2 months · $800', packageUnit: 'MONTH', packageQuantity: 2, fullDayPrice: 800,
      coverageType: 'ROLLING_MONTHS', status: 'ACTIVE' }],
    classes: [{ id: '40', className: 'G1', actualClass: 'G1', campusId: '1', campusName: 'North Campus',
      startDate: '2026-09-14', endDate: '2027-06-30', status: 'ACTIVE' }] }] };
  let savedItems = [originalItem];
  let receiptItems = [originalItem];
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) return route.continue();
    if (path.endsWith('/v1/enrollment/orders/100/correction') && route.request().method() === 'PUT') {
      correctionPayload = route.request().postDataJSON();
      const added = { ...correctionPayload.items[0], orderItemId: undefined, id: '501', lineNo: 2,
        classEnrollmentId: '701', logicalCourseId: '600', entitlementExpiryDate: '2026-12-31', itemStatus: 'ACTIVE' };
      savedItems = [originalItem, added];
      receiptItems = savedItems;
      await route.fulfill({ json: { success: true, code: 'OK', data: null } });
      return;
    }
    const order = { id: '100', enrollmentNo: 'EO-100', studentId: '200', studentNameSnapshot: 'Yuqi Chen',
      signupDate: '2026-09-14', version: 1, orderStatus: 'CONFIRMED', paymentStatus: 'PAID', amountDue: 1600,
      receivedAmount: 800, balanceAmount: 800, campusId: '1', campusNameSnapshot: 'North Campus',
      items: savedItems, packages: [], payments: [], participants: [],
      receipts: [{ id: '300', templateCode: 'DEFAULT_RECEIPT', receiptPayload: JSON.stringify({
        studentName: 'Yuqi Chen', signupDate: '2026-09-14', items: receiptItems, payments: [],
      }) }] };
    const data = path.endsWith('/v1/auth/me') ? admin
      : path.endsWith('/v1/enrollment/orders/100') ? order
        : path.endsWith('/v2/group-course-model/products/10') ? setup
          : path.endsWith('/v2/group-course-model/classes/40/scheduled-dates')
            ? ['2026-09-14', '2026-10-12', '2026-11-02', '2026-11-09']
            : path.endsWith('/v1/enrollment/payment-methods/active') ? []
              : path.endsWith('/v1/base/campuses') ? [{ id: '1', campusName: 'North Campus', status: 'ACTIVE' }]
                : path.endsWith('/v1/base/course-products') ? { records: [], total: 0 }
                  : [];
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });

  await page.goto('/registration?orderId=100');
  await page.getByRole('button', { name: 'Edit Receipt' }).click();
  await page.getByRole('button', { name: 'Add course' }).click();
  await expect(page.locator('.course-entry-row')).toHaveCount(2);
  await page.getByRole('button', { name: 'Save changes' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Save correction' }).click();

  await expect.poll(() => correctionPayload?.items?.length).toBe(1);
  expect(correctionPayload?.items[0]).not.toHaveProperty('orderItemId');
  expect(correctionPayload?.items[0]).toMatchObject({
    renewalSourceOrderItemId: '500',
    courseProductId: '10',
    groupProjectId: '20',
    groupRegistrationPlanId: '30',
    packageQuantity: 2,
    priceAmount: 800,
  });
  await expect(page).toHaveURL(/\/registration\?orderId=100/);
  await expect(page.locator('.course-entry-row')).toHaveCount(2);
});

test('Edit Receipt连续调整全日/半日价格后保留付款，只有手动改付款才更新付款流水', async ({ page }) => {
  await useMockAdmin(page);
  const correctionPayloads: Array<Record<string, any>> = [];
  const item: Record<string, any> = {
    id: '500', orderItemId: '500', lineNo: 1, courseType: 'GROUP', courseProductId: '10',
    courseProductName: 'After School', groupProjectId: '20', groupRegistrationPlanId: '30',
    classId: '40', classEnrollmentId: '700', className: 'G1', logicalCourseId: '600',
    gradeBand: 'G1-G5', packageUnit: 'SESSION', packageQuantity: 8, sessions: 8,
    priceAmount: 3398, originalPriceAmount: 3398, packageTotalPrice: 3398,
    attendanceOption: 'FULL_DAY', startDate: '2026-10-28', itemStatus: 'ACTIVE',
  };
  const payment: Record<string, any> = {
    paymentId: '900', id: '900', paymentMethod: 'CASH', methodName: 'Cash', amount: 3398,
    paidAt: '2026-10-28T00:00:00', handledByName: 'Admin', allocations: [{ orderItemLineNo: 1, amount: 3398 }],
  };
  const plan = { id: '30', planName: 'Full year · 8 sessions', packageUnit: 'SESSION', packageQuantity: 8,
    fullDayPrice: 3398, halfDayPrice: 2898, coverageType: 'FIXED_DATES', status: 'ACTIVE' };
  const setup = { productId: '10', productName: 'After School', projects: [{ id: '20', projectName: 'G1-G5', status: 'ACTIVE',
    plans: [plan], classes: [{ id: '40', className: 'G1', actualClass: 'G1', campusId: '1',
      campusName: 'North Campus', startDate: '2026-10-28', endDate: '2027-06-30', status: 'ACTIVE' }] }] };
  let orderVersion = 1;

  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) return route.continue();
    if (path.endsWith('/v1/enrollment/orders/100/correction') && route.request().method() === 'PUT') {
      const payload = route.request().postDataJSON();
      correctionPayloads.push(payload);
      const changedItem = payload.items?.[0];
      if (changedItem) {
        for (const field of changedItem.correctionChangedFields ?? []) item[field] = changedItem[field];
      }
      const correctedPayment = payload.payments?.[0];
      if (correctedPayment) {
        for (const field of correctedPayment.correctionChangedFields ?? []) payment[field] = correctedPayment[field];
        payment.amount = correctedPayment.amount;
        payment.allocations = correctedPayment.allocations ?? payment.allocations;
      }
      orderVersion += 1;
      await route.fulfill({ json: { success: true, code: 'OK', data: null } });
      return;
    }
    const amountDue = Number(item.priceAmount);
    const receivedAmount = Number(payment.amount);
    const order = { id: '100', enrollmentNo: 'EO-100', version: orderVersion, studentId: '200',
      studentNameSnapshot: 'Yuqi Chen', signupDate: '2026-10-28', orderStatus: 'CONFIRMED',
      paymentStatus: receivedAmount >= amountDue ? 'PAID' : receivedAmount > 0 ? 'PARTIAL' : 'UNPAID',
      amountDue, originalAmount: amountDue, tuitionSubtotalAmount: amountDue, receivedAmount,
      balanceAmount: amountDue - receivedAmount, campusId: '1', campusNameSnapshot: 'North Campus',
      schoolSnapshot: 'PS 244', items: [{ ...item }], packages: [], payments: [{ ...payment }], participants: [],
      receipts: [{ id: '300', templateCode: 'DEFAULT_RECEIPT', receiptPayload: JSON.stringify({
        studentName: 'Yuqi Chen', signupDate: '2026-10-28', items: [{ ...item }], payments: [{ ...payment }],
      }) }] };
    const data = path.endsWith('/v1/auth/me') ? admin
      : path.endsWith('/v1/enrollment/orders/100') ? order
        : path.endsWith('/v2/group-course-model/products/10') ? setup
          : path.endsWith('/v2/group-course-model/classes/40/scheduled-dates')
            ? ['2026-10-28', '2026-11-04', '2026-11-11', '2026-11-18', '2026-11-25', '2026-12-02', '2026-12-09', '2026-12-16']
            : path.endsWith('/v1/enrollment/payment-methods/active') ? [{ id: '1', methodCode: 'CASH', methodName: 'Cash', status: 'ACTIVE' }]
              : path.endsWith('/v1/base/campuses') ? [{ id: '1', campusName: 'North Campus', status: 'ACTIVE' }]
                : path.endsWith('/v1/base/course-products') ? { records: [], total: 0 }
                  : [];
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });

  await page.goto('/registration?orderId=100');
  await page.getByRole('button', { name: 'Edit Receipt' }).click();
  const courseRow = page.locator('.course-entry-row').filter({ hasText: 'After School' }).first();
  const attendance = courseRow.locator('label.course-control').filter({ hasText: 'Attendance' });
  const priceInput = courseRow.locator('.price-cell input');
  const paymentAmount = page.locator('.course-payment-entry').first().locator('.payment-amount-field input');
  await expect(priceInput).toHaveValue('3,398');

  // 第一次：整日改半日，价格自动同步；付款金额保持原样，不提交付款字段。
  await attendance.locator('.el-select__wrapper').click();
  await page.getByRole('option', { name: 'Morning half day ($2,898)' }).click();
  await expect(priceInput).toHaveValue('2,898');
  await expect(paymentAmount).toHaveValue('3,398');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Save correction' }).click();
  await expect.poll(() => correctionPayloads.length).toBe(1);
  expect(correctionPayloads[0].items[0]).toMatchObject({
    attendanceOption: 'MORNING', priceAmount: 2898, originalPriceAmount: 2898,
  });
  expect(correctionPayloads[0]).not.toHaveProperty('payments');
  await expect(page.locator('.course-entry-row').first().locator('.price-cell')).toContainText('2,898');

  // 第二次：切回全日并保存，验证页面重新加载后以上次保存值为基准继续更正。
  await page.getByRole('button', { name: 'Edit Receipt' }).click();
  const reloadedRow = page.locator('.course-entry-row').filter({ hasText: 'After School' }).first();
  await reloadedRow.locator('label.course-control').filter({ hasText: 'Attendance' }).locator('.el-select__wrapper').click();
  await page.getByRole('option', { name: 'Full day ($3,398)' }).click();
  await expect(reloadedRow.locator('.price-cell input')).toHaveValue('3,398');
  await expect(page.locator('.course-payment-entry').first().locator('.payment-amount-field input')).toHaveValue('3,398');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Save correction' }).click();
  await expect.poll(() => correctionPayloads.length).toBe(2);
  expect(correctionPayloads[1].items[0]).toMatchObject({
    attendanceOption: 'FULL_DAY', priceAmount: 3398, originalPriceAmount: 3398,
  });
  expect(correctionPayloads[1]).not.toHaveProperty('payments');

  // 第三次：再次改成半日并明确调整付款；只有这次才提交付款更正和对应分摊。
  await page.getByRole('button', { name: 'Edit Receipt' }).click();
  const finalRow = page.locator('.course-entry-row').filter({ hasText: 'After School' }).first();
  await finalRow.locator('label.course-control').filter({ hasText: 'Attendance' }).locator('.el-select__wrapper').click();
  await page.getByRole('option', { name: 'Morning half day ($2,898)' }).click();
  await expect(page.locator('.course-payment-entry').first().locator('.payment-amount-field input')).toHaveValue('3,398');
  await paymentAmount.click();
  await paymentAmount.press('Meta+A');
  await paymentAmount.pressSequentially('2800');
  await expect(paymentAmount).toHaveValue('2800');
  await paymentAmount.press('Tab');
  await expect(paymentAmount).toHaveValue('2,800');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Save correction' }).click();
  await expect.poll(() => correctionPayloads.length).toBe(3);
  expect(correctionPayloads[2].items[0]).toMatchObject({ attendanceOption: 'MORNING', priceAmount: 2898 });
  expect(correctionPayloads[2].correctionReason).toBeUndefined();
  expect(correctionPayloads[2].payments).toHaveLength(1);
  expect(correctionPayloads[2].payments[0]).toMatchObject({ paymentId: '900', amount: 2800 });
  expect(correctionPayloads[2].payments[0].correctionChangedFields).toContain('amount');
  await expect(page.locator('.course-entry-row').first().locator('.price-cell')).toContainText('2,898');
});
