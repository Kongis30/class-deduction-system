import http from '@/api/http';
import type { ApiResponse } from '@/types/http';
import type { EntityId } from '@/types/domain';

export type GroupPlanCoverageType = 'FIXED_DATES' | 'ROLLING_MONTHS' | 'SESSION_COUNT';
export type GroupPlanUnit = 'SESSION' | 'MONTH';

export interface GroupRegistrationPlan {
  id: EntityId;
  planCode: string;
  planName: string;
  coverageType: GroupPlanCoverageType;
  termCode?: string;
  coverageStartDate?: string;
  coverageEndDate?: string;
  packageUnit: GroupPlanUnit;
  packageQuantity: number;
  fullDayPrice: number;
  halfDayPrice?: number;
  currencyCode: string;
  status: string;
  displayOrder: number;
  referenceCount: number;
}

export interface GroupProjectClass {
  id: EntityId;
  className: string;
  actualClass: string;
  campusId?: EntityId;
  campusName?: string;
  startDate?: string;
  endDate?: string;
  status: string;
}

export interface GroupCourseProject {
  id: EntityId;
  projectCode: string;
  projectName: string;
  description?: string;
  status: string;
  displayOrder: number;
  plans: GroupRegistrationPlan[];
  classes: GroupProjectClass[];
}

export interface GroupCourseProductSetup {
  productId: EntityId;
  productName: string;
  projects: GroupCourseProject[];
}

export interface GroupProjectHoliday {
  id: EntityId;
  holidayName: string;
  startDate: string;
  endDate: string;
}

export interface GroupProjectHolidayPayload {
  holidayName: string;
  startDate: string;
  endDate: string;
}

export interface GroupProjectHolidayPreview {
  affectedOccurrenceCount: number;
  affectedSessionEnrollmentCount: number;
  conflicts: string[];
}

export interface GroupCourseProjectPayload {
  projectCode: string;
  projectName: string;
  description?: string;
  displayOrder?: number;
  status?: string;
}

export interface GroupRegistrationPlanPayload {
  planCode: string;
  planName: string;
  coverageType: GroupPlanCoverageType;
  termCode?: string;
  coverageStartDate?: string;
  coverageEndDate?: string;
  packageUnit: GroupPlanUnit;
  packageQuantity: number;
  fullDayPrice: number;
  halfDayPrice?: number;
  currencyCode?: string;
  displayOrder?: number;
  status?: string;
}

export function fetchGroupCourseProductSetup(productId: EntityId, activeOnly = false) {
  return http.get<ApiResponse<GroupCourseProductSetup>>(`/v2/group-course-model/products/${productId}`, {
    params: activeOnly ? { activeOnly: true } : undefined,
  });
}

const projectHolidaysUrl = (projectId: EntityId) => `/v2/group-course-model/projects/${projectId}/holidays`;

export function fetchGroupProjectHolidays(projectId: EntityId) {
  return http.get<ApiResponse<GroupProjectHoliday[]>>(projectHolidaysUrl(projectId));
}

export function previewGroupProjectHoliday(projectId: EntityId, payload: GroupProjectHolidayPayload, holidayId?: EntityId) {
  return http.post<ApiResponse<GroupProjectHolidayPreview>>(`${projectHolidaysUrl(projectId)}/preview`, payload, {
    params: holidayId ? { holidayId } : undefined,
  });
}

export function createGroupProjectHoliday(projectId: EntityId, payload: GroupProjectHolidayPayload) {
  return http.post<ApiResponse<GroupProjectHoliday>>(`${projectHolidaysUrl(projectId)}`, payload);
}

export function updateGroupProjectHoliday(projectId: EntityId, holidayId: EntityId, payload: GroupProjectHolidayPayload) {
  return http.put<ApiResponse<GroupProjectHoliday>>(`${projectHolidaysUrl(projectId)}/${holidayId}`, payload);
}

export function deleteGroupProjectHoliday(projectId: EntityId, holidayId: EntityId) {
  return http.delete<ApiResponse<void>>(`${projectHolidaysUrl(projectId)}/${holidayId}`);
}

export function fetchGroupClassScheduledDates(classId: EntityId, startDate?: string, endDate?: string) {
  return http.get<ApiResponse<string[]>>(`/v2/group-course-model/classes/${classId}/scheduled-dates`, {
    params: { startDate, endDate },
  });
}

export function createGroupCourseProject(productId: EntityId, payload: GroupCourseProjectPayload) {
  return http.post<ApiResponse<EntityId>>(`/v2/group-course-model/products/${productId}/projects`, payload);
}

export function updateGroupCourseProject(projectId: EntityId, payload: GroupCourseProjectPayload) {
  return http.put<ApiResponse<null>>(`/v2/group-course-model/projects/${projectId}`, payload);
}

export function deleteGroupCourseProject(projectId: EntityId) {
  return http.delete<ApiResponse<null>>(`/v2/group-course-model/projects/${projectId}`);
}

export function createGroupRegistrationPlan(projectId: EntityId, payload: GroupRegistrationPlanPayload) {
  return http.post<ApiResponse<EntityId>>(`/v2/group-course-model/projects/${projectId}/plans`, payload);
}

export function updateGroupRegistrationPlan(planId: EntityId, payload: GroupRegistrationPlanPayload) {
  return http.put<ApiResponse<null>>(`/v2/group-course-model/plans/${planId}`, payload);
}

export function deleteGroupRegistrationPlan(planId: EntityId) {
  return http.delete<ApiResponse<null>>(`/v2/group-course-model/plans/${planId}`);
}
