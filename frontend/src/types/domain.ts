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
  sourceChannel?: string;
  guardianName: string;
  phone: string;
  guardianEmail?: string;
  guardianRelationType?: string;
  status: string;
  campusId?: EntityId;
  campusName?: string;
  notes?: string;
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
  notes?: string;
}

export interface CourseProductItem {
  id: EntityId;
  productCode: string;
  productName: string;
  subjectName?: string;
  teachingMode: string;
  defaultTotalUnits: number;
  defaultLessonUnits: number;
  expiryDurationDays?: number;
  defaultClassroomId?: EntityId;
  defaultClassroomName?: string;
  defaultTeacherId?: EntityId;
  defaultTeacherName?: string;
  listPrice: number;
  status: string;
  notes?: string;
  slots?: ProductScheduleSlotItem[];
}

export interface ProductScheduleSlotItem {
  id?: EntityId;
  weekday: number;
  startTime: string;
  endTime: string;
}

// ─── Enrollment ────────────────────────────────────────

export interface EnrollmentOrderItem {
  id: EntityId;
  enrollmentNo: string;
  studentId: EntityId;
  studentName: string;
  classId?: EntityId;
  className?: string;
  courseProductId?: EntityId;
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
  id: EntityId;
  enrollmentNo: string;
  studentId: EntityId;
  studentNo: string;
  studentName: string;
  classId?: EntityId;
  className?: string;
  orderStatus: string;
  cashierUserId?: EntityId;
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
  id: EntityId;
  packageNo: string;
  enrollmentOrderId: EntityId;
  studentId: EntityId;
  studentName: string;
  courseProductId: EntityId;
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
  id: EntityId;
  methodCode: string;
  methodName: string;
  sortOrder: number;
  status: string;
}

export interface PaymentRecordItem {
  id: EntityId;
  paymentNo: string;
  paymentMethod: string;
  paymentStatus: string;
  amount: number;
  paidAt: string;
  handledByName?: string;
  remark?: string;
}

export interface ReceiptRecordItem {
  id: EntityId;
  receiptNo: string;
  enrollmentOrderId: EntityId;
  studentId: EntityId;
  studentName: string;
  templateCode?: string;
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
  primaryTeacherId?: EntityId;
  primaryTeacherName?: string;
  defaultClassroomId?: EntityId;
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
  id: EntityId;
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
  id: EntityId;
  lessonNo: string;
  packageId: EntityId;
  packageNo: string;
  packageName?: string;
  classGroupId?: EntityId;
  className?: string;
  studentId: EntityId;
  studentName: string;
  scheduleRuleId?: EntityId;
  scheduleSlotId?: EntityId;
  occurrenceType: string;
  plannedTeacherId?: EntityId;
  plannedTeacherName?: string;
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
}

// ─── Class Group ─────────────────────────────────────────

export interface ClassGroupItem {
  id: EntityId;
  className: string;
  description?: string;
  scheduleDesc?: string;
  teacherId?: EntityId;
  teacherName?: string;
  classroomId?: EntityId;
  classroomName?: string;
  activeStudentCount: number;
}

export interface ClassEnrolledStudentItem {
  studentId: EntityId;
  studentName: string;
  enrollmentDate: string;
  expiryDate: string;
  enrollmentStatus: string;
  expiryStatus: 'EXPIRED' | 'EXPIRING_SOON' | 'NORMAL';
}

export interface ClassOccurrenceItem {
  id: EntityId;
  lessonNo: string;
  occurrenceType: string;
  plannedTeacherId?: EntityId;
  plannedTeacherName?: string;
  plannedClassroomId?: EntityId;
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
  actualUnits: number;
  shouldDeduct: boolean;
  deductedUnits: number;
  shouldExtendEndDate: boolean;
  recordSource?: string;
  confirmedAt?: string;
}

export interface LessonRecordDetailItem extends LessonRecordItem {
  plannedTeacherId?: EntityId;
  plannedTeacherName?: string;
  plannedClassroomId?: EntityId;
  plannedClassroomName?: string;
  actualStartAt?: string;
  actualEndAt?: string;
  confirmedByUserId?: EntityId;
  notes?: string;
}

export interface LessonRecordConfirmResult {
  lessonRecordId: EntityId;
  occurrenceId: EntityId;
  ledgerId: EntityId;
  deductedUnits: number;
  remainingUnits: number;
  packageCompleted: boolean;
}

export interface PackageBalanceSnapshotItem {
  packageId: EntityId;
  packageNo: string;
  totalUnits: number;
  consumedUnits: number;
  remainingUnits: number;
  lastLedgerId?: EntityId;
  snapshotAt?: string;
}

export interface LessonStatusRuleItem {
  id: EntityId;
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
  courseProductId: EntityId;
  productCode: string;
  productName: string;
  subjectName: string;
  hourlyRate: number;
  notes?: string;
}

export interface UnpaidLessonItem {
  lessonRecordId: EntityId;
  lessonDate: string;
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
