package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 排课页面的轻量学生选项，仅返回列表需要展示的字段。 */
@Schema(description = "Schedule student selection option")
public record ScheduleStudentOptionVO(
        Long id,
        String studentName
) {
}
