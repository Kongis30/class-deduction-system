import { expect, test, type Locator, type Page } from '@playwright/test';

import { apiLogin } from '../helpers/api';
import { queryOne } from '../helpers/db';
import { loginAsAdmin } from '../ui/helpers/session';
import {
  ACCEPTANCE_MARKER,
  cleanupAcceptanceData,
  createClassroomByApi,
  createOneOnOneProductAndPriceByApi,
  createRecurringScheduleByApi,
  createTeacherAccountByApi,
  ensureTransferPaymentMethod,
  withAcceptanceDb,
  type ApiRecord,
} from './helpers';
import { saveTeacherPayRateByApi } from '../ui/business-flow/business-flow-api';

test.describe.serial('Acceptance - 1-on-1 registration, deduction, and teacher payment browser flow', () => {
  test.beforeAll(async () => {
    await cleanupAcceptanceData();
  });

  test.afterAll(async () => {
    await cleanupAcceptanceData();
  });

  test('admin can register 1-on-1, deduct a scheduled lesson, pay the teacher, and persist the data', async ({ page, request }) => {
    const token = await apiLogin(request);
    const suffix = `browser-${Date.now().toString(36)}`;
    const marker = `${ACCEPTANCE_MARKER} ${suffix}`;
    await ensureTransferPaymentMethod(request, token);
    const teacher = await createTeacherAccountByApi(request, token, suffix);
    const classroom = await createClassroomByApi(request, token, suffix);
    const { product, option } = await createOneOnOneProductAndPriceByApi(request, token, suffix, 1200);
    await saveTeacherPayRateByApi(request, token, marker, {
      teacherId: teacher.profile.id,
      courseProductId: product.id,
      hourlyRate: 55,
    });

    await loginAsAdmin(page);
    const registration = await submitOneOnOneRegistrationFromUi(page, {
      productName: product.productName,
      gradeBand: option.gradeBand,
      packageLabel: option.periodLabel || option.durationLabel,
      firstName: 'Browser',
      lastName: suffix,
      phone: `626555${String(Date.now()).slice(-4)}`,
      paidAmount: 300,
      handledByName: `${ACCEPTANCE_MARKER} Handler ${suffix}`,
      remark: marker,
    });

    const packageId = registration.courseArtifacts[0]?.packageId;
    expect(packageId, '1-on-1 registration should create a package').toBeTruthy();

    await createRecurringScheduleByApi(request, token, packageId, teacher.profile.id, classroom.id);
    await deductFirstLessonFromUi(page, `Browser ${suffix}`);
    const lessonRecord = await fetchConfirmedLessonRecord(packageId);

    await createAndConfirmTeacherPaymentFromUi(page, teacher.profile.teacherName, 110);
    await assertPersistedFlowData({
      orderId: registration.enrollmentOrderId,
      packageId,
      lessonRecordId: lessonRecord.id,
      teacherId: teacher.profile.id,
      expectedPaymentAmount: 110,
    });
  });
});

async function submitOneOnOneRegistrationFromUi(
  page: Page,
  data: {
    productName: string;
    gradeBand: string;
    packageLabel: string;
    firstName: string;
    lastName: string;
    phone: string;
    paidAmount: number;
    handledByName: string;
    remark: string;
  },
): Promise<ApiRecord> {
  const intakeResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/registration/intake') && res.request().method() === 'POST',
  );
  await page.goto('/registration');
  await expect(page.getByRole('heading', { name: 'Registration & Receipt' })).toBeVisible();

  await page.getByPlaceholder('First name').fill(data.firstName);
  await page.getByPlaceholder('Last name').fill(data.lastName);
  await fillLineInput(page, 'Grade:', 'G4');
  await fillLineInput(page, 'School:', 'Browser Flow School');
  await fillLineInput(page, 'Guardian phone:', data.phone);

  await page.locator('.course-entry-row').first().locator('.table-select--primary').click();
  await page.getByRole('option', { name: data.productName, exact: true }).last().click();
  await setCourseRowSelect(page, 'Grade', data.gradeBand);
  await setCourseRowSelect(page, 'Package', data.packageLabel);

  const paymentRow = page.locator('.adjustment-row--payment').first();
  await paymentRow.locator('.el-input-number input').fill(String(data.paidAmount));
  await paymentRow.locator('.el-input-number input').blur();
  await page.getByPlaceholder('Handled by').fill(data.handledByName);
  await page.getByPlaceholder('Registration remark').fill(data.remark);
  await page.getByRole('button', { name: 'Save' }).click();

  const response = await intakeResponse;
  expect(response.ok(), await response.text()).toBeTruthy();
  const body = JSON.parse(preprocessLargeIds(await response.text())) as { success: boolean; message: string; data: ApiRecord };
  expect(body.success, body.message).toBe(true);
  await expect(page).toHaveURL(/\/schedule\/recurring/);
  return body.data;
}

