import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

import { apiGet, apiLogin, type PageResult } from '../../helpers/api';
import { cleanupE2EData, createDbConnection } from '../../helpers/db';
import {
  confirmMessageBox,
  expectNoActiveTableRow,
  fillInputByLabel,
  fillNumberByLabel,
  panelByTitle,
  selectByLabel,
  tableRowByText,
  visibleDialog,
} from '../helpers/element-plus';
import { loginAsAdmin } from '../helpers/session';
import { deleteCampusesByMarker } from './base-data-api';

const MARKER = 'E2EUI_BASE';

type ApiRecord = Record<string, any>;

test.describe.serial('Base Data - Campuses and Classrooms E2E UI', () => {
  test.beforeAll(async ({ request }) => {
    await cleanupBaseData(request);
  });

  test.afterAll(async ({ request }) => {
    await cleanupBaseData(request);
  });

  test('校区和教室页面覆盖新增、编辑、筛选、删除和数据一致性', async ({ page, request }) => {
    const token = await apiLogin(request);
    const suffix = Date.now().toString(36);
    const campusName = `${MARKER} Campus ${suffix}`;
    const editedCampusName = `${campusName} Edited`;
    const roomName = `${MARKER} Room ${suffix}`;
    const editedRoomName = `${roomName} Edited`;

    await loginAsAdmin(page);

    await createCampusFromUi(page, campusName);
    await expectCampusVisible(page, campusName, 'Initial campus note');
    await expectCampusInApi(request, token, campusName, 'ACTIVE');

    await createClassroomFromUi(page, roomName, campusName);
    await expectClassroomVisible(page, roomName, campusName, '18', 'Active');
    await expectClassroomInApi(request, token, roomName, campusName, 18, 'ACTIVE');

    await filterClassroomByCampusAndStatus(page, roomName, campusName, 'Active');

    await editClassroomFromUi(page, roomName, editedRoomName);
    await expectClassroomVisible(page, editedRoomName, campusName, '24', 'Inactive');
    await expectClassroomInApi(request, token, editedRoomName, campusName, 24, 'INACTIVE');

    await deleteClassroomFromUi(page, editedRoomName);
    await expectClassroomDeleted(page, request, token, editedRoomName);

    await editCampusFromUi(page, campusName, editedCampusName);
    await expectCampusVisible(page, editedCampusName, 'Edited campus note');
    await expectCampusInApi(request, token, editedCampusName, 'INACTIVE');

    await deleteCampusFromUi(page, editedCampusName);
    await expectCampusDeleted(page, request, token, editedCampusName);
  });
});

async function cleanupBaseData(request: APIRequestContext): Promise<void> {
  const db = await createDbConnection();
  try {
    await cleanupE2EData(db, MARKER);
  } finally {
    await db.end();
  }
  const token = await apiLogin(request);
  await deleteCampusesByMarker(request, token, MARKER);
}

async function createCampusFromUi(page: Page, campusName: string): Promise<void> {
  await page.goto('/base-data/campuses');
  await page.getByRole('button', { name: 'Add campus' }).click();
  const dialog = visibleDialog(page, 'New campus');
  await fillInputByLabel(dialog, 'Campus name', campusName);
  await fillNumberByLabel(dialog, 'Display order', '77');
  await selectByLabel(page, dialog, 'Status', 'Active');
  await fillInputByLabel(dialog, 'Notes', 'Initial campus note');

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/campuses') && res.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Save' }).click();
  expect((await response).ok(), '校区新增接口应成功').toBeTruthy();
}

async function editCampusFromUi(page: Page, campusName: string, editedCampusName: string): Promise<void> {
  await page.goto('/base-data/campuses');
  await tableRowByText(page, campusName).getByRole('button', { name: 'Edit' }).click();
  const dialog = visibleDialog(page, 'Edit campus');
  await fillInputByLabel(dialog, 'Campus name', editedCampusName);
  await selectByLabel(page, dialog, 'Status', 'Inactive');
  await fillInputByLabel(dialog, 'Notes', 'Edited campus note');

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/campuses/') && res.request().method() === 'PUT',
  );
  await dialog.getByRole('button', { name: 'Save' }).click();
  expect((await response).ok(), '校区编辑接口应成功').toBeTruthy();
}

async function deleteCampusFromUi(page: Page, campusName: string): Promise<void> {
  await page.goto('/base-data/campuses');
  await tableRowByText(page, campusName).getByRole('button', { name: 'Delete' }).click();
  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/campuses/') && res.request().method() === 'DELETE',
  );
  await confirmMessageBox(page);
  expect((await response).ok(), '校区删除接口应成功').toBeTruthy();
}

