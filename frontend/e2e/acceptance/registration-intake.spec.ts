import { expect, test } from '@playwright/test';

import { apiLogin, apiPost, todayInNewYork } from '../helpers/api';
import { loginAsAdmin } from '../ui/helpers/session';
import {
  assertRegistrationDbState,
  cleanupAcceptanceData,
  createOneOnOneProductAndPriceByApi,
  ensureTransferPaymentMethod,
  withAcceptanceDb,
} from './helpers';

test.describe.serial('Acceptance - Registration intake', () => {
  test.beforeAll(async () => {
    await cleanupAcceptanceData();
  });

  test.afterAll(async () => {
    await cleanupAcceptanceData();
  });

  test('报名保存入口应有前端校验，提交后应创建订单、课包、付款流水和收据快照', async ({ page, request }) => {
    const token = await apiLogin(request);
    const suffix = Date.now().toString(36);
    await ensureTransferPaymentMethod(request, token);
    const { product, option } = await createOneOnOneProductAndPriceByApi(request, token, suffix, 900);

    await loginAsAdmin(page);
    const productsLoaded = page.waitForResponse((res) =>
      res.url().includes('/api/v1/base/course-products') && res.request().method() === 'GET',
    );
    const pricesLoaded = page.waitForResponse((res) =>
      res.url().includes('/api/v1/base/course-price-options/active') && res.request().method() === 'GET',
    );
    await page.goto('/registration');
    expect((await productsLoaded).ok(), 'registration should load active course products').toBeTruthy();
    expect((await pricesLoaded).ok(), 'registration should load active course price options').toBeTruthy();

    await page.getByRole('button', { name: 'Save' }).click();
    await expect(page.locator('.validation-list li').filter({ hasText: 'Select one campus before submitting.' })).toBeVisible();

    const result = await apiPost<Record<string, any>>(request, token, '/v1/registration/intake', {
      student: {
        studentName: `Acceptance Student ${suffix}`,
        grade: 'G4',
        school: 'Acceptance School',
        guardianPhone: `626555${suffix.slice(-4).padStart(4, '0')}`,
      },
      campusName: 'Online Class',
      signupDate: todayInNewYork(),
      periodStartDate: todayInNewYork(),
      periodEndDate: todayInNewYork(),
      handledByName: `${suffix} front desk`,
      items: [{
        lineNo: 1,
        registerClass: '1-on-1',
        courseType: 'ONE_ON_ONE',
        courseProductId: product.id,
        coursePriceOptionId: option.id,
        detail: option.detailText,
        gradeBand: option.gradeBand,
        periodLabel: option.periodLabel,
        priceAmount: 900,
        originalPriceAmount: 900,
        oneOnOneHours: 40,
        oneOnOneUnits: 80,
      }],
      payments: [{ paymentMethod: 'TRANSFER', amount: 300, paidAt: `${todayInNewYork()}T00:00:00` }],
      receiptOptions: { issueReceipt: true },
      remark: `E2E_ACCEPTANCE registration ${suffix}`,
    });

    expect(result.amountDue).toBe(900);
    expect(result.paidAmount).toBe(300);
    expect(result.balanceAmount).toBe(600);
    expect(result.paymentStatus).toBe('PARTIAL');
    expect(result.receiptId).toBeTruthy();

    await withAcceptanceDb((db) =>
      assertRegistrationDbState(db, result, {
        paymentMethod: 'TRANSFER',
        paidAmount: '300.00',
        balanceAmount: '600.00',
      }),
    );
  });
});
