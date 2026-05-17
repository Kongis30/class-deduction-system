package com.classdeduction.system.modules.base.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Course product response payload.
 */
@Builder
@Schema(description = "Course product response payload")
public record CourseProductVO(
        Long id,
        String productCode,
        String productName,
        String subjectName,
        String teachingMode,
        Integer defaultTotalUnits,
        Integer defaultLessonUnits,
        Integer expiryDurationDays,
        Long defaultClassroomId,
        String defaultClassroomName,
        BigDecimal listPrice,
        String status,
        String notes,
        List<ProductScheduleSlotVO> slots,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    @Builder
    public record ProductScheduleSlotVO(
            Long id,
            Integer weekday,
            String startTime,
            String endTime
    ) {}
}
