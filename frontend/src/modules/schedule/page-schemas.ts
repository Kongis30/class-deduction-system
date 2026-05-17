import type { ScheduleWorkspaceSchema } from '@/modules/shared/workspace-types';
import { createTag } from '@/modules/shared/workspace-builders';

export const recurringScheduleSchema: ScheduleWorkspaceSchema = {
  tableTitle: 'Recurring Schedule',
  filters: [
    { key: 'studentName', label: 'Student', type: 'input', placeholder: 'Search by student name' },
    { key: 'teacherName', label: 'Instructor', type: 'input', placeholder: 'Search by instructor name' },
    { key: 'packageName', label: 'Package', type: 'input', placeholder: 'Search by package name' },
    {
      key: 'classroomName',
      label: 'Classroom',
      type: 'select',
      placeholder: 'Select a classroom',
      options: [{ label: 'A101', value: 'A101' }, { label: 'C302', value: 'C302' }],
    },
    { key: 'dateRange', label: 'Date range', type: 'date-range', placeholder: 'Select a date range' },
  ],
  actions: [
    { label: 'Generate future lessons', buttonType: 'primary' },
    { label: 'Run conflict check', buttonType: 'warning' },
    { label: 'Save as template', plain: true },
  ],
  boardColumns: [
    {
      title: 'Tuesday',
      subtitle: '04/08/2026',
      cards: [
        {
          time: '6:00 PM to 7:00 PM ET',
          title: 'Ava Lin / Math Enrichment',
          subtitle: 'Ethan Chen · A101',
          status: createTag('Scheduled', 'success'),
          details: ['Primary lesson', 'Deduct 1 unit', 'Ready to copy into later weeks'],
        },
        {
          time: '7:00 PM to 8:00 PM ET',
          title: 'A101 conflict example',
          subtitle: 'A trial session is already using this room',
          status: createTag('Room conflict', 'danger'),
          details: ['Try A102 instead', 'Recheck instructor availability before saving'],
        },
      ],
    },
    {
      title: 'Thursday',
      subtitle: '04/10/2026',
      cards: [
        {
          time: '6:00 PM to 7:00 PM ET',
          title: 'Ava Lin / Math Enrichment',
          subtitle: 'Ethan Chen · A101',
          status: createTag('Scheduled', 'success'),
          details: ['Primary lesson', 'Package balance is sufficient'],
        },
      ],
    },
    {
      title: 'Saturday',
      subtitle: '04/12/2026',
      cards: [
        {
          time: '10:00 AM to 11:30 AM ET',
          title: 'Noah Zhang / SHSAT Prep',
          subtitle: 'Olivia Wu · C302',
          status: createTag('Needs review', 'warning'),
          details: ['Makeup hold needs confirmation', 'May affect peak room usage'],
        },
      ],
    },
  ],
  legend: [
    createTag('Scheduled', 'success'),
    createTag('Needs review', 'warning'),
    createTag('Room conflict', 'danger'),
    createTag('Makeup hold', 'info'),
  ],
};

export const scheduleAdjustmentSchema: ScheduleWorkspaceSchema = {
  tableTitle: 'Adjustments & Makeup Classes',
  filters: [
    { key: 'studentName', label: 'Student', type: 'input', placeholder: 'Search by student name' },
    { key: 'teacherName', label: 'Instructor', type: 'input', placeholder: 'Search by instructor name' },
    {
      key: 'exceptionStatus',
      label: 'Exception type',
      type: 'select',
      placeholder: 'Select an exception type',
      options: [{ label: 'Leave approved', value: 'Leave approved' }, { label: 'Instructor canceled', value: 'Instructor canceled' }, { label: 'Makeup in progress', value: 'Makeup in progress' }],
    },
    { key: 'dateRange', label: 'Date range', type: 'date-range', placeholder: 'Select a date range' },
  ],
  actions: [
    { label: 'Create makeup lesson', buttonType: 'primary' },
    { label: 'Resolve selected exceptions', buttonType: 'warning' },
  ],
  boardColumns: [
    {
      title: 'Open items',
      subtitle: 'This week',
      cards: [
        {
          time: 'Thursday 6:00 PM ET',
          title: 'Ava Lin / Leave approved',
          subtitle: 'Original room A101, original instructor Ethan Chen',
          status: createTag('Makeup needed', 'danger'),
          details: ['Approved leave does not deduct units', 'Suggested makeup slot: Sunday 10:00 AM ET'],
        },
        {
          time: 'Saturday 10:00 AM ET',
          title: 'Noah Zhang / Instructor canceled',
          subtitle: 'Original room C302, original instructor Olivia Wu',
          status: createTag('Reschedule needed', 'warning'),
          details: ['A new instructor and room still need confirmation', 'Recommended to rebook within the same week'],
        },
      ],
    },
    {
      title: 'Recommended makeup',
      subtitle: 'Suggested time slots',
      cards: [
        {
          time: 'Sunday 10:00 AM ET',
          title: 'Ava Lin / Makeup option',
          subtitle: 'Ethan Chen · A102',
          status: createTag('Ready to book', 'success'),
          details: ['Instructor is free', 'Room is available', 'Package balance is sufficient'],
        },
      ],
    },
    {
      title: 'Completed',
      subtitle: 'Closed this week',
      cards: [
        {
          time: 'Wednesday 7:00 PM ET',
          title: 'Sophia Wang / Substitute completed',
          subtitle: 'Substitute instructor: Daniel Park',
          status: createTag('Closed', 'success'),
          details: ['Lesson status already synced', 'Follow-up email still pending'],
        },
      ],
    },
  ],
  legend: [
    createTag('Makeup needed', 'danger'),
    createTag('Reschedule needed', 'warning'),
    createTag('Ready to book', 'success'),
    createTag('Closed', 'info'),
  ],
};

