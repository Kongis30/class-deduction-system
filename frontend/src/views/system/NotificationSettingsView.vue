<template>
  <div class="settings-page">
    <el-tabs v-model="activeTab" class="settings-tabs">
      <el-tab-pane label="Events" name="events">
        <div class="panel">
          <div class="panel__header">
            <span class="panel__title">Notification Events</span>
            <span class="panel__hint">Turn notification events on or off and configure the email template used for each event.</span>
          </div>

          <el-table v-loading="eventsLoading" :data="eventRows" row-key="id" class="data-table">
            <el-table-column label="Event" min-width="220">
              <template #default="{ row }">
                <div class="event-cell">
                  <div class="event-cell__title">{{ row.eventName }}</div>
                  <div class="event-cell__desc">{{ row.description }}</div>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="Template" min-width="200">
              <template #default="{ row }">
                <div class="event-template-cell">
                  <div>{{ row.templateName }}</div>
                  <div class="event-template-cell__hint">{{ row.templateHint }}</div>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="Status" min-width="140" align="center">
              <template #default="{ row }">
                <div class="event-status-cell">
                  <el-switch
                    :model-value="row.enabled"
                    :loading="eventToggleLoading"
                    @change="(val: boolean) => handleLowBalanceToggle(val)"
                  />
                  <el-tag size="small" :type="row.enabled ? 'success' : 'info'">
                    {{ row.enabled ? 'Enabled' : 'Disabled' }}
                  </el-tag>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="Actions" min-width="180" align="right">
              <template #default>
                <el-button type="primary" plain @click="openLowBalanceTemplateDialog">Set Email Template</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty
            v-if="!eventsLoading && eventRows.length === 0"
            description="No notification events are available right now."
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="System Email" name="sender">
        <div class="panel">
          <div class="panel__header">
            <span class="panel__title">System Email Account</span>
            <span class="panel__hint">View or update the mailbox used by the system to send notifications.</span>
          </div>

          <div v-loading="configLoading" class="sender-summary">
            <el-empty v-if="!currentConfig" description="No system email account has been configured.">
              <el-button type="primary" @click="openConfigDialog">Set System Email</el-button>
            </el-empty>

            <template v-else>
              <div class="sender-summary__top">
                <div>
                  <div class="sender-summary__address">{{ currentConfig.fromAddress || '-' }}</div>
                  <div class="sender-summary__name">{{ currentConfig.fromName || 'No display name' }}</div>
                </div>
                <div class="sender-summary__actions">
                  <el-tag :type="currentConfig.enabled ? 'success' : 'info'">
                    {{ currentConfig.enabled ? 'Email Enabled' : 'Email Disabled' }}
                  </el-tag>
                  <el-button type="primary" @click="openConfigDialog">Modify</el-button>
                </div>
              </div>

              <el-descriptions :column="2" border class="sender-summary__details">
                <el-descriptions-item label="Sender Email">{{ displayValue(currentConfig.fromAddress) }}</el-descriptions-item>
                <el-descriptions-item label="Display Name">{{ displayValue(currentConfig.fromName) }}</el-descriptions-item>
                <el-descriptions-item label="SMTP Host">{{ displayValue(currentConfig.smtpHost) }}</el-descriptions-item>
                <el-descriptions-item label="SMTP Port">{{ currentConfig.smtpPort || '-' }}</el-descriptions-item>
                <el-descriptions-item label="Authorization Code">Configured, hidden for security</el-descriptions-item>
                <el-descriptions-item label="Strict Allowlist">
                  {{ currentConfig.strictAllowlist ? 'Enabled' : 'Disabled' }}
                </el-descriptions-item>
                <el-descriptions-item label="Allowed Recipients">{{ displayValue(currentConfig.allowedRecipients) }}</el-descriptions-item>
                <el-descriptions-item label="Last Updated">{{ displayValue(currentConfig.updatedAt) }}</el-descriptions-item>
              </el-descriptions>

              <div class="config-actions">
                <el-button :loading="testing" @click="handleTestEmail">Send Test Email</el-button>
              </div>
              <div class="config-hint">
                The test email is sent to the configured system email account. Edit it only when you need to change SMTP credentials or delivery controls.
              </div>
            </template>
          </div>

          <el-dialog v-model="configDialogVisible" width="680px" :close-on-click-modal="false" class="edit-dialog">
            <template #header><h3>{{ currentConfig ? 'Modify System Email' : 'Set System Email' }}</h3></template>
            <el-form ref="configFormRef" label-position="top" :model="configForm" class="config-form">
              <div class="config-grid">
                <div class="config-col">
                  <el-form-item label="Sender Email" prop="fromAddress" :rules="[{ required: true, message: 'Sender email is required' }]">
                    <template #label>
                      Sender Email
                      <el-tooltip content="The email address used as the sender (From field). It should match your SMTP account." placement="top" raw-content>
                        <span class="info-icon">ⓘ</span>
                      </el-tooltip>
                    </template>
                    <el-input v-model="configForm.fromAddress" placeholder="your-email@domain.com" />
                  </el-form-item>

                  <el-form-item label="Display Name" prop="fromName" :rules="[{ required: true, message: 'Display name is required' }]">
                    <template #label>
                      Display Name
                      <el-tooltip content="The name shown to recipients in the From field, e.g. 'JIN BEST TUTORING'." placement="top" raw-content>
                        <span class="info-icon">ⓘ</span>
                      </el-tooltip>
                    </template>
                    <el-input v-model="configForm.fromName" placeholder="JIN BEST TUTORING" />
                  </el-form-item>

                  <el-form-item label="SMTP Host" prop="smtpHost" :rules="[{ required: true, message: 'SMTP host is required' }]">
                    <el-input v-model="configForm.smtpHost" placeholder="smtp.qq.com" />
                  </el-form-item>

                  <el-form-item label="SMTP Port" prop="smtpPort" :rules="[{ required: true, message: 'SMTP port is required' }]">
                    <el-input-number v-model="configForm.smtpPort" :min="1" :max="65535" style="width:100%" />
                  </el-form-item>

                  <el-form-item label="SMTP Authorization Code">
                    <template #label>
                      SMTP Authorization Code
                      <el-tooltip raw-content placement="top">
                        <template #content>
                          <div style="max-width:280px;line-height:1.6;">
                            <strong>What is this?</strong><br/>
                            A password generated by your email provider for SMTP sending. Leave blank to keep the current code.
                          </div>
                        </template>
                        <span class="info-icon">ⓘ</span>
                      </el-tooltip>
                    </template>
                    <el-input v-model="configForm.authCode" placeholder="Leave blank to keep current code" show-password />
                  </el-form-item>
                </div>

                <div class="config-col config-col--side">
                  <el-form-item label="Enable Email Dispatch">
                    <template #label>
                      Enable Email Dispatch
                      <el-tooltip content="When OFF, no automated emails are sent from the system." placement="top" raw-content>
                        <span class="info-icon">ⓘ</span>
                      </el-tooltip>
                    </template>
                    <el-switch v-model="configForm.enabled" :active-value="true" :inactive-value="false" />
                  </el-form-item>

                  <el-form-item label="Strict Allowlist Mode">
                    <template #label>
                      Strict Allowlist Mode
                      <el-tooltip content="ON: only send to addresses listed below. OFF: send to any recipient." placement="top" raw-content>
                        <span class="info-icon">ⓘ</span>
                      </el-tooltip>
                    </template>
                    <el-switch v-model="configForm.strictAllowlist" :active-value="true" :inactive-value="false" />
                  </el-form-item>

                  <el-form-item label="Allowed Recipients">
                    <template #label>
                      Allowed Recipients
                      <el-tooltip content="JSON array of allowed email addresses. Only effective when allowlist mode is ON." placement="top" raw-content>
                        <span class="info-icon">ⓘ</span>
                      </el-tooltip>
                    </template>
                    <el-input
                      v-model="configForm.allowedRecipients"
                      type="textarea"
                      :rows="4"
                      placeholder='["recipient1@example.com", "recipient2@example.com"]'
                    />
                  </el-form-item>
                </div>
              </div>

              <div class="config-hint">
                Existing authorization codes are never shown. Leave the authorization code blank unless you need to replace it.
              </div>
            </el-form>
            <template #footer>
              <el-button @click="configDialogVisible = false">Cancel</el-button>
              <el-button type="primary" :loading="configSaving" @click="handleConfigSave">Save</el-button>
            </template>
          </el-dialog>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="templateDialogVisible" width="720px" :close-on-click-modal="false" class="edit-dialog">
      <template #header><h3>Low Balance Renewal Reminder Template</h3></template>

      <div class="template-dialog__intro">
        <div class="template-dialog__intro-title">Insert placeholders by clicking</div>
        <div class="template-dialog__intro-text">
          Use the buttons below to insert student name, course name, remaining hours and other placeholders into the email subject or body.
        </div>
      </div>

      <el-form ref="templateFormRef" label-position="top" :model="templateForm">
        <el-form-item label="Template Name" prop="templateName" :rules="[{ required: true, message: 'Required' }]">
          <el-input v-model="templateForm.templateName" placeholder="e.g. Low Balance Reminder" />
        </el-form-item>

        <el-form-item label="Email Subject" prop="subjectTemplate" :rules="[{ required: true, message: 'Required' }]">
          <el-input ref="subjectInputRef" v-model="templateForm.subjectTemplate" placeholder="Enter email subject" />
        </el-form-item>
        <div class="placeholder-panel">
          <div class="placeholder-panel__title">Click to insert into subject</div>
          <div class="placeholder-panel__list">
            <el-button
              v-for="placeholder in lowBalancePlaceholders"
              :key="`subject-${placeholder.token}`"
              size="small"
              @click="insertPlaceholder('subjectTemplate', placeholder.token)"
            >
              {{ placeholder.label }}
            </el-button>
          </div>
        </div>

        <el-form-item label="Email Body" prop="bodyTemplate" :rules="[{ required: true, message: 'Required' }]">
          <el-input
            ref="bodyInputRef"
            v-model="templateForm.bodyTemplate"
            type="textarea"
            :rows="8"
            placeholder="Enter email body"
          />
        </el-form-item>
        <div class="placeholder-panel placeholder-panel--body">
          <div class="placeholder-panel__title">Click to insert into body</div>
          <div class="placeholder-panel__list">
            <el-button
              v-for="placeholder in lowBalancePlaceholders"
              :key="`body-${placeholder.token}`"
              size="small"
              @click="insertPlaceholder('bodyTemplate', placeholder.token)"
            >
              {{ placeholder.label }}
            </el-button>
          </div>
          <div class="placeholder-panel__descriptions">
            <div v-for="placeholder in lowBalancePlaceholders" :key="placeholder.token" class="placeholder-panel__description-item">
              <span class="placeholder-panel__description-label">{{ placeholder.label }}</span>
              <span class="placeholder-panel__description-text">{{ placeholder.description }}</span>
            </div>
          </div>
        </div>

        <div class="preview-panel">
          <div class="preview-panel__header">
            <div>
              <div class="preview-panel__title">Preview Email</div>
              <div class="preview-panel__subtitle">Preview uses friendly sample values so you can quickly see what the email will look like.</div>
            </div>
          </div>

          <div class="preview-panel__chips">
            <span v-for="item in previewPlaceholderEntries" :key="item.token" class="preview-chip">
              <strong>{{ item.label }}:</strong> {{ item.value }}
            </span>
          </div>

          <div class="preview-email">
            <div class="preview-email__row">
              <span class="preview-email__label">Subject</span>
              <span class="preview-email__value">{{ previewSubject }}</span>
            </div>
            <div class="preview-email__divider"></div>
            <div class="preview-email__label preview-email__label--body">Body</div>
            <div class="preview-email__body">{{ previewBody }}</div>
          </div>
        </div>

        <el-form-item label="Status">
          <el-select v-model="templateForm.status" style="width: 160px;">
            <el-option label="Active" value="ACTIVE" />
            <el-option label="Inactive" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="templateDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="templateSaving" @click="handleTemplateSave">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, type InputInstance } from 'element-plus';
