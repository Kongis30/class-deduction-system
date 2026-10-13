import type { EntityId } from '@/types/domain';
import type { OneOnOneLesson, OneOnOnePackage, OneOnOneStudent, OneOnOneTeacher, PrivateSignatureHistory } from '@/api/one-on-one';

/** 签字表按学生课包汇总各老师的课次；消课列表仍保持老师分组。 */
export function findSignaturePackage(
  teachers: OneOnOneTeacher[], studentId: EntityId, packageId: EntityId,
): { student: OneOnOneStudent; coursePackage: OneOnOnePackage } | null {
  const matches = teachers.flatMap(teacher => teacher.students)
    .flatMap(student => student.packages.map(coursePackage => ({ student, coursePackage })))
    .filter(({ student, coursePackage }) =>
      String(student.studentId) === String(studentId)
      && String(coursePackage.packageId) === String(packageId));
  if (!matches.length) return null;

  const lessons = new Map<string, OneOnOneLesson>();
  for (const { coursePackage } of matches) {
    for (const lesson of coursePackage.lessons) {
      lessons.set(String(lesson.occurrenceId), lesson);
    }
  }
  return {
    student: matches[0].student,
    coursePackage: {
      ...matches[0].coursePackage,
      lessons: Array.from(lessons.values()).sort((left, right) =>
        Date.parse(left.plannedStartAt) - Date.parse(right.plannedStartAt)
        || String(left.occurrenceId).localeCompare(String(right.occurrenceId))),
    },
  };
}

/** 请假和取消课次留在消课审计中，不进入待签字的实际课表。 */
export function isSignatureLesson(lesson: OneOnOneLesson): boolean {
  return lesson.planningStatus !== 'CANCELLED'
    && !['FORMAL_LEAVE', 'TEMP_CANCEL', 'TEACHER_CANCEL', 'NO_SHOW', 'CANCELLED']
      .includes(lesson.resultStatus ?? '');
}

/** 两个打印入口共用课次范围与顺序：已保存的历史课次和当前未来排课按日期合并。 */
export function signatureLessonRows(coursePackage: OneOnOnePackage): OneOnOneLesson[] {
  return coursePackage.lessons.filter(isSignatureLesson).sort((left, right) =>
    left.plannedStartAt.localeCompare(right.plannedStartAt)
    || String(left.occurrenceId).localeCompare(String(right.occurrenceId)));
}

/** 打印表中的状态来自课次自身，不能用当前排课规则推断历史课次的状态。 */
export function signatureLessonStatus(row: OneOnOneLesson): string {
  if (row.signatureHistory) return 'Verified legacy signature';
  if (row.confirmStage === 'CONFIRMED') {
    const result = row.resultStatusName || row.resultStatus || 'Completed';
    return Number(row.deductedUnits ?? 0) > 0
      ? `${result} · ${Number(row.deductedUnits).toFixed(1)}h deducted`
      : result;
  }
  if (row.confirmStage === 'PENDING_ADMIN') return 'Awaiting admin review';
  if (row.confirmStage === 'REJECTED') return 'Rejected';
  return 'Scheduled / awaiting teacher';
}

/** 旧数据缺少科目时沿用消课打印页的课包名称兜底。 */
export function signatureSubjectName(row: OneOnOneLesson | undefined, coursePackage: OneOnOnePackage | null): string {
  return row?.subjectName?.trim()
    || coursePackage?.subjectName?.trim()
    || coursePackage?.courseProductName?.trim()
    || coursePackage?.packageName
    || 'Unnamed lesson';
}

/** 纸质签字表的历史日期仅用于打印展示，不成为可消课、占用老师或扣课时的正式课次。 */
export function withSignatureHistory(
  coursePackage: OneOnOnePackage, history: PrivateSignatureHistory[],
): OneOnOnePackage {
  const historicalLessons: OneOnOneLesson[] = history.map(row => {
    // 17:00 UTC 在纽约冬夏令时均为同一自然日；历史来源没有具体上课时间。
    const plannedStartAt = `${row.lessonDate}T17:00:00Z`;
    return {
      occurrenceId: `history:${row.id}`,
      lessonNo: `HISTORY-${row.id}`,
      packageId: row.packageId,
      plannedTeacherId: row.teacherId,
      plannedTeacherName: row.teacherName,
      actualTeacherId: row.teacherId,
      actualTeacherName: row.teacherName,
      subjectName: row.subjectName,
      plannedStartAt,
      plannedEndAt: new Date(Date.parse(plannedStartAt) + row.plannedHours * 3_600_000).toISOString(),
      plannedUnits: row.plannedHours,
      planningStatus: 'HISTORICAL',
      resultStatus: 'COMPLETED',
      confirmStage: 'CONFIRMED',
      historicalSignatureText: row.signatureText,
      signatureHistory: true,
    };
  });
  return {
    ...coursePackage,
    lessons: [...coursePackage.lessons, ...historicalLessons]
      .sort((left, right) => Date.parse(left.plannedStartAt) - Date.parse(right.plannedStartAt)
        || String(left.occurrenceId).localeCompare(String(right.occurrenceId))),
  };
}