export const teacherScheduleSchema: ScheduleWorkspaceSchema = {
  tableTitle: 'Instructor Schedule',
  filters: [
    { key: 'teacherName', label: 'Instructor', type: 'input', placeholder: 'Search by instructor name' },
    { key: 'dateRange', label: 'Date range', type: 'date-range', placeholder: 'Select a date range' },
  ],
  actions: [
    { label: 'Export instructor schedule', buttonType: 'primary' },
    { label: 'Email schedule to instructor', plain: true },
  ],
  boardColumns: [
    {
      title: 'Tuesday',
      subtitle: '04/08/2026',
      cards: [
        {
          time: '6:00 PM to 7:00 PM ET',
          title: 'Ava Lin / Math Enrichment',
          subtitle: 'A101',
          status: createTag('Confirmed', 'success'),
          details: ['Standard lesson', 'Family confirmed attendance'],
        },
        {
          time: '7:20 PM to 8:20 PM ET',
          title: 'Lucas Zhao / Algebra Intensive',
          subtitle: 'A102',
          status: createTag('Confirmed', 'success'),
          details: ['Private lesson', 'Balance review due next week'],
        },
      ],
    },
    {
      title: 'Thursday',
      subtitle: '04/10/2026',
      cards: [
        {
          time: '6:00 PM to 7:00 PM ET',
          title: 'Ava Lin / Leave approved',
          subtitle: 'A101',
          status: createTag('Exception', 'warning'),
          details: ['No deduction by default', 'Needs makeup follow-up'],
        },
      ],
    },
    {
      title: 'Sunday',
      subtitle: '04/13/2026',
      cards: [
        {
          time: '10:00 AM to 11:00 AM ET',
          title: 'Ava Lin / Makeup option',
          subtitle: 'A102',
          status: createTag('Open slot', 'info'),
          details: ['Proposed makeup time', 'Room is currently available'],
        },
      ],
    },
  ],
  legend: [
    createTag('Confirmed', 'success'),
    createTag('Exception', 'warning'),
    createTag('Open slot', 'info'),
  ],
};

export const classroomOccupancySchema: ScheduleWorkspaceSchema = {
  tableTitle: 'Classroom Occupancy',
  filters: [
    { key: 'classroomName', label: 'Classroom', type: 'input', placeholder: 'Search by classroom name' },
    { key: 'teacherName', label: 'Instructor', type: 'input', placeholder: 'Search by instructor name' },
    { key: 'dateRange', label: 'Date range', type: 'date-range', placeholder: 'Select a date range' },
  ],
  actions: [
    { label: 'Export occupancy view', buttonType: 'primary' },
    { label: 'Check room conflicts', plain: true },
  ],
  boardColumns: [
    {
      title: 'A101',
      subtitle: 'Tuesday',
      cards: [
        {
          time: '6:00 PM to 7:00 PM ET',
          title: 'Ava Lin / Math Enrichment',
          subtitle: 'Ethan Chen',
          status: createTag('Booked', 'success'),
          details: ['Primary room assignment', 'Package PKG-202604-001'],
        },
        {
          time: '7:00 PM to 8:00 PM ET',
          title: 'Trial lesson overlap',
          subtitle: 'Separate enrollment request',
          status: createTag('Conflict', 'danger'),
          details: ['Move this booking or change the room before saving'],
        },
      ],
    },
    {
      title: 'B201',
      subtitle: 'Thursday',
      cards: [
        {
          time: '6:30 PM to 7:30 PM ET',
          title: 'Reading Support Group',
          subtitle: 'Grace Liu',
          status: createTag('Booked', 'success'),
          details: ['Group lesson', 'Capacity still available'],
        },
      ],
    },
    {
      title: 'C302',
      subtitle: 'Saturday',
      cards: [
        {
          time: '10:00 AM to 11:30 AM ET',
          title: 'SHSAT Prep Cohort',
          subtitle: 'Olivia Wu',
          status: createTag('Booked', 'success'),
          details: ['Weekend peak slot', 'No conflict at the moment'],
        },
      ],
    },
  ],
  legend: [
    createTag('Booked', 'success'),
    createTag('Conflict', 'danger'),
  ],
};
