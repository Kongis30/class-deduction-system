package com.classdeduction.system.modules.systemconfig.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 触发规则响应。
 */
@Builder
@Schema(description = "Notification trigger rule response")
public record NotificationTriggerRuleVO(
        Long id,
        String triggerType,
        String triggerName,
        Long templateId,
        String templateName,
        Boolean enabled,
        String recipientType,
        String customRecipients,
        Integer thresholdValue,
        String remark,
        String updatedAt
) {
}