import { expect, test } from '@playwright/test';

import { loginAsAdmin } from '../ui/helpers/session';

const products = [
  { id: '8001', productName: 'After School G1', sortOrder: 1, status: 'ACTIVE', teachingMode: 'GROUP' },
  { id: '8002', productName: 'Saturday G2', sortOrder: 2, status: 'ACTIVE', teachingMode: 'GROUP' },
  { id: '8003', productName: 'Legacy Camp', sortOrder: 3, status: 'INACTIVE', teachingMode: 'GROUP' },
];

function summaryRow(overrides: Record<string, unknown>) {
  return {
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
    ...overrides,
  };
}

const baseRows = [
  summaryRow({
    orderId: '1001',
    orderItemId: '2001',
    studentId: '3001',
    studentName: 'Summer Liu',
    courseProductId: '8001',
    courseProductName: 'After School G1',
    pickupFeeAmount: 300,
    paidAmount: 3499,
    balanceAmount: 0,
  }),
  summaryRow({
    orderId: '1002',
    orderItemId: '2002',
    studentId: '3002',
    studentName: 'Hong Xuan Zheng',
    school: 'PS 21',
    courseProductId: '8001',
    courseProductName: 'After School G1',
    pickupRequired: false,
    pickupFeeAmount: 0,
    discountAmount: 350,
    amountDue: 3149,
    paidAmount: 3149,
    balanceAmount: 0,
  }),
  summaryRow({
    orderId: '1003',
    orderItemId: '2003',
    studentId: '3003',
    studentName: 'Yifa Wang',
    school: 'PS 22',
    grade: '2',
    className: 'G2',
    registerClass: 'G2',
    courseProductId: '8002',
    courseProductName: 'Saturday G2',
    paymentMethod: 'Zelle',
    paymentStatus: 'PARTIAL',
    pickupFeeAmount: 500,
    discountAmount: 350,
    amountDue: 3649,
    paidAmount: 1649,
    balanceAmount: -2000,
    overdue: true,
    alertTypes: ['OVERDUE'],
  }),
  summaryRow({
    orderId: '1004',
    orderItemId: '2004',
    studentId: '3004',
    studentName: 'Legacy Student',
    className: 'Old Sheet',
    registerClass: 'Old Sheet',
    paymentMethod: 'Check',
    paymentStatus: 'PARTIAL',
    paidAmount: 100,
    balanceAmount: -50,
    overdue: true,
    alertTypes: ['OVERDUE'],
  }),
];

function buildPagedRows(count: number) {
  return Array.from({ length: count }, (_, index) => summaryRow({
    orderId: String(5000 + index),
    enrollmentNo: `E-${5000 + index}`,
    orderItemId: String(6000 + index),
    studentId: String(7000 + index),
    studentName: `Student ${index}`,
    className: 'G1',
    registerClass: 'G1',
    courseProductId: '8001',
    courseProductName: 'After School G1',
    paidAmount: 100 + index,
    balanceAmount: -index,
    overdue: index > 0,
    alertTypes: index > 0 ? ['OVERDUE'] : [],
  }));
}

async function mockProductsWith(page: import('@playwright/test').Page, productRows = products) {
  await page.route('**/api/v1/base/course-products/all', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, code: 'OK', message: 'Success', data: productRows, timestamp: '2026-08-31T00:00:00-04:00' }),
    });
  });
}

async function mockProducts(page: import('@playwright/test').Page) {
  await mockProductsWith(page);
}

async function mockSummary(page: import('@playwright/test').Page, rows: unknown[]) {
  await page.route('**/api/v1/data-summary**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: {
          totalRegistrationCount: rows.length,
          totalIncomeAmount: 8297,
          totalOutstandingAmount: -2000,
          campusSummaries: [],
          courseTypeSummaries: [],
          classCourseSummaries: [],
          paymentStatusSummaries: [],
          rows,
        },
      }),
    });
  });
}

