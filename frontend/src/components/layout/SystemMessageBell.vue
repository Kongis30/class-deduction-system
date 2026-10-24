<template>
  <div v-if="isAdmin" class="system-message-bell">
    <button
      type="button"
      class="system-message-bell__trigger"
      :aria-label="unreadCount ? `${unreadCount} unread system messages` : 'System messages'"
      title="System messages"
      @click="openMessages"
    >
      <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="system-message-bell__badge">
        <el-icon><Bell /></el-icon>
      </el-badge>
      <span v-if="!appStore.isMobileViewport">Messages</span>
    </button>

    <el-dialog
      v-model="dialogVisible"
      title="System messages"
      :width="dialogWidth"
      :show-close="false"
      :close-on-click-modal="true"
      :append-to-body="true"
      aria-label="System messages"
      class="system-message-bell__dialog"
    >
      <template #header>
        <header class="system-message-dialog__header">
          <div class="system-message-dialog__title">
            <strong>System messages</strong>
            <span class="system-message-dialog__unread">{{ unreadCount }} unread</span>
          </div>
          <div class="system-message-dialog__actions">
            <el-button size="small" circle aria-label="Close system messages" @click="dialogVisible = false">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
        </header>
      </template>

      <div class="system-message-dialog__body">
        <p v-if="loadError" class="system-message-dialog__notice">
          Could not load messages. <button type="button" @click="loadInbox">Retry</button>
        </p>
        <div v-else-if="loading && messages.length === 0" class="system-message-dialog__empty">Loading messages…</div>
        <div v-else-if="messages.length === 0" class="system-message-dialog__empty">No system messages yet.</div>

        <template v-else-if="activeMessage">
          <div class="system-message-focus">
            <div>
              <span class="system-message-dialog__eyebrow">{{ activeMessage.title }}</span>
              <h2>{{ formatWeekRange(activeMessage.weekStartDate, activeMessage.weekEndDate) }}</h2>
            </div>
            <div class="system-message-focus__summary">
              <span class="system-message-focus__count">{{ birthdaySummary(activeMessage.itemCount) }}</span>
              <el-button
                size="small"
                circle
                plain
                class="system-message-print-button"
                aria-label="Print birthday list"
                title="Print birthday list"
                @click="printMessage"
              >
                <el-icon><Printer /></el-icon>
              </el-button>
            </div>
          </div>

          <div class="system-message-table-wrap">
            <table class="system-message-table">
              <thead>
                <tr>
                  <th scope="col">Student</th>
                  <th scope="col">Grade</th>
                  <th scope="col">Campus</th>
                  <th scope="col">Birthday</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="activeMessage.students.length === 0">
                  <td colspan="4" class="system-message-table__empty">No students have birthdays this week.</td>
                </tr>
                <tr v-for="student in activeMessage.students" :key="student.studentId">
                  <td>
                    <button type="button" class="system-message-table__student" @click="openStudent(student.studentId, activeMessage!)">
                      {{ student.studentName }}
                    </button>
                  </td>
                  <td>{{ student.grade || '—' }}</td>
                  <td>{{ student.campusName || '—' }}</td>
                  <td><time>{{ formatBirthdayDate(student.birthdayDate) }}</time></td>
                </tr>
              </tbody>
            </table>
          </div>

          <section v-if="historyMessages.length" class="system-message-history" aria-label="Previous messages">
            <h3>Previous weeks</h3>
            <button
              v-for="message in historyMessages"
              :key="message.id"
              type="button"
              class="system-message-history__item"
              :class="{ 'system-message-history__item--unread': !message.readAt }"
              @click="selectMessage(message)"
            >
              <span class="system-message-bell__unread-dot" aria-hidden="true" />
              <span>{{ formatWeekRange(message.weekStartDate, message.weekEndDate) }}</span>
              <small>{{ message.itemCount }} {{ message.itemCount === 1 ? 'birthday' : 'birthdays' }}</small>
            </button>
          </section>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Bell, Close, Printer } from '@element-plus/icons-vue';
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { fetchSystemMessageInbox, markSystemMessageRead } from '@/api/system';
import { useAppStore } from '@/stores/app';
import { useAuthStore } from '@/stores/auth';
import { isAdminUser } from '@/types/auth';
import type { BirthdayStudentMessageItem, SystemMessageInbox, SystemMessageItem } from '@/types/domain';