async function deductFirstLessonFromUi(page: Page, studentName: string): Promise<void> {
  await page.goto('/one-on-one-deduction');
  await page.getByPlaceholder(/Name, guardian, phone/).fill(studentName);
  await page.getByRole('button', { name: 'Search' }).click();
  await expect(page.locator('.oo-student-group__toggle').filter({ hasText: studentName })).toBeVisible();
  await page.locator('.oo-student-group__toggle').filter({ hasText: studentName }).click();
  await page.locator('.oo-package-item').first().click();

  const lessonRecordResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/lessons/records') && res.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Review & deduct' }).first().click();
  const dialog = visibleDialog(page).filter({ hasText: 'Review & Deduct Lesson' }).last();
  await expect(dialog).toBeVisible();
  await dialog.locator('.el-dialog__footer .el-button--primary').click();
  const response = await lessonRecordResponse;
  expect(response.ok(), await response.text()).toBeTruthy();
  await expect(page.getByText(/Lesson directly confirmed by admin|Review complete/)).toBeVisible();
}

async function createAndConfirmTeacherPaymentFromUi(page: Page, teacherName: string, expectedAmount: number): Promise<void> {
  const createPaymentResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/finance/payments') && res.request().method() === 'POST' && !res.url().includes('/confirm'),
  );
  await page.goto('/finance/payments');
  await expect(page.getByRole('heading', { name: 'Teacher Payments' })).toBeVisible();
  await page.getByPlaceholder('Search teacher...').fill(teacherName);
  await page.locator('.tp-sidebar__item').filter({ hasText: teacherName }).click();
  await expect(page.getByText('1:1 Lessons')).toBeVisible();
  await expect(page.getByText(`$${expectedAmount.toFixed(2)}`).first()).toBeVisible();
  await page.locator('.tp-section--card').filter({ hasText: '1:1 Lessons' }).locator('.el-checkbox__input').first().click();
  await expect(page.getByText(`Amount: $${expectedAmount.toFixed(2)}`)).toBeVisible();
  await page.getByRole('button', { name: 'Pay' }).click();
  await page.getByRole('button', { name: 'OK' }).click();
  const createResponse = await createPaymentResponse;
  expect(createResponse.ok(), await createResponse.text()).toBeTruthy();
  await expect(page.getByText('Payment created.')).toBeVisible();

  const confirmResponse = page.waitForResponse((res) =>
    res.url().includes('/api/v1/finance/payments/') && res.url().includes('/confirm') && res.request().method() === 'POST',
  );
  const historyRow = page.locator('.el-table__row').filter({ hasText: `$${expectedAmount.toFixed(2)}` }).filter({ hasText: 'PENDING' }).first();
  await expect(historyRow).toBeVisible();
  await historyRow.getByRole('button', { name: 'Confirm' }).click();
  await page.getByRole('button', { name: 'OK' }).click();
  const response = await confirmResponse;
  expect(response.ok(), await response.text()).toBeTruthy();
  await expect(page.getByText('Payment confirmed.')).toBeVisible();
  await expect(page.locator('.el-table__row').filter({ hasText: `$${expectedAmount.toFixed(2)}` }).filter({ hasText: 'PAID' }).first()).toBeVisible();
}

async function fetchConfirmedLessonRecord(packageId: string | number): Promise<ApiRecord> {
  return withAcceptanceDb(async (db) => {
    const record = await queryOne<ApiRecord>(
      db,
      'SELECT * FROM lesson_record WHERE package_id = ? AND confirm_stage = "CONFIRMED" ORDER BY confirmed_at DESC LIMIT 1',
      [packageId],
    );
    expect(record).toBeTruthy();
    return record;
  });
}

