import { expect, test } from '@playwright/test';

const admin = {
  userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'],
};

test('saved receipt prints its content through the browser print command', async ({ page }) => {
  await page.addInitScript((user) => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);

  const receiptPayload = {
    studentName: 'Print Receipt Student',
    grade: 'G4',
    school: 'Test School',
    campusName: 'North Campus',
    signupDate: '2026-10-14',
    amountDue: 500,
    paidAmount: 500,
    balanceAmount: 0,
    items: [{
      lineNo: 1,
      courseType: 'ONE_ON_ONE',
      registerClass: '1-on-1',
      detail: 'Math 10 hours',
      gradeBand: 'G4',
      priceAmount: 500,
      oneOnOneHours: 10,
      oneOnOneUnits: 20,
    }],
    payments: [{ paymentMethod: 'CASH', amount: 500, paidAt: '2026-10-14' }],
  };
  const order = {
    id: '100', studentId: '200', studentNameSnapshot: receiptPayload.studentName,
    items: receiptPayload.items, packages: [], payments: [], participants: [],
    receipts: [{ id: '300', templateCode: 'DEFAULT_RECEIPT', receiptPayload: JSON.stringify(receiptPayload) }],
  };

  let releaseOrderRequest!: () => void;
  const orderRequestGate = new Promise<void>((resolve) => {
    releaseOrderRequest = resolve;
  });

  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) {
      await route.continue();
      return;
    }
    if (path.endsWith('/v1/enrollment/orders/100')) {
      await orderRequestGate;
    }
    const data = path.endsWith('/v1/auth/me') ? admin
      : path.endsWith('/v1/enrollment/orders/100') ? order : [];
    await route.fulfill({ json: { success: true, code: 'OK', data } });
  });

  await page.goto('/registration?orderId=100');
  const printButton = page.getByRole('button', { name: 'Print', exact: true });
  await expect(printButton).toBeDisabled();
  releaseOrderRequest();
  await expect(page.locator('#app .paper-form--readonly')).toContainText('Print Receipt Student');
  await expect(printButton).toBeEnabled();

  await page.evaluate(() => {
    window.print = () => {
      const preparedBeforePrint = document.querySelector('#print-document-root .paper-form');
      window.dispatchEvent(new Event('beforeprint'));
      const printRoot = document.querySelector<HTMLElement>('#print-document-root .paper-form');
      document.documentElement.dataset.receiptPrintInvoked = 'yes';
      document.documentElement.dataset.receiptPrintSnapshotPreserved = String(preparedBeforePrint === printRoot);
      document.documentElement.dataset.receiptPrintContentReady = String(
        Boolean(printRoot?.innerText.includes('Print Receipt Student')),
      );
    };
  });
  await printButton.click();
  await expect(page.locator('html')).toHaveAttribute('data-receipt-print-invoked', 'yes');
  await expect(page.locator('html')).toHaveAttribute('data-receipt-print-snapshot-preserved', 'true');
  await expect(page.locator('html')).toHaveAttribute('data-receipt-print-content-ready', 'true');
  await expect(page.locator('#print-document-root .paper-form')).toContainText('Print Receipt Student');
  await page.evaluate(() => window.dispatchEvent(new Event('afterprint')));

  // 模拟直接按 ⌘P：不经过页面按钮，也必须在打印媒体下展示已保存的收据。
  await page.evaluate(() => window.dispatchEvent(new Event('beforeprint')));
  await page.emulateMedia({ media: 'print' });
  await expect(page.locator('#print-document-root .paper-form')).toContainText('Print Receipt Student');
  await expect(page.locator('#print-document-root .paper-form')).toBeVisible();
  await expect(page.locator('#app')).toBeHidden();

  await page.evaluate(() => window.dispatchEvent(new Event('afterprint')));
  await expect(page.locator('#print-document-root')).toHaveCount(0);
});
