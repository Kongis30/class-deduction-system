package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 调课结果响应。
 */
@Builder
@Schema(description = "Schedule adjustment result")
public record ScheduleAdjustmentResultVO(
        Long adjustmentLogId,
        Long affectedOccurrenceId,
        Long createdOccurrenceId,
        String adjustmentType
) {
}
