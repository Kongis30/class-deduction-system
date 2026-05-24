package com.classdeduction.system.modules.finance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 未结算课次响应。
 */
@Builder
@Schema(description = "Unpaid lesson record VO")
public record UnpaidLessonVO(
        Long lessonRecordId,
        LocalDateTime lessonDate,
        Long studentId,
        String studentName,
        Long packageId,
        String packageName,
        Long courseProductId,
        String productName,
        String subjectName,
        Integer actualUnits,
        BigDecimal hours,
        BigDecimal hourlyRate,
        BigDecimal amount,
        String resultStatus,
        String occurrenceType,
        String className
) {}
