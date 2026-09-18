package com.classdeduction.system.modules.enrollment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Enrollment order line item response payload.
 */
@Builder
@Schema(description = "Enrollment order line item response payload")
public record EnrollmentOrderItemVO(
        Long id,
        Long enrollmentOrderId,
        Integer lineNo,
        String registerClass,
        String courseType,
        Long courseProductId,
        Long coursePriceOptionId,
        Long classId,
        String detail,
        String gradeBand,
        String periodLabel,
        String timeSlotLabel,
        LocalDate startDate,
        BigDecimal priceAmount,
        BigDecimal originalPriceAmount,
        Boolean manualPriceOverride,
        BigDecimal discountAmount,
        BigDecimal amountDue,
        BigDecimal paidAmount,
        BigDecimal balanceAmount,
        BigDecimal oneOnOneHours,
        Integer oneOnOneUnits,
        String itemStatus,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
