import type { OneOnOneLesson, OneOnOnePackage, OneOnOneStudent } from '@/api/one-on-one';
import type { SchedulePrintRevision } from '@/api/schedule';
import { signatureLessonRows, signatureSubjectName } from '@/utils/one-on-one-signature-data';

type SheetMode = 'parent' | 'teacher';

const TIME_ZONE = 'America/New_York';

/** 生成独立的签字表预览文档；Schedule 与消课页共用同一份 1-on-1 课程数据契约。 */
export function renderSignatureSheetDocument(
  student: OneOnOneStudent,
  coursePackage: OneOnOnePackage,
  mode: SheetMode,
  revisions: SchedulePrintRevision[] = [],
  warnings: string[] = [],
  teacherKey?: string,
): string {
  const lessons = signatureLessonRows(coursePackage).filter(row =>
    mode !== 'teacher' || !teacherKey || teacherKey === '__all__'
      || teacherIdentity(row) === teacherKey);
  const title = mode === 'parent' ? 'Student Signature Sheet' : 'Teacher Signature Sheets';
  const content = mode === 'parent'
    ? renderParentSheet(student, coursePackage, lessons, revisions)
    : renderTeacherSheets(student, coursePackage, lessons, revisions);

  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(title)} - ${escapeHtml(studentDisplayName(student, coursePackage))}</title>
