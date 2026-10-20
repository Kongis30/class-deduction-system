<template>
  <section v-loading="loading" class="refund-receipt-page page-stack">
    <div class="refund-receipt-toolbar no-print">
      <el-button @click="backToReceipts">Back to receipts</el-button>
      <el-button type="primary" :disabled="!receipt" @click="printReceipt">Print receipt</el-button>
    </div>

    <el-alert v-if="errorMessage" type="error" :closable="false" show-icon :title="errorMessage" />

    <article v-if="receipt" class="refund-receipt-paper">
      <header class="refund-receipt-brand">
        <div>
          <p class="refund-receipt-eyebrow">JIN BEST TUTORING</p>
          <h1>Refund Receipt</h1>
          <p class="refund-receipt-subtitle">Refund transaction record</p>
        </div>
        <div class="refund-receipt-number">
          <span>Receipt no.</span>
          <strong>{{ receipt.receiptNo }}</strong>
        </div>
      </header>

      <section class="refund-receipt-details" aria-label="Receipt details">
        <div class="refund-receipt-detail">
          <span>Student</span>
          <strong>{{ studentName || '—' }}</strong>
        </div>
        <div class="refund-receipt-detail">
          <span>Enrollment no.</span>
          <strong>{{ enrollmentNo || '—' }}</strong>
        </div>
        <div class="refund-receipt-detail">
          <span>Refund date</span>
          <strong>{{ formatDate(payment.paidAt || receipt.issuedAt) }}</strong>
        </div>
        <div class="refund-receipt-detail">
          <span>Refund method</span>
          <strong>{{ refundMethod }}</strong>
        </div>
      </section>

      <section v-if="courseDetails.length" class="refund-receipt-courses">
        <h2>Course</h2>
        <p v-for="(course, index) in courseDetails" :key="`${index}-${course}`">{{ course }}</p>
      </section>

      <table class="refund-receipt-table">
        <thead>
          <tr><th>Description</th><th>Amount</th></tr>
        </thead>
        <tbody>
          <tr>
            <td>{{ refundDescription }}</td>
            <td>{{ formatMoney(refundAmount) }}</td>
          </tr>
          <tr class="refund-receipt-total">
            <td>Total refunded</td>
            <td>{{ formatMoney(refundAmount) }}</td>
          </tr>
        </tbody>
      </table>

      <footer class="refund-receipt-footer">
        <span>Recorded by {{ payment.handledByName || 'System' }}</span>
        <span>{{ payment.remark || '' }}</span>
      </footer>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { fetchReceipt } from '@/api/enrollment';
import { extractErrorMessage } from '@/api/http';
import type { ReceiptRecordItem } from '@/types/domain';
import { formatRoundedUsd } from '@/utils/money-display';

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const errorMessage = ref('');
const receipt = ref<ReceiptRecordItem | null>(null);

const payload = computed(() => parseRecord(receipt.value?.receiptPayload));
const payment = computed(() => {
  const rows = Array.isArray(payload.value.payments) ? payload.value.payments : [];
  return rows.length > 0 ? parseRecord(rows[0]) : {};
});
const studentName = computed(() => String(payload.value.studentName ?? receipt.value?.studentName ?? '').trim());
const enrollmentNo = computed(() => String(payload.value.enrollmentNo ?? '').trim());
const refundAmount = computed(() => Math.abs(toNumber(payment.value.amount ?? payload.value.paymentAmount)));
const refundMethod = computed(() => {
  const method = String(payment.value.paymentMethod ?? '').trim();
  if (payload.value.refundDisposition === 'CREDIT' || method === 'STUDENT_CREDIT') return 'Student credit';
  return paymentMethodLabel(method);
});
const refundDescription = computed(() => `Refund · ${refundMethod.value}`);
const courseDetails = computed(() => {
  const items = Array.isArray(payload.value.items) ? payload.value.items : [];
  return items.map((item) => {
    const row = parseRecord(item);
    return [row.courseProductName, row.detail, row.gradeBand, row.periodLabel]
      .map((value) => String(value ?? '').trim())
      .filter((value, index, values) => value && values.indexOf(value) === index)
      .join(' · ');
  }).filter(Boolean);
});

onMounted(loadReceipt);

