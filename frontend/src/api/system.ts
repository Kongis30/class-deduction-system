import http from './http';
import type { ApiResponse, PageResult } from '@/types/http';
import type { BrandConfigItem, EntityId, NotificationTemplateItem, NotificationConfigItem, NotificationTriggerRuleItem, SystemMessageInbox } from '@/types/domain';

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

export interface NotificationTemplateUpdatePayload {
  templateName: string;
  subjectTemplate: string;
  bodyTemplate: string;
  status?: string;
  remark?: string;
}

export function updateNotificationTemplate(id: EntityId, data: NotificationTemplateUpdatePayload) {
  return http.put<ApiResponse<NotificationTemplateItem>>(`/v1/system/notification-templates/${id}`, data);
}

// ─── Email Sender Config ────────────────────────────────

export interface NotificationConfigUpdatePayload {
  enabled: boolean;
  fromAddress: string;
  fromName: string;
  authCode?: string;
  strictAllowlist: boolean;
  allowedRecipients: string;
  lowBalanceThreshold: number;
  lowBalanceRecipients: string;
  smtpHost?: string;
  smtpPort?: number;
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

// ─── In-app messages ───────────────────────────────────

export function fetchSystemMessageInbox() {
  return http.get<ApiResponse<SystemMessageInbox>>('/v1/system/messages');
}

export function markSystemMessageRead(id: EntityId) {
  return http.put<ApiResponse<null>>(`/v1/system/messages/${id}/read`);
}

// ─── Trigger Rules ──────────────────────────────────────

export interface NotificationTriggerRuleUpdatePayload {
  enabled: boolean;
  templateId?: EntityId | null;
  recipientType: string;
  customRecipients: string;
  thresholdValue: number | null;
  remark?: string;
}

export function fetchNotificationTriggerRules() {
  return http.get<ApiResponse<NotificationTriggerRuleItem[]>>('/v1/system/notification-trigger-rules');
}

export function updateNotificationTriggerRule(id: EntityId, data: NotificationTriggerRuleUpdatePayload) {
  return http.put<ApiResponse<NotificationTriggerRuleItem>>(`/v1/system/notification-trigger-rules/${id}`, data);
}
