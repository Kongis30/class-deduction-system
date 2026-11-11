import http from './http';
import type { AxiosRequestConfig } from 'axios';
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
  teachingMode?: 'ONE_ON_ONE' | 'GROUP';
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

export interface ParticipantCourseAmountItem {
  orderItemId: EntityId;
  priceAmount: number;
  amountDue: number;
  paidAmount: number;
  balanceAmount: number;
}

export function fetchParticipantCourseAmounts(orderId: EntityId, participantIndex: number) {
  return http.get<ApiResponse<ParticipantCourseAmountItem[]>>(
    `/v1/enrollment/orders/${orderId}/participants/${participantIndex}/course-amounts`,
  );
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

export function updateCoursePackageDeductedHours(id: EntityId, deductedHours: number) {
  return http.put(`/v1/enrollment/packages/${id}/deducted-hours`, { deductedHours });
}

/** 保存管理员看到的起始课时，版本变化时要求先刷新。 */
export function updateCoursePackageStartingHours(id: EntityId, startingHours: number, expectedVersion: number) {
  return http.put(`/v1/enrollment/packages/${id}/starting-hours`, { startingHours, expectedVersion });
}

export interface EnrollmentOrderCorrectionPayload {
  expectedVersion?: number;
  /**
   * 死快照行（订单行已软删但收据快照仍保留）的行号列表。
   * 后端按行号把这些行从收据快照中移除；订单金额不受影响。
   */
  removedReceiptItemLineNos?: number[];
  /**
   * 死快照行的原订单行 ID（字符串）：老快照行可能缺 lineNo，按行号定位不到时
   * 后端用快照行的 orderItemId/enrollmentOrderItemId 字符串匹配移除。
   */
  removedReceiptItemOrderItemIds?: string[];
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
  orderItemId?: EntityId;
  participantIndex?: number;
  paymentMethod: string;
  handledByName: string;
  handledByUserId?: string | number;
  remark?: string;
  disposition?: 'REFUND' | 'CREDIT';
  endCourse?: boolean;
}

export function endEnrollmentCourse(id: string | number, orderItemId: string | number, studentId: string | number) {
  return http.post<ApiResponse<void>>(`/v1/enrollment/orders/${id}/end-course`, { orderItemId, studentId });
}

export interface RefundCoursePreviewItem {
  orderItemId: EntityId;
  lineNo: number;
  detail?: string;
  courseType?: string;
  classId?: EntityId;
  paidAmount: number;
  consumedLessonCount: number;
  consumedAmount: number;
  automaticStopSupported: boolean;
  studentName?: string;
}

export interface RefundCoursePreview {
  items: RefundCoursePreviewItem[];
  unassignedPaymentAmount: number;
}

export function fetchRefundPreview(id: string | number, participantIndex?: number) {
  return http.get<ApiResponse<RefundCoursePreview>>(
    `/v1/enrollment/orders/${id}/refund-preview`,
    { params: participantIndex == null ? undefined : { participantIndex } },
  );
}

export function refundEnrollmentOrder(id: string | number, data: RefundPayload) {
  return http.post<ApiResponse<ReceiptRecordItem>>(`/v1/enrollment/orders/${id}/refund`, data);
}

export function deleteEnrollmentOrderItem(orderId: string | number, itemId: string | number) {
  return http.delete(`/v1/enrollment/orders/${orderId}/items/${itemId}`);
}

export function deleteCreditTopUpReceipt(orderId: string | number, receiptId: string | number, expectedCreditBalance: string) {
  return http.delete<ApiResponse<void>>(`/v1/enrollment/orders/${orderId}/credit-top-up-receipt`, {
    params: { receiptId, expectedCreditBalance },
  });
}

export function forceVoidEnrollmentOrder(id: string | number, reason?: string) {
  return http.post(`/v1/enrollment/orders/${id}/force-void`, { reason });
}

// ─── Registration Intake ──────────────────────────────
export interface RegistrationStudentPayload {
  studentId?: EntityId;
  correctionChangedFields?: string[];
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
  renewalSourceOrderItemId?: EntityId;
  correctionChangedFields?: string[];
  lineNo?: number;
  registerClass?: string;
  courseType?: string;
  courseProductId?: EntityId;
  coursePriceOptionId?: EntityId;
  groupProjectId?: EntityId;
  groupRegistrationPlanId?: EntityId;
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
  discountAmount?: number;
  oneOnOneHours?: number;
  openingUsedHours?: number;
  oneOnOneUnits?: number;
  teacherId?: EntityId;
  pickupRequired?: boolean;
  pickupAddress?: string;
  dropoffRequired?: boolean;
  dropoffAddress?: string;
  pickupFeeCharged?: boolean;
  pickupFeeAmount?: number;
  dropoffFeeCharged?: boolean;
  dropoffFeeAmount?: number;
  serviceFees?: RegistrationFeeLinePayload[];
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
  orderItemLineNo?: number;
}

export interface RegistrationCoursePaymentAllocationPayload {
  orderItemLineNo: number;
  amount: number;
}

export interface RegistrationPaymentPayload {
  paymentId?: EntityId;
  correctionChangedFields?: string[];
  paymentMethod: string;
  amount: number;
  paidAt?: string;
  creditTopUp?: boolean;
  participantIndex?: number;
  handledByUserId?: EntityId;
  handledByName?: string;
  remark?: string;
  allocations?: RegistrationCoursePaymentAllocationPayload[];
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
  creditTopUpAmount?: number;
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
  /** 用户确认班课冲突后重新提交时置 true，允许报名继续。 */
  ignoreStudentConflicts?: boolean;
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
  creditTopUpOrderId?: EntityId;
  creditTopUpAmount?: number;
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
  packageStatuses?: string;
  teacherId?: EntityId;
  teachingMode?: string;
  renewalDueWithinDays?: number;
  includeScheduleSummary?: boolean;
  prioritizeLowBalance?: boolean;
  sortBy?: string;
  sortOrder?: string;
  pageNum?: number;
  pageSize?: number;
}

export function fetchCoursePackages(
  params: CoursePackageQuery = {},
  config: Pick<AxiosRequestConfig, 'signal' | 'timeout'> = {},
) {
  return http.get<ApiResponse<PageResult<CoursePackageItem>>>('/v1/enrollment/packages', { params, ...config });
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

export function setCoursePackageRenewalPriority(id: string | number, pinned: boolean) {
  return http.put(`/v1/enrollment/packages/${id}/renewal-priority`, { pinned });
}

export function pauseCoursePackage(id: string | number) {
  return http.post(`/v1/enrollment/packages/${id}/pause`);
}

export function resumeCoursePackage(id: string | number) {
  return http.post(`/v1/enrollment/packages/${id}/resume`);
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
  allocations?: Array<{
    enrollmentOrderItemId: EntityId;
    amount: number;
  }>;
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

export function deleteRefundReceipt(id: EntityId) {
  return http.delete<ApiResponse<void>>(`/v1/enrollment/receipts/${id}`);
}