import { computed, nextTick, reactive, ref } from 'vue';

import {
  fetchNotificationConfig,
  fetchNotificationTemplates,
  fetchNotificationTriggerRules,
  sendTestEmail,
  updateNotificationConfig,
  updateNotificationTemplate,
  updateNotificationTriggerRule,
} from '@/api/system';
import type { EntityId, NotificationConfigItem, NotificationTemplateItem, NotificationTriggerRuleItem } from '@/types/domain';

const LOW_BALANCE_TRIGGER = 'LOW_BALANCE';

const activeTab = ref('events');

const lowBalancePlaceholders = [
  { label: 'Student Name', token: '${studentName}', description: 'The student name tied to the low-balance package.' },
  { label: 'Course Name', token: '${packageName}', description: 'The package or course name that needs renewal.' },
  { label: 'Remaining Hours', token: '${remainingHours}', description: 'The current remaining lesson hours.' },
  { label: 'Estimated End Date', token: '${estimatedEndDate}', description: 'The estimated package completion date.' },
  { label: 'Brand Name', token: '${brandName}', description: 'Your configured school or brand name.' },
];

const previewPlaceholderMap: Record<string, string> = {
  '${studentName}': 'Jack',
  '${packageName}': 'SHSAT lesson',
  '${remainingHours}': '2.0',
  '${estimatedEndDate}': '2026-07-15',
  '${brandName}': 'JIN BEST TUTORING',
};