const appStore = useAppStore();
const authStore = useAuthStore();
const router = useRouter();
const isAdmin = computed(() => isAdminUser(authStore.currentUser));
const inbox = ref<SystemMessageInbox>({ unreadCount: 0, messages: [] });
const loading = ref(false);
const loadError = ref(false);
const dialogVisible = ref(false);
const selectedMessageId = ref<string | null>(null);
let pollTimer: number | undefined;
let requestPending = false;

const messages = computed(() => inbox.value.messages);
const unreadCount = computed(() => inbox.value.unreadCount);
const dialogWidth = 'min(680px, calc(100vw - 24px))';
const activeMessage = computed(() => messages.value.find(message => String(message.id) === selectedMessageId.value)
  ?? messages.value[0]
  ?? null);
const historyMessages = computed(() => messages.value.filter(message => message.id !== activeMessage.value?.id));

async function loadInbox() {
  if (!isAdmin.value || requestPending || document.visibilityState === 'hidden') return;
  requestPending = true;
  loading.value = true;
  try {
    const response = await fetchSystemMessageInbox();
    inbox.value = response.data.data;
    if (!messages.value.some(message => String(message.id) === selectedMessageId.value)) {
      selectedMessageId.value = messages.value[0] ? String(messages.value[0].id) : null;
    }
    loadError.value = false;
  } catch {
    loadError.value = true;
  } finally {
    requestPending = false;
    loading.value = false;
  }
}

async function markRead(message: SystemMessageItem) {
  if (message.readAt) return;
  await markSystemMessageRead(message.id);
  message.readAt = new Date().toISOString();
  inbox.value.unreadCount = Math.max(0, inbox.value.unreadCount - 1);
}

async function selectMessage(message: SystemMessageItem) {
  selectedMessageId.value = String(message.id);
  try {
    await markRead(message);
  } catch {
    loadError.value = true;
  }
}

// 点击铃铛打开收件箱，默认选中最新一周并在展示内容时标为已读。
async function openMessages() {
  dialogVisible.value = true;
  await loadInbox();
  if (activeMessage.value) await selectMessage(activeMessage.value);
}

async function openStudent(studentId: BirthdayStudentMessageItem['studentId'], message: SystemMessageItem) {
  try {
    await markRead(message);
  } catch {
    loadError.value = true;
  }
  dialogVisible.value = false;
  await router.push({
    name: 'admin-config-people',
    query: { tab: 'students', studentId },
  });
}

function birthdaySummary(count: number) {
  return count === 1 ? '1 student has a birthday this week' : `${count} students have birthdays this week`;
}

function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, character => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  })[character] ?? character);
}

function printMessage() {
  if (!activeMessage.value) return;

  const message = activeMessage.value;
  const printWindow = window.open('', '_blank', 'width=820,height=640');
  if (!printWindow) return;

  const week = formatWeekRange(message.weekStartDate, message.weekEndDate);
  const rows = message.students.map(student => `
    <tr>
      <td>${escapeHtml(student.studentName)}</td>
      <td>${escapeHtml(student.grade || '—')}</td>
      <td>${escapeHtml(student.campusName || '—')}</td>
      <td>${escapeHtml(formatBirthdayDate(student.birthdayDate))}</td>
    </tr>`).join('');
  const emptyRow = '<tr><td class="empty" colspan="4">No students have birthdays this week.</td></tr>';

  printWindow.document.open();
  printWindow.document.write(`<!doctype html>
    <html lang="en">
      <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>${escapeHtml(message.title)} · ${escapeHtml(week)}</title>
        <style>
          @page { size: letter portrait; margin: 0.55in; }
          body { color: #26384c; font: 14px/1.45 Arial, sans-serif; }
          h1 { margin: 0; font-size: 20px; }
          p { margin: 5px 0 18px; color: #5f7187; }
          table { width: 100%; border-collapse: collapse; }
          th, td { border-bottom: 1px solid #dce3eb; padding: 9px 10px; text-align: left; }
          th { background: #f1f5f9; color: #52657b; font-size: 12px; }
          .empty { padding: 24px 10px; color: #718198; text-align: center; }
        </style>
      </head>
      <body>
        <h1>${escapeHtml(message.title)}</h1>
        <p>${escapeHtml(week)} · ${escapeHtml(birthdaySummary(message.itemCount))}</p>
        <table>
          <thead><tr><th>Student</th><th>Grade</th><th>Campus</th><th>Birthday</th></tr></thead>
          <tbody>${rows || emptyRow}</tbody>
        </table>
        <script>window.addEventListener('load', () => { window.focus(); window.print(); });<\\/script>
      </body>
    </html>`);
  printWindow.document.close();
}

