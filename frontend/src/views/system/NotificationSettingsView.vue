<template>
  <div class="settings-page">
    <el-tabs v-model="activeTab" class="settings-tabs">
      <el-tab-pane label="Events" name="events">
        <div class="panel">
          <div class="panel__header">
            <span class="panel__title">Advanced notification rules</span>
            <span class="panel__hint">Technical settings for templates and legacy event records. Daily renewal follow-up is managed from Renewal reminders.</span>
          </div>


          <div class="event-group">
            <div class="event-group__header" @click="lowBalanceExpanded = !lowBalanceExpanded">
              <div class="event-group__title-wrap">
                <el-icon class="event-group__arrow" :class="{ 'is-expanded': lowBalanceExpanded }"><ArrowRight /></el-icon>
                <div>
                  <div class="event-group__title">Low Balance</div>
                  <div class="event-group__desc">
                    Low-balance rules only prepare records. They never send guardian emails automatically.
                  </div>
                </div>
              </div>
              <el-tag size="small" type="info">{{ lowBalanceRows.length }} events</el-tag>
            </div>

            <el-collapse-transition>
              <div v-show="lowBalanceExpanded" class="event-group__body">
                <el-table v-if="!appStore.isMobileViewport" v-loading="eventsLoading" :data="lowBalanceRows" row-key="key" class="data-table">
                  <el-table-column label="Event" min-width="240">
                    <template #default="{ row }">
                      <div class="event-cell">
                        <div class="event-cell__title">{{ row.eventName }}</div>
                        <div class="event-cell__desc">{{ row.scene }}</div>
                      </div>
                    </template>
                  </el-table-column>

                  <el-table-column label="Template" min-width="180">
                    <template #default="{ row }">
                      <div class="event-template-cell">
                        <div>{{ row.templateName }}</div>
                        <div class="event-template-cell__hint">{{ row.templateHint }}</div>
                      </div>
                    </template>
                  </el-table-column>

                  <el-table-column label="Threshold" min-width="220">
                    <template #default="{ row }">
                      <div class="threshold-cell">

                        <div class="threshold-cell__display">
                          <span class="threshold-cell__value">{{ thresholdInputs[row.triggerType] }} {{ row.unit }}</span>
                          <el-button
                            class="threshold-cell__edit-btn"
                            link
                            type="primary"
                            :icon="EditPen"
                            @click="openThresholdDialog(row)"
                          />
                        </div>
                        <div class="threshold-cell__hint">{{ row.thresholdHint }}</div>
                      </div>
                    </template>
                  </el-table-column>

                  <el-table-column label="Status" min-width="140" align="center">
                    <template #default="{ row }">
                      <div class="event-status-cell">
                        <el-switch
                          :model-value="row.enabled"
                          :loading="togglingType === row.triggerType"
                          @change="(val: boolean) => handleToggle(row, val)"
                        />
                        <el-tag size="small" :type="row.enabled ? 'success' : 'info'">
                          {{ row.enabled ? 'Enabled' : 'Disabled' }}
                        </el-tag>
                      </div>
                    </template>
                  </el-table-column>

                  <el-table-column label="Actions" min-width="170" align="right">
                    <template #default="{ row }">
                      <el-button type="primary" plain @click="openTemplateDialog(row)">Set Email Template</el-button>
                    </template>
                  </el-table-column>
                </el-table>

                <div v-else v-loading="eventsLoading" class="mobile-notification-rules">
                  <article v-for="row in lowBalanceRows" :key="row.key" class="mobile-notification-rule">
                    <header class="mobile-notification-rule__heading">
                      <div><strong>{{ row.eventName }}</strong><span>{{ row.scene }}</span></div>
                      <el-tag size="small" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? 'Enabled' : 'Disabled' }}</el-tag>
                    </header>
                    <div class="mobile-notification-rule__template">
                      <span>Email template</span>
                      <strong>{{ row.templateName }}</strong>
                      <small>{{ row.templateHint }}</small>
                    </div>
                    <div class="mobile-notification-rule__threshold">
                      <div><span>Threshold</span><strong>{{ thresholdInputs[row.triggerType] }} {{ row.unit }}</strong></div>
                      <small>{{ row.thresholdHint }}</small>
                      <el-button text type="primary" @click="openThresholdDialog(row)">Edit threshold</el-button>
                    </div>
                    <div class="mobile-notification-rule__actions">
                      <el-switch
                        :model-value="row.enabled"
                        :loading="togglingType === row.triggerType"
                        @change="(val: boolean) => handleToggle(row, val)"
                      />
                      <el-button type="primary" plain @click="openTemplateDialog(row)">Set email template</el-button>
                    </div>
                  </article>
                </div>

                <el-empty
                  v-if="!eventsLoading && lowBalanceRows.length === 0"
                  description="No low balance events are available right now."
                />
              </div>
            </el-collapse-transition>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="System Email" name="sender">
        <div class="panel">
          <div class="panel__header">
            <span class="panel__title">System Email Account</span>
            <span class="panel__hint">Set up the mailbox used after an administrator previews and confirms an email.</span>
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
                    {{ currentConfig.enabled ? 'Ready for confirmed sending' : 'Email Disabled' }}
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
                The test email is sent only when you click the button. Renewal emails always require separate preview and confirmation.
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


    <el-dialog v-model="thresholdDialogVisible" width="420px" :close-on-click-modal="false" class="edit-dialog">
      <template #header><h3>{{ thresholdDialogLabel }} Threshold</h3></template>
      <div class="threshold-dialog">
        <div class="threshold-dialog__input">
          <el-input-number
            v-model="thresholdDialogValue"
            :min="thresholdDialogDef?.min ?? 0"
            :step="thresholdDialogDef?.step ?? 1"
            :precision="thresholdDialogDef?.precision ?? 0"
            controls-position="right"
            style="width: 180px"
          />
          <span class="threshold-dialog__unit">{{ thresholdDialogDef?.unit }}</span>
        </div>
        <div class="threshold-dialog__hint">{{ thresholdDialogDef?.thresholdHint }}</div>
      </div>
      <template #footer>
        <el-button @click="thresholdDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="savingThresholdType === thresholdDialogType" @click="handleThresholdSave">Save</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="templateDialogVisible" width="720px" :close-on-click-modal="false" class="edit-dialog">
      <template #header><h3>{{ editingEventLabel }} Template</h3></template>

      <div class="template-dialog__intro">
        <div class="template-dialog__intro-title">Insert placeholders by clicking</div>
        <div class="template-dialog__intro-text">
          Use the buttons below to insert student name, course name, remaining balance and other placeholders into the email subject or body.
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
              v-for="placeholder in activePlaceholders"
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
              v-for="placeholder in activePlaceholders"
              :key="`body-${placeholder.token}`"
              size="small"
              @click="insertPlaceholder('bodyTemplate', placeholder.token)"
            >
              {{ placeholder.label }}
            </el-button>
          </div>
          <div class="placeholder-panel__descriptions">
            <div v-for="placeholder in activePlaceholders" :key="placeholder.token" class="placeholder-panel__description-item">
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
import { ArrowRight, EditPen } from '@element-plus/icons-vue';
import { ElMessage, type InputInstance } from 'element-plus';
import { computed, nextTick, reactive, ref } from 'vue';
import { useAppStore } from '@/stores/app';

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