const previewPlaceholderEntries = lowBalancePlaceholders.map((item) => ({
  ...item,
  value: previewPlaceholderMap[item.token] ?? item.token,
}));

const eventsLoading = ref(false);
const eventToggleLoading = ref(false);
const triggerRules = ref<NotificationTriggerRuleItem[]>([]);
const notificationTemplates = ref<NotificationTemplateItem[]>([]);

const lowBalanceRule = computed(() =>
  triggerRules.value.find((rule) => rule.triggerType === LOW_BALANCE_TRIGGER) ?? null,
);

const lowBalanceTemplate = computed(() => {
  const rule = lowBalanceRule.value;
  if (rule?.templateId != null) {
    const assigned = notificationTemplates.value.find((template) => String(template.id) === String(rule.templateId));
    if (assigned) {
      return assigned;
    }
  }

  return notificationTemplates.value.find((template) =>
    template.templateCode === 'LOW_BALANCE_DEFAULT'
    || template.templateCode.includes('LOW_BALANCE')
    || template.templateName.toLowerCase().includes('low balance'),
  ) ?? null;
});

const eventRows = computed(() => {
  const rule = lowBalanceRule.value;
  if (!rule) {
    return [];
  }
  return [
    {
      id: rule.id,
      eventName: 'Low Balance Renewal Reminder',
      description: 'Send an email reminder when a student package balance becomes low and renewal follow-up is needed.',
      enabled: rule.enabled,
      templateName: lowBalanceTemplate.value?.templateName ?? 'No template selected',
      templateHint: lowBalanceTemplate.value?.subjectTemplate ?? 'Open template settings to choose and edit the email content.',
    },
  ];
});

