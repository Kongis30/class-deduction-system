package com.classdeduction.system.modules.enrollment.vo;

/** 在读或停课的私教课包按学生、课程与年级展开后的查询行。 */
public record OneOnOneScheduleCourseRow(
        Long studentId,
        String studentName,
        String courseName,
        String gradeBand,
        boolean hasSchedule,
        Long packageId,
        String packageStatus,
        Long privateLessonGroupId,
        Integer participantCount,
        String participantNames,
        String participantStudentIds
) {
    public OneOnOneScheduleCourseRow(Long studentId, String studentName, String courseName,
                                               String gradeBand, boolean hasSchedule) {
        this(studentId, studentName, courseName, gradeBand, hasSchedule, null, "ACTIVE", null, 1, null, null);
    }
}
