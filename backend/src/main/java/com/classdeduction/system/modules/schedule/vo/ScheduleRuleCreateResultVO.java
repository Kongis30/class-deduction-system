package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 排课规则创建结果。
 */
@Builder
@Schema(description = "Schedule rule creation result")
public record ScheduleRuleCreateResultVO(
        Long ruleId,
        Integer slotCount,
        Integer generatedOccurrenceCount,
        LocalDateTime firstGeneratedStartAt,
        LocalDateTime lastGeneratedEndAt
) {
}
