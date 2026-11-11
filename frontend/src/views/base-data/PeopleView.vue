<template>
  <div class="page-stack people-page">
    <section class="people-header" :class="{ 'people-header--mobile-without-actions': activeTab === 'students' }">
      <div>
        <span class="people-header__title">People & accounts</span>
        <p class="people-header__hint">
          {{ headerHint }}
        </p>
      </div>
      <div class="people-header__actions" :class="{ 'people-header__actions--single': activeTab !== 'teachers' }">
        <template v-if="activeTab === 'students'">
          <el-button type="primary" @click="openStudentCreate">Add student</el-button>
        </template>
        <template v-else-if="activeTab === 'teachers'">
          <el-button @click="openSubjectManager">Manage subjects</el-button>
          <el-button type="primary" plain @click="openTeacherCreate">Add teacher</el-button>
        </template>
        <el-button v-else-if="activeTab === 'managers'" type="primary" @click="openManagerCreate">Add manager</el-button>
      </div>
    </section>

    <el-tabs v-model="activeTab" class="people-tabs" @tab-change="syncRouteTab">
      <el-tab-pane label="Students" name="students">
        <StudentsView ref="studentsViewRef" />
      </el-tab-pane>
      <el-tab-pane label="Teachers" name="teachers">
        <TeachersView ref="teachersViewRef" />
      </el-tab-pane>
      <el-tab-pane v-if="canManageManagers" label="Managers" name="managers">
        <ManagerAccountsView ref="managerAccountsRef" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { isAdminUser } from '@/types/auth';

import StudentsView from './StudentsView.vue';
import TeachersView from './TeachersView.vue';
import ManagerAccountsView from '../system/ManagerAccountsView.vue';

type PeopleTab = 'students' | 'teachers' | 'managers';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const canManageManagers = computed(() => isAdminUser(authStore.currentUser));
const activeTab = ref<PeopleTab>(initialTab(route.query.tab, canManageManagers.value));
const headerHint = computed(() => activeTab.value === 'managers'
  ? 'Manage manager accounts and review each account’s registration history totals.'
  : 'Manage student and teacher profiles. Login access is handled inside each row.');
const studentsViewRef = ref<InstanceType<typeof StudentsView>>();
const teachersViewRef = ref<InstanceType<typeof TeachersView>>();
const managerAccountsRef = ref<InstanceType<typeof ManagerAccountsView>>();

watch(
  () => route.query.tab,
  (tab) => {
    activeTab.value = initialTab(tab, canManageManagers.value);
  },
);

function syncRouteTab(tab: string | number) {
  const nextTab = initialTab(tab, canManageManagers.value);
  router.replace({
    name: 'admin-config-people',
    query: {
      ...route.query,
      tab: nextTab,
    },
  });
}

function initialTab(value: unknown, allowManagers: boolean): PeopleTab {
  if (value === 'teachers') return 'teachers';
  if (value === 'managers' && allowManagers) return 'managers';
  return 'students';
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

async function openSubjectManager() {
  activeTab.value = 'teachers';
  await syncTabAndRender('teachers');
  teachersViewRef.value?.openSubjectManager();
}

async function openManagerCreate() {
  activeTab.value = 'managers';
  await syncTabAndRender('managers');
  managerAccountsRef.value?.openCreateDialog();
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

@media (max-width: 640px) {
  .people-header {
    min-height: 0;
    padding: 0;
    border: 0;
    background: transparent;
  }

  .people-header > div:first-child { display: none; }

  /* Students keeps its primary action beside the in-panel search row. */
  .people-header--mobile-without-actions { display: none; }

  .people-header__actions {
    display: flex;
    justify-content: flex-end;
    width: 100%;
    gap: 8px;
  }

  .people-header__actions--single { justify-content: flex-end; }

  .people-header__actions > .el-button {
    width: auto;
    min-height: 44px;
    flex: 0 1 auto;
    margin: 0;
    padding: 0 14px;
    font-size: 13px;
  }

}

:global(html.mobile-layout) & {
  .people-page .people-tabs.el-tabs :deep(.el-tabs__header) {
    border: 1px solid #cbd8e6;
    border-radius: 10px;
    background: #e5edf5;
    box-shadow: 0 1px 2px rgb(29 50 79 / 6%);
  }
}
</style>
