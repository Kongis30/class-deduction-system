package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 当前排课版本中单个老师名下的课时汇总。 */
@Builder
@Schema(description = "Teacher-specific hours for the current schedule period")
public record OneOnOneTeacherHoursVO(
        Long teacherId,
        String teacherName,
        Double allocatedUnits,
        Double deductedUnits,
        Double remainingUnits
) {
}
