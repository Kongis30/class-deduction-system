package com.classdeduction.system.modules.systemconfig.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 通知模板响应。
 */
@Builder
@Schema(description = "Notification template response")
public record NotificationTemplateVO(
        Long id,
        String templateCode,
        String templateName,
        String channelType,
        String subjectTemplate,
        String bodyTemplate,
        String status,
        String remark
) {
}
