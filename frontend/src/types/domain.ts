export type EntityId = string;

export interface DashboardSummary {
  totalStudents: number;
  totalTeachers: number;
  totalClassrooms: number;
  totalCampuses: number;
  totalCourseProducts: number;
  todayLessonsCount: number;
  lowBalancePackages: number;
  campusDetails: CampusDetail[];
}

export interface CampusDetail {
  campusId: EntityId;
  campusName: string;
  students: number;
  teachers: number;
  classrooms: number;
  todayLessons: number;
  lowBalancePackages: number;
}

// ─── Discount Presets ──────────────────────────────────

export interface DiscountPresetItem {
  id: EntityId;
  presetName: string;
  discountType: 'PERCENTAGE' | 'FIXED';
  discountValue: number;
  sortOrder: number;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}

// ─── Base Data ─────────────────────────────────────────

export interface StudentItem {
  id: EntityId;
  studentNo: string;
  studentName: string;
  englishName?: string;
  gender: string;
  birthDate?: string;
  grade?: string;
  studentClass?: string;
  school?: string;
  sourceChannel?: string;
  guardianName: string;
  phone: string;
  guardianPhone?: string;
  guardianPhone2?: string;
  guardianEmail?: string;
  guardianRelationType?: string;
  pickupRequired?: boolean;
  pickupAddress?: string;
  dropoffRequired?: boolean;
  dropoffAddress?: string;
  status: string;
  campusId?: EntityId;
  campusName?: string;
  latestSignupDate?: string;
  enrollmentCount?: number;
  receiptCount?: number;
  enrollmentAmountDue?: number;
  enrollmentPaidAmount?: number;
  enrollmentBalanceAmount?: number;
  aggregatePaymentStatus?: string;
  hasEnrollmentOrder?: boolean;
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TeacherItem {
  id: EntityId;
  teacherNo: string;
  teacherName: string;
  mobile: string;
  email: string;
  subjectTags: string[];
  status: string;
  campusId?: EntityId;
  notes?: string;
}

export interface ClassroomItem {
  id: EntityId;
  roomCode: string;
  roomName: string;
  campus: string;
  campusId?: EntityId;
  capacity: number;
  status: string;
  notes?: string;
}

export interface SubjectItem {
  id: EntityId;
  subjectName: string;
  displayOrder: number;
  status: string;
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CampusItem {
  id: EntityId;
  campusName: string;
  displayOrder: number;
  status: string;
  defaultPickupFeeAmount?: number;
  defaultDropoffFeeAmount?: number;
  notes?: string;
}

export interface CoursePriceOptionItem {
  id: EntityId;
  optionCode: string;
  courseType: string;
  registerClass: string;
  gradeBand?: string;
  periodLabel?: string;
  durationLabel?: string;
  timeSlotLabel?: string;
  detailText: string;
  packageUnit?: 'MONTH' | 'WEEK' | 'SESSION' | 'HOUR' | string;
  packageQuantity?: number;
  totalPrice?: number;
  derivedUnitPrice?: number;
  fullDayPrice?: number;
  halfDayPrice?: number;
  oneOnOneHours?: number;
  priceAmount: number;
  currencyCode: string;
  courseProductId?: EntityId;
  classId?: EntityId;
  actualClasses?: Array<{
    id: EntityId;
    className: string;
    actualClass?: string;
    teacherId?: EntityId;
    teacherName?: string;
    classroomId?: EntityId;
    classroomName?: string;
    scheduleDesc?: string;
    campusId?: EntityId;
    campusName?: string;
  }>;
  groupTeacherId?: EntityId;
  groupClassroomId?: EntityId;
  groupScheduleSlots?: Array<{
    teacherId?: EntityId;
    subjectName?: string;
    alternateTeacherId?: EntityId;
    alternateSubjectName?: string;
    alternateClassroomId?: EntityId;
    alternateDeliveryMode?: 'IN_PERSON' | 'ONLINE' | string;
    rotationAnchorDate?: string;
    weekday: number;
    startTime: string;
    endTime: string;
    morningEndTime?: string;
    afternoonStartTime?: string;
    attendanceOptions?: string;
  }>;
  groupBillingType?: string;
  groupStartDate?: string;
  groupEndDate?: string;
  groupTotalSessions?: number;
  groupTotalPrice?: number;
  groupSessionPrice?: number;
  groupStartMonth?: string;
  groupEndMonth?: string;
  sortOrder: number;
  status: string;
  notes?: string;
  referenced?: boolean;
  referenceCount?: number;
  referenceSources?: string[];
  createdAt?: string;
  updatedAt?: string;
}

export interface CourseProductItem {
  id: EntityId;
  productCode: string;
  productName: string;
  courseType?: string;
  registerClass?: string;
  subjectName?: string;
  teachingMode: string;
  sortOrder?: number;
  status: string;
  notes?: string;
  referenced?: boolean;
  referenceCount?: number;
  referenceSources?: string[];
}

// ─── Enrollment ────────────────────────────────────────

export interface EnrollmentOrderItem {
  id: EntityId;
  /** 原报名单 ID；同一报名单的不同课程项会分别出现在对应 Tab。 */
  orderItemId?: EntityId;
  enrollmentNo: string;
  studentId: EntityId;
  studentName: string;
  classId?: EntityId;
  className?: string;
  courseProductId?: EntityId;
  courseProductName?: string;
  courseType?: string;
  campusId?: EntityId;
  campusNameSnapshot?: string;
  handledByName?: string;
  signupDate?: string;
  periodStartDate?: string;
  periodEndDate?: string;
  studentNameSnapshot?: string;
  studentClassSnapshot?: string;
  gradeSnapshot?: string;
  schoolSnapshot?: string;
  parentPhoneSnapshot?: string;
  parentPhone2Snapshot?: string;
  pickupRequiredSnapshot?: boolean;
  pickupAddressSnapshot?: string;
  dropoffRequiredSnapshot?: boolean;
  dropoffAddressSnapshot?: string;
  currencyCode?: string;
  billingPolicy?: string;
  tuitionSubtotalAmount?: number;
  pickupFeeAmount?: number;
  dropoffFeeAmount?: number;
  extraFeeAmount?: number;
  originalAmount: number;
  discountAmount: number;
  receivedAmount: number;
  amountDue?: number;
  balanceAmount?: number;
  paymentStatus: string;
  orderStatus: string;
  packageCount?: number;
  items?: EnrollmentOrderLineItem[];
  participants?: EnrollmentOrderParticipantItem[];
  participantIndex?: number;
  createdAt: string;
}

export interface EnrollmentOrderDetailItem {
  id: EntityId;
  enrollmentNo: string;
  studentId: EntityId;
  studentNo: string;
  studentName: string;
  classId?: EntityId;
  className?: string;
  campusId?: EntityId;
  campusNameSnapshot?: string;
  orderStatus: string;
  cashierUserId?: EntityId;
  handledByName?: string;
  signupDate?: string;
  periodStartDate?: string;
  periodEndDate?: string;
  studentNameSnapshot?: string;
  studentClassSnapshot?: string;
  gradeSnapshot?: string;
  schoolSnapshot?: string;
  parentPhoneSnapshot?: string;
  parentPhone2Snapshot?: string;
  pickupRequiredSnapshot?: boolean;
  pickupAddressSnapshot?: string;
  dropoffRequiredSnapshot?: boolean;
  dropoffAddressSnapshot?: string;
  currencyCode?: string;
  billingPolicy?: string;
  tuitionSubtotalAmount?: number;
  pickupFeeAmount?: number;
  dropoffFeeAmount?: number;
  extraFeeAmount?: number;
  extraFeeDetail?: string;
  originalAmount: number;
  discountAmount: number;
  discountDetail?: string;
  receivedAmount: number;
  amountDue?: number;
  balanceAmount?: number;
  paymentStatus: string;
  remark?: string;
  items: EnrollmentOrderLineItem[];
  participants?: EnrollmentOrderParticipantItem[];
  payments: PaymentRecordItem[];
  packages: CoursePackageItem[];
  receipts: ReceiptRecordItem[];
  createdAt: string;
  updatedAt?: string;
}

export interface EnrollmentOrderParticipantItem {
  id: EntityId;
  studentId: EntityId;
  participantIndex: number;
  studentName: string;
  studentNo?: string;
  grade?: string;
  school?: string;
  studentClass?: string;
  guardianName?: string;
  guardianPhone?: string;
  guardianPhone2?: string;
  guardianEmail?: string;
  pickupRequired?: boolean;
  pickupAddress?: string;
  dropoffRequired?: boolean;
  dropoffAddress?: string;
  grossAmount?: number;
  discountAmount?: number;
  discountDetail?: string;
  amountDue?: number;
  paidAmount?: number;
  balanceAmount?: number;
}

export interface EnrollmentOrderLineItem {
  id: EntityId;
  enrollmentOrderId: EntityId;
  lineNo: number;
  registerClass?: string;
  courseType?: string;
  courseProductId?: EntityId;
  coursePriceOptionId?: EntityId;
  groupProjectId?: EntityId;
  groupRegistrationPlanId?: EntityId;
  classId?: EntityId;
  className?: string;
  detail?: string;
  gradeBand?: string;
  periodLabel?: string;
  timeSlotLabel?: string;
  startDate?: string;
  /** 学生实际购买的 SESSION 次数；packageQuantity 是定价选项上限。 */
  sessions?: number;
  purchasedMonths?: number;
  packageUnit?: string;
  packageQuantity?: number;
  packageTotalPrice?: number;
  attendanceOption?: string;
  attendanceScheduleJson?: string;
  priceAmount?: number;
  originalPriceAmount?: number;
  manualPriceOverride?: boolean;
  discountAmount?: number;
  amountDue?: number;
  paidAmount?: number;
  balanceAmount?: number;
  oneOnOneHours?: number;
  oneOnOneUnits?: number;
  pickupRequiredSnapshot?: boolean;
  pickupAddressSnapshot?: string;
  dropoffRequiredSnapshot?: boolean;
  dropoffAddressSnapshot?: string;
  pickupFeeAmount?: number;
  dropoffFeeAmount?: number;
  extraFeeAmount?: number;
  extraFeeDetail?: string;
  itemStatus?: string;
  stoppedConsumedAmount?: number;
  stoppedSessionCount?: number;
  stoppedAt?: string;
  remark?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CoursePackageItem {
  id: EntityId;
  packageNo: string;
  enrollmentOrderId: EntityId;
  studentId: EntityId;
  studentName: string;
  courseProductId: EntityId;
  courseProductName?: string;
  coursePriceOptionId?: EntityId;
  gradeBand?: string;
  packageName: string;
  subjectName?: string;
  teachingMode?: string;
  purchasedHours?: number;
  totalUnits: number;
  startingHours: number;
  consumedUnits: number;
  openingUsedHours?: number;
  pendingDeductionUnits?: number;
  pendingAdminDeductionUnits?: number;
  remainingUnits: number;
  unresolvedPastHours?: number;
  receivedAmount: number;
  packageStatus: string;
  scheduleStatus?: 'Scheduled' | 'Needs setup' | 'Incomplete' | 'Paused';
  nextLessonStartAt?: string;
  startDate?: string;
  estimatedEndDate?: string;
  privateLessonGroupId?: EntityId;
  privateLessonParticipantCount?: number;
  privateLessonParticipants?: Array<{
    studentId: EntityId;
    studentName?: string;
    studentNo?: string;
    grade?: string;
    school?: string;
    packageId: EntityId;
    participantIndex: number;
    priceMultiplier?: number;
    memberStatus?: string;
  }>;
  primaryTeacherName?: string;
  teacherAssignments?: PackageTeacherAssignmentItem[];
  createdAt: string;
}

export interface PackageTeacherAssignmentItem {
  id: EntityId;
  teacherId: EntityId;
  teacherNo?: string;
  teacherName?: string;
  subjectName?: string;
  allocatedHours?: number;
  consumedHours?: number;
  remainingHours?: number;
  primary: boolean;
  assignmentStatus: string;
  effectiveFrom?: string;
  effectiveTo?: string;
  changeReason?: string;
}

export interface GroupClassPackageProgressItem {
  classId: EntityId;
  productId?: EntityId;
  className: string;
  productName?: string;
  subjectName?: string;
  billingType?: string;
  scheduleDesc?: string;
  teacherName?: string;
  totalUnits: number;
  consumedUnits: number;
  remainingUnits: number;
  activeStudentCount: number;
  status: string;
  startDate?: string;
  estimatedEndDate?: string;
}

export interface GroupClassStudentProgressItem {
  enrollmentId: EntityId;
  classId: EntityId;
  studentId: EntityId;
  studentName: string;
  billingType?: string;
  totalUnits: number;
  consumedUnits: number;
  remainingUnits: number;
  enrollmentDate?: string;
  expiryDate?: string;
  enrollmentStatus: string;
}

export interface PaymentMethodItem {
  id: EntityId;
  methodCode: string;
  methodName: string;
  sortOrder: number;
  status: string;
}

export interface PaymentRecordItem {
  id: EntityId;
  paymentNo: string;
  participantIndex?: number;
  paymentMethod: string;
  paymentType?: string;
  paymentStatus: string;
  amount: number;
  paidAt: string;
  handledByName?: string;
  remark?: string;
  allocations?: Array<{
    enrollmentOrderItemId: EntityId;
    amount: number;
  }>;
}

export interface ReceiptRecordItem {
  id: EntityId;
  receiptNo: string;
  enrollmentOrderId: EntityId;
  studentId: EntityId;
  paymentRecordId?: EntityId;
  enrollmentOrderActive?: boolean;
  activeCourseItemIds?: EntityId[];
  studentName: string;
  templateCode?: string;
  receiptPayload?: Record<string, unknown> | string | null;
  fileUrl?: string;
  issuedAt: string;
}

// ─── Schedule ──────────────────────────────────────────

export interface RecurringScheduleRuleItem {
  id: EntityId;
  packageId: EntityId;
  packageNo: string;
  studentId: EntityId;
  studentName: string;
  defaultClassroomId?: EntityId;
  defaultClassroomName?: string;
  ruleStatus: string;
  startDate: string;
  endDate?: string;
  slotCount: number;
  generatedOccurrenceCount: number;
  nextLessonStartAt?: string;
  createdAt: string;
}

export interface RecurringScheduleSlotItem {
  id: EntityId;
  teacherId?: EntityId;
  deliveryMode?: 'IN_PERSON' | 'ONLINE' | string;
  classroomId?: EntityId;
  subjectName?: string;
  weekday: number;
  startTime: string;
  endTime: string;
  plannedUnits: number;
  allocatedUnits?: number;
  rolloverAllocatedUnits?: number;
  slotStatus: string;
}

export interface RecurringScheduleRuleDetailItem extends RecurringScheduleRuleItem {
  packageName?: string;
  notes?: string;
  deductedHours?: number;
  pendingHours?: number;
  otherReservedHours?: number;
  preservedReservedHours?: number;
  startingHours?: number;
  slots: RecurringScheduleSlotItem[];
  createdAt: string;
  updatedAt?: string;
}

export interface LessonOccurrenceItem {
  id: EntityId;
  lessonRecordId?: EntityId;
  recordId?: EntityId;
  lessonNo: string;
  packageId: EntityId;
  packageNo: string;
  packageName?: string;
  productName?: string;
  classGroupId?: EntityId;
  className?: string;
  subjectName?: string;
  studentId: EntityId;
  studentName: string;
  participantNames?: string[];
  scheduleRuleId?: EntityId;
  scheduleSlotId?: EntityId;
  occurrenceType: string;
  plannedTeacherId?: EntityId;
  plannedTeacherName?: string;
  deliveryMode?: 'IN_PERSON' | 'ONLINE' | string;
  plannedClassroomId?: EntityId;
  plannedClassroomName?: string;
  plannedStartAt: string;
  plannedEndAt: string;
  plannedUnits: number;
  planningStatus: string;
  sourceOccurrenceId?: EntityId;
  conflictChecked: boolean;
  notes?: string;
  resultStatus?: string;
  resultStatusName?: string;
  confirmStage?: 'PENDING_ADMIN' | 'CONFIRMED' | 'REJECTED' | string;
  confirmedAt?: string;
  confirmedByUserId?: EntityId;
  teacherConfirmedAt?: string;
  teacherConfirmedByUserId?: EntityId;
  adminConfirmedAt?: string;
  adminConfirmedByUserId?: EntityId;
  rejectReason?: string;
  rejectedAt?: string;
  rejectedByUserId?: EntityId;
  actualTeacherId?: EntityId;
  actualTeacherName?: string;
  actualClassroomId?: EntityId;
  actualClassroomName?: string;
  actualStartAt?: string;
  actualEndAt?: string;
  actualUnits?: number;
  deductedUnits?: number;
  shouldDeduct?: 0 | 1;
  privateLessonGroupId?: EntityId;
}

export interface ClassroomOccupancyItem {
  occurrenceId: EntityId;
  lessonNo: string;
  classroomId: EntityId;
  classroomName: string;
  teacherId?: EntityId;
  teacherName?: string;
  packageId: EntityId;
  packageNo: string;
  packageName?: string;
  studentId?: EntityId;
  studentName?: string;
  plannedStartAt: string;
  plannedEndAt: string;
  plannedUnits: number;
  planningStatus: string;
  classGroupId?: EntityId;
  className?: string;
  subjectName?: string;
}

export interface PendingClassroomAssignmentItem {
  targetType: 'CLASS_GROUP' | 'OCCURRENCE' | string;
  targetId: EntityId;
  occurrenceId?: EntityId;
  lessonNo?: string;
  classGroupId?: EntityId;
  productId?: EntityId;
  productName?: string;
  className?: string;
  gradeBand?: string;
  actualClass?: string;
  packageId?: EntityId;
  packageName?: string;
  studentId?: EntityId;
  studentName?: string;
  teacherId?: EntityId;
  teacherName?: string;
  plannedStartAt?: string;
  plannedEndAt?: string;
  plannedUnits?: number;
  scheduleDesc?: string;
}

// ─── Class Group ─────────────────────────────────────────

export interface ClassGroupItem {
  id: EntityId;
  className: string;
  description?: string;
  productId?: EntityId;
  groupProjectId?: EntityId;
  groupProjectName?: string;
  productName?: string;
  registerClass?: string;
  courseType?: string;
  gradeBand?: string;
  actualClass?: string;
  scheduleDesc?: string;
  teacherId?: EntityId;
  teacherName?: string;
  classroomId?: EntityId;
  classroomName?: string;
  campusId?: EntityId;
  campusName?: string;
  startDate?: string;
  endDate?: string;
  activeStudentCount: number;
  scheduleSlots?: Array<{
    teacherId?: EntityId;
    classroomId?: EntityId;
    deliveryMode?: 'IN_PERSON' | 'ONLINE' | string;
    subjectName?: string;
    alternateTeacherId?: EntityId;
    alternateSubjectName?: string;
    alternateClassroomId?: EntityId;
    alternateDeliveryMode?: 'IN_PERSON' | 'ONLINE' | string;
    rotationAnchorDate?: string;
    weekday: number;
    startTime: string;
    endTime: string;
    plannedUnits?: number;
  }>;
}

export interface ClassEnrolledStudentItem {
  studentId: EntityId;
  studentName: string;
  enrollmentDate: string;
  expiryDate: string;
  billingType?: 'PER_SESSION' | 'MONTHLY' | string;
  purchasedQuantity?: number;
  usedSessions?: number;
  remainingSessions?: number;
  enrollmentStatus: string;
  expiryStatus: 'EXPIRED' | 'EXPIRING_SOON' | 'NORMAL';
}

export interface ClassOccurrenceItem {
  id: EntityId;
  lessonRecordId?: EntityId;
  recordId?: EntityId;
  lessonNo: string;
  occurrenceType: string;
  subjectName?: string;
  plannedTeacherId?: EntityId;
  plannedTeacherName?: string;
  deliveryMode?: 'IN_PERSON' | 'ONLINE' | string;
  plannedClassroomId?: EntityId;
  plannedClassroomName?: string;
  plannedStartAt: string;
  plannedEndAt: string;
  plannedUnits: number;
  planningStatus: string;
  resultStatus?: string;
  confirmed: boolean;
  confirmStage?: 'PENDING_ADMIN' | 'CONFIRMED' | 'REJECTED' | string;
  confirmedAt?: string;
  confirmedByUserId?: EntityId;
  teacherConfirmedAt?: string;
  teacherConfirmedByUserId?: EntityId;
  adminConfirmedAt?: string;
  adminConfirmedByUserId?: EntityId;
  rejectReason?: string;
  rejectedAt?: string;
  rejectedByUserId?: EntityId;
  actualTeacherId?: EntityId;
  actualTeacherName?: string;
  actualClassroomId?: EntityId;
  actualClassroomName?: string;
  actualStartAt?: string;
  actualEndAt?: string;
  actualUnits?: number;
  notes?: string;
}

// ─── Lesson ─────────────────────────────────────────────

export interface LessonRecordItem {
  id: EntityId;
  occurrenceId: EntityId;
  lessonNo: string;
  packageId: EntityId;
  packageNo: string;
  packageName?: string;
  studentId: EntityId;
  studentName: string;
  resultStatus: string;
  actualTeacherId?: EntityId;
  actualTeacherName?: string;
  actualClassroomId?: EntityId;
  actualClassroomName?: string;
  actualStartAt?: string;
  actualEndAt?: string;
  actualUnits: number;
  shouldDeduct: boolean;
  deductedUnits: number;
  shouldExtendEndDate: boolean;
  confirmStage?: 'PENDING_ADMIN' | 'CONFIRMED' | 'REJECTED' | string;
  teacherConfirmedAt?: string;
  teacherConfirmedByUserId?: EntityId;
  adminConfirmedAt?: string;
  adminConfirmedByUserId?: EntityId;
  rejectReason?: string;
  rejectedAt?: string;
  rejectedByUserId?: EntityId;
  recordSource?: string;
  confirmedAt?: string;
  notes?: string;
}

export interface LessonRecordDetailItem extends LessonRecordItem {
  plannedTeacherId?: EntityId;
  plannedTeacherName?: string;
  plannedClassroomId?: EntityId;
  plannedClassroomName?: string;
  confirmedByUserId?: EntityId;
}

export interface LessonRecordConfirmResult {
  lessonRecordId: EntityId;
  occurrenceId: EntityId;
  ledgerId: EntityId;
  deductedUnits: number;
  remainingUnits: number;
  packageCompleted: boolean;
}

// ─── System Config ─────────────────────────────────────

export interface BrandConfigItem {
  id: EntityId;
  brandName: string;
  logoUrl?: string;
  themeColor?: string;
  supportEmail?: string;
  receiptFooterText?: string;
  status: string;
}

export interface NotificationTemplateItem {
  id: EntityId;
  templateCode: string;
  templateName: string;
  channelType: string;
  subjectTemplate: string;
  bodyTemplate: string;
  status: string;
  remark?: string;
}

export interface NotificationConfigItem {
  id: EntityId;
  enabled: boolean;
  fromAddress: string;
  fromName: string;
  strictAllowlist: boolean;
  allowedRecipients: string;
  lowBalanceThreshold: number;
  lowBalanceRecipients: string;
  smtpHost: string;
  smtpPort: number;
  updatedAt?: string;
}

export interface NotificationTriggerRuleItem {
  id: EntityId;
  triggerType: string;
  triggerName: string;
  templateId: EntityId | null;
  templateName: string | null;
  enabled: boolean;
  recipientType: string;
  customRecipients: string;
  thresholdValue: number | null;
  remark?: string;
  updatedAt?: string;
}

// ─── Auth ──────────────────────────────────────────────

export interface TeacherAccountItem {
  userId: EntityId;
  teacherId: EntityId;
  teacherNo?: string;
  teacherName?: string;
  username: string;
  displayName?: string;
  mobile?: string;
  email?: string;
  status: string;
  lastLoginAt?: string;
  remark?: string;
}

export interface StudentAccountItem {
  userId: EntityId;
  studentId: EntityId;
  studentNo?: string;
  studentName?: string;
  username: string;
  displayName?: string;
  email?: string;
  status: string;
  lastLoginAt?: string;
  remark?: string;
}

// ─── Finance ────────────────────────────────────────────

export interface TeacherPayRateItem {
  id: EntityId;
  teacherId: EntityId;
  teacherNo: string;
  teacherName: string;
  courseProductId?: EntityId;
  productCode?: string;
  productName?: string;
  subjectName?: string;
  studentId?: EntityId;
  studentName?: string;
  gradeBand?: string;
  deliveryMode?: string;
  hourlyRate: number;
  notes?: string;
}

export interface UnpaidLessonItem {
  lessonRecordId: EntityId;
  lessonStartAt: string | null;
  lessonEndAt: string | null;
  studentId: EntityId;
  studentName: string;
  packageId: EntityId;
  packageName: string;
  courseProductId: EntityId;
  productName: string;
  subjectName: string;
  actualUnits: number;
  hours: number;
  hourlyRate: number;
  amount: number;
  resultStatus: string;
  occurrenceType?: string;
  className?: string;
  participantNames?: string[];
}

export interface TeacherPaymentItem {
  id: EntityId;
  paymentNo: string;
  teacherId: EntityId;
  teacherNo: string;
  teacherName: string;
  periodStart: string;
  periodEnd: string;
  totalUnits: number;
  totalHours: number;
  totalAmount: number;
  status: string;
  paidAt?: string;
  paidByName?: string;
  notes?: string;
  createdAt: string;
  items?: TeacherPaymentItemDetail[];
}

export interface TeacherPaymentItemDetail {
  id: EntityId;
  lessonRecordId: EntityId;
  packageId: EntityId;
  packageName: string;
  courseProductId: EntityId;
  productName: string;
  subjectName: string;
  actualUnits: number;
  hours: number;
  hourlyRate: number;
  amount: number;
  occurrenceType?: string;
  className?: string;
}
