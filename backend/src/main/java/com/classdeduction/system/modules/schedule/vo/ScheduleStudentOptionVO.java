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
    /** 左侧栏的一条可排课单位；共享课组以主课包为入口并携带全部成员。 */
    public record CourseGradeOption(
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
        public CourseGradeOption(String courseName, String gradeBand, boolean hasSchedule) {
            this(courseName, gradeBand, hasSchedule, null, "ACTIVE", null, 1, null, null);
        }
    }
}
