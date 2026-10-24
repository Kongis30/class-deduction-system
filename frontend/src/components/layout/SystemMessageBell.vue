<template>
  <div v-if="isAdmin" class="system-message-bell">
    <el-popover
      :visible="inboxVisible"
      :placement="inboxPlacement"
      :width="inboxWidth"
      :teleported="true"
      :show-arrow="false"
      trigger="manual"
      popper-class="system-message-inbox-popover"
      @update:visible="inboxVisible = $event"
    >
      <template #reference>
        <button
          type="button"
          class="system-message-bell__trigger"
          :aria-label="unreadCount ? `${unreadCount} unread system messages` : 'System messages'"
          :aria-expanded="inboxVisible"
          title="System messages"
          @click="toggleInbox"
        >
          <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="system-message-bell__badge">
            <el-icon><Bell /></el-icon>
          </el-badge>
        </button>
      </template>

      <section class="system-message-inbox" aria-label="System message list">
        <header class="system-message-inbox__header">
          <div>
            <strong>System messages</strong>
            <span v-if="unreadCount" class="system-message-dialog__unread">{{ unreadCount }} unread</span>
          </div>
          <el-button size="small" circle aria-label="Close system messages" @click="inboxVisible = false">
            <el-icon><Close /></el-icon>
          </el-button>
        </header>

        <p v-if="loadError" class="system-message-dialog__notice">
          Could not load messages. <button type="button" @click="loadInbox">Retry</button>
        </p>
        <div v-else-if="loading && messages.length === 0" class="system-message-inbox__empty">Loading messages…</div>
        <div v-else-if="messages.length === 0" class="system-message-inbox__empty">No system messages yet.</div>
        <div v-else class="system-message-inbox__list">
          <button
            v-for="message in messages"
            :key="message.id"
            type="button"
            class="system-message-inbox__item"
            :class="{ 'system-message-inbox__item--unread': !message.readAt }"
            @click="openMessage(message)"
          >
            <span class="system-message-inbox__copy">
              <strong :title="message.title">{{ message.title }}</strong>
              <small>{{ formatWeekRange(message.weekStartDate, message.weekEndDate) }}</small>
            </span>
            <time v-if="message.createdAt" class="system-message-inbox__sent-at" :datetime="message.createdAt">
              {{ formatSentAt(message.createdAt) }}
            </time>
          </button>
        </div>
      </section>
    </el-popover>

    <el-dialog
      v-model="detailVisible"
      :title="activeMessage?.title ?? 'Birthday reminder'"
      :width="dialogWidth"
      :show-close="false"
      :close-on-click-modal="true"
      :append-to-body="true"
      aria-label="Birthday reminder details"
      class="system-message-bell__dialog"
    >
      <template #header>
        <header class="system-message-dialog__header">
          <strong>{{ activeMessage?.title ?? 'Birthday reminder' }}</strong>
          <div class="system-message-dialog__actions">
            <el-button size="small" circle aria-label="Close birthday reminder" @click="detailVisible = false">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
        </header>
      </template>

      <div class="system-message-dialog__body">
        <p v-if="readError" class="system-message-dialog__notice">Could not mark this message as read. It will remain unread.</p>
        <div v-if="activeMessage" class="system-message-focus">
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

        <div v-if="activeMessage" class="system-message-detail">
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
        </div>
        <div v-else class="system-message-dialog__empty">This message is no longer available.</div>
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
const readError = ref(false);
const inboxVisible = ref(false);
const detailVisible = ref(false);
const selectedMessageId = ref<string | null>(null);
let pollTimer: number | undefined;
let requestPending = false;

const messages = computed(() => inbox.value.messages);
const unreadCount = computed(() => inbox.value.unreadCount);
const dialogWidth = 'min(680px, calc(100vw - 24px))';
const inboxWidth = computed(() => appStore.isMobileViewport ? 'min(360px, calc(100vw - 20px))' : 340);
const inboxPlacement = computed<'top-start' | 'right-end'>(() => appStore.isMobileViewport ? 'top-start' : 'right-end');
const activeMessage = computed(() => messages.value.find(message => String(message.id) === selectedMessageId.value)
  ?? null);

