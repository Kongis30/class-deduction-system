import assert from 'node:assert/strict';
import test from 'node:test';
import { renderSignatureSheetDocument } from '../src/utils/signature-sheet-print.ts';

const student = {
  studentId: 4,
  studentName: 'A & B',
  packages: [],
};

const coursePackage = {
  packageId: 7,
  packageNo: 'PKG-7',
  packageName: 'Math',
  subjectName: 'Math',
  totalUnits: 40,
  purchasedUnits: 36,
  bonusUnits: 4,
  consumedUnits: 2,
  remainingUnits: 38,
  renewalReminder: false,
  packageStatus: 'ACTIVE',
  lessons: [
    lesson('thu', '2026-09-24T18:00:00-04:00'),
    lesson('sat', '2026-09-26T18:00:00-04:00'),
    { ...lesson('cancelled', '2026-09-28T18:00:00-04:00'), planningStatus: 'CANCELLED' },
  ],
};

function lesson(lessonNo, plannedStartAt) {
  const start = new Date(plannedStartAt);
  const end = new Date(start.getTime() + 2 * 60 * 60 * 1000);
  return {
    occurrenceId: lessonNo,
    lessonNo,
    packageId: 7,
    subjectName: 'Math',
    plannedTeacherId: 12,
    plannedTeacherName: 'Teacher One',
    plannedStartAt,
    plannedEndAt: end.toISOString(),
    plannedUnits: 4,
    planningStatus: 'PLANNED',
  };
}

test('student sheet shows the selected course schedule and excludes cancelled lessons', () => {
  const html = renderSignatureSheetDocument(student, coursePackage, 'parent');
  assert.match(html, /A &amp; B/);
  assert.match(html, /Thu&amp;Sat 6pm-8pm/);
  assert.match(html, /Total hours:<\/strong> 40\.0h/);
  assert.doesNotMatch(html, /cancelled/);
});

test('teacher mode creates the teacher signature table directly', () => {
  const html = renderSignatureSheetDocument(student, coursePackage, 'teacher');
  assert.match(html, /Teacher Signature Sheet/);
  assert.match(html, /Teacher One/);
  assert.match(html, /Thu&amp;Sat 6pm-8pm/);
  assert.match(html, /onclick="window\.print\(\)"/);
});
