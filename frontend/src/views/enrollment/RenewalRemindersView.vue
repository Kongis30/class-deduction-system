<template>
  <div class="renewal-page">
    <section class="renewal-header table-page-header">
      <div>
        <span class="table-page-header__eyebrow">Student follow-up</span>
        <h2>Renew students</h2>
        <p>Use this page when a course is ending or a student has few lessons left. Start a new registration here, then optionally send a reviewed reminder email.</p>
      </div>
      <div class="renewal-header__actions">
        <el-button @click="settingsVisible = true">Set follow-up rules</el-button>
      </div>
    </section>

    <section class="renewal-guide">
      <div class="renewal-guide__intro">
        <strong>How to use this page</strong>
        <span>Renewal starts a new registration. It does not change the current course or send an email automatically.</span>
      </div>
      <ol class="renewal-guide__steps">
        <li>
          <span>1</span>
          <div><strong>Check who needs renewal</strong><small>Use the status cards to focus the list.</small></div>
        </li>
        <li>
          <span>2</span>
          <div><strong>Renew student</strong><small>Opens Registration with the student details filled in.</small></div>
        </li>
        <li>
          <span>3</span>
          <div><strong>Optional: email the guardian</strong><small>Review every email before confirming send.</small></div>
        </li>
      </ol>
    </section>

    <el-alert
      v-if="!overview.emailReady"
      type="warning"
      :closable="false"
      show-icon
      title="Optional email reminders are not ready"
      description="You can still renew students. Set up the sender in Alerts only when you want to email guardians; nothing is ever sent automatically."
    >
      <template #default>
        <el-button link type="primary" @click="openEmailSetup">Open email setup</el-button>
      </template>
    </el-alert>

    <section class="renewal-metrics">
      <button type="button" class="renewal-metric" :class="{ 'renewal-metric--active': activeFilter === 'ALL' }" @click="activeFilter = 'ALL'">
        <span>All to review</span>
        <strong>{{ overview.reminders.length }}</strong>
        <small>Students who may need renewal</small>
      </button>
      <button type="button" class="renewal-metric renewal-metric--danger" :class="{ 'renewal-metric--active': activeFilter === 'EXPIRED' }" @click="activeFilter = 'EXPIRED'">
        <span>Already ended</span>
        <strong>{{ expiredCount }}</strong>
        <small>Already past the end date</small>
      </button>
      <button type="button" class="renewal-metric renewal-metric--warning" :class="{ 'renewal-metric--active': activeFilter === 'ENDING' }" @click="activeFilter = 'ENDING'">
        <span>Ending soon</span>
        <strong>{{ endingSoonCount }}</strong>
        <small>Within {{ overview.settings.expiryReminderDays }} days</small>
      </button>
      <button type="button" class="renewal-metric renewal-metric--warning" :class="{ 'renewal-metric--active': activeFilter === 'LOW_LESSONS' }" @click="activeFilter = 'LOW_LESSONS'">
        <span>Few lessons left</span>
        <strong>{{ lowLessonsCount }}</strong>
        <small>{{ overview.settings.oneOnOneRemainingLessons }} lessons or fewer</small>
      </button>
    </section>

    <section class="renewal-table-panel">
      <div class="renewal-table-panel__header">
        <div>
          <h3>Students to renew</h3>
          <span>{{ filteredReminders.length }} student-course records need review</span>
        </div>
        <div class="renewal-table-panel__actions">
          <span v-if="selectedReminders.length" class="selection-count">{{ selectedReminders.length }} email(s) selected</span>
          <el-button type="primary" :disabled="selectedReminders.length === 0" @click="openPreview">
            Review selected emails
          </el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="filteredReminders"
        row-key="reminderId"
        size="small"
        class="renewal-table"
        :row-class-name="rowClassName"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="46" :selectable="canSelectReminder" />
        <el-table-column label="Student / course" min-width="210">
          <template #default="{ row }">
            <strong>{{ row.studentName }}</strong>
            <small>{{ row.courseName }}</small>
          </template>
        </el-table-column>
        <el-table-column label="Why renewal may be needed" min-width="220">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            <small>{{ row.reason }}</small>
          </template>
        </el-table-column>
        <el-table-column label="Course end" min-width="135">
          <template #default="{ row }">
            <div>{{ formatDate(row.expectedEndDate) }}</div>
            <small v-if="row.daysUntilEnd != null">{{ daysLabel(row.daysUntilEnd) }}</small>
          </template>
        </el-table-column>
        <el-table-column label="Remaining" min-width="135">
          <template #default="{ row }">
            <span v-if="row.remainingLessons != null">{{ row.remainingLessons }} lesson(s)</span>
            <span v-else>—</span>
            <small v-if="row.remainingHours != null">{{ formatHours(row.remainingHours) }}h</small>
          </template>
        </el-table-column>
        <el-table-column label="Guardian email" min-width="220">
          <template #default="{ row }">
            <span v-if="row.emailAvailable">{{ row.guardianEmail }}</span>
            <el-tag v-else type="info" size="small">Email missing</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Next step" width="168" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="startRenewal(row)">Renew student</el-button>
            <el-button link type="primary" :disabled="!row.emailAvailable" @click="openSinglePreview(row)">Review email</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="settingsVisible" title="Follow-up rules" width="520px" destroy-on-close>
      <p class="dialog-hint">These rules only decide which students appear here. They never send an email automatically.</p>
      <el-form label-position="top" class="settings-form">
        <el-form-item label="Show courses ending within">
          <el-input-number v-model="settingsForm.expiryReminderDays" :min="1" :max="180" />
          <span class="unit-label">days</span>
        </el-form-item>
        <el-form-item label="Show 1-on-1 students when lessons remaining are">
          <el-input-number v-model="settingsForm.oneOnOneRemainingLessons" :min="1" :max="20" />
          <span class="unit-label">lessons or fewer</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="settingsVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="savingSettings" @click="saveSettings">Save rules</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewVisible" title="Review guardian emails" width="900px" destroy-on-close @closed="resetPreview">
      <p class="dialog-hint">
        Each email below is generated for its selected student. Review the recipient and content before confirming.
        Nothing is sent until you click Confirm and send.
      </p>
      <div class="preview-layout">
        <aside class="preview-list">
          <button
            v-for="item in selectedReminders"
            :key="item.reminderId"
            type="button"
            class="preview-list__item"
            :class="{ 'preview-list__item--active': currentPreview?.reminderId === item.reminderId }"
            @click="currentPreviewId = item.reminderId"
          >
            <strong>{{ item.studentName }}</strong>
            <span>{{ item.guardianEmail }}</span>
            <small>{{ item.courseName }}</small>
          </button>
        </aside>
        <section v-if="currentPreview" class="email-preview">
          <div class="email-preview__line"><span>To</span><strong>{{ currentPreview.guardianEmail }}</strong></div>
          <div class="email-preview__line"><span>From</span><strong>{{ overview.senderAddress || 'Configured sender' }}</strong></div>
          <div class="email-preview__line"><span>Subject</span><strong>{{ currentPreview.emailSubject }}</strong></div>
          <pre>{{ currentPreview.emailBody }}</pre>
        </section>
      </div>
      <template #footer>
        <el-button @click="previewVisible = false">Back</el-button>
        <el-button type="primary" :loading="sending" :disabled="!overview.emailReady" @click="confirmSend">
          Confirm and send {{ selectedReminders.length }} email(s)
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';