const activeTab = ref('events');
const appStore = useAppStore();


interface PlaceholderItem {
  label: string;
  token: string;
  description: string;
  preview: string;
}


const COMMON_PLACEHOLDERS: PlaceholderItem[] = [
  { label: 'Student Name', token: '${studentName}', description: 'The student name tied to the low-balance package.', preview: 'Jack' },
  { label: 'Course Name', token: '${packageName}', description: 'The package or course name that needs renewal.', preview: 'SHSAT lesson' },
  { label: 'Estimated End Date', token: '${estimatedEndDate}', description: 'The estimated package completion date.', preview: '2026-07-15' },
  { label: 'Brand Name', token: '${brandName}', description: 'Your configured school or brand name.', preview: 'JIN BEST TUTORING' },
];


interface LowBalanceEventDef {
  triggerType: string;
  label: string;
  scene: string;
  unit: string;
  thresholdHint: string;
  min: number;
  step: number;
  precision: number;

  toDisplay: (raw: number | null) => number;
  toRaw: (display: number) => number;
  placeholders: PlaceholderItem[];
}

const LOW_BALANCE_EVENTS: LowBalanceEventDef[] = [
  {
    triggerType: 'LOW_BALANCE_HOUR',
    label: 'Low Balance (Hours)',
    scene: '1 on 1 lessons billed by hours',
    unit: 'hours',
    thresholdHint: 'Notify when remaining hours ≤ this value',
    min: 0.5,
    step: 0.5,
    precision: 1,

    toDisplay: (raw) => (raw == null ? 0 : raw * 0.5),
    toRaw: (display) => Math.round(display * 2),
    placeholders: [
      ...COMMON_PLACEHOLDERS,
      { label: 'Remaining Hours', token: '${remainingHours}', description: 'The current remaining lesson hours.', preview: '2.0' },
    ],
  },
  {
    triggerType: 'LOW_BALANCE_SESSION',
    label: 'Low Balance (Sessions)',
    scene: 'Group class billed by sessions',
    unit: 'sessions',
    thresholdHint: 'Notify when remaining sessions ≤ this value',
    min: 1,
    step: 1,
    precision: 0,
    toDisplay: (raw) => (raw == null ? 0 : raw),
    toRaw: (display) => Math.round(display),
    placeholders: [
      ...COMMON_PLACEHOLDERS,
      { label: 'Remaining Sessions', token: '${remainingSessions}', description: 'The current remaining lesson sessions.', preview: '3' },
    ],
  },
  {
    triggerType: 'LOW_BALANCE_MONTH',
    label: 'Low Balance (Months)',
    scene: 'Group class billed by months',
    unit: 'months',
    thresholdHint: 'Notify when remaining months ≤ this value',
    min: 1,
    step: 1,
    precision: 0,
    toDisplay: (raw) => (raw == null ? 0 : raw),
    toRaw: (display) => Math.round(display),
    placeholders: [
      ...COMMON_PLACEHOLDERS,
      { label: 'Remaining Months', token: '${remainingMonths}', description: 'The current remaining membership months.', preview: '1' },
      { label: 'Expiry Month', token: '${expiryMonth}', description: 'The month when the package expires.', preview: '2026-07' },
    ],
  },
];

