<template>
  <section class="receipt-preview">
    <div class="receipt-actions">
      <div class="receipt-actions__left">
        <el-switch v-model="showEin" active-text="Show EIN" />
        <span v-if="showEin && !resolvedTaxId" class="receipt-actions__hint">No EIN configured for this campus.</span>
      </div>
      <el-button type="primary" :disabled="!normalized.hasPayload" @click="printReceipt">Print receipt</el-button>
    </div>

    <el-alert
      v-if="!normalized.hasPayload"
      type="warning"
      :closable="false"
      show-icon
      title="Receipt snapshot is not available from the current API response."
      description="Open the receipt from the registration success result to preview and print it until the receipt detail API exposes the saved payload."
    />
    <a
      v-if="!normalized.hasPayload && receiptFileUrl"
      class="receipt-file-link"
      :href="receiptFileUrl"
      target="_blank"
      rel="noopener noreferrer"
    >
      Open saved receipt file
    </a>

    <article v-else class="receipt-print-surface">
      <header class="receipt-header">
        <div class="receipt-logo">
          <img v-if="normalized.logoUrl" :src="normalized.logoUrl" alt="JIN BEST TUTORING" />
          <span v-else>JIN</span>
        </div>
        <div class="receipt-brand">
          <p>Registration Receipt</p>
          <h1>{{ normalized.brandName }}</h1>
          <span>Building confidence, skills, and stronger academic results</span>
        </div>
        <div class="receipt-contact">
          <strong>{{ normalized.campusPhone || '929-701-9872' }}</strong>
          <span>{{ normalized.campusName || 'Campus' }}</span>
          <span>{{ normalized.campusAddress || 'Campus address' }}</span>
          <span v-if="showEin" class="receipt-ein">EIN: {{ resolvedTaxId || 'Not configured' }}</span>
        </div>
      </header>

      <div class="campus-strip">
        <div
          v-for="campus in campusCards"
          :key="campus.key"
          :class="['campus-strip__item', { 'campus-strip__item--active': campus.active }]"
        >
          <strong>{{ campus.name }}</strong>
          <span>{{ campus.address }}</span>
          <small>{{ campus.phone }}</small>
        </div>
      </div>

      <section class="receipt-meta-grid">
        <div>
          <span>Receipt no.</span>
          <strong>{{ normalized.receiptNo || '-' }}</strong>
        </div>
        <div>
          <span>Enrollment no.</span>
          <strong>{{ normalized.enrollmentNo || '-' }}</strong>
        </div>
        <div>
          <span>Sign up date</span>
          <strong>{{ formatDateValue(normalized.signupDate) }}</strong>
        </div>
        <div>
          <span>Handled by</span>
          <strong>{{ normalized.handledByName || '-' }}</strong>
        </div>
      </section>

      <section class="receipt-section">
        <h2>Student Information</h2>
        <div class="receipt-field-grid">
          <div>
            <span>Student</span>
            <strong>{{ normalized.studentName || '-' }}</strong>
          </div>
          <div>
            <span>Student no.</span>
            <strong>{{ normalized.studentNo || '-' }}</strong>
          </div>
          <div>
            <span>Grade</span>
            <strong>{{ normalized.grade || '-' }}</strong>
          </div>
          <div>
            <span>School</span>
            <strong>{{ normalized.school || '-' }}</strong>
          </div>
          <div>
            <span>Guardian phone</span>
            <strong>{{ normalized.guardianPhone || '-' }}</strong>
          </div>
          <div>
            <span>Phone #2</span>
            <strong>{{ normalized.guardianPhone2 || '-' }}</strong>
          </div>
          <div>
            <span>Sign up period</span>
            <strong>{{ periodLabel }}</strong>
          </div>
          <div>
            <span>Payment status</span>
            <strong>{{ formatText(normalized.paymentStatus) || '-' }}</strong>
          </div>
        </div>
      </section>

      <section class="receipt-section pickup-section">
        <h2>Pickup / Dropoff</h2>
        <div class="pickup-print-grid">
          <div>
            <span>Pick up</span>
            <strong>{{ normalized.pickupRequired ? 'Yes' : 'No' }}</strong>
            <small>{{ normalized.pickupRequired ? normalized.pickupAddress || 'Address pending' : 'Not requested' }}</small>
          </div>
          <div>
            <span>Drop off</span>
            <strong>{{ normalized.dropoffRequired ? 'Yes' : 'No' }}</strong>
            <small>{{ normalized.dropoffRequired ? normalized.dropoffAddress || 'Address pending' : 'Not requested' }}</small>
          </div>
        </div>
      </section>

      <section class="receipt-section">
        <h2>Course Details</h2>
        <table class="receipt-table">
          <thead>
            <tr>
              <th>Register Class</th>
              <th>Detail</th>
              <th class="receipt-table__amount">Price</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in normalized.items" :key="item.key">
              <td>
                <strong>{{ item.registerClass || '-' }}</strong>
                <small v-if="item.courseType">{{ formatText(item.courseType) }}</small>
              </td>
              <td>
                <strong>{{ item.detail || '-' }}</strong>
                <small v-if="item.periodLabel || item.timeSlotLabel">{{ [item.periodLabel, item.timeSlotLabel].filter(Boolean).join(' / ') }}</small>
                <small v-if="item.oneOnOneHours">1 on 1 hours: {{ item.oneOnOneHours }}</small>
              </td>
              <td class="receipt-table__amount">{{ formatMoney(item.priceAmount) }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="receipt-money-layout">
        <div class="payment-box">
          <h2>Payment</h2>
          <div v-if="normalized.payments.length" class="payment-list">
            <div v-for="payment in normalized.payments" :key="payment.key">
              <span>{{ formatText(payment.paymentMethod) || 'Payment' }}</span>
              <strong>{{ formatMoney(payment.amount) }}</strong>
            </div>
          </div>
          <p v-else>No payment recorded on this receipt.</p>
          <div class="zelle-box">
            <img v-if="normalized.zelleQrCodeUrl" :src="normalized.zelleQrCodeUrl" alt="Zelle QR code" />
            <div v-else>
              <strong>Zelle</strong>
              <span>QR code area</span>
            </div>
          </div>
        </div>

        <div class="totals-box">
          <h2>Summary</h2>
          <div>
            <span>Tuition subtotal</span>
            <strong>{{ formatMoney(normalized.tuitionSubtotalAmount) }}</strong>
          </div>
          <div>
            <span>Pickup/dropoff fee</span>
            <strong>{{ formatMoney(normalized.pickupFeeAmount) }}</strong>
          </div>
          <div>
            <span>Other fees</span>
            <strong>{{ formatMoney(normalized.extraFeeAmount) }}</strong>
          </div>
          <div v-for="fee in normalized.extraFees" :key="fee.key" class="totals-box__detail">
            <span>{{ fee.label }}</span>
            <strong>{{ formatMoney(fee.amount) }}</strong>
          </div>
          <div>
            <span>Discount</span>
            <strong>-{{ formatMoney(normalized.discountAmount) }}</strong>
          </div>
          <div v-for="discount in normalized.discounts" :key="discount.key" class="totals-box__detail">
            <span>{{ discount.label }}</span>
            <strong>-{{ formatMoney(discount.amount) }}</strong>
          </div>
          <div class="totals-box__total">
            <span>Amount due</span>
            <strong>{{ formatMoney(normalized.amountDue) }}</strong>
          </div>
          <div>
            <span>Paid amount</span>
            <strong>{{ formatMoney(normalized.paidAmount) }}</strong>
          </div>
          <div class="totals-box__total">
            <span>Balance</span>
            <strong>{{ formatMoney(normalized.balanceAmount) }}</strong>
          </div>
        </div>
      </section>

      <section class="receipt-policy">
        <h2>Policies</h2>
        <p v-if="normalized.policyText">{{ normalized.policyText }}</p>
        <template v-else>
          <p><strong>Refund Policy:</strong> A partial refund creates a separate refund record and updates the order balance without changing active courses. Refunding the full currently received amount cancels the enrollment, future lessons, and class eligibility.</p>
          <p><strong>Staff Review:</strong> Course-specific refunds, tuition credit, and mid-course withdrawals requiring changes to course delivery need staff handling.</p>
          <p><strong>Course Suspension Policy:</strong> Mid-course suspension and any remaining balance require manual staff handling and are not recorded by this system. The system does not create tuition credit automatically.</p>
          <p><strong>Material Policy:</strong> Materials are included. Lost materials require a $10 reprint fee per item.</p>
          <p><strong>Discipline Policy:</strong> Students must follow classroom rules. Serious repeated violations may lead to dismissal from class.</p>
        </template>
      </section>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';

import { APP_BRAND_NAME } from '@/config/app-config';
import type { ReceiptRecordItem } from '@/types/domain';

interface Props {
  payload?: Record<string, unknown> | string | null;
  receipt?: ReceiptRecordItem | null;
}

interface ReceiptCourseItem {
  key: string;
  registerClass: string;
  courseType: string;
  detail: string;
  periodLabel: string;
  timeSlotLabel: string;
  oneOnOneHours?: number;
  priceAmount: number;
}

interface MoneyLine {
  key: string;
  label: string;
  amount: number;
}

interface ReceiptPayment {
  key: string;
  paymentMethod: string;
  amount: number;
}

interface NormalizedReceipt {
  hasPayload: boolean;
  receiptNo: string;
  enrollmentNo: string;
  studentNo: string;
  studentName: string;
  grade: string;
  school: string;
  guardianPhone: string;
  guardianPhone2: string;
  campusName: string;
  signupDate: string;
  periodStartDate: string;
  periodEndDate: string;
  pickupRequired: boolean;
  pickupAddress: string;
  dropoffRequired: boolean;
  dropoffAddress: string;
  handledByName: string;
  paymentStatus: string;
  items: ReceiptCourseItem[];
  extraFees: MoneyLine[];
  discounts: MoneyLine[];
  payments: ReceiptPayment[];
  tuitionSubtotalAmount: number;
  pickupFeeAmount: number;
  extraFeeAmount: number;
  discountAmount: number;
  amountDue: number;
  paidAmount: number;
  balanceAmount: number;
  taxId: string;
  campusAddress: string;
  campusPhone: string;
  brandName: string;
  logoUrl: string;
  zelleQrCodeUrl: string;
  policyText: string;
}

const props = defineProps<Props>();

const showEin = ref(false);

const rawPayload = computed(() => parsePayload(props.payload ?? props.receipt?.receiptPayload));

const normalized = computed<NormalizedReceipt>(() => {
  const payload = rawPayload.value;
  const hasPayload = Boolean(payload);
  const items = getArray(payload, 'items').map((item, index) => ({
    key: getString(item, 'orderItemId') || `item-${index}`,
    registerClass: getString(item, 'registerClass'),
    courseType: getString(item, 'courseType'),
    detail: getString(item, 'detail'),
    periodLabel: getString(item, 'periodLabel'),
    timeSlotLabel: getString(item, 'timeSlotLabel'),
    oneOnOneHours: optionalNumber(item, 'oneOnOneHours'),
    priceAmount: getNumber(item, 'priceAmount'),
  }));

  return {
    hasPayload,
    receiptNo: getString(payload, 'receiptNo') || props.receipt?.receiptNo || '',
    enrollmentNo: getString(payload, 'enrollmentNo'),
    studentNo: getString(payload, 'studentNo'),
    studentName: getString(payload, 'studentName') || props.receipt?.studentName || '',
    grade: getString(payload, 'grade'),
    school: getString(payload, 'school'),
    guardianPhone: getString(payload, 'guardianPhone'),
    guardianPhone2: getString(payload, 'guardianPhone2'),
    campusName: getString(payload, 'campusName'),
    signupDate: getString(payload, 'signupDate'),
    periodStartDate: getString(payload, 'periodStartDate'),
    periodEndDate: getString(payload, 'periodEndDate'),
    pickupRequired: getBoolean(payload, 'pickupRequired'),
    pickupAddress: getString(payload, 'pickupAddress'),
    dropoffRequired: getBoolean(payload, 'dropoffRequired'),
    dropoffAddress: getString(payload, 'dropoffAddress'),
    handledByName: getString(payload, 'handledByName'),
    paymentStatus: getString(payload, 'paymentStatus'),
    items,
    extraFees: getMoneyLines(payload, 'extraFees'),
    discounts: getMoneyLines(payload, 'discounts'),
    payments: getPayments(payload),
    tuitionSubtotalAmount: getNumber(payload, 'tuitionSubtotalAmount'),
    pickupFeeAmount: getNumber(payload, 'pickupFeeAmount'),
    extraFeeAmount: getNumber(payload, 'extraFeeAmount'),
    discountAmount: getNumber(payload, 'discountAmount'),
    amountDue: getNumber(payload, 'amountDue'),
    paidAmount: getNumber(payload, 'paidAmount'),
    balanceAmount: getNumber(payload, 'balanceAmount'),
    taxId: getString(payload, 'taxId'),
    campusAddress: getString(payload, 'campusAddress'),
    campusPhone: getString(payload, 'campusPhone'),
    brandName: getString(payload, 'brandName') || APP_BRAND_NAME,
    logoUrl: getString(payload, 'logoUrl'),
    zelleQrCodeUrl: getString(payload, 'zelleQrCodeUrl'),
    policyText: getString(payload, 'policyText'),
  };
});

const periodLabel = computed(() => {
  const start = formatDateValue(normalized.value.periodStartDate);
  const end = formatDateValue(normalized.value.periodEndDate);
  if (start === '-' && end === '-') {
    return '-';
  }
  return `${start} to ${end}`;
});

const resolvedTaxId = computed(() => normalized.value.taxId || resolveCampusTaxId(normalized.value.campusName));
const receiptFileUrl = computed(() => props.receipt?.fileUrl ?? '');

const campusCards = computed(() => {
  const campusName = normalized.value.campusName.toLowerCase();
  return [
    {
      key: 'south',
      name: 'South Campus',
      address: campusName.includes('south') ? normalized.value.campusAddress || 'South campus address' : 'South campus address',
      phone: normalized.value.campusPhone || '929-701-9872',
      active: campusName.includes('south') || (!campusName.includes('north') && !campusName.includes('online')),
    },
    {
      key: 'north',
      name: 'North Campus',
      address: campusName.includes('north') ? normalized.value.campusAddress || 'North campus address' : 'North campus address',
      phone: normalized.value.campusPhone || '929-701-9872',
      active: campusName.includes('north'),
    },
    {
      key: 'online',
      name: 'Online Class',
      address: 'Online class',
      phone: normalized.value.campusPhone || '929-701-9872',
      active: campusName.includes('online'),
    },
  ];
});

function printReceipt() {
  document.body.classList.add('receipt-printing');
  const cleanup = () => document.body.classList.remove('receipt-printing');
  window.addEventListener('afterprint', cleanup, { once: true });
  window.print();
  window.setTimeout(cleanup, 1000);
}

onBeforeUnmount(() => {
  document.body.classList.remove('receipt-printing');
});

function parsePayload(value: unknown): Record<string, unknown> | null {
  if (isRecord(value)) {
    return value;
  }
  if (typeof value !== 'string' || !value.trim()) {
    return null;
  }
  try {
    const parsed = JSON.parse(value);
    return isRecord(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

function getMoneyLines(payload: Record<string, unknown> | null, key: string) {
  return getArray(payload, key).map((line, index) => ({
    key: `${key}-${index}`,
    label: getString(line, 'label') || formatText(key),
    amount: getNumber(line, 'amount'),
  }));
}

function getPayments(payload: Record<string, unknown> | null) {
  return getArray(payload, 'payments').map((payment, index) => ({
    key: getString(payment, 'paymentId') || `payment-${index}`,
    paymentMethod: getString(payment, 'paymentMethod'),
    amount: getNumber(payment, 'amount'),
  }));
}

function getArray(payload: Record<string, unknown> | null, key: string): Record<string, unknown>[] {
  const value = payload?.[key];
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter(isRecord);
}

function getString(payload: Record<string, unknown> | null, key: string) {
  const value = payload?.[key];
  if (typeof value === 'string') {
    return value;
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }
  return '';
}

function getBoolean(payload: Record<string, unknown> | null, key: string) {
  const value = payload?.[key];
  if (typeof value === 'boolean') {
    return value;
  }
  if (typeof value === 'number') {
    return value > 0;
  }
  return value === 'true' || value === '1';
}

function getNumber(payload: Record<string, unknown> | null, key: string) {
  const value = payload?.[key];
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === 'string') {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }
  return 0;
}

function optionalNumber(payload: Record<string, unknown> | null, key: string) {
  const value = getNumber(payload, key);
  return value > 0 ? value : undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function formatMoney(value: number | undefined) {
  return `$${roundMoney(value ?? 0).toFixed(2)}`;
}

function roundMoney(value: number) {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}

function formatDateValue(value: string) {
  if (!value) {
    return '-';
  }
  return value.includes('T') ? value.slice(0, 10) : value;
}

function formatText(value: string) {
  return value.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
}

function resolveCampusTaxId(campusName: string) {
  const normalizedName = campusName.toLowerCase();
  if (normalizedName.includes('north')) {
    return '42-2229088';
  }
  if (normalizedName.includes('south')) {
    return '39-2116039';
  }
  return '';
}
</script>

<style scoped>
.receipt-preview {
  display: grid;
  gap: 14px;
}

.receipt-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.receipt-actions__left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.receipt-actions__hint {
  color: var(--el-color-warning);
  font-size: 12px;
}

.receipt-file-link {
  justify-self: start;
  color: var(--el-color-primary);
  font-weight: 600;
}

.receipt-print-surface {
  width: min(100%, 880px);
  margin: 0 auto;
  padding: 28px;
  background: #ffffff;
  border: 1px solid #d8e0ea;
  border-radius: 18px;
  box-shadow: 0 16px 38px rgba(15, 23, 42, 0.08);
  color: #172033;
}

.receipt-header {
  display: grid;
  grid-template-columns: 92px 1fr minmax(220px, 0.65fr);
  gap: 18px;
  align-items: center;
  padding-bottom: 16px;
  border-bottom: 3px solid #163b66;
}

.receipt-logo {
  display: grid;
  place-items: center;
  width: 86px;
  height: 86px;
  overflow: hidden;
  background: #163b66;
  border-radius: 50%;
  color: #ffffff;
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.receipt-logo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.receipt-brand p {
  margin: 0 0 4px;
  color: #6b7c93;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.receipt-brand h1 {
  margin: 0;
  color: #163b66;
  font-size: 30px;
  line-height: 1.1;
}

.receipt-brand span,
.receipt-contact span,
.campus-strip__item span,
.campus-strip__item small,
.receipt-field-grid span,
.receipt-meta-grid span,
.pickup-print-grid span,
.pickup-print-grid small,
.receipt-table small {
  color: #60708a;
}

.receipt-contact {
  display: grid;
  gap: 4px;
  justify-items: end;
  text-align: right;
  font-size: 12px;
}

.receipt-contact strong {
  color: #163b66;
  font-size: 18px;
}

.receipt-ein {
  padding: 3px 8px;
  background: #eef5ff;
  border-radius: 999px;
  color: #163b66 !important;
  font-weight: 700;
}

.campus-strip {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin: 16px 0;
}

.campus-strip__item {
  display: grid;
  gap: 4px;
  padding: 10px;
  border: 1px solid #d8e0ea;
  border-radius: 10px;
  font-size: 11px;
}

.campus-strip__item--active {
  border-color: #163b66;
  background: #f1f6ff;
}

.campus-strip__item strong {
  color: #163b66;
}

.receipt-meta-grid,
.receipt-field-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.receipt-meta-grid div,
.receipt-field-grid div,
.pickup-print-grid div {
  display: grid;
  gap: 3px;
  min-height: 58px;
  padding: 10px;
  background: #f8fafc;
  border: 1px solid #e3e9f0;
  border-radius: 10px;
}

.receipt-meta-grid strong,
.receipt-field-grid strong,
.pickup-print-grid strong {
  color: #172033;
  font-size: 13px;
}

.receipt-section {
  margin-top: 16px;
}

.receipt-section h2,
.payment-box h2,
.totals-box h2,
.receipt-policy h2 {
  margin: 0 0 10px;
  color: #163b66;
  font-size: 15px;
}

.pickup-print-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.receipt-table {
  width: 100%;
  border-collapse: collapse;
}

.receipt-table th {
  background: #163b66;
  color: #ffffff;
  font-size: 12px;
  text-align: left;
}

.receipt-table th,
.receipt-table td {
  padding: 9px 10px;
  border: 1px solid #b8c5d6;
  vertical-align: top;
}

.receipt-table td {
  font-size: 12px;
}

.receipt-table td strong,
.receipt-table td small {
  display: block;
}

.receipt-table__amount {
  width: 120px;
  text-align: right !important;
  font-weight: 700;
}

.receipt-money-layout {
  display: grid;
  grid-template-columns: minmax(260px, 0.85fr) minmax(320px, 1fr);
  gap: 16px;
  margin-top: 16px;
}

.payment-box,
.totals-box,
.receipt-policy {
  padding: 14px;
  background: #f8fafc;
  border: 1px solid #e3e9f0;
  border-radius: 12px;
}

.payment-list,
.totals-box {
  display: grid;
  gap: 8px;
}

.payment-list div,
.totals-box div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
}

.totals-box__detail {
  padding-left: 12px;
  color: #60708a;
  font-size: 12px !important;
}

.totals-box__total {
  margin-top: 4px;
  padding-top: 8px;
  border-top: 1px solid #d8e0ea;
  color: #163b66;
  font-size: 15px !important;
  font-weight: 800;
}

.zelle-box {
  display: grid;
  place-items: center;
  width: 130px;
  height: 130px;
  margin-top: 16px;
  border: 2px dashed #9fb0c6;
  border-radius: 12px;
  background: #ffffff;
  text-align: center;
}

.zelle-box img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.zelle-box strong,
.zelle-box span {
  display: block;
}

.receipt-policy {
  margin-top: 16px;
  break-inside: avoid;
  page-break-inside: avoid;
}

.receipt-policy p {
  margin: 5px 0;
  color: #46556b;
  font-size: 11px;
  line-height: 1.45;
}

@media (max-width: 900px) {
  .receipt-header,
  .receipt-money-layout,
  .receipt-meta-grid,
  .receipt-field-grid,
  .pickup-print-grid,
  .campus-strip {
    grid-template-columns: 1fr;
  }

  .receipt-contact {
    justify-items: start;
    text-align: left;
  }
}
</style>

<style>
@media print {
  body.receipt-printing {
    background: #ffffff !important;
  }

  body.receipt-printing .sidebar,
  body.receipt-printing .app-header,
  body.receipt-printing .receipt-actions,
  body.receipt-printing .el-overlay,
  body.receipt-printing .el-dialog__header,
  body.receipt-printing .el-dialog__footer {
    display: none !important;
  }

  body.receipt-printing .app-shell,
  body.receipt-printing .app-shell__main,
  body.receipt-printing .app-shell__content {
    display: block !important;
    min-height: 0 !important;
    padding: 0 !important;
    background: #ffffff !important;
  }

  body.receipt-printing .page-stack > *:not(.receipt-print-scope),
  body.receipt-printing .receipt-print-scope > *:not(.receipt-preview),
  body.receipt-printing .receipt-history-print-area > *:not(.receipt-preview) {
    display: none !important;
  }

  body.receipt-printing .receipt-print-scope,
  body.receipt-printing .receipt-preview,
  body.receipt-printing .receipt-print-surface {
    display: block !important;
    width: 100% !important;
    max-width: none !important;
    margin: 0 !important;
    box-shadow: none !important;
  }

  body.receipt-printing .receipt-print-surface {
    padding: 16px !important;
    border: none !important;
    border-radius: 0 !important;
    color-adjust: exact;
    print-color-adjust: exact;
    -webkit-print-color-adjust: exact;
  }

  body.receipt-printing .receipt-policy,
  body.receipt-printing .receipt-money-layout,
  body.receipt-printing .receipt-table tr {
    break-inside: avoid;
    page-break-inside: avoid;
  }
}
</style>
