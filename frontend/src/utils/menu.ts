import type { AppMenuItem } from '@/types/menu';
import { isTeacherUser, type AuthUser } from '@/types/auth';

export const appMenus: AppMenuItem[] = [
  {
    key: 'dashboard',
    title: 'Dashboard',
    icon: 'HomeFilled',
    path: '/dashboard',
  },
  {
    key: 'base-data',
    title: 'Base Data',
    icon: 'Files',
    children: [
      { key: 'students', title: 'Students', path: '/base-data/students' },
      { key: 'teachers', title: 'Teachers', path: '/base-data/teachers' },
      { key: 'classrooms', title: 'Classrooms', path: '/base-data/classrooms' },
      { key: 'campuses', title: 'Campuses', path: '/base-data/campuses' },
      { key: 'course-products', title: 'Course Products', path: '/base-data/course-products' },
    ],
  },
  {
    key: 'enrollment',
    title: 'Enrollment',
    icon: 'Tickets',
    children: [
      { key: 'orders', title: 'Enrollment Orders', path: '/enrollment/orders' },
      { key: 'packages', title: 'Course Packages', path: '/enrollment/packages' },
    ],
  },
  {
    key: 'schedule',
    title: 'Scheduling',
    icon: 'Calendar',
    children: [
      { key: 'recurring', title: 'Schedule Rules', path: '/schedule/recurring' },
      { key: 'adjustments', title: 'Confirm', path: '/schedule/adjustments' },
      { key: 'teacher-schedule', title: 'Teacher Calendar', path: '/schedule/teacher' },
      { key: 'classroom-schedule', title: 'Room Calendar', path: '/schedule/classrooms' },
      { key: 'student-schedule', title: 'Student Calendar', path: '/schedule/student' },
    ],
  },
  {
    key: 'settings',
    title: 'Settings',
    icon: 'Setting',
    children: [
      { key: 'rules', title: 'Lesson Rules', path: '/system/rules' },
      { key: 'notifications', title: 'Notifications', path: '/system/notifications' },
      { key: 'brand', title: 'Brand Settings', path: '/system/brand' },
      { key: 'accounts', title: 'Accounts & Permissions', path: '/system/accounts' },
    ],
  },
];

/**
 * Returns menus filtered for a teacher user.
 * Teachers only see the Scheduling section with all schedule views,
 * and can confirm lessons for any teacher (with a warning for other teachers' lessons).
 */
export function getTeacherMenus(): AppMenuItem[] {
  return [
    {
      key: 'schedule',
      title: 'Scheduling',
      icon: 'Calendar',
      children: [
        { key: 'adjustments', title: 'Confirm', path: '/schedule/adjustments' },
        { key: 'teacher-schedule', title: 'Teacher Calendar', path: '/schedule/teacher' },
        { key: 'classroom-schedule', title: 'Room Calendar', path: '/schedule/classrooms' },
        { key: 'student-schedule', title: 'Student Calendar', path: '/schedule/student' },
      ],
    },
  ];
}

/**
 * Returns the appropriate menus for the current user.
 */
export function getMenusForUser(user: AuthUser | null): AppMenuItem[] {
  if (isTeacherUser(user)) {
    return getTeacherMenus();
  }
  return appMenus;
}
