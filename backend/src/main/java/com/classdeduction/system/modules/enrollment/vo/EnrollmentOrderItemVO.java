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
        Long logicalCourseId,
        Integer lineNo,
        String registerClass,
        String courseType,
        Long courseProductId,
        Long groupProjectId,
        Long groupRegistrationPlanId,
        Long coursePriceOptionId,
        Long classId,
        String className,
        String detail,
        String gradeBand,
        String periodLabel,
        String timeSlotLabel,
        LocalDate startDate,
        Integer sessions,
        Integer purchasedMonths,
        String packageUnit,
        BigDecimal packageQuantity,
        BigDecimal packageTotalPrice,
        String attendanceOption,
        String attendanceScheduleJson,
        Integer entitlementPurchasedQuantity,
        Long classEnrollmentId,
        LocalDate entitlementExpiryDate,
        String entitlementStatus,
        BigDecimal priceAmount,
        BigDecimal originalPriceAmount,
        Boolean manualPriceOverride,
        BigDecimal discountAmount,
        BigDecimal amountDue,
        BigDecimal paidAmount,
        BigDecimal balanceAmount,
        BigDecimal oneOnOneHours,
        BigDecimal openingUsedHours,
        Integer oneOnOneUnits,
        Boolean pickupRequiredSnapshot,
        String pickupAddressSnapshot,
        Boolean dropoffRequiredSnapshot,
        String dropoffAddressSnapshot,
        BigDecimal pickupFeeAmount,
        BigDecimal dropoffFeeAmount,
        BigDecimal extraFeeAmount,
        String extraFeeDetail,
        String itemStatus,
        BigDecimal stoppedConsumedAmount,
        Integer stoppedSessionCount,
        LocalDateTime stoppedAt,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
