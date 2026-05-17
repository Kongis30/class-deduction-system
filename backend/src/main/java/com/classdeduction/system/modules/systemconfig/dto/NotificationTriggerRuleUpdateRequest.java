package com.classdeduction.system.modules.systemconfig.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 触发规则更新请求。
 */
@Data
@Schema(description = "Trigger rule update request")
public class NotificationTriggerRuleUpdateRequest {

    @Schema(description = "Enable this trigger")
    private Boolean enabled;

    @Schema(description = "Email template ID assigned to this trigger")
    private Long templateId;

    @Schema(description = "Recipient type: GUARDIAN_EMAIL / CUSTOM_EMAIL")
    private String recipientType;

    @Schema(description = "Custom recipients JSON array string")
    private String customRecipients;

    @Min(value = 1, message = "Threshold must be at least 1 unit (0.5h)")
    @Schema(description = "Threshold value in 0.5h units (LOW_BALANCE trigger only)")
    private Integer thresholdValue;

    @Schema(description = "Remark")
    private String remark;
}