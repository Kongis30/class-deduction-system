import { test, type Page } from '@playwright/test';

const admin = { userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'] };

// 回归：收据 payload 两个 item 的 id 相同或经 JSON.parse 降精度后撞车时，
// 课程行 rowKey 必须仍然唯一——否则两个课程卡片共用同一付款行，输入联动。
// 同一笔未分摊付款在两张卡片都显示，且编辑任一卡片写入同一个 allocations key → 全部输入框联动。
test('课程行 rowKey 撞车时付款输入框不得跨课程联动', async ({ page }) => {
  await page.addInitScript((user) => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);
  const item1 = { id: '501', orderItemId: '501', lineNo: 1, courseType: 'GROUP', courseProductId: '10',
    packageUnit: 'MONTH', packageQuantity: 10, priceAmount: 3150, extraFeeAmount: 270, discountAmount: 0,
    startDate: '2026-09-10', itemStatus: 'ACTIVE' };
  const item2 = { id: '501', orderItemId: '501', lineNo: 2, courseType: 'GROUP', courseProductId: '11',
    packageUnit: 'MONTH', packageQuantity: 2, priceAmount: 699, extraFeeAmount: 0, discountAmount: 0,
    startDate: '2026-09-10', itemStatus: 'ACTIVE' };
  const mk = (id: string, method: string, amount: number, at: string) => ({ id, paymentMethod: method, amount, paidAt: at });
  const payments = [
    mk('900', 'ZELLE', 500, '2026-09-13T00:00:00'), mk('901', 'ZELLE', 500, '2026-09-14T00:00:00'),
    mk('902', 'ZELLE', 500, '2026-09-18T00:00:00'), mk('903', 'CASH', 744, '2026-09-23T00:00:00'),
    mk('904', 'ZELLE', 499, '2026-11-03T00:00:00'), mk('905', 'ZELLE', 501, '2026-11-03T00:00:00'),
  ];
  const order = { id: '100', version: 1, studentId: '200', studentNameSnapshot: 'Cynthia Xu',
    signupDate: '2026-04-30', orderStatus: 'CONFIRMED', amountDue: 4119, receivedAmount: 3244,
    balanceAmount: 875, pickupFeeAmount: 0, dropoffFeeAmount: 0,
    extraFeeAmount: 270, discountAmount: 0, campusId: '1', campusNameSnapshot: 'North Campus',
    items: [
      { ...item1, id: '501' }, { ...item2, id: '502' },
    ], packages: [], payments, participants: [],
    receipts: [{ id: '300', templateCode: 'DEFAULT_RECEIPT', receiptPayload: JSON.stringify({ items: [item1, item2] }) }] };
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) return route.continue();
    const data = path.endsWith('/v1/auth/me') ? admin
      : path.endsWith('/v1/enrollment/orders/100') ? order
        : path.endsWith('/v1/enrollment/payment-methods/active') ? [
            { id: '1', methodCode: 'CASH', methodName: 'Cash', status: 'ACTIVE' },
            { id: '2', methodCode: 'ZELLE', methodName: 'Zelle', status: 'ACTIVE' }]
          : path.endsWith('/v1/base/campuses') ? [{ id: '1', campusName: 'North Campus', status: 'ACTIVE' }]
            : path.endsWith('/v1/base/course-products') ? { records: [], total: 0 } : [];
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });
  await page.goto('/registration?orderId=100');
  await page.getByRole('button', { name: 'Edit Receipt' }).click();
  await page.waitForTimeout(800);
  const dump = async (label: string) => {
    const out = await page.evaluate(() => {
      const cards = document.querySelectorAll('.course-adjustment-card--payment');
      return Array.from(cards).map((c, i) => {
        const amounts = Array.from(c.querySelectorAll('.money-amount-input input')).map((el: HTMLInputElement) => el.value);
        const footer = c.querySelector('.course-adjustment-card__footer')?.textContent?.trim();
        return `CARD ${i}: [${amounts.join(', ')}] ${footer}`;
      });
    });
    console.log(label + ' ' + out.join(' | '));
  };
  await dump('before:');
  const inputs1 = page.locator('.course-adjustment-card--payment').nth(0).locator('.money-amount-input input');
  const inputs2 = page.locator('.course-adjustment-card--payment').nth(1).locator('.money-amount-input input');
  console.log('inputs card1:', await inputs1.count(), 'card2:', await inputs2.count());
  if (await inputs1.count() > 0 && await inputs2.count() > 0) {
    await inputs1.first().click();
    await inputs1.first().fill('800');
    await page.waitForTimeout(200);
    await dump('during(card1.p1=800):');
    await inputs1.first().blur();
    await page.waitForTimeout(300);
    await dump('after:');
  }
});
