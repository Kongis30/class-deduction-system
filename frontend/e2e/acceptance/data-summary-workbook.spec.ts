import { expect, test } from '@playwright/test';

import { loginAsAdmin } from '../ui/helpers/session';

const summaryRows = [
  {
    orderId: '1001',
    enrollmentNo: 'E-1001',
    orderItemId: '2001',
    studentId: '3001',
    studentName: 'Summer Liu',
    school: 'PS 32',
    grade: '1',
    signupDate: '2026-04-30',
    periodStartDate: '2026-09-10',
    periodEndDate: '2027-06-26',
    className: 'G1',
    registerClass: 'G1',
    phone: '929-426-5311',
    phone2: '',
    pickupRequired: true,
    dropoffRequired: false,
    paymentMethod: 'Cash',
    paymentStatus: 'PAID',
    linePriceAmount: 3499,
    pickupFeeAmount: 300,
    extraFeeAmount: 0,
    discountAmount: 300,
    amountDue: 3499,
    paidAmount: 3499,
    balanceAmount: 0,
    overdue: false,
    expiringSoon: false,
    lowOneOnOneHours: false,
    alertTypes: [],
  },
  {
    orderId: '1002',
    enrollmentNo: 'E-1002',
    orderItemId: '2002',
    studentId: '3002',
    studentName: 'Hong Xuan Zheng',
    school: 'PS 21',
    grade: '1',
    signupDate: '2026-04-28',
    periodStartDate: '2026-09-10',
    periodEndDate: '2027-06-26',
    className: 'G1',
    registerClass: 'G1',
    phone: '646-867-5030',
    phone2: '',
    pickupRequired: false,
    dropoffRequired: false,
    paymentMethod: 'Cash',
    paymentStatus: 'PAID',
    linePriceAmount: 3499,
    pickupFeeAmount: 0,
    extraFeeAmount: 0,
    discountAmount: 350,
    amountDue: 3149,
    paidAmount: 3149,
    balanceAmount: 0,
    overdue: false,
    expiringSoon: false,
    lowOneOnOneHours: false,
    alertTypes: [],
  },
  {
    orderId: '1003',
    enrollmentNo: 'E-1003',
    orderItemId: '2003',
    studentId: '3003',
    studentName: 'Yifa Wang',
    school: 'PS 22',
    grade: '2',
    signupDate: '2026-04-30',
    periodStartDate: '2026-09-10',
    periodEndDate: '2027-06-26',
    className: 'G2',
    registerClass: 'G2',
    phone: '929-789-7535',
    phone2: '',
    pickupRequired: true,
    dropoffRequired: false,
    paymentMethod: 'Zelle',
    paymentStatus: 'PARTIAL',
    linePriceAmount: 3499,
    pickupFeeAmount: 500,
    extraFeeAmount: 0,
    discountAmount: 350,
    amountDue: 3649,
    paidAmount: 1649,
    balanceAmount: -2000,
    overdue: true,
    expiringSoon: false,
    lowOneOnOneHours: false,
    alertTypes: ['OVERDUE'],
  },
];

test('Data Summary 应只展示 Excel 式宽表并支持组合表头筛选', async ({ page }) => {
  await loginAsAdmin(page);
  await page.route('**/api/v1/data-summary**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: {
          totalRegistrationCount: summaryRows.length,
          totalIncomeAmount: 8297,
          totalOutstandingAmount: -2000,
          campusSummaries: [],
          courseTypeSummaries: [],
          classCourseSummaries: [],
          paymentStatusSummaries: [],
          rows: summaryRows,
        },
      }),
    });
  });

  await page.goto('/data-summary');

  const workbook = page.getByTestId('data-summary-workbook');
  await expect(workbook).toBeVisible();
  await expect(workbook.getByRole('columnheader')).toHaveCount(16);
  await expect(workbook.getByRole('columnheader').first()).toHaveText('Campus');
  await expect(workbook.locator('.workbook-data-row')).toHaveCount(3);
  await expect(page.getByRole('tab')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Record payment' })).toHaveCount(0);

  await page.getByRole('button', { name: 'Filter Student Name' }).click();
  await page.getByPlaceholder('Search Student Name').fill('summer');
  await expect(workbook.locator('.workbook-data-row')).toHaveCount(1);
  await expect(workbook.getByText('Summer Liu')).toBeVisible();

  await page.getByRole('button', { name: 'Filter School' }).click();
  await page.getByPlaceholder('Search School').fill('PS 32');
  await expect(workbook.locator('.workbook-data-row')).toHaveCount(1);
  await page.locator('.workbook-filter').filter({ hasText: 'Filter School' }).getByRole('button', { name: 'Clear all' }).click();
  await expect(workbook.locator('.workbook-data-row')).toHaveCount(3);

  await page.getByRole('button', { name: 'Filter Class' }).click();
  await page.locator('.workbook-filter').filter({ hasText: 'Filter Class' }).locator('.el-select__wrapper').click();
  await page.getByRole('option', { name: 'G2' }).click();
  await expect(workbook.locator('.workbook-data-row')).toHaveCount(1);
  await expect(workbook.getByText('Yifa Wang')).toBeVisible();

  await page.keyboard.press('Escape');
  await page.locator('.workbook-filter').filter({ hasText: 'Filter Class' }).getByRole('button', { name: 'Clear all' }).click();
  await page.getByRole('button', { name: 'Filter Balance' }).click();
  await page.locator('.workbook-filter').filter({ hasText: 'Filter Balance' }).getByPlaceholder('Maximum').fill('-1');
  await expect(workbook.locator('.workbook-data-row')).toHaveCount(1);
  await expect(workbook.getByText('-$2,000')).toBeVisible();
});
