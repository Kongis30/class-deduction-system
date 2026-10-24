package com.classdeduction.system.modules.systemconfig.vo;

import java.time.LocalDate;

/** 生日提醒中可展示的学生资料；birthdayDate 是本周发生的日期，不包含出生年份。 */
public record BirthdayStudentMessageVO(
        String studentId,
        String studentName,
        String grade,
        String campusName,
        LocalDate birthdayDate
) {
}
