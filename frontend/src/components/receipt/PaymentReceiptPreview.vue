<template>
  <section class="payment-receipt-preview">
    <div class="payment-receipt-actions">
      <el-button type="primary" @click="printReceipt">Print receipt</el-button>
    </div>

    <article class="payment-receipt-print-surface">
      <header class="payment-receipt-header">
        <div>
          <span>JIN BEST TUTORING</span>
          <h2>{{ documentTitle }}</h2>
        </div>
        <strong>{{ receipt.receiptNo }}</strong>
      </header>

      <div class="payment-receipt-meta">
        <div><span>Student</span><strong>{{ payload.studentName || receipt.studentName || '—' }}</strong></div>
        <div><span>Enrollment</span><strong>{{ payload.enrollmentNo || '—' }}</strong></div>
        <div><span>Issued at</span><strong>{{ formatDateTime(payload.issuedAt || receipt.issuedAt) }}</strong></div>
        <div><span>Status after {{ transactionNoun }}</span><strong>{{ formatStatus(payload.paymentStatus) }}</strong></div>
      </div>

      <table class="payment-receipt-table">
        <thead>
          <tr>
            <th>{{ transactionLabel }} no.</th>
            <th>Method</th>
            <th>Date</th>
            <th>Handled by</th>
            <th>Notes</th>
            <th>Amount</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="payment in payload.payments" :key="payment.paymentNo">
            <td>{{ payment.paymentNo || '—' }}</td>
            <td>{{ formatStatus(payment.paymentMethod) }}</td>
            <td>{{ formatDateTime(payment.paidAt) }}</td>
            <td>{{ payment.handledByName || '—' }}</td>
            <td>{{ payment.remark || '—' }}</td>
            <td>{{ formatTransactionMoney(payment.amount) }}</td>
          </tr>
        </tbody>
      </table>

      <div class="payment-receipt-summary">
        <div><span>This {{ transactionNoun }}</span><strong>{{ formatTransactionMoney(payload.paymentAmount) }}</strong></div>
        <div><span>Total received</span><strong>{{ formatMoney(payload.receivedAmount) }}</strong></div>
        <div class="payment-receipt-summary__balance">
          <span>Balance after {{ transactionNoun }}</span>
          <strong>{{ formatMoney(payload.balanceAmount) }}</strong>
        </div>
      </div>

      <footer>
        This receipt records this {{ transactionNoun }} only. The original registration receipt remains unchanged.
      </footer>
    </article>
  </section>
</template>

<script setup lang="ts">
import dayjs from 'dayjs';
import { computed, onBeforeUnmount } from 'vue';

import type { ReceiptRecordItem } from '@/types/domain';

interface PaymentReceiptLine {
  paymentNo: string;
  paymentMethod: string;
  amount: number;
  paidAt: string;
  handledByName: string;
  remark: string;
}

const props = defineProps<{ receipt: ReceiptRecordItem }>();

const payload = computed(() => {
  const value = parsePayload(props.receipt.receiptPayload);
  return {
    enrollmentNo: stringValue(value.enrollmentNo),
    studentName: stringValue(value.studentName),
    issuedAt: stringValue(value.issuedAt),
    paymentStatus: stringValue(value.paymentStatus),
    paymentAmount: numberValue(value.paymentAmount),
    receivedAmount: numberValue(value.receivedAmount),
    balanceAmount: numberValue(value.balanceAmount),
    payments: Array.isArray(value.payments)
      ? value.payments.filter(isRecord).map(payment => ({
        paymentNo: stringValue(payment.paymentNo),
        paymentMethod: stringValue(payment.paymentMethod),
        amount: numberValue(payment.amount),
        paidAt: stringValue(payment.paidAt),
        handledByName: stringValue(payment.handledByName),
        remark: stringValue(payment.remark),
      } satisfies PaymentReceiptLine))
      : [],
  };
});
const isRefund = computed(() =>
  stringValue(parsePayload(props.receipt.receiptPayload).receiptType) === 'REFUND'
  || props.receipt.templateCode === 'REFUND_RECEIPT'
);
const transactionNoun = computed(() => isRefund.value ? 'refund' : 'payment');
const transactionLabel = computed(() => isRefund.value ? 'Refund' : 'Payment');
const documentTitle = computed(() => `${transactionLabel.value} Receipt`);