const lowBalanceExpanded = ref(true);

const eventsLoading = ref(false);
const togglingType = ref<string | null>(null);
const savingThresholdType = ref<string | null>(null);

const thresholdDialogVisible = ref(false);
const thresholdDialogType = ref<string | null>(null);
const thresholdDialogValue = ref<number>(0);
const triggerRules = ref<NotificationTriggerRuleItem[]>([]);
const notificationTemplates = ref<NotificationTemplateItem[]>([]);


const thresholdInputs = reactive<Record<string, number>>({});


function findRule(triggerType: string): NotificationTriggerRuleItem | null {
  return triggerRules.value.find((rule) => rule.triggerType === triggerType) ?? null;
}


function resolveTemplate(rule: NotificationTriggerRuleItem | null): NotificationTemplateItem | null {
  if (rule?.templateId != null) {
    const assigned = notificationTemplates.value.find((template) => String(template.id) === String(rule.templateId));
    if (assigned) {
      return assigned;
    }
  }
  return null;
}


const lowBalanceRows = computed(() =>
  LOW_BALANCE_EVENTS.flatMap((def) => {
    const rule = findRule(def.triggerType);

    if (!rule) {
      return [];
    }
    const template = resolveTemplate(rule);
    return [{
      key: def.triggerType,
      triggerType: def.triggerType,
      ruleId: rule.id,
      eventName: def.label,
      scene: def.scene,
      enabled: rule.enabled,
      unit: def.unit,
      min: def.min,
      step: def.step,
      precision: def.precision,
      thresholdHint: def.thresholdHint,
      templateName: template?.templateName ?? rule.templateName ?? 'No template selected',
      templateHint: template?.subjectTemplate ?? 'Open template settings to choose and edit the email content.',
    }];
  }),
);

