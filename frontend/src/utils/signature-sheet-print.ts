import type { OneOnOneLesson, OneOnOnePackage, OneOnOneStudent } from '@/api/one-on-one';
import type { SchedulePrintRevision } from '@/api/schedule';
import { isSignatureLesson } from '@/utils/one-on-one-signature-data';

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
  const lessons = coursePackage.lessons.filter(row => (isSignatureLesson(row)
    || (row.confirmStage === 'CONFIRMED' && Number(row.deductedUnits ?? 0) > 0))
    && (mode !== 'teacher' || !teacherKey || teacherKey === '__all__'
      || teacherIdentity(row) === teacherKey));
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
  .lesson-status { color: #47566a; font-size: 12px; }
  .print-warning { max-width: 1000px; margin: 0 auto 12px; padding: 12px; border: 1px solid #be6d00; background: #fff2d8; color: #6b3900; }
  .lesson-table td { font-size: 12px; }
  .lesson-table th { font-size: 12px; }
  .empty { padding: 24px; border: 1px solid #bbb; text-align: center; color: #555; }
  @media print {
    @page { size: letter portrait; margin: 0.5in; }
    body { padding: 0; background: #fff; }
    .toolbar { display: none; }
    .sheet { max-width: none; margin: 0; padding: 0; box-shadow: none; }
  }
</style></head><body>
<div class="toolbar"><button onclick="window.close()">Close</button><button class="primary" onclick="window.print()" ${warnings.length ? 'disabled title="Resolve the missing print data and retry"' : ''}>Print</button></div>
${warnings.length ? `<div class="print-warning"><strong>Print data is incomplete.</strong> ${warnings.map(escapeHtml).join(' ')}</div>` : ''}
${content}
</body></html>`;
}


function renderParentSheet(
  student: OneOnOneStudent,
  coursePackage: OneOnOnePackage,
  lessons: OneOnOneLesson[],
  revisions: SchedulePrintRevision[],
) {
  const participantNames = studentDisplayName(student, coursePackage);
  const completed = lessons.filter(isHistoricalLesson);
  const planned = lessons.filter(row => !isHistoricalLesson(row));
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
    ${renderLessonSection('Completed lesson history', completed, revisions)}
    ${renderLessonSection('Scheduled and pending lessons', planned, revisions)}
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
    ${renderRevisionHistory(revisions)}
    ${renderLessonSection('Completed lesson history', group.rows.filter(isHistoricalLesson), revisions)}
    ${renderLessonSection('Scheduled and pending lessons', group.rows.filter(row => !isHistoricalLesson(row)), revisions)}
  </main>`).join('');
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
          · ${escapeHtml(slot.subjectName || 'Unnamed lesson')}
          · ${escapeHtml(slot.teacherName || 'To be assigned')}
          · ${escapeHtml(slot.deliveryMode === 'ONLINE' ? 'Online' : slot.classroomName || 'Room not set')}
          · ${formatHours(slot.allocatedHours)}
        </p>`).join('')
      : '<p class="lesson-status">This older schedule has no preserved weekly slot details.</p>';
    return `<section class="revision">
      <div class="revision-heading"><span>${heading} #${revision.revisionNumber}</span><span>${escapeHtml(changedAt)}</span></div>
      <div class="revision-meta">Rule start: ${escapeHtml(startDate)}
        · Starting: ${revision.startingHours == null ? '—' : formatHours(revision.startingHours)}
        · Allocated: ${formatHours(revision.allocatedHours)}</div>
      ${slots}
    </section>`;
  }).join('')}`;
}

/** 已确认课次与有凭据的旧签字行独立列出；待处理课次保留在当前排课中。 */
function renderLessonSection(title: string, rows: OneOnOneLesson[], revisions: SchedulePrintRevision[]) {
  const versionByRule = new Map(revisions.map(revision => [String(revision.ruleId), revision.revisionNumber]));
  const sorted = [...rows].sort((left, right) =>
    left.plannedStartAt.localeCompare(right.plannedStartAt)
    || String(left.occurrenceId).localeCompare(String(right.occurrenceId)));
  if (!sorted.length) return `<h2>${escapeHtml(title)}</h2><p class="lesson-status">No entries.</p>`;
  return `<h2>${escapeHtml(title)}</h2>
    <table class="lesson-table"><thead><tr>
      <th>Date / time</th><th>Lesson</th><th>Teacher</th><th>Schedule</th><th>Status / hours</th><th>Signature</th>
    </tr></thead><tbody>${sorted.map(row => {
      const versionNumber = row.scheduleRuleId == null ? null : versionByRule.get(String(row.scheduleRuleId));
      const versionLabel = row.signatureHistory ? 'Imported history'
        : versionNumber == null ? 'Earlier schedule' : `Version ${versionNumber}`;
      return `<tr>
        <td>${escapeHtml(formatDate(row.plannedStartAt))}<div class="lesson-status">${escapeHtml(formatLessonTime(row))}</div></td>
        <td>${escapeHtml(subjectLabel(row))}</td>
        <td>${escapeHtml(teacherName(row))}</td>
        <td>${escapeHtml(versionLabel)}</td>
        <td>${escapeHtml(lessonStatus(row))}</td>
        <td>${escapeHtml(row.historicalSignatureText ?? '')}</td>
      </tr>`;
    }).join('')}</tbody></table>`;
}

function isHistoricalLesson(row: OneOnOneLesson) {
  return row.signatureHistory === true || row.confirmStage === 'CONFIRMED';
}

function lessonStatus(row: OneOnOneLesson) {
  if (row.signatureHistory) return 'Verified legacy signature';
  if (row.confirmStage === 'CONFIRMED') {
    const result = row.resultStatusName || row.resultStatus || 'Completed';
    return Number(row.deductedUnits ?? 0) > 0
      ? `${result} · ${formatHours(row.deductedUnits)} deducted`
      : result;
  }
  if (row.confirmStage === 'PENDING_ADMIN') return 'Awaiting admin review';
  return 'Scheduled / awaiting teacher';
}

function groupByTeacher(lessons: OneOnOneLesson[]) {
  const groups = new Map<string, { teacher: string; rows: OneOnOneLesson[] }>();
  for (const row of lessons) {
    const teacher = teacherName(row);
    const key = teacherIdentity(row);
    const group = groups.get(key);
    if (group) group.rows.push(row);
    else groups.set(key, { teacher, rows: [row] });
  }
  return [...groups.values()];
}

function teacherIdentity(row: OneOnOneLesson) {
  const teacher = teacherName(row);
  return String(row.confirmStage === 'CONFIRMED'
    ? row.actualTeacherId ?? row.plannedTeacherId ?? teacher
    : row.plannedTeacherId ?? teacher).trim().toLowerCase();
}

function studentDisplayName(student: OneOnOneStudent, coursePackage: OneOnOnePackage): string {
  const participants = (coursePackage.participants ?? [])
    .map(item => item.studentName?.trim())
    .filter((name): name is string => Boolean(name));
  return participants.length > 1 ? participants.join(' / ') : student.studentName;
}

function subjectLabel(row: OneOnOneLesson) {
  return row.subjectName?.trim() || 'Unnamed lesson';
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
  if (row.signatureHistory) return 'Time not recorded';
  return `${formatClock(row.plannedStartAt.slice(11, 16))}–${formatClock(row.plannedEndAt.slice(11, 16))}`;
}

function escapeHtml(value: string | number | undefined | null) {
  return String(value ?? '').replace(/[&<>"']/g,
    char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char] ?? char);
}
