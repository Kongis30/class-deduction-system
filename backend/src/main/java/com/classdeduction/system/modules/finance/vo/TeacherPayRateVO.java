package com.classdeduction.system.modules.finance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * 课时单价响应。
 */
@Builder
@Schema(description = "Teacher pay rate response")
public record TeacherPayRateVO(
        Long id,
        Long teacherId,
        String teacherNo,
        String teacherName,
        Long courseProductId,
        String productCode,
        String productName,
        String subjectName,
        BigDecimal hourlyRate,
        String notes
) {}