const templateDialogVisible = ref(false);
const templateSaving = ref(false);
const templateFormRef = ref();
const editingTemplateId = ref<EntityId | null>(null);
const subjectInputRef = ref<InputInstance>();
const bodyInputRef = ref<InputInstance>();
const templateForm = reactive({
  templateName: '',
  subjectTemplate: '',
  bodyTemplate: '',
  status: 'ACTIVE',
});

const previewSubject = computed(() => renderPreviewTemplate(templateForm.subjectTemplate));
const previewBody = computed(() => renderPreviewTemplate(templateForm.bodyTemplate));

const configLoading = ref(false);
const testing = ref(false);
const configSaving = ref(false);
const configDialogVisible = ref(false);
const configFormRef = ref();
const currentConfig = ref<NotificationConfigItem | null>(null);
const configForm = reactive({
  enabled: false,
  fromAddress: '',
  fromName: '',
  authCode: '',
  strictAllowlist: true,
  allowedRecipients: '',
  smtpHost: '',
  smtpPort: 587,
});

loadEventData();
loadConfig();

async function loadEventData() {
  eventsLoading.value = true;
  try {
    const [rulesRes, templatesRes] = await Promise.all([
      fetchNotificationTriggerRules(),
      fetchNotificationTemplates({ pageSize: 100 }),
    ]);
    triggerRules.value = rulesRes.data.data;
    notificationTemplates.value = templatesRes.data.data.records;
  } catch {
    ElMessage.error('Failed to load notification events.');
  } finally {
    eventsLoading.value = false;
  }
}

