package com.classdeduction.system.modules.enrollment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Data summary response")
@JsonInclude(JsonInclude.Include.NON_NULL)
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

    public DataSummaryResponseVO withoutFinancialAmounts() {
        return new DataSummaryResponseVO(totalRegistrationCount, null, null,
                campusSummaries.stream().map(SummaryGroupVO::withoutFinancialAmounts).toList(),
                courseTypeSummaries.stream().map(SummaryGroupVO::withoutFinancialAmounts).toList(),
                classCourseSummaries.stream().map(SummaryGroupVO::withoutFinancialAmounts).toList(),
                paymentStatusSummaries.stream().map(SummaryGroupVO::withoutFinancialAmounts).toList(),
                rows.stream().map(RowVO::withoutFinancialAmounts).toList());
    }

    @Schema(description = "Data summary group")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SummaryGroupVO(
            String groupKey,
            String groupName,
            Long recordCount,
            Long studentCount,
            BigDecimal incomeAmount,
            @Schema(description = "Unpaid balance. Negative values represent money still owed.")
            BigDecimal outstandingAmount
    ) {
        private SummaryGroupVO withoutFinancialAmounts() {
            return new SummaryGroupVO(groupKey, groupName, recordCount, studentCount, null, null);
        }
    }

    @Schema(description = "Data summary table row")
    @JsonInclude(JsonInclude.Include.NON_NULL)
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
            @Schema(description = "Course price option grade-band snapshot; legacy Saturday rows may store their season label here.")
            String gradeBand,
            LocalDate signupDate,
            LocalDate periodStartDate,
            LocalDate periodEndDate,
            String periodLabel,
            String registrationTermCode,
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
            /** 整张收据的付款状态；汇总、收款和退款操作使用此状态。 */
            String orderPaymentStatus,
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
            Integer privateLessonParticipantCount,
            String itemStatus,
            Boolean refunded,
            BigDecimal stoppedConsumedAmount,
            Integer stoppedSessionCount,
            java.time.LocalDateTime registrationSavedAt,
            Long registrationOperatorUserId,
            String registrationOperatorName,
            Long logicalCourseId
    ) {
        private RowVO withoutFinancialAmounts() {
            return new RowVO(orderId, enrollmentNo, orderItemId, studentId, participantIndex,
                    studentName, studentClass, school, grade, gradeBand, signupDate, periodStartDate,
                    periodEndDate, periodLabel, registrationTermCode, attendanceOption, timeSlotLabel, campusId, campusName,
                    classId, className, courseProductId, courseProductName, registerClass, courseType,
                    detail, phone, phone2, pickupRequired, pickupAddress, dropoffRequired, dropoffAddress,
                    pickupDropoffLabel, paymentMethod, paymentStatus, orderPaymentStatus, null, null, null, null, null,
                    null, null, null, totalHours, usedHours, remainingHours, teacherId, teacherName,
                    expiryDate, overdue, expiringSoon, lowOneOnOneHours, alertTypes, orderStatus,
                    reviewIssueCodes, privateLessonGroupId, privateLessonParticipantCount, itemStatus, refunded,
                    null, stoppedSessionCount, registrationSavedAt, registrationOperatorUserId,
                    registrationOperatorName, logicalCourseId);
        }
    }
}