import {
  fetchRenewalReminders,
  sendRenewalReminders,
  updateRenewalReminderSettings,
  type RenewalReminderItem,
  type RenewalReminderOverview,
} from '@/api/renewal-reminders';
import { extractErrorMessage } from '@/api/http';

type ReminderFilter = 'ALL' | 'EXPIRED' | 'ENDING' | 'LOW_LESSONS';

const router = useRouter();
const loading = ref(false);
const savingSettings = ref(false);
const sending = ref(false);
const settingsVisible = ref(false);
const previewVisible = ref(false);
const activeFilter = ref<ReminderFilter>('ALL');
const selectedReminders = ref<RenewalReminderItem[]>([]);
const currentPreviewId = ref<string | null>(null);

const overview = ref<RenewalReminderOverview>({
  settings: { expiryReminderDays: 30, oneOnOneRemainingLessons: 2 },
  emailReady: false,
  reminders: [],
});
const settingsForm = reactive({
  expiryReminderDays: 30,
  oneOnOneRemainingLessons: 2,
});

const expiredCount = computed(() => overview.value.reminders.filter((item) => item.status === 'EXPIRED').length);
const endingSoonCount = computed(() => overview.value.reminders.filter((item) => item.daysUntilEnd != null && item.daysUntilEnd >= 0).length);
const lowLessonsCount = computed(() => overview.value.reminders.filter((item) => item.remainingLessons != null
  && item.remainingLessons <= overview.value.settings.oneOnOneRemainingLessons).length);
