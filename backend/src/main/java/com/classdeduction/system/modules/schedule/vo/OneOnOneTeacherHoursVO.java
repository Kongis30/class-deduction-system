package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 消课页老师课时：当前排课版本的已确认消课，以及该老师待处理和未来的计划课时。 */
@Builder
@Schema(description = "Teacher hours with deductions limited to the current schedule version")
public record OneOnOneTeacherHoursVO(
        Long teacherId,
        String teacherName,
        Double allocatedUnits,
        Double deductedUnits,
        Double remainingUnits
) {
}
