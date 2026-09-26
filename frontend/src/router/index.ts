import { createRouter, createWebHistory, createWebHashHistory, type RouteRecordRaw } from 'vue-router';

import AppLayout from '@/components/layout/AppLayout.vue';
import { APP_SYSTEM_NAME } from '@/config/app-config';
import { getStoredAuthToken, getStoredAuthUser } from '@/utils/auth-storage';
import { getDefaultLandingPath, getUserWorkspace, isAdminOrStaffUser, isAdminUser, isManagerUser, type AuthUser } from '@/types/auth';

// Route-level lazy loading keeps the initial admin bundle smaller.
const LoginView = () => import('@/views/LoginView.vue');
const DashboardView = () => import('@/views/dashboard/DashboardView.vue');
const PeopleView = () => import('@/views/base-data/PeopleView.vue');
const ClassroomsView = () => import('@/views/base-data/ClassroomsView.vue');
const CourseProductsView = () => import('@/views/base-data/CourseProductsView.vue');
const CampusesView = () => import('@/views/base-data/CampusesView.vue');
const EnrollmentOrdersView = () => import('@/views/enrollment/EnrollmentOrdersView.vue');
const CoursePackagesView = () => import('@/views/enrollment/CoursePackagesView.vue');
const ReceiptRecordsView = () => import('@/views/enrollment/ReceiptRecordsView.vue');
const RegistrationIntakeView = () => import('@/views/registration/RegistrationIntakeView.vue');
const DataSummaryView = () => import('@/views/data-summary/DataSummaryView.vue');
const StudentCreditView = () => import('@/views/finance/StudentCreditView.vue');
const PickupDropoffListView = () => import('@/views/transport/PickupDropoffListView.vue');
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
const UserGuideView = () => import('@/views/system/UserGuideView.vue');
const TeacherPayRatesView = () => import('@/views/finance/TeacherPayRatesView.vue');
const TeacherPaymentsView = () => import('@/views/finance/TeacherPaymentsView.vue');
const MyPayrollView = () => import('@/views/finance/MyPayrollView.vue');
const DailyRegistrationsView = () => import('@/views/enrollment/DailyRegistrationsView.vue');
const ManagerAccountsView = () => import('@/views/system/ManagerAccountsView.vue');

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
        redirect: '/admin/config/people',
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
        redirect: '/admin/config/people',
      },
      {
        path: 'admin/config',
        redirect: '/admin/config/people',
      },
      {
        path: 'back-office',
        redirect: '/admin/config/people',
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
        path: 'daily-registrations',
        name: 'daily-registrations',
        component: DailyRegistrationsView,
        meta: {
          title: 'Daily Registrations',
          section: 'Daily Registrations',
          description: 'Review registrations by the time they were saved and trace their history.',
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
        path: 'my-payroll',
        name: 'my-payroll',
        component: MyPayrollView,
        meta: {
          title: 'Payment History',
          section: 'Payment History',
          description: 'Review your own payment history and lesson payment details.',
        },
      },
      {
        path: 'group-class-lessons',
        name: 'group-class-lessons',
        redirect: '/calendar',
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
        path: 'student-credit',
        name: 'student-credit',
        component: StudentCreditView,
        meta: {
          title: 'Student Credit',
          section: 'Student Credit',
          description: 'Review available student Credit balances and their source transactions.',
        },
      },
      {
        path: 'pickup-dropoff-list',
        name: 'pickup-dropoff-list',
        component: PickupDropoffListView,
        meta: {
          title: 'Pickup / Drop-off List',
          section: 'Pickup / Drop-off List',
          description: 'Review transport requirements and addresses by course and class.',
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
        path: 'admin/config/people',
        name: 'admin-config-people',
        component: PeopleView,
        meta: {
          title: 'Students & teachers',
          section: 'People',
          description: 'Manage student and teacher records with login access in one place.',
        },
      },
      {
        path: 'admin/config/managers',
        name: 'admin-config-managers',
        component: ManagerAccountsView,
        meta: {
          title: 'Manager accounts',
          section: 'People',
          description: 'Create and manage registration manager accounts.',
        },
      },
      {
        path: 'admin/config/students',
        name: 'admin-config-students',
        redirect: { name: 'admin-config-people', query: { tab: 'students' } },
      },
      {
        path: 'admin/config/teachers',
        name: 'admin-config-teachers',
        redirect: { name: 'admin-config-people', query: { tab: 'teachers' } },
      },
      {
        path: 'admin/config/course-prices',
        name: 'admin-config-course-prices',
        component: CoursePriceOptionsView,
        meta: {
          title: 'Prices',
          section: 'Courses',
          description: 'Configure structured course price options used by registration.',
        },
      },
      {
        path: 'admin/config/payment-methods',
        name: 'admin-config-payment-methods',
        component: PaymentMethodsView,
        meta: {
          title: 'Payment methods',
          section: 'Finance',
          description: 'Manage the payment method dictionary used by registration and enrollment.',
        },
      },
      {
        path: 'admin/config/receipt',
        name: 'admin-config-receipt',
        component: BrandSettingsView,
        meta: {
          title: 'Brand & receipts',
          section: 'System',
          description: 'Manage brand name, receipt footer text, and EIN settings.',
        },
      },
      {
        path: 'admin/config/campuses',
        name: 'admin-config-campuses',
        component: CampusesView,
        meta: {
          title: 'Campuses',
          section: 'System',
          description: 'Manage campus locations for classroom assignment.',
        },
      },
      {
        path: 'admin/config/accounts',
        name: 'admin-config-accounts',
        redirect: { name: 'admin-config-people' },
      },
      {
        path: 'admin/config/notifications',
        name: 'admin-config-notifications',
        component: NotificationSettingsView,
        meta: {
          title: 'Alerts',
          section: 'System',
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
          title: 'Pay rates',
          section: 'Finance',
          description: 'Configure standard teacher hourly rates and optional special rates.',
        },
      },
      {
        path: 'base-data/students',
        name: 'students',
        redirect: { name: 'admin-config-people', query: { tab: 'students' } },
      },
      {
        path: 'base-data/teachers',
        name: 'teachers',
        redirect: { name: 'admin-config-people', query: { tab: 'teachers' } },
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
          title: 'Products',
          section: 'Courses',
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
          title: 'Enrollments',
          section: 'Enrollment',
          description: 'Review historical registrations, record later payments, and open each saved receipt.',
        },
      },
      {
        path: 'enrollment/packages',
        name: 'course-packages',
        component: CoursePackagesView,
        meta: {
          title: 'Packages',
          section: 'Enrollment',
          description: 'Track package balance, assigned instructor, and estimated end dates.',
        },
      },
      {
        path: 'enrollment/receipts',
        name: 'receipt-records',
        component: ReceiptRecordsView,
        meta: {
          title: 'Receipts',
          section: 'Enrollment',
          description: 'Find each student, then open or print their saved receipts.',
        },
      },
      {
        path: 'schedule/recurring',
        name: 'recurring-schedule',
        component: RecurringScheduleView,
        meta: {
          title: 'Schedule',
          section: 'Courses',
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
          title: 'Alerts',
          section: 'System',
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
        redirect: { name: 'admin-config-people' },
      },
      {
        path: 'finance/pay-rates',
        name: 'pay-rates',
        component: TeacherPayRatesView,
        meta: {
          title: 'Pay rates',
          section: 'Finance',
          description: 'Configure standard teacher hourly rates and optional special rates.',
        },
      },
      {
        path: 'finance/payments',
        name: 'teacher-payments',
        component: TeacherPaymentsView,
        meta: {
          title: 'Teacher payroll',
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

const teacherSelfServicePaths = new Set(['/my-lessons', '/my-payroll', '/calendar']);
const studentSelfServicePaths = new Set(['/calendar']);
const adminConfigurationPaths = new Set([
  '/admin/config/people',
  '/admin/config/managers',
  '/admin/config/students',
  '/admin/config/teachers',
  '/admin/config/course-prices',
  '/admin/config/payment-methods',
  '/admin/config/receipt',
  '/admin/config/campuses',
  '/admin/config/notifications',
  '/admin/config/teacher-pay-rates',
  '/base-data/course-products',
  '/base-data/classrooms',
  '/schedule/recurring',
]);

function getSelfServiceFallbackPath(user: AuthUser | null, path: string): string | null {
  switch (getUserWorkspace(user)) {
    case 'TEACHER':
      return teacherSelfServicePaths.has(path) ? null : getDefaultLandingPath(user);
    case 'STUDENT':
      return studentSelfServicePaths.has(path) ? null : getDefaultLandingPath(user);
    default:
      return null;
  }
}

const router = createRouter({
  history: isElectron ? createWebHashHistory() : createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: (to, from, savedPosition) => {
    if (savedPosition) {
      return savedPosition;
    }
    if (to.path === from.path) {
      return false;
    }
    return { top: 0 };
  },
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
  if (adminConfigurationPaths.has(to.path) && !isAdminOrStaffUser(storedUser)) {
    return getDefaultLandingPath(storedUser);
  }
  if (to.path === '/admin/config/managers' && !isAdminUser(storedUser)) {
    return getDefaultLandingPath(storedUser);
  }
  if (to.name === 'registration' && to.query.orderId && isManagerUser(storedUser)) {
    return { name: 'receipt-records' };
  }
  return true;
});

router.afterEach((to) => {
  document.title = `${to.meta.title ?? 'Admin'} - ${APP_SYSTEM_NAME}`;
});

export default router;
