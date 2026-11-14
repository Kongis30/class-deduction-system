import type { AppMenuItem } from '@/types/menu';
import { getUserWorkspace, isAdminOrStaffUser, isAdminUser, isManagerUser, type AuthUser } from '@/types/auth';

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
    key: 'data-summary',
    title: 'Data Summary',
    icon: 'DataAnalysis',
    path: '/data-summary',
  },
  {
    key: 'pickup-dropoff-list',
    title: 'Pickup / Drop-off List',
    icon: 'Van',
    path: '/pickup-dropoff-list',
  },
  {
    key: 'student-credit',
    title: 'Student Credit',
    icon: 'Money',
    path: '/student-credit',
  },
  {
    key: 'daily-registrations',
    title: 'Daily Registrations',
    icon: 'DataAnalysis',
    path: '/daily-registrations',
  },
  {
    key: 'monthly-revenue',
    title: 'Monthly Revenue Breakdown',
    icon: 'Money',
    path: '/monthly-revenue',
  },
];

export const adminGuideMenu: AppMenuItem = {
  key: 'admin-help',
  title: 'Help',
  icon: 'Files',
  children: [
    { key: 'admin-guide', title: 'User Guide', description: 'How to use the system', path: '/admin/guide' },
  ],
};

export const adminPeopleMenu: AppMenuItem = {
  key: 'admin-people',
  title: 'People',
  icon: 'User',
  children: [
    { key: 'config-people', title: 'Students & teachers', description: 'Profiles and login access', path: '/admin/config/people' },
  ],
};

export const adminEnrollmentMenu: AppMenuItem = {
  key: 'admin-enrollment',
  title: 'Enrollment',
  icon: 'Tickets',
  children: [
    { key: 'enrollment-orders', title: 'Enrollments', description: 'Registration records', path: '/enrollment/orders' },
    { key: 'course-packages', title: 'Packages', description: 'Balance and hours', path: '/enrollment/packages' },
    { key: 'receipt-history', title: 'Receipts', description: 'Saved receipts by student', path: '/enrollment/receipts' },
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
    { key: 'teacher-payments', title: 'Teacher payroll', description: 'Settle teacher pay', path: '/finance/payments' },
    { key: 'config-payment-methods', title: 'Payment methods', description: 'Accepted payment options', path: '/admin/config/payment-methods' },
  ],
};

export const adminSystemMenu: AppMenuItem = {
  key: 'admin-system',
  title: 'System',
  icon: 'Setting',
  children: [
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
    {
      key: 'my-payroll',
      title: 'Payment History',
      icon: 'DataAnalysis',
      path: '/my-payroll',
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
  if (!isAdminOrStaffUser(user)) return [];

  const menus = [adminGuideMenu, adminPeopleMenu, adminEnrollmentMenu, adminCoursesMenu, adminFinanceMenu, adminSystemMenu];
  if (!isManagerUser(user) || isAdminUser(user)) return menus;

  return menus
    .filter(menu => menu.key !== 'admin-finance' && menu.key !== 'admin-system')
    .map(menu => menu.key === 'admin-courses'
      ? {
          ...menu,
          children: menu.children?.filter(item => item.key !== 'course-products' && item.key !== 'course-prices'),
        }
      : menu);
}

/**
 * Returns the appropriate menus for the current user.
 */
export function getMenusForUser(user: AuthUser | null): AppMenuItem[] {
  switch (getUserWorkspace(user)) {
    case 'TEACHER':
      return getTeacherMenus();
    case 'STUDENT':
      return getStudentMenus();
    default:
      // 每日报名与月度收入都包含财务金额，仅对 MANAGER 隐藏（ADMIN 除外）。
      return dailyMenus.filter(menu => !['daily-registrations', 'monthly-revenue'].includes(menu.key)
        || !isManagerUser(user) || isAdminUser(user));
  }
}