function formatDateOnly(value: string, options: Intl.DateTimeFormatOptions) {
  const date = new Date(`${value}T00:00:00Z`);
  return new Intl.DateTimeFormat('en-US', { ...options, timeZone: 'UTC' }).format(date);
}

function formatWeekRange(start: string, end: string) {
  return `${formatDateOnly(start, { month: 'short', day: 'numeric' })} – ${formatDateOnly(end, { month: 'short', day: 'numeric' })}`;
}

function formatBirthdayDate(value: string) {
  return formatDateOnly(value, { weekday: 'short', month: 'short', day: 'numeric' });
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible') void loadInbox();
}

watch(isAdmin, (admin) => {
  if (pollTimer !== undefined) window.clearInterval(pollTimer);
  if (!admin) {
    inbox.value = { unreadCount: 0, messages: [] };
    return;
  }
  void loadInbox();
  pollTimer = window.setInterval(() => void loadInbox(), 60_000);
}, { immediate: true });

if (typeof document !== 'undefined') {
  document.addEventListener('visibilitychange', handleVisibilityChange);
}

onBeforeUnmount(() => {
  if (pollTimer !== undefined) window.clearInterval(pollTimer);
  document.removeEventListener('visibilitychange', handleVisibilityChange);
});
</script>

<style scoped>
.system-message-bell { display: inline-flex; min-width: 0; }
.system-message-bell__trigger {
  display: inline-flex;
  min-height: 42px;
  align-items: center;
  justify-content: center;
  gap: 9px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  padding: 0 12px;
  color: rgba(255, 255, 255, 0.78);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.system-message-bell__trigger:hover,
.system-message-bell__trigger:focus-visible { outline: none; background: rgba(255, 255, 255, 0.1); color: #fff; }
.system-message-bell__trigger > .el-icon { font-size: 18px; }
.system-message-bell__badge :deep(.el-badge__content) { border: 1px solid #17212f; }
.system-message-bell__unread-dot { width: 7px; height: 7px; flex: 0 0 7px; border-radius: 50%; background: transparent; }
.system-message-history__item--unread .system-message-bell__unread-dot { background: #3987e8; }

.system-message-dialog__header { display: flex; min-height: 30px; align-items: center; justify-content: space-between; gap: 12px; }
.system-message-dialog__title { display: flex; min-width: 0; align-items: center; gap: 9px; }
.system-message-dialog__header strong { color: #26384c; font-size: 15px; font-weight: 650; line-height: 1.2; }
.system-message-dialog__unread { border-radius: 999px; background: #f1f5f9; padding: 3px 7px; color: #718198; font-size: 10px; line-height: 1; white-space: nowrap; }
.system-message-dialog__eyebrow { color: #74859a; font-size: 10px; font-weight: 700; letter-spacing: 0.09em; }
.system-message-dialog__actions { display: flex; flex: 0 0 auto; gap: 4px; }
.system-message-dialog__body { display: grid; min-height: 0; max-height: min(68vh, 620px); flex: 1 1 auto; gap: 18px; overflow-y: auto; overscroll-behavior: contain; color: #26384c; }
.system-message-focus { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; border-bottom: 1px solid #e7ecf2; padding: 0 0 14px; }
.system-message-focus h2 { margin: 4px 0 0; color: #24384f; font-size: 20px; font-weight: 650; line-height: 1.25; }
.system-message-focus__summary { display: flex; align-items: center; gap: 10px; }
.system-message-focus__count { color: #63758b; font-size: 12px; text-align: right; }
:global(.system-message-bell__dialog .system-message-print-button) {
  width: 36px;
  min-width: 36px;
  min-height: 36px !important;
  height: 36px !important;
  padding: 0;
}
.system-message-table-wrap { overflow-x: auto; border: 1px solid #e2e8f0; border-radius: 9px; }
.system-message-table { width: 100%; border-collapse: collapse; font-size: 13px; text-align: left; }
.system-message-table th { background: #f4f7fa; color: #65778d; font-size: 11px; font-weight: 650; white-space: nowrap; }
.system-message-table th,
.system-message-table td { padding: 10px 12px; }
.system-message-table tbody tr + tr { border-top: 1px solid #edf1f5; }
.system-message-table td { color: #44576c; }
.system-message-table__student { border: 0; background: transparent; padding: 0; color: #273d55; font: inherit; font-weight: 650; text-align: left; cursor: pointer; }
.system-message-table__student:hover { color: #2878d0; }
.system-message-table time { color: #334b65; font-weight: 600; white-space: nowrap; }
.system-message-table__empty { padding: 24px 12px !important; color: #7c8999 !important; text-align: center; }
.system-message-history { display: grid; gap: 2px; }
.system-message-history h3 { margin: 0 0 4px; color: #718198; font-size: 11px; font-weight: 700; letter-spacing: 0.07em; text-transform: uppercase; }
.system-message-history__item { display: flex; min-height: 38px; align-items: center; gap: 9px; border: 0; border-top: 1px solid #edf1f5; background: #fff; padding: 7px 2px; color: #344a61; font: inherit; font-size: 12px; text-align: left; cursor: pointer; }
.system-message-history__item > span:nth-child(2) { flex: 1; }
.system-message-history__item small { color: #7c8999; font-size: 11px; }
.system-message-history__item:hover { background: #f8fafc; }
.system-message-dialog__empty { padding: 34px 12px; color: #7c8999; font-size: 13px; text-align: center; }
.system-message-dialog__notice { margin: 0; color: #b54747; font-size: 12px; }
.system-message-dialog__notice button { border: 0; background: transparent; color: #2878d0; cursor: pointer; }
:global(.system-message-bell__dialog.el-dialog) {
  display: flex;
  min-width: min(320px, calc(100vw - 24px));
  min-height: min(240px, calc(100dvh - 48px));
  max-width: calc(100vw - 24px);
  max-height: calc(100dvh - 48px);
  flex-direction: column;
  overflow: hidden;
  border-radius: 14px;
}
:global(.system-message-bell__dialog .el-dialog__header) { margin: 0; border-bottom: 1px solid #edf1f5; padding: 6px 16px; }
:global(.system-message-bell__dialog .el-dialog__body) { min-height: 0; flex: 1 1 auto; overflow: hidden; padding: 12px 16px 16px; }
:global(.system-message-bell__dialog .system-message-dialog__actions .el-button) { width: 34px; min-width: 34px; min-height: 34px !important; height: 34px !important; padding: 0; }

@media (max-width: 920px) {
  .system-message-bell { display: inline-flex; width: auto; justify-content: center; }
  .system-message-bell__trigger { width: 44px; min-height: 44px; padding: 0; color: #53657c; }
  .system-message-bell__badge :deep(.el-badge__content) { border-color: #fff; }
  :global(.system-message-bell__dialog.el-dialog) { margin-top: 6vh !important; max-height: calc(100dvh - 6vh - 12px); }
  :global(.system-message-bell__dialog .el-dialog__header) { padding: 5px 12px; }
  :global(.system-message-bell__dialog .el-dialog__body) { padding: 10px 12px 14px; }
  .system-message-dialog__body { gap: 14px; }
  .system-message-focus { align-items: flex-start; flex-direction: column; gap: 5px; }
  .system-message-focus__summary { width: 100%; justify-content: space-between; }
  .system-message-focus__count { text-align: left; }
  :global(.system-message-bell__dialog .system-message-print-button) {
    width: 44px;
    min-width: 44px;
    min-height: 44px !important;
    height: 44px !important;
  }
  .system-message-table { font-size: 12px; }
  .system-message-table th,
  .system-message-table td { padding: 9px 7px; }
  .system-message-table th { font-size: 10px; }
}
</style>
