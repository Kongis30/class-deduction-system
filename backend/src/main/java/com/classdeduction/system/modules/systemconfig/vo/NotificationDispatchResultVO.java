package com.classdeduction.system.modules.systemconfig.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

/**
 * 一次手动分发的执行结果摘要。
 */
@Builder
@Schema(description = "Notification dispatch result")
public record NotificationDispatchResultVO(
        int requestedBatchSize,
        int claimedCount,
        int successCount,
        int failedCount,
        List<Long> successLogIds,
        List<Long> failedLogIds
) {
}
