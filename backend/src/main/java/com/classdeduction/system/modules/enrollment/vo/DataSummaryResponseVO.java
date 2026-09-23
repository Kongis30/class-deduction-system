package com.classdeduction.system.modules.enrollment.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Data summary response")
public record DataSummaryResponseVO(
        Long totalRegistrationCount,
        BigDecimal totalIncomeAmount,
        @Schema(description = "Total unpaid balance. Negative values represent money still owed.")
        BigDecimal totalOutstandingAmount,
        List<SummaryGroupVO> campusSummaries,
        List<SummaryGroupVO> courseTypeSummaries,
        List<SummaryGroupVO> classCourseSummaries,
        List<SummaryGroupVO> paymentStatusSummaries,
        List<RowVO> rows
) {

    @Schema(description = "Data summary group")
    public record SummaryGroupVO(
            String groupKey,
            String groupName,
            Long recordCount,
            Long studentCount,
            BigDecimal incomeAmount,
            @Schema(description = "Unpaid balance. Negative values represent money still owed.")
            BigDecimal outstandingAmount
    ) {
    }

    @Schema(description = "Data summary table row")
    public record RowVO(
            Long orderId,
            String enrollmentNo,
            Long orderItemId,
            Long studentId,
            Integer participantIndex,
            String studentName,
            String studentClass,
            String school,
            String grade,
            LocalDate signupDate,
            LocalDate periodStartDate,
            LocalDate periodEndDate,
            String periodLabel,
            String attendanceOption,
            @Schema(description = "Custom attendance slot description, e.g. 9am-10am for CUSTOM rows.")
            String timeSlotLabel,
            Long campusId,
            String campusName,
            Long classId,
            String className,
            Long courseProductId,
            String courseProductName,
            String registerClass,
            String courseType,
            String detail,
            String phone,
            String phone2,
            Boolean pickupRequired,
            String pickupAddress,
            Boolean dropoffRequired,
            String dropoffAddress,
            String pickupDropoffLabel,
            String paymentMethod,
            String paymentStatus,
            BigDecimal linePriceAmount,
            BigDecimal pickupFeeAmount,
            BigDecimal dropoffFeeAmount,
            BigDecimal extraFeeAmount,
            BigDecimal discountAmount,
            BigDecimal amountDue,
            BigDecimal paidAmount,
            @Schema(description = "Display balance. Negative values represent money still owed.")
            BigDecimal balanceAmount,
            Double totalHours,
            Double usedHours,
            Double remainingHours,
            Long teacherId,
            String teacherName,
            LocalDate expiryDate,
            Boolean overdue,
            Boolean expiringSoon,
            Boolean lowOneOnOneHours,
            List<String> alertTypes,
            String orderStatus,
            List<String> reviewIssueCodes,
            Long privateLessonGroupId,
            Integer privateLessonParticipantCount
    ) {
    }
}
