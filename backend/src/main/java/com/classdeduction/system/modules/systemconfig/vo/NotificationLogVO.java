package com.classdeduction.system.modules.systemconfig.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 通知日志响应。
 */
@Builder
@Schema(description = "Notification log response")
public record NotificationLogVO(
        Long id,
        String channelType,
        Long templateId,
        Long studentId,
        Long packageId,
        Long lessonRecordId,
        String recipient,
        String sendStatus,
        String requestPayload,
        String responsePayload,
        String errorMessage,
        LocalDateTime scheduledAt,
        LocalDateTime sentAt,
        Integer retryCount
) {
}
