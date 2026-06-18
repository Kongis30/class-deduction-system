import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

import { apiDelete, apiGet, apiLogin, type PageResult } from '../../helpers/api';
import { cleanupE2EData, createDbConnection } from '../../helpers/db';
import {
  expectNoActiveTableRow,
  fillInputByLabel,
  panelByTitle,
  selectByLabel,
  selectElementPlusOption,
  tableRowByText,
  visibleDialog,
} from '../helpers/element-plus';
import { loginAsAdmin } from '../helpers/session';
import { createStudentAccountByApi, createTeacherAccountWithUserByApi } from './base-data-api';

const MARKER = 'E2EUI_BASE';

type ApiRecord = Record<string, any>;

test.describe.serial('Base Data - Students and Teachers E2E UI', () => {
  test.beforeAll(async () => {
    const db = await createDbConnection();
    try {
      await cleanupE2EData(db, MARKER);
    } finally {
      await db.end();
    }
  });

  test.afterAll(async () => {
    const db = await createDbConnection();
    try {
      await cleanupE2EData(db, MARKER);
    } finally {
      await db.end();
    }
  });

  test('学生和教师页面覆盖编辑、筛选、字段配置、deleted 查看和数据一致性', async ({ page, request }) => {
    const token = await apiLogin(request);
    const suffix = Date.now().toString(36);
    const studentSetup = await createStudentAccountByApi(request, token, MARKER, `${suffix}_profile_student`);
    const teacherSetup = await createTeacherAccountWithUserByApi(request, token, MARKER, `${suffix}_profile_teacher`);
    const editedStudentName = `${MARKER} Edited Student ${suffix}`;
    const editedTeacherName = `${MARKER} Edited Teacher ${suffix}`;

    await loginAsAdmin(page);

    await exerciseStudentFieldSettings(page, studentSetup.profile.studentName);
    await editStudentProfileFromUi(page, studentSetup.profile.studentName, editedStudentName);
    await expectStudentProfileVisible(page, editedStudentName);
    await expectStudentProfileInApi(request, token, editedStudentName);

    await editTeacherProfileFromUi(page, teacherSetup.profile.teacherName, editedTeacherName);
    await expectTeacherProfileVisible(page, editedTeacherName);
    await expectTeacherProfileInApi(request, token, editedTeacherName);

    await apiDelete(request, token, `/v1/auth/student-accounts/${studentSetup.userId}`);
    await apiDelete(request, token, `/v1/auth/teacher-accounts/${teacherSetup.userId}`);

    await expectStudentDeletedVisible(page, request, token, editedStudentName);
    await expectTeacherDeletedVisible(page, request, token, editedTeacherName);
  });
});

async function exerciseStudentFieldSettings(page: Page, studentName: string): Promise<void> {
  await page.goto('/base-data/students');
  await page.getByPlaceholder('Search by name').fill(studentName);
  await tableRowByText(page, studentName).getByRole('button', { name: 'Edit' }).click();
  const studentDialog = visibleDialog(page, 'Edit student');
  await studentDialog.locator('.dialog-header__settings').click();

  const settingsDialog = visibleDialog(page, 'Form Field Settings');
  await expect(settingsDialog.getByText('Toggle and rename optional fields')).toBeVisible();
  await settingsDialog.getByRole('button', { name: 'Reset to Default' }).click();
  await settingsDialog.getByRole('button', { name: 'Save' }).click();
  await studentDialog.getByRole('button', { name: 'Cancel' }).click();
}

async function editStudentProfileFromUi(page: Page, originalName: string, editedName: string): Promise<void> {
  await page.goto('/base-data/students');
  await page.getByPlaceholder('Search by name').fill(originalName);
  await tableRowByText(page, originalName).getByRole('button', { name: 'Edit' }).click();
  const dialog = visibleDialog(page, 'Edit student');
  await fillInputByLabel(dialog, 'Student name', editedName);
  await selectByLabel(page, dialog, 'Gender', 'Male');
  await fillInputByLabel(dialog, 'Guardian name', `${MARKER} Edited Guardian`);
  await fillInputByLabel(dialog, 'Phone', '626-555-0402');
  await fillInputByLabel(dialog, 'Guardian email', 'edited.student.guardian@example.com');
  await selectByLabel(page, dialog, 'Status', 'Inactive');

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/students/') && res.request().method() === 'PUT',
  );
  await dialog.getByRole('button', { name: 'Save' }).click();
  expect((await response).ok(), '学生档案编辑接口应成功').toBeTruthy();
}

