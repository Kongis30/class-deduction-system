<template>
  <div class="page-stack people-page">
    <section class="people-header">
      <div>
        <span class="people-header__title">People</span>
        <p class="people-header__hint">
          Manage students and teachers here. Login access is handled inside each row, so staff do not need a separate Accounts page.
        </p>
      </div>
    </section>

    <el-tabs v-model="activeTab" class="people-tabs" @tab-change="syncRouteTab">
      <el-tab-pane label="Students" name="students">
        <StudentsView />
      </el-tab-pane>
      <el-tab-pane label="Teachers" name="teachers">
        <TeachersView />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import StudentsView from './StudentsView.vue';
import TeachersView from './TeachersView.vue';

type PeopleTab = 'students' | 'teachers';

const route = useRoute();
const router = useRouter();
const activeTab = ref<PeopleTab>(route.query.tab === 'teachers' ? 'teachers' : 'students');

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
</script>

<style scoped>
.people-header {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
  padding: 16px 20px;
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
