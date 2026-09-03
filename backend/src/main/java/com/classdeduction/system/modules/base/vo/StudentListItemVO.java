package com.classdeduction.system.modules.base.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Student list item response payload.
 */
@Builder
@Schema(description = "Student list item response payload")
public record StudentListItemVO(
        Long id,
        String studentNo,
        String studentName,
        String englishName,
        String gender,
        LocalDate birthDate,
        String grade,
        String studentClass,
        String school,
        String sourceChannel,
        String status,
        String notes,
        String guardianName,
        String phone,
        String guardianPhone2,
        String guardianEmail,
        String guardianRelationType,
        Boolean pickupRequired,
        String pickupAddress,
        Boolean dropoffRequired,
        String dropoffAddress,
        Long campusId,
        String campusName,
        LocalDate latestSignupDate,
        Long enrollmentCount,
        BigDecimal enrollmentAmountDue,
        BigDecimal enrollmentPaidAmount,
        BigDecimal enrollmentBalanceAmount,
        String aggregatePaymentStatus,
        Boolean hasEnrollmentOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