async function editTeacherProfileFromUi(page: Page, originalName: string, editedName: string): Promise<void> {
  await page.goto('/base-data/teachers');
  await page.getByPlaceholder('Search by name').fill(originalName);
  await tableRowByText(page, originalName).getByRole('button', { name: 'Edit' }).click();
  const dialog = visibleDialog(page, 'Edit');
  await fillInputByLabel(dialog, 'Name', editedName);
  await fillInputByLabel(dialog, 'Phone', '626-555-0401');
  await fillInputByLabel(dialog, 'Email', 'edited.teacher@example.com');
  await selectElementPlusOption(page, dialog.locator('.el-form-item').filter({ hasText: 'Subjects' }).locator('.el-select').first(), 'Science');
  await selectByLabel(page, dialog, 'Status', 'Inactive');
  await fillInputByLabel(dialog, 'Notes', 'Edited teacher profile note');

  const response = page.waitForResponse((res) =>
    res.url().includes('/api/v1/base/teachers/') && res.request().method() === 'PUT',
  );
  await dialog.getByRole('button', { name: 'Save' }).click();
  expect((await response).ok(), '教师档案编辑接口应成功').toBeTruthy();
}

async function expectStudentProfileVisible(page: Page, studentName: string): Promise<void> {
  await page.goto('/base-data/students');
  await page.getByPlaceholder('Search by name').fill(studentName);
  await selectByLabel(page, page, 'Status', 'Inactive');
  const row = tableRowByText(panelByTitle(page, 'Students'), studentName);
  await expect(row).toContainText(`${MARKER} Edited Guardian`);
  await expect(row).toContainText('626-555-0402');
  await expect(row).toContainText('edited.student.guardian@example.com');
  await expect(row).toContainText('Inactive');
}

async function expectTeacherProfileVisible(page: Page, teacherName: string): Promise<void> {
  await page.goto('/base-data/teachers');
  await page.getByPlaceholder('Search by name').fill(teacherName);
  await selectByLabel(page, page, 'Status', 'Inactive');
  const row = tableRowByText(panelByTitle(page, 'Instructors'), teacherName);
  await expect(row).toContainText('626-555-0401');
  await expect(row).toContainText('edited.teacher@example.com');
  await expect(row).toContainText('Math');
  await expect(row).toContainText('Science');
  await expect(row).toContainText('Inactive');
}

async function expectStudentProfileInApi(
  request: APIRequestContext,
  token: string,
  studentName: string,
): Promise<void> {
  const students = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/base/students', {
    keyword: studentName,
    status: 'INACTIVE',
    pageNum: 1,
    pageSize: 20,
  });
  expect(students.records).toContainEqual(expect.objectContaining({
    studentName,
    gender: 'Male',
    guardianName: `${MARKER} Edited Guardian`,
    phone: '626-555-0402',
    guardianEmail: 'edited.student.guardian@example.com',
    status: 'INACTIVE',
  }));
}

async function expectTeacherProfileInApi(
  request: APIRequestContext,
  token: string,
  teacherName: string,
): Promise<void> {
  const teachers = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/base/teachers', {
    keyword: teacherName,
    status: 'INACTIVE',
    pageNum: 1,
    pageSize: 20,
  });
  expect(teachers.records).toContainEqual(expect.objectContaining({
    teacherName,
    mobile: '626-555-0401',
    email: 'edited.teacher@example.com',
    status: 'INACTIVE',
  }));
  expect(teachers.records[0].subjectTags).toEqual(expect.arrayContaining(['Math', 'Science']));
}

async function expectStudentDeletedVisible(
  page: Page,
  request: APIRequestContext,
  token: string,
  studentName: string,
): Promise<void> {
  await page.goto('/base-data/students');
  await page.getByPlaceholder('Search by name').fill(studentName);
  await expectNoActiveTableRow(page, studentName);
  await page.getByRole('button', { name: 'Deleted' }).click();
  const deletedDialog = visibleDialog(page, 'Deleted Students');
  await expect(tableRowByText(deletedDialog, studentName)).toContainText('edited.student.guardian@example.com');
  // 关闭 Deleted 弹窗（footer 的 Close 按钮，区别于 header 的 "Close this dialog"）
  await deletedDialog.getByRole('button', { name: 'Close', exact: true }).click();
  await expect(deletedDialog).toBeHidden();

  const deletedStudents = await apiGet<ApiRecord[]>(request, token, '/v1/base/students/deleted');
  expect(deletedStudents).toContainEqual(expect.objectContaining({ studentName }));
}

async function expectTeacherDeletedVisible(
  page: Page,
  request: APIRequestContext,
  token: string,
  teacherName: string,
): Promise<void> {
  await page.goto('/base-data/teachers');
  await page.getByPlaceholder('Search by name').fill(teacherName);
  await expectNoActiveTableRow(page, teacherName);
  await page.getByRole('button', { name: 'Deleted' }).click();
  const deletedDialog = visibleDialog(page, 'Deleted Instructors');
  await expect(tableRowByText(deletedDialog, teacherName)).toContainText('edited.teacher@example.com');
  // 关闭 Deleted 弹窗（footer 的 Close 按钮，区别于 header 的 "Close this dialog"）
  await deletedDialog.getByRole('button', { name: 'Close', exact: true }).click();
  await expect(deletedDialog).toBeHidden();

  const deletedTeachers = await apiGet<ApiRecord[]>(request, token, '/v1/base/teachers/deleted');
  expect(deletedTeachers).toContainEqual(expect.objectContaining({ teacherName }));
}
