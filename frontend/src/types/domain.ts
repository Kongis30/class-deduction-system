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
  campusId: number;
  campusName: string;
  students: number;
  teachers: number;
  classrooms: number;
  todayLessons: number;
  lowBalancePackages: number;
}

// ─── Discount Presets ──────────────────────────────────

export interface DiscountPresetItem {
  id: number;
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
  id: string;
  studentNo: string;
  studentName: string;
  englishName?: string;
  gender: string;
  birthDate?: string;
  sourceChannel?: string;
  guardianName: string;
  phone: string;
  guardianEmail?: string;
  guardianRelationType?: string;
  status: string;
  campusId?: number;
  notes?: string;
}

export interface TeacherItem {
  id: number;
  teacherNo: string;
  teacherName: string;
  mobile: string;
  email: string;
  subjectTags: string[];
  status: string;
  campusId?: number;
  notes?: string;
}

export interface ClassroomItem {
  id: number;
  roomCode: string;
  roomName: string;
  campus: string;
  campusId?: number;
  capacity: number;
  status: string;
  notes?: string;
}

export interface SubjectItem {
  id: number;
  subjectName: string;
  displayOrder: number;
  status: string;
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CampusItem {
  id: number;
  campusName: string;
  displayOrder: number;
  status: string;
  notes?: string;
}

export interface CourseProductItem {
  id: number;
  productCode: string;
  productName: string;
  subjectName?: string;
  teachingMode: string;
  defaultTotalUnits: number;
  defaultLessonUnits: number;
  expiryDurationDays?: number;
  defaultClassroomId?: number;
  defaultClassroomName?: string;
  defaultTeacherId?: number;
  defaultTeacherName?: string;
  listPrice: number;
  status: string;
  notes?: string;
  slots?: ProductScheduleSlotItem[];
}

export interface ProductScheduleSlotItem {
  id?: number;
  weekday: number;
  startTime: string;
  endTime: string;
}

// ─── Enrollment ────────────────────────────────────────

export interface EnrollmentOrderItem {
  id: number;
  enrollmentNo: string;
  studentId: number;
  studentName: string;
  classId?: number;
  className?: string;
  courseProductId?: number;
  courseProductName?: string;
  handledByName?: string;
  currencyCode?: string;
  originalAmount: number;
  discountAmount: number;
  receivedAmount: number;
  paymentStatus: string;
  orderStatus: string;
  packageCount?: number;
  createdAt: string;
}

export interface EnrollmentOrderDetailItem {
  id: number;
  enrollmentNo: string;
  studentId: number;
  studentNo: string;
  studentName: string;
  classId?: number;
  className?: string;
  orderStatus: string;
  cashierUserId?: number;
  handledByName?: string;
  currencyCode?: string;
  originalAmount: number;
  discountAmount: number;
  receivedAmount: number;
  paymentStatus: string;
  remark?: string;
  payments: PaymentRecordItem[];
  packages: CoursePackageItem[];
  createdAt: string;
  updatedAt?: string;
}

export interface CoursePackageItem {
  id: number;
  packageNo: string;
  enrollmentOrderId: number;
  studentId: number;
  studentName: string;
  courseProductId: number;
  packageName: string;
  subjectName?: string;
  teachingMode?: string;
  totalUnits: number;
  consumedUnits: number;
  remainingUnits: number;
  receivedAmount: number;
  packageStatus: string;
  startDate?: string;
  estimatedEndDate?: string;
  primaryTeacherName?: string;
  createdAt: string;
}

export interface PaymentMethodItem {
  id: number;
  methodCode: string;
  methodName: string;
  sortOrder: number;
  status: string;
}

export interface PaymentRecordItem {
  id: number;
  paymentNo: string;
  paymentMethod: string;
  paymentStatus: string;
  amount: number;
  paidAt: string;
  handledByName?: string;
  remark?: string;
}

export interface ReceiptRecordItem {
  id: number;
  receiptNo: string;
  enrollmentOrderId: number;
  studentId: number;
  studentName: string;
  templateCode?: string;
  fileUrl?: string;
  issuedAt: string;
}

// ─── Schedule ──────────────────────────────────────────

export interface RecurringScheduleRuleItem {
  id: number;
  packageId: number;
  packageNo: string;
  studentId: number;
  studentName: string;
  primaryTeacherId?: number;
  primaryTeacherName?: string;
  defaultClassroomId?: number;
  defaultClassroomName?: string;
  ruleStatus: string;
  startDate: string;
  endDate?: string;
  generationHorizonDays: number;
  slotCount: number;
  generatedOccurrenceCount: number;
  nextLessonStartAt?: string;
  createdAt: string;
}

export interface RecurringScheduleSlotItem {
  id: number;
  weekday: number;
  startTime: string;
  endTime: string;
  plannedUnits: number;
  slotStatus: string;
}

export interface RecurringScheduleRuleDetailItem extends RecurringScheduleRuleItem {
  packageName?: string;
  notes?: string;
  slots: RecurringScheduleSlotItem[];
  createdAt: string;
  updatedAt?: string;
}

export interface LessonOccurrenceItem {
  id: number;
  lessonNo: string;
  packageId: number;
  packageNo: string;
  packageName?: string;
  studentId: number;
  studentName: string;
  scheduleRuleId?: number;
  scheduleSlotId?: number;
  occurrenceType: string;
  plannedTeacherId?: number;
  plannedTeacherName?: string;
  plannedClassroomId?: number;
  plannedClassroomName?: string;
  plannedStartAt: string;
  plannedEndAt: string;
  plannedUnits: number;
  planningStatus: string;
  sourceOccurrenceId?: number;
  conflictChecked: boolean;
  notes?: string;
  resultStatus?: string;
  resultStatusName?: string;
}

export interface ClassroomOccupancyItem {
  occurrenceId: number;
  lessonNo: string;
  classroomId: number;
  classroomName: string;
  teacherId?: number;
  teacherName?: string;
  packageId: number;
  packageNo: string;
  packageName?: string;
  studentId?: number;
  studentName?: string;
  plannedStartAt: string;
  plannedEndAt: string;
  plannedUnits: number;
  planningStatus: string;
  classGroupId?: number;
  className?: string;
}

// ─── Class Group ─────────────────────────────────────────

export interface ClassGroupItem {
  id: number;
  className: string;
  description?: string;
  scheduleDesc?: string;
  teacherName?: string;
  classroomName?: string;
  activeStudentCount: number;
}

export interface ClassEnrolledStudentItem {
  studentId: number;
  studentName: string;
  enrollmentDate: string;
  expiryDate: string;
  enrollmentStatus: string;
  expiryStatus: 'EXPIRED' | 'EXPIRING_SOON' | 'NORMAL';
}

export interface ClassOccurrenceItem {
  id: number;
  lessonNo: string;
  occurrenceType: string;
  plannedTeacherId?: number;
  plannedTeacherName?: string;
  plannedClassroomId?: number;
  plannedClassroomName?: string;
  plannedStartAt: string;
  plannedEndAt: string;
  plannedUnits: number;
  planningStatus: string;
  resultStatus?: string;
  confirmed: boolean;
}

// ─── Lesson ─────────────────────────────────────────────

export interface LessonRecordItem {
  id: number;
  occurrenceId: number;
  lessonNo: string;
  packageId: number;
  packageNo: string;
  packageName?: string;
  studentId: number;
  studentName: string;
  resultStatus: string;
  actualTeacherId?: number;
  actualTeacherName?: string;
  actualClassroomId?: number;
  actualClassroomName?: string;
  actualUnits: number;
  shouldDeduct: boolean;
  deductedUnits: number;
  shouldExtendEndDate: boolean;
  recordSource?: string;
  confirmedAt?: string;
}

export interface LessonRecordDetailItem extends LessonRecordItem {
  plannedTeacherId?: number;
  plannedTeacherName?: string;
  plannedClassroomId?: number;
  plannedClassroomName?: string;
  actualStartAt?: string;
  actualEndAt?: string;
  confirmedByUserId?: number;
  notes?: string;
}

export interface LessonRecordConfirmResult {
  lessonRecordId: number;
  occurrenceId: number;
  ledgerId: number;
  deductedUnits: number;
  remainingUnits: number;
  packageCompleted: boolean;
}

export interface PackageBalanceSnapshotItem {
  packageId: number;
  packageNo: string;
  totalUnits: number;
  consumedUnits: number;
  remainingUnits: number;
  lastLedgerId?: number;
  snapshotAt?: string;
}

export interface LessonStatusRuleItem {
  id: number;
  statusCode: string;
  statusName: string;
  defaultShouldDeduct: boolean;
  deductMode: string;
  fixedUnits?: number;
  defaultShouldExtend: boolean;
  allowManualOverride: boolean;
  status: string;
  remark?: string;
}

// ─── System Config ─────────────────────────────────────

export interface BrandConfigItem {
  id: number;
  brandName: string;
  logoUrl?: string;
  themeColor?: string;
  supportEmail?: string;
  receiptFooterText?: string;
  status: string;
}

export interface NotificationTemplateItem {
  id: number;
  templateCode: string;
  templateName: string;
  channelType: string;
  subjectTemplate: string;
  bodyTemplate: string;
  status: string;
  remark?: string;
}

export interface NotificationConfigItem {
  id: number;
  enabled: boolean;
  fromAddress: string;
  fromName: string;
  strictAllowlist: boolean;
  allowedRecipients: string;
  lowBalanceThreshold: number;
  lowBalanceRecipients: string;
  updatedAt?: string;
}

export interface NotificationTriggerRuleItem {
  id: number;
  triggerType: string;
  triggerName: string;
  templateId: number | null;
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
  userId: number;
  teacherId: number;
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
  userId: number;
  studentId: number;
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
  id: number;
  teacherId: number;
  teacherNo: string;
  teacherName: string;
  courseProductId: number;
  productCode: string;
  productName: string;
  subjectName: string;
  hourlyRate: number;
  notes?: string;
}

export interface UnpaidLessonItem {
  lessonRecordId: number;
  lessonDate: string;
  studentId: number;
  studentName: string;
  packageId: number;
  packageName: string;
  courseProductId: number;
  productName: string;
  subjectName: string;
  actualUnits: number;
  hours: number;
  hourlyRate: number;
  amount: number;
  resultStatus: string;
  occurrenceType?: string;
  className?: string;
}

export interface TeacherPaymentItem {
  id: number;
  paymentNo: string;
  teacherId: number;
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
  id: number;
  lessonRecordId: number;
  packageId: number;
  packageName: string;
  courseProductId: number;
  productName: string;
  subjectName: string;
  actualUnits: number;
  hours: number;
  hourlyRate: number;
  amount: number;
  occurrenceType?: string;
  className?: string;
}