const filteredReminders = computed(() => overview.value.reminders.filter((item) => {
  if (activeFilter.value === 'EXPIRED') return item.status === 'EXPIRED';
  if (activeFilter.value === 'ENDING') return item.daysUntilEnd != null && item.daysUntilEnd >= 0;
  if (activeFilter.value === 'LOW_LESSONS') {
    return item.remainingLessons != null && item.remainingLessons <= overview.value.settings.oneOnOneRemainingLessons;
  }
  return true;
}));
const currentPreview = computed(() => selectedReminders.value.find((item) => item.reminderId === currentPreviewId.value)
  ?? selectedReminders.value[0]
  ?? null);

async function loadOverview() {
  loading.value = true;
  try {
    const response = await fetchRenewalReminders();
    overview.value = response.data.data;
    settingsForm.expiryReminderDays = overview.value.settings.expiryReminderDays;
    settingsForm.oneOnOneRemainingLessons = overview.value.settings.oneOnOneRemainingLessons;
    selectedReminders.value = [];
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    loading.value = false;
  }
}

function handleSelectionChange(rows: RenewalReminderItem[]) {
  selectedReminders.value = rows;
}

function canSelectReminder(row: RenewalReminderItem) {
  return row.emailAvailable;
}

function openPreview() {
  currentPreviewId.value = selectedReminders.value[0]?.reminderId ?? null;
  previewVisible.value = true;
}

function openSinglePreview(item: RenewalReminderItem) {
  selectedReminders.value = [item];
  currentPreviewId.value = item.reminderId;
  previewVisible.value = true;
}

function resetPreview() {
  currentPreviewId.value = null;
}

async function saveSettings() {
  savingSettings.value = true;
  try {
    const response = await updateRenewalReminderSettings({ ...settingsForm });
    overview.value = response.data.data;
    settingsVisible.value = false;
    ElMessage.success('Reminder rules saved.');
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    savingSettings.value = false;
  }
}

async function confirmSend() {
  sending.value = true;
  try {
    const response = await sendRenewalReminders(selectedReminders.value.map((item) => item.reminderId));
    const result = response.data.data;
    if (result.failedCount > 0) {
      ElMessage.warning(`${result.sentCount} sent, ${result.failedCount} failed. Review email setup or recipient details.`);
    } else {
      ElMessage.success(`${result.sentCount} reminder email(s) sent.`);
    }
    previewVisible.value = false;
    await loadOverview();
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    sending.value = false;
  }
}

function startRenewal(item: RenewalReminderItem) {
  router.push({ name: 'registration', query: { renewStudentId: item.studentId } });
}

function openEmailSetup() {
  router.push({ name: 'admin-config-notifications' });
}

function statusLabel(status: RenewalReminderItem['status']) {
  return ({ EXPIRED: 'Expired', EXPIRING_SOON: 'Ending soon', NEEDS_RENEWAL: 'Renew soon' } as Record<string, string>)[status];
}

function statusTagType(status: RenewalReminderItem['status']) {
  return status === 'EXPIRED' ? 'danger' : 'warning';
}

function daysLabel(days: number) {
  return days < 0 ? `${Math.abs(days)} day(s) overdue` : `${days} day(s) left`;
}

function formatDate(value?: string) {
  return value ? new Date(`${value}T00:00:00`).toLocaleDateString() : '—';
}

function formatHours(value: number) {
  return Number(value).toFixed(1);
}

function rowClassName({ row }: { row: RenewalReminderItem }) {
  return row.status === 'EXPIRED' ? 'renewal-row--expired' : '';
}

onMounted(loadOverview);
</script>

<style scoped>
.renewal-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.renewal-header,
.renewal-table-panel,
.renewal-metric {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
}

.renewal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
}

