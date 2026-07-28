import { createRouter, createWebHistory, createWebHashHistory, type RouteRecordRaw } from 'vue-router';

import AppLayout from '@/components/layout/AppLayout.vue';
import { APP_SYSTEM_NAME } from '@/config/app-config';
import { getStoredAuthToken, getStoredAuthUser } from '@/utils/auth-storage';
import { getDefaultLandingPath, isAdminUser, isStudentUser, isTeacherUser, type AuthUser } from '@/types/auth';

// Route-level lazy loading keeps the initial admin bundle smaller.
const LoginView = () => import('@/views/LoginView.vue');
const DashboardView = () => import('@/views/dashboard/DashboardView.vue');
const StudentsView = () => import('@/views/base-data/StudentsView.vue');
const TeachersView = () => import('@/views/base-data/TeachersView.vue');
const ClassroomsView = () => import('@/views/base-data/ClassroomsView.vue');
const CourseProductsView = () => import('@/views/base-data/CourseProductsView.vue');
const CampusesView = () => import('@/views/base-data/CampusesView.vue');
const EnrollmentOrdersView = () => import('@/views/enrollment/EnrollmentOrdersView.vue');
const CoursePackagesView = () => import('@/views/enrollment/CoursePackagesView.vue');
const ReceiptRecordsView = () => import('@/views/enrollment/ReceiptRecordsView.vue');
const RegistrationIntakeView = () => import('@/views/registration/RegistrationIntakeView.vue');
const DataSummaryView = () => import('@/views/data-summary/DataSummaryView.vue');
const CalendarView = () => import('@/views/schedule/CalendarView.vue');
const RecurringScheduleView = () => import('@/views/schedule/RecurringScheduleView.vue');
const ScheduleAdjustmentsView = () => import('@/views/schedule/ScheduleAdjustmentsView.vue');
const TeacherScheduleView = () => import('@/views/schedule/TeacherScheduleView.vue');
const ClassroomOccupancyView = () => import('@/views/schedule/ClassroomOccupancyView.vue');
const StudentScheduleView = () => import('@/views/schedule/StudentScheduleView.vue');
const MyLessonsView = () => import('@/views/schedule/MyLessonsView.vue');
const OneOnOneDeductionView = () => import('@/views/one-on-one/OneOnOneDeductionView.vue');
const NotificationSettingsView = () => import('@/views/system/NotificationSettingsView.vue');
const BrandSettingsView = () => import('@/views/system/BrandSettingsView.vue');
const CoursePriceOptionsView = () => import('@/views/system/CoursePriceOptionsView.vue');
const PaymentMethodsView = () => import('@/views/system/PaymentMethodsView.vue');
const AccountSettingsView = () => import('@/views/system/AccountSettingsView.vue');
const UserGuideView = () => import('@/views/system/UserGuideView.vue');
const TeacherPayRatesView = () => import('@/views/finance/TeacherPayRatesView.vue');
const TeacherPaymentsView = () => import('@/views/finance/TeacherPaymentsView.vue');

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: {
      title: 'Sign In',
    },
  },
  {
    path: '/',
    component: AppLayout,
    redirect: () => getDefaultLandingPath(getStoredAuthUser()),
    children: [
      {
        path: 'base-data',
        redirect: '/admin/config/students',
      },
      {
        path: 'enrollment',
        redirect: '/registration',
      },
      {
        path: 'schedule',
        redirect: '/calendar',
      },
      {
        path: 'finance',
        redirect: '/admin/config/teacher-pay-rates',
      },
      {
        path: 'system',
        redirect: '/admin/config/accounts',
      },
      {
        path: 'admin/config',
        redirect: '/admin/config/students',
      },
      {
        path: 'back-office',
        redirect: '/admin/config/students',
      },
      {
        path: 'registration',
        name: 'registration',
        component: RegistrationIntakeView,
        meta: {
          title: 'Registration',
          section: 'Registration',
          description: 'Submit student, course, payment, and receipt intake in one workflow.',
        },
      },
      {
        path: 'calendar',
        name: 'calendar',
        component: CalendarView,
        meta: {
          title: 'Calendar',
          section: 'Calendar',
          description: 'View teacher, student, and room schedules.',
        },
      },
      {
        path: 'one-on-one-deduction',
        name: 'one-on-one-deduction',
        component: OneOnOneDeductionView,
        meta: {
          title: '1-on-1 Lessons',
          section: '1-on-1 Lessons',
          description: 'Find a teacher, choose a student, review lessons, and confirm hours.',
        },
      },
      {
        path: 'my-lessons',
        name: 'my-lessons',
        component: MyLessonsView,
        meta: {
          title: 'My Lessons',
          section: 'My Teaching',
          description: 'View your students, lessons, and hours.',
        },
      },
      {
        path: 'group-class-lessons',
        name: 'group-class-lessons',
        component: ScheduleAdjustmentsView,
        meta: {
          title: 'Group Class Lessons',
          section: 'Group Class Lessons',
          description: 'Review group class attendance and deduct lessons for enrolled students.',
        },
      },
      {
        path: 'classrooms',
        name: 'classroom-management',
        component: ClassroomsView,
        meta: {
          title: 'Classrooms',
          section: 'Classrooms',
          description: 'Add rooms and check room schedules.',
        },
      },
      {
        path: 'classrooms/occupancy',
        name: 'classroom-management-occupancy',
        component: ClassroomOccupancyView,
        meta: {
          title: 'Room Calendar',
          section: 'Classrooms',
          description: 'View weekly classroom occupancy by room and time slot.',
        },
      },
      {
        path: 'classrooms/backfill',
        name: 'classroom-management-backfill',
        redirect: '/classrooms/occupancy',
      },
      {
        path: 'data-summary',
        name: 'data-summary',
        component: DataSummaryView,
        meta: {
          title: 'Data Summary',
          section: 'Data Summary',
          description: 'Review registrations, payments, courses, and remaining hours.',
        },
      },
      {
        path: 'renewal-reminders',
        name: 'renewal-reminders',
        redirect: {
          name: 'data-summary',
          query: { tab: 'renewal' },
        },
        meta: {
          title: 'Renewal follow-up',
          section: 'Renewal follow-up',
          description: 'Find students ready to renew, start a new registration, and optionally send reviewed guardian emails.',
        },
      },
      {
        path: 'my-courses',
        name: 'my-courses',
        component: OneOnOneDeductionView,
        meta: {
          title: 'My Courses & Hours',
          section: 'My Courses',
          description: 'View your schedule, active courses, and remaining hours.',
        },
      },
      {
        path: 'dashboard',
        name: 'dashboard',
        component: DashboardView,
        meta: {
          title: 'Dashboard',
          section: 'Dashboard',
        },
      },
      {
        path: 'admin/config/students',
        name: 'admin-config-students',
        component: StudentsView,
        meta: {
          title: 'Students',
          section: 'Admin Configuration',
          description: 'Manage student profiles, guardian contacts, and active course packages.',
        },
      },
      {
        path: 'admin/config/teachers',
        name: 'admin-config-teachers',
        component: TeachersView,
        meta: {
          title: 'Teachers',
          section: 'Admin Configuration',
          description: 'Review instructor profiles, teaching details, and account linkage.',
        },
      },
      {
        path: 'admin/config/course-prices',
        name: 'admin-config-course-prices',
        component: CoursePriceOptionsView,
        meta: {
          title: 'Course Pricing',
          section: 'Settings',
          description: 'Configure structured course price options used by registration.',
        },
      },
      {
        path: 'admin/config/payment-methods',
        name: 'admin-config-payment-methods',
        component: PaymentMethodsView,
        meta: {
          title: 'Payment Methods',
          section: 'Settings',
          description: 'Manage the payment method dictionary used by registration and enrollment.',
        },
      },
      {
        path: 'admin/config/receipt',
        name: 'admin-config-receipt',
        component: BrandSettingsView,
        meta: {
          title: 'Brand & receipts',
          section: 'Settings',
          description: 'Manage brand name, receipt footer text, and EIN settings.',
        },
      },
      {
        path: 'admin/config/campuses',
        name: 'admin-config-campuses',
        component: CampusesView,
        meta: {
          title: 'Campuses',
          section: 'Settings',
          description: 'Manage campus locations for classroom assignment.',
        },
      },
      {
        path: 'admin/config/accounts',
        name: 'admin-config-accounts',
        component: AccountSettingsView,
        meta: {
          title: 'Accounts',
          section: 'Settings',
          description: 'Manage permissions for admins, staff, teachers, and students.',
        },
      },
      {
        path: 'admin/config/notifications',
        name: 'admin-config-notifications',
        component: NotificationSettingsView,
        meta: {
          title: 'Notifications',
          section: 'Settings',
          description: 'Configure templates, delivery settings, and retry behavior.',
        },
      },
      {
        path: 'admin/guide',
        name: 'admin-guide',
        component: UserGuideView,
        meta: {
          title: 'User Guide',
          section: 'Help',
          description: 'Learn daily workflows, setup order, registration, deduction, classroom, and data review steps.',
        },
      },
      {
        path: 'admin/config/teacher-pay-rates',
        name: 'admin-config-teacher-pay-rates',
        component: TeacherPayRatesView,
        meta: {
          title: 'Teacher Pay',
          section: 'Settings',
          description: 'Configure per-teacher, per-product hourly pay rates.',
        },
      },
      {
        path: 'base-data/students',
        name: 'students',
        component: StudentsView,
        meta: {
          title: 'Students',
          section: 'Base Data',
          description: 'Manage student profiles, guardian contacts, and active course packages.',
        },
      },
      {
        path: 'base-data/teachers',
        name: 'teachers',
        component: TeachersView,
        meta: {
          title: 'Teachers',
          section: 'Base Data',
          description: 'Review teacher profiles, teaching details, and account linkage.',
        },
      },
      {
        path: 'base-data/classrooms',
        name: 'classrooms',
        component: ClassroomsView,
        meta: {
          title: 'Classrooms',
          section: 'Base Data',
          description: 'Manage classroom records, availability, and room capacity.',
        },
      },
      {
        path: 'base-data/course-products',
        name: 'course-products',
        component: CourseProductsView,
        meta: {
          title: 'Course Products',
          section: 'Base Data',
          description: 'Configure course templates, default lesson units, and standard pricing.',
        },
      },
      {
        path: 'base-data/campuses',
        name: 'campuses',
        component: CampusesView,
        meta: {
          title: 'Campuses',
          section: 'Base Data',
          description: 'Manage campus locations for classroom assignment.',
        },
      },
      {
        path: 'enrollment/orders',
        name: 'enrollment-orders',
        component: EnrollmentOrdersView,
        meta: {
          title: 'Enrollment History',
          section: 'Enrollment',
          description: 'Review historical registrations, record later payments, and open each saved registration form.',
        },
      },
      {
        path: 'enrollment/packages',
        name: 'course-packages',
        component: CoursePackagesView,
        meta: {
          title: 'Course Packages',
          section: 'Enrollment',
          description: 'Track package balance, assigned instructor, and estimated end dates.',
        },
      },
      {
        path: 'enrollment/receipts',
        name: 'receipt-records',
        component: ReceiptRecordsView,
        meta: {
          title: 'Registration Receipts',
          section: 'Enrollment',
          description: 'Find each student, then open or print their saved registration forms.',
        },
      },
      {
        path: 'schedule/recurring',
        name: 'recurring-schedule',
        component: RecurringScheduleView,
        meta: {
          title: 'Schedule 1-on-1 Lessons',
          section: 'Scheduling',
          description: 'Create and adjust weekly lesson schedules for individual course packages.',
        },
      },
      {
        path: 'schedule/adjustments',
        name: 'schedule-adjustments',
        component: ScheduleAdjustmentsView,
        meta: {
          title: 'Lesson confirmation',
          section: 'Scheduling',
          description: 'Review lesson outcomes, record leave, reschedule, and manage makeups.',
        },
      },
      {
        path: 'schedule/teacher',
        name: 'teacher-schedule',
        component: TeacherScheduleView,
        meta: {
          title: 'Teacher Calendar',
          section: 'Scheduling',
          description: 'View weekly schedules from the instructor perspective.',
        },
      },
      {
        path: 'schedule/classrooms',
        name: 'classroom-occupancy',
        component: ClassroomOccupancyView,
        meta: {
          title: 'Room Calendar',
          section: 'Scheduling',
          description: 'View weekly classroom occupancy by room and time slot.',
        },
      },
      {
        path: 'schedule/student',
        name: 'student-schedule',
        component: StudentScheduleView,
        meta: {
          title: 'Student Calendar',
          section: 'Scheduling',
          description: 'View a student weekly class calendar with week navigation.',
        },
      },
      {
        path: 'system/notifications',
        name: 'notification-settings',
        component: NotificationSettingsView,
        meta: {
          title: 'Notifications',
          section: 'Settings',
          description: 'Configure templates, delivery settings, and retry behavior.',
        },
      },
      {
        path: 'system/brand',
        name: 'brand-settings',
        component: BrandSettingsView,
        meta: {
          title: 'Brand & receipts',
          section: 'Settings',
          description: 'Manage brand name, logo, theme colors, receipts, and EIN settings.',
        },
      },
      {
        path: 'system/accounts',
        name: 'account-settings',
        component: AccountSettingsView,
        meta: {
          title: 'Accounts & Permissions',
          section: 'Settings',
          description: 'Manage permissions for admins, staff, and future instructor roles.',
        },
      },
      {
        path: 'finance/pay-rates',
        name: 'pay-rates',
        component: TeacherPayRatesView,
        meta: {
          title: 'Teacher Pay Rates',
          section: 'Finance',
          description: 'Configure per-teacher, per-product hourly pay rates.',
        },
      },
      {
        path: 'finance/payments',
        name: 'teacher-payments',
        component: TeacherPaymentsView,
        meta: {
          title: 'Teacher Payments',
          section: 'Finance',
          description: 'Settle teacher payments based on confirmed lesson records.',
        },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: () => getDefaultLandingPath(getStoredAuthUser()),
  },
];

declare global {
  interface Window {
    electronAPI?: { platform: string };
  }
}

const isElectron = typeof window !== 'undefined' && window.electronAPI?.platform !== undefined;

const teacherSelfServicePaths = new Set(['/my-lessons', '/calendar']);
const studentSelfServicePaths = new Set(['/calendar']);
const adminConfigurationPaths = new Set([
  '/admin/config/students',
  '/admin/config/teachers',
  '/admin/config/course-prices',
  '/admin/config/payment-methods',
  '/admin/config/receipt',
  '/admin/config/campuses',
  '/admin/config/accounts',
  '/admin/config/notifications',
  '/admin/config/teacher-pay-rates',
  '/base-data/course-products',
  '/base-data/classrooms',
  '/schedule/recurring',
]);

function getSelfServiceFallbackPath(user: AuthUser | null, path: string): string | null {
  if (isTeacherUser(user)) {
    return teacherSelfServicePaths.has(path) ? null : getDefaultLandingPath(user);
  }
  if (isStudentUser(user)) {
    return studentSelfServicePaths.has(path) ? null : getDefaultLandingPath(user);
  }
  return null;
}

const router = createRouter({
  history: isElectron ? createWebHashHistory() : createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({
    top: 0,
  }),
});

router.beforeEach((to) => {
  const hasToken = Boolean(getStoredAuthToken());
  const storedUser = getStoredAuthUser();
  if (to.name === 'login' && hasToken) {
    return getDefaultLandingPath(storedUser);
  }
  if (to.name !== 'login' && !hasToken) {
    return {
      name: 'login',
      query: {
        redirect: to.fullPath,
      },
    };
  }
  const selfServiceFallbackPath = getSelfServiceFallbackPath(storedUser, to.path);
  if (selfServiceFallbackPath && to.path !== selfServiceFallbackPath) {
    return selfServiceFallbackPath;
  }
  if (adminConfigurationPaths.has(to.path) && !isAdminUser(storedUser)) {
    return getDefaultLandingPath(storedUser);
  }
  return true;
});

router.afterEach((to) => {
  document.title = `${to.meta.title ?? 'Admin'} - ${APP_SYSTEM_NAME}`;
});

export default router;
