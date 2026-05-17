import type { ListWorkspaceSchema, SettingsWorkspaceSchema } from '@/modules/shared/workspace-types';
import { createTag } from '@/modules/shared/workspace-builders';

export const lessonRuleListSchema: ListWorkspaceSchema = {
  tableTitle: 'Lesson Status Rules',
  filters: [
    { key: 'statusName', label: 'Status name', type: 'input', placeholder: 'Search by status name' },
    {
      key: 'defaultShouldDeduct',
      label: 'Deduction behavior',
      type: 'select',
      placeholder: 'Select deduction behavior',
      options: [{ label: 'Deduct units', value: 'Deduct units' }, { label: 'Do not deduct', value: 'Do not deduct' }],
    },
    {
      key: 'status',
      label: 'Rule status',
      type: 'select',
      placeholder: 'Select rule status',
      options: [{ label: 'Active', value: 'Active' }, { label: 'Inactive', value: 'Inactive' }],
    },
  ],
  actions: [
    { label: 'Add lesson rule', buttonType: 'primary' },
    { label: 'Export rules', plain: true },
  ],
  columns: [
    { key: 'statusCode', label: 'Status code', minWidth: 120 },
    { key: 'statusName', label: 'Status name', minWidth: 140, align: 'center' },
    { key: 'defaultShouldDeduct', label: 'Deduct units', minWidth: 110, align: 'center' },
    { key: 'deductMode', label: 'Deduction mode', minWidth: 120, align: 'center' },
    { key: 'defaultShouldExtend', label: 'Extend schedule', minWidth: 120, align: 'center' },
    { key: 'allowManualOverride', label: 'Manual override', minWidth: 120, align: 'center' },
    { key: 'status', label: 'Rule status', minWidth: 100, align: 'center' },
  ],
  rows: [
    {
      id: 'rule-1',
      statusCode: 'COMPLETED',
      statusName: 'Completed',
      defaultShouldDeduct: createTag('Deduct units', 'success'),
      deductMode: 'Actual units',
      defaultShouldExtend: createTag('Do not extend', 'info'),
      allowManualOverride: createTag('No', 'info'),
      status: createTag('Active', 'success'),
    },
    {
      id: 'rule-2',
      statusCode: 'LEAVE_APPROVED',
      statusName: 'Leave approved',
      defaultShouldDeduct: createTag('Do not deduct', 'info'),
      deductMode: 'Not applicable',
      defaultShouldExtend: createTag('Extend', 'warning'),
      allowManualOverride: createTag('Yes', 'warning'),
      status: createTag('Active', 'success'),
    },
    {
      id: 'rule-3',
      statusCode: 'NO_SHOW',
      statusName: 'No-show',
      defaultShouldDeduct: createTag('Deduct units', 'danger'),
      deductMode: 'Fixed units',
      defaultShouldExtend: createTag('Do not extend', 'success'),
      allowManualOverride: createTag('No', 'info'),
      status: createTag('Active', 'success'),
    },
  ],
};

export const notificationSettingsSchema: SettingsWorkspaceSchema = {
  tableTitle: 'Notifications',
  sections: [
    {
      title: 'Delivery settings',
      description: 'Later this section can hold SMTP or third-party email configuration.',
      fields: [
        { label: 'Notification switch', value: 'Enabled', hint: 'Turn this off to pause all automated sends' },
        { label: 'Default sender', value: 'noreply@jinbesttutoring.com', hint: 'Replace with the production mailbox when ready' },
        { label: 'Retry policy', value: 'Retry twice after 10 minutes', hint: 'A simple starter rule for the MVP' },
        { label: 'Staff copy', value: 'Enabled', hint: 'Send key messages to the internal operations inbox' },
      ],
    },
    {
      title: 'Lesson recap template',
      description: 'This content block can later become a rich-text or token-based editor.',
      fields: [
        { label: 'Subject line', value: 'Lesson recap and remaining units update', hint: 'Support student, course, and balance tokens' },
        { label: 'Body summary', value: 'Show lesson result, deducted units, remaining balance, and next class time in ET', hint: 'Keep it easy for families to scan' },
        { label: 'Send trigger', value: 'Automatically after lesson confirmation', hint: 'Depends on the lesson record status' },
        { label: 'Internal copy target', value: 'Operations inbox', hint: 'Helps staff track delivery results' },
      ],
    },
  ],
  actions: [
    { label: 'Save settings', buttonType: 'primary' },
    { label: 'Send test email', buttonType: 'success' },
  ],
};

export const brandSettingsSchema: SettingsWorkspaceSchema = {
  tableTitle: 'Brand Settings',
  sections: [
    {
      title: 'Brand basics',
      description: 'This section should eventually become the main brand settings form.',
      fields: [
        { label: 'Business name', value: 'JIN BEST TUTORING', hint: 'Use this exact capitalization in visible UI' },
        { label: 'Admin title', value: 'JIN BEST TUTORING Admin', hint: 'Use in login, title bar, and navigation' },
        { label: 'Primary timezone', value: 'America/New_York', hint: 'All user-facing dates and times should follow ET' },
        { label: 'Primary color', value: '#18B58A', hint: 'The current shell already uses this brand accent' },
      ],
    },
    {
      title: 'Templates and assets',
      description: 'Link these settings to receipts, emails, and the login experience.',
      fields: [
        { label: 'Logo asset', value: 'Production file pending upload', hint: 'The UI currently uses a text-based placeholder' },
        { label: 'Receipt template', value: 'Standard receipt v1', hint: 'Future work can add header, signature, and QR settings' },
        { label: 'Email signature', value: 'JIN BEST TUTORING Operations', hint: 'Used by notification templates' },
        { label: 'Login subtitle', value: 'Manage scheduling, enrollments, and lesson tracking', hint: 'Keep messaging short and professional' },
      ],
    },
  ],
  actions: [
    { label: 'Save brand settings', buttonType: 'primary' },
    { label: 'Preview receipt template', plain: true },
  ],
};

export const accountSettingsSchema: SettingsWorkspaceSchema = {
  tableTitle: 'Accounts & Permissions',
  sections: [
    {
      title: 'Role overview',
      description: 'Make role boundaries easy to read before the permission editor is built.',
      fields: [
        { label: 'Administrator', value: 'System settings, brand settings, and account management', hint: 'Full configuration access' },
        { label: 'Operations staff', value: 'Base data, enrollment, scheduling, lessons, and exports', hint: 'Primary phase-one role' },
        { label: 'Instructor role', value: 'View personal schedule and confirm own lessons only', hint: 'Reserved for a later release' },
        { label: 'Disabled account policy', value: 'Disable instead of deleting to keep audit history', hint: 'Recommended for traceability' },
      ],
    },
    {
      title: 'Permission model guidance',
      description: 'Later this can expand into menu-level, action-level, and data-scope permissions.',
      fields: [
        { label: 'Menu access', value: 'Open features by business domain', hint: 'For example, scheduling can be operations-only' },
        { label: 'Action access', value: 'Split save, export, and configuration actions', hint: 'Reduce accidental changes' },
        { label: 'Data scope', value: 'Future instructor role should only see personal schedule data', hint: 'Keeps scope narrow and safe' },
        { label: 'Audit log', value: 'Track account and time for high-risk changes', hint: 'Required for sensitive settings' },
      ],
    },
  ],
  actions: [
    { label: 'Add account', buttonType: 'primary' },
    { label: 'Export permission matrix', plain: true },
  ],
};
