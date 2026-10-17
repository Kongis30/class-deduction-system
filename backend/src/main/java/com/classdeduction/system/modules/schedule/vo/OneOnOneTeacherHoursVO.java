package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 消课页老师课时：按老师拆分分配、已扣、待处理和未来有效排课。 */
@Builder
@Schema(description = "Teacher-level package hours")
public record OneOnOneTeacherHoursVO(
        Long teacherId,
        String teacherName,
        Double allocatedUnits,
        Double deductedUnits,
        @Schema(description = "Past lesson hours for this teacher awaiting final admin processing")
        Double pendingDeductionUnits,
        @Schema(description = "Hours in this teacher's valid future scheduled lessons")
        Double remainingUnits
) {
}
