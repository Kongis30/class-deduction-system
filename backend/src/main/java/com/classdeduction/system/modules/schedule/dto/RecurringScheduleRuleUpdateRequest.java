package com.classdeduction.system.modules.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 周期排课规则更新请求：修改规则、替换时段并重新生成未来未确认的计划课次。
 * 注意：packageId 不可变更，规则始终绑定原始课程包。
 */
@Data
@Schema(description = "Recurring schedule rule update request")
public class RecurringScheduleRuleUpdateRequest {

    @Schema(description = "Shared private lesson group ID for the existing schedule")
    private Long privateLessonGroupId;

    @Schema(description = "Updated default classroom ID", example = "1880000000000000003")
    private Long defaultClassroomId;

    @Schema(description = "Rule status: ACTIVE/PAUSED/ENDED", example = "ACTIVE")
    private String ruleStatus;

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
