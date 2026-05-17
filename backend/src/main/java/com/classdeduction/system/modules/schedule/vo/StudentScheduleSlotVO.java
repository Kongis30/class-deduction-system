package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Student's occupied schedule slot for calendar display.
 */
@Builder
@Schema(description = "Student schedule slot")
public record StudentScheduleSlotVO(
        @Schema(description = "Occurrence ID")
        Long occurrenceId,
        @Schema(description = "Weekday 1=Mon..7=Sun")
        Integer weekday,
        @Schema(description = "Start time HH:mm")
        String startTime,
        @Schema(description = "End time HH:mm")
        String endTime,
        @Schema(description = "Planned units")
        Integer plannedUnits,
        @Schema(description = "Course/package label for display")
        String courseLabel,
        @Schema(description = "Package ID")
        Long packageId,
        @Schema(description = "Planning status")
        String planningStatus
) {
}
