package com.classdeduction.system.modules.systemconfig.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 邮件发送方配置响应。
 */
@Builder
@Schema(description = "Notification config response")
public record NotificationConfigVO(
        Long id,
        Boolean enabled,
        String fromAddress,
        String fromName,
        Boolean strictAllowlist,
        String allowedRecipients,
        Integer lowBalanceThreshold,
        String lowBalanceRecipients,
        String updatedAt
) {
}