<template>
  <div class="page-stack guide-page">
    <section class="table-page-header guide-hero">
      <div>
        <span class="table-page-header__eyebrow">{{ pageCopy.eyebrow }}</span>
        <h2>{{ pageCopy.title }}</h2>
        <p>{{ pageCopy.description }}</p>
      </div>
      <div class="guide-language-switch" aria-label="Guide language">
        <button
          type="button"
          :class="{ 'guide-language-switch__button--active': activeLang === 'zh' }"
          @click="activeLang = 'zh'"
        >
          中文
        </button>
        <button
          type="button"
          :class="{ 'guide-language-switch__button--active': activeLang === 'en' }"
          @click="activeLang = 'en'"
        >
          English
        </button>
      </div>
    </section>

    <section class="guide-common">
      <div class="guide-common__header">
        <h3>{{ pageCopy.quickTitle }}</h3>
        <p>{{ pageCopy.quickDescription }}</p>
      </div>
      <div class="guide-quick-grid">
        <a
          v-for="entry in pageCopy.dailyEntries"
          :key="entry.title"
          :href="`#${entry.target}`"
          class="guide-card guide-card--compact"
        >
          <span>{{ entry.step }}</span>
          <h3>{{ entry.title }}</h3>
          <p>{{ entry.desc }}</p>
          <strong>{{ pageCopy.viewSteps }} →</strong>
        </a>
      </div>
    </section>

    <section class="guide-layout">
      <aside class="guide-nav">
        <strong>{{ pageCopy.navTitle }}</strong>
        <a v-for="section in pageCopy.guideSections" :key="section.id" :href="`#${section.id}`">{{ section.title }}</a>
      </aside>

      <div class="guide-content">
        <section
          v-for="section in pageCopy.guideSections"
          :id="section.id"
          :key="section.id"
          class="guide-section"
        >
          <div class="guide-section__header">
            <span>{{ section.badge }}</span>
            <h3>{{ section.title }}</h3>
            <p>{{ section.summary }}</p>
          </div>

          <div class="guide-steps">
            <article v-for="item in section.steps" :key="item.title" class="guide-step">
              <div class="guide-step__number">{{ item.order }}</div>
              <div>
                <h4>{{ item.title }}</h4>
                <p>{{ item.detail }}</p>
                <small v-if="item.note">{{ item.note }}</small>
              </div>
            </article>
          </div>
        </section>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';

type GuideLang = 'zh' | 'en';

interface GuideStep {
  order: string;
  title: string;
  detail: string;
  note?: string;
}

interface GuideSection {
  id: string;
  badge: string;
  title: string;
  summary: string;
  steps: GuideStep[];
}

interface GuideCopy {
  eyebrow: string;
  title: string;
  description: string;
  quickTitle: string;
  quickDescription: string;
  viewSteps: string;
  navTitle: string;
  dailyEntries: Array<{ step: string; title: string; desc: string; target: string }>;
  guideSections: GuideSection[];
}

const activeLang = ref<GuideLang>('zh');