const templateDialogVisible = ref(false);
const templateSaving = ref(false);
const templateFormRef = ref();
const editingTemplateId = ref<EntityId | null>(null);
const editingTriggerType = ref<string | null>(null);
const subjectInputRef = ref<InputInstance>();
const bodyInputRef = ref<InputInstance>();
const templateForm = reactive({
  templateName: '',
  subjectTemplate: '',
  bodyTemplate: '',
  status: 'ACTIVE',
});


const editingEventDef = computed(() =>
  LOW_BALANCE_EVENTS.find((def) => def.triggerType === editingTriggerType.value) ?? null,
);
const editingEventLabel = computed(() => editingEventDef.value?.label ?? 'Low Balance');
const activePlaceholders = computed(() => editingEventDef.value?.placeholders ?? COMMON_PLACEHOLDERS);


const previewPlaceholderMap = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {};
  for (const placeholder of activePlaceholders.value) {
    map[placeholder.token] = placeholder.preview;
  }
  return map;
});

const previewPlaceholderEntries = computed(() =>
  activePlaceholders.value.map((item) => ({
    ...item,
    value: previewPlaceholderMap.value[item.token] ?? item.token,
  })),
);

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

    syncThresholdInputs();
  } catch {
    ElMessage.error('Failed to load notification events.');
  } finally {
    eventsLoading.value = false;
  }
}


function syncThresholdInputs() {
  for (const def of LOW_BALANCE_EVENTS) {
    const rule = findRule(def.triggerType);
    thresholdInputs[def.triggerType] = def.toDisplay(rule?.thresholdValue ?? null);
  }
}


function buildRulePayload(
  rule: NotificationTriggerRuleItem,
  overrides: Partial<Record<'enabled' | 'templateId' | 'thresholdValue', unknown>> = {},
) {
  return {
    enabled: (overrides.enabled as boolean | undefined) ?? rule.enabled,
    templateId: (overrides.templateId as EntityId | null | undefined) ?? rule.templateId ?? null,
    recipientType: rule.recipientType ?? 'GUARDIAN_EMAIL',
    customRecipients: rule.customRecipients ?? '',
    thresholdValue: (overrides.thresholdValue as number | null | undefined) ?? rule.thresholdValue ?? null,
    remark: rule.remark ?? '',
  };
}


async function handleToggle(row: { triggerType: string; ruleId: EntityId }, enabled: boolean) {
  const rule = findRule(row.triggerType);
  if (!rule) {
    ElMessage.error('This event is not configured yet.');
    return;
  }
  togglingType.value = row.triggerType;
  try {
    await updateNotificationTriggerRule(rule.id, buildRulePayload(rule, { enabled }));
    rule.enabled = enabled;
    ElMessage.success(enabled ? 'Event enabled.' : 'Event disabled.');
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Update failed.');
  } finally {
    togglingType.value = null;
  }
}


const thresholdDialogDef = computed(() =>
  LOW_BALANCE_EVENTS.find((item) => item.triggerType === thresholdDialogType.value) ?? null,
);
const thresholdDialogLabel = computed(() => thresholdDialogDef.value?.label ?? 'Low Balance');


function openThresholdDialog(row: { triggerType: string }) {
  const def = LOW_BALANCE_EVENTS.find((item) => item.triggerType === row.triggerType);
  const rule = findRule(row.triggerType);
  if (!def || !rule) {
    ElMessage.error('This event is not configured yet.');
    return;
  }
  thresholdDialogType.value = row.triggerType;
  thresholdDialogValue.value = def.toDisplay(rule.thresholdValue ?? null);
  thresholdDialogVisible.value = true;
}


async function handleThresholdSave() {
  const triggerType = thresholdDialogType.value;
  const def = LOW_BALANCE_EVENTS.find((item) => item.triggerType === triggerType);
  const rule = triggerType ? findRule(triggerType) : null;
  if (!def || !rule) {
    ElMessage.error('This event is not configured yet.');
    return;
  }
  const display = thresholdDialogValue.value;
  if (display == null || display < def.min) {
    ElMessage.error(`Threshold must be at least ${def.min} ${def.unit}.`);
    return;
  }
  const rawValue = def.toRaw(display);
  savingThresholdType.value = def.triggerType;
  try {
    await updateNotificationTriggerRule(rule.id, buildRulePayload(rule, { thresholdValue: rawValue }));
    ElMessage.success('Threshold saved.');
    thresholdDialogVisible.value = false;
    await loadEventData();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Save failed.');
  } finally {
    savingThresholdType.value = null;
  }
}


