import type { AppMenuItem } from '@/types/menu';
import { isAdminUser, isStudentUser, isTeacherUser, type AuthUser } from '@/types/auth';

export const dailyMenus: AppMenuItem[] = [
  {
    key: 'registration',
    title: 'Registration',
    icon: 'Tickets',
    path: '/registration',
  },
  {
    key: 'calendar',
    title: 'Calendar',
    icon: 'Calendar',
    path: '/calendar',
  },
  {
    key: 'one-on-one-deduction',
    title: '1-on-1 Lessons',
    icon: 'Tickets',
    path: '/one-on-one-deduction',
  },
  {
    key: 'group-class-lessons',
    title: 'Group Class Lessons',
    icon: 'Calendar',
    path: '/group-class-lessons',
  },
  {
    key: 'data-summary',
    title: 'Data Summary',
    icon: 'DataAnalysis',
    path: '/data-summary',
  },
];

export const adminPeopleMenu: AppMenuItem = {
  key: 'admin-people',
  title: 'People',
  icon: 'User',
  children: [
    { key: 'config-students', title: 'Students', description: 'Profiles and contacts', path: '/admin/config/students' },
    { key: 'config-teachers', title: 'Teachers', description: 'Teacher profiles', path: '/admin/config/teachers' },
    { key: 'config-accounts', title: 'Accounts', description: 'User access', path: '/admin/config/accounts' },
  ],
};

export const adminEnrollmentMenu: AppMenuItem = {
  key: 'admin-enrollment',
  title: 'Enrollment',
  icon: 'Tickets',
  children: [
    { key: 'enrollment-orders', title: 'Enrollments', description: 'Registration records', path: '/enrollment/orders' },
    { key: 'course-packages', title: 'Packages', description: 'Balance and hours', path: '/enrollment/packages' },
    { key: 'receipt-history', title: 'Receipts', description: 'Registration forms by student', path: '/enrollment/receipts' },
  ],
};

export const adminCoursesMenu: AppMenuItem = {
  key: 'admin-courses',
  title: 'Courses',
  icon: 'Files',
  children: [
    { key: 'course-products', title: 'Products', description: 'Course products', path: '/base-data/course-products' },
    { key: 'course-prices', title: 'Prices', description: 'Grade, duration, price', path: '/admin/config/course-prices' },
    { key: 'schedule-rules', title: 'Schedule', description: 'Recurring class rules', path: '/schedule/recurring' },
  ],
};

export const adminFinanceMenu: AppMenuItem = {
  key: 'admin-finance',
  title: 'Finance',
  icon: 'Money',
  children: [
    { key: 'config-teacher-pay-rates', title: 'Pay rates', description: 'Teacher hourly rates', path: '/admin/config/teacher-pay-rates' },
    { key: 'config-payment-methods', title: 'Payments', description: 'Payment options', path: '/admin/config/payment-methods' },
  ],
};

export const adminSystemMenu: AppMenuItem = {
  key: 'admin-system',
  title: 'System',
  icon: 'Setting',
  children: [
    { key: 'admin-guide', title: 'Guide', description: 'How to use the system', path: '/admin/guide' },
    { key: 'config-receipt', title: 'Brand & receipts', description: 'Brand, receipts, and EIN', path: '/admin/config/receipt' },
    { key: 'config-campuses', title: 'Campuses', description: 'Locations and rooms', path: '/admin/config/campuses' },
    { key: 'config-notifications', title: 'Alerts', description: 'Notification rules', path: '/admin/config/notifications' },
  ],
};

/**
 * Returns menus filtered for a teacher user.
 * Teachers only see their own calendar and lesson workspace.
 */
export function getTeacherMenus(): AppMenuItem[] {
  return [
    {
      key: 'my-teacher-calendar',
      title: 'My Calendar',
      icon: 'Calendar',
      path: '/calendar',
    },
    {
      key: 'my-lessons',
      title: 'My Lessons',
      icon: 'Calendar',
      path: '/my-lessons',
    },
  ];
}

/**
 * Returns menus filtered for a student user.
 * Students only see their own calendar.
 */
export function getStudentMenus(): AppMenuItem[] {
  return [
    {
      key: 'my-student-calendar',
      title: 'My Calendar',
      icon: 'Calendar',
      path: '/calendar',
    },
  ];
}

/**
 * Returns low-frequency navigation separated from daily work entries.
 */
export function getSecondaryMenusForUser(user: AuthUser | null): AppMenuItem[] {
  return isAdminUser(user)
    ? [adminPeopleMenu, adminEnrollmentMenu, adminCoursesMenu, adminFinanceMenu, adminSystemMenu]
    : [];
}

/**
 * Returns the appropriate menus for the current user.
 */
export function getMenusForUser(user: AuthUser | null): AppMenuItem[] {
  if (isStudentUser(user)) {
    return getStudentMenus();
  }
  if (isTeacherUser(user)) {
    return getTeacherMenus();
  }
  return dailyMenus;
}