async function assertPersistedFlowData(data: {
  orderId: string | number;
  packageId: string | number;
  lessonRecordId: string | number;
  teacherId: string | number;
  expectedPaymentAmount: number;
}): Promise<void> {
  await withAcceptanceDb(async (db) => {
    const order = await queryOne<ApiRecord>(db, 'SELECT * FROM enrollment_order WHERE id = ?', [data.orderId]);
    expect(order).toBeTruthy();
    expect(order!.payment_status).toBe('PARTIAL');
    expect(Number(order!.received_amount).toFixed(2)).toBe('300.00');

    const pkg = await queryOne<ApiRecord>(db, 'SELECT * FROM course_package WHERE id = ?', [data.packageId]);
    expect(pkg).toBeTruthy();
    expect(Number(pkg!.total_units)).toBe(80);
    expect(Number(pkg!.consumed_units)).toBe(4);
    expect(Number(pkg!.remaining_units)).toBe(76);

    const record = await queryOne<ApiRecord>(db, 'SELECT * FROM lesson_record WHERE id = ?', [data.lessonRecordId]);
    expect(record).toBeTruthy();
    expect(record!.result_status).toBe('COMPLETED');
    expect(record!.confirm_stage).toBe('CONFIRMED');
    expect(Number(record!.actual_units)).toBe(4);
    expect(Number(record!.deducted_units)).toBe(4);

    const ledger = await queryOne<ApiRecord>(
      db,
      'SELECT * FROM hour_ledger WHERE lesson_record_id = ? AND ledger_type = "DEDUCT"',
      [data.lessonRecordId],
    );
    expect(ledger).toBeTruthy();
    expect(Number(ledger!.change_units)).toBe(-4);
    expect(Number(ledger!.balance_after_units)).toBe(76);

    const payment = await queryOne<ApiRecord>(
      db,
      'SELECT * FROM teacher_payment WHERE teacher_id = ? ORDER BY created_at DESC LIMIT 1',
      [data.teacherId],
    );
    expect(payment).toBeTruthy();
    expect(payment!.status).toBe('PAID');
    expect(Number(payment!.total_hours).toFixed(1)).toBe('2.0');
    expect(Number(payment!.total_amount).toFixed(2)).toBe(data.expectedPaymentAmount.toFixed(2));

    const paymentItem = await queryOne<ApiRecord>(
      db,
      'SELECT * FROM teacher_payment_item WHERE payment_id = ? AND lesson_record_id = ?',
      [payment!.id, data.lessonRecordId],
    );
    expect(paymentItem).toBeTruthy();
    expect(Number(paymentItem!.actual_units)).toBe(4);
    expect(Number(paymentItem!.hourly_rate).toFixed(2)).toBe('55.00');
    expect(Number(paymentItem!.amount).toFixed(2)).toBe(data.expectedPaymentAmount.toFixed(2));
  });
}

async function fillLineInput(page: Page, label: string, value: string): Promise<void> {
  const row = page.locator('label').filter({ hasText: label }).first();
  await row.locator('input').fill(value);
}

async function setCourseRowSelect(page: Page, label: string, optionName: string): Promise<void> {
  const row = page.locator('.course-entry-row').first();
  const field = row.locator('.course-control').filter({ hasText: label }).first();
  await field.locator('.el-select').click();
  await page.getByRole('option').filter({ hasText: optionName }).last().click();
}

function visibleDialog(page: Page): Locator {
  return page.locator('.el-dialog:visible');
}

function preprocessLargeIds(jsonString: string): string {
  return jsonString
    .replace(/"(\w*[iI][dD])":\s*(\d{15,})/g, '"$1":"$2"')
    .replace(/"data":\s*(\d{15,})/g, '"data":"$1"')
    .replace(/"packageIds":\s*\[(\d{15,})\]/g, '"packageIds":["$1"]')
    .replace(/"paymentIds":\s*\[(\d{15,})\]/g, '"paymentIds":["$1"]')
    .replace(/"receiptIds":\s*\[(\d{15,})\]/g, '"receiptIds":["$1"]');
}
