import { expect, test } from '@playwright/test';

import { ADMIN_PASSWORD, ADMIN_USERNAME } from '../helpers/api';

const adminRoutes = [
  '/dashboard',
  '/registration',
  '/daily-registrations',
  '/calendar',
  '/one-on-one-deduction',
  '/my-lessons',
  '/my-payroll',
  '/classrooms',
  '/classrooms/occupancy',
  '/data-summary',
  '/student-credit',
  '/pickup-dropoff-list',
  '/admin/config/people',
  '/admin/config/course-prices',
  '/admin/config/payment-methods',
  '/admin/config/receipt',
  '/admin/config/campuses',
  '/admin/config/notifications',
  '/admin/guide',
  '/admin/config/teacher-pay-rates',
  '/base-data/classrooms',
  '/base-data/course-products',
  '/base-data/campuses',
  '/enrollment/orders',
  '/enrollment/packages',
  '/enrollment/receipts',
  '/schedule/recurring',
  '/schedule/adjustments',
  '/schedule/teacher',
  '/schedule/classrooms',
  '/schedule/student',
  '/system/notifications',
  '/system/brand',
  '/finance/pay-rates',
  '/finance/payments',
];

test.describe('mobile responsive workspace', () => {
  test.use({
    viewport: { width: 390, height: 844 },
    isMobile: true,
    hasTouch: true,
  });

  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByPlaceholder('Enter your username').fill(ADMIN_USERNAME);
    await page.getByPlaceholder('Enter password').fill(ADMIN_PASSWORD);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.locator('.app-shell')).toBeVisible();
  });

  test('registration is a five-step mobile task flow', async ({ page }, testInfo) => {
    await page.goto('/registration');
    await expect(page.locator('.mobile-tab-bar')).toBeVisible();
    await expect(page.locator('.mobile-registration-progress__copy strong')).toHaveText('Student');
    await page.screenshot({ path: testInfo.outputPath('registration-student-step.png') });

    const steps = ['Course', 'Fees', 'Payment', 'Review'];
    for (const title of steps) {
      await page.getByRole('button', { name: 'Continue' }).click();
      await expect(page.locator('.mobile-registration-progress__copy strong')).toHaveText(title);
      await expect(page.locator('.mobile-registration-actions')).toBeVisible();
      const widths = await page.evaluate(() => ({
        viewport: document.documentElement.clientWidth,
        document: document.documentElement.scrollWidth,
      }));
      expect(widths.document, `Registration ${title} step overflows the phone viewport`).toBeLessThanOrEqual(widths.viewport + 1);
    }

    await page.screenshot({ path: testInfo.outputPath('registration-review-step.png') });
    await expect(page.locator('.mobile-registration-review')).toBeVisible();
  });

  test('course packages use readable cards and reachable actions on mobile', async ({ page }, testInfo) => {
    await page.goto('/enrollment/packages');
    await expect(page.locator('.mobile-package-list').first()).toBeVisible();
    await expect(page.locator('.mobile-package-card').first()).toBeVisible();
    await expect(page.locator('.package-group .product-table')).toHaveCount(0);
    await expect(page.locator('.mobile-package-card__actions').first().getByRole('button', { name: 'Renew' })).toBeVisible();
    await page.screenshot({ path: testInfo.outputPath('course-packages-mobile.png') });
  });

  test('high-use inventory and receipt pages replace desktop tables with mobile cards', async ({ page }) => {
    const mobileViews = [
      ['/classrooms', '.mobile-classroom-list'],
      ['/base-data/classrooms', '.mobile-classroom-list'],
      ['/base-data/campuses', '.mobile-campus-list'],
      ['/base-data/course-products', '.mobile-product-list'],
      ['/enrollment/orders', '.mobile-enrollment-list'],
      ['/enrollment/receipts', '.mobile-receipt-list'],
    ] as const;

    for (const [route, cardList] of mobileViews) {
      await page.goto(route);
      await expect(page.locator(cardList).first(), `${route} should render its mobile card layout`).toBeVisible();
      await expect(page.locator('.app-shell__content .el-table:visible'), `${route} should not show the desktop table`).toHaveCount(0);
    }
  });

  test('mobile configuration and room calendar screens do not render desktop tables', async ({ page }, testInfo) => {
    const mobileRoutes = [
      '/admin/config/course-prices',
      '/admin/config/payment-methods',
      '/admin/config/notifications',
      '/classrooms/occupancy',
      '/schedule/classrooms',
    ];

    for (const route of mobileRoutes) {
      await page.goto(route);
      await expect(page.locator('.app-shell__content .el-table:visible'), `${route} should use mobile-sized content`).toHaveCount(0);
      if (route === '/admin/config/course-prices' || route === '/classrooms/occupancy') {
        const fileName = route === '/admin/config/course-prices' ? 'course-prices-mobile.png' : 'classroom-occupancy-mobile.png';
        await page.screenshot({ path: testInfo.outputPath(fileName) });
      }
    }
  });

  test('lesson confirmation uses a full-width student list before showing lesson details', async ({ page }) => {
    await page.goto('/schedule/adjustments');
    const workspace = page.locator('.adj-workspace').first();
    const sidebar = workspace.locator('.adj-sidebar');
    const content = workspace.locator('.adj-content');

    await expect(sidebar).toBeVisible();
    await expect(content).toBeHidden();
    const sidebarWidth = await sidebar.evaluate(element => element.getBoundingClientRect().width);
    expect(sidebarWidth).toBeGreaterThan(340);

    const search = sidebar.getByRole('button', { name: 'Search students' });
    await expect(search).toBeVisible();
    await expect(sidebar.getByPlaceholder('Search student...')).toBeHidden();
    await search.click();
    await expect(sidebar.getByPlaceholder('Search student...')).toBeVisible();
    await search.click();
    await expect(sidebar.getByPlaceholder('Search student...')).toBeHidden();

    await sidebar.locator('.adj-sidebar__item').first().click();
    await expect(sidebar).toBeHidden();
    await expect(content).toBeVisible();
    await expect(content.getByRole('button', { name: /Student list/ })).toBeVisible();
    await content.getByRole('button', { name: /Student list/ }).click();
    await expect(sidebar).toBeVisible();
    await expect(content).toBeHidden();
  });

  test('teacher finance pages use a full-width teacher list before showing details', async ({ page }) => {
    for (const route of ['/finance/payments', '/finance/pay-rates']) {
      await page.goto(route);
      const sidebar = page.locator(route === '/finance/payments' ? '.tp-sidebar' : '.pr-sidebar');
      const content = page.locator(route === '/finance/payments' ? '.tp-content' : '.pr-content');
      const item = sidebar.locator(route === '/finance/payments' ? '.tp-sidebar__item' : '.pr-sidebar__item').first();

      await expect(sidebar).toBeVisible();
      await expect(content).toBeHidden();
      expect(await sidebar.evaluate(element => element.getBoundingClientRect().width)).toBeGreaterThan(340);
      await item.click();
      await expect(sidebar).toBeHidden();
      await expect(content).toBeVisible();
      await expect(content.getByRole('button', { name: /Teacher list/ })).toBeVisible();
      await content.getByRole('button', { name: /Teacher list/ }).click();
      await expect(sidebar).toBeVisible();
      await expect(content).toBeHidden();
    }
  });

  test('all admin routes fit the phone viewport without page-level horizontal overflow', async ({ page }) => {
    const overflowingRoutes: string[] = [];
    const visibleTableRoutes: string[] = [];

    for (const route of adminRoutes) {
      await page.goto(route);
      await page.locator('.app-shell__content').waitFor();
      await page.waitForTimeout(80);
      const overflowDetails = await page.evaluate(() => {
        const viewport = document.documentElement.clientWidth;
        const documentWidth = document.documentElement.scrollWidth;
        const visibleTables = [...document.querySelectorAll('.el-table')]
          .filter((table) => {
            const rect = table.getBoundingClientRect();
            return rect.width > 0 && rect.height > 0;
          }).length;
        const elements = [...document.querySelectorAll('body *')]
          .map((element) => {
            const rect = element.getBoundingClientRect();
            return {
              tag: element.tagName.toLowerCase(),
              className: typeof element.className === 'string' ? element.className : '',
              text: element.textContent?.trim().slice(0, 60) ?? '',
              right: Math.round(rect.right),
              width: Math.round(rect.width),
            };
          })
          .filter((element) => element.right > viewport + 1 && element.width > 0)
          .sort((left, right) => right.right - left.right)
          .slice(0, 8);
        return { viewport, documentWidth, visibleTables, elements };
      });
      // People deliberately keeps its compact mobile data table because the selected user asked for Students and Teachers to match the Managers table.
      if (overflowDetails.visibleTables && route !== '/admin/config/people') {
        visibleTableRoutes.push(`${route} (${overflowDetails.visibleTables})`);
      }
      if (overflowDetails.documentWidth > overflowDetails.viewport + 1) {
        overflowingRoutes.push(`${route} (${JSON.stringify(overflowDetails)})`);
      }
    }

    expect(overflowingRoutes, `Page-level horizontal overflow: ${overflowingRoutes.join(', ')}`).toEqual([]);
    expect(visibleTableRoutes, `Desktop tables still visible on mobile routes: ${visibleTableRoutes.join(', ')}`).toEqual([]);
  });
});
