package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

/**
 * 本周临时调整结果：返回周课时变化和实际被修改的课次。
 */
@Builder
@Schema(description = "Weekly temporary schedule adjustment result")
public record WeeklyScheduleAdjustmentResultVO(
        Integer originalWeeklyUnits,
        Integer adjustedWeeklyUnits,
        Integer unitDelta,
        Integer createdCount,
        Integer updatedCount,
        Integer deletedCount,
        List<LessonOccurrenceVO> adjustedOccurrences
) {
}
