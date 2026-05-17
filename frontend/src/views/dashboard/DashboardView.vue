<template>
  <div class="page-stack">
    <section class="hero-card">
      <div>
        <span class="hero-card__eyebrow">Dashboard</span>
        <h2>Operations overview</h2>
        <p>System-wide metrics and per-campus statistics.</p>
      </div>
    </section>

    <!-- 系统级统计 -->
    <section class="stat-grid">
      <article v-for="card in cards" :key="card.label" class="stat-card">
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
        <small>{{ card.hint }}</small>
      </article>
    </section>

    <!-- 各校区明细 -->
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
              <span>Classrooms</span>
              <strong>{{ campus.classrooms }}</strong>
            </div>
            <div class="campus-stat">
              <span>Today's Lessons</span>
              <strong>{{ campus.todayLessons }}</strong>
            </div>
            <div class="campus-stat">
              <span>Teachers</span>
              <strong>{{ campus.teachers }}</strong>
            </div>
            <div class="campus-stat">
              <span>Students</span>
              <strong>{{ campus.students }}</strong>
            </div>
            <div class="campus-stat">
              <span>Low Balance</span>
              <strong :class="{ 'campus-stat--warn': campus.lowBalancePackages > 0 }">
                {{ campus.lowBalancePackages }}
              </strong>
            </div>
          </div>
        </article>
      </div>
    </section>

    <section class="content-grid">
      <article class="panel-card">
        <div class="panel-card__header">
          <h3>Quick actions</h3>
          <span>High-frequency tasks</span>
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

      <article class="panel-card">
        <div class="panel-card__header">
          <h3>Summary</h3>
          <span>Real-time data from the system</span>
        </div>
        <ul class="todo-list">
          <li>{{ summary.totalStudents }} active students enrolled in courses.</li>
          <li>{{ summary.totalTeachers }} active teachers in the system.</li>
          <li>{{ summary.todayLessonsCount }} lessons scheduled for today.</li>
          <li>{{ summary.lowBalancePackages }} course package(s) are running low on units.</li>
        </ul>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
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

const actions = [
  { label: 'Create enrollment', handler: () => router.push('/enrollment/orders') },
  { label: 'Build schedule', handler: () => router.push('/schedule/rules') },
  { label: 'Add student', handler: () => router.push('/base-data/students') },
  { label: 'Add instructor', handler: () => router.push('/base-data/teachers') },
  { label: 'Add classroom', handler: () => router.push('/base-data/classrooms') },
  { label: 'Confirm lesson', handler: () => router.push('/schedule/adjustments') },
];

onMounted(async () => {
  try {
    const response = await fetchDashboardSummary();
    summary.value = response.data.data;
  } catch (error) {
    console.error('Failed to load dashboard data:', error);
  }
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

.todo-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.todo-list li {
  font-size: 13px;
  color: #303133;
  padding: 10px 12px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  background: #fafbfc;
}

@media (max-width: 900px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .content-grid {
    grid-template-columns: 1fr;
  }
}
</style>
