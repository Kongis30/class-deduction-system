<template>
  <div class="page-stack">
    <section class="hero-card">
      <div>
        <span class="hero-card__eyebrow">Dashboard</span>
        <h2>What do you need to do?</h2>
        <p>Start from a daily task. The system will take you to the right page so staff do not need to learn every menu first.</p>
      </div>
    </section>

    <section class="task-panel">
      <button
        v-for="task in dailyTasks"
        :key="task.title"
        type="button"
        class="task-card"
        @click="router.push(task.path)"
      >
        <span>{{ task.step }}</span>
        <strong>{{ task.title }}</strong>
        <small>{{ task.description }}</small>
      </button>
    </section>

    <section class="stat-grid">
      <article v-for="card in cards" :key="card.label" class="stat-card">
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
        <small>{{ card.hint }}</small>
      </article>
    </section>


    <section v-if="summary.campusDetails?.length" class="campus-section">
      <div class="campus-section__header">
        <span class="campus-section__title">Per-Campus Breakdown</span>
      </div>
      <div class="campus-grid">
        <article v-for="campus in summary.campusDetails" :key="campus.campusId" class="campus-card">
          <div class="campus-card__header">
            <span class="campus-card__name">{{ campus.campusName }}</span>
          </div>
          <div class="campus-card__stats">
            <div class="campus-stat">
              <span>Today's Lessons</span>
              <strong>{{ campus.todayLessons }}</strong>
            </div>
            <div class="campus-stat">
              <span>Classrooms</span>
              <strong>{{ campus.classrooms }}</strong>
            </div>
            <div class="campus-stat">
              <span>Students</span>
              <strong>{{ campus.students }}</strong>
            </div>
          </div>
        </article>
      </div>
    </section>

    <section class="content-grid">
      <article class="panel-card">
        <div class="panel-card__header">
          <h3>First-time setup checklist</h3>
          <span>Complete these before daily use</span>
        </div>
        <ul class="setup-list">
          <li v-for="item in setupItems" :key="item.title" :class="{ 'setup-list__item--done': item.done }">
            <span>{{ item.done ? 'Done' : 'To do' }}</span>
            <div>
              <strong>{{ item.title }}</strong>
              <small>{{ item.description }}</small>
            </div>
            <button type="button" @click="router.push(item.path)">{{ item.action }}</button>
          </li>
        </ul>
      </article>

      <article class="panel-card">
        <div class="panel-card__header">
          <h3>Quick links</h3>
          <span>Low-frequency admin tools</span>
        </div>
        <div class="quick-grid">
          <button
            v-for="action in actions"
            :key="action.label"
            class="quick-grid__item"
            @click="action.handler"
          >
            {{ action.label }}
          </button>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { fetchDashboardSummary } from '@/api/dashboard';
import type { DashboardSummary } from '@/types/domain';

const router = useRouter();

const summary = ref<DashboardSummary>({
  totalStudents: 0,
  totalTeachers: 0,
  totalClassrooms: 0,
  totalCampuses: 0,
  totalCourseProducts: 0,
  todayLessonsCount: 0,
  lowBalancePackages: 0,
  campusDetails: [],
});

const cards = computed(() => [
  { label: 'Students', value: String(summary.value.totalStudents), hint: 'Active enrolled students' },
  { label: 'Teachers', value: String(summary.value.totalTeachers), hint: 'Active instructors' },
  { label: 'Classrooms', value: String(summary.value.totalClassrooms), hint: 'Available rooms' },
  { label: 'Campuses', value: String(summary.value.totalCampuses), hint: 'Teaching locations' },
  { label: 'Course Products', value: String(summary.value.totalCourseProducts), hint: 'Available courses' },
  { label: "Today's Lessons", value: String(summary.value.todayLessonsCount), hint: 'Scheduled for today' },
  { label: 'Low Balance Alerts', value: String(summary.value.lowBalancePackages), hint: 'Packages below threshold' },
]);

const dailyTasks = [
  {
    step: '1',
    title: 'Register a student',
    description: 'Create a new registration, record payment, and print the receipt.',
    path: '/registration',
  },
  {
    step: '2',
    title: 'Review and deduct lessons',
    description: 'Check teacher confirmations and complete admin deduction.',
    path: '/schedule/adjustments',
  },
  {
    step: '3',
    title: 'Check conflicts',
    description: 'Open student, teacher, and room calendars before changing schedules.',
    path: '/classrooms/occupancy',
  },
  {
    step: '4',
    title: 'Follow up balances',
    description: 'Find unpaid, ending, or low-balance students in one list.',
    path: '/data-summary',
  },
];