const guideCopy: Record<GuideLang, GuideCopy> = {
  zh: {
    eyebrow: 'Quick Guide',
    title: '你现在要做什么？',
    description: '选择一个常见任务，照着 3 个步骤操作即可。不需要先了解全部菜单。',
    quickTitle: '常见业务',
    quickDescription: '点击任务直接查看最短操作步骤。',
    viewSteps: '查看步骤',
    navTitle: '全部教学',
    dailyEntries: [
      { step: '1', title: '给学生报名', desc: '选择课程、收费并生成收据。', target: 'registration' },
      { step: '2', title: '查看排课冲突', desc: '查看学生、老师和教室日历。', target: 'calendar' },
      { step: '3', title: '确认课程并扣课', desc: '老师确认，管理员最终扣课。', target: 'confirmation' },
      { step: '4', title: '补款、退款和收据', desc: '按报名订单处理财务记录。', target: 'payments' },
      { step: '5', title: '处理欠款和续费', desc: '集中查看需要跟进的学生。', target: 'follow-up' },
    ],
    guideSections: [
      {
        id: 'registration',
        badge: '最常用',
        title: '给学生报名',
        summary: '从学生列表或 Registration 进入，一张表完成课程、费用、付款和收据。',
        steps: [
          { order: '1', title: '找到学生', detail: '已有学生：在 People > Students 点击 Register。新学生：直接打开 Registration 填写学生和家长信息。' },
          { order: '2', title: '选择课程和费用', detail: '选择校区、课程、年级和课包。接送需求不会自动收费，只有本次明确勾选 Charge 才计费。' },
          { order: '3', title: '保存并交付收据', detail: '检查总额和付款方式后保存。系统会创建报名记录，并可立即查看或打印 Receipt。' },
        ],
      },
      {
        id: 'calendar',
        badge: '排课',
        title: '查看排课和冲突',
        summary: 'Calendar 同时检查学生、老师和教室，红色表示时间重叠。',
        steps: [
          { order: '1', title: '选择查看对象', detail: '打开 Calendar，选择 Student、Teacher 或 Room，再选择具体人员、教室和周次。学生日历会同时显示一对一和班课。' },
          { order: '2', title: '查看红色冲突', detail: '冲突摘要和红色时间格会显示重叠课程数量。点击即可查看相关课程和时间。' },
          { order: '3', title: '调整排课', detail: '根据冲突修改时间、老师或教室。未安排教室的课次可在对应排课页面补录。' },
        ],
      },
      {
        id: 'confirmation',
        badge: '消课',
        title: '确认课程并扣课',
        summary: '老师只确认一次课程结果，管理员审核后才正式扣课。',
        steps: [
          { order: '1', title: '老师点击 Confirm', detail: '老师在 My Lessons 中切换 1-on-1 或 Group class。Confirm 在开课前 14 天出现，课程结束后不会过期。' },
          { order: '2', title: '填写课程结果', detail: '选择 Completed、Formal Leave 或 No Show；正常完成时填写实际开始时间和时长。' },
          { order: '3', title: '管理员审核扣课', detail: '老师提交后显示 Awaiting admin review。管理员点击 Review & deduct 后才扣课并计入老师工资。' },
        ],
      },
      {
        id: 'payments',
        badge: '财务',
        title: '补款、退款和收据',
        summary: '所有操作都针对报名订单，不直接针对学生资料。',
        steps: [
          { order: '1', title: '找到报名订单', detail: '打开 Back office > Enrollment > Enrollments，或在学生 Details > Registration history 找到订单。' },
          { order: '2', title: '选择操作', detail: '未付清时选择 Record payment；需要退款时选择 Refund。全额退款会取消未来课程，已消课订单需人工处理。' },
          { order: '3', title: '查看凭证', detail: '点击 View receipt 查看或打印历史收据。付款和退款会保留独立流水，方便核对。' },
        ],
      },
      {
        id: 'follow-up',
        badge: '跟进',
        title: '处理欠款和续费',
        summary: 'Data Summary 只展示需要关注的记录，不用逐个打开学生。',
        steps: [
          { order: '1', title: '打开 Data Summary', detail: '先看红色欠款，再看临近到期和剩余两节课以内的一对一课包。' },
          { order: '2', title: '确认需要跟进的人', detail: '使用校区、课程和付款状态筛选，核对学生当前余额和剩余课时。' },
          { order: '3', title: '完成后续处理', detail: '补款进入报名订单；续费从学生列表点击 Register 创建新报名。处理完成后提醒会自动更新。' },
        ],
      },
      {
        id: 'admin-setup',
        badge: '管理员',
        title: '首次配置与低频管理',
        summary: '只有管理员需要使用这些入口，日常员工可以跳过。',
        steps: [
          { order: '1', title: '维护人员', detail: 'People 使用 Students / Teachers Tab 管理资料和登录权限，没有单独的 Accounts 页面。' },
          { order: '2', title: '维护课程', detail: 'Courses 依次维护 Products、Prices 和 Schedule。保存排课前系统会检查冲突。' },
          { order: '3', title: '维护机构规则', detail: 'Finance 管理费率和付款方式；System 管理品牌收据、校区和提醒规则。' },
        ],
      },
    ],
  },
  en: {
    eyebrow: 'Quick Guide',
    title: 'What do you need to do?',
    description: 'Choose a common task and follow three short steps. You do not need to learn every menu first.',
    quickTitle: 'Common tasks',
    quickDescription: 'Select a task to jump directly to the shortest instructions.',
    viewSteps: 'View steps',
    navTitle: 'All instructions',
    dailyEntries: [
      { step: '1', title: 'Register a student', desc: 'Choose courses, charge fees, and create a receipt.', target: 'registration' },
      { step: '2', title: 'Check schedule conflicts', desc: 'Review student, teacher, and room calendars.', target: 'calendar' },
      { step: '3', title: 'Confirm and deduct a lesson', desc: 'Teacher confirms; admin completes deduction.', target: 'confirmation' },
      { step: '4', title: 'Payment, refund, or receipt', desc: 'Handle finance by enrollment order.', target: 'payments' },
      { step: '5', title: 'Follow up balances and renewals', desc: 'See students who need attention.', target: 'follow-up' },
    ],
    guideSections: [
      {
        id: 'registration',
        badge: 'Most common',
        title: 'Register a Student',
        summary: 'Start from the student list or Registration. One form covers courses, fees, payment, and receipt.',
        steps: [
          { order: '1', title: 'Find the student', detail: 'Existing student: select Register under People > Students. New student: open Registration and enter student and guardian details.' },
          { order: '2', title: 'Choose courses and fees', detail: 'Select campus, course, grade, and package. Pickup/drop-off needs do not charge automatically; select Charge only when this registration should include the fee.' },
          { order: '3', title: 'Save and provide the receipt', detail: 'Review the total and payment method, then save. The enrollment is created and its Receipt can be viewed or printed immediately.' },
        ],
      },
      {
        id: 'calendar',
        badge: 'Schedule',
        title: 'Check Schedules and Conflicts',
        summary: 'Calendar checks students, teachers, and rooms. Red means lessons overlap.',
        steps: [
          { order: '1', title: 'Choose what to view', detail: 'Open Calendar and choose Student, Teacher, or Room, then select the person, room, and week. Student Calendar includes 1-on-1 and group lessons.' },
          { order: '2', title: 'Review red conflicts', detail: 'The conflict summary and red time cells show how many lessons overlap. Select one to review the related lessons and times.' },
          { order: '3', title: 'Adjust the schedule', detail: 'Resolve the conflict by changing time, teacher, or room. Assign missing rooms from the related scheduling page.' },
        ],
      },
      {
        id: 'confirmation',
        badge: 'Deduction',
        title: 'Confirm and Deduct a Lesson',
        summary: 'The teacher confirms the result once. Admin review completes the deduction.',
        steps: [
          { order: '1', title: 'Teacher selects Confirm', detail: 'In My Lessons, choose 1-on-1 or Group class. Confirm appears 14 days before the lesson and does not expire after it ends.' },
          { order: '2', title: 'Enter the lesson result', detail: 'Choose Completed, Formal Leave, or No Show. For a completed lesson, enter actual start time and duration.' },
          { order: '3', title: 'Admin reviews and deducts', detail: 'Teacher submission shows Awaiting admin review. Hours and payroll are updated only after admin selects Review & deduct.' },
        ],
      },
      {
        id: 'payments',
        badge: 'Finance',
        title: 'Payment, Refund, and Receipt',
        summary: 'These actions belong to an enrollment order, not directly to a student profile.',
        steps: [
          { order: '1', title: 'Find the enrollment order', detail: 'Open Back office > Enrollment > Enrollments, or find the order under student Details > Registration history.' },
          { order: '2', title: 'Choose the action', detail: 'Use Record payment for an unpaid balance or Refund for a return. Full refund cancels future lessons; an order with deducted lessons requires manual handling.' },
          { order: '3', title: 'Review the document', detail: 'Select View receipt to view or print the saved receipt. Payments and refunds remain as separate ledger entries.' },
        ],
      },
      {
        id: 'follow-up',
        badge: 'Follow-up',
        title: 'Handle Balances and Renewals',
        summary: 'Data Summary shows only records that need attention.',
        steps: [
          { order: '1', title: 'Open Data Summary', detail: 'Start with red unpaid balances, then review expiring registrations and 1-on-1 packages with two or fewer lessons left.' },
          { order: '2', title: 'Find who needs follow-up', detail: 'Filter by campus, course, or payment status and verify the current balance and remaining lessons.' },
          { order: '3', title: 'Complete the follow-up', detail: 'Record payment on the enrollment order. For renewal, select Register from the student list to create a new enrollment.' },
        ],
      },
      {
        id: 'admin-setup',
        badge: 'Admin only',
        title: 'Initial Setup and Low-frequency Management',
        summary: 'Only administrators need these entries. Daily users can skip this section.',
        steps: [
          { order: '1', title: 'Maintain people', detail: 'People uses Students and Teachers tabs for profiles and login access. There is no separate Accounts page.' },
          { order: '2', title: 'Maintain courses', detail: 'Under Courses, maintain Products, Prices, and Schedule in that order. Conflicts are checked before schedule changes are saved.' },
          { order: '3', title: 'Maintain school rules', detail: 'Finance manages rates and payment methods. System manages brand and receipts, campuses, and alerts.' },
        ],
      },
    ],
  },
};

