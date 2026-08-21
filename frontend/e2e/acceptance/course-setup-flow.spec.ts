import { expect, test, type Page } from '@playwright/test';

import {
  apiDelete,
  apiGet,
  apiLogin,
  apiPost,
  type PageResult,
} from '../helpers/api';
import { loginAsAdmin } from '../ui/helpers/session';

interface ApiRecord {
  id: string;
  productName?: string;
  courseProductId?: string;
  classId?: string;
  [key: string]: unknown;
}

test.describe('Acceptance - Course setup flow', () => {
  test('admin can add a group registration option with schedule slot details from the UI', async ({ page, request }) => {
    const token = await apiLogin(request);
    const suffix = Date.now().toString(36);
    const productName = `E2E Course Setup ${suffix}`;
    let productId: string | undefined;
    let optionId: string | undefined;
    let classId: string | undefined;

    try {
      productId = await apiPost<string>(request, token, '/v1/base/course-products', {
        productCode: '',
        productName,
        teachingMode: 'GROUP',
        sortOrder: 91,
        status: 'ACTIVE',
        notes: 'E2E course setup flow',
      });

      await loginAsAdmin(page);
      await page.goto(`/admin/config/course-prices?courseProductId=${productId}&from=course-products`);

      await expect(page.getByRole('heading', { name: 'Set Grade Classes & Registration Options' })).toBeVisible();
      await page.getByRole('button', { name: productName }).click();
      await expect(page.getByText(productName).first()).toBeVisible();
      await page.getByRole('button', { name: /Add grade|Add registration option/ }).click();

      const dialog = page.locator('.registration-option-dialog');
      await expect(dialog).toBeVisible();
      await expect(dialog).toContainText('Choose grade');
      await expect(dialog).toContainText('Set registration option');
      await expect(dialog).toContainText('Set grade class schedule');
      await expect(dialog).toContainText('Notes');
      await expectNoHorizontalOverflow(dialog);

      await page.getByRole('button', { name: 'Save registration option' }).click();
      await expect(page.getByText('Grade or level is required')).toBeVisible();

      await setElementPlusSelect(dialog.getByLabel('Grade'), 'E2E-G3');
      await fillTextboxByLabel(dialog, 'Registration option name', 'Fall 13 sessions');
      await setElementPlusSelect(dialog.getByLabel('Count by'), 'By session');
      await setInputNumberByLabel(dialog, 'Number of sessions', '13');
      await setInputNumberByLabel(dialog, 'Price', '1199');
      await setInputNumberByLabel(dialog, 'Half-day price (optional)', '699');
      await fillTextboxByLabel(dialog, 'Class starts', '2026-09-12');
      await fillTextboxByLabel(dialog, 'Class ends', '2026-11-01');

      await selectWeeklySlot(page, 6, '09:00', '12:00');
      await expect(dialog.getByText('Selected class slots (1):')).toBeVisible();
      await setElementPlusSelect(dialog.locator('.slot-teacher-row').first().locator('.slot-field').filter({ hasText: 'Applies to' }), 'Morning only');
      await fillTextboxByLabel(dialog.locator('.slot-teacher-row').first(), 'Taught subject', 'Global I');
      await expect(dialog.locator('.slot-teacher-row').first().getByText('Pay / hr')).toHaveCount(0);

      await page.getByRole('button', { name: 'Save registration option' }).click();
      await expect(page.getByText('Registration option created.')).toBeVisible();
      await expect(dialog).toBeHidden();
      await expect(page.getByText('E2E-G3')).toBeVisible();
      await expect(page.getByText('Fall 13 sessions')).toBeVisible();
      await expect(page.getByText('$1,199.00')).toBeVisible();
      await expect(page.getByText('$699.00')).toBeVisible();

      const optionsPage = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/base/course-price-options', {
        courseProductId: productId,
        pageNum: 1,
        pageSize: 20,
      });
      const createdOption = optionsPage.records.find(item => String(item.periodLabel) === 'Fall 13 sessions');
      expect(createdOption, 'created option should be queryable from API').toBeTruthy();
      optionId = createdOption?.id;
      classId = createdOption?.classId;
    } finally {
      if (optionId) {
        await apiDelete(request, token, `/v1/base/course-price-options/${optionId}`).catch(() => undefined);
      }
      if (classId) {
        await apiDelete(request, token, `/v1/classes/${classId}`).catch(() => undefined);
      } else if (productId) {
        const classes = await apiGet<ApiRecord[]>(request, token, '/v1/classes').catch(() => []);
        const group = classes.find(item => String(item.productId) === String(productId));
        if (group?.id) {
          await apiDelete(request, token, `/v1/classes/${group.id}`).catch(() => undefined);
        }
      }
      if (productId) {
        await apiDelete(request, token, `/v1/base/course-products/${productId}`).catch(() => undefined);
      }
    }
  });
});

async function expectNoHorizontalOverflow(locator: ReturnType<Page['locator']>): Promise<void> {
  await expect
    .poll(async () => locator.evaluate((node) => {
      const element = node as HTMLElement;
      return {
        dialogOverflow: element.scrollWidth - element.clientWidth,
        bodyOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      };
    }))
    .toEqual({ dialogOverflow: 0, bodyOverflow: 0 });
}

async function setElementPlusSelect(field: ReturnType<Page['locator']>, optionName: string): Promise<void> {
  await field.locator('.el-select').click();
  await field.page().getByRole('option', { name: optionName, exact: true }).last().click();
}

async function fillTextboxByLabel(scope: ReturnType<Page['locator']>, label: string, value: string): Promise<void> {
  const field = scope.locator('.el-form-item, .slot-field').filter({ hasText: label }).first();
  const textbox = field.getByRole('textbox').first();
  await textbox.fill(value);
  await textbox.blur();
}

async function setInputNumberByLabel(scope: ReturnType<Page['locator']>, label: string, value: string): Promise<void> {
  const field = scope.locator('.el-form-item, .slot-field').filter({ hasText: label }).first();
  const input = field.locator('.el-input-number input, input').first();
  await input.fill(value);
  await input.blur();
}

async function selectWeeklySlot(page: Page, weekday: number, startTime: string, endTime: string): Promise<void> {
  const start = weeklyCell(page, weekday, startTime);
  const end = weeklyCell(page, weekday, endTime);
  const startBox = await start.boundingBox();
  const endBox = await end.boundingBox();
  expect(startBox, `start cell ${weekday} ${startTime} should exist`).toBeTruthy();
  expect(endBox, `end cell ${weekday} ${endTime} should exist`).toBeTruthy();
  await page.mouse.move(startBox!.x + startBox!.width / 2, startBox!.y + startBox!.height / 2);
  await page.mouse.down();
  await page.mouse.move(endBox!.x + endBox!.width / 2, endBox!.y + endBox!.height / 2, { steps: 8 });
  await page.mouse.up();
}

function weeklyCell(page: Page, weekday: number, time: string) {
  const [hourText, minuteText] = time.split(':');
  const rowIndex = (Number(hourText) - 8) * 2 + (minuteText === '30' ? 1 : 0);
  return page.locator('.weekly-time-selector .wts-row').nth(rowIndex).locator('.wts-cell').nth(weekday - 1);
}