async function createClassroomFromUi(page: Page, roomName: string, campusName: string): Promise<void> {
  await page.goto('/base-data/classrooms');
  await page.getByRole('button', { name: 'Add classroom' }).click();
  const dialog = visibleDialog(page, 'New classroom');
  await fillInputByLabel(dialog, 'Room name', roomName);
  await selectByLabel(page, dialog, 'Campus', campusName);
  await fillNumberByLabel(dialog, 'Capacity', '18');
  await selectByLabel(page, dialog, 'Status', 'Active');
  await fillInputByLabel(dialog, 'Notes', 'Initial classroom note');

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/classrooms') && res.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Save' }).click();
  expect((await response).ok(), '教室新增接口应成功').toBeTruthy();
}

async function editClassroomFromUi(page: Page, roomName: string, editedRoomName: string): Promise<void> {
  await page.goto('/base-data/classrooms');
  await page.getByPlaceholder('Search by name').fill(roomName);
  await tableRowByText(page, roomName).getByRole('button', { name: 'Edit' }).click();
  const dialog = visibleDialog(page, 'Edit classroom');
  await fillInputByLabel(dialog, 'Room name', editedRoomName);
  await fillNumberByLabel(dialog, 'Capacity', '24');
  await selectByLabel(page, dialog, 'Status', 'Inactive');
  await fillInputByLabel(dialog, 'Notes', 'Edited classroom note');

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/classrooms/') && res.request().method() === 'PUT',
  );
  await dialog.getByRole('button', { name: 'Save' }).click();
  expect((await response).ok(), '教室编辑接口应成功').toBeTruthy();
}

async function deleteClassroomFromUi(page: Page, roomName: string): Promise<void> {
  await page.goto('/base-data/classrooms');
  await page.getByPlaceholder('Search by name').fill(roomName);
  await tableRowByText(page, roomName).getByRole('button', { name: 'Delete' }).click();
  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/classrooms/') && res.request().method() === 'DELETE',
  );
  await confirmMessageBox(page);
  expect((await response).ok(), '教室删除接口应成功').toBeTruthy();
}

async function filterClassroomByCampusAndStatus(
  page: Page,
  roomName: string,
  campusName: string,
  statusLabel: string,
): Promise<void> {
  await page.goto('/base-data/classrooms');
  await page.getByPlaceholder('Search by name').fill(roomName);
  await selectByLabel(page, page, 'Campus', campusName);
  await selectByLabel(page, page, 'Status', statusLabel);
  const row = tableRowByText(page, roomName);
  await expect(row).toContainText(campusName);
  await expect(row).toContainText(statusLabel);
}

async function expectCampusVisible(page: Page, campusName: string, note: string): Promise<void> {
  await page.goto('/base-data/campuses');
  const row = tableRowByText(page, campusName);
  await expect(row).toContainText(campusName);
  await expect(row).toContainText(note);
}

async function expectClassroomVisible(
  page: Page,
  roomName: string,
  campusName: string,
  capacity: string,
  status: string,
): Promise<void> {
  await page.goto('/base-data/classrooms');
  await page.getByPlaceholder('Search by name').fill(roomName);
  const row = tableRowByText(panelByTitle(page, 'Classrooms'), roomName);
  await expect(row).toContainText(campusName);
  await expect(row).toContainText(capacity);
  await expect(row).toContainText(status);
}

async function expectCampusInApi(
  request: APIRequestContext,
  token: string,
  campusName: string,
  status: string,
): Promise<void> {
  const campuses = await apiGet<ApiRecord[]>(request, token, '/v1/base/campuses');
  expect(campuses).toContainEqual(expect.objectContaining({ campusName, status }));
}

async function expectClassroomInApi(
  request: APIRequestContext,
  token: string,
  roomName: string,
  campusName: string,
  capacity: number,
  status: string,
): Promise<void> {
  const classrooms = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/base/classrooms', {
    keyword: roomName,
    pageNum: 1,
    pageSize: 20,
  });
  expect(classrooms.records).toContainEqual(expect.objectContaining({
    roomName,
    campus: campusName,
    capacity,
    status,
  }));
}

async function expectClassroomDeleted(
  page: Page,
  request: APIRequestContext,
  token: string,
  roomName: string,
): Promise<void> {
  await page.goto('/base-data/classrooms');
  await page.getByPlaceholder('Search by name').fill(roomName);
  await expectNoActiveTableRow(page, roomName);

  const classrooms = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/base/classrooms', {
    keyword: roomName,
    pageNum: 1,
    pageSize: 20,
  });
  expect(classrooms.records).toHaveLength(0);
}

async function expectCampusDeleted(
  page: Page,
  request: APIRequestContext,
  token: string,
  campusName: string,
): Promise<void> {
  await page.goto('/base-data/campuses');
  await expectNoActiveTableRow(page, campusName);

  const campuses = await apiGet<ApiRecord[]>(request, token, '/v1/base/campuses');
  expect(campuses.some((item) => item.campusName === campusName)).toBe(false);
}
