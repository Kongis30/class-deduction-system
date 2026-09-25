import assert from 'node:assert/strict';
import test from 'node:test';
import { calendarWeekRange } from '../src/utils/calendar-week.ts';

const timezone = 'America/New_York';

test('Friday calendar includes the upcoming Saturday lesson', () => {
  assert.deepEqual(calendarWeekRange(new Date('2026-09-25T20:00:00Z'), timezone), {
    startDate: '2026-09-21',
    endExclusiveDate: '2026-09-28',
  });
});

test('Sunday in New York remains in the displayed week across browser time zones', () => {
  assert.deepEqual(calendarWeekRange(new Date('2026-09-28T02:00:00Z'), timezone), {
    startDate: '2026-09-21',
    endExclusiveDate: '2026-09-28',
  });
});

test('Monday in New York starts the next calendar week', () => {
  assert.deepEqual(calendarWeekRange(new Date('2026-09-28T05:00:00Z'), timezone), {
    startDate: '2026-09-28',
    endExclusiveDate: '2026-10-05',
  });
});