<style>
  * { box-sizing: border-box; }
  body { margin: 0; padding: 24px; color: #111; font-family: Arial, Helvetica, sans-serif; background: #f4f6f8; }
  .toolbar { display: flex; justify-content: flex-end; gap: 8px; max-width: 1000px; margin: 0 auto 16px; }
  .toolbar button { padding: 9px 16px; border: 1px solid #c8d3df; border-radius: 5px; background: #fff; color: #24364b; cursor: pointer; font-size: 14px; }
  .toolbar button.primary { border-color: #3688ea; background: #3688ea; color: #fff; }
  .toolbar button:disabled { cursor: not-allowed; opacity: .55; }
  .sheet { max-width: 1000px; margin: 0 auto 20px; padding: 22px 24px; background: #fff; box-shadow: 0 2px 12px #1b2a3a1c; page-break-after: always; }
  .sheet:last-child { page-break-after: auto; }
  .meta { display: flex; flex-wrap: wrap; gap: 8px 28px; margin-bottom: 10px; }
  .meta p { margin: 0; font-size: 16px; line-height: 1.35; }
  .hours { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; width: 100%; }
  .hours section { display: grid; gap: 5px; padding: 9px 12px; border: 1px solid #777; border-radius: 4px; }
  .hours section:last-child { border-left: 3px solid #222; background: #f2f2f2; }
  .teacher-meta { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px 24px; margin-bottom: 28px; }
  .teacher-meta p { margin: 0; font-size: 15px; font-weight: 600; }
  .teacher-meta p:first-child { grid-column: 1 / -1; }
  .teacher-hours { display: flex; flex-wrap: wrap; gap: 6px 20px; margin: -14px 0 22px; }
  .teacher-hours p { margin: 0; font-size: 13px; }
  .lesson-table thead { display: table-header-group; }
  .lesson-table tr { page-break-inside: avoid; break-inside: avoid; }
  table { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: 14px; }
  th, td { height: 28px; padding: 4px 6px; border: 1px solid #111; text-align: left; vertical-align: middle; overflow-wrap: anywhere; }
  th { font-weight: 700; }
  h1 { margin: 0 0 16px; font-size: 20px; }
  h2 { margin: 20px 0 9px; font-size: 16px; }
  .revision { border: 1px solid #b9c8d8; padding: 10px 12px; margin: 8px 0; break-inside: avoid; }
  .revision-heading { display: flex; justify-content: space-between; gap: 12px; font-weight: 700; }
  .revision-meta { margin: 6px 0; color: #47566a; font-size: 12px; }
  .revision-slot { margin: 4px 0 0; font-size: 13px; }
  .revision-change { margin: 6px 0 0; color: #47566a; font-size: 12px; }
  .lesson-status { color: #47566a; font-size: 12px; }
  .print-warning { max-width: 1000px; margin: 0 auto 12px; padding: 12px; border: 1px solid #be6d00; background: #fff2d8; color: #6b3900; }
  .lesson-table th, .lesson-table td { height: 22px; padding: 2px 4px; font-size: 14px; }
  .empty { padding: 24px; border: 1px solid #bbb; text-align: center; color: #555; }
  @media print {
    @page { size: letter portrait; margin: 0.5in 0.5in 0.5in 0.75in; }
    body { padding: 0; background: #fff; }
    .toolbar { display: none; }
    .sheet { width: 100%; max-width: none; margin: 0; padding: 0; box-shadow: none; }
    .lesson-table { width: 100%; margin-left: 0; }
  }
</style></head><body>
<div class="toolbar"><button onclick="window.close()">Close</button><button class="primary" onclick="window.print()" ${warnings.length ? 'disabled title="Resolve the missing print data and retry"' : ''}>Print</button></div>
${warnings.length ? `<div class="print-warning"><strong>Print data is incomplete.</strong> ${warnings.map(escapeHtml).join(' ')}</div>` : ''}
${content}
</body></html>`;
}

/** 1-on-1 消课页与课包详情共用的正式签字表模板，字段和打印样式以消课页为准。 */
export function renderOneOnOneSignatureSheetDocument(
  student: OneOnOneStudent,
  coursePackage: OneOnOnePackage,
  mode: SheetMode,
  teacherKey = '__all__',
  options: { embedded?: boolean } = {},
): string {
  const allLessons = signatureLessonRows(coursePackage);
  const lessons = mode === 'teacher' && teacherKey !== '__all__'
    ? allLessons.filter(row => signatureTeacherKey(row) === teacherKey)
    : allLessons;
  if (mode === 'teacher') {
    lessons.sort((left, right) => Date.parse(left.plannedStartAt) - Date.parse(right.plannedStartAt)
      || signatureSubjectName(left, coursePackage).localeCompare(signatureSubjectName(right, coursePackage))
      || currentTeacherName(left).localeCompare(currentTeacherName(right)));
  }
  const studentName = escapeHtml(currentStudentDisplayName(student, coursePackage));
  const embeddedClass = options.embedded ? ' class="embedded-preview"' : '';
  const content = mode === 'parent'
    ? renderCurrentParentSheet(studentName, coursePackage, allLessons)
    : renderCurrentTeacherSheet(studentName, coursePackage, lessons, teacherKey);

  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>${mode === 'parent' ? 'Parent' : 'Teacher'} Signature Sheet - ${studentName}</title>
<style>
  * { box-sizing: border-box; }
  body { margin: 0; padding: 24px; color: #111; font-family: Arial, Helvetica, sans-serif; background: #f4f6f8; }
  body.embedded-preview { padding: 12px; }
  .toolbar { display: flex; justify-content: flex-end; gap: 8px; max-width: 1000px; margin: 0 auto 16px; }
  body.embedded-preview .toolbar { display: none; }
  .toolbar button { padding: 9px 16px; border: 1px solid #c8d3df; border-radius: 5px; background: #fff; color: #24364b; cursor: pointer; font-size: 14px; }
  .toolbar button.primary { border-color: #3688ea; background: #3688ea; color: #fff; }
  .oo-signature-sheet { max-width: 1000px; margin: 0 auto 12px; padding: 14px 16px; border: 1px solid #111; border-radius: 6px; background: #fff; color: #111; font-family: Arial, Helvetica, sans-serif; }
  .oo-signature-sheet__meta { display: flex; flex-wrap: wrap; gap: 16px; margin-bottom: 8px; }
  .oo-signature-sheet__meta p { margin: 0; color: #111; font-size: 16px; line-height: 1.22; }
  .oo-signature-sheet__meta strong { font-weight: 800; }
  .oo-signature-hours { display: flex; flex: 1 0 100%; gap: 10px; }
  .oo-signature-hours__group { display: flex; flex: 1; flex-direction: column; flex-wrap: nowrap; align-items: flex-start; gap: 4px; padding: 7px 10px; border: 1px solid #777; border-radius: 4px; }
  .oo-signature-hours__group p { white-space: nowrap; }
  .oo-signature-hours__group--balance { border-left: 3px solid #222; background: #f2f2f2; }
  .oo-signature-subjects { display: grid; gap: 2px; margin-bottom: 30px; }
  .oo-signature-subject-line { display: grid; grid-template-columns: 1.2fr 1fr 1.45fr 1fr 1fr; gap: 16px; color: #111; font-size: 15px; font-weight: 600; }
  .oo-signature-table { width: 100%; border-collapse: collapse; table-layout: fixed; color: #111; font-size: 14px; }
  .oo-signature-table th, .oo-signature-table td { height: 22px; padding: 2px 4px; border: 1px solid #111; text-align: left; vertical-align: middle; word-break: break-word; }
  .oo-signature-table th { font-weight: 700; }
  .oo-teacher-sheet__meta { display: grid; grid-template-columns: auto auto auto; gap: 6px 36px; margin-bottom: 32px; }
  .oo-teacher-sheet__meta p { margin: 0; color: #111; font-size: 16px; font-weight: 600; line-height: 1.35; }
  .oo-teacher-sheet__meta p:first-child { grid-column: 1 / -1; }
  .oo-teacher-sheet__meta p:nth-child(4) { grid-column: 1; }
  .empty { padding: 24px; border: 1px solid #bbb; text-align: center; color: #555; }
  @media (max-width: 700px) {
    body { padding: 12px; }
    .oo-signature-hours { flex-direction: column; }
    .oo-signature-hours__group p { white-space: normal; }
    .oo-signature-subject-line { grid-template-columns: 1fr 1fr; gap: 6px 12px; }
    .oo-teacher-sheet__meta { grid-template-columns: 1fr; gap: 6px; }
    .oo-teacher-sheet__meta p:first-child, .oo-teacher-sheet__meta p:nth-child(4) { grid-column: auto; }
    .oo-signature-table { font-size: 12px; }
  }
  @media print {
    @page { size: letter portrait; margin: 0.5in 0.5in 0.5in 0.75in; }
    body { padding: 0; background: #fff; }
    .toolbar { display: none; }
    .oo-signature-sheet { width: 100%; max-width: none; margin: 0; padding: 0; border: 0; border-radius: 0; break-inside: avoid; }
    .oo-signature-subjects { margin-bottom: 28px; }
    .oo-signature-table { margin-top: 6px; font-size: 14px; }
    .oo-signature-table thead { display: table-header-group; }
    .oo-signature-table tr { break-inside: avoid; }
  }
</style></head><body${embeddedClass}>
<div class="toolbar"><button onclick="window.close()">Close</button><button class="primary" onclick="window.print()">Print</button></div>
${content}
</body></html>`;
}

function renderCurrentParentSheet(studentName: string, coursePackage: OneOnOnePackage, lessons: OneOnOneLesson[]) {
  const lessonRows = lessons.map(row => `<tr>
    <td>${escapeHtml(signatureSubjectName(row, coursePackage))}</td>
    <td>${escapeHtml(currentTeacherName(row))}</td>
    <td>${escapeHtml(currentLessonDate(row))}</td>
    <td>${escapeHtml(row.historicalSignatureText ?? '')}</td>
  </tr>`).join('');
  const summaries = currentSubjectSummaries(lessons, coursePackage).map(summary => `<div class="oo-signature-subject-line">
    <span><strong>Lesson name:</strong> ${escapeHtml(summary.subject)}</span>
    <span><strong>Teacher:</strong> ${escapeHtml(summary.teacher)}</span>
    <span><strong>Classtime:</strong> ${escapeHtml(summary.classTime)}</span>
    <span><strong>Hours on sheet:</strong> ${formatNumber(summary.hours)}</span>
    <span><strong>Starting Date:</strong> ${escapeHtml(summary.startDate)}</span>
  </div>`).join('');

  return `<section class="oo-signature-sheet oo-parent-signature-sheet">
    <div class="oo-signature-sheet__meta">
      <p><strong>Student:</strong> ${studentName}</p>
      <div class="oo-signature-hours">
        <div class="oo-signature-hours__group">
          <p><strong>Lessons on this sheet:</strong> ${formatNumber(lessons.reduce((total, row) => total + Number(row.plannedUnits ?? 0), 0))}</p>
          <p><strong>Package hours:</strong> ${formatNumber(coursePackage.totalUnits)}</p>
          <p><strong>Purchased Hours:</strong> ${formatNumber(coursePackage.purchasedUnits ?? coursePackage.totalUnits)}</p>
          <p><strong>Bonus Hours:</strong> ${formatNumber(coursePackage.bonusUnits ?? 0)}</p>
        </div>
        <div class="oo-signature-hours__group oo-signature-hours__group--balance">
          <p><strong>Already deducted:</strong> ${formatNumber(coursePackage.consumedUnits)}</p>
          <p><strong>Remaining:</strong> ${formatNumber(coursePackage.remainingUnits)}</p>
        </div>
      </div>
    </div>
    <div class="oo-signature-subjects">${summaries}</div>
    <table class="oo-signature-table oo-signature-table--detail"><thead><tr>
      <th>Lesson name</th><th>Teacher</th><th>Date</th><th>Signature</th>
    </tr></thead><tbody>${lessonRows || '<tr><td colspan="4">No lessons on this sheet.</td></tr>'}</tbody></table>
  </section>`;
}

function currentStudentDisplayName(student: OneOnOneStudent, coursePackage: OneOnOnePackage) {
  const participantNames = (coursePackage.participants ?? [])
    .map(item => item.studentName?.trim())
    .filter((name): name is string => Boolean(name));
  return participantNames.length ? participantNames.join(' / ') : student.studentName;
}

function renderCurrentTeacherSheet(
  studentName: string,
  coursePackage: OneOnOnePackage,
  lessons: OneOnOneLesson[],
  teacherKey: string,
) {
  const teacherOptions = currentTeacherOptions(coursePackage);
  const summaries = teacherOptions
    .filter(item => teacherKey === '__all__' || item.key === teacherKey)
    .map(item => {
      const hours = coursePackage.teacherHours?.find(row => String(row.teacherId) === item.key)
        ?? coursePackage.teacherHours?.find(row => !row.teacherId && normalizeTeacher(row.teacherName) === item.key);
      return `<p><strong>Total Hours:</strong> ${formatNumber(coursePackage.totalUnits)}h ·
        <strong>Starting Hours:</strong> ${formatNumber(coursePackage.startingHours)}h ·
        <strong>Allocated to ${escapeHtml(item.teacher)}:</strong> ${formatNumber(hours?.allocatedUnits)}h ·
        <strong>Current schedule deducted by ${escapeHtml(item.teacher)}:</strong> ${formatNumber(hours?.deductedUnits)}h ·
        <strong>Remaining with ${escapeHtml(item.teacher)}:</strong> ${formatNumber(hours?.remainingUnits)}h</p>`;
    }).join('');
  const teacherNames = Array.from(new Set(lessons.map(row => row.plannedTeacherName?.trim() || currentTeacherName(row))));
  const rows = lessons.map(row => `<tr>
    <td>${escapeHtml(signatureSubjectName(row, coursePackage))}</td>
    <td>${escapeHtml(currentTeacherName(row))}</td>
    <td>${escapeHtml(currentLessonDate(row))}</td>
    <td>${escapeHtml(currentTimeOfDay(row) || '—')}</td>
    <td>${formatNumber(row.plannedUnits)}</td>
    <td>${escapeHtml(row.historicalSignatureText ?? '')}</td>
  </tr>`).join('');
  const startDate = lessons.length ? currentLessonDate(lessons[0]) : '—';

  return `<section class="oo-signature-sheet oo-teacher-signature-sheet">
    <div class="oo-teacher-sheet__meta">
      <p><strong>Student:</strong> ${studentName}</p>
      <p><strong>Course:</strong> ${escapeHtml(coursePackage.packageName)}</p>
      <p><strong>Teacher(s):</strong> ${escapeHtml(teacherNames.join(', ') || '—')}</p>
      <p><strong>Classtime:</strong> ${escapeHtml(currentClassTime(lessons))}</p>
      <p><strong>Starting Date:</strong> ${escapeHtml(startDate)}</p>
      ${summaries}
    </div>
    <table class="oo-signature-table oo-signature-table--teacher"><thead><tr>
      <th>Lesson name</th><th>Teacher</th><th>Date</th><th>Time</th><th>Hours</th><th>Signature</th>
    </tr></thead><tbody>${rows || '<tr><td colspan="6">No lessons on this sheet.</td></tr>'}</tbody></table>
  </section>`;
}

function currentSubjectSummaries(lessons: OneOnOneLesson[], coursePackage: OneOnOnePackage) {
  const groups = new Map<string, { subject: string; teacher: string; rows: OneOnOneLesson[]; hours: number }>();
  for (const row of lessons) {
    const subject = signatureSubjectName(row, coursePackage);
    const teacher = currentTeacherName(row);
    const teacherId = row.confirmStage === 'CONFIRMED'
      ? row.actualTeacherId || row.plannedTeacherId
      : row.plannedTeacherId;
    const key = `${subject}__${teacherId == null || String(teacherId).trim() === ''
      ? normalizeTeacher(teacher)
      : String(teacherId)}`;
    const group = groups.get(key) ?? { subject, teacher, rows: [], hours: 0 };
    group.rows.push(row);
    group.hours += Number(row.plannedUnits ?? 0);
    groups.set(key, group);
  }
  return [...groups.values()].map(group => ({
    ...group,
    classTime: currentClassTime(group.rows),
    startDate: currentLessonDate(group.rows[0]),
  }));
}

function currentTeacherOptions(coursePackage: OneOnOnePackage) {
  const options = new Map<string, { key: string; teacher: string }>();
  for (const row of signatureLessonRows(coursePackage)) {
    const teacher = row.plannedTeacherName?.trim() || currentTeacherName(row);
    const key = currentScheduleTeacherKey(row);
    if (!options.has(key)) options.set(key, { key, teacher });
  }
  return [...options.values()].sort((left, right) => left.teacher.localeCompare(right.teacher));
}

function signatureTeacherKey(row: OneOnOneLesson) {
  return currentScheduleTeacherKey(row);
}

function currentScheduleTeacherKey(row: OneOnOneLesson) {
  return String(row.plannedTeacherId ?? '').trim().toLowerCase()
    || normalizeTeacher(row.plannedTeacherName);
}

function normalizeTeacher(value?: string) {
  return value?.trim().toLowerCase() ?? '';
}

function currentTeacherName(row: OneOnOneLesson) {
  const name = row.confirmStage === 'CONFIRMED' ? row.actualTeacherName || row.plannedTeacherName : row.plannedTeacherName;
  return name?.split(/\r?\n/)[0]?.trim() || 'To be assigned';
}

function currentLessonDate(row: OneOnOneLesson) {
  if (row.signatureHistory) {
    const [year, month, day] = row.plannedStartAt.slice(0, 10).split('-');
    return year && month && day ? `${Number(month)}/${Number(day)}/${year}` : '—';
  }
  return formatDate(row.plannedStartAt);
}

function currentTimeOfDay(row: OneOnOneLesson) {
  if (row.signatureHistory || !row.plannedStartAt || !row.plannedEndAt) return '';
  const start = formatClock(row.plannedStartAt.slice(11, 16)).toLowerCase();
  const end = formatClock(row.plannedEndAt.slice(11, 16)).toLowerCase().replace(':00', '');
  return `${start}-${end}`;
}

function currentClassTime(lessons: OneOnOneLesson[]) {
  const schedules = new Map<string, Set<number>>();
  for (const row of lessons) {
    const time = currentTimeOfDay(row);
    const date = dateKeyInTimeZone(row.plannedStartAt);
    if (!time || !date) continue;
    const [year, month, day] = date.split('-').map(Number);
    const weekday = new Date(Date.UTC(year, month - 1, day)).getUTCDay();
    const days = schedules.get(time) ?? new Set<number>();
    days.add(weekday);
    schedules.set(time, days);
  }
  if (!schedules.size) return 'To be scheduled';
  const weekdayNames = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  return [...schedules.entries()].map(([time, days]) =>
    `${[...days].sort((a, b) => a - b).map(day => weekdayNames[day]).join('&')} ${time}`,
  ).join(', ');
}

function formatNumber(value?: number) {
  return Number(value ?? 0).toFixed(1);
}

function dateKeyInTimeZone(value: string) {
  if (!value) return '';
  if (!value.endsWith('Z') && !/[+-]\d{2}:\d{2}$/.test(value)) return value.slice(0, 10);
  const date = new Date(value);
  if (Number.isNaN(date.valueOf())) return '';
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: TIME_ZONE, year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(date);
}


function renderParentSheet(
  student: OneOnOneStudent,
  coursePackage: OneOnOnePackage,
  lessons: OneOnOneLesson[],
  revisions: SchedulePrintRevision[],
) {
  const participantNames = studentDisplayName(student, coursePackage);
  const groupTag = (coursePackage.participantCount ?? 1) > 1
    ? `<p><strong>Lesson format:</strong> 1-on-${escapeHtml(coursePackage.participantCount)} shared course</p>`
    : '';

  return `<main class="sheet">
    <h1>Student Signature Sheet</h1>
    <div class="meta"><p><strong>Student:</strong> ${escapeHtml(participantNames)}</p>${groupTag}
      <div class="hours"><section>
        <p><strong>Package hours:</strong> ${formatHours(coursePackage.totalUnits)}</p>
        <p><strong>Purchased Hours:</strong> ${formatHours(coursePackage.purchasedUnits ?? coursePackage.totalUnits)}</p>
        <p><strong>Bonus Hours:</strong> ${formatHours(coursePackage.bonusUnits ?? 0)}</p>
      </section><section>
        <p><strong>Already deducted:</strong> ${formatHours(coursePackage.consumedUnits)}</p>
        <p><strong>Remaining:</strong> ${formatHours(coursePackage.remainingUnits)}</p>
      </section></div>
    </div>
    ${renderRevisionHistory(revisions)}
    ${renderLessonSection(lessons, coursePackage, 'parent')}
  </main>`;
}

function renderTeacherSheets(
  student: OneOnOneStudent,
  coursePackage: OneOnOnePackage,
  lessons: OneOnOneLesson[],
  revisions: SchedulePrintRevision[],
) {
  const groups = groupByTeacher(lessons);
  if (!groups.length) {
    return `<main class="sheet"><h1>Teacher Signature Sheet</h1>
      <p><strong>Student:</strong> ${escapeHtml(studentDisplayName(student, coursePackage))}</p>
      ${renderRevisionHistory(revisions)}
      <div class="empty">No lessons are available for this course.</div></main>`;
  }
  return groups.map((group, index) => `<main class="sheet">
    <h1>Teacher Signature Sheet${groups.length > 1 ? ` ${index + 1}` : ''}</h1>
    <div class="teacher-meta">
      <p><strong>Student:</strong> ${escapeHtml(studentDisplayName(student, coursePackage))}</p>
      <p><strong>Teacher:</strong> ${escapeHtml(group.teacher)}</p>
      <p><strong>Course:</strong> ${escapeHtml(coursePackage.packageName)}</p>
    </div>
    ${renderTeacherHours(coursePackage, group.key, group.teacher)}
    ${renderRevisionHistory(revisions)}
    ${renderLessonSection(group.rows, coursePackage, 'teacher')}
  </main>`).join('');
}

function renderTeacherHours(coursePackage: OneOnOnePackage, teacherKey: string, teacherName: string) {
  const hours = coursePackage.teacherHours?.find(item =>
    String(item.teacherId).trim().toLowerCase() === teacherKey
    || (!item.teacherId && item.teacherName?.trim().toLowerCase() === teacherName.trim().toLowerCase()));
  return `<div class="teacher-hours">
    <p><strong>Total Hours:</strong> ${formatHours(coursePackage.totalUnits)}</p>
    <p><strong>Starting Hours:</strong> ${formatHours(coursePackage.startingHours)}</p>
    <p><strong>Allocated to ${escapeHtml(teacherName)}:</strong> ${formatHours(hours?.allocatedUnits)}</p>
    <p><strong>Current schedule deducted by ${escapeHtml(teacherName)}:</strong> ${formatHours(hours?.deductedUnits)}</p>
    <p><strong>Remaining with ${escapeHtml(teacherName)}:</strong> ${formatHours(hours?.remainingUnits)}</p>
  </div>`;
}

/** 每个规则版本的时段快照直接打印，保留变更发生前后的完整配置。 */
function renderRevisionHistory(revisions: SchedulePrintRevision[]) {
  if (!revisions.length) {
    return '<h2>Schedule changes</h2><p class="lesson-status">No recurring schedule version is recorded for this course.</p>';
  }
  return `<h2>Schedule changes</h2>${revisions.map(revision => {
    const changedAt = revision.changedAt ? formatLocalDateTime(revision.changedAt) : 'Date not recorded';
    const heading = revision.supersedesRuleId ? 'Schedule changed' : 'Initial schedule';
    const startDate = revision.startDate ? formatDate(revision.startDate) : '—';
    const slots = revision.slots.length
      ? revision.slots.map(slot => `<p class="revision-slot">
          ${escapeHtml(weekdayName(slot.weekday))} ${escapeHtml(formatClock(slot.startTime))}–${escapeHtml(formatClock(slot.endTime))}
          · ${escapeHtml(slot.subjectName || 'Course name not recorded')}
          · ${escapeHtml(slot.teacherName || 'To be assigned')}
          · ${escapeHtml(slot.deliveryMode === 'ONLINE' ? 'Online' : slot.classroomName || 'Room not set')}
          · ${formatHours(slot.allocatedHours)}
        </p>`).join('')
      : '<p class="lesson-status">This older schedule has no preserved weekly slot details.</p>';
    const detailChanges = (revision.detailsChanges ?? []).map(change => `<p class="revision-change">
      Course details updated ${escapeHtml(change.changedAt ? formatLocalDateTime(change.changedAt) : 'Date not recorded')}:
      ${escapeHtml(change.beforeSummary)} → ${escapeHtml(change.afterSummary)}
    </p>`).join('');
    return `<section class="revision">
      <div class="revision-heading"><span>${heading} #${revision.revisionNumber}</span><span>${escapeHtml(changedAt)}</span></div>
      <div class="revision-meta">Rule start: ${escapeHtml(startDate)}
        · Starting: ${revision.startingHours == null ? '—' : formatHours(revision.startingHours)}
        · Allocated: ${formatHours(revision.allocatedHours)}</div>
      ${slots}
      ${detailChanges}
    </section>`;
  }).join('')}`;
}

/** 与消课页使用相同的签字表列；历史记录取课次快照，未来记录取新排课生成的课次。 */
function renderLessonSection(rows: OneOnOneLesson[], coursePackage: OneOnOnePackage, mode: SheetMode) {
  if (!rows.length) return '<h2>Lesson records</h2><p class="lesson-status">No entries.</p>';
  const teacherColumns = mode === 'teacher' ? '<th>Time</th><th>Hours</th>' : '';
  return `<h2>Lesson records</h2>
    <table class="lesson-table"><thead><tr>
      <th>Lesson name</th><th>Teacher</th><th>Date</th>${teacherColumns}<th>Signature</th>
    </tr></thead><tbody>${rows.map(row => `<tr>
        <td>${escapeHtml(signatureSubjectName(row, coursePackage))}</td>
        <td>${escapeHtml(teacherName(row))}</td>
        <td>${escapeHtml(formatDate(row.plannedStartAt))}</td>
        ${mode === 'teacher' ? `<td>${escapeHtml(formatLessonTime(row))}</td><td>${formatHours(row.plannedUnits)}</td>` : ''}
        <td>${escapeHtml(row.historicalSignatureText ?? '')}</td>
      </tr>`).join('')}</tbody></table>`;
}

function groupByTeacher(lessons: OneOnOneLesson[]) {
  const groups = new Map<string, { teacher: string; rows: OneOnOneLesson[] }>();
  for (const row of lessons) {
    const teacher = row.plannedTeacherName?.split(/\r?\n/)[0]?.trim() || teacherName(row);
    const key = teacherIdentity(row);
    const group = groups.get(key);
    if (group) group.rows.push(row);
    else groups.set(key, { teacher, rows: [row] });
  }
  return [...groups.values()];
}

function teacherIdentity(row: OneOnOneLesson) {
  const teacher = row.plannedTeacherName?.trim() || teacherName(row);
  return String(row.plannedTeacherId ?? teacher).trim().toLowerCase();
}

function studentDisplayName(student: OneOnOneStudent, coursePackage: OneOnOnePackage): string {
  const participants = (coursePackage.participants ?? [])
    .map(item => item.studentName?.trim())
    .filter((name): name is string => Boolean(name));
  return participants.length > 1 ? participants.join(' / ') : student.studentName;
}

function teacherName(row: OneOnOneLesson) {
  return (row.confirmStage === 'CONFIRMED' ? row.actualTeacherName || row.plannedTeacherName : row.plannedTeacherName)
    ?.split(/\r?\n/)[0]?.trim() || 'To be assigned';
}

function weekdayName(weekday: number) {
  return ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][weekday - 1] || 'Day not set';
}

function formatHours(value?: number) {
  return `${Number(value ?? 0).toFixed(1)}h`;
}

/** 后端课次和规则时间是纽约本地时间，不能按浏览器时区再次换算。 */
function formatDate(value?: string) {
  if (!value) return '—';
  let date = value.slice(0, 10);
  if (value.endsWith('Z') || /[+-]\d{2}:\d{2}$/.test(value)) {
    const parsed = new Date(value);
    if (Number.isNaN(parsed.valueOf())) return '—';
    date = new Intl.DateTimeFormat('en-CA', { timeZone: TIME_ZONE, year: 'numeric', month: '2-digit', day: '2-digit' }).format(parsed);
  }
  const [year, month, day] = date.split('-');
  return year && month && day ? `${Number(month)}/${Number(day)}/${year}` : '—';
}

function formatLocalDateTime(value: string) {
  return `${formatDate(value)} ${formatClock(value.slice(11, 16))}`;
}

function formatClock(value?: string) {
  if (!value) return '—';
  const [hoursText, minutes = '00'] = value.slice(0, 5).split(':');
  const hours = Number(hoursText);
  if (!Number.isInteger(hours) || hours < 0 || hours > 23) return '—';
  return `${hours % 12 || 12}:${minutes}${hours >= 12 ? 'pm' : 'am'}`;
}

function formatLessonTime(row: OneOnOneLesson) {
  if (row.signatureHistory) return '—';
  const start = formatClock(row.plannedStartAt.slice(11, 16));
  const end = formatClock(row.plannedEndAt.slice(11, 16)).replace(':00', '');
  return `${start}-${end}`;
}

function escapeHtml(value: string | number | undefined | null) {
  return String(value ?? '').replace(/[&<>"']/g,
    char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char] ?? char);
}