function parsePayload(value: unknown): Record<string, unknown> {
  if (isRecord(value)) return value;
  if (typeof value !== 'string' || !value.trim()) return {};
  try {
    const parsed = JSON.parse(value);
    return isRecord(parsed) ? parsed : {};
  } catch {
    return {};
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function stringValue(value: unknown) {
  return value == null ? '' : String(value);
}

function numberValue(value: unknown) {
  const parsed = Number(value ?? 0);
  return Number.isFinite(parsed) ? parsed : 0;
}

function formatMoney(value: number) {
  return `$${value.toFixed(2)}`;
}

function formatTransactionMoney(value: number) {
  return formatMoney(Math.abs(value));
}

function formatDateTime(value: string) {
  return value ? dayjs(value).format('MMM D, YYYY HH:mm') : '—';
}

function formatStatus(value: string) {
  return value
    ? value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, letter => letter.toUpperCase())
    : '—';
}

function printReceipt() {
  document.body.classList.add('payment-receipt-printing');
  const cleanup = () => document.body.classList.remove('payment-receipt-printing');
  window.addEventListener('afterprint', cleanup, { once: true });
  window.print();
  window.setTimeout(cleanup, 1000);
}

onBeforeUnmount(() => document.body.classList.remove('payment-receipt-printing'));
</script>

<style scoped>
.payment-receipt-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.payment-receipt-print-surface {
  max-width: 760px;
  margin: 0 auto;
  border: 1px solid #dbe3ec;
  background: #fff;
  color: #1f2937;
  padding: 28px;
}

.payment-receipt-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 16px;
  border-bottom: 3px solid #123764;
}

.payment-receipt-header span {
  color: #123764;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.payment-receipt-header h2 {
  margin: 4px 0 0;
  color: #123764;
  font-size: 24px;
}

.payment-receipt-header > strong {
  font-size: 14px;
}

.payment-receipt-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 24px;
  padding: 18px 0;
}

.payment-receipt-meta > div,
.payment-receipt-summary > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.payment-receipt-meta span,
.payment-receipt-summary span {
  color: #64748b;
  font-size: 12px;
}

.payment-receipt-table {
  width: 100%;
  border-collapse: collapse;
}

.payment-receipt-table th,
.payment-receipt-table td {
  border: 1px solid #dbe3ec;
  padding: 8px;
  font-size: 12px;
  text-align: left;
}

.payment-receipt-table th {
  background: #123764;
  color: #fff;
}

.payment-receipt-table th:last-child,
.payment-receipt-table td:last-child {
  text-align: right;
}

.payment-receipt-summary {
  display: grid;
  gap: 8px;
  width: min(320px, 100%);
  margin: 18px 0 0 auto;
}

.payment-receipt-summary__balance {
  padding-top: 8px;
  border-top: 2px solid #123764;
  font-size: 16px;
}

.payment-receipt-print-surface footer {
  margin-top: 28px;
  color: #64748b;
  font-size: 11px;
  text-align: center;
}

@media print {
  :global(body.payment-receipt-printing *) {
    visibility: hidden !important;
  }

  :global(body.payment-receipt-printing .payment-receipt-print-surface),
  :global(body.payment-receipt-printing .payment-receipt-print-surface *) {
    visibility: visible !important;
  }

  :global(body.payment-receipt-printing .payment-receipt-print-surface) {
    position: absolute;
    inset: 0;
    max-width: none;
    border: 0;
  }
}
</style>
