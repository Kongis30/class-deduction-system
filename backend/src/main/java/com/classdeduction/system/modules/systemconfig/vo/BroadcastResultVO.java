package com.classdeduction.system.modules.systemconfig.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

/**
 * 群发结果。
 */
@Builder
@Schema(description = "Broadcast result")
public record BroadcastResultVO(
        int totalRecipients,
        int queuedCount,
        List<String> recipientPreview
) {
}