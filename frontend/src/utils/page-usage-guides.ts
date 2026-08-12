import type { RouteLocationRaw } from 'vue-router';

export interface PageUsageAction {
  label: string;
  to: RouteLocationRaw;
}

export interface PageUsageGuide {
  eyebrow: string;
  title: string;
  description: string;
  steps: string[];
  actions: PageUsageAction[];
}

const dashboardGuide: PageUsageGuide = {
  eyebrow: 'Daily start',
  title: 'Start from the task you need to finish.',
  description: 'Use this page as the staff home screen. It points you to the correct workflow without learning every menu first.',
  steps: [
    'Use the task cards for daily work: registration, lesson deduction, conflicts, and follow-up.',
    'Use the setup checklist when opening the system for the first time.',
    'Use Quick links only for lower-frequency back-office setup.',
  ],
  actions: [
    { label: 'New registration', to: '/registration' },
    { label: 'Review lessons', to: '/schedule/adjustments' },
    { label: 'Course prices', to: '/admin/config/course-prices' },
  ],
};

const guides: Record<string, PageUsageGuide> = {
  dashboard: dashboardGuide,
  registration: {
    eyebrow: 'Registration flow',
    title: 'Create the order, payment, and receipt in one pass.',
    description: 'Use this page when a student signs up, renews, or pays against an existing registration.',
    steps: [
      'Confirm or create the student profile first, including guardian contact and pickup/drop-off needs.',
      'Choose a course option. 1-on-1 and group classes are visually separated in the course dropdown.',
      'For group classes, select the number of months; pricing is calculated from the class price setup.',
      'Record payment only after reviewing the total. Receipts are saved as snapshots after submission.',
    ],
    actions: [
      { label: 'Course products', to: '/base-data/course-products' },
      { label: 'Course prices', to: '/admin/config/course-prices' },
      { label: 'Enrollment history', to: '/enrollment/orders' },
      { label: 'Receipts', to: '/enrollment/receipts' },
    ],
  },
  calendar: {
    eyebrow: 'Calendar',
    title: 'Check student, teacher, and room schedules before changing lessons.',
    description: 'Use the calendar for conflict checks and daily schedule review.',
    steps: [
      'Switch the calendar scope to see the schedule from the student, teacher, or room perspective.',
      'Before creating or moving a lesson, confirm the teacher and classroom are both available.',
      'Use the lesson confirmation pages when attendance or deduction needs to be finalized.',
    ],
    actions: [
      { label: '1-on-1 lessons', to: '/one-on-one-deduction' },
      { label: 'Group lessons', to: '/group-class-lessons' },
      { label: 'Room calendar', to: '/classrooms/occupancy' },
    ],
  },
  'one-on-one-deduction': {
    eyebrow: '1-on-1 lessons',
    title: 'Teacher confirms first, admin reviews and deducts second.',
    description: 'Use this page to print a student sheet, confirm lesson results, and keep package hours accurate.',
    steps: [
      'Find the student or teacher, then choose the active course package.',
      'Teachers submit attendance first. Admins review the result before hours are deducted.',
      'Completed and cancelled lessons deduct hours; formal leave does not deduct and can extend future scheduling.',
    ],
    actions: [
      { label: 'Schedule lessons', to: '/schedule/recurring' },
      { label: 'Packages', to: '/enrollment/packages' },
      { label: 'Renewal follow-up', to: '/data-summary?tab=renewal' },
    ],
  },
  'my-lessons': {
    eyebrow: 'Teacher workspace',
    title: 'Review your assigned students and submit lesson confirmation.',
    description: 'This is the teacher-facing lesson page. It keeps the teacher action separate from admin deduction.',
    steps: [
      'Find the student or course you are teaching.',
      'Submit the real lesson outcome after class.',
      'Admin staff will review and finalize deduction after your confirmation.',
    ],
    actions: [
      { label: 'My calendar', to: '/calendar' },
      { label: '1-on-1 lessons', to: '/one-on-one-deduction' },
    ],
  },
  'my-courses': {
    eyebrow: 'Student courses',
    title: 'Review active courses, schedules, and remaining hours.',
    description: 'This view is for checking course progress without changing finance records.',
    steps: [
      'Choose the active package to see scheduled and completed lessons.',
      'Use remaining hours to understand when renewal is needed.',
      'Ask staff to update schedule or payment records from the admin workflows.',
    ],
    actions: [
      { label: 'Calendar', to: '/calendar' },
      { label: 'Registration', to: '/registration' },
    ],
  },
  'group-class-lessons': {
    eyebrow: 'Group class deduction',
    title: 'Review group attendance and deduct class sessions.',
    description: 'Group classes only use completed or cancelled outcomes. Both consume the planned class session.',
    steps: [
      'Choose the class date and review the enrolled student list.',
      'Check teacher confirmation first when available.',
      'Admin review finalizes the session deduction for each student.',
    ],
    actions: [
      { label: 'Course prices', to: '/admin/config/course-prices' },
      { label: 'Room calendar', to: '/classrooms/occupancy' },
      { label: 'Data summary', to: '/data-summary' },
    ],
  },
  'schedule-adjustments': {
    eyebrow: 'Lesson confirmation',
    title: 'Review lesson outcomes before deduction becomes final.',
    description: 'Use this page for attendance corrections, leave handling, and admin confirmation.',
    steps: [
      'Filter to the lesson set that needs review.',
      'Confirm teacher-submitted results before final deduction.',
      'Use package and renewal pages after deduction to follow up low balances.',
    ],
    actions: [
      { label: 'Packages', to: '/enrollment/packages' },
      { label: 'Renewal follow-up', to: '/data-summary?tab=renewal' },
      { label: 'Schedule lessons', to: '/schedule/recurring' },
    ],
  },
  'data-summary': {
    eyebrow: 'Operations review',
    title: 'Review registration, payment, and renewal risk in one workbook.',
    description: 'Data Summary is a read-only operations workbook. Use it to find issues, then complete actions on the related workflow page.',
    steps: [
      'Read Tuition as the course line price before discounts and before pickup/drop-off fees.',
      'Use each column header to filter. Text columns search, date columns use ranges, and amount columns support min/max plus Asc or Desc sorting.',
      'Sort or range-filter Balance to find unpaid follow-up; negative Balance means money is still owed.',
      'Use Enrollment History for payment or refund actions, and Registration for renewals.',
    ],
    actions: [
      { label: 'New registration', to: '/registration' },
      { label: 'Enrollment history', to: '/enrollment/orders' },
      { label: 'Packages', to: '/enrollment/packages' },
    ],
  },
  'admin-config-people': {
    eyebrow: 'People setup',
    title: 'Manage students, teachers, and login access in one place.',
    description: 'Use this page for profile cleanup, account creation, and controlled deletion.',
    steps: [
      'Use the Students tab for guardian contact, status, pickup/drop-off profile, and registration history.',
      'Use the Teachers tab for instructor profiles, mobile number, and login accounts.',
      'Deletion is an admin operation. Review the red warning and linked data before confirming.',
    ],
    actions: [
      { label: 'Register student', to: '/registration' },
      { label: 'Course products', to: '/base-data/course-products' },
      { label: 'Teacher pay rates', to: '/admin/config/teacher-pay-rates' },
    ],
  },
  'course-products': {
    eyebrow: 'Course setup step 1',
    title: 'Create the parent course before adding grade and price rows.',
    description: 'Products define what the business sells. Prices define the grade, hours, schedule, and billing amount.',
    steps: [
      'Create the product with the correct teaching mode: 1-on-1 or group class.',
      'After saving, continue to Course Pricing to add grade and price rows.',
      'Do not put grade-specific price logic here; keep that in the pricing matrix.',
    ],
    actions: [
      { label: 'Course pricing', to: '/admin/config/course-prices' },
      { label: 'Registration', to: '/registration' },
      { label: 'Schedule lessons', to: '/schedule/recurring' },
    ],
  },
  'admin-config-course-prices': {
    eyebrow: 'Course setup step 2',
    title: 'Set up the registration options users will choose.',
    description: 'Each row becomes a selectable registration option and is copied into order and receipt snapshots.',
    steps: [
      'Pick teaching mode, then select the course product on the left.',
      '1-on-1 rows define grade, package hours, and package price.',
      'Group rows define grade class, monthly price, schedule, teacher, room, and admin-visible session information.',
      'Use the header tooltips and compact actions when reviewing or editing the pricing matrix.',
    ],
    actions: [
      { label: 'Course products', to: '/base-data/course-products' },
      { label: 'Registration', to: '/registration' },
      { label: 'Room calendar', to: '/classrooms/occupancy' },
    ],
  },
  'recurring-schedule': {
    eyebrow: '1-on-1 scheduling',
    title: 'Create weekly lesson plans for active course packages.',
    description: 'Use this after registration when a 1-on-1 package needs concrete scheduled lessons.',
    steps: [
      'Select the student package that needs scheduling.',
      'Select weekly time slots, then choose a teacher on each slot when that lesson has an assigned teacher.',
      'Use Classroom when the package should reserve a default room.',
      'Check teacher, student, and room conflicts before saving the plan.',
    ],
    actions: [
      { label: 'Registration', to: '/registration' },
      { label: 'Teacher calendar', to: '/schedule/teacher' },
      { label: 'Student calendar', to: '/schedule/student' },
      { label: 'Room calendar', to: '/classrooms/occupancy' },
    ],
  },
  'classroom-management': {
    eyebrow: 'Rooms',
    title: 'Create and maintain physical classroom records.',
    description: 'Rooms are used by schedules, conflict checks, and classroom occupancy views.',
    steps: [
      'Create each classroom with campus, capacity, and active status.',
      'Use inactive status instead of deleting a room still referenced by history.',
      'Open Room Calendar to verify usage before assigning new classes.',
    ],
    actions: [
      { label: 'Room calendar', to: '/classrooms/occupancy' },
      { label: 'Campuses', to: '/admin/config/campuses' },
      { label: 'Course pricing', to: '/admin/config/course-prices' },
    ],
  },
  'classrooms': {
    eyebrow: 'Rooms',
    title: 'Create and maintain physical classroom records.',
    description: 'Rooms are used by schedules, conflict checks, and classroom occupancy views.',
    steps: [
      'Create each classroom with campus, capacity, and active status.',
      'Use inactive status instead of deleting a room still referenced by history.',
      'Open Room Calendar to verify usage before assigning new classes.',
    ],
    actions: [
      { label: 'Room calendar', to: '/classrooms/occupancy' },
      { label: 'Campuses', to: '/admin/config/campuses' },
    ],
  },
  'classroom-management-occupancy': {
    eyebrow: 'Room calendar',
    title: 'Check classroom usage and conflicts before scheduling.',
    description: 'This page shows whether a room is free, occupied, or double-booked.',
    steps: [
      'Pick a campus, classroom, and week.',
      'Use occupied and conflict markers before adding or moving lessons.',
      'Return to scheduling or course pricing after confirming room availability.',
    ],
    actions: [
      { label: 'Schedule 1-on-1', to: '/schedule/recurring' },
      { label: 'Course pricing', to: '/admin/config/course-prices' },
      { label: 'Classrooms', to: '/classrooms' },
    ],
  },
  'classroom-occupancy': {
    eyebrow: 'Room calendar',
    title: 'Check classroom usage and conflicts before scheduling.',
    description: 'This page shows whether a room is free, occupied, or double-booked.',
    steps: [
      'Pick a campus, classroom, and week.',
      'Use occupied and conflict markers before adding or moving lessons.',
      'Return to scheduling or course pricing after confirming room availability.',
    ],
    actions: [
      { label: 'Schedule 1-on-1', to: '/schedule/recurring' },
      { label: 'Course pricing', to: '/admin/config/course-prices' },
    ],
  },
  'teacher-schedule': {
    eyebrow: 'Teacher calendar',
    title: 'Review a teacher schedule before assigning lessons.',
    description: 'Use this page to avoid teacher conflicts and understand weekly workload.',
    steps: [
      'Select the teacher and week.',
      'Review existing lessons before adding new recurring schedules.',
      'Use deduction pages after lessons happen to confirm attendance.',
    ],
    actions: [
      { label: 'Schedule 1-on-1', to: '/schedule/recurring' },
      { label: '1-on-1 lessons', to: '/one-on-one-deduction' },
      { label: 'Teacher pay rates', to: '/admin/config/teacher-pay-rates' },
    ],
  },
  'student-schedule': {
    eyebrow: 'Student calendar',
    title: 'Review one student schedule and avoid conflicts.',
    description: 'Use this before adding new lessons or answering guardian schedule questions.',
    steps: [
      'Find the student and choose the week.',
      'Check active 1-on-1 and group class times together.',
      'Use registration or scheduling pages when the student needs a new course.',
    ],
    actions: [
      { label: 'Registration', to: '/registration' },
      { label: 'Schedule 1-on-1', to: '/schedule/recurring' },
      { label: 'Packages', to: '/enrollment/packages' },
    ],
  },
  'enrollment-orders': {
    eyebrow: 'Enrollment records',
    title: 'Review orders, later payments, refunds, and receipts.',
    description: 'Use this when finance records need review after registration is submitted.',
    steps: [
      'Use the table headers to filter by course, student, order number, payment status, or order status.',
      'Switch between 1-on-1 and Group Class tabs. Expand a group class to review the enrolled student orders.',
      'Use payment actions for later payment and refund workflows.',
      'Open saved receipts instead of recreating old receipt content.',
    ],
    actions: [
      { label: 'New registration', to: '/registration' },
      { label: 'Receipts', to: '/enrollment/receipts' },
      { label: 'Data summary', to: '/data-summary' },
    ],
  },
  'course-packages': {
    eyebrow: 'Package balance',
    title: 'Track remaining hours, assigned teacher, and package status.',
    description: 'Use this page after registration and lesson deduction to understand active balances.',
    steps: [
      'Search the student or package that needs review.',
      'Check total, used, and remaining hours before renewal follow-up.',
      'Use scheduling for active packages that still need planned lessons.',
    ],
    actions: [
      { label: 'Schedule lessons', to: '/schedule/recurring' },
      { label: 'Renewal follow-up', to: '/data-summary?tab=renewal' },
      { label: 'Enrollment history', to: '/enrollment/orders' },
    ],
  },
  'receipt-records': {
    eyebrow: 'Receipt records',
    title: 'Find saved registration, payment, and refund receipts.',
    description: 'Receipts are saved snapshots. Use this page to print or reopen historical receipt content.',
    steps: [
      'Search by student, order, or receipt keyword.',
      'Choose the correct receipt type before printing.',
      'Use Enrollment History when the underlying order needs payment or refund action.',
    ],
    actions: [
      { label: 'Enrollment history', to: '/enrollment/orders' },
      { label: 'New registration', to: '/registration' },
      { label: 'Brand settings', to: '/admin/config/receipt' },
    ],
  },
  'admin-config-payment-methods': {
    eyebrow: 'Payment setup',
    title: 'Control the payment methods staff can select.',
    description: 'Registration and later payment forms use this dictionary.',
    steps: [
      'Keep only real payment methods active.',
      'Use clear names that match receipt and finance review language.',
      'Deactivate old methods instead of deleting history-sensitive options.',
    ],
    actions: [
      { label: 'Registration', to: '/registration' },
      { label: 'Enrollment history', to: '/enrollment/orders' },
    ],
  },
  'admin-config-receipt': {
    eyebrow: 'Receipt setup',
    title: 'Configure the brand details printed on receipts.',
    description: 'This page controls business name, footer text, logo, and EIN display.',
    steps: [
      'Review brand and tax information before printing official receipts.',
      'Use a concise footer so printed receipts stay readable.',
      'Open receipt records to verify the final printed result.',
    ],
    actions: [
      { label: 'Receipt records', to: '/enrollment/receipts' },
      { label: 'Registration', to: '/registration' },
    ],
  },
  'brand-settings': {
    eyebrow: 'Receipt setup',
    title: 'Configure the brand details printed on receipts.',
    description: 'This page controls business name, footer text, logo, and EIN display.',
    steps: [
      'Review brand and tax information before printing official receipts.',
      'Use a concise footer so printed receipts stay readable.',
      'Open receipt records to verify the final printed result.',
    ],
    actions: [
      { label: 'Receipt records', to: '/enrollment/receipts' },
      { label: 'Registration', to: '/registration' },
    ],
  },
  'admin-config-campuses': {
    eyebrow: 'Campus setup',
    title: 'Maintain locations used by rooms and registration.',
    description: 'Campuses group classrooms and help staff choose where a student attends.',
    steps: [
      'Create each real teaching location first.',
      'Create classrooms under the correct campus.',
      'Use active/inactive status to keep old history stable.',
    ],
    actions: [
      { label: 'Classrooms', to: '/classrooms' },
      { label: 'Room calendar', to: '/classrooms/occupancy' },
      { label: 'Registration', to: '/registration' },
    ],
  },
  campuses: {
    eyebrow: 'Campus setup',
    title: 'Maintain locations used by rooms and registration.',
    description: 'Campuses group classrooms and help staff choose where a student attends.',
    steps: [
      'Create each real teaching location first.',
      'Create classrooms under the correct campus.',
      'Use active/inactive status to keep old history stable.',
    ],
    actions: [
      { label: 'Classrooms', to: '/classrooms' },
      { label: 'Room calendar', to: '/classrooms/occupancy' },
    ],
  },
  'admin-config-notifications': {
    eyebrow: 'Notification setup',
    title: 'Configure email templates and delivery behavior.',
    description: 'Use this before sending renewal reminders or other guardian notifications.',
    steps: [
      'Configure sender and SMTP settings first.',
      'Review templates before enabling real sending.',
      'Use strict recipient controls while testing.',
    ],
    actions: [
      { label: 'Data summary', to: '/data-summary' },
      { label: 'People', to: '/admin/config/people' },
    ],
  },
  'notification-settings': {
    eyebrow: 'Notification setup',
    title: 'Configure email templates and delivery behavior.',
    description: 'Use this before sending renewal reminders or other guardian notifications.',
    steps: [
      'Configure sender and SMTP settings first.',
      'Review templates before enabling real sending.',
      'Use strict recipient controls while testing.',
    ],
    actions: [
      { label: 'Data summary', to: '/data-summary' },
      { label: 'People', to: '/admin/config/people' },
    ],
  },
  'admin-config-teacher-pay-rates': {
    eyebrow: 'Teacher pay setup',
    title: 'Set pay rates before running teacher payment review.',
    description: 'Pay rates are used together with confirmed lesson records.',
    steps: [
      'Choose teacher, course product, and pay basis carefully.',
      'Keep old rates inactive when pay changes need historical clarity.',
      'Use Teacher Payments after lesson records are confirmed.',
    ],
    actions: [
      { label: 'Teacher payments', to: '/finance/payments' },
      { label: '1-on-1 lessons', to: '/one-on-one-deduction' },
      { label: 'People', to: '/admin/config/people?tab=teachers' },
    ],
  },
  'pay-rates': {
    eyebrow: 'Teacher pay setup',
    title: 'Set pay rates before running teacher payment review.',
    description: 'Pay rates are used together with confirmed lesson records.',
    steps: [
      'Choose teacher, course product, and pay basis carefully.',
      'Keep old rates inactive when pay changes need historical clarity.',
      'Use Teacher Payments after lesson records are confirmed.',
    ],
    actions: [
      { label: 'Teacher payments', to: '/finance/payments' },
      { label: '1-on-1 lessons', to: '/one-on-one-deduction' },
    ],
  },
  'teacher-payments': {
    eyebrow: 'Teacher payments',
    title: 'Review payable lessons after admin confirmation.',
    description: 'Use this page to prepare settlement based on confirmed lesson records and configured rates.',
    steps: [
      'Filter by teacher and date range.',
      'Review included lesson records before settlement.',
      'Adjust pay rates from settings when calculation rules are missing or outdated.',
    ],
    actions: [
      { label: 'Pay rates', to: '/admin/config/teacher-pay-rates' },
      { label: '1-on-1 lessons', to: '/one-on-one-deduction' },
      { label: 'Group lessons', to: '/group-class-lessons' },
    ],
  },
  'admin-guide': {
    eyebrow: 'Guide',
    title: 'Use the full guide when staff need end-to-end training.',
    description: 'This page explains the main workflows in more detail than the floating page tips.',
    steps: [
      'Start with the common setup and daily workflow sections.',
      'Use the left navigation to jump to the workflow you are training.',
      'Return to the relevant operational page when ready to execute the task.',
    ],
    actions: [
      { label: 'Dashboard', to: '/dashboard' },
      { label: 'Registration', to: '/registration' },
      { label: 'Course setup', to: '/base-data/course-products' },
    ],
  },
};

export function getPageUsageGuide(routeName: string | symbol | null | undefined, metaTitle?: unknown): PageUsageGuide {
  const key = String(routeName ?? '');
  return guides[key] ?? {
    eyebrow: 'Page guide',
    title: `Use ${String(metaTitle ?? 'this page')} from the matching workflow.`,
    description: 'This page is part of the back-office workflow. Use the actions below to move to the most common related pages.',
    steps: [
      'Review the page header and filters before changing data.',
      'Prefer deactivating records when old orders, receipts, or lessons may reference them.',
      'Use Dashboard or User Guide if you are unsure which workflow should come next.',
    ],
    actions: [
      { label: 'Dashboard', to: '/dashboard' },
      { label: 'User Guide', to: '/admin/guide' },
      { label: 'Registration', to: '/registration' },
    ],
  };
}
