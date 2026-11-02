<template>
  <div class="calendar-home page-stack">
    <section class="calendar-hero">
      <div>
        <span class="calendar-hero__eyebrow">Calendar</span>
        <h2>{{ title }}</h2>
        <p>{{ description }}</p>
      </div>
    </section>

    <div v-if="canViewAllCalendars" class="calendar-tabs-toolbar">
      <el-tabs v-model="activeTab" class="calendar-tabs">
        <el-tab-pane name="teacher">
          <template #label>
            <span class="calendar-tab-label calendar-tab-label--desktop">Teacher Calendar</span>
            <span class="calendar-tab-label calendar-tab-label--mobile">Teachers</span>
          </template>
          <TeacherScheduleView />
        </el-tab-pane>
        <el-tab-pane name="student">
          <template #label>
            <span class="calendar-tab-label calendar-tab-label--desktop">Student Calendar</span>
            <span class="calendar-tab-label calendar-tab-label--mobile">Students</span>
          </template>
          <StudentScheduleView ref="studentCalendarRef" />
        </el-tab-pane>
        <el-tab-pane name="room">
          <template #label>
            <span class="calendar-tab-label calendar-tab-label--desktop">Room Calendar</span>
            <span class="calendar-tab-label calendar-tab-label--mobile">Rooms</span>
          </template>
          <ClassroomOccupancyView />
        </el-tab-pane>
      </el-tabs>
      <div
        v-if="activeTab === 'student'"
        class="calendar-tabs-toolbar__action"
      >
        <el-button type="primary" plain @click="studentCalendarRef?.openDailyPrivateLessons()">Daily 1-on-1</el-button>
      </div>
    </div>

    <TeacherScheduleView v-else-if="workspace === 'TEACHER'" />
    <StudentScheduleView v-else-if="workspace === 'STUDENT'" />
    <TeacherScheduleView v-else />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { useAuthStore } from '@/stores/auth';
import { getUserWorkspace } from '@/types/auth';
import ClassroomOccupancyView from './ClassroomOccupancyView.vue';
import StudentScheduleView from './StudentScheduleView.vue';
import TeacherScheduleView from './TeacherScheduleView.vue';

const authStore = useAuthStore();
const route = useRoute();
const activeTab = ref('teacher');
const studentCalendarRef = ref<{ openDailyPrivateLessons: () => void }>();

const workspace = computed(() => getUserWorkspace(authStore.currentUser));
const canViewAllCalendars = computed(() => workspace.value === 'STAFF');

watch(
  () => [route.query.tab, route.query.classroomId, canViewAllCalendars.value] as const,
  ([tab, classroomId, canViewAll]) => {
    if (!canViewAll) return;
    if (tab === 'teacher' || tab === 'student' || tab === 'room') {
      activeTab.value = tab;
    } else if (classroomId) {
      activeTab.value = 'room';
    }
  },
  { immediate: true },
);

const title = computed(() => {
  switch (workspace.value) {
    case 'TEACHER':
      return 'My Teaching Schedule';
    case 'STUDENT':
      return 'My Class Schedule';
    default:
      return 'School Schedule';
  }
});

const description = computed(() => {
  switch (workspace.value) {
    case 'TEACHER':
      return 'View your own weekly lessons and room assignments.';
    case 'STUDENT':
      return 'View your own weekly classes, teachers, and rooms.';
    default:
      return 'View teacher, student, and room calendars from one place.';
  }
});
</script>

<style scoped>
.calendar-home {
  gap: 16px;
  min-height: max(calc(100vh - 92px), 860px);
  overflow: auto;
}

.calendar-tabs-toolbar { position: relative; min-width: 0; }
.calendar-tabs-toolbar__action {
  position: absolute;
  top: 16px;
  right: 16px;
  z-index: 2;
  display: flex;
  height: 40px;
  align-items: center;
}
.calendar-tabs-toolbar :deep(.el-tabs__header) { padding-right: 128px; }

.calendar-hero {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 22px;
  border-radius: 18px;
  background: linear-gradient(135deg, #eef6ff, #f8fbff);
  border: 1px solid #d9e8ff;
  flex-shrink: 0;
}

.calendar-hero__eyebrow {
  display: inline-flex;
  margin-bottom: 6px;
  color: #3b82f6;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.calendar-hero h2 {
  margin: 0;
  font-size: 24px;
  color: #1f2a44;
}

.calendar-hero p {
  margin: 6px 0 0;
  color: #64748b;
}

.calendar-tabs {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 760px;
  padding: 16px;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.08);
}

.calendar-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.calendar-tabs :deep(.el-tab-pane),
.calendar-tabs :deep(.page-stack) {
  height: 100%;
  min-height: 0;
}

.calendar-tabs :deep(.classroom-calendar) {
  min-height: 760px;
}

.calendar-tab-label--mobile {
  display: none;
}

@media (max-width: 640px) {
  .calendar-home { min-height: 0; height: auto; overflow: visible; gap: 4px; }
  .calendar-hero { display: none; }
  .calendar-tabs { min-height: 0; padding: 0; border-radius: 0; background: transparent; box-shadow: none; }
  .calendar-tabs-toolbar__action { top: 4px; right: 4px; height: 44px; }
  .calendar-tabs :deep(.el-tabs__header) {
    margin: 0 0 8px;
    padding: 4px 120px 4px 4px;
    border-radius: 12px;
    background: #eaf0f6;
  }
  .calendar-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }
  .calendar-tabs :deep(.el-tabs__nav) {
    display: flex;
    width: 100%;
    border: 0;
  }
  .calendar-tabs :deep(.el-tabs__active-bar) { display: none; }
  .calendar-tabs :deep(.el-tabs__item) {
    display: flex;
    flex: 1 1 0;
    justify-content: center;
    min-width: 0;
    height: 44px;
    padding: 0 8px;
    border: 0;
    border-radius: 9px;
    color: #52647c;
    font-size: 14px;
    font-weight: 600;
  }
  .calendar-tabs :deep(.el-tabs__item.is-active) {
    background: #fff;
    color: #2563eb;
    box-shadow: 0 1px 3px rgba(15, 23, 42, 0.12);
  }
  .calendar-tabs :deep(.el-tabs__content) { height: auto; overflow: visible; }
  .calendar-tabs :deep(.el-tab-pane),
  .calendar-tabs :deep(.page-stack) { height: auto; min-height: 0; overflow: visible; }
  .calendar-tab-label--desktop { display: none; }
  .calendar-tab-label--mobile { display: inline; }
  .calendar-tabs :deep(.teacher-calendar__header),
  .calendar-tabs :deep(.student-calendar__header) { display: none; }
  .calendar-tabs :deep(.classroom-calendar__title),
  .calendar-tabs :deep(.classroom-calendar__note),
  .calendar-tabs :deep(.classroom-help) { display: none; }
  .calendar-tabs :deep(.classroom-calendar) { min-height: 0; }
}
</style>