// 退款收据是独立快照；详情从收据本身读取，不能依赖可能已取消或软删除的报名单。
async function loadReceipt() {
  const id = Array.isArray(route.params.receiptId) ? route.params.receiptId[0] : route.params.receiptId;
  if (!id) {
    errorMessage.value = 'Receipt not found.';
    return;
  }
  loading.value = true;
  try {
    const response = await fetchReceipt(String(id));
    const savedReceipt = response.data.data;
    if (savedReceipt.templateCode !== 'REFUND_RECEIPT') {
      throw new Error('This page only displays refund receipts.');
    }
    receipt.value = savedReceipt;
  } catch (error) {
    errorMessage.value = extractErrorMessage(error);
  } finally {
    loading.value = false;
  }
}

function backToReceipts() {
  void router.push({ name: 'receipt-records' });
}

function printReceipt() {
  window.print();
}

function parseRecord(value: unknown): Record<string, unknown> {
  if (typeof value === 'string') {
    try {
      const parsed: unknown = JSON.parse(value);
      return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
        ? parsed as Record<string, unknown>
        : {};
    } catch {
      return {};
    }
  }
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

function toNumber(value: unknown) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function formatMoney(value: number) {
  return formatRoundedUsd(value);
}

function formatDate(value: unknown) {
  const text = String(value ?? '').trim();
  if (!text) return '—';
  return text.replace('T', ' ').slice(0, 16);
}

function paymentMethodLabel(method: string) {
  const labels: Record<string, string> = {
    CASH: 'Cash',
    CHECK: 'Check',
    ZELLE: 'Zelle',
    VENMO: 'Venmo',
    CREDIT_CARD: 'Credit card',
    BANK_TRANSFER: 'Bank transfer',
  };
  return labels[method] ?? (method || '—');
}
</script>

<style scoped>
.refund-receipt-page {
  max-width: 920px;
  margin: 0 auto;
}

.refund-receipt-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.refund-receipt-paper {
  padding: 40px 48px;
  background: #fff;
  border: 1px solid #dbe3ef;
  border-radius: 12px;
  box-shadow: 0 12px 32px rgb(29 52 83 / 8%);
  color: #24364b;
}

.refund-receipt-brand {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 24px;
  border-bottom: 2px solid #1d4773;
}

.refund-receipt-eyebrow {
  margin: 0 0 8px;
  color: #41698f;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.refund-receipt-brand h1 {
  margin: 0;
  color: #173c65;
  font-size: 28px;
}

.refund-receipt-subtitle {
  margin: 6px 0 0;
  color: #72839a;
}

.refund-receipt-number {
  display: grid;
  gap: 6px;
  text-align: right;
}

.refund-receipt-number span,
.refund-receipt-detail span {
  color: #718198;
  font-size: 12px;
}

.refund-receipt-number strong {
  color: #173c65;
}

.refund-receipt-details {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px 32px;
  padding: 28px 0;
}

.refund-receipt-detail {
  display: grid;
  gap: 6px;
}

.refund-receipt-detail strong {
  overflow-wrap: anywhere;
}

.refund-receipt-courses {
  margin: 0 0 20px;
  padding: 16px 18px;
  border: 1px solid #dce5ef;
  border-radius: 8px;
  background: #f7f9fc;
}

.refund-receipt-courses h2 {
  margin: 0 0 8px;
  font-size: 14px;
}

.refund-receipt-courses p {
  margin: 4px 0;
}

.refund-receipt-table {
  width: 100%;
  border-collapse: collapse;
}

.refund-receipt-table th,
.refund-receipt-table td {
  padding: 14px 16px;
  border: 1px solid #d8e0e9;
  text-align: left;
}

.refund-receipt-table th {
  background: #edf2f7;
  color: #3e536d;
  font-size: 12px;
  text-transform: uppercase;
}

.refund-receipt-table th:last-child,
.refund-receipt-table td:last-child {
  text-align: right;
}

.refund-receipt-total td {
  color: #173c65;
  font-size: 16px;
  font-weight: 800;
}

.refund-receipt-footer {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  margin-top: 24px;
  color: #718198;
  font-size: 12px;
}

@media print {
  :global(body) {
    background: #fff !important;
  }

  :global(.sidebar),
  :global(.app-header),
  .no-print {
    display: none !important;
  }

  :global(.app-shell__main),
  :global(.app-shell__content) {
    margin: 0 !important;
    padding: 0 !important;
  }

  .refund-receipt-page {
    display: block !important;
    max-width: none;
    margin: 0;
  }

  .refund-receipt-paper {
    padding: 20mm 18mm;
    border: 0;
    border-radius: 0;
    box-shadow: none;
  }
}
</style>
