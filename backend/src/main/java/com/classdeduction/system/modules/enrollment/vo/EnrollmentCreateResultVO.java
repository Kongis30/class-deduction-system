package com.classdeduction.system.modules.enrollment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报名开单结果响应结构。
 */
@Builder
@Schema(description = "报名开单结果响应结构")
public record EnrollmentCreateResultVO(
        Long enrollmentOrderId,
        String enrollmentNo,
        List<Long> packageIds,
        List<String> packageNos,
        List<Long> paymentIds,
        List<String> paymentNos,
        List<Long> receiptIds,
        List<String> receiptNos,
        Long scheduleRuleId,
        Integer generatedOccurrenceCount,
        LocalDateTime firstLessonAt,
        LocalDateTime lastLessonAt
) {
}
