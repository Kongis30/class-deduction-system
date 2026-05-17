package com.classdeduction.system.modules.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调课请求：支持调时间、换老师、换教室和创建补课。
 */
@Data
@Schema(description = "Schedule adjustment request")
public class ScheduleAdjustmentRequest {

    @NotNull(message = "Occurrence ID is required")
    @Schema(description = "Target occurrence ID", example = "1880000000000000001")
    private Long occurrenceId;

    @NotBlank(message = "Adjustment type is required")
    @Schema(description = "Adjustment type: RESCHEDULE/CHANGE_ROOM/CHANGE_TEACHER/MAKEUP_CREATE", example = "RESCHEDULE")
    private String adjustmentType;

    @Schema(description = "New planned teacher ID", example = "1880000000000000002")
    private Long plannedTeacherId;

    @Schema(description = "New planned classroom ID", example = "1880000000000000003")
    private Long plannedClassroomId;

    @Schema(description = "New planned start time", example = "2026-04-12T18:00:00")
    private LocalDateTime plannedStartAt;

    @Schema(description = "New planned end time", example = "2026-04-12T20:00:00")
    private LocalDateTime plannedEndAt;

    @Min(value = 1, message = "Planned units must be at least 1")
    @Schema(description = "New planned units", example = "4")
    private Integer plannedUnits;

    @Schema(description = "Operator user ID", example = "1880000000000000010")
    private Long adjustedBy;

    @Schema(description = "Adjustment reason")
    private String reason;
}
