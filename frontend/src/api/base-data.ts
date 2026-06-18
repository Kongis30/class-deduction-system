import http from './http';
import type { ApiResponse, PageResult } from '@/types/http';
import type { CampusItem, ClassroomItem, CourseProductItem, DiscountPresetItem, EntityId, StudentItem, SubjectItem, TeacherItem } from '@/types/domain';

// ─── Students ──────────────────────────────────────��─
export interface StudentQuery {
  keyword?: string;
  guardian?: string;
  status?: string;
  pageNum?: number;
  pageSize?: number;
}

export function fetchStudents(params: StudentQuery = {}) {
  return http.get<ApiResponse<PageResult<StudentItem>>>('/v1/base/students', { params });
}

export function fetchAllStudents() {
  return http.get<ApiResponse<StudentItem[]>>('/v1/base/students/all');
}

export interface StudentSavePayload {
  studentName: string;
  gender?: string;
  guardianName?: string;
  guardianPhone?: string;
  guardianEmail?: string;
  guardianRelationType?: string;
  status?: string;
  notes?: string;
  englishName?: string;
  birthDate?: string;
  sourceChannel?: string;
  campusId?: EntityId;
}

export function createStudent(data: StudentSavePayload) {
  return http.post('/v1/base/students', data);
}

export function updateStudent(id: string, data: StudentSavePayload) {
  return http.put(`/v1/base/students/${id}`, data);
}

export function deleteStudent(id: string) {
  return http.delete(`/v1/base/students/${id}`);
}

export function fetchDeletedStudents() {
  return http.get<ApiResponse<StudentItem[]>>('/v1/base/students/deleted');
}

// ─── Teachers ────────��───────────────────────────────
export function fetchAllTeachers() {
  return http.get<ApiResponse<TeacherItem[]>>('/v1/base/teachers/active');
}

export interface TeacherQuery {
  keyword?: string;
  subject?: string;
  status?: string;
  pageNum?: number;
  pageSize?: number;
}

export function fetchTeachers(params: TeacherQuery = {}) {
  return http.get<ApiResponse<PageResult<TeacherItem>>>('/v1/base/teachers', { params });
}

export interface TeacherSavePayload {
  teacherName: string;
  mobile?: string | null;
  email?: string | null;
  subjectTags?: string[];
  status?: string;
  notes?: string | null;
}

export function createTeacher(data: TeacherSavePayload) {
  return http.post('/v1/base/teachers', data);
}

export function updateTeacher(id: EntityId, data: TeacherSavePayload) {
  return http.put(`/v1/base/teachers/${id}`, data);
}

export function deleteTeacher(id: EntityId) {
  return http.delete(`/v1/base/teachers/${id}`);
}

export function fetchDeletedTeachers() {
  return http.get<ApiResponse<TeacherItem[]>>('/v1/base/teachers/deleted');
}

// ─── Classrooms ─────────────────────────────────────
export interface ClassroomQuery {
  keyword?: string;
  campus?: string;
  status?: string;
  pageNum?: number;
  pageSize?: number;
}

export function fetchClassrooms(params: ClassroomQuery = {}) {
  return http.get<ApiResponse<PageResult<ClassroomItem>>>('/v1/base/classrooms', { params });
}

export function fetchAllClassrooms() {
  return http.get<ApiResponse<ClassroomItem[]>>('/v1/base/classrooms/active');
}

export interface ClassroomSavePayload {
  roomName: string;
  campus: string;
  capacity?: number;
  status?: string;
  notes?: string;
}

export function createClassroom(data: ClassroomSavePayload) {
  return http.post('/v1/base/classrooms', data);
}

export function updateClassroom(id: EntityId, data: ClassroomSavePayload) {
  return http.put(`/v1/base/classrooms/${id}`, data);
}

export function deleteClassroom(id: EntityId) {
  return http.delete(`/v1/base/classrooms/${id}`);
}

// ─── Subjects ────────────────────────────────────────
export function fetchSubjects() {
  return http.get<ApiResponse<SubjectItem[]>>('/v1/base/subjects');
}

export interface SubjectSavePayload {
  subjectName: string;
  displayOrder?: number;
  status?: string;
  notes?: string;
}

export function createSubject(data: SubjectSavePayload) {
  return http.post<ApiResponse<number>>('/v1/base/subjects', data);
}

export function updateSubject(id: EntityId, data: SubjectSavePayload) {
  return http.put<ApiResponse<void>>(`/v1/base/subjects/${id}`, data);
}

export function deleteSubject(id: EntityId) {
  return http.delete<ApiResponse<void>>(`/v1/base/subjects/${id}`);
}

// ─── Campuses ────────────────────────────────────────
export function fetchCampuses() {
  return http.get<ApiResponse<CampusItem[]>>('/v1/base/campuses');
}

export interface CampusSavePayload {
  campusName: string;
  displayOrder?: number;
  status?: string;
  notes?: string;
}

export function createCampus(data: CampusSavePayload) {
  return http.post('/v1/base/campuses', data);
}

export function updateCampus(id: EntityId, data: CampusSavePayload) {
  return http.put(`/v1/base/campuses/${id}`, data);
}

export function deleteCampus(id: EntityId) {
  return http.delete(`/v1/base/campuses/${id}`);
}

// ─── Course Products ─────────────────────────────────
export interface CourseProductQuery {
  keyword?: string;
  status?: string;
  teachingMode?: string;
  pageNum?: number;
  pageSize?: number;
}

export function fetchCourseProducts(params: CourseProductQuery = {}) {
  return http.get<ApiResponse<PageResult<CourseProductItem>>>('/v1/base/course-products', { params });
}

export interface CourseProductSavePayload {
  productCode: string;
  productName: string;
  subjectName?: string;
  teachingMode: string;
  defaultTotalUnits?: number;
  defaultLessonUnits?: number;
  billingType?: string;
  startDate?: string;
  totalSessions?: number;
  startMonth?: string;
  endMonth?: string;
  defaultClassroomId?: EntityId;
  defaultTeacherId?: EntityId;
  listPrice: number;
  status?: string;
  notes?: string;
  slots?: { weekday: number; startTime: string; endTime: string }[];
}

export function createCourseProduct(data: CourseProductSavePayload) {
  return http.post('/v1/base/course-products', data);
}

export function updateCourseProduct(id: EntityId, data: CourseProductSavePayload) {
  return http.put(`/v1/base/course-products/${id}`, data);
}

export function deleteCourseProduct(id: EntityId) {
  return http.delete(`/v1/base/course-products/${id}`);
}

// ─── Discount Presets ──────────────────────────────────
export function fetchDiscountPresets() {
  return http.get<ApiResponse<DiscountPresetItem[]>>('/v1/base/discount-presets');
}

export interface DiscountPresetSavePayload {
  presetName: string;
  discountType: 'PERCENTAGE' | 'FIXED';
  discountValue: number;
  sortOrder?: number;
  status?: string;
}

export function createDiscountPreset(data: DiscountPresetSavePayload) {
  return http.post('/v1/base/discount-presets', data);
}

export function updateDiscountPreset(id: EntityId, data: DiscountPresetSavePayload) {
  return http.put(`/v1/base/discount-presets/${id}`, data);
}

export function deleteDiscountPreset(id: EntityId) {
  return http.delete(`/v1/base/discount-presets/${id}`);
}
