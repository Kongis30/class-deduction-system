package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 排课规则更新的结果汇总。
 */
@Schema(description = "Schedule rule update result")
public record ScheduleRuleUpdateResultVO(
        Long ruleId,
        Integer newSlotCount,
        Integer preservedOccurrenceCount,
        Integer deletedOccurrenceCount,
        Integer generatedOccurrenceCount,
        Integer confirmedOccurrenceCount,
        LocalDateTime firstGeneratedStartAt,
        LocalDateTime lastGeneratedEndAt,
        Integer reclaimedOccurrenceCount
) {
}
