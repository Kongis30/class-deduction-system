import http from './http';
import type { ApiResponse } from '@/types/http';
import type { EntityId } from '@/types/domain';

export interface RenewalReminderSettings {
  expiryReminderDays: number;
  oneOnOneRemainingLessons: number;
}

export interface RenewalReminderItem {
  reminderId: string;
  reminderType: 'ONE_ON_ONE' | 'GROUP';
  studentId: EntityId;
  studentName: string;
  guardianName?: string;
  guardianEmail?: string;
  packageId?: EntityId;
  classEnrollmentId?: EntityId;
  classId?: EntityId;
  courseName: string;
  courseType: 'ONE_ON_ONE' | 'GROUP';
  expectedEndDate?: string;
  daysUntilEnd?: number;
  remainingHours?: number;
  remainingLessons?: number;
  status: 'EXPIRED' | 'EXPIRING_SOON' | 'NEEDS_RENEWAL';
  reason: string;
  emailAvailable: boolean;
  emailSubject: string;
  emailBody: string;
}

export interface RenewalReminderOverview {
  settings: RenewalReminderSettings;
  emailReady: boolean;
  senderAddress?: string;
  reminders: RenewalReminderItem[];
}

export interface RenewalReminderSendResult {
  requestedCount: number;
  sentCount: number;
  failedCount: number;
  items: Array<{
    reminderId: string;
    studentName?: string;
    recipient?: string;
    sent: boolean;
    message: string;
  }>;
}

export function fetchRenewalReminders() {
  return http.get<ApiResponse<RenewalReminderOverview>>('/v1/renewal-reminders');
}

export function updateRenewalReminderSettings(settings: RenewalReminderSettings) {
  return http.put<ApiResponse<RenewalReminderOverview>>('/v1/renewal-reminders/settings', settings);
}

export function sendRenewalReminders(reminderIds: string[]) {
  return http.post<ApiResponse<RenewalReminderSendResult>>('/v1/renewal-reminders/send', {
    reminderIds,
    previewConfirmed: true,
  });
}
