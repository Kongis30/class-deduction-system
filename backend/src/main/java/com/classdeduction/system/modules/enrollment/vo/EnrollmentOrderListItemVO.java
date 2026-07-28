package com.classdeduction.system.modules.enrollment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报名单列表项响应结构。
 */
@Builder
@Schema(description = "报名单列表项响应结构")
public record EnrollmentOrderListItemVO(
        Long id,
        String enrollmentNo,
        Long studentId,
        String studentName,
        Long classId,
        String className,
        Long courseProductId,
        String courseProductName,
        Long campusId,
        String campusNameSnapshot,
        String handledByName,
        LocalDate signupDate,
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        String studentNameSnapshot,
        String gradeSnapshot,
        String schoolSnapshot,
        String parentPhoneSnapshot,
        String parentPhone2Snapshot,
        Boolean pickupRequiredSnapshot,
        String pickupAddressSnapshot,
        Boolean dropoffRequiredSnapshot,
        String dropoffAddressSnapshot,
        String currencyCode,
        BigDecimal tuitionSubtotalAmount,
        BigDecimal pickupFeeAmount,
        BigDecimal dropoffFeeAmount,
        BigDecimal extraFeeAmount,
        BigDecimal originalAmount,
        BigDecimal discountAmount,
        BigDecimal receivedAmount,
        BigDecimal amountDue,
        BigDecimal balanceAmount,
        String paymentStatus,
        String orderStatus,
        Integer packageCount,
        List<EnrollmentOrderItemVO> items,
        LocalDateTime createdAt
) {
}
