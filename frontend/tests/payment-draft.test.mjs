import assert from 'node:assert/strict';
import test from 'node:test';

import { paymentDraftAmount, sumPaymentAllocations } from '../src/views/registration/payment-draft.ts';

test('edited receipt uses its visible course payments instead of the old payment total', () => {
  const payment = {
    amount: 1988,
    courseAllocations: { afterSchool: 720, saturday: 1188 },
  };

  assert.equal(paymentDraftAmount(payment, 2), 1908);
  payment.courseAllocations.afterSchool = 700;
  assert.equal(sumPaymentAllocations(payment.courseAllocations), 1888);
});

test('single-course, unallocated legacy, and refund payments retain their recorded amounts', () => {
  assert.equal(paymentDraftAmount({ amount: 720, courseAllocations: {} }, 1), 720);
  assert.equal(paymentDraftAmount({ amount: 300, courseAllocations: {} }, 2), 300);
  assert.equal(paymentDraftAmount({ amount: -50, paymentType: 'REFUND', courseAllocations: { course: -50 } }, 2), -50);
});

test('course allocations add in cents', () => {
  assert.equal(sumPaymentAllocations({ first: 0.1, second: 0.2 }), 0.3);
});
