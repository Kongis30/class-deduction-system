import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

import { apiGet, apiLogin, type PageResult } from '../../helpers/api';
import { cleanupE2EData, createDbConnection } from '../../helpers/db';
import { expectNoActiveTableRow, panelByTitle, selectElementPlusOption, tableRowByText } from '../helpers/element-plus';
import { loginAsAdmin } from '../helpers/session';

const MARKER = 'E2EUI';
const PASSWORD = 'Test@123456';

type ApiRecord = Record<string, any>;

interface StudentFixture {
  name: string;
  guardianName: string;
  guardianPhone: string;
  guardianEmail: string;
  username: string;
}

interface TeacherFixture {
  name: string;
  mobile: string;
  email: string;
  username: string;
  subjects: string[];
}

test.describe.serial('账号、学生、教师档案同步 E2E UI', () => {
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

  test('从账号页创建并删除学生/教师账号时，主数据页面保持同步', async ({ page, request }) => {
    const token = await apiLogin(request);
    const suffix = Date.now().toString(36);
    const student: StudentFixture = {
      name: `${MARKER} Student ${suffix}`,
      guardianName: `${MARKER} Guardian ${suffix}`,
      guardianPhone: '626-555-0102',
      guardianEmail: `${MARKER.toLowerCase()}.${suffix}.guardian@example.com`,
      username: `${MARKER}_${suffix}_student`,
    };
    const teacher: TeacherFixture = {
      name: `${MARKER} Teacher ${suffix}`,
      mobile: '626-555-0101',
      email: `${MARKER.toLowerCase()}.${suffix}.teacher@example.com`,
      username: `${MARKER}_${suffix}_teacher`,
      subjects: ['Math', 'Science'],
    };

    await loginAsAdmin(page);

    await createStudentAccountFromUi(page, student);
    await expectStudentAccountVisible(page, student);
    await expectStudentProfileVisible(page, student);
    await expectStudentConsistentInApi(request, token, student);

    await createTeacherAccountFromUi(page, teacher);
    await expectTeacherAccountVisible(page, teacher);
    await expectTeacherProfileVisible(page, teacher);
    await expectTeacherConsistentInApi(request, token, teacher);

    await deleteStudentAccountFromUi(page, student);
    await expectStudentDeletedEverywhere(page, request, token, student);

    await deleteTeacherAccountFromUi(page, teacher);
    await expectTeacherDeletedEverywhere(page, request, token, teacher);
  });
});

