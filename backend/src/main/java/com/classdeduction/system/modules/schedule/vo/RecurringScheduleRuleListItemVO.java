package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 周期排课规则列表项。
 */
@Builder
@Schema(description = "Recurring schedule rule list item")
public record RecurringScheduleRuleListItemVO(
        Long id,
        Long packageId,
        String packageNo,
        Long studentId,
        String studentName,
        Long defaultClassroomId,
        String defaultClassroomName,
        String ruleStatus,
        LocalDate startDate,
        LocalDate endDate,
        Integer generationHorizonDays,
        Integer slotCount,
        Integer generatedOccurrenceCount,
        LocalDateTime nextLessonStartAt,
        LocalDateTime createdAt
) {
}
