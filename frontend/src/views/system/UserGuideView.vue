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

    <section class="guide-quick-grid">
      <article v-for="entry in pageCopy.dailyEntries" :key="entry.title" class="guide-card guide-card--compact">
        <span>{{ entry.step }}</span>
        <h3>{{ entry.title }}</h3>
        <p>{{ entry.desc }}</p>
      </article>
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
  navTitle: string;
  dailyEntries: Array<{ step: string; title: string; desc: string }>;
  guideSections: GuideSection[];
}

const activeLang = ref<GuideLang>('zh');

const guideCopy: Record<GuideLang, GuideCopy> = {
  zh: {
    eyebrow: 'Quick start',
    title: '开箱即用指南',
    description: '先由管理员完成一次准备；之后员工只需使用左侧四个入口。不要先学习全部功能，按当前要做的事进入对应页面即可。',
    navTitle: '快速定位',
    dailyEntries: [
      { step: '1', title: '报名', desc: '填一张表，系统自动算金额、记录付款、打印收据。' },
      { step: '2', title: '一对一课程', desc: '按老师和学生找课程，确认实际上课时长。' },
      { step: '3', title: '教室管理', desc: '查看教室占用，为未安排的课程补教室。' },
      { step: '4', title: '数据整理', desc: '每天查看欠款、到期和剩余课时提醒。' },
    ],
    guideSections: [
      {
        id: 'first-start',
        badge: 'First time only',
        title: '管理员首次准备',
        summary: '只需做一次。完成后前台报名会自动带出课程矩阵、价格和接送费，员工不需要理解后台配置。',
        steps: [
          { order: '1', title: '检查校区和接送费', detail: '打开 Back office > School settings > Campuses & service fees，确认南校、北校、线上课，以及默认接送费。' },
          { order: '2', title: '检查课程价格', detail: '打开 Back office > Course setup > Course prices，找到 PPT 预置模板，补齐真实价格。', note: 'PPT 未提供正式价格，预置模板为 $0.00；报名时选择 $0 行必须手动录入真实价格和原因。冲刺班的年级、周期和时段需要客户补充后再创建。' },
          { order: '3', title: '班课可先建、后排课', detail: '班课价格行保存后会生成对应年级班。课程、年级和价格可以先准备好，不必当场确定老师和教室。' },
          { order: '4', title: '需要时再点 Schedule', detail: '从 Courses 的 Schedules 或课程价格行的 Schedule 进入班课排课，选择老师、教室和每周时间后保存。系统会检查冲突。' },
        ],
      },
      {
        id: 'daily-work',
        badge: 'Every day',
        title: '员工每天只做这四件事',
        summary: '左侧四个入口覆盖日常工作。除非需要改规则，否则不进入 Back office。',
        steps: [
          { order: '1', title: '有新生：打开报名', detail: '填写学生和家长信息，选择校区、课程、年级和周期。已维护的价格、接送费和总额会自动带出；$0 模板必须手动录入真实价格。' },
          { order: '2', title: '有一对一课：打开一对一课程', detail: '选择老师 → 学生 → 学生课程，按表格确认本次实际上课时长。' },
          { order: '3', title: '有教室问题：打开教室管理', detail: '先看教室周日历；只有未安排教室的课程才打开待分配列表补录。' },
          { order: '4', title: '每天收尾：打开数据整理', detail: '优先处理红色负余额、临近到期和一对一剩余两节课以内的记录。' },
        ],
      },
      {
        id: 'registration',
        badge: 'Registration',
        title: '报名：照着表格填写即可',
        summary: '不需要先建学生，也不需要手工算钱。完成一张表即可保存报名和打印收据。',
        steps: [
          { order: '1', title: '先填学生和校区', detail: '填写姓名、家长电话、年级、学校和报名日期；如有接送，勾选后补地址。系统会提示是否复用已有学生。' },
          { order: '2', title: '再选课程', detail: '每行依次选课程、年级、时长或时段。需要多个课程时点 Add course row。', note: '班课未排课时不能报名，请通知管理员完成班课排课。' },
          { order: '3', title: '确认费用和付款', detail: '系统自动计算应付与余额。可添加多笔付款并记录日期和方式；特殊价格必须写明原因。' },
          { order: '4', title: '保存并打印', detail: '保存后可直接打印收据。普通收据默认不显示 EIN；需要时点击 Show EIN。后续补款、退款和历史收据由管理员在 Back office > Records 处理。' },
        ],
      },
      {
        id: 'deduction',
        badge: 'Deduction',
        title: '一对一：按老师文件夹操作',
        summary: '页面只显示完成当前确认所需的信息：学生、课程、课次、时长和剩余课时。',
        steps: [
          { order: '1', title: '找到学生课程', detail: '可按学生、老师、课程或年级筛选；点击左侧学生课程后，右侧会显示课次表和剩余课时。' },
          { order: '2', title: '先安排、再确认', detail: '如课程没有老师或教室，先点 Set teacher & room；有排课后再确认实际上课。' },
          { order: '3', title: '老师确认后等管理员确认', detail: '老师确认只提交记录；管理员终确认后才真正扣课并进入工资统计。实际时长支持 1.5 小时等 0.5 小时档位。' },
          { order: '4', title: '看到提醒就续费', detail: '剩余两节课以内会显示续费提醒。需要固定排课时，管理员在 Back office > Course setup > Weekly schedules 设置。' },
        ],
      },
      {
        id: 'classrooms-review',
        badge: 'Review',
        title: '教室与数据整理',
        summary: '教室可以后补；数据整理像 Excel 一样用于查异常，不需要逐条查看所有学生。',
        steps: [
          { order: '1', title: '教室先看日历', detail: '打开 Classrooms，选教室和周次查看占用。报名时不填教室是正常流程。' },
          { order: '2', title: '只处理待分配', detail: '有遗漏时打开 Room assignment queue，为课程或课次补教室；保存时系统阻止时间冲突。' },
          { order: '3', title: '数据整理优先看红色行', detail: '负数余额表示家长尚未付清；缴清后会自动取消红色。可筛选校区、课程和付款状态。' },
          { order: '4', title: '续费提醒集中处理', detail: '切到 Renewal follow-up，查看课程到期或低课时学生；确认后再发家长提醒。' },
        ],
      },
      {
        id: 'back-office',
        badge: 'Back office',
        title: '什么时候才进入 Back office',
        summary: '只在改规则、查历史、结算工资或管理账号时使用。日常报名和排课不需要进入这里。',
        steps: [
          { order: '1', title: '改课程或价格', detail: 'Course setup > Courses / Course prices。已有学生使用过的价格不要直接改，新增一条价格或停用旧价格。' },
          { order: '2', title: '查历史、补款或退款', detail: 'Records > Enrollments / Receipts。退款会保留独立流水，便于之后核对。' },
          { order: '3', title: '管理账号和工资', detail: 'People & accounts > Accounts 管理师生登录；Payments & payroll > Pay rates / Teacher Payments 配费率和结算工资。' },
          { order: '4', title: '改机构规则', detail: 'School settings 用于校区接送费、收据 EIN、提醒规则和系统说明。无明确需求时不要修改。' },
        ],
      },
    ],
  },
  en: {
    eyebrow: 'User Guide',
    title: 'System User Guide',
    description: 'Set up the school once, then staff only use the four daily entries. Open Back office only to change rules or review history.',
    navTitle: 'Quick links',
    dailyEntries: [
      { step: '1', title: 'Registration', desc: 'Fill one form. The system calculates fees, records payment, and prints the receipt.' },
      { step: '2', title: '1-on-1 Deduction', desc: 'Review lessons by teacher, student, and course, then complete teacher and admin confirmation.' },
      { step: '3', title: 'Classrooms', desc: 'Assign missing rooms and review weekly room occupancy.' },
      { step: '4', title: 'Data Summary', desc: 'Review unpaid balances, renewals, and low-hour alerts.' },
    ],
    guideSections: [
      {
        id: 'first-start',
        badge: 'First time only',
        title: 'Administrator First-time Setup',
        summary: 'Complete this once. Registration will then offer the approved course matrix, prices, and service fees.',
        steps: [
          { order: '1', title: 'Check campuses and service fees', detail: 'Open Back office > School settings > Campuses & service fees to confirm campuses and default pickup/drop-off fees.' },
          { order: '2', title: 'Check course prices', detail: 'Open Back office > Course setup > Course prices. Complete the real prices for the PPT templates.', note: 'The PPT did not provide approved prices, so seeded templates use $0.00. Registration requires a manual price and reason when staff select a $0 row. Sprint Class still needs its grade, period, and time matrix from the customer.' },
          { order: '3', title: 'Create grade classes first', detail: 'A group price row creates its grade class. You can save it before teachers, rooms, and weekly times are finalized.' },
          { order: '4', title: 'Schedule when ready', detail: 'Use Schedules on Courses or Schedule on a price row to set teacher, room, and weekly times. Conflicts are checked before lessons are created.' },
        ],
      },
      {
        id: 'daily-work',
        badge: 'Every day',
        title: 'The Four Daily Entries',
        summary: 'Staff use only the four left-side entries. Back office is for administrators and lower-frequency work.',
        steps: [
          { order: '1', title: 'New student: Registration', detail: 'Enter student details, choose campus, course, grade, and duration. Maintained prices and service fees are filled automatically; $0 templates require a manual real price.' },
          { order: '2', title: '1-on-1 lesson: 1-on-1 Lessons', detail: 'Choose teacher, student, and course, then confirm actual lesson time.' },
          { order: '3', title: 'Room issue: Classrooms', detail: 'Review room occupancy first. Open the assignment queue only for courses still missing a room.' },
          { order: '4', title: 'Daily close: Data Summary', detail: 'Handle red negative balances, expiring registrations, and students with two or fewer 1-on-1 lessons left.' },
        ],
      },
      {
        id: 'registration',
        badge: 'Registration',
        title: 'Registration, Payments, and Receipts',
        summary: 'Follow the paper-form order. You do not need to create a student or calculate totals first.',
        steps: [
          { order: '1', title: 'Enter student and campus', detail: 'Enter name, guardian phone, grade, school, and registration date. Select pickup/drop-off only when needed.' },
          { order: '2', title: 'Choose courses', detail: 'For each row choose course, grade, and duration or time. Use Add course row for multiple courses.', note: 'A group class must be scheduled before it can be registered.' },
          { order: '3', title: 'Review fees and payment', detail: 'The system calculates totals. Add multiple payment rows for installments; a manual price must include a reason.' },
          { order: '4', title: 'Save and print', detail: 'Save, then print the receipt. Later payments, refunds, and receipt history are in Back office > Records.' },
        ],
      },
      {
        id: 'deduction',
        badge: 'Deduction',
        title: 'Deduction and Teacher Confirmation',
        summary: 'The page shows only the student, course, lessons, hours, and next action needed for confirmation.',
        steps: [
          { order: '1', title: 'Find the student course', detail: 'Filter by student, teacher, course, or grade. Select a student course to see lessons and remaining hours.' },
          { order: '2', title: 'Arrange before confirming', detail: 'Use Set teacher & room when resources are missing. Confirm only after the lesson is arranged.' },
          { order: '3', title: 'Teacher then administrator', detail: 'Teacher confirmation submits the record. Administrator final confirmation deducts hours and makes it eligible for payroll.' },
          { order: '4', title: 'Renew when alerted', detail: 'Two lessons or fewer shows a renewal alert. Use Weekly schedules in Back office for fixed 1-on-1 times.' },
        ],
      },
      {
        id: 'classrooms-review',
        badge: 'Review',
        title: 'Classrooms and Data Review',
        summary: 'Rooms can be assigned later. Data Summary is a spreadsheet-style exception list, not a page to review student by student.',
        steps: [
          { order: '1', title: 'Review the room calendar', detail: 'Choose a room and week to see occupancy. Leaving room assignment until after registration is normal.' },
          { order: '2', title: 'Only resolve pending assignments', detail: 'Use Room assignment queue to assign missing rooms. Saving prevents time conflicts.' },
          { order: '3', title: 'Prioritize red rows', detail: 'A negative balance means payment is still owed. The row clears automatically when paid.' },
          { order: '4', title: 'Process renewals together', detail: 'Open Renewal follow-up for expiring registrations and low-hour students, then review messages before sending.' },
        ],
      },
      {
        id: 'back-office',
        badge: 'Back office',
        title: 'When to Open Back Office',
        summary: 'Use it only to change rules, review history, settle payroll, or manage accounts.',
        steps: [
          { order: '1', title: 'Change courses or prices', detail: 'Use Course setup > Courses / Course prices. Do not overwrite a price already used by students; add a new row or deactivate the old one.' },
          { order: '2', title: 'Review records, payments, and refunds', detail: 'Use Records > Enrollments / Receipts. Refunds keep their own payment history for later review.' },
          { order: '3', title: 'Manage accounts and payroll', detail: 'Use People & accounts > Accounts and Payments & payroll > Pay rates / Teacher Payments.' },
          { order: '4', title: 'Change school rules', detail: 'Use School settings for service fees, receipt EIN, alerts, and system guidance. Do not change a rule without a confirmed business need.' },
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

.guide-quick-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.guide-card,
.guide-section {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
}

.guide-card {
  padding: 14px;
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
</style>