const setupItems = computed(() => [
  {
    title: 'Create campuses and rooms',
    description: 'Needed for registration campus selection and classroom calendars.',
    done: summary.value.totalCampuses > 0 && summary.value.totalClassrooms > 0,
    action: 'Open',
    path: '/admin/config/campuses',
  },
  {
    title: 'Create teachers',
    description: 'Needed before assigning 1-on-1 or group lessons.',
    done: summary.value.totalTeachers > 0,
    action: 'Open',
    path: '/admin/config/people?tab=teachers',
  },
  {
    title: 'Configure course products and prices',
    description: 'Needed before staff can select courses on Registration.',
    done: summary.value.totalCourseProducts > 0,
    action: 'Open',
    path: '/admin/config/course-prices',
  },
  {
    title: 'Review renewal and email settings',
    description: 'Optional, but useful before sending guardian reminders.',
    done: true,
    action: 'Open',
    path: '/admin/config/notifications',
  },
]);

const actions = [
  { label: 'Students & teachers', handler: () => router.push('/admin/config/people') },
  { label: 'Course prices', handler: () => router.push('/admin/config/course-prices') },
  { label: 'Class schedules', handler: () => router.push('/schedule/recurring') },
  { label: 'Payment methods', handler: () => router.push('/admin/config/payment-methods') },
];

async function loadSummary() {
  try {
    const response = await fetchDashboardSummary();
    summary.value = response.data.data;
  } catch (error) {
    console.error('Failed to load dashboard data:', error);
  }
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') {
    loadSummary();
  }
}

onMounted(() => {
  loadSummary();
  document.addEventListener('visibilitychange', onVisibilityChange);
});

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange);
});
</script>

<style scoped>
.hero-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  border-radius: 8px;
  background: #f0faf6;
  border: 1px solid #d4f0e4;
  padding: 20px 24px;
  margin-bottom: 20px;
}

.hero-card__eyebrow {
  display: block;
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: #18b58a;
  margin-bottom: 4px;
}

.hero-card h2 {
  margin: 0 0 6px;
  font-size: 20px;
  color: #1a1a1a;
  font-weight: 600;
}

.hero-card p {
  margin: 0;
  font-size: 13px;
  color: #606266;
  max-width: 480px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 20px;
}

.task-panel {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 20px;
}

.task-card {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 6px 10px;
  align-items: start;
  border: 1px solid #d8e7ff;
  border-radius: 10px;
  background: #f7fbff;
  padding: 16px;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s;
}

.task-card:hover {
  border-color: #409eff;
  box-shadow: 0 8px 18px rgba(64, 158, 255, 0.12);
  transform: translateY(-1px);
}

.task-card span {
  grid-row: span 2;
  display: inline-grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background: #409eff;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.task-card strong {
  color: #1f2937;
  font-size: 15px;
}

.task-card small {
  color: #606266;
  font-size: 12px;
  line-height: 1.45;
}

.stat-card {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.stat-card span {
  font-size: 12px;
  color: #909399;
}

.stat-card strong {
  font-size: 28px;
  font-weight: 700;
  color: #1a1a1a;
}

.stat-card small {
  font-size: 12px;
  color: #909399;
}

/* ── Campus section ────────────────────────── */
.campus-section {
  margin-bottom: 20px;
}

.campus-section__header {
  margin-bottom: 12px;
}

.campus-section__title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.campus-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
}

.campus-card {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
  padding: 16px 18px;
}

.campus-card__header {
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f0f0;
}

.campus-card__name {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.campus-card__stats {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.campus-stat {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.campus-stat span {
  font-size: 12px;
  color: #909399;
}

.campus-stat strong {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.campus-stat--warn {
  color: #f56c6c !important;
}

/* ── Bottom panels ─────────────────────────── */
.content-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.panel-card {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
  padding: 16px 20px;
}

.panel-card__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 14px;
}

.panel-card__header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.panel-card__header span {
  font-size: 12px;
  color: #909399;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.quick-grid__item {
  border: 1px solid #e8edf2;
  border-radius: 6px;
  background: #f8fafc;
  padding: 14px 12px;
  font-size: 13px;
  color: #303133;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
  text-align: left;
}

.quick-grid__item:hover {
  background: #eefaf5;
  border-color: #18b58a;
}

.setup-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.setup-list li {
  display: grid;
  grid-template-columns: 52px 1fr auto;
  gap: 12px;
  align-items: center;
  font-size: 13px;
  color: #303133;
  padding: 10px 12px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  background: #fafbfc;
}

.setup-list li > span {
  color: #b88230;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
}

.setup-list__item--done > span {
  color: #18b58a;
}

.setup-list strong {
  display: block;
  color: #1f2937;
  margin-bottom: 2px;
}

.setup-list small {
  color: #606266;
}

.setup-list button {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  padding: 6px 10px;
  color: #303133;
  cursor: pointer;
}

.setup-list button:hover {
  border-color: #409eff;
  color: #409eff;
}

@media (max-width: 900px) {
  .task-panel {
    grid-template-columns: repeat(2, 1fr);
  }
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .content-grid {
    grid-template-columns: 1fr;
  }

  .stat-card {
    min-width: 0;
    padding: 12px;
  }

  .stat-card strong {
    font-size: clamp(20px, 6vw, 24px);
    overflow-wrap: anywhere;
  }
}
</style>
