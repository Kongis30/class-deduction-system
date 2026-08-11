import { expect, test, type Page } from '@playwright/test';

import { apiLogin } from '../helpers/api';
import { queryAll, queryOne } from '../helpers/db';
import { loginAsAdmin } from '../ui/helpers/session';
import {
  ACCEPTANCE_MARKER,
  cleanupAcceptanceData,
  createOneOnOneProductAndPriceByApi,
  ensureTransferPaymentMethod,
  normalizeReceiptPayload,
  readReceiptPayload,
  submitOneOnOneRegistrationByApi,
  withAcceptanceDb,
} from './helpers';

test.describe.serial('Acceptance - Financial business flow', () => {
  test.beforeAll(async () => {
    await cleanupAcceptanceData();
  });

  test.afterAll(async () => {
    await cleanupAcceptanceData();
  });

  test('部分付款报名、后续付款、部分退款和三类收据应形成完整闭环', async ({ page, request }) => {
    const token = await apiLogin(request);
    const suffix = Date.now().toString(36);
    await ensureTransferPaymentMethod(request, token);
    const { product, option } = await createOneOnOneProductAndPriceByApi(request, token, suffix, 900);
    const registration = await submitOneOnOneRegistrationByApi(request, token, {
      suffix,
      productId: product.id,
      optionId: option.id,
      priceAmount: 900,
      paidAmount: 300,
    });
    const studentName = `${ACCEPTANCE_MARKER} Student ${suffix}`;
    const orderId = registration.enrollmentOrderId;
    const originalReceiptPayload = await getRegistrationReceiptPayload(orderId);

    await loginAsAdmin(page);
    await recordFollowUpPaymentFromEnrollmentHistory(page, studentName, 400);
    let receiptDialog = visibleDialog(page).filter({ hasText: 'Payment Receipt' }).last();
    await expect(receiptDialog.locator('.el-dialog__title')).toHaveText('Payment Receipt');
    await expect(receiptDialog.getByText(registration.enrollmentNo)).toBeVisible();
    await expect(receiptDialog.getByText('$400.00').first()).toBeVisible();
    await closeVisibleDialog(page);

    await refundFromStudentDetail(page, studentName, 150);
    receiptDialog = visibleDialog(page).filter({ hasText: 'Refund Receipt' }).last();
    await expect(receiptDialog.locator('.el-dialog__title')).toHaveText('Refund Receipt');
    await expect(receiptDialog.getByText(registration.enrollmentNo)).toBeVisible();
    await expect(receiptDialog.getByText('$150.00').first()).toBeVisible();
    await closeVisibleDialog(page);

    await assertReceiptRecordsVisible(page, studentName, registration.enrollmentNo);
    await assertFinancialLedger(orderId, originalReceiptPayload);
  });

  test('未付款报名应可取消，取消后不能再作为可收款订单处理', async ({ page, request }) => {
    const token = await apiLogin(request);
    const suffix = `cancel-${Date.now().toString(36)}`;
    const { product, option } = await createOneOnOneProductAndPriceByApi(request, token, suffix, 600);
    const registration = await submitOneOnOneRegistrationByApi(request, token, {
      suffix,
      productId: product.id,
      optionId: option.id,
      priceAmount: 600,
    });
    const studentName = `${ACCEPTANCE_MARKER} Student ${suffix}`;

    await loginAsAdmin(page);
    await page.goto('/enrollment/orders');
    await page.getByPlaceholder('Order no. or student name').fill(studentName);
    await expect(page.getByText(studentName)).toBeVisible();

    const row = page.locator('.el-table__row').filter({ hasText: studentName }).first();
    await row.locator('.order-action-more').click();
    await page.getByRole('menuitem', { name: 'Cancel' }).click();
    await page.getByRole('button', { name: 'OK' }).click();
    await expect(page.getByText('Order cancelled.')).toBeVisible();

    await withAcceptanceDb(async (db) => {
      const order = await queryOne<Record<string, any>>(db, 'SELECT * FROM enrollment_order WHERE id = ?', [registration.enrollmentOrderId]);
      expect(order).toBeTruthy();
      expect(order!.order_status).toBe('CANCELLED');
      expect(order!.payment_status).toBe('UNPAID');
      expect(Number(order!.received_amount).toFixed(2)).toBe('0.00');
    });
  });
});

async function recordFollowUpPaymentFromEnrollmentHistory(page: Page, studentName: string, amount: number): Promise<void> {
  await page.goto('/enrollment/orders');
  await page.getByPlaceholder('Order no. or student name').fill(studentName);
  const row = page.locator('.el-table__row').filter({ hasText: studentName }).first();
  await expect(row).toBeVisible();
  await row.locator('.order-action-more').click();
  await page.getByRole('menuitem', { name: 'Pay' }).click();

  const dialog = visibleDialog(page).filter({ hasText: 'Outstanding Balance' }).last();
  await expect(dialog.getByRole('heading', { name: 'Record Payment' })).toBeVisible();
  await dialog.getByRole('spinbutton').fill(String(amount));
  await dialog.getByPlaceholder('Handler').fill('System Admin');
  await dialog.getByPlaceholder('Notes').fill(`${ACCEPTANCE_MARKER} follow-up payment`);
  await dialog.getByRole('button', { name: 'Record Payment' }).click();
}