.renewal-header h2,
.renewal-table-panel h3 {
  margin: 0;
  color: #1f2937;
}

.renewal-header h2 {
  margin-bottom: 6px;
  font-size: 20px;
}

.renewal-header p,
.renewal-table-panel__header span,
.renewal-table small,
.dialog-hint {
  color: #6b7280;
  font-size: 13px;
}

.renewal-header p {
  margin: 0;
}

.renewal-guide {
  display: grid;
  grid-template-columns: minmax(220px, 0.8fr) minmax(0, 2fr);
  gap: 16px;
  padding: 12px 14px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fbff;
}

.renewal-guide__intro {
  display: grid;
  align-content: start;
  gap: 4px;
}

.renewal-guide__intro strong {
  color: #1e3a5f;
  font-size: 13px;
}

.renewal-guide__intro span,
.renewal-guide__steps small {
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.renewal-guide__steps {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.renewal-guide__steps li {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  gap: 7px;
  align-items: start;
}

.renewal-guide__steps li > span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #dbeafe;
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
}

.renewal-guide__steps div {
  display: grid;
  gap: 2px;
}

.renewal-guide__steps strong {
  color: #334155;
  font-size: 12px;
  line-height: 1.35;
}

.renewal-header__actions,
.renewal-table-panel__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.renewal-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.renewal-metric {
  display: grid;
  gap: 3px;
  padding: 12px 14px;
  text-align: left;
  cursor: pointer;
}

.renewal-metric--active {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgb(64 158 255 / 12%);
}

.renewal-metric span,
.renewal-metric small {
  color: #6b7280;
  font-size: 12px;
}

.renewal-metric strong {
  color: #1f2937;
  font-size: 20px;
}

.renewal-metric--danger strong {
  color: #dc2626;
}

.renewal-metric--warning strong {
  color: #b7791f;
}

.renewal-table-panel {
  padding: 12px;
  overflow-x: auto;
}

.renewal-table-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.renewal-table strong,
.renewal-table small {
  display: block;
}

.renewal-table small {
  margin-top: 3px;
}

.selection-count {
  color: #2563eb;
}

.dialog-hint {
  margin: 0 0 16px;
  line-height: 1.6;
}

.settings-form :deep(.el-form-item__content) {
  display: flex;
  gap: 8px;
}

.unit-label {
  color: #6b7280;
  font-size: 13px;
}

.preview-layout {
  display: grid;
  grid-template-columns: minmax(230px, 0.7fr) minmax(0, 1.5fr);
  min-height: 340px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  overflow: hidden;
}

.preview-list {
  overflow: auto;
  border-right: 1px solid #e5e7eb;
  background: #f8fafc;
}

.preview-list__item {
  display: grid;
  width: 100%;
  gap: 3px;
  padding: 12px;
  border: 0;
  border-bottom: 1px solid #e5e7eb;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.preview-list__item--active {
  background: #eff6ff;
}

.preview-list__item span,
.preview-list__item small {
  overflow: hidden;
  color: #6b7280;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.email-preview {
  display: grid;
  align-content: start;
  gap: 10px;
  padding: 16px;
}

.email-preview__line {
  display: grid;
  grid-template-columns: 64px 1fr;
  gap: 8px;
  font-size: 13px;
}

.email-preview__line span {
  color: #6b7280;
}

.email-preview pre {
  margin: 8px 0 0;
  padding: 14px;
  overflow: auto;
  border-radius: 6px;
  background: #f8fafc;
  color: #374151;
  font: 13px/1.65 inherit;
  white-space: pre-wrap;
}

:deep(.renewal-row--expired td) {
  background: #fff5f5 !important;
}

@media (max-width: 960px) {
  .renewal-header,
  .renewal-table-panel__header,
  .renewal-guide {
    align-items: stretch;
    flex-direction: column;
  }

  .renewal-guide {
    grid-template-columns: 1fr;
  }

  .renewal-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .preview-layout {
    grid-template-columns: 1fr;
  }

  .preview-list {
    max-height: 180px;
    border-right: 0;
    border-bottom: 1px solid #e5e7eb;
  }
}

@media (max-width: 640px) {
  .renewal-guide__steps {
    grid-template-columns: 1fr;
  }
}
</style>