test('Data Summary 应在表格上方显示 Product Tab 并按产品过滤数据', async ({ page }) => {
  await loginAsAdmin(page);
  await mockProducts(page);
  await mockSummary(page, baseRows);

  await page.goto('/data-summary');

  const workbook = page.getByTestId('data-summary-workbook');
  const tabBar = page.getByTestId('product-tab-bar');
  await expect(workbook).toBeVisible();
  await expect(tabBar).toBeVisible();

  // Tab 栏固定在表格上方，切换产品时不需要先滚到数据底部。
  const workbookBox = await workbook.boundingBox();
  const tabBarBox = await tabBar.boundingBox();
  expect(tabBarBox!.y).toBeLessThan(workbookBox!.y);

  await expect(page.locator('.product-tab')).toHaveCount(4);
  await expect(page.getByTestId('product-tab-8001')).toHaveText('After School G1');
  await expect(page.getByTestId('product-tab-__UNASSIGNED__')).toHaveText('Unassigned');

  // 默认选中第一个产品，只显示该产品的记录。
  await expect(page.locator('.workbook-data-row')).toHaveCount(2);
  await expect(workbook.getByText('Summer Liu')).toBeVisible();
  await expect(workbook.getByText('Yifa Wang')).toHaveCount(0);
  await expect(page.locator('.workbook-toolbar__stat').first()).toContainText('2');

  // 切换产品 Tab。
  await page.getByTestId('product-tab-8002').click();
  await expect(page.locator('.workbook-data-row')).toHaveCount(1);
  await expect(workbook.getByText('Yifa Wang')).toBeVisible();

  // 空产品保留 Tab 并显示明确的空状态。
  await page.getByTestId('product-tab-8003').click();
  await expect(page.locator('.workbook-data-row')).toHaveCount(0);
  await expect(workbook.getByText('No matching records for this course.')).toBeVisible();

  // 未关联产品的历史行进入 Unassigned 页签，不会被错误归入其他产品。
  await page.getByTestId('product-tab-__UNASSIGNED__').click();
  await expect(page.locator('.workbook-data-row')).toHaveCount(1);
  await expect(workbook.getByText('Legacy Student')).toBeVisible();
  // Unassigned 不是真实产品，不可拖拽。
  await expect(page.getByTestId('product-tab-__UNASSIGNED__')).toHaveAttribute('draggable', 'false');

  // 余额高亮规则在产品页签内保持不变。
  await page.getByTestId('product-tab-8002').click();
  await expect(workbook.getByText('-$2,000')).toBeVisible();
});

test('Data Summary 移动端应显示表格并允许在固定页签下滚动长列表', async ({ page }) => {
  const admin = { userId: '1', username: 'admin', displayName: 'Admin', userType: 'ADMIN', roleCodes: ['ADMIN'] };
  await page.addInitScript((user) => {
    localStorage.setItem('jbt_auth_token', 'test-token');
    localStorage.setItem('jbt_auth_user', JSON.stringify(user));
  }, admin);
  await page.route('**/api/v1/auth/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, code: 'OK', data: admin }),
    });
  });
  await mockProducts(page, products.slice(0, 1));
  const rows = buildPagedRows(131).map((row, index) => ({
    ...row,
    courseProductId: '8001',
    courseProductName: 'After School G1',
    studentName: `Student ${index + 1}`,
  }));
  await mockSummary(page, rows);
  await page.setViewportSize({ width: 390, height: 844 });

  await page.goto('/data-summary');

  const workbook = page.getByTestId('data-summary-workbook');
  const tabBar = page.getByTestId('product-tab-bar');
  const contentScroller = page.locator('.workbook-content-scroll');
  await expect(workbook).toBeVisible();
  await expect(workbook.locator('.workbook-data-row').first()).toBeVisible();
  await expect(contentScroller).toHaveCSS('overflow-y', 'auto');

  const before = await contentScroller.evaluate((element) => ({
    scrollTop: element.scrollTop,
    scrollHeight: element.scrollHeight,
    clientHeight: element.clientHeight,
  }));
  expect(before.scrollHeight).toBeGreaterThan(before.clientHeight);

  await contentScroller.evaluate((element) => { element.scrollTop = element.scrollHeight; });
  await expect.poll(() => contentScroller.evaluate((element) => element.scrollTop)).toBeGreaterThan(0);
  await expect(tabBar).toBeVisible();
  const tabBox = await tabBar.boundingBox();
  const scrollBox = await contentScroller.boundingBox();
  expect(tabBox!.y).toBeLessThan(scrollBox!.y);
});

