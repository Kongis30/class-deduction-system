<template>
  <div class="settings-page">
    <el-tabs v-model="activeTab" class="settings-tabs">
      <!-- Tab 1: Templates -->
      <el-tab-pane label="Templates" name="templates">
        <div class="panel">
          <div class="panel__header">
            <span class="panel__title">Notification Templates</span>
            <span class="panel__hint">Customize email subject and body templates with variable placeholders.</span>
          </div>
          <el-table v-loading="loading" :data="tableData" row-key="id" class="data-table">
            <el-table-column label="Name" min-width="160" prop="templateName" />
            <el-table-column label="Code" min-width="120" prop="templateCode" />
            <el-table-column label="Channel" min-width="90" align="center" prop="channelType" />
            <el-table-column label="Subject" min-width="200">
              <template #default="{ row }">
                {{ row.subjectTemplate?.slice(0, 80) }}{{ row.subjectTemplate?.length > 80 ? '…' : '' }}
              </template>
            </el-table-column>
            <el-table-column label="Status" min-width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                  {{ row.status === 'ACTIVE' ? 'Active' : 'Inactive' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Actions" min-width="80" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" @click="openTemplateDialog(row)">Edit</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- Template Edit Dialog -->
        <el-dialog v-model="templateDialogVisible" width="640px" :close-on-click-modal="false" class="edit-dialog">
          <template #header><h3>Edit Template</h3></template>
          <el-form ref="templateFormRef" label-position="top" :model="templateForm">
            <el-form-item label="Template Name" prop="templateName" :rules="[{ required: true, message: 'Required' }]">
              <el-input v-model="templateForm.templateName" />
            </el-form-item>
            <el-form-item label="Subject Template" prop="subjectTemplate" :rules="[{ required: true, message: 'Required' }]">
              <el-input v-model="templateForm.subjectTemplate" />
            </el-form-item>
            <el-form-item label="Body Template" prop="bodyTemplate" :rules="[{ required: true, message: 'Required' }]">
              <el-input v-model="templateForm.bodyTemplate" type="textarea" :rows="6" />
            </el-form-item>
            <div class="variable-hint">
              Available variables:
              <span class="var-list">
                <code v-for="v in templateVariables" :key="v" class="var-tag">{{ v }}</code>
              </span>
            </div>
            <el-form-item label="Status">
              <el-select v-model="templateForm.status" style="width:140px">
                <el-option label="Active" value="ACTIVE" />
                <el-option label="Inactive" value="INACTIVE" />
              </el-select>
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="templateDialogVisible = false">Cancel</el-button>
            <el-button type="primary" :loading="saving" @click="handleTemplateSave">Save</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <!-- Tab 2: Sender Settings -->
      <el-tab-pane label="Sender Settings" name="sender">
        <div class="panel">
          <div class="panel__header">
            <span class="panel__title">Email Sender Configuration</span>
            <span class="panel__hint">Configure SMTP credentials for outgoing emails.</span>
          </div>
          <el-form v-loading="configLoading" label-position="top" :model="configForm" class="config-form">
            <div class="config-grid">
              <div class="config-col">
                <el-form-item label="SMTP Server Address">
                  <template #label>
                    SMTP Server Address
                    <el-tooltip content="The email address used as the sender (From field). Must match your SMTP credentials." placement="top" raw-content>
                      <span class="info-icon">ⓘ</span>
                    </el-tooltip>
                  </template>
                  <el-input v-model="configForm.fromAddress" placeholder="your-smtp-email@domain.com" />
                </el-form-item>

                <el-form-item label="Display Name">
                  <template #label>
                    Display Name
                    <el-tooltip content="The name shown to recipients in the From field, e.g. 'JIN BEST TUTORING'." placement="top" raw-content>
                      <span class="info-icon">ⓘ</span>
                    </el-tooltip>
                  </template>
                  <el-input v-model="configForm.fromName" placeholder="JIN BEST TUTORING" />
                </el-form-item>

                <el-form-item label="SMTP Authorization Code">
                  <template #label>
                    SMTP Authorization Code
                    <el-tooltip raw-content placement="top">
                      <template #content>
                        <div style="max-width:280px;line-height:1.6;">
                          <strong>What is this?</strong><br/>
                          A 16-character password generated by your email provider for SMTP sending.<br/><br/>
                          <strong>How to get it (QQ Mail example):</strong><br/>
                          1. Log in to <em>mail.qq.com</em><br/>
                          2. Go to <strong>Settings → Account</strong><br/>
                          3. Scroll to <strong>POP3/SMTP service</strong> → enable it<br/>
                          4. Click <strong>Generate authorization code</strong><br/>
                          5. Enter the code here. This is NOT your login password.<br/><br/>
                          <strong>Note:</strong> Store this securely — it gives full SMTP access to this email account.
                        </div>
                      </template>
                      <span class="info-icon">ⓘ</span>
                    </el-tooltip>
                  </template>
                  <el-input v-model="configForm.authCode" placeholder="16-character authorization code" show-password />
                </el-form-item>

                <el-form-item label="SMTP Host">
                  <el-input v-model="configForm.smtpHost" placeholder="smtp.qq.com" />
                </el-form-item>

                <el-form-item label="SMTP Port">
                  <el-input-number v-model="configForm.smtpPort" :min="1" :max="65535" style="width:100%" />
                </el-form-item>

                <el-form-item label="SMTP Username">
                  <el-input v-model="configForm.smtpUsername" placeholder="your-email@qq.com" />
                </el-form-item>

                <el-form-item label="SMTP Password">
                  <el-input v-model="configForm.smtpPassword" placeholder="SMTP authorization code or password" show-password />
                </el-form-item>
              </div>

              <div class="config-col config-col--side">
                <el-form-item label="Enable Email Dispatch">
                  <template #label>
                    Enable Email Dispatch
                    <el-tooltip content="When OFF, no emails are sent regardless of other settings. Turn ON to activate email sending." placement="top" raw-content>
                      <span class="info-icon">ⓘ</span>
                    </el-tooltip>
                  </template>
                  <el-switch v-model="configForm.enabled" :active-value="true" :inactive-value="false" />
                </el-form-item>

                <el-form-item label="Strict Allowlist Mode">
                  <template #label>
                    Strict Allowlist Mode
                    <el-tooltip content="ON: only send to addresses listed below. OFF: send to any recipient (not recommended)." placement="top" raw-content>
                      <span class="info-icon">ⓘ</span>
                    </el-tooltip>
                  </template>
                  <el-switch v-model="configForm.strictAllowlist" :active-value="true" :inactive-value="false" />
                </el-form-item>

                <el-form-item label="Allowed Recipients">
                  <template #label>
                    Allowed Recipients
                    <el-tooltip content="JSON array of allowed email addresses. Only effective when allowlist mode is ON. Example: [&quot;parent@example.com&quot;, &quot;admin@school.edu&quot;]" placement="top" raw-content>
                      <span class="info-icon">ⓘ</span>
                    </el-tooltip>
                  </template>
                  <el-input
                    v-model="configForm.allowedRecipients"
                    type="textarea"
                    :rows="3"
                    placeholder='["recipient1@example.com", "recipient2@example.com"]'
                  />
                </el-form-item>
              </div>
            </div>

            <div class="config-actions">
              <el-button type="primary" :loading="saving" @click="handleConfigSave">Save Settings</el-button>
              <el-button :loading="testing" @click="handleTestEmail">Send Test Email</el-button>
            </div>
            <div class="config-hint">
              The test email will be sent to the SMTP sender address above. Check your inbox to verify the configuration is correct.
            </div>
          </el-form>
        </div>
      </el-tab-pane>

      <!-- Tab 3: Triggers -->
      <el-tab-pane label="Triggers" name="triggers">
        <div class="panel">
          <div class="panel__header">
            <span class="panel__title">Email Trigger Rules</span>
            <span class="panel__hint">Control when and to whom automated emails are sent.</span>
          </div>
          <div v-loading="rulesLoading" class="rule-list">
            <div v-for="rule in triggerRules" :key="rule.id" class="rule-card">
              <div class="rule-card__header">
                <div class="rule-card__title-row">
                  <span class="rule-card__name">{{ getTriggerLabel(rule.triggerType) }}</span>
                  <el-tooltip :content="getTriggerDescription(rule.triggerType)" placement="top" raw-content>
                    <span class="info-icon">ⓘ</span>
                  </el-tooltip>
                </div>
                <el-switch
                  :model-value="rule.enabled"
                  @change="(val: boolean) => handleRuleToggle(rule.id, val)"
                  :active-value="true"
                  :inactive-value="false"
                />
              </div>
              <div class="rule-card__body">
                <div class="rule-form__row">
                  <template v-if="rule.triggerType === 'LOW_BALANCE'">
                    <div class="rule-field">
                      <span class="rule-field__label">{{ getThresholdLabel() }}</span>
                      <el-tooltip content="Enter the remaining hours threshold. When a student's package balance drops below this value, a notification email is sent. Unit: 0.5h (e.g. 4 units = 2 hours)." placement="top" raw-content>
                        <span class="info-icon">ⓘ</span>
                      </el-tooltip>
                      <el-input-number
                        v-model="(ruleEditForms[rule.id] as any).thresholdValue"
                        :min="1"
                        :step="2"
                        :precision="0"
                        style="width:140px"
                      />
                    </div>
                  </template>

                  <div class="rule-field">
                    <span class="rule-field__label">Email Template</span>
                    <el-select
                      v-model="(ruleEditForms[rule.id] as any).templateId"
                      placeholder="Select template"
                      clearable
                      style="width: 200px"
                    >
                      <el-option
                        v-for="t in triggerTemplates"
                        :key="t.id"
                        :label="t.templateName"
                        :value="t.id"
                      />
                    </el-select>
                  </div>
                </div>
                <div class="rule-card__actions">
                  <el-button size="small" type="primary" :loading="ruleSaving[rule.id]" @click="handleRuleSave(rule.id)">Save Rule</el-button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 4: Broadcast -->
      <el-tab-pane label="Broadcast" name="broadcast">
        <div class="panel">
          <div class="panel__header">
            <span class="panel__title">Send Bulk Notification</span>
            <span class="panel__hint">Queue a batch email to selected recipients. Emails are sent asynchronously via the scheduler.</span>
          </div>
          <el-form ref="broadcastFormRef" label-position="top" :model="broadcastForm" class="broadcast-form">
            <el-form-item
              :label="getRecipientPresetLabel()"
              prop="recipientPreset"
              :rules="[{ required: true, message: 'Please select a recipient group' }]"
            >
              <div class="preset-grid">
                <label
                  v-for="opt in recipientPresetOptions"
                  :key="opt.value"
                  class="preset-card"
                  :class="{ 'preset-card--active': broadcastForm.recipientPreset === opt.value }"
                >
                  <input type="radio" :value="opt.value" v-model="broadcastForm.recipientPreset" style="display:none" />
                  <div class="preset-card__icon">{{ opt.icon }}</div>
                  <div class="preset-card__title">{{ opt.label }}</div>
                  <div class="preset-card__desc">{{ opt.description }}</div>
                </label>
              </div>
            </el-form-item>

            <el-form-item
              v-if="broadcastForm.recipientPreset === 'CUSTOM'"
              :label="getCustomRecipientsLabel()"
              prop="recipients"
              :rules="[{ required: true, message: 'At least one recipient is required' }]"
            >
              <el-input
                v-model="broadcastForm.recipientsInput"
                type="textarea"
                :rows="4"
                placeholder="recipient1@example.com&#10;recipient2@example.com&#10;recipient3@example.com"
              />
              <div class="field-hint">Enter one email per line or separate with commas. Each address must contain @.</div>
            </el-form-item>

            <div v-if="broadcastForm.recipientPreset && broadcastForm.recipientPreset !== 'CUSTOM'" class="recipient-count-hint">
              <span class="count-badge">{{ recipientCount }}</span> recipient(s) will receive this email based on the selected group.
            </div>

            <el-form-item
              label="Email Subject"
              prop="subject"
              :rules="[{ required: true, message: 'Subject is required' }]"
            >
              <el-input v-model="broadcastForm.subject" placeholder="Enter email subject" />
            </el-form-item>
            <el-form-item
              label="Email Body"
              prop="body"
              :rules="[{ required: true, message: 'Body is required' }]"
            >
              <el-input v-model="broadcastForm.body" type="textarea" :rows="8" placeholder="Enter email body content" />
            </el-form-item>
            <div class="variable-hint">
              Available variable: <code>${brandName}</code> — replaced with your brand name.
            </div>
            <div class="broadcast-actions">
              <el-button type="primary" :loading="broadcasting" @click="handleBroadcast">Queue Broadcast</el-button>
            </div>
            <div v-if="broadcastResult" class="broadcast-result">
              <el-alert
                :title="`Queued ${broadcastResult.queuedCount} email(s) for ${broadcastResult.totalRecipients} recipient(s).`"
                type="success"
                :closable="false"
                show-icon
              />
              <div class="recipient-preview">
                <span class="preview-label">Recipients:</span>
                <span v-for="(r, i) in broadcastResult.recipientPreview" :key="i" class="preview-item">{{ r }}</span>
                <span v-if="broadcastResult.totalRecipients > broadcastResult.recipientPreview.length" class="preview-more">
                  +{{ broadcastResult.totalRecipients - broadcastResult.recipientPreview.length }} more
                </span>
              </div>
            </div>
          </el-form>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { onMounted, reactive, ref } from 'vue';
import {
  fetchNotificationTemplates, updateNotificationTemplate,
  fetchNotificationConfig, updateNotificationConfig, sendTestEmail,
  fetchNotificationTriggerRules, updateNotificationTriggerRule,
  broadcastNotification, type BroadcastResult
} from '@/api/system';
import type { NotificationTemplateItem, NotificationConfigItem, NotificationTriggerRuleItem } from '@/types/domain';

// ── Tab ────────────────────────────────────────────────
const activeTab = ref('templates');

// ── Templates ──────────────────────────────────────────
const templateVariables = ['${brandName}', '${studentName}', '${packageName}', '${lessonStatus}', '${deductHours}', '${remainingHours}', '${estimatedEndDate}'];

const loading = ref(false);
const saving = ref(false);
const tableData = ref<NotificationTemplateItem[]>([]);
const templateDialogVisible = ref(false);
const templateFormRef = ref();
const editingTemplateId = ref<number | null>(null);
const templateForm = reactive({
  templateName: '',
  subjectTemplate: '',
  bodyTemplate: '',
  status: 'ACTIVE',
});

onMounted(() => {
  loadTemplates();
  loadConfig();
  loadRules();
});

async function loadTemplates() {
  loading.value = true;
  try {
    const res = await fetchNotificationTemplates({ pageSize: 100 });
    tableData.value = res.data.data.records;
  } catch {
    ElMessage.error('Failed to load templates.');
  } finally {
    loading.value = false;
  }
}

function openTemplateDialog(row: NotificationTemplateItem) {
  editingTemplateId.value = row.id;
  Object.assign(templateForm, {
    templateName: row.templateName,
    subjectTemplate: row.subjectTemplate,
    bodyTemplate: row.bodyTemplate,
    status: row.status,
  });
  templateDialogVisible.value = true;
}

async function handleTemplateSave() {
  const valid = await templateFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    await updateNotificationTemplate(editingTemplateId.value!, {
      templateName: templateForm.templateName,
      subjectTemplate: templateForm.subjectTemplate,
      bodyTemplate: templateForm.bodyTemplate,
      status: templateForm.status,
    });
    ElMessage.success('Template updated.');
    templateDialogVisible.value = false;
    await loadTemplates();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Save failed.');
  } finally {
    saving.value = false;
  }
}

// ── Sender Config ───────────────────────────────────────
const configLoading = ref(false);
const testing = ref(false);
const configForm = reactive({
  enabled: false,
  fromAddress: '',
  fromName: '',
  authCode: '',
  strictAllowlist: true,
  allowedRecipients: '',
  smtpHost: '',
  smtpPort: 587,
  smtpUsername: '',
  smtpPassword: '',
});

async function loadConfig() {
  configLoading.value = true;
  try {
    const res = await fetchNotificationConfig();
    const c: NotificationConfigItem = res.data.data;
    configForm.enabled = c.enabled ?? false;
    configForm.fromAddress = c.fromAddress ?? '';
    configForm.fromName = c.fromName ?? '';
    configForm.authCode = c.authCode ?? '';
    configForm.strictAllowlist = c.strictAllowlist ?? true;
    configForm.allowedRecipients = c.allowedRecipients ?? '';
    configForm.smtpHost = c.smtpHost ?? '';
    configForm.smtpPort = c.smtpPort ?? 587;
    configForm.smtpUsername = c.smtpUsername ?? '';
  } catch {
    // not configured yet
  } finally {
    configLoading.value = false;
  }
}

async function handleConfigSave() {
  saving.value = true;
  try {
    await updateNotificationConfig({
      enabled: configForm.enabled,
      fromAddress: configForm.fromAddress,
      fromName: configForm.fromName,
      authCode: configForm.authCode,
      strictAllowlist: configForm.strictAllowlist,
      allowedRecipients: configForm.allowedRecipients,
      lowBalanceThreshold: 2,
      lowBalanceRecipients: '[]',
      smtpHost: configForm.smtpHost,
      smtpPort: configForm.smtpPort,
      smtpUsername: configForm.smtpUsername,
      smtpPassword: configForm.smtpPassword || undefined,
    });
    ElMessage.success('Sender settings saved.');
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Save failed.');
  } finally {
    saving.value = false;
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

// ── Trigger Rules ───────────────────────────────────────
const rulesLoading = ref(false);
const triggerRules = ref<NotificationTriggerRuleItem[]>([]);
const ruleEditForms = reactive<Record<number, Record<string, unknown>>>({});
const ruleSaving = reactive<Record<number, boolean>>({});
const triggerTemplates = ref<{ id: number; templateName: string }[]>([]);

function getTriggerLabel(type: string): string {
  return type === 'LESSON_CONFIRMED' ? 'Lesson Confirmed' : 'Low Balance Warning';
}

function getTriggerDescription(type: string): string {
  return type === 'LESSON_CONFIRMED'
    ? 'Sends an email notification to the guardian after a lesson record is confirmed (COMPLETED, MAKEUP_COMPLETED, etc.).'
    : 'Sends an email notification when a course package remaining balance drops below the configured threshold.';
}

function getRecipientTypeLabel(): string {
  return 'Recipient Type';
}

function getGuardianEmailLabel(): string {
  return 'Guardian Email — sent to student\'s guardian';
}

function getCustomEmailLabel(): string {
  return 'Custom Email — use specified addresses';
}

function getTriggerCustomRecipientsLabel(): string {
  return 'Custom Recipients (JSON array)';
}

function getThresholdLabel(): string {
  return 'Balance Threshold (units, 1 unit = 0.5h)';
}

function getTemplateLabel(): string {
  return 'Email Template';
}

async function loadRules() {
  rulesLoading.value = true;
  try {
    const [rulesRes, templatesRes] = await Promise.all([
      fetchNotificationTriggerRules(),
      fetchNotificationTemplates({ pageSize: 100 }),
    ]);
    triggerRules.value = rulesRes.data.data;
    triggerTemplates.value = templatesRes.data.data.records.map(t => ({ id: t.id, templateName: t.templateName }));
    for (const rule of triggerRules.value) {
      ruleEditForms[rule.id] = {
        recipientType: rule.recipientType ?? 'GUARDIAN_EMAIL',
        customRecipients: rule.customRecipients ?? '',
        thresholdValue: rule.thresholdValue ?? 4,
        templateId: rule.templateId ?? null,
      };
    }
  } catch {
    ElMessage.error('Failed to load trigger rules.');
  } finally {
    rulesLoading.value = false;
  }
}

async function handleRuleToggle(ruleId: number, enabled: boolean) {
  try {
    const form = ruleEditForms[ruleId] as any;
    await updateNotificationTriggerRule(ruleId, {
      enabled,
      templateId: form?.templateId ?? null,
      recipientType: form?.recipientType ?? 'GUARDIAN_EMAIL',
      customRecipients: form?.customRecipients ?? '',
      thresholdValue: form?.thresholdValue ?? null,
    });
    const rule = triggerRules.value.find(r => r.id === ruleId);
    if (rule) rule.enabled = enabled;
    ElMessage.success(enabled ? 'Trigger enabled.' : 'Trigger disabled.');
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Update failed.');
  }
}

async function handleRuleSave(ruleId: number) {
  ruleSaving[ruleId] = true;
  const form = ruleEditForms[ruleId] as any;
  const rule = triggerRules.value.find(r => r.id === ruleId);
  try {
    await updateNotificationTriggerRule(ruleId, {
      enabled: rule?.enabled ?? false,
      templateId: form?.templateId ?? null,
      recipientType: form?.recipientType ?? 'GUARDIAN_EMAIL',
      customRecipients: form?.customRecipients ?? '',
      thresholdValue: form?.thresholdValue ?? null,
    });
    await loadRules();
    ElMessage.success('Trigger rule saved.');
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Save failed.');
  } finally {
    ruleSaving[ruleId] = false;
  }
}

// ── Broadcast ───────────────────────────────────────────
const broadcastFormRef = ref();
const broadcasting = ref(false);
const broadcastResult = ref<BroadcastResult | null>(null);
const recipientCount = ref(0);
const recipientPresetOptions = [
  { value: 'ALL_STUDENTS', label: 'All Students', icon: '👨‍🎓', description: 'All registered students (guardian email)' },
  { value: 'ALL_ENROLLED_STUDENTS', label: 'Enrolled Students', icon: '📚', description: 'Students with active course packages' },
  { value: 'ALL_TEACHERS', label: 'All Teachers', icon: '👩‍🏫', description: 'All teachers with user accounts' },
  { value: 'ALL_ADMIN_USERS', label: 'Admin Users', icon: '🔐', description: 'All admin users in the system' },
  { value: 'CUSTOM', label: 'Custom', icon: '✏️', description: 'Manually enter email addresses below' },
];
const broadcastForm = reactive({
  recipientPreset: '',
  recipientsInput: '',
  subject: '',
  body: '',
});

function getRecipientsLabel(): string {
  return 'Recipient Email Addresses';
}

function getRecipientPresetLabel(): string {
  return 'Recipient Group';
}

function getCustomRecipientsLabel(): string {
  return 'Custom Email Addresses';
}

function parseRecipients(input: string): string[] {
  return input
    .split(/[\n,]+/)
    .map(r => r.trim())
    .filter(r => r.includes('@'));
}

async function handleBroadcast() {
  const valid = await broadcastFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  broadcasting.value = true;
  broadcastResult.value = null;
  try {
    const recipients = broadcastForm.recipientPreset === 'CUSTOM' ? parseRecipients(broadcastForm.recipientsInput) : [];
    const result = await broadcastNotification({
      subject: broadcastForm.subject,
      body: broadcastForm.body,
      recipients,
      recipientPreset: broadcastForm.recipientPreset || undefined,
    });
    broadcastResult.value = result.data.data;
    ElMessage.success('Notification queued for dispatch.');
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Broadcast failed.');
  } finally {
    broadcasting.value = false;
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

/* Edit Dialog */
.edit-dialog :deep(.el-dialog__body) { padding: 16px 20px; }
.edit-dialog :deep(.el-dialog__header) { border-bottom: 1px solid #ebeef5; padding: 16px 20px; }
.edit-dialog :deep(.el-dialog__header h3) { margin: 0; font-size: 16px; font-weight: 600; }
.edit-dialog :deep(.el-dialog__footer) { border-top: 1px solid #ebeef5; padding: 12px 20px; }

/* Sender Config */
.config-form { max-width: 900px; }

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

/* Variable hint */
.variable-hint {
  font-size: 12px;
  color: #909399;
  margin: -8px 0 16px 0;
  line-height: 1.6;
}

.variable-hint code,
.var-tag {
  background: #f4f4f5;
  padding: 1px 5px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #606266;
}

.var-list {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-left: 4px;
}

/* Trigger Rules */
.rule-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.rule-card {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  overflow: hidden;
}

.rule-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #f9fafb;
  border-bottom: 1px solid #e8edf2;
  gap: 12px;
}

.rule-card__title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rule-card__name {
  font-weight: 600;
  font-size: 14px;
  color: #1a1a1a;
}

.rule-card__body {
  padding: 16px;
}

.rule-form {
  margin-bottom: 12px;
}

.rule-form :deep(.el-form-item) {
  margin-bottom: 8px;
}

.rule-form :deep(.el-form-item__label) {
  font-size: 13px;
  color: #606266;
}

.rule-field {
  display: flex;
  align-items: center;
  gap: 6px;
}

.rule-field__label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
}

.rule-form__row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 16px;
}

.rule-template-name {
  font-size: 13px;
  color: #909399;
}

.rule-card__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

/* Broadcast */
.broadcast-form {
  max-width: 720px;
}

.broadcast-actions {
  margin-top: 20px;
}

/* Preset grid */
.preset-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
  width: 100%;
}

.preset-card {
  border: 1.5px solid #e8edf2;
  border-radius: 8px;
  padding: 14px 16px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s, box-shadow 0.2s;
  user-select: none;
}

.preset-card:hover {
  border-color: #a0c4f0;
  background: #f0f7ff;
}

.preset-card--active {
  border-color: #409eff;
  background: #ecf5ff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.15);
}

.preset-card__icon {
  font-size: 22px;
  margin-bottom: 6px;
}

.preset-card__title {
  font-size: 13px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 4px;
}

.preset-card__desc {
  font-size: 11px;
  color: #909399;
  line-height: 1.4;
}

.recipient-count-hint {
  font-size: 13px;
  color: #606266;
  margin-bottom: 16px;
  padding: 8px 12px;
  background: #f4f9ff;
  border: 1px solid #d9ecff;
  border-radius: 6px;
  display: inline-block;
}

.count-badge {
  display: inline-block;
  background: #409eff;
  color: #fff;
  border-radius: 10px;
  padding: 0 8px;
  font-size: 12px;
  font-weight: 600;
  margin-right: 6px;
  line-height: 20px;
}

.field-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.broadcast-result {
  margin-top: 16px;
}

.recipient-preview {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.preview-label {
  font-size: 13px;
  color: #606266;
  margin-right: 4px;
}

.preview-item {
  background: #f0f9eb;
  color: #67c23a;
  border: 1px solid #c2e7b0;
  border-radius: 4px;
  padding: 2px 8px;
  font-size: 12px;
}

.preview-more {
  color: #909399;
  font-size: 12px;
}
</style>