const pageCopy = computed(() => guideCopy[activeLang.value]);
</script>

<style scoped>
.guide-page {
  color: var(--text-primary);
}

.guide-hero {
  align-items: center;
}

.guide-language-switch {
  display: inline-flex;
  flex-shrink: 0;
  overflow: hidden;
  border: 1px solid #cbd5e1;
  border-radius: 999px;
  background: #fff;
}

.guide-language-switch button {
  min-width: 76px;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 13px;
  font-weight: 700;
  padding: 7px 12px;
}

.guide-language-switch__button--active {
  background: var(--accent) !important;
  color: #fff !important;
}

.guide-common {
  padding: 16px;
  border: 1px solid #bfdbfe;
  border-radius: 12px;
  background: #eff6ff;
}

.guide-common__header {
  margin-bottom: 12px;
}

.guide-common__header h3 {
  margin: 0 0 4px;
  font-size: 18px;
}

.guide-common__header p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.guide-quick-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.guide-card,
.guide-section {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
}

.guide-card {
  display: flex;
  min-height: 150px;
  flex-direction: column;
  padding: 14px;
  color: var(--text-primary);
  text-decoration: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
}

.guide-card:hover {
  border-color: #60a5fa;
  box-shadow: 0 6px 16px rgb(37 99 235 / 10%);
  transform: translateY(-2px);
}

