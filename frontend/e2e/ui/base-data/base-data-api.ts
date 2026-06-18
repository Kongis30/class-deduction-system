import { expect, type APIRequestContext } from '@playwright/test';

import { apiDelete, apiGet, apiPost, type PageResult } from '../../helpers/api';

type ApiRecord = Record<string, any>;

export async function deleteCampusesByMarker(request: APIRequestContext, token: string, marker: string): Promise<void> {
  const campuses = await apiGet<ApiRecord[]>(request, token, '/v1/base/campuses');
  for (const campus of campuses.filter((item) => String(item.campusName).includes(marker))) {
    await apiDelete(request, token, `/v1/base/campuses/${campus.id}`);
  }
}

export async function deleteSubjectsByMarker(request: APIRequestContext, token: string, marker: string): Promise<void> {
  const subjects = await apiGet<ApiRecord[]>(request, token, '/v1/base/subjects');
  for (const subject of subjects.filter((item) => String(item.subjectName).includes(marker))) {
    await apiDelete(request, token, `/v1/base/subjects/${subject.id}`);
  }
}

export async function createCampusByApi(
  request: APIRequestContext,
  token: string,
  marker: string,
  suffix: string,
): Promise<ApiRecord> {
  await apiPost<string>(request, token, '/v1/base/campuses', {
    campusName: `${marker} API Campus ${suffix}`,
    displayOrder: 90,
    status: 'ACTIVE',
    notes: marker,
  });
  const campuses = await apiGet<ApiRecord[]>(request, token, '/v1/base/campuses');
  const campus = campuses.find((item) => item.campusName === `${marker} API Campus ${suffix}`);
  expect(campus, 'API setup should create campus').toBeTruthy();
  return campus!;
}

export async function createClassroomByApi(
  request: APIRequestContext,
  token: string,
  marker: string,
  suffix: string,
  campusName: string,
): Promise<ApiRecord> {
  await apiPost<string>(request, token, '/v1/base/classrooms', {
    roomName: `${marker} API Room ${suffix}`,
    campus: campusName,
    capacity: 12,
    status: 'ACTIVE',
    notes: marker,
  });
  const classrooms = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/base/classrooms', {
    keyword: `${marker} API Room ${suffix}`,
    pageNum: 1,
    pageSize: 20,
  });
  expect(classrooms.records[0], 'API setup should create classroom').toBeTruthy();
  return classrooms.records[0];
}

export async function createTeacherAccountByApi(
  request: APIRequestContext,
  token: string,
  marker: string,
  suffix: string,
): Promise<ApiRecord> {
  const teacherName = `${marker} API Teacher ${suffix}`;
  await apiPost<string>(request, token, '/v1/auth/teacher-accounts', {
    teacherName,
    username: `${marker}_${suffix}_api_teacher`,
    password: 'Test@123456',
    mobile: '626-555-0301',
    email: `${marker.toLowerCase()}.${suffix}.teacher@example.com`,
    subjectTags: ['Math'],
  });
  const teachers = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/base/teachers', {
    keyword: teacherName,
    pageNum: 1,
    pageSize: 20,
  });
  expect(teachers.records[0], 'API setup should create teacher profile').toBeTruthy();
  return teachers.records[0];
}

export async function createStudentAccountByApi(
  request: APIRequestContext,
  token: string,
  marker: string,
  suffix: string,
): Promise<{ userId: string; profile: ApiRecord }> {
  const studentName = `${marker} API Student ${suffix}`;
  const userId = await apiPost<string>(request, token, '/v1/auth/student-accounts', {
    studentName,
    guardianName: `${marker} API Guardian`,
    guardianPhone: '626-555-0302',
    guardianEmail: `${marker.toLowerCase()}.${suffix}.guardian@example.com`,
    username: `${marker}_${suffix}_api_student`,
    password: 'Test@123456',
  });
  const students = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/base/students', {
    keyword: studentName,
    pageNum: 1,
    pageSize: 20,
  });
  expect(students.records[0], 'API setup should create student profile').toBeTruthy();
  return { userId, profile: students.records[0] };
}

export async function createTeacherAccountWithUserByApi(
  request: APIRequestContext,
  token: string,
  marker: string,
  suffix: string,
): Promise<{ userId: string; profile: ApiRecord }> {
  const teacherName = `${marker} API Teacher ${suffix}`;
  const userId = await apiPost<string>(request, token, '/v1/auth/teacher-accounts', {
    teacherName,
    username: `${marker}_${suffix}_api_teacher`,
    password: 'Test@123456',
    mobile: '626-555-0301',
    email: `${marker.toLowerCase()}.${suffix}.teacher@example.com`,
    subjectTags: ['Math'],
  });
  const teachers = await apiGet<PageResult<ApiRecord>>(request, token, '/v1/base/teachers', {
    keyword: teacherName,
    pageNum: 1,
    pageSize: 20,
  });
  expect(teachers.records[0], 'API setup should create teacher profile').toBeTruthy();
  return { userId, profile: teachers.records[0] };
}