test('打印班级名单前必须选择一个班级，且只打印所选班级', async ({ page }) => {
  await page.addInitScript(() => {
    window.print = () => { document.body.dataset.printCalled = 'true'; };
  });
  await loginAsAdmin(page);
  await mockProducts(page);
  await mockSummary(page, baseRows);

  await page.goto('/data-summary');
  await page.getByRole('button', { name: 'Print class roster' }).click();

  const printButton = page.getByRole('button', { name: 'Print', exact: true });
  await expect(printButton).toBeDisabled();
  await page.getByPlaceholder('Select a class').click();
  await page.locator('.el-select-dropdown__item').first().click();
  await expect(page.getByText('2 students will be printed')).toBeVisible();
  await expect(printButton).toBeEnabled();
  await printButton.click();

  await expect.poll(() => page.locator('body').getAttribute('data-print-called')).toBe('true');
  await expect(page.locator('.roster-print__page')).toHaveCount(1);
  await expect(page.locator('.roster-print__page')).toContainText('Summer Liu');
  await expect(page.locator('.roster-print__page')).toContainText('Hong Xuan Zheng');
});

test('切换 Product Tab 应保留每个产品的筛选排序与分页状态', async ({ page }) => {
  await loginAsAdmin(page);
  await mockProducts(page);
  await mockSummary(page, buildPagedRows(60));

  await page.goto('/data-summary');

  const workbook = page.getByTestId('data-summary-workbook');

  // 默认每页 50 条，60 条记录分两页。
  await expect(page.locator('.workbook-data-row')).toHaveCount(50);
  await expect(page.locator('.el-pagination__total')).toContainText('60');

  // 在产品 8001 上设置筛选与排序：Student Name = Student 5（命中 11 条），Balance 降序。
  await page.getByRole('button', { name: 'Filter Student Name' }).click();
  await page.getByPlaceholder('Search Student Name').fill('Student 5');
  await expect(page.locator('.workbook-data-row')).toHaveCount(11);

  await page.getByRole('button', { name: 'Filter Balance' }).click();
  await page.locator('.workbook-filter').filter({ hasText: 'Filter Balance' }).getByRole('button', { name: 'Desc' }).click();
  await page.keyboard.press('Escape');
  // Balance 降序后，欠款最少的 Student 5 排第一。
  await expect(workbook.locator('.workbook-data-row').first()).toContainText('Student 5');

  // 切到产品 8002（无记录），再切回 8001：筛选与排序状态恢复。
  await page.getByTestId('product-tab-8002').click();
  await expect(page.locator('.workbook-data-row')).toHaveCount(0);

  await page.getByTestId('product-tab-8001').click();
  await expect(page.locator('.workbook-data-row')).toHaveCount(11);
  await expect(workbook.locator('.workbook-data-row').first()).toContainText('Student 5');
  await expect(page.getByRole('button', { name: 'Filter Student Name' })).toBeVisible();
  // 当前表头仍处于筛选激活状态。
  await expect(page.locator('.workbook-header--active').first()).toBeVisible();

  // 清空筛选后翻到第 2 页，切换产品再返回，页码应恢复。
  await page.getByRole('button', { name: 'Filter Student Name' }).click();
  await page.locator('.workbook-filter').filter({ hasText: 'Filter Student Name' }).getByRole('button', { name: 'Clear all' }).click();
  await page.keyboard.press('Escape');
  await expect(page.locator('.workbook-data-row')).toHaveCount(50);

  await page.locator('.el-pagination .btn-next').click();
  await expect(page.locator('.workbook-data-row')).toHaveCount(10);

  await page.getByTestId('product-tab-8003').click();
  await page.getByTestId('product-tab-8001').click();
  await expect(page.locator('.workbook-data-row')).toHaveCount(10);
  await expect(page.locator('.el-pagination')).toContainText('60');
});

