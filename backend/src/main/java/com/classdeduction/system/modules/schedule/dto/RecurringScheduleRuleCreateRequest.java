package com.classdeduction.system.modules.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    @Schema(description = "Default primary teacher ID", example = "1880000000000000002")
    private Long primaryTeacherId;

    @Schema(description = "Default classroom ID", example = "1880000000000000003")
    private Long defaultClassroomId;

    @NotNull(message = "Rule start date is required")
    @Schema(description = "Rule start date", example = "2026-04-07")
    private LocalDate startDate;

    @Schema(description = "Rule end date", example = "2026-08-31")
    private LocalDate endDate;

    @Min(value = 1, message = "Generation horizon must be at least 1 day")
    @Max(value = 180, message = "Generation horizon must be at most 180 days")
    @Schema(description = "How many days of occurrences should be generated ahead", example = "90")
    private Integer generationHorizonDays = 90;

    @Schema(description = "Rule notes")
    private String notes;

    @Valid
    @NotEmpty(message = "At least one recurring slot is required")
    @Schema(description = "Recurring weekly slots")
    private List<RecurringScheduleSlotRequest> slots;
}
