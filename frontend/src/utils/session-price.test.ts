import assert from 'node:assert/strict';
import test from 'node:test';

import { calculateProratedSessionPrice } from './session-price.ts';

test('session price is ceiled before it is saved', () => {
  assert.equal(calculateProratedSessionPrice(1399, 12, 13), 1292);
  assert.equal(calculateProratedSessionPrice(1399, 13, 13), 1399);
  assert.equal(calculateProratedSessionPrice(1399, 1, 13), 108);
});

test('fractional configured prices are calculated from cents', () => {
  assert.equal(calculateProratedSessionPrice(100.50, 2, 3), 67);
});

test('positive packages never become free after ceiling', () => {
  assert.equal(calculateProratedSessionPrice(1, 1, 2), 1);
  assert.equal(calculateProratedSessionPrice(0, 1, 2), 0);
});