function buildLowBalanceRulePayload(rule: NotificationTriggerRuleItem, overrides: Partial<Record<'enabled' | 'templateId', unknown>> = {}) {
  return {
    enabled: (overrides.enabled as boolean | undefined) ?? rule.enabled,
    templateId: (overrides.templateId as EntityId | null | undefined) ?? rule.templateId ?? lowBalanceTemplate.value?.id ?? null,
    recipientType: rule.recipientType ?? 'GUARDIAN_EMAIL',
    customRecipients: rule.customRecipients ?? '',
    thresholdValue: rule.thresholdValue ?? null,
    remark: rule.remark ?? '',
  };
}

async function handleLowBalanceToggle(enabled: boolean) {
  if (!lowBalanceRule.value) {
    ElMessage.error('Low balance event is not configured yet.');
    return;
  }
  eventToggleLoading.value = true;
  try {
    await updateNotificationTriggerRule(lowBalanceRule.value.id, buildLowBalanceRulePayload(lowBalanceRule.value, { enabled }));
    lowBalanceRule.value.enabled = enabled;
    ElMessage.success(enabled ? 'Event enabled.' : 'Event disabled.');
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Update failed.');
  } finally {
    eventToggleLoading.value = false;
  }
}

function openLowBalanceTemplateDialog() {
  const template = lowBalanceTemplate.value;
  if (!template) {
    ElMessage.error('No low balance reminder template was found. Please initialize a default template first.');
    return;
  }
  editingTemplateId.value = template.id;
  Object.assign(templateForm, {
    templateName: template.templateName,
    subjectTemplate: template.subjectTemplate,
    bodyTemplate: template.bodyTemplate,
    status: template.status,
  });
  templateDialogVisible.value = true;
}

async function handleTemplateSave() {
  const valid = await templateFormRef.value?.validate().catch(() => false);
  if (!valid || !editingTemplateId.value) {
    return;
  }
  templateSaving.value = true;
  try {
    await updateNotificationTemplate(editingTemplateId.value, {
      templateName: templateForm.templateName,
      subjectTemplate: templateForm.subjectTemplate,
      bodyTemplate: templateForm.bodyTemplate,
      status: templateForm.status,
    });

    if (lowBalanceRule.value && String(lowBalanceRule.value.templateId ?? '') !== String(editingTemplateId.value)) {
      await updateNotificationTriggerRule(
        lowBalanceRule.value.id,
        buildLowBalanceRulePayload(lowBalanceRule.value, { templateId: editingTemplateId.value }),
      );
    }

    ElMessage.success('Template saved.');
    templateDialogVisible.value = false;
    await loadEventData();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Save failed.');
  } finally {
    templateSaving.value = false;
  }
}