.guide-card span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: #e6f7f1;
  color: var(--accent-deep);
  font-size: 12px;
  font-weight: 700;
}

.guide-card h3,
.guide-section h3,
.guide-step h4 {
  margin: 8px 0 4px;
}

.guide-card p,
.guide-section p,
.guide-step p,
.guide-step small {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.55;
}

.guide-card > strong {
  margin-top: auto;
  padding-top: 12px;
  color: #2563eb;
  font-size: 12px;
}

.guide-layout {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.guide-nav {
  position: sticky;
  top: 72px;
  display: grid;
  gap: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  padding: 12px;
}

.guide-nav strong {
  margin-bottom: 4px;
  font-size: 13px;
}

.guide-nav a {
  border-radius: 8px;
  padding: 7px 8px;
  color: var(--text-secondary);
  font-size: 13px;
}

.guide-nav a:hover {
  background: #f1f5f9;
  color: var(--accent-deep);
}

.guide-content {
  display: grid;
  gap: 14px;
}

.guide-section {
  scroll-margin-top: 72px;
  padding: 16px;
}

.guide-section__header {
  margin-bottom: 12px;
}

.guide-section__header > span {
  color: var(--accent-deep);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.guide-section__header h3 {
  font-size: 18px;
}

.guide-steps {
  display: grid;
  gap: 10px;
}

.guide-step {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 10px;
  padding: 10px;
  border-radius: 10px;
  background: #f8fafc;
}

.guide-step__number {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
}

.guide-step small {
  display: block;
  margin-top: 5px;
  color: #a16207;
}

@media (max-width: 1080px) {
  .guide-quick-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .guide-layout {
    grid-template-columns: 1fr;
  }

  .guide-nav {
    position: static;
  }
}

@media (max-width: 640px) {
  .guide-quick-grid {
    grid-template-columns: 1fr;
  }
}
</style>
