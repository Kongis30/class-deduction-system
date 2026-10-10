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
