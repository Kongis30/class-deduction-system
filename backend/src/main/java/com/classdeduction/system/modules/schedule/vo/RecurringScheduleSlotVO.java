package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalTime;

/**
 * 周期排课时段响应。
 */
@Builder
@Schema(description = "Recurring schedule slot response")
public record RecurringScheduleSlotVO(
        Long id,
        Integer weekday,
        LocalTime startTime,
        LocalTime endTime,
        Integer plannedUnits,
        String slotStatus
) {
}