async function insertPlaceholder(field: 'subjectTemplate' | 'bodyTemplate', token: string) {
  const inputRef = field === 'subjectTemplate' ? subjectInputRef.value : bodyInputRef.value;
  const inputEl = field === 'subjectTemplate' ? inputRef?.input : inputRef?.textarea;
  const currentValue = templateForm[field];

  if (!inputEl || typeof inputEl.selectionStart !== 'number' || typeof inputEl.selectionEnd !== 'number') {
    templateForm[field] = `${currentValue}${token}`;
    return;
  }

  const start = inputEl.selectionStart;
  const end = inputEl.selectionEnd;
  templateForm[field] = `${currentValue.slice(0, start)}${token}${currentValue.slice(end)}`;

  await nextTick();
  const nextPosition = start + token.length;
  inputEl.focus();
  inputEl.setSelectionRange(nextPosition, nextPosition);
}

function renderPreviewTemplate(template: string) {
  let preview = template || '';
  for (const [token, sampleValue] of Object.entries(previewPlaceholderMap)) {
    preview = preview.split(token).join(sampleValue);
  }
  return preview;
}

async function loadConfig() {
  configLoading.value = true;
  try {
    const res = await fetchNotificationConfig();
    currentConfig.value = res.data.data ?? null;
  } catch {
    currentConfig.value = null;
  } finally {
    configLoading.value = false;
  }
}

function openConfigDialog() {
  applyConfigToForm(currentConfig.value);
  configDialogVisible.value = true;
}

function applyConfigToForm(config: NotificationConfigItem | null) {
  configForm.enabled = config?.enabled ?? false;
  configForm.fromAddress = config?.fromAddress ?? '';
  configForm.fromName = config?.fromName ?? '';
  configForm.authCode = '';
  configForm.strictAllowlist = config?.strictAllowlist ?? true;
  configForm.allowedRecipients = config?.allowedRecipients ?? '';
  configForm.smtpHost = config?.smtpHost ?? '';
  configForm.smtpPort = config?.smtpPort ?? 587;
}

function displayValue(value?: string | number | null): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  return String(value);
}

async function handleConfigSave() {
  const valid = await configFormRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  if (!currentConfig.value && !configForm.authCode.trim()) {
    ElMessage.error('SMTP authorization code is required for the first setup.');
    return;
  }
  configSaving.value = true;
  try {
    await updateNotificationConfig({
      enabled: configForm.enabled,
      fromAddress: configForm.fromAddress,
      fromName: configForm.fromName,
      authCode: configForm.authCode.trim() || undefined,
      strictAllowlist: configForm.strictAllowlist,
      allowedRecipients: configForm.allowedRecipients,
      lowBalanceThreshold: currentConfig.value?.lowBalanceThreshold ?? 2,
      lowBalanceRecipients: currentConfig.value?.lowBalanceRecipients ?? '[]',
      smtpHost: configForm.smtpHost,
      smtpPort: configForm.smtpPort,
    });
    ElMessage.success('Sender settings saved.');
    configDialogVisible.value = false;
    await loadConfig();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Save failed.');
  } finally {
    configSaving.value = false;
  }
}

async function handleTestEmail() {
  testing.value = true;
  try {
    await sendTestEmail();
    ElMessage.success('Test email queued. Check your inbox shortly.');
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Failed to send test email.');
  } finally {
    testing.value = false;
  }
}
</script>

<style scoped>
.settings-page {
  padding: 0;
}

.settings-tabs :deep(.el-tabs__header) {
  background: #fff;
  border-radius: 8px 8px 0 0;
  padding: 0 16px;
  margin-bottom: 0;
}

.settings-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.settings-tabs :deep(.el-tabs__item) {
  font-size: 14px;
  font-weight: 500;
}