async function loadInbox() {
  if (!isAdmin.value || requestPending || document.visibilityState === 'hidden') return;
  requestPending = true;
  loading.value = true;
  try {
    const response = await fetchSystemMessageInbox();
    inbox.value = response.data.data;
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

async function openMessage(message: SystemMessageItem) {
  selectedMessageId.value = String(message.id);
  inboxVisible.value = false;
  detailVisible.value = true;
  readError.value = false;
  try {
    await markRead(message);
  } catch {
    readError.value = true;
  }
}

// 铃铛只展开收件箱；只有点击具体消息才进入生日名单详情。
function toggleInbox() {
  inboxVisible.value = !inboxVisible.value;
  if (inboxVisible.value) void loadInbox();
}

async function openStudent(studentId: BirthdayStudentMessageItem['studentId'], message: SystemMessageItem) {
  try {
    await markRead(message);
  } catch {
    readError.value = true;
  }
  detailVisible.value = false;
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

function formatSentAt(value: string) {
  // 后端 createdAt 是美国东部本地时间，不带时区；按原始时钟显示并标注 ET。
  const parts = value.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/);
  if (!parts) return value;
  const localTime = new Date(Date.UTC(Number(parts[1]), Number(parts[2]) - 1, Number(parts[3]), Number(parts[4]), Number(parts[5])));
  const formatted = new Intl.DateTimeFormat('en-US', {
    timeZone: 'UTC', month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit',
  }).format(localTime);
  return `${formatted} ET`;
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
  width: 42px;
  min-height: 42px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 8px;
  background: transparent;
  padding: 0;
  color: rgba(255, 255, 255, 0.78);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.system-message-bell__trigger:hover,
.system-message-bell__trigger:focus-visible { outline: none; background: rgba(255, 255, 255, 0.1); color: #fff; }
.system-message-bell__trigger > .el-icon { font-size: 18px; }
.system-message-bell__badge :deep(.el-badge__content) { border: 1px solid #17212f; }

:global(.system-message-inbox-popover.el-popper) {
  box-sizing: border-box;
  max-width: calc(100vw - 20px);
  overflow: hidden;
  border: 1px solid #e1e7ef;
  border-radius: 12px;
  padding: 0 !important;
  box-shadow: 0 14px 38px rgb(24 43 67 / 18%);
}
.system-message-inbox { display: flex; height: 360px; max-height: calc(100dvh - 104px); min-height: 180px; flex-direction: column; color: #26384c; }
.system-message-inbox__header { display: flex; min-height: 48px; align-items: center; justify-content: space-between; gap: 12px; border-bottom: 1px solid #edf1f5; padding: 0 12px 0 15px; }
.system-message-inbox__header > div { display: flex; min-width: 0; align-items: center; gap: 8px; }
.system-message-inbox__header strong { color: #26384c; font-size: 14px; font-weight: 650; }
.system-message-inbox__list { display: grid; min-height: 0; flex: 1 1 auto; align-content: start; gap: 6px; overflow-x: hidden; overflow-y: auto; overscroll-behavior: contain; padding: 8px; }
.system-message-inbox__item { display: flex; width: 100%; min-height: 68px; align-items: stretch; justify-content: space-between; gap: 8px; border: 1px solid #e1e7ef; border-radius: 8px; background: #fff; padding: 9px 12px 7px; color: inherit; text-align: left; cursor: pointer; }
.system-message-inbox__item:hover { background: #f7faff; }
.system-message-inbox__copy { display: grid; min-width: 0; flex: 1 1 auto; align-content: center; gap: 4px; }
.system-message-inbox__copy strong { overflow: hidden; color: #33485f; font-size: 12px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.system-message-inbox__item--unread .system-message-inbox__copy strong { color: #243a53; }
.system-message-inbox__copy small { color: #78889b; font-size: 11px; }
.system-message-inbox__sent-at { align-self: flex-end; flex: 0 0 auto; color: #8a96a5; font-size: 9px; line-height: 1.2; white-space: nowrap; }
.system-message-inbox__empty { display: grid; min-height: 118px; place-items: center; padding: 16px; color: #7c8999; font-size: 12px; text-align: center; }
:global(.system-message-inbox-popover .el-popper__arrow) { display: none; }

.system-message-dialog__header { display: flex; min-height: 30px; align-items: center; justify-content: space-between; gap: 12px; }
.system-message-dialog__header strong { color: #26384c; font-size: 15px; font-weight: 650; line-height: 1.2; }
.system-message-dialog__unread { border-radius: 999px; background: #f1f5f9; padding: 3px 7px; color: #718198; font-size: 10px; line-height: 1; white-space: nowrap; }
.system-message-dialog__eyebrow { color: #74859a; font-size: 10px; font-weight: 700; letter-spacing: 0.09em; }
.system-message-dialog__actions { display: flex; flex: 0 0 auto; gap: 4px; }
.system-message-dialog__body { display: grid; min-height: 0; max-height: min(68vh, 620px); flex: 1 1 auto; gap: 18px; overflow-y: auto; overscroll-behavior: contain; color: #26384c; }
.system-message-detail { display: grid; gap: 18px; }
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
