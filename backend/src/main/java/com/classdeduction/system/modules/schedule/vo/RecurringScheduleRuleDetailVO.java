package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 周期排课规则详情。
 */
@Builder
@Schema(description = "Recurring schedule rule detail")
public record RecurringScheduleRuleDetailVO(
        Long id,
        Long packageId,
        String packageNo,
        String packageName,
        Long studentId,
        String studentName,
        Long defaultClassroomId,
        String defaultClassroomName,
        String ruleStatus,
        LocalDate startDate,
        LocalDate endDate,
        String notes,
        Integer generatedOccurrenceCount,
        LocalDateTime nextLessonStartAt,
        Double deductedHours,
        Double pendingHours,
        Double otherReservedHours,
        List<RecurringScheduleSlotVO> slots,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
