import type { OneOnOneLesson, OneOnOnePackage, OneOnOneStudent } from '@/api/one-on-one';

type SheetMode = 'parent' | 'teacher';

interface SheetGroup {
  subject: string;
  teacher: string;
  teacherKey: string;
  classTime: string;
  hours: number;
  startDate: string;
  rows: OneOnOneLesson[];
}

const TIME_ZONE = 'America/New_York';
const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

/** 生成独立的签字表预览文档；Schedule 与消课页共用同一份 1-on-1 课程数据契约。 */
export function renderSignatureSheetDocument(
  student: OneOnOneStudent,
  coursePackage: OneOnOnePackage,
  mode: SheetMode,
): string {
  const lessons = coursePackage.lessons.filter(lesson => lesson.planningStatus !== 'CANCELLED');
  const title = mode === 'parent' ? 'Student Signature Sheet' : 'Teacher Signature Sheets';
  const content = mode === 'parent'
    ? renderParentSheet(student, coursePackage, lessons)
    : renderTeacherSheets(student, coursePackage, lessons);

  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(title)} - ${escapeHtml(student.studentName)}</title>
<style>
  * { box-sizing: border-box; }
  body { margin: 0; padding: 24px; color: #111; font-family: Arial, Helvetica, sans-serif; background: #f4f6f8; }
  .toolbar { display: flex; justify-content: flex-end; gap: 8px; max-width: 1000px; margin: 0 auto 16px; }
  .toolbar button { padding: 9px 16px; border: 1px solid #c8d3df; border-radius: 5px; background: #fff; color: #24364b; cursor: pointer; font-size: 14px; }
  .toolbar button.primary { border-color: #3688ea; background: #3688ea; color: #fff; }
  .sheet { max-width: 1000px; margin: 0 auto 20px; padding: 22px 24px; background: #fff; box-shadow: 0 2px 12px #1b2a3a1c; page-break-after: always; }
  .sheet:last-child { page-break-after: auto; }
  .meta { display: flex; flex-wrap: wrap; gap: 8px 28px; margin-bottom: 10px; }
  .meta p { margin: 0; font-size: 16px; line-height: 1.35; }
  .hours { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; width: 100%; }
  .hours section { display: grid; gap: 5px; padding: 9px 12px; border: 1px solid #777; border-radius: 4px; }
  .hours section:last-child { border-left: 3px solid #222; background: #f2f2f2; }
  .subject-list { display: grid; gap: 5px; margin: 14px 0 24px; }
  .subject-line { display: grid; grid-template-columns: 1.2fr 1fr 1.45fr 1fr 1fr; gap: 12px; font-size: 14px; font-weight: 600; }
  .teacher-meta { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px 24px; margin-bottom: 28px; }
  .teacher-meta p { margin: 0; font-size: 15px; font-weight: 600; }
  .teacher-meta p:first-child { grid-column: 1 / -1; }
  table { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: 14px; }
  th, td { height: 28px; padding: 4px 6px; border: 1px solid #111; text-align: left; vertical-align: middle; overflow-wrap: anywhere; }
  th { font-weight: 700; }
  h1 { margin: 0 0 16px; font-size: 20px; }
  .empty { padding: 24px; border: 1px solid #bbb; text-align: center; color: #555; }
  @media print {
    @page { size: letter portrait; margin: 0.5in; }
    body { padding: 0; background: #fff; }
    .toolbar { display: none; }
    .sheet { max-width: none; margin: 0; padding: 0; box-shadow: none; }
  }
</style></head><body>
<div class="toolbar"><button onclick="window.close()">Close</button><button class="primary" onclick="window.print()">Print</button></div>
${content}
</body></html>`;
}

function renderParentSheet(student: OneOnOneStudent, coursePackage: OneOnOnePackage, lessons: OneOnOneLesson[]) {
  const subjectGroups = groupBySubjectAndTeacher(lessons);
  const participants = coursePackage.participants?.map(item => item.studentName).filter(Boolean).join(' / ');
  const groupTag = (coursePackage.participantCount ?? 1) > 1
    ? `<p><strong>Group:</strong> 1-on-${escapeHtml(String(coursePackage.participantCount))}${participants ? ` · ${escapeHtml(participants)}` : ''}</p>`
    : '';
  const subjectLines = subjectGroups.map(group => `<div class="subject-line">
    <span><strong>Lesson name:</strong> ${escapeHtml(group.subject)}</span>
    <span><strong>Teacher:</strong> ${escapeHtml(group.teacher)}</span>
    <span><strong>Classtime:</strong> ${escapeHtml(group.classTime)}</span>
    <span><strong>Allocated Hours:</strong> ${formatHours(group.hours)}</span>
    <span><strong>Starting Date:</strong> ${escapeHtml(group.startDate)}</span>
  </div>`).join('');
  const rows = lessons.map(row => `<tr>
    <td>${escapeHtml(subjectLabel(row, coursePackage))}</td>
    <td>${escapeHtml(teacherName(row))}</td>
    <td>${escapeHtml(formatDate(row.plannedStartAt))}</td><td></td>
  </tr>`).join('');

  return `<main class="sheet">
    <h1>Student Signature Sheet</h1>
    <div class="meta"><p><strong>Student:</strong> ${escapeHtml(student.studentName)}</p>${groupTag}
      <div class="hours"><section>
        <p><strong>Total hours:</strong> ${formatHours(coursePackage.totalUnits)}</p>
        <p><strong>Purchased Hours:</strong> ${formatHours(coursePackage.purchasedUnits ?? coursePackage.totalUnits)}</p>
        <p><strong>Bonus Hours:</strong> ${formatHours(coursePackage.bonusUnits ?? 0)}</p>
      </section><section>
        <p><strong>Already deducted:</strong> ${formatHours(coursePackage.consumedUnits)}</p>
        <p><strong>Remaining:</strong> ${formatHours(coursePackage.remainingUnits)}</p>
      </section></div>
    </div>
    <div class="subject-list">${subjectLines}</div>
    ${lessons.length ? `<table><thead><tr><th>Lesson name</th><th>Teacher</th><th>Date</th><th>Signature</th></tr></thead><tbody>${rows}</tbody></table>` : '<div class="empty">No scheduled lessons are available for this course.</div>'}
  </main>`;
}

function renderTeacherSheets(student: OneOnOneStudent, coursePackage: OneOnOnePackage, lessons: OneOnOneLesson[]) {
  const groups = groupByTeacherSchedule(lessons, coursePackage);
  if (!groups.length) return '<main class="sheet"><h1>Teacher Signature Sheets</h1><div class="empty">No scheduled lessons are available for this course.</div></main>';
  return groups.map((group, index) => `<main class="sheet">
    <h1>Teacher Signature Sheet${groups.length > 1 ? ` ${index + 1}` : ''}</h1>
    <div class="teacher-meta">
      <p><strong>Student:</strong> ${escapeHtml(student.studentName)}</p>
      <p><strong>Lesson name:</strong> ${escapeHtml(group.subject)}</p>
      <p><strong>Teacher:</strong> ${escapeHtml(group.teacher)}</p>
      <p><strong>Classtime:</strong> ${escapeHtml(group.classTime)}</p>
      <p><strong>Allocated Hours:</strong> ${formatHours(group.hours)}</p>
      <p><strong>Starting Date:</strong> ${escapeHtml(group.startDate)}</p>
    </div>
    <table><thead><tr><th>Lesson name</th><th>Teacher</th><th>Date</th><th>Signature</th></tr></thead><tbody>
      ${group.rows.map(row => `<tr><td>${escapeHtml(group.subject)}</td><td>${escapeHtml(group.teacher)}</td><td>${escapeHtml(formatDate(row.plannedStartAt))}</td><td></td></tr>`).join('')}
    </tbody></table>
  </main>`).join('');
}

function groupBySubjectAndTeacher(lessons: OneOnOneLesson[]): SheetGroup[] {
  const groups = new Map<string, SheetGroup>();
  for (const row of lessons) {
    const subject = subjectLabel(row);
    const teacher = teacherName(row);
    const teacherKey = normalizeKey(row.plannedTeacherId ?? row.actualTeacherId ?? teacher);
    const key = `${normalizeKey(subject)}::${teacherKey}`;
    const existing = groups.get(key);
    if (existing) {
      existing.rows.push(row);
      existing.hours += Number(row.plannedUnits ?? 0);
    } else {
      groups.set(key, { subject, teacher, teacherKey, classTime: '', hours: Number(row.plannedUnits ?? 0), startDate: formatDate(row.plannedStartAt), rows: [row] });
    }
  }
  return Array.from(groups.values()).map(group => ({
    ...group,
    classTime: summarizeClassTime(group.rows),
    startDate: formatDate(group.rows[0]?.plannedStartAt),
  }));
}

function groupByTeacherSchedule(lessons: OneOnOneLesson[], coursePackage: OneOnOnePackage): SheetGroup[] {
  const groups = new Map<string, SheetGroup>();
  for (const row of lessons) {
    const subject = subjectLabel(row, coursePackage);
    const teacher = teacherName(row);
    const teacherKey = normalizeKey(row.plannedTeacherId ?? row.actualTeacherId ?? teacher);
    const timeKey = timeOfDay(row) || 'To be scheduled';
    const key = `${normalizeKey(subject)}::${teacherKey}::${timeKey}`;
    const existing = groups.get(key);
    if (existing) {
      existing.rows.push(row);
      existing.hours += Number(row.plannedUnits ?? 0);
    } else {
      groups.set(key, { subject, teacher, teacherKey, classTime: timeKey, hours: Number(row.plannedUnits ?? 0), startDate: formatDate(row.plannedStartAt), rows: [row] });
    }
  }
  return Array.from(groups.values()).map(group => {
    const rows = [...group.rows].sort((left, right) => Date.parse(left.plannedStartAt) - Date.parse(right.plannedStartAt));
    return { ...group, rows, classTime: summarizeClassTime(rows), startDate: formatDate(rows[0]?.plannedStartAt) };
  });
}

function summarizeClassTime(rows: OneOnOneLesson[]) {
  const byTime = new Map<string, Set<number>>();
  for (const row of rows) {
    const time = timeOfDay(row);
    const weekday = weekdayOf(row.plannedStartAt);
    if (!time || weekday == null) continue;
    const days = byTime.get(time) ?? new Set<number>();
    days.add(weekday);
    byTime.set(time, days);
  }
  if (!byTime.size) return 'To be scheduled';
  return Array.from(byTime.entries()).map(([time, days]) =>
    `${Array.from(days).sort((left, right) => left - right).map(day => WEEKDAYS[day]).join('&')} ${time}`,
  ).join(', ');
}

function subjectLabel(row: OneOnOneLesson, coursePackage?: OneOnOnePackage) {
  return row.subjectName?.trim() || coursePackage?.subjectName?.trim() || coursePackage?.courseProductName?.trim() || coursePackage?.packageName || 'Unnamed lesson';
}

function teacherName(row: OneOnOneLesson) {
  return (row.plannedTeacherName || row.actualTeacherName)?.split(/\r?\n/)[0]?.trim() || 'To be assigned';
}

function formatHours(value?: number) {
  return `${Number(value ?? 0).toFixed(1)}h`;
}

function formatDate(value?: string) {
  if (!value || Number.isNaN(Date.parse(value))) return '—';
  const parts = new Intl.DateTimeFormat('en-US', { timeZone: TIME_ZONE, month: 'numeric', day: 'numeric', year: 'numeric' }).formatToParts(new Date(value));
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find(item => item.type === type)?.value ?? '';
  return `${part('month')}/${part('day')}/${part('year')}`;
}

function timeOfDay(row: OneOnOneLesson) {
  if (!row.plannedStartAt || !row.plannedEndAt) return '';
  return `${formatTime(row.plannedStartAt)}-${formatTime(row.plannedEndAt)}`;
}

function formatTime(value: string) {
  const parts = new Intl.DateTimeFormat('en-US', { timeZone: TIME_ZONE, hour: 'numeric', minute: '2-digit', hour12: true }).formatToParts(new Date(value));
  const hour = parts.find(item => item.type === 'hour')?.value ?? '';
  const minute = parts.find(item => item.type === 'minute')?.value ?? '00';
  const period = parts.find(item => item.type === 'dayPeriod')?.value.toLowerCase() ?? '';
  return `${hour}:${minute === '00' ? '00' : minute}${period}`.replace(':00', '');
}

function weekdayOf(value: string) {
  if (!value || Number.isNaN(Date.parse(value))) return null;
  const weekday = new Intl.DateTimeFormat('en-US', { timeZone: TIME_ZONE, weekday: 'short' }).format(new Date(value));
  const index = WEEKDAYS.indexOf(weekday);
  return index < 0 ? null : index;
}

function normalizeKey(value: string | number) {
  return String(value).trim().toLocaleLowerCase();
}

function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char] ?? char);
}
