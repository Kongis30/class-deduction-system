<template>
  <div class="calendar-home page-stack">
    <section class="calendar-hero">
      <div>
        <span class="calendar-hero__eyebrow">Calendar</span>
        <h2>{{ title }}</h2>
        <p>{{ description }}</p>
      </div>
    </section>

    <el-tabs v-if="canViewAllCalendars" v-model="activeTab" class="calendar-tabs">
      <el-tab-pane label="Teacher Calendar" name="teacher">
        <TeacherScheduleView />
      </el-tab-pane>
      <el-tab-pane label="Student Calendar" name="student">
        <StudentScheduleView />
      </el-tab-pane>
      <el-tab-pane label="Room Calendar" name="room">
        <ClassroomOccupancyView />
      </el-tab-pane>
    </el-tabs>

    <TeacherScheduleView v-else-if="isTeacher" />
    <StudentScheduleView v-else-if="isStudent" />
    <TeacherScheduleView v-else />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { useAuthStore } from '@/stores/auth';
import { isStudentUser, isTeacherUser } from '@/types/auth';
import ClassroomOccupancyView from './ClassroomOccupancyView.vue';
import StudentScheduleView from './StudentScheduleView.vue';
import TeacherScheduleView from './TeacherScheduleView.vue';

const authStore = useAuthStore();
const route = useRoute();
const activeTab = ref('teacher');

const isTeacher = computed(() => isTeacherUser(authStore.currentUser));
const isStudent = computed(() => isStudentUser(authStore.currentUser));
const canViewAllCalendars = computed(() => !isTeacher.value && !isStudent.value);

watch(
  () => route.query.tab,
  (tab) => {
    if (tab === 'room' && canViewAllCalendars.value) {
      activeTab.value = 'room';
    }
  },
  { immediate: true },
);

const title = computed(() => {
  if (isTeacher.value) return 'My Teaching Schedule';
  if (isStudent.value) return 'My Class Schedule';
  return 'School Schedule';
});

const description = computed(() => {
  if (isTeacher.value) return 'View your own weekly lessons and room assignments.';
  if (isStudent.value) return 'View your own weekly classes, teachers, and rooms.';
  return 'View teacher, student, and room calendars from one place.';
});
</script>

<style scoped>
.calendar-home {
  gap: 16px;
  min-height: max(calc(100vh - 92px), 860px);
  overflow: auto;
}

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
</style>
