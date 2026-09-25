import assert from 'node:assert/strict';
import test from 'node:test';
import { exceedsMoney, formatExactUsd, formatRoundedUsd } from '../src/utils/money-display.ts';

test('all monetary views round for display without changing cents', () => {
  const amount = 10.49;
  assert.equal(formatRoundedUsd(amount), '$10');
  assert.equal(formatRoundedUsd(10.5), '$11');
  assert.equal(formatExactUsd(amount), '$10.49');
  assert.equal(amount, 10.49);
});

test('credit and overpayment checks reject a single cent over the exact limit', () => {
  assert.equal(exceedsMoney(10, 10), false);
  assert.equal(exceedsMoney(10.01, 10), true);
  assert.equal(exceedsMoney(10.49, 10.49), false);
  assert.equal(exceedsMoney(10.5, 10.49), true);
  assert.equal(exceedsMoney(0.1 + 0.2, 0.3), false);
});