async function createStudentAccountFromUi(page: Page, student: StudentFixture): Promise<void> {
  await page.goto('/system/accounts');
  const studentSection = panelByTitle(page, 'Student Accounts');

  await studentSection.getByRole('button', { name: 'Add account' }).click();
  const dialog = page.locator('.el-dialog:visible').filter({ hasText: 'New Account' });
  await dialog.getByPlaceholder('e.g. Sophia Lin').fill(student.name);
  await dialog.getByPlaceholder('Primary guardian').fill(student.guardianName);
  await dialog.getByPlaceholder('(626) 555-0102').fill(student.guardianPhone);
  await dialog.getByPlaceholder('parent@example.com').fill(student.guardianEmail);
  await dialog.getByPlaceholder('Login username').fill(student.username);
  await dialog.getByPlaceholder('Min 8 characters').fill(PASSWORD);
  await dialog.getByPlaceholder('Re-enter password').fill(PASSWORD);

  const createResponse = page.waitForResponse((response) =>
    response.url().includes('/api/v1/auth/student-accounts') && response.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Create' }).click();
  expect((await createResponse).ok(), '学生账号创建接口应成功').toBeTruthy();
}

async function createTeacherAccountFromUi(page: Page, teacher: TeacherFixture): Promise<void> {
  await page.goto('/system/accounts');
  const teacherSection = panelByTitle(page, 'Teacher Accounts');

  await teacherSection.getByRole('button', { name: 'Add account' }).click();
  const dialog = page.locator('.el-dialog:visible').filter({ hasText: 'New Account' });
  await dialog.getByPlaceholder('e.g. Emily Chen').fill(teacher.name);
  await dialog.getByPlaceholder('(626) 555-0101').fill(teacher.mobile);
  await dialog.getByPlaceholder('teacher@example.com').fill(teacher.email);
  const subjectSelect = dialog.locator('.el-form-item').filter({ hasText: 'Subjects' }).locator('.el-select').first();
  for (const subject of teacher.subjects) {
    await selectElementPlusOption(page, subjectSelect, subject);
  }
  await dialog.getByPlaceholder('Login username').fill(teacher.username);
  await dialog.getByPlaceholder('Min 8 characters').fill(PASSWORD);
  await dialog.getByPlaceholder('Re-enter password').fill(PASSWORD);

  const createResponse = page.waitForResponse((response) =>
    response.url().includes('/api/v1/auth/teacher-accounts') && response.request().method() === 'POST',
  );
  await dialog.getByRole('button', { name: 'Create' }).click();
  expect((await createResponse).ok(), '教师账号创建接口应成功').toBeTruthy();
}

async function expectStudentAccountVisible(page: Page, student: StudentFixture): Promise<void> {
  await page.goto('/system/accounts');
  const studentSection = panelByTitle(page, 'Student Accounts');
  const row = tableRowByText(studentSection, student.username);
  await expect(row).toContainText(student.name);
  await expect(row).toContainText(student.guardianEmail);
  await expect(row).toContainText('Active');
}

async function expectTeacherAccountVisible(page: Page, teacher: TeacherFixture): Promise<void> {
  await page.goto('/system/accounts');
  const teacherSection = panelByTitle(page, 'Teacher Accounts');
  const row = tableRowByText(teacherSection, teacher.username);
  await expect(row).toContainText(teacher.name);
  await expect(row).toContainText(teacher.email);
  await expect(row).toContainText('Active');
}

async function expectStudentProfileVisible(page: Page, student: StudentFixture): Promise<void> {
  await page.goto('/base-data/students');
  await page.getByPlaceholder('Search by name').fill(student.name);
  const row = tableRowByText(page, student.name);
  await expect(row).toContainText(student.guardianName);
  await expect(row).toContainText(student.guardianPhone);
  await expect(row).toContainText(student.guardianEmail);
  await expect(row).toContainText('Active');
}

async function expectTeacherProfileVisible(page: Page, teacher: TeacherFixture): Promise<void> {
  await page.goto('/base-data/teachers');
  await page.getByPlaceholder('Search by name').fill(teacher.name);
  const row = tableRowByText(page, teacher.name);
  await expect(row).toContainText(teacher.mobile);
  await expect(row).toContainText(teacher.email);
  for (const subject of teacher.subjects) {
    await expect(row).toContainText(subject);
  }
  await expect(row).toContainText('Active');
}

async function deleteStudentAccountFromUi(page: Page, student: StudentFixture): Promise<void> {
  await page.goto('/system/accounts');
  const studentSection = panelByTitle(page, 'Student Accounts');
  await tableRowByText(studentSection, student.username).getByRole('button', { name: 'Delete account' }).click();
  await confirmDeleteAccount(page, '/api/v1/auth/student-accounts');
}

async function deleteTeacherAccountFromUi(page: Page, teacher: TeacherFixture): Promise<void> {
  await page.goto('/system/accounts');
  const teacherSection = panelByTitle(page, 'Teacher Accounts');
  await tableRowByText(teacherSection, teacher.username).getByRole('button', { name: 'Delete account' }).click();
  await confirmDeleteAccount(page, '/api/v1/auth/teacher-accounts');
}

async function confirmDeleteAccount(page: Page, apiPath: string): Promise<void> {
  const dialog = page.locator('.el-dialog:visible').filter({ hasText: 'Delete Account' });
  const deleteResponse = page.waitForResponse((response) =>
    response.url().includes(apiPath) && response.request().method() === 'DELETE',
  );
  // The product intentionally requires a 10-second cooling-off period before deletion.
  await dialog.getByRole('button', { name: 'Delete account', exact: true }).click();
  expect((await deleteResponse).ok(), '账号删除接口应成功').toBeTruthy();
}

async function expectStudentDeletedEverywhere(
  page: Page,
  request: APIRequestContext,
  token: string,
  student: StudentFixture,
): Promise<void> {
  await page.goto('/system/accounts');
  const studentSection = panelByTitle(page, 'Student Accounts');
  await expectNoActiveTableRow(studentSection, student.username);
  await studentSection.getByRole('button', { name: 'Deleted' }).click();
  await expect(tableRowByText(page.locator('.el-dialog:visible'), student.username)).toContainText(student.name);
  await page.locator('.el-dialog:visible').getByRole('button', { name: 'Close', exact: true }).click();

  await page.goto('/base-data/students');
  await page.getByPlaceholder('Search by name').fill(student.name);
  await expectNoActiveTableRow(page, student.name);
  await page.getByRole('button', { name: 'Deleted' }).click();
  await expect(tableRowByText(page.locator('.el-dialog:visible'), student.name)).toContainText(student.guardianEmail);

  const activeAccounts = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/auth/student-accounts', {
    keyword: student.name,
    pageNum: 1,
    pageSize: 20,
  });
  expect(activeAccounts.records).toHaveLength(0);

  const activeProfiles = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/base/students', {
    keyword: student.name,
    pageNum: 1,
    pageSize: 20,
  });
  expect(activeProfiles.records).toHaveLength(0);

  const deletedAccounts = await apiGet<ApiRecord[]>(request, token, '/v1/auth/student-accounts/deleted');
  expect(deletedAccounts.some((item) => item.username === student.username && item.studentName === student.name)).toBe(true);

  const deletedProfiles = await apiGet<ApiRecord[]>(request, token, '/v1/base/students/deleted');
  expect(deletedProfiles.some((item) => item.studentName === student.name && item.guardianEmail === student.guardianEmail)).toBe(true);
}

