package com.classdeduction.system.modules.enrollment.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record DailyRegistrationResponseVO(
        LocalDate fromDate,
        LocalDate toDate,
        long courseRegistrationCount,
        long orderCount,
        long uniqueStudentCount,
        BigDecimal amountDue,
        BigDecimal amountCollected,
        BigDecimal outstandingAmount,
        List<OperatorSummaryVO> operators,
        List<RegistrationRowVO> rows
) {
    public record OperatorSummaryVO(
            Long operatorUserId,
            String operatorName,
            long courseRegistrationCount,
            long orderCount,
            long uniqueStudentCount,
            BigDecimal amountDue,
            BigDecimal amountCollected
    ) {
    }

    public record RegistrationRowVO(
            Long orderId,
            Long orderItemId,
            Long studentId,
            String studentName,
            String courseName,
            String courseType,
            String className,
            String grade,
            String campusName,
            LocalDate signupDate,
            OffsetDateTime savedAt,
            Long operatorUserId,
            String operatorName,
            String paymentMethod,
            String paymentStatus,
            String orderStatus,
            BigDecimal amountDue,
            BigDecimal amountCollected,
            BigDecimal outstandingAmount
    ) {
    }
}
