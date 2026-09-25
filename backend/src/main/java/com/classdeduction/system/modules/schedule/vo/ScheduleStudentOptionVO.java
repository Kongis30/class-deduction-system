package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 排课页面的轻量学生选项，仅返回列表需要展示的字段。 */
@Schema(description = "Schedule student selection option")
public record ScheduleStudentOptionVO(
        Long id,
        String studentName,
        List<CourseGradeOption> courseGrades
) {
    /** 左侧栏展示学生所属的有效课程与年级组合。 */
    public record CourseGradeOption(String courseName, String gradeBand) {
    }
}