test('周六班 Data Summary 应把全年学生同时显示在 Fall 和 Spring 班级中，但统计只计算一次', async ({ page }) => {
  await loginAsAdmin(page);
  await mockProductsWith(page, [{
    id: '8100',
    productName: '2026-2027 Saturday Course',
    courseType: 'SATURDAY',
    sortOrder: 1,
    status: 'ACTIVE',
    teachingMode: 'GROUP',
  }]);

  const saturdayRows = [
    summaryRow({
      orderId: '8101',
      orderItemId: '8201',
      studentId: '8301',
      studentName: 'Annual Student',
      studentClass: 'G5',
      courseProductId: '8100',
      courseProductName: '2026-2027 Saturday Course',
      className: '2026-2027 Saturday Full Year G5',
      periodLabel: 'Full year',
      paidAmount: 1200,
      balanceAmount: 0,
    }),
    summaryRow({
      orderId: '8102',
      orderItemId: '8202',
      studentId: '8302',
      studentName: 'Fall Student',
      studentClass: 'G5',
      courseProductId: '8100',
      courseProductName: '2026-2027 Saturday Course',
      className: '2026-2027 Saturday Fall G5',
      periodLabel: 'Fall',
      paidAmount: 600,
      balanceAmount: 0,
    }),
    summaryRow({
      orderId: '8103',
      orderItemId: '8203',
      studentId: '8303',
      studentName: 'Spring Student',
      studentClass: 'G5',
      courseProductId: '8100',
      courseProductName: '2026-2027 Saturday Course',
      className: '2026-2027 Saturday Spring G5',
      periodLabel: 'Spring',
      paidAmount: 600,
      balanceAmount: 0,
    }),
  ];
  await mockSummary(page, saturdayRows);

  await page.goto('/data-summary');

  await expect(page.locator('.workbook-data-row')).toHaveCount(4);
  await expect(page.locator('tbody .workbook-column--studentClass')).toHaveText(['G5 · Fall', 'G5 · Spring']);
  await expect(page.getByText('Annual Student')).toHaveCount(2);
  await expect(page.locator('.workbook-toolbar__stat').first()).toContainText('3');
});

test('拖拽 Product Tab 应即时调整顺序并保存系统级共享配置', async ({ page }) => {
  await loginAsAdmin(page);
  await mockProducts(page);
  await mockSummary(page, baseRows);

  const orderRequests: string[][] = [];
  await page.route('**/api/v1/base/course-products/tab-order', async (route) => {
    orderRequests.push(route.request().postDataJSON().productIds);
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, code: 'OK', message: 'Success', data: null, timestamp: '2026-08-31T00:00:00-04:00' }),
    });
  });

  await page.goto('/data-summary');

  await expect(page.getByTestId('product-tab-bar')).toBeVisible();
  const tabA = page.getByTestId('product-tab-8001');
  const tabB = page.getByTestId('product-tab-8002');
  await expect(tabA).toHaveAttribute('draggable', 'true');

  // 拖拽 8002 到 8001 之前：页面不刷新，顺序立即更新。
  await tabB.dragTo(tabA);
  await expect(page.locator('.product-tab').first()).toHaveText('Saturday G2');
  await expect(page.getByTestId('product-tab-8001')).toHaveText('After School G1');

  // 防抖合并后保存最终顺序（不含 Unassigned 伪页签）。
  await expect.poll(() => orderRequests.length, { timeout: 5000 }).toBe(1);
  expect(orderRequests[0]).toEqual(['8002', '8001', '8003']);
  await expect(page.locator('.el-message--success')).toBeVisible();
});

test('Tab 顺序保存失败应提示错误并回滚到最后一次成功顺序', async ({ page }) => {
  await loginAsAdmin(page);
  await mockProducts(page);
  await mockSummary(page, baseRows);

  await page.route('**/api/v1/base/course-products/tab-order', async (route) => {
    await route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({ success: false, code: 'INTERNAL_ERROR', message: 'Failed to save product tab order.', data: null, timestamp: '2026-08-31T00:00:00-04:00' }),
    });
  });

  await page.goto('/data-summary');

  await expect(page.getByTestId('product-tab-bar')).toBeVisible();

  // 拖拽 8003 到 8001 之前：本地立即生效。
  await page.getByTestId('product-tab-8003').dragTo(page.getByTestId('product-tab-8001'));
  await expect(page.locator('.product-tab').first()).toHaveText('Legacy Camp');

  // 保存失败后回滚到加载时的顺序。
  await expect(page.locator('.el-message--error')).toBeVisible({ timeout: 5000 });
  await expect(page.locator('.product-tab').first()).toHaveText('After School G1');
  await expect(page.locator('.product-tab').nth(1)).toHaveText('Saturday G2');
  await expect(page.locator('.product-tab').nth(2)).toHaveText('Legacy Camp');
});
