import http from './http';
import type { ApiResponse, PageResult } from '@/types/http';
import type { BrandConfigItem, LessonStatusRuleItem, NotificationTemplateItem, NotificationConfigItem, NotificationTriggerRuleItem } from '@/types/domain';

// ─── Lesson Status Rules ────────────────────────────

export interface LessonStatusRuleQuery {
  statusCode?: string;
  statusName?: string;
  status?: string;
  pageNum?: number;
  pageSize?: number;
}

export function fetchLessonStatusRules(params: LessonStatusRuleQuery = {}) {
  return http.get<ApiResponse<PageResult<LessonStatusRuleItem>>>('/v1/system/lesson-status-rules', { params });
}

export function fetchLessonStatusRule(id: number) {
  return http.get<ApiResponse<LessonStatusRuleItem>>(`/v1/system/lesson-status-rules/${id}`);
}

export interface LessonStatusRuleUpdatePayload {
  statusName: string;
  defaultShouldDeduct: boolean;
  deductMode: string;
  fixedUnits?: number;
  defaultShouldExtend: boolean;
  allowManualOverride: boolean;
  status?: string;
  remark?: string;
}

export function updateLessonStatusRule(id: number, data: LessonStatusRuleUpdatePayload) {
  return http.put<ApiResponse<LessonStatusRuleItem>>(`/v1/system/lesson-status-rules/${id}`, data);
}

// ─── Brand Config ───────────────────────────────────

export function fetchBrandConfig() {
  return http.get<ApiResponse<BrandConfigItem>>('/v1/system/brand-config/current');
}

export interface BrandConfigUpdatePayload {
  brandName: string;
  logoUrl?: string;
  themeColor?: string;
  supportEmail?: string;
  receiptFooterText?: string;
  status?: string;
}

export function updateBrandConfig(data: BrandConfigUpdatePayload) {
  return http.put<ApiResponse<BrandConfigItem>>('/v1/system/brand-config/current', data);
}

// ─── Notification Templates ─────────────────────────

export interface NotificationTemplateQuery {
  keyword?: string;
  channelType?: string;
  status?: string;
  pageNum?: number;
  pageSize?: number;
}

export function fetchNotificationTemplates(params: NotificationTemplateQuery = {}) {
  return http.get<ApiResponse<PageResult<NotificationTemplateItem>>>('/v1/system/notification-templates', { params });
}

export function fetchNotificationTemplate(id: number) {
  return http.get<ApiResponse<NotificationTemplateItem>>(`/v1/system/notification-templates/${id}`);
}

export interface NotificationTemplateUpdatePayload {
  templateName: string;
  subjectTemplate: string;
  bodyTemplate: string;
  status?: string;
  remark?: string;
}

export function updateNotificationTemplate(id: number, data: NotificationTemplateUpdatePayload) {
  return http.put<ApiResponse<NotificationTemplateItem>>(`/v1/system/notification-templates/${id}`, data);
}

// ─── Email Sender Config ────────────────────────────────

export interface NotificationConfigUpdatePayload {
  enabled: boolean;
  fromAddress: string;
  fromName: string;
  authCode: string;
  strictAllowlist: boolean;
  allowedRecipients: string;
  lowBalanceThreshold: number;
  lowBalanceRecipients: string;
}

export function fetchNotificationConfig() {
  return http.get<ApiResponse<NotificationConfigItem>>('/v1/system/notification-config');
}

export function updateNotificationConfig(data: NotificationConfigUpdatePayload) {
  return http.put<ApiResponse<NotificationConfigItem>>('/v1/system/notification-config', data);
}

export function sendTestEmail() {
  return http.post<ApiResponse<null>>('/v1/system/notification-config/test');
}

// ─── Trigger Rules ──────────────────────────────────────

export interface NotificationTriggerRuleUpdatePayload {
  enabled: boolean;
  recipientType: string;
  customRecipients: string;
  thresholdValue: number | null;
  remark?: string;
}

export function fetchNotificationTriggerRules() {
  return http.get<ApiResponse<NotificationTriggerRuleItem[]>>('/v1/system/notification-trigger-rules');
}

export function updateNotificationTriggerRule(id: number, data: NotificationTriggerRuleUpdatePayload) {
  return http.put<ApiResponse<NotificationTriggerRuleItem>>(`/v1/system/notification-trigger-rules/${id}`, data);
}

// ─── Broadcast ──────────────────────────────────────────

export interface NotificationBroadcastPayload {
  subject: string;
  body: string;
  recipients?: string[];
  recipientPreset?: 'ALL_STUDENTS' | 'ALL_ENROLLED_STUDENTS' | 'ALL_TEACHERS' | 'ALL_ADMIN_USERS' | 'CUSTOM';
  studentKeyword?: string;
}

export interface BroadcastResult {
  totalRecipients: number;
  queuedCount: number;
  recipientPreview: string[];
}

export function broadcastNotification(data: NotificationBroadcastPayload) {
  return http.post<ApiResponse<BroadcastResult>>('/v1/system/notifications/broadcast', data);
}
