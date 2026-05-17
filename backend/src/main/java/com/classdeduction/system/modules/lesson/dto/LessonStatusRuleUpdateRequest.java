package com.classdeduction.system.modules.lesson.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 课次状态规则更新请求：不允许变更状态编码，只允许维护行为配置。
 */
@Data
@Schema(description = "Lesson status rule update request")
public class LessonStatusRuleUpdateRequest {

    @NotBlank(message = "Status name is required")
    @Size(max = 64, message = "Status name must not exceed 64 characters")
    @Schema(description = "Status name", example = "Completed")
    private String statusName;

    @NotNull(message = "Default deduct flag is required")
    @Schema(description = "Default deduct flag", example = "true")
    private Boolean defaultShouldDeduct;

    @NotBlank(message = "Deduct mode is required")
    @Schema(description = "Deduct mode: NONE / ACTUAL_UNITS / PLANNED_UNITS / FIXED_UNITS", example = "ACTUAL_UNITS")
    private String deductMode;

    @Schema(description = "Fixed units, required only when deductMode is FIXED_UNITS", example = "4")
    private Integer fixedUnits;

    @NotNull(message = "Default extend flag is required")
    @Schema(description = "Default extend-end-date flag", example = "false")
    private Boolean defaultShouldExtend;

    @NotNull(message = "Manual override flag is required")
    @Schema(description = "Allow manual override", example = "true")
    private Boolean allowManualOverride;

    @Schema(description = "Rule status: ACTIVE / DISABLED", example = "ACTIVE")
    private String status;

    @Size(max = 500, message = "Remark must not exceed 500 characters")
    @Schema(description = "Remark")
    private String remark;
}
