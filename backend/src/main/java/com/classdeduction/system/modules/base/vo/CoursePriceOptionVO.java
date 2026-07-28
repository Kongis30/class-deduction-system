package com.classdeduction.system.modules.base.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Course price option response payload.
 */
@Builder
@Schema(description = "Course price option response payload")
public record CoursePriceOptionVO(
        Long id,
        String optionCode,
        String courseType,
        String registerClass,
        String gradeBand,
        String periodLabel,
        String durationLabel,
        String timeSlotLabel,
        String detailText,
        BigDecimal oneOnOneHours,
        BigDecimal priceAmount,
        String currencyCode,
        Long courseProductId,
        Long classId,
        Long groupTeacherId,
        Long groupClassroomId,
        List<GroupScheduleSlotVO> groupScheduleSlots,
        String groupBillingType,
        String groupStartDate,
        Integer groupTotalSessions,
        String groupStartMonth,
        String groupEndMonth,
        Integer sortOrder,
        String status,
        String notes,
        Boolean referenced,
        Long referenceCount,
        List<String> referenceSources,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record GroupScheduleSlotVO(Integer weekday, String startTime, String endTime) {
    }
}
