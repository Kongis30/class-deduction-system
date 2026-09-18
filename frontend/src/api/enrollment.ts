import http from './http';
import type { ApiResponse, PageResult } from '@/types/http';
import type {
  CoursePackageItem,
  EntityId,
  EnrollmentOrderDetailItem,
  EnrollmentOrderItem,
  GroupClassPackageProgressItem,
  GroupClassStudentProgressItem,
  PaymentMethodItem,
  ReceiptRecordItem,
} from '@/types/domain';

// ─── Enrollment Orders ───────────────────────────────
export interface EnrollmentOrderQuery {
  keyword?: string;
  orderStatus?: string;
  paymentStatus?: string;
  studentId?: string | number;
  classId?: string | number;
  excludeClassOrders?: boolean;
  pageNum?: number;
  pageSize?: number;
}

export function fetchEnrollmentOrders(params: EnrollmentOrderQuery = {}) {
  return http.get<ApiResponse<PageResult<EnrollmentOrderItem>>>('/v1/enrollment/orders', { params });
}

export function fetchEnrollmentOrder(id: string | number) {
  return http.get<ApiResponse<EnrollmentOrderDetailItem>>(`/v1/enrollment/orders/${id}`);
}

export interface EnrollmentOrderSavePayload {
  studentId: string | number;
  courseProductId: string | number;
  cashierUserId?: EntityId;
  handledByName: string;
  currencyCode?: string;
  packageName?: string;
  startDate?: string;
  estimatedEndDate?: string;
  paidAt?: string;
  issueReceipt?: boolean;
  templateCode?: string;
  originalAmount: number;
  discountAmount: number;
  receivedAmount: number;
  totalUnits: number;
  remark?: string;
}

export function createEnrollmentOrder(data: EnrollmentOrderSavePayload) {
  return http.post('/v1/enrollment/orders', data);
}

export function cancelEnrollmentOrder(id: string | number) {
  return http.post(`/v1/enrollment/orders/${id}/cancel`);
}

export interface EnrollmentOrderCorrectionPayload {
  participants?: RegistrationStudentPayload[];
  participantBillings?: RegistrationParticipantBillingPayload[];
  studentName?: string;
  grade?: string;
  studentClass?: string;
  school?: string;
  guardianPhone?: string;
  guardianPhone2?: string;
  campusId?: EntityId;
  campusName?: string;
  campusAddress?: string;
  campusPhone?: string;
  taxId?: string;
  pickupRequired?: boolean;
  pickupAddress?: string;
  dropoffRequired?: boolean;
  dropoffAddress?: string;
  signupDate?: string;
  handledByName?: string;
  remark?: string;
  pickupFeeCharged?: boolean;
  pickupFeeAmount?: number;
  dropoffFeeCharged?: boolean;
  dropoffFeeAmount?: number;
  extraFeeAmount?: number;
  extraFees?: RegistrationFeeLinePayload[];
  discountAmount?: number;
  discounts?: RegistrationFeeLinePayload[];
  items?: RegistrationCourseItemPayload[];
  payments?: RegistrationPaymentPayload[];
  receiptOptions?: RegistrationReceiptOptionsPayload;
}

export function correctEnrollmentOrder(id: string | number, data: EnrollmentOrderCorrectionPayload) {
  return http.put<ApiResponse<void>>(`/v1/enrollment/orders/${id}/correction`, data);
}

export interface RefundPayload {
  refundAmount: number;
  participantIndex?: number;
  paymentMethod: string;
  handledByName: string;
  handledByUserId?: string | number;
  remark?: string;
}

export function refundEnrollmentOrder(id: string | number, data: RefundPayload) {
  return http.post<ApiResponse<ReceiptRecordItem>>(`/v1/enrollment/orders/${id}/refund`, data);
}

export function deleteEnrollmentOrder(id: string | number) {
  return http.delete(`/v1/enrollment/orders/${id}`);
}

export function forceVoidEnrollmentOrder(id: string | number, reason?: string) {
  return http.post(`/v1/enrollment/orders/${id}/force-void`, { reason });
}

// ─── Registration Intake ──────────────────────────────
export interface RegistrationStudentPayload {
  studentId?: EntityId;
  studentName: string;
  englishName?: string;
  gender?: string;
  birthDate?: string;
  grade?: string;
  studentClass?: string;
  school?: string;
  sourceChannel?: string;
  guardianName?: string;
  guardianPhone?: string;
  guardianPhone2?: string;
  guardianEmail?: string;
  guardianRelationType?: string;
  pickupRequired?: boolean;
  pickupAddress?: string;
  dropoffRequired?: boolean;
  dropoffAddress?: string;
}

