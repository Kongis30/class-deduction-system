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
    for (const width of [320, 360, 390, 430]) {
      await page.setViewportSize({ width, height: 844 });
      const layout = await page.evaluate(() => ({
        viewport: document.documentElement.clientWidth,
        document: document.documentElement.scrollWidth,
        registration: document.querySelector('.registration-page')?.getBoundingClientRect().width ?? 0,
        nameColumns: getComputedStyle(document.querySelector('.student-name-fields')!).gridTemplateColumns.split(' ').length,
        fieldRight: Math.max(...[...document.querySelectorAll('.student-lines .el-input__wrapper')]
          .map(element => element.getBoundingClientRect().right)),
      }));
      expect(layout.document, `Student step should fit a ${width}px viewport: ${JSON.stringify(layout)}`)
        .toBeLessThanOrEqual(layout.viewport + 1);
      expect(layout.fieldRight, `Student fields should stay inside a ${width}px viewport: ${JSON.stringify(layout)}`)
        .toBeLessThanOrEqual(layout.viewport + 1);
      expect(layout.nameColumns, 'First and last name should share one row').toBe(2);
    }
    await page.setViewportSize({ width: 390, height: 844 });
    const registrationScrollRegion = page.locator('.app-shell__content--registration');
    await registrationScrollRegion.evaluate(element => { element.scrollTop = element.scrollHeight; });
    const signupDateVisibility = await page.evaluate(() => ({
      fieldBottom: document.querySelector('.student-lines .line-date')?.getBoundingClientRect().bottom ?? 0,
      actionsTop: document.querySelector('.mobile-registration-actions')?.getBoundingClientRect().top ?? 0,
    }));
    expect(signupDateVisibility.fieldBottom, 'The last student field should remain above the fixed action bar').toBeLessThan(signupDateVisibility.actionsTop);
    await registrationScrollRegion.evaluate(element => { element.scrollTop = 0; });

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
    await expect(page.locator('.registration-page .paper-form')).toHaveCSS('overflow-y', 'visible');
    const registrationContent = page.locator('.app-shell__content--registration');
    await expect(registrationContent).toHaveCSS('overflow-y', 'auto');
    const scrollTop = await registrationContent.evaluate(element => {
      element.scrollTop = element.scrollHeight;
      return element.scrollTop;
    });
    expect(scrollTop, 'The registration page content should scroll as one page').toBeGreaterThan(0);
  });

  test('mobile navigation mirrors sidebar links and scrolls horizontally', async ({ page }) => {
    const navigation = page.locator('.mobile-tab-bar');
    await expect(navigation.locator('a')).toHaveCount(7);
    await expect(page.getByRole('button', { name: 'Menu' })).toHaveCount(0);
    expect(await navigation.evaluate(element => element.scrollWidth)).toBeGreaterThan(
      await navigation.evaluate(element => element.clientWidth),
    );

    await navigation.getByRole('link', { name: 'Daily reg.' }).click();
    await expect(page).toHaveURL(/\/daily-registrations$/);
  });

  test('saved registration receipts scale the desktop canvas on mobile while registration stays mobile-first', async ({ page }, testInfo) => {
    await page.goto('/registration');
    const registrationPage = page.locator('.registration-page');
    await expect(registrationPage).not.toHaveClass(/registration-page--receipt-preview/);

    await page.goto('/registration?orderId=2104065853844115458');
    await expect(registrationPage).toHaveClass(/registration-page--receipt-preview/);
    await expect(page.locator('.registration-desk')).toBeHidden();
    await expect(page.locator('.private-lesson-switcher')).toBeHidden();
    await expect(page.locator('.mobile-registration-review')).toBeHidden();
    await expect(page.getByText('Enrollment order not found')).toHaveCount(0);
    const paperForm = page.locator('.paper-form');
    const previewStyles = await paperForm.evaluate(element => {
      const style = getComputedStyle(element);
      return {
        fontSize: style.fontSize,
        padding: style.padding,
        canvasWidth: Number.parseFloat(style.width),
        zoom: Number.parseFloat(style.zoom),
        visibleWidth: element.getBoundingClientRect().width,
        viewportWidth: element.parentElement?.clientWidth ?? 0,
      };
    });
    expect(previewStyles.fontSize).toBe('16px');
    expect(previewStyles.padding).toBe('30px 34px 24px');
    expect(previewStyles.canvasWidth).toBe(920);
    expect(previewStyles.zoom).toBeGreaterThan(0);
    expect(previewStyles.zoom).toBeLessThan(1);
    expect(previewStyles.visibleWidth).toBeLessThanOrEqual(previewStyles.viewportWidth);
    await page.screenshot({ path: testInfo.outputPath('saved-receipt-mobile.png'), fullPage: true });

    await page.emulateMedia({ media: 'print' });
    await expect(page.locator('.mobile-tab-bar')).toBeHidden();
    await expect(page.locator('.mobile-registration-actions')).toBeHidden();
    await expect(page.locator('.mobile-registration-review')).toBeHidden();
    const printStyles = await paperForm.evaluate(element => {
      const style = getComputedStyle(element);
      return { fontSize: style.fontSize, padding: style.padding };
    });
    expect(printStyles).toEqual({ fontSize: '11.5px', padding: '0px' });
  });

  test('course packages use readable rows and reachable actions on mobile', async ({ page }, testInfo) => {
    await page.goto('/enrollment/packages');
    await expect(page.locator('.package-group__toggle').first()).toHaveAttribute('aria-expanded', 'false');
    await expect(page.locator('.mobile-package-list')).toHaveCount(0);
    await page.locator('.package-group__toggle').first().click();
    await expect(page.locator('.mobile-package-row').first()).toBeVisible();
    await expect(page.locator('.package-group .product-table')).toHaveCount(0);
    await expect(page.locator('.mobile-package-row__actions').first().getByRole('button', { name: /More actions for/ })).toBeVisible();
    const listOverflow = await page.locator('.mobile-package-list').first().evaluate(element => element.scrollWidth > element.clientWidth);
    expect(listOverflow, 'Package information should fit without horizontal scrolling on a phone').toBe(false);
    await page.screenshot({ path: testInfo.outputPath('course-packages-mobile.png') });
  });

  test('enrollment group class page can scroll vertically on mobile', async ({ page }, testInfo) => {
    await page.goto('/enrollment/orders');
    const content = page.locator('.app-shell__content--enrollment');
    await expect(content).toHaveCSS('overflow-y', 'auto');

    const actionRow = await Promise.all([
      page.getByRole('button', { name: 'Register' }).boundingBox(),
      page.getByRole('button', { name: 'More enrollment actions' }).boundingBox(),
      page.getByRole('button', { name: 'Show search filters' }).boundingBox(),
    ]);
    expect(actionRow.every(Boolean)).toBe(true);
    expect(Math.max(...actionRow.map(box => box?.y ?? 0)) - Math.min(...actionRow.map(box => box?.y ?? 0))).toBeLessThan(4);

    const oneOnOneTable = content.locator('.product-table:not(.group-table)');
    const courseHeader = oneOnOneTable.locator('.el-table__header-wrapper th').first().locator('.cell');
    const headerAlignment = await courseHeader.evaluate(element => {
      const titleText = [...element.childNodes].find(node => node.nodeType === Node.TEXT_NODE && node.textContent?.trim());
      const titleRange = document.createRange();
      if (titleText) titleRange.selectNode(titleText);
      const title = titleText ? titleRange.getBoundingClientRect() : null;
      const sort = element.querySelector('.el-table__column-sort-button')?.getBoundingClientRect();
      return { titleCenter: title ? title.top + title.height / 2 : 0, sortCenter: sort ? sort.top + sort.height / 2 : 0 };
    });
    expect(Math.abs(headerAlignment.titleCenter - headerAlignment.sortCenter), 'Sort controls should stay on the same line as the column name').toBeLessThan(4);
    expect(await oneOnOneTable.evaluate(element => element.getBoundingClientRect().width)).toBeLessThanOrEqual(720);
    expect(await oneOnOneTable.evaluate(element => getComputedStyle(element).zoom)).toBe('0.9');
    await expect(oneOnOneTable.locator('.el-table__body-wrapper td .cell').first()).toHaveCSS('font-size', '11px');
    await expect(oneOnOneTable.locator('.el-table__body-wrapper td .cell').first()).toHaveCSS('text-align', 'center');
    const visibleColumnCount = await content.locator('.enrollment-table-scroll').first().evaluate(region => {
      const rightEdge = region.getBoundingClientRect().right;
      return [...region.querySelectorAll('.product-table:not(.group-table) .el-table__header-wrapper th')]
        .filter(column => column.getBoundingClientRect().right <= rightEdge + 1).length;
    });
    expect(visibleColumnCount, 'A phone should show several complete columns before horizontal scrolling').toBeGreaterThanOrEqual(4);
    await page.screenshot({ path: testInfo.outputPath('enrollment-orders-mobile-table.png') });

    const scrollTop = await content.evaluate(element => {
      element.scrollTop = element.scrollHeight;
      return element.scrollTop;
    });
    expect(scrollTop, 'Expanded group class content should scroll above the fixed bottom navigation').toBeGreaterThan(0);
  });

  test('schedule package loading reports failures and retries on mobile', async ({ page }) => {
    await page.goto('/schedule/recurring');
    const scheduledGroup = page.locator('.schedule-student-group__toggle').filter({ hasText: '22' }).first();
    await expect(scheduledGroup).toBeVisible();
    await scheduledGroup.click();
    await expect(scheduledGroup).toHaveAttribute('aria-expanded', 'true');
    const firstCourse = page.locator('.schedule-student-group__students .col-item:visible').first();
    await expect(firstCourse).toBeVisible();

    let packageRequestCount = 0;
    await page.route('**/v1/enrollment/packages*', async route => {
      packageRequestCount += 1;
      await route.fulfill({
        status: 400,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Test package request failure' }),
      });
    });

    await firstCourse.click();
    await expect(page.getByText(/Could not load course packages/)).toBeVisible();
    await expect.poll(() => packageRequestCount).toBe(1);
    await page.getByRole('button', { name: 'Retry' }).click();
    await expect(page.getByText(/Could not load course packages/)).toBeVisible();
    await expect.poll(() => packageRequestCount).toBe(2);
    await expect(page.getByText('Loading packages...')).toHaveCount(0);
  });

  test('schedule mobile package selection renders without runtime errors', async ({ page }) => {
    const pageErrors: string[] = [];
    page.on('pageerror', error => pageErrors.push(error.message));
    await page.goto('/schedule/recurring');
    const scheduledGroup = page.locator('.schedule-student-group__toggle').filter({ hasText: '22' }).first();
    await expect(scheduledGroup).toBeVisible();
    await scheduledGroup.click();
    const firstCourse = page.locator('.schedule-student-group__students .col-item:visible').first();
    await expect(firstCourse).toBeVisible();
    await firstCourse.click();
    const selectedPackageSummary = page.locator('.col-packages > .mobile-selection-summary');
    await expect(selectedPackageSummary).toBeVisible({ timeout: 20000 });
    await expect(selectedPackageSummary).toContainText('remaining');
    expect(pageErrors).toEqual([]);
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

  test('classroom management page scrolls above the fixed bottom navigation on mobile', async ({ page }) => {
    await page.goto('/classrooms');
    const content = page.locator('.app-shell__content');
    await expect(content).toHaveCSS('overflow-y', 'auto');
    const scrollTop = await content.evaluate(element => {
      element.scrollTop = element.scrollHeight;
      return element.scrollTop;
    });
    expect(scrollTop, 'The classroom list should be reachable by scrolling the page').toBeGreaterThan(0);

    const tableScroll = page.locator('.classroom-table-scroll');
    await expect(tableScroll).toHaveCSS('touch-action', 'pan-x pan-y');
    const horizontal = await tableScroll.evaluate(element => ({
      scrollWidth: element.scrollWidth,
      clientWidth: element.clientWidth,
      scrollLeft: (element.scrollLeft = 999, element.scrollLeft),
    }));
    expect(horizontal.scrollWidth, 'The classroom table should overflow its viewport').toBeGreaterThan(horizontal.clientWidth);
    expect(horizontal.scrollLeft, 'The classroom table should scroll horizontally').toBeGreaterThan(0);
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

  test('room calendar shows the full week and uses page scrolling on mobile', async ({ page }, testInfo) => {
    await page.goto('/calendar?tab=room');
    const calendarTabContainer = page.locator('.calendar-tabs .el-tabs__header');
    await expect(calendarTabContainer).toHaveCSS('border-top-width', '1px');
    await expect(calendarTabContainer).toHaveCSS('border-top-style', 'solid');
    await expect(calendarTabContainer).toHaveCSS('background-color', 'rgb(229, 237, 245)');
    await expect(page.locator('.classroom-mobile-select')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Manage rooms' })).toBeVisible();
    await expect(page.locator('.calendar-controls__label')).toBeVisible();
    await expect(page.locator('.calendar-controls__count')).toBeVisible();
    await expect(page.locator('.wts-header__cell')).toHaveCount(7);
    await expect(page.locator('.wts-grid-scroll')).toHaveCSS('max-height', 'none');
    await expect(page.locator('.wts-grid-scroll')).toHaveCSS('overflow-y', 'hidden');
    // 日历横向滚动只保留在网格内，纵向拖动应继续上抛给页面滚动（合并拖动，避免“卡住”）。
    await expect(page.locator('.wts-grid-scroll')).toHaveCSS('touch-action', 'pan-x pan-y');
    await expect(page.locator('.wts-grid-scroll')).toHaveCSS('overscroll-behavior-x', 'contain');
    const mobileGrid = await page.locator('.wts-grid-scroll').evaluate(element => ({
      clientWidth: element.clientWidth,
      scrollWidth: element.scrollWidth,
      dayWidth: element.querySelector('.wts-header__cell')?.getBoundingClientRect().width ?? 0,
    }));
    // 自动缩放后整周应完整可见，不再依赖横向滚动。
    expect(mobileGrid.scrollWidth, 'The full week should fit without horizontal scrolling').toBeLessThanOrEqual(mobileGrid.clientWidth + 2);
    expect(mobileGrid.dayWidth, 'Each day should stay readable after auto-fit').toBeGreaterThanOrEqual(40);

    const datePicker = page.locator('.calendar-date-picker');
    const datePickerSize = await datePicker.evaluate(element => {
      const { width, height } = element.getBoundingClientRect();
      return { width, height };
    });
    expect(datePickerSize.width).toBeLessThanOrEqual(44);
    expect(datePickerSize.height).toBeLessThanOrEqual(44);
    await datePicker.click();
    await expect(page.locator('.el-picker-panel:visible')).toBeVisible();
    await page.keyboard.press('Escape');

    // 手动缩放：放大后出现横向滚动，点击百分比可一键恢复整周适配。
    await expect(page.locator('.wts-mobile-zoom-controls')).toBeVisible();
    await expect(page.locator('.wts-zoom-btn')).toHaveCount(3);
    await page.getByRole('button', { name: 'Zoom in' }).click();
    await expect
      .poll(() => page.locator('.wts-grid-scroll').evaluate(el => el.scrollWidth > el.clientWidth))
      .toBe(true);
    await page.getByRole('button', { name: /Reset zoom/ }).click();
    await expect
      .poll(() => page.locator('.wts-grid-scroll').evaluate(el => el.scrollWidth <= el.clientWidth + 2))
      .toBe(true);

    const calendarPageScroll = page.locator('.app-shell__content--calendar');
    await expect(calendarPageScroll).toHaveCSS('overflow-y', 'auto');
    const pageScrollTop = await calendarPageScroll.evaluate(element => {
      element.scrollTop = element.scrollHeight;
      return element.scrollTop;
    });
    expect(pageScrollTop, 'The full-week calendar should be reachable by scrolling the page').toBeGreaterThan(0);
    await calendarPageScroll.evaluate(element => { element.scrollTop = 0; });

    const calendarWidths = await page.evaluate(() => ({
      viewport: document.documentElement.clientWidth,
      document: document.documentElement.scrollWidth,
    }));
    expect(calendarWidths.document, 'The mobile Calendar page should fit the phone width').toBeLessThanOrEqual(calendarWidths.viewport + 1);
    await page.screenshot({ path: testInfo.outputPath('calendar-room-week-mobile.png') });

    const firstDayOfWeek = await page.locator('.wts-header__date').first().textContent();
    await page.getByRole('button', { name: 'Next week' }).click();
    await expect(page.locator('.wts-header__date').first()).not.toHaveText(firstDayOfWeek ?? '');
  });

  test('teacher and student calendars use the compact week toolbar on mobile', async ({ page }, testInfo) => {
    for (const [tab, trigger, groupSelector, gradeSelector] of [
      ['teacher', 'Select a teacher', '.teacher-group__header', ''],
      ['student', 'Select a student', '.student-product-group__header', '.student-grade-group__header'],
    ] as const) {
      await page.goto(`/calendar?tab=${tab}`);
      await page.getByRole('button', { name: trigger }).click();
      const panel = page.locator('.calendar-mobile-roster__panel:visible');
      await panel.locator(groupSelector).first().click();
      if (gradeSelector) await panel.locator(gradeSelector).first().click();
      await panel.locator('.calendar-mobile-roster__item').first().click();

      const nav = page.locator('.calendar-controls__nav:visible').first();
      const week = nav.locator('.calendar-week-current');
      await expect(week.locator('.calendar-controls__count')).toBeVisible();
      const weekLabel = week.locator('.calendar-controls__label');
      await expect(weekLabel).toHaveText(/–/);
      expect(await weekLabel.evaluate(element => element.scrollWidth > element.clientWidth + 1), `${tab} week label should not truncate`).toBe(false);

      const picker = week.locator('.calendar-date-picker');
      const pickerSize = await picker.evaluate(element => {
        const { width, height } = element.getBoundingClientRect();
        return { width, height };
      });
      expect(pickerSize.width).toBeLessThanOrEqual(44);
      expect(pickerSize.height).toBeLessThanOrEqual(44);

      const [navBox, zoomBox] = await Promise.all([
        nav.boundingBox(),
        page.locator('.wts-mobile-zoom-bar:visible').first().boundingBox(),
      ]);
      expect(navBox).not.toBeNull();
      expect(zoomBox).not.toBeNull();
      expect(Math.abs(zoomBox!.width - navBox!.width), `${tab} zoom bar should align with the week toolbar`).toBeLessThanOrEqual(1);
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
        await page.evaluate(() => document.documentElement.clientWidth) + 1,
      );
      await page.screenshot({ path: testInfo.outputPath(`calendar-${tab}-toolbar-mobile.png`) });
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

  test('student add action shares the mobile search row', async ({ page }, testInfo) => {
    await page.goto('/admin/config/people?tab=students');
    const add = page.locator('.student-add-trigger');
    await expect(add).toBeVisible();
    await expect(page.locator('.people-header')).toBeHidden();

    const actions = add.locator('xpath=..');
    const search = actions.locator('.mobile-search-trigger');
    const deleted = actions.getByRole('button', { name: 'View deleted students' });
    const [addBox, searchBox, deletedBox] = await Promise.all([
      add.boundingBox(),
      search.boundingBox(),
      deleted.boundingBox(),
    ]);
    expect(addBox).not.toBeNull();
    expect(searchBox).not.toBeNull();
    expect(deletedBox).not.toBeNull();
    expect(Math.abs(addBox!.y - searchBox!.y), 'Add student and search should share one row').toBeLessThanOrEqual(1);
    expect(Math.abs(addBox!.y - deletedBox!.y), 'Add student and Deleted should share one row').toBeLessThanOrEqual(1);
    expect(addBox!.x).toBeGreaterThan(searchBox!.x);
    expect(addBox!.x + addBox!.width).toBeLessThanOrEqual(page.viewportSize()!.width + 1);

    await add.click();
    await expect(page.locator('.student-dialog:visible')).toBeVisible();
    await page.screenshot({ path: testInfo.outputPath('people-student-add-mobile.png') });
    await page.keyboard.press('Escape');
  });

  test('people tables keep the fixed action column reachable on a phone', async ({ page }, testInfo) => {
    for (const tab of ['students', 'teachers']) {
      await page.goto(`/admin/config/people?tab=${tab}`);
      await page.locator('.app-shell__content').waitFor();

      if (tab === 'students') {
        await page.locator('details.student-product > summary').first().click();
        await page.locator('details.student-project > summary').first().click();
        await page.locator('details.student-group > summary').first().click();
      }

      const region = page.locator('.people-table-zoom-scroll:visible').first();
      const table = page.locator('.product-table:visible').first();
      await expect(table).toBeVisible();
      const actionHeader = table.locator('.el-table__header-wrapper th').last();
      await expect(actionHeader).toHaveText('More');
      const [headerBox, regionBox] = await Promise.all([actionHeader.boundingBox(), region.boundingBox()]);
      expect(headerBox).not.toBeNull();
      expect(regionBox).not.toBeNull();
      expect(headerBox!.x, `${tab} action column should stay inside the phone viewport`).toBeGreaterThanOrEqual(regionBox!.x);
      expect(headerBox!.x + headerBox!.width, `${tab} action column should stay pinned to the right edge`).toBeLessThanOrEqual(regionBox!.x + regionBox!.width + 1);

      await table.locator('.mobile-table-actions button').first().click();
      await expect(page.locator('.el-dropdown-menu:visible .el-dropdown-menu__item').first()).toBeVisible();
      await page.keyboard.press('Escape');
      await page.screenshot({ path: testInfo.outputPath(`people-${tab}-mobile-actions.png`) });
    }
  });

  test('compact desktop header does not scroll the page or clip the sidebar', async ({ page }) => {
    await page.setViewportSize({ width: 972, height: 800 });
    await page.goto('/calendar?tab=room');
    await page.locator('.app-shell').waitFor();
    await page.waitForTimeout(300);

    const layout = await page.evaluate(() => {
      const viewport = document.documentElement.clientWidth;
      const sidebar = document.querySelector('.sidebar')?.getBoundingClientRect();
      const header = document.querySelector<HTMLElement>('.app-header')?.getBoundingClientRect();
      return {
        viewport,
        documentWidth: document.documentElement.scrollWidth,
        scrollX: window.scrollX,
        sidebarLeft: Math.round(sidebar?.left ?? -1),
        headerRight: Math.round(header?.right ?? 0),
        headerHeight: Math.round(header?.height ?? 0),
      };
    });
    expect(layout.documentWidth, `Page should not overflow horizontally: ${JSON.stringify(layout)}`).toBeLessThanOrEqual(layout.viewport + 1);
    expect(layout.scrollX).toBe(0);
    expect(layout.sidebarLeft).toBe(0);
    expect(layout.headerRight).toBeLessThanOrEqual(layout.viewport + 1);
    expect(layout.headerHeight).toBeLessThanOrEqual(120);
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
