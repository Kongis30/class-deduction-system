package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 消课页老师课时：三个数值均属于最后一次实际调整排课后的同一周期。 */
@Builder
@Schema(description = "Teacher-level package hours")
public record OneOnOneTeacherHoursVO(
        Long teacherId,
        String teacherName,
        Double allocatedUnits,
        Double deductedUnits,
        @Schema(description = "Current-period allocation minus finally deducted hours, including past unconfirmed lessons")
        Double remainingUnits
) {
}