export interface RegistrationCourseItemPayload {
  orderItemId?: EntityId;
  lineNo?: number;
  registerClass?: string;
  courseType?: string;
  courseProductId?: EntityId;
  coursePriceOptionId?: EntityId;
  classId?: EntityId;
  detail?: string;
  gradeBand?: string;
  periodLabel?: string;
  timeSlotLabel?: string;
  startDate?: string;
  packageUnit?: string;
  packageQuantity?: number;
  packageTotalPrice?: number;
  attendanceOption?: string;
  attendanceScheduleJson?: string;
  priceAmount?: number;
  originalPriceAmount?: number;
  manualPriceOverride?: boolean;
  oneOnOneHours?: number;
  openingUsedHours?: number;
  oneOnOneUnits?: number;
  teacherId?: EntityId;
  sessions?: number;
  purchasedMonths?: number;
  startMonth?: string;
  endMonth?: string;
  remark?: string;
}

export interface RegistrationFeeLinePayload {
  label: string;
  amount: number;
  note?: string;
}

export interface RegistrationPaymentPayload {
  paymentMethod: string;
  amount: number;
  paidAt?: string;
  participantIndex?: number;
  handledByUserId?: EntityId;
  handledByName?: string;
  remark?: string;
}

export interface RegistrationParticipantBillingPayload {
  participantIndex: number;
  discounts?: RegistrationFeeLinePayload[];
  discountAmount?: number;
  payments?: RegistrationPaymentPayload[];
}

export interface RegistrationReceiptOptionsPayload {
  issueReceipt?: boolean;
  templateCode?: string;
  showTaxId?: boolean;
  taxId?: string;
  campusAddress?: string;
  campusPhone?: string;
  brandName?: string;
  logoUrl?: string;
  zelleQrCodeUrl?: string;
  policyText?: string;
}

export interface RegistrationIntakePayload {
  student: RegistrationStudentPayload;
  participants?: RegistrationStudentPayload[];
  campusId?: EntityId;
  campusName?: string;
  signupDate?: string;
  periodStartDate?: string;
  periodEndDate?: string;
  renewPackageId?: EntityId;
  renewClassEnrollmentId?: EntityId;
  cashierUserId?: EntityId;
  handledByName: string;
  currencyCode?: string;
  items: RegistrationCourseItemPayload[];
  pickupFeeCharged?: boolean;
  pickupFeeAmount?: number;
  dropoffFeeCharged?: boolean;
  dropoffFeeAmount?: number;
  extraFeeAmount?: number;
  extraFees?: RegistrationFeeLinePayload[];
  discountAmount?: number;
  discounts?: RegistrationFeeLinePayload[];
  payments?: RegistrationPaymentPayload[];
  participantBillings?: RegistrationParticipantBillingPayload[];
  receiptOptions?: RegistrationReceiptOptionsPayload;
  remark?: string;
}

export interface RegistrationAccountResult {
  userId: EntityId;
  username: string;
  displayName: string;
  userType: string;
  created: boolean;
  initialPassword?: string;
}

export interface RegistrationCourseArtifactResult {
  orderItemId: EntityId;
  lineNo: number;
  courseType?: string;
  packageId?: EntityId;
  packageNo?: string;
  classEnrollmentId?: EntityId;
  classId?: EntityId;
}

export interface RegistrationPaymentResult {
  paymentId: EntityId;
  paymentNo: string;
  participantIndex?: number;
  paymentMethod: string;
  amount: number;
  paidAt?: string;
  handledByName?: string;
  remark?: string;
}

export interface RegistrationIntakeResult {
  enrollmentOrderId: EntityId;
  enrollmentNo: string;
  studentId: EntityId;
  studentNo: string;
  studentName: string;
  studentReused: boolean;
  studentAccount?: RegistrationAccountResult;
  privateLessonGroupId?: EntityId;
  participants?: Array<{
    studentId: EntityId;
    studentNo: string;
    studentName: string;
    studentReused: boolean;
    packageId?: EntityId;
    packageNo?: string;
    priceAmount?: number;
    studentAccount?: RegistrationAccountResult;
  }>;
  teacherAccounts: RegistrationAccountResult[];
  courseArtifacts: RegistrationCourseArtifactResult[];
  payments: RegistrationPaymentResult[];
  receiptId?: EntityId;
  receiptNo?: string;
  receiptPreviewData?: Record<string, unknown>;
  amountDue: number;
  paidAmount: number;
  balanceAmount: number;
  paymentStatus: string;
}

export function submitRegistrationIntake(data: RegistrationIntakePayload) {
  return http.post<ApiResponse<RegistrationIntakeResult>>('/v1/registration/intake', data);
}

// ─── Course Packages ─────────────────────────────────
export interface CoursePackageQuery {
  keyword?: string;
  studentId?: string | number;
  courseProductId?: string | number;
  packageStatus?: string;
  teacherId?: EntityId;
  teachingMode?: string;
  sortBy?: string;
  sortOrder?: string;
  pageNum?: number;
  pageSize?: number;
}