.panel {
  border: 1px solid #e8edf2;
  border-radius: 0 0 8px 8px;
  background: #fff;
  padding: 20px;
}

.panel__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.panel__title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.panel__hint {
  font-size: 12px;
  color: #909399;
  flex: 1;
}

.data-table {
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

.event-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.event-cell__title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2d3d;
}

.event-cell__desc,
.event-template-cell__hint {
  font-size: 12px;
  line-height: 1.6;
  color: #909399;
}

.event-template-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.event-status-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.edit-dialog :deep(.el-dialog__body) {
  padding: 16px 20px;
}

.edit-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #ebeef5;
  padding: 16px 20px;
}

.edit-dialog :deep(.el-dialog__header h3) {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.edit-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
}

.template-dialog__intro {
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: 8px;
  background: #f5f9ff;
  border: 1px solid #d9ecff;
}

.template-dialog__intro-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2d3d;
  margin-bottom: 4px;
}

.template-dialog__intro-text {
  font-size: 12px;
  line-height: 1.6;
  color: #606266;
}

.placeholder-panel {
  margin: -6px 0 18px;
  padding: 12px;
  border-radius: 8px;
  background: #fafbfc;
  border: 1px dashed #dcdfe6;
}

.placeholder-panel__title {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 10px;
}

.placeholder-panel__list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.placeholder-panel__descriptions {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.placeholder-panel__description-item {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  line-height: 1.5;
}

.placeholder-panel__description-label {
  font-weight: 600;
  color: #1f2d3d;
}

.placeholder-panel__description-text {
  color: #909399;
}

.preview-panel {
  margin: 4px 0 18px;
  padding: 16px;
  border-radius: 10px;
  background: linear-gradient(180deg, #fbfdff 0%, #f6f9fc 100%);
  border: 1px solid #e3ebf3;
}

.preview-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.preview-panel__title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2d3d;
}

.preview-panel__subtitle {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
}

.preview-panel__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.preview-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border-radius: 999px;
  background: #eef5ff;
  color: #4b5e73;
  font-size: 12px;
}

.preview-email {
  background: #fff;
  border: 1px solid #e8edf2;
  border-radius: 10px;
  padding: 14px 16px;
}

.preview-email__row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.preview-email__label {
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.preview-email__label--body {
  margin-bottom: 8px;
}

.preview-email__value {
  font-size: 14px;
  line-height: 1.6;
  color: #1f2d3d;
  white-space: pre-wrap;
}

.preview-email__divider {
  margin: 14px 0;
  border-top: 1px solid #eef2f6;
}

.preview-email__body {
  font-size: 14px;
  line-height: 1.75;
  color: #1f2d3d;
  white-space: pre-wrap;
}

.sender-summary {
  min-height: 180px;
}

.sender-summary__top {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.sender-summary__address {
  font-size: 20px;
  font-weight: 600;
  color: #1f2d3d;
  margin-bottom: 4px;
}

.sender-summary__name {
  font-size: 13px;
  color: #606266;
}

.sender-summary__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sender-summary__details {
  max-width: 960px;
}

.config-form {
  max-width: 960px;
}

.config-grid {
  display: flex;
  gap: 48px;
  flex-wrap: wrap;
}

.config-col {
  flex: 2;
  min-width: 280px;
}

.config-col--side {
  flex: 1;
  min-width: 200px;
}

.info-icon {
  display: inline-block;
  width: 16px;
  height: 16px;
  line-height: 16px;
  text-align: center;
  font-size: 12px;
  color: #909399;
  background: #f4f4f5;
  border-radius: 50%;
  cursor: help;
  margin-left: 4px;
  font-style: normal;
}

.info-icon:hover {
  background: #e8edf2;
  color: #606266;
}

.config-actions {
  display: flex;
  gap: 12px;
  margin-top: 28px;
}

.config-hint {
  margin-top: 12px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
</style>
