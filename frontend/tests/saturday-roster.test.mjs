import assert from 'node:assert/strict';
import test from 'node:test';
import { filterSaturdayRosterRows } from '../src/utils/saturday-roster.ts';

const row = (id, periodLabel, paidAmount = 0) => ({
  orderId: `order-${id}`,
  orderItemId: `item-${id}`,
  studentId: 'student-1',
  studentName: 'Same Student',
  periodLabel,
  pickupRequired: false,
  dropoffRequired: false,
  paymentStatus: 'PAID',
  pickupFeeAmount: 0,
  dropoffFeeAmount: 0,
  extraFeeAmount: 0,
  discountAmount: 0,
  amountDue: paidAmount,
  paidAmount,
  balanceAmount: 0,
  overdue: false,
  expiringSoon: false,
  lowOneOnOneHours: false,
  alertTypes: [],
});

test('season tabs include full-year registrations once without cloning records', () => {
  const fall = row('fall', 'Fall 2026', 100);
  const spring = row('spring', 'Spring 2027', 150);
  const fullYear = row('year', '2026-2027 Full Year', 400);
  const sourceRows = [fall, spring, fullYear];

  assert.deepEqual(filterSaturdayRosterRows(sourceRows, 'FALL'), [fall, fullYear]);
  assert.deepEqual(filterSaturdayRosterRows(sourceRows, 'SPRING'), [spring, fullYear]);
  assert.deepEqual(filterSaturdayRosterRows(sourceRows, 'ALL'), sourceRows);
  assert.equal(filterSaturdayRosterRows(sourceRows, 'ALL').reduce((sum, item) => sum + item.paidAmount, 0), 650);
});

test('all roster preserves two actual registrations for the same student', () => {
  const firstRegistration = row('one', 'Fall 2026', 100);
  const secondRegistration = row('two', 'Spring 2027', 200);

  const allRows = filterSaturdayRosterRows([firstRegistration, secondRegistration], 'ALL');

  assert.equal(allRows.length, 2);
  assert.deepEqual(allRows.map(item => item.orderItemId), ['item-one', 'item-two']);
  assert.equal(allRows.reduce((sum, item) => sum + item.paidAmount, 0), 300);
});

test('unclassified rows remain visible only in all roster', () => {
  const unclassified = row('unknown', '2026-2027');

  assert.deepEqual(filterSaturdayRosterRows([unclassified], 'FALL'), []);
  assert.deepEqual(filterSaturdayRosterRows([unclassified], 'SPRING'), []);
  assert.deepEqual(filterSaturdayRosterRows([unclassified], 'ALL'), [unclassified]);
});

test('season is read from the enrollment option snapshot, not student grade or class', () => {
  const spring = {
    ...row('spring-snapshot', '2026-2027 Saturday Course'),
    grade: '3',
    gradeBand: '春季班',
    className: 'G3',
  };

  assert.deepEqual(filterSaturdayRosterRows([spring], 'SPRING'), [spring]);
  assert.deepEqual(filterSaturdayRosterRows([spring], 'FALL'), []);
});