function openTemplateDialog(row: { triggerType: string }) {
  const rule = findRule(row.triggerType);
  const template = resolveTemplate(rule);
  if (!template) {
    ElMessage.error('No reminder template was found for this event. Please initialize a default template first.');
    return;
  }
  editingTriggerType.value = row.triggerType;
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


    const rule = editingTriggerType.value ? findRule(editingTriggerType.value) : null;
    if (rule && String(rule.templateId ?? '') !== String(editingTemplateId.value)) {
      await updateNotificationTriggerRule(rule.id, buildRulePayload(rule, { templateId: editingTemplateId.value }));
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
  for (const [token, sampleValue] of Object.entries(previewPlaceholderMap.value)) {
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
    ElMessage.success('Test email sent. Check the configured inbox.');
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Failed to send test email.');
  } finally {
    testing.value = false;
  }
}
</script>

<style scoped>
.mobile-notification-rules { display: grid; gap: 10px; }
.mobile-notification-rule { padding: 14px; border: 1px solid #e2e8f0; border-radius: 12px; background: #fff; }
.mobile-notification-rule__heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; }
.mobile-notification-rule__heading > div { display: grid; min-width: 0; gap: 4px; }
.mobile-notification-rule__heading strong { color: #26384c; font-size: 15px; }
.mobile-notification-rule__heading span,
.mobile-notification-rule__template > span,
.mobile-notification-rule__template small,
.mobile-notification-rule__threshold > span,
.mobile-notification-rule__threshold small { color: #718096; font-size: 12px; line-height: 1.4; }
.mobile-notification-rule__template,
.mobile-notification-rule__threshold { display: grid; gap: 5px; margin-top: 12px; padding: 10px; border-radius: 9px; background: #f6f8fb; }
.mobile-notification-rule__template strong,
.mobile-notification-rule__threshold strong { color: #334155; font-size: 14px; overflow-wrap: anywhere; }
.mobile-notification-rule__actions { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 12px; }

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


.event-group {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  overflow: hidden;
}

.event-group__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  background: #f7f9fc;
  cursor: pointer;
  user-select: none;
}

.event-group__header:hover {
  background: #f0f4f9;
}

.event-group__title-wrap {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.event-group__arrow {
  margin-top: 3px;
  font-size: 14px;
  color: #909399;
  transition: transform 0.2s ease;
}

.event-group__arrow.is-expanded {
  transform: rotate(90deg);
}

.event-group__title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2d3d;
}

.event-group__desc {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: #909399;
  max-width: 640px;
}

.event-group__body {
  padding: 12px;
  background: #fff;
}


.threshold-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.threshold-cell__input {
  display: flex;
  align-items: center;
  gap: 8px;
}

.threshold-cell__display {
  display: flex;
  align-items: center;
  gap: 6px;
}

.threshold-cell__value {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.threshold-cell__edit-btn {
  padding: 0;
  height: auto;
}

.threshold-cell__unit {
  font-size: 12px;
  color: #606266;
}

.threshold-cell__hint {
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
}

.threshold-dialog {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.threshold-dialog__input {
  display: flex;
  align-items: center;
  gap: 8px;
}

.threshold-dialog__unit {
  font-size: 13px;
  color: #606266;
}

.threshold-dialog__hint {
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
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

html.mobile-layout & {
  .config-grid { grid-template-columns: minmax(0, 1fr); }
  .config-col--side { min-width: 0; }
}

@media (max-width: 640px) {
  .preview-panel { padding: 12px; }
  .preview-panel__header { flex-direction: column; }
  .preview-panel__chips { flex-wrap: wrap; }
  .config-actions { display: grid; grid-template-columns: 1fr; gap: 8px; }
  .config-actions > .el-button { width: 100%; margin-left: 0; }
}
</style>
