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
      { step: '1', title: '创建课程产品', desc: '先建 Product，再建价格和班课设置。', target: 'product-setup' },
      { step: '2', title: '给学生报名', desc: '选择课程、收费并生成收据。', target: 'registration' },
      { step: '3', title: '查看收据', desc: '从订单或 Receipt Records 打开历史收据。', target: 'receipts' },
      { step: '4', title: '补款或退款', desc: '继续支付 partial paid 订单或处理退款。', target: 'payments' },
      { step: '5', title: '处理欠款和续费', desc: '集中查看需要跟进的学生。', target: 'follow-up' },
    ],
    guideSections: [
      {
        id: 'product-setup',
        badge: '管理员',
        title: '创建课程 Product',
        summary: 'Product 是课程大类，Pricing Matrix 才是报名时可选择的具体价格和班级。',
        steps: [
          { order: '1', title: '创建 Product', detail: '进入 Course Products，选择 1-on-1 或 Group Class，填写课程名称、科目和状态。Product 只负责定义卖什么，不在这里放年级价格细节。' },
          { order: '2', title: '配置 Pricing Matrix', detail: '进入 Pricing Matrix，选择刚创建的 Product。1-on-1 行维护年级、课包小时和价格；Group Class 行维护年级班、月价格、老师、教室和每周排课。' },
          { order: '3', title: '检查是否可报名', detail: '确认价格行是 Active。班课需要有月价格和有效月份；1-on-1 需要有 package hours 和 price。保存后 Registration 的课程下拉框才会出现。' },
        ],
      },
      {
        id: 'registration',
        badge: '最常用',
        title: '给学生报名',
        summary: '从学生列表或 Registration 进入，一张表完成课程、费用、付款和收据。',
        steps: [
          { order: '1', title: '找到学生', detail: '已有学生：在 People > Students 点击 Register。新学生：直接打开 Registration 填写学生和家长信息。' },
          { order: '2', title: '选择课程和费用', detail: '课程下拉框会区分 1-on-1 和 Group Class。班课报名只选择购买月份数，系统按班级价格自动计算学费。接送需求不会自动收费，只有本次明确勾选 Charge 才计费。' },
          { order: '3', title: '保存并交付收据', detail: '检查总额和付款方式后保存。系统会创建报名记录，并可立即查看或打印 Receipt。' },
        ],
      },
      {
        id: 'receipts',
        badge: '收据',
        title: '查看和打印收据',
        summary: '收据是保存下来的历史快照，后续改学生资料或价格不会改变旧收据。',
        steps: [
          { order: '1', title: '从订单打开', detail: '进入 Enrollment History，找到报名订单，点击 Actions 里的 View receipt。适合刚报名后马上给家长查看。' },
          { order: '2', title: '从收据列表查找', detail: '进入 Receipt Records，可按学生、订单号或收据号搜索。这里适合查历史收据、补打或核对付款凭证。' },
          { order: '3', title: '打印或保存', detail: '打开收据后使用页面里的打印/下载按钮。Registration 表单打印时会保留已填写内容和选项说明。' },
        ],
      },
      {
        id: 'calendar',
        badge: '排课',
        title: '查看排课和冲突',
        summary: 'Calendar 同时检查学生、老师和教室，红色表示时间重叠。',
        steps: [
          { order: '1', title: '选择查看对象', detail: '打开 Calendar，选择 Student、Teacher 或 Room，再选择具体人员、教室和周次。学生日历会同时显示一对一和班课。' },
          { order: '2', title: '创建每周排课', detail: 'Create Weekly Schedule 中先选择时间段；如果课程需要指定老师，在每个 time slot 上选择老师。Default teacher 已不再使用。' },
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
        title: 'Partial paid 订单继续付款',
        summary: '所有操作都针对报名订单，不直接针对学生资料。',
        steps: [
          { order: '1', title: '找到未付清订单', detail: '进入 Enrollment History，用 Payment 表头筛选 Unpaid 或 Partial，也可以从 Data Summary 的红色 Balance 反查订单。' },
          { order: '2', title: 'Record payment', detail: '在订单 Actions 中选择 Pay / Record payment，填写本次支付方式、金额、日期、经手人和备注。可以一次录入多个 payment split。' },
          { order: '3', title: '确认余额和凭证', detail: '保存后订单的 Paid、Balance 和 Payment status 会更新。继续点击 View receipt 或付款记录中的 statement 查看本次付款凭证。' },
        ],
      },
      {
        id: 'refunds',
        badge: '退款',
        title: '处理退款',
        summary: '退款也在 Enrollment History 按订单处理，系统会保留独立退款流水。',
        steps: [
          { order: '1', title: '确认订单状态', detail: '找到订单后先查看已收金额、课程是否已开始消课、未来课程是否还需要取消。只有已收款订单才需要退款。' },
          { order: '2', title: '提交 Refund', detail: '在 Actions 中选择 Refund，填写退款金额、退款方式、经手人和原因。部分退款只更新财务余额；全额退还当前已收金额会取消报名资格和未来课程。' },
          { order: '3', title: '核对结果', detail: '退款后查看 Payment records 和 Receipt/statement，确认 received、balance、order status 和学生后续排课状态符合预期。已消课的复杂退款需要人工复核。' },
        ],
      },
      {
        id: 'follow-up',
        badge: '跟进',
        title: '处理欠款和续费',
        summary: 'Data Summary 只展示需要关注的记录，不用逐个打开学生。',
        steps: [
          { order: '1', title: '打开 Data Summary', detail: '先看红色欠款，再看临近到期和剩余两节课以内的一对一课包。Tuition 表示课程明细原价，不含接送费，也未扣除 discount。' },
          { order: '2', title: '使用表头筛选', detail: '点击每个表头筛选。文字列可搜索，日期列可选区间，金额列可设置 min/max，并支持 Asc 或 Desc 排序。' },
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
          { order: '2', title: '维护课程', detail: 'Courses 依次维护 Products 和 Pricing Matrix。1-on-1 行维护课包小时和价格；班课行维护年级班、月价格、老师、教室、每周排课和管理员可见的 sessions 信息。' },
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
      { step: '1', title: 'Create a product', desc: 'Create Product first, then pricing and class setup.', target: 'product-setup' },
      { step: '2', title: 'Register a student', desc: 'Choose courses, charge fees, and create a receipt.', target: 'registration' },
      { step: '3', title: 'View receipts', desc: 'Open saved receipts from orders or Receipt Records.', target: 'receipts' },
      { step: '4', title: 'Payment or refund', desc: 'Finish partial paid orders or process refunds.', target: 'payments' },
      { step: '5', title: 'Follow up balances and renewals', desc: 'See students who need attention.', target: 'follow-up' },
    ],
    guideSections: [
      {
        id: 'product-setup',
        badge: 'Admin',
        title: 'Create a Course Product',
        summary: 'Product is the course family. Pricing Matrix creates the registration options staff can select.',
        steps: [
          { order: '1', title: 'Create the Product', detail: 'Open Course Products, choose 1-on-1 or Group Class, then enter the course name, subject, and status. Keep grade-specific pricing out of Product.' },
          { order: '2', title: 'Configure Pricing Matrix', detail: 'Open Pricing Matrix and select the Product. 1-on-1 rows define grade, package hours, and price. Group Class rows define grade class, monthly price, teacher, room, and weekly schedule.' },
          { order: '3', title: 'Check registration readiness', detail: 'Make sure the price row is Active. Group classes need monthly price and valid months; 1-on-1 rows need package hours and price. Active rows appear in the Registration course dropdown.' },
        ],
      },
      {
        id: 'registration',
        badge: 'Most common',
        title: 'Register a Student',
        summary: 'Start from the student list or Registration. One form covers courses, fees, payment, and receipt.',
        steps: [
          { order: '1', title: 'Find the student', detail: 'Existing student: select Register under People > Students. New student: open Registration and enter student and guardian details.' },
          { order: '2', title: 'Choose courses and fees', detail: 'The course dropdown separates 1-on-1 and Group Class options. For group classes, select the number of months and the system calculates tuition from the class price. Pickup/drop-off needs do not charge automatically; select Charge only when this registration should include the fee.' },
          { order: '3', title: 'Save and provide the receipt', detail: 'Review the total and payment method, then save. The enrollment is created and its Receipt can be viewed or printed immediately.' },
        ],
      },
      {
        id: 'receipts',
        badge: 'Receipt',
        title: 'View and Print Receipts',
        summary: 'Receipts are saved snapshots. Later profile or price changes do not rewrite old receipts.',
        steps: [
          { order: '1', title: 'Open from the order', detail: 'Go to Enrollment History, find the enrollment order, and choose View receipt from Actions. This is the fastest path right after registration.' },
          { order: '2', title: 'Search Receipt Records', detail: 'Open Receipt Records to search by student, order number, or receipt number. Use this for historical receipts, reprints, and payment proof checks.' },
          { order: '3', title: 'Print or save', detail: 'Use the print/download button in the receipt view. Printed registration forms keep filled values and option context visible.' },
        ],
      },
      {
        id: 'calendar',
        badge: 'Schedule',
        title: 'Check Schedules and Conflicts',
        summary: 'Calendar checks students, teachers, and rooms. Red means lessons overlap.',
        steps: [
          { order: '1', title: 'Choose what to view', detail: 'Open Calendar and choose Student, Teacher, or Room, then select the person, room, and week. Student Calendar includes 1-on-1 and group lessons.' },
          { order: '2', title: 'Create weekly schedules', detail: 'In Create Weekly Schedule, choose time slots first. Select a teacher on each time slot when that lesson has an assigned teacher. Default teacher is no longer used.' },
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
        title: 'Finish a Partial Paid Order',
        summary: 'These actions belong to an enrollment order, not directly to a student profile.',
        steps: [
          { order: '1', title: 'Find the unpaid order', detail: 'Open Enrollment History and filter Payment by Unpaid or Partial. You can also start from the red Balance column in Data Summary and trace back to the order.' },
          { order: '2', title: 'Record payment', detail: 'Choose Pay / Record payment from order Actions. Enter payment method, amount, date, handler, and notes. Multiple payment splits can be recorded in one submission.' },
          { order: '3', title: 'Confirm balance and proof', detail: 'After saving, Paid, Balance, and Payment status update on the order. Use View receipt or the payment statement to review the payment proof.' },
        ],
      },
      {
        id: 'refunds',
        badge: 'Refund',
        title: 'Process a Refund',
        summary: 'Refunds are handled from Enrollment History and kept as separate payment records.',
        steps: [
          { order: '1', title: 'Review the order', detail: 'Check received amount, whether lessons have already been deducted, and whether future lessons should be cancelled. Only orders with received payment need refund action.' },
          { order: '2', title: 'Submit Refund', detail: 'Choose Refund from Actions, then enter refund amount, method, handler, and reason. A partial refund updates finance only; refunding the full currently received amount cancels enrollment eligibility and future lessons.' },
          { order: '3', title: 'Verify the result', detail: 'Review Payment records and Receipt/statement. Confirm received, balance, order status, and the student schedule are correct. Complex refunds after deducted lessons need manual review.' },
        ],
      },
      {
        id: 'follow-up',
        badge: 'Follow-up',
        title: 'Handle Balances and Renewals',
        summary: 'Data Summary shows only records that need attention.',
        steps: [
          { order: '1', title: 'Open Data Summary', detail: 'Start with red unpaid balances, then review expiring registrations and 1-on-1 packages with two or fewer lessons left. Tuition means the course line price before discounts and before pickup/drop-off fees.' },
          { order: '2', title: 'Use header filters', detail: 'Select each table header to filter. Text columns search, date columns use ranges, and amount columns support min/max plus Asc or Desc sorting.' },
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
          { order: '2', title: 'Maintain courses', detail: 'Under Courses, maintain Products and Pricing Matrix. 1-on-1 rows define package hours and price; group rows define grade class, monthly price, teacher, room, weekly schedule, and admin-visible sessions.' },
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