async function refundFromStudentDetail(page: Page, studentName: string, amount: number): Promise<void> {
  await page.goto(`/admin/config/people?tab=students&keyword=${encodeURIComponent(studentName)}`);
  const row = page.locator('.el-table__row').filter({ hasText: studentName }).first();
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: 'Details' }).click();

  const drawer = page.locator('.student-detail-drawer');
  await expect(drawer.getByText('Registration history')).toBeVisible();
  await drawer.getByRole('button', { name: 'Refund' }).click();

  const dialog = visibleDialog(page).filter({ hasText: 'Refund amount' }).last();
  await dialog.getByRole('spinbutton').fill(String(amount));
  await dialog.getByPlaceholder('Your name').fill('System Admin');
  await dialog.getByPlaceholder('Optional reason').fill(`${ACCEPTANCE_MARKER} partial refund`);
  await dialog.getByRole('button', { name: 'Confirm refund' }).click();
}

async function assertReceiptRecordsVisible(page: Page, studentName: string, enrollmentNo: string): Promise<void> {
  await page.goto('/enrollment/receipts');
  await page.getByPlaceholder('Search student').fill(studentName);
  await page.getByRole('button', { name: 'Search' }).click();
  await expect(page.getByText(studentName).first()).toBeVisible();

  await expect(page.getByText(enrollmentNo).first()).toBeVisible();

  await page.getByRole('tab', { name: 'Payment receipts' }).click();
  await expect(page.getByText(enrollmentNo).first()).toBeVisible();
  await page.getByRole('button', { name: 'View receipt' }).first().click();
  await expect(visibleDialog(page).last().locator('.el-dialog__title')).toHaveText('Payment Receipt');
  await closeVisibleDialog(page);

  await page.getByRole('tab', { name: 'Refund receipts' }).click();
  await expect(page.getByText(enrollmentNo).first()).toBeVisible();
  await page.getByRole('button', { name: 'View receipt' }).first().click();
  await expect(visibleDialog(page).last().locator('.el-dialog__title')).toHaveText('Refund Receipt');
  await closeVisibleDialog(page);
}

async function assertFinancialLedger(orderId: string | number, originalReceiptPayload: string): Promise<void> {
  await withAcceptanceDb(async (db) => {
    const order = await queryOne<Record<string, any>>(db, 'SELECT * FROM enrollment_order WHERE id = ?', [orderId]);
    expect(order).toBeTruthy();
    expect(order!.order_status).toBe('CONFIRMED');
    expect(order!.payment_status).toBe('PARTIAL');
    expect(Number(order!.received_amount).toFixed(2)).toBe('550.00');
    expect(Number(order!.balance_amount).toFixed(2)).toBe('350.00');

    const payments = await queryAll<Record<string, any>>(
      db,
      'SELECT payment_type, amount, remark FROM payment_record WHERE enrollment_order_id = ? ORDER BY id',
      [orderId],
    );
    expect(payments.map((item) => item.payment_type)).toEqual(['PAYMENT', 'PAYMENT', 'REFUND']);
    expect(payments.map((item) => Number(item.amount).toFixed(2))).toEqual(['300.00', '400.00', '-150.00']);
    expect(payments[1].remark).toBe(`${ACCEPTANCE_MARKER} follow-up payment`);
    expect(payments[2].remark).toBe(`${ACCEPTANCE_MARKER} partial refund`);

    const receipts = await queryAll<Record<string, any>>(
      db,
      'SELECT template_code, receipt_payload FROM receipt_record WHERE enrollment_order_id = ? ORDER BY issued_at, id',
      [orderId],
    );
    expect(receipts.map((item) => item.template_code).sort()).toEqual([
      'DEFAULT_RECEIPT',
      'PAYMENT_RECEIPT',
      'REFUND_RECEIPT',
    ]);
    const registrationReceipt = receipts.find((item) => item.template_code === 'DEFAULT_RECEIPT');
    const paymentReceipt = receipts.find((item) => item.template_code === 'PAYMENT_RECEIPT');
    const refundReceipt = receipts.find((item) => item.template_code === 'REFUND_RECEIPT');
    expect(normalizeReceiptPayload(registrationReceipt!.receipt_payload)).toBe(originalReceiptPayload);
    expect(readReceiptPayload(paymentReceipt!.receipt_payload).paymentAmount).toBe(400);
    expect(readReceiptPayload(refundReceipt!.receipt_payload).paymentAmount).toBe(150);
  });
}

async function getRegistrationReceiptPayload(orderId: string | number): Promise<string> {
  return withAcceptanceDb(async (db) => {
    const receipt = await queryOne<Record<string, any>>(
      db,
      'SELECT receipt_payload FROM receipt_record WHERE enrollment_order_id = ? AND template_code = "DEFAULT_RECEIPT"',
      [orderId],
    );
    expect(receipt).toBeTruthy();
    return normalizeReceiptPayload(receipt!.receipt_payload);
  });
}

function visibleDialog(page: Page) {
  return page.locator('.el-dialog:visible');
}

async function closeVisibleDialog(page: Page): Promise<void> {
  await page.keyboard.press('Escape');
  await expect(visibleDialog(page)).toHaveCount(0);
}
