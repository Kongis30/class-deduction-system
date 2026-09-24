package com.classdeduction.system.modules.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 周期排课规则创建请求：一次创建规则、时段并生成计划课次。
 */
@Data
@Schema(description = "Recurring schedule rule create request")
public class RecurringScheduleRuleCreateRequest {

    @NotNull(message = "Package ID is required")
    @Schema(description = "Course package ID", example = "1880000000000000001")
    private Long packageId;

    @Schema(description = "Shared private lesson group ID for 1-on-2 or 1-on-3 schedules")
    private Long privateLessonGroupId;

    @Schema(description = "Default classroom ID", example = "1880000000000000003")
    private Long defaultClassroomId;

    @NotNull(message = "Rule start date is required")
    @Schema(description = "Rule start date", example = "2026-04-07")
    private LocalDate startDate;

    @Schema(description = "Rule end date", example = "2026-08-31")
    private LocalDate endDate;

    @Schema(description = "Rule notes")
    private String notes;

    @Valid
    @NotEmpty(message = "At least one recurring slot is required")
    @Schema(description = "Recurring weekly slots")
    private List<RecurringScheduleSlotRequest> slots;
}
