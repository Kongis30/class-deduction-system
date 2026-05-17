import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

import AppLayout from '@/components/layout/AppLayout.vue';
import { APP_SYSTEM_NAME } from '@/config/app-config';
import { getStoredAuthToken } from '@/utils/auth-storage';

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
const RecurringScheduleView = () => import('@/views/schedule/RecurringScheduleView.vue');
const ScheduleAdjustmentsView = () => import('@/views/schedule/ScheduleAdjustmentsView.vue');
const TeacherScheduleView = () => import('@/views/schedule/TeacherScheduleView.vue');
const ClassroomOccupancyView = () => import('@/views/schedule/ClassroomOccupancyView.vue');
const StudentScheduleView = () => import('@/views/schedule/StudentScheduleView.vue');
const LessonRulesView = () => import('@/views/system/LessonRulesView.vue');
const NotificationSettingsView = () => import('@/views/system/NotificationSettingsView.vue');
const BrandSettingsView = () => import('@/views/system/BrandSettingsView.vue');
const AccountSettingsView = () => import('@/views/system/AccountSettingsView.vue');

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
    redirect: '/dashboard',
    children: [
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
          title: 'Instructors',
          section: 'Base Data',
          description: 'Review instructor profiles, teaching details, and account linkage.',
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
          title: 'Enrollment Orders',
          section: 'Enrollment',
          description: 'Create student enrollments, record payments, and generate receipts.',
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
        path: 'schedule/recurring',
        name: 'recurring-schedule',
        component: RecurringScheduleView,
        meta: {
          title: 'Schedule Rules',
          section: 'Scheduling',
          description: 'Create recurring schedule rules and generate lesson occurrences.',
        },
      },
      {
        path: 'schedule/adjustments',
        name: 'schedule-adjustments',
        component: ScheduleAdjustmentsView,
        meta: {
          title: 'Confirm',
          section: 'Scheduling',
          description: 'Confirm lesson outcomes, record leave, reschedule, and manage makeups.',
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
        path: 'system/rules',
        name: 'lesson-rules',
        component: LessonRulesView,
        meta: {
          title: 'Lesson Rules',
          section: 'Settings',
          description: 'Manage deduction and extension rules for lesson outcomes.',
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
          title: 'Brand Settings',
          section: 'Settings',
          description: 'Manage brand name, logo, theme colors, and receipt footer settings.',
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
    ],
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({
    top: 0,
  }),
});

router.beforeEach((to) => {
  const hasToken = Boolean(getStoredAuthToken());
  if (to.name === 'login' && hasToken) {
    return {
      name: 'dashboard',
    };
  }
  if (to.name !== 'login' && !hasToken) {
    return {
      name: 'login',
      query: {
        redirect: to.fullPath,
      },
    };
  }
  return true;
});

router.afterEach((to) => {
  document.title = `${to.meta.title ?? 'Admin'} - ${APP_SYSTEM_NAME}`;
});

export default router;