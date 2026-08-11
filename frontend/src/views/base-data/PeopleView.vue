<template>
  <div class="page-stack people-page">
    <section class="people-header">
      <div>
        <span class="people-header__title">People</span>
        <p class="people-header__hint">
          Manage students and teachers here. Login access is handled inside each row, so staff do not need a separate Accounts page.
        </p>
      </div>
      <div class="people-header__actions">
        <el-button type="primary" @click="openStudentCreate">Add student</el-button>
        <el-button type="primary" plain @click="openTeacherCreate">Add teacher</el-button>
      </div>
    </section>

    <el-tabs v-model="activeTab" class="people-tabs" @tab-change="syncRouteTab">
      <el-tab-pane label="Students" name="students">
        <StudentsView ref="studentsViewRef" />
      </el-tab-pane>
      <el-tab-pane label="Teachers" name="teachers">
        <TeachersView ref="teachersViewRef" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import StudentsView from './StudentsView.vue';
import TeachersView from './TeachersView.vue';

type PeopleTab = 'students' | 'teachers';

const route = useRoute();
const router = useRouter();
const activeTab = ref<PeopleTab>(route.query.tab === 'teachers' ? 'teachers' : 'students');
const studentsViewRef = ref<InstanceType<typeof StudentsView>>();
const teachersViewRef = ref<InstanceType<typeof TeachersView>>();

watch(
  () => route.query.tab,
  (tab) => {
    activeTab.value = tab === 'teachers' ? 'teachers' : 'students';
  },
);

function syncRouteTab(tab: string | number) {
  const nextTab: PeopleTab = tab === 'teachers' ? 'teachers' : 'students';
  router.replace({
    name: 'admin-config-people',
    query: {
      ...route.query,
      tab: nextTab,
    },
  });
}

async function openStudentCreate() {
  activeTab.value = 'students';
  await syncTabAndRender('students');
  studentsViewRef.value?.openCreateDialog();
}

async function openTeacherCreate() {
  activeTab.value = 'teachers';
  await syncTabAndRender('teachers');
  teachersViewRef.value?.openCreateDialog();
}

async function syncTabAndRender(tab: PeopleTab) {
  await router.replace({
    name: 'admin-config-people',
    query: { ...route.query, tab },
  });
  await nextTick();
}
</script>

<style scoped>
.people-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
  padding: 16px 20px;
}

.people-header__actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
}

.people-header__title {
  display: block;
  font-size: 18px;
  font-weight: 700;
  color: #1f2937;
}

.people-header__hint {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}

.people-tabs :deep(.el-tabs__header) {
  margin: 0 0 12px;
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
  padding: 0 16px;
}

.people-tabs :deep(.el-tabs__content) {
  overflow: visible;
}
</style>
