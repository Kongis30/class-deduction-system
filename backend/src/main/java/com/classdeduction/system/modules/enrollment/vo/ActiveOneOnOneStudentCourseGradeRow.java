package com.classdeduction.system.modules.enrollment.vo;

/** 有效 1 对 1 课时包按学生、课程与年级展开后的查询行。 */
public record ActiveOneOnOneStudentCourseGradeRow(
        Long studentId,
        String studentName,
        String courseName,
        String gradeBand
) {
}
