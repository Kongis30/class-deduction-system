package com.classdeduction.system.modules.finance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * 结算明细响应。
 */
@Builder
@Schema(description = "Teacher payment item VO")
public record TeacherPaymentItemVO(
        Long id,
        Long lessonRecordId,
        Long packageId,
        String packageName,
        Long courseProductId,
        String productName,
        String subjectName,
        Integer actualUnits,
        BigDecimal hours,
        BigDecimal hourlyRate,
        BigDecimal amount,
        String occurrenceType,
        String className
) {}
