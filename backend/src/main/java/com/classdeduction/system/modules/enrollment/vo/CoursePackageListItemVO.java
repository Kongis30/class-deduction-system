package com.classdeduction.system.modules.enrollment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 课时包列表项响应结构。
 */
@Builder
@Schema(description = "课时包列表项响应结构")
public record CoursePackageListItemVO(
        Long id,
        String packageNo,
        Long enrollmentOrderId,
        Long enrollmentOrderItemId,
        Long studentId,
        String studentName,
        Long courseProductId,
        String courseProductName,
        String packageName,
        String subjectName,
        String teachingMode,
        Double totalUnits,
        Double consumedUnits,
        Double openingUsedHours,
        Double remainingUnits,
        BigDecimal receivedAmount,
        String packageStatus,
        LocalDate startDate,
        LocalDate estimatedEndDate,
        String primaryTeacherName,
        LocalDateTime createdAt
) {
}
