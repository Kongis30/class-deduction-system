package com.classdeduction.system.modules.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalTime;

/**
 * 周期排课时段请求：定义每周某一天的固定上课时间与课时单位。
 */
@Data
@Schema(description = "Recurring schedule slot request")
public class RecurringScheduleSlotRequest {

    @NotNull(message = "Teacher is required for every slot")
    @Schema(description = "Teacher assigned to this slot")
    private Long teacherId;

    @Schema(description = "Internal delivery mode: IN_PERSON or ONLINE")
    private String deliveryMode;

    @Schema(description = "Classroom assigned to this slot; null for online lessons")
    private Long classroomId;

    @NotBlank(message = "Course/subject name is required for every slot")
    @Schema(description = "Course/subject name taught in this slot, e.g. History, Chemistry")
    private String subjectName;

    @NotNull(message = "Weekday is required")
    @Min(value = 1, message = "Weekday must be between 1 and 7")
    @Max(value = 7, message = "Weekday must be between 1 and 7")
    @Schema(description = "Weekday, 1 for Monday through 7 for Sunday", example = "2")
    private Integer weekday;

    @NotNull(message = "Start time is required")
    @Schema(description = "Planned start time", example = "18:00:00")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    @Schema(description = "Planned end time", example = "20:00:00")
    private LocalTime endTime;

    @NotNull(message = "Planned units are required")
    @Min(value = 1, message = "Planned units must be at least 1")
    @Schema(description = "Planned units where 1 unit = 0.5 hour", example = "4")
    private Integer plannedUnits;

    @NotNull(message = "Allocated units are required for every slot")
    @Min(value = 1, message = "Allocated units must be at least 1")
    @Schema(description = "Total units (1 unit = 0.5h) allocated to this slot", example = "20")
    private Integer allocatedUnits;
}