async function expectTeacherDeletedEverywhere(
  page: Page,
  request: APIRequestContext,
  token: string,
  teacher: TeacherFixture,
): Promise<void> {
  await page.goto('/system/accounts');
  const teacherSection = panelByTitle(page, 'Teacher Accounts');
  await expectNoActiveTableRow(teacherSection, teacher.username);
  await teacherSection.getByRole('button', { name: 'Deleted' }).click();
  await expect(tableRowByText(page.locator('.el-dialog:visible'), teacher.username)).toContainText(teacher.name);
  await page.locator('.el-dialog:visible').getByRole('button', { name: 'Close', exact: true }).click();

  await page.goto('/base-data/teachers');
  await page.getByPlaceholder('Search by name').fill(teacher.name);
  await expectNoActiveTableRow(page, teacher.name);
  await page.getByRole('button', { name: 'Deleted' }).click();
  await expect(tableRowByText(page.locator('.el-dialog:visible'), teacher.name)).toContainText(teacher.email);

  const activeAccounts = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/auth/teacher-accounts', {
    keyword: teacher.name,
    pageNum: 1,
    pageSize: 20,
  });
  expect(activeAccounts.records).toHaveLength(0);

  const activeProfiles = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/base/teachers', {
    keyword: teacher.name,
    pageNum: 1,
    pageSize: 20,
  });
  expect(activeProfiles.records).toHaveLength(0);

  const deletedAccounts = await apiGet<ApiRecord[]>(request, token, '/v1/auth/teacher-accounts/deleted');
  expect(deletedAccounts.some((item) => item.username === teacher.username && item.teacherName === teacher.name)).toBe(true);

  const deletedProfiles = await apiGet<ApiRecord[]>(request, token, '/v1/base/teachers/deleted');
  expect(deletedProfiles.some((item) => item.teacherName === teacher.name && item.email === teacher.email)).toBe(true);
}

async function expectStudentConsistentInApi(
  request: APIRequestContext,
  token: string,
  student: StudentFixture,
): Promise<void> {
  const accounts = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/auth/student-accounts', {
    keyword: student.name,
    pageNum: 1,
    pageSize: 20,
  });
  expect(accounts.records).toContainEqual(expect.objectContaining({
    username: student.username,
    studentName: student.name,
    displayName: student.name,
    email: student.guardianEmail,
    status: 'ACTIVE',
  }));

  const profiles = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/base/students', {
    keyword: student.name,
    pageNum: 1,
    pageSize: 20,
  });
  expect(profiles.records).toContainEqual(expect.objectContaining({
    studentName: student.name,
    guardianName: student.guardianName,
    phone: student.guardianPhone,
    guardianEmail: student.guardianEmail,
    status: 'ACTIVE',
  }));
}

async function expectTeacherConsistentInApi(
  request: APIRequestContext,
  token: string,
  teacher: TeacherFixture,
): Promise<void> {
  const accounts = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/auth/teacher-accounts', {
    keyword: teacher.name,
    pageNum: 1,
    pageSize: 20,
  });
  expect(accounts.records).toContainEqual(expect.objectContaining({
    username: teacher.username,
    teacherName: teacher.name,
    displayName: teacher.name,
    mobile: teacher.mobile,
    email: teacher.email,
    status: 'ACTIVE',
  }));

  const profiles = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/base/teachers', {
    keyword: teacher.name,
    pageNum: 1,
    pageSize: 20,
  });
  expect(profiles.records).toContainEqual(expect.objectContaining({
    teacherName: teacher.name,
    mobile: teacher.mobile,
    email: teacher.email,
    status: 'ACTIVE',
  }));
  expect(profiles.records[0].subjectTags).toEqual(expect.arrayContaining(teacher.subjects));
}