export function fetchCoursePackages(params: CoursePackageQuery = {}) {
  return http.get<ApiResponse<PageResult<CoursePackageItem>>>('/v1/enrollment/packages', { params });
}

export function fetchMyCoursePackages(params: Omit<CoursePackageQuery, 'studentId'> = {}) {
  return http.get<ApiResponse<PageResult<CoursePackageItem>>>('/v1/enrollment/packages/my', { params });
}

export function fetchGroupClassPackageProgress(params: CoursePackageQuery = {}) {
  return http.get<ApiResponse<PageResult<GroupClassPackageProgressItem>>>('/v1/enrollment/packages/group-classes', { params });
}

export function fetchGroupClassStudentProgress(classId: string | number) {
  return http.get<ApiResponse<GroupClassStudentProgressItem[]>>(`/v1/enrollment/packages/group-classes/${classId}/students`);
}

export function fetchCoursePackage(id: string | number) {
  return http.get<ApiResponse<CoursePackageItem>>(`/v1/enrollment/packages/${id}`);
}

export function closeCoursePackage(id: string | number) {
  return http.post(`/v1/enrollment/packages/${id}/close`);
}

export function pauseCoursePackage(id: string | number) {
  return http.post(`/v1/enrollment/packages/${id}/pause`);
}

export interface CoursePackageResumePayload {
  startDate: string;
}

export function resumeCoursePackage(id: string | number, data: CoursePackageResumePayload) {
  return http.post(`/v1/enrollment/packages/${id}/resume`, data);
}

export interface CoursePackageHoursAdjustmentPayload {
  changeHours: number;
  adjustmentType: 'BONUS_HOURS' | 'MANUAL_CORRECTION';
  reason?: string;
}

export function adjustCoursePackageHours(id: EntityId, data: CoursePackageHoursAdjustmentPayload) {
  return http.post(`/v1/enrollment/packages/${id}/adjust-hours`, data);
}

// ─── Payment Records ─────────────────────────────────
export interface PaymentSplit {
  paymentMethod: string;
  amount: number;
  paidAt?: string;
  participantIndex?: number;
  handledByName?: string;
  remark?: string;
}

export interface PaymentRecordSavePayload {
  enrollmentOrderId: EntityId;
  splits: PaymentSplit[];
  paidAt?: string;
  handledByUserId?: EntityId;
  handledByName?: string;
  remark?: string;
}

export function createPaymentRecord(data: PaymentRecordSavePayload) {
  return http.post<ApiResponse<ReceiptRecordItem>>('/v1/enrollment/payments', data);
}

// ─── Payment Allocation（课程级付款分摊）────────────────
export interface PaymentAllocationPreview {
  enrollmentOrderItemId: EntityId;
  courseName: string | null;
  itemAmountDue: number;
  itemPaidAmount: number;
  itemBalanceAmount: number;
  allocatedAmount: number;
}

export function previewPaymentAllocation(orderId: EntityId, amount: number) {
  return http.post<ApiResponse<PaymentAllocationPreview[]>>(
    `/v1/enrollment/payments/order/${orderId}/allocation-preview`,
    { amount },
  );
}

// ─── Payment Methods ─────────────────────────────────
export function fetchPaymentMethods() {
  return http.get<ApiResponse<PaymentMethodItem[]>>('/v1/enrollment/payment-methods');
}

export function fetchActivePaymentMethods() {
  return http.get<ApiResponse<PaymentMethodItem[]>>('/v1/enrollment/payment-methods/active');
}

export interface PaymentMethodSavePayload {
  methodCode: string;
  methodName: string;
  sortOrder?: number;
  status?: string;
}

export function createPaymentMethod(data: PaymentMethodSavePayload) {
  return http.post('/v1/enrollment/payment-methods', data);
}

export function updatePaymentMethod(id: EntityId, data: PaymentMethodSavePayload) {
  return http.put(`/v1/enrollment/payment-methods/${id}`, data);
}

export function deletePaymentMethod(id: EntityId) {
  return http.delete(`/v1/enrollment/payment-methods/${id}`);
}

// ─── Receipt Records ─────────────────────────────────
export interface ReceiptQuery {
  keyword?: string;
  studentId?: EntityId;
  enrollmentOrderId?: EntityId;
  templateCode?: string;
  pageNum?: number;
  pageSize?: number;
}

export function fetchReceipts(params: ReceiptQuery = {}) {
  return http.get<ApiResponse<PageResult<ReceiptRecordItem>>>('/v1/enrollment/receipts', { params });
}

export function fetchReceipt(id: EntityId) {
  return http.get<ApiResponse<ReceiptRecordItem>>(`/v1/enrollment/receipts/${id}`);
}
