import { expect, test } from '@playwright/test';

const admin = {
  userId: '1',
  username: 'admin',
  displayName: 'Admin',
  userType: 'ADMIN',
  roleCodes: ['ADMIN'],
};

// 使用固定的退款名单验证真实布局：拖动后下表增高，上表必须同步收缩。
test('Data Summary refund splitter resizes both table viewports', async ({ page }) => {
  await page.addInitScript((user) => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);

  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) {
      await route.continue();
      return;
    }
    let data: unknown = [];
    if (path.endsWith('/v1/auth/me')) {
      data = admin;
    } else if (path.endsWith('/v1/base/course-products/all')) {
      data = [{ id: '8001', productName: 'After School G1', sortOrder: 1, status: 'ACTIVE', teachingMode: 'GROUP' }];
    } else if (path.endsWith('/v1/data-summary')) {
      data = {
        rows: [
          {
            orderId: '1001', orderItemId: '2001', studentId: '3001', studentName: 'Active Student',
            courseProductId: '8001', courseProductName: 'After School G1', className: 'G1',
            signupDate: '2026-09-10', paidAmount: 800, balanceAmount: 0,
          },
          {
            orderId: '1002', orderItemId: '2002', studentId: '3002', studentName: 'Refunded Student',
            courseProductId: '8001', courseProductName: 'After School G1', className: 'G1',
            signupDate: '2026-09-10', paidAmount: 0, balanceAmount: 0, refunded: true,
          },
        ],
      };
    }
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });

  await page.goto('/data-summary');
  const handle = page.getByRole('separator', { name: 'Resize space between tables' });
  const workbook = page.locator('.workbook-scroll');
  const refundViewport = page.locator('.refund-records__scroll');
  await expect(handle).toBeVisible();
  await expect(refundViewport).toBeVisible();
  await expect(page.locator('.data-summary-page .el-loading-mask')).toBeHidden();
  await handle.hover();

  const initialWorkbookHeight = await workbook.evaluate((element) => element.getBoundingClientRect().height);
  const initialRefundHeight = await refundViewport.evaluate((element) => element.getBoundingClientRect().height);
  const box = await handle.boundingBox();
  expect(box).not.toBeNull();

  await page.mouse.move(box!.x + box!.width / 2, box!.y + box!.height / 2);
  await page.mouse.down();
  await page.mouse.move(box!.x + box!.width / 2, box!.y + box!.height / 2 - 100, { steps: 5 });
  await page.mouse.up();

  const resizedWorkbookHeight = await workbook.evaluate((element) => element.getBoundingClientRect().height);
  const resizedRefundHeight = await refundViewport.evaluate((element) => element.getBoundingClientRect().height);
  expect(resizedRefundHeight).toBeLessThan(initialRefundHeight - 70);
  expect(resizedWorkbookHeight).toBeGreaterThan(initialWorkbookHeight + 70);

  const raisedBox = await handle.boundingBox();
  expect(raisedBox).not.toBeNull();
  await page.mouse.move(raisedBox!.x + raisedBox!.width / 2, raisedBox!.y + raisedBox!.height / 2);
  await page.mouse.down();
  await page.mouse.move(raisedBox!.x + raisedBox!.width / 2, raisedBox!.y + raisedBox!.height / 2 + 120, { steps: 5 });
  await page.mouse.up();

  const loweredWorkbookHeight = await workbook.evaluate((element) => element.getBoundingClientRect().height);
  const loweredRefundHeight = await refundViewport.evaluate((element) => element.getBoundingClientRect().height);
  expect(loweredRefundHeight).toBeGreaterThan(resizedRefundHeight + 90);
  expect(loweredWorkbookHeight).toBeLessThan(resizedWorkbookHeight - 90);

  await handle.focus();
  await page.keyboard.press('ArrowUp');
  await expect(handle).toHaveAttribute('aria-valuenow', String(loweredRefundHeight - 20));
});
