package com.classdeduction.system.modules.enrollment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 课时包详情响应结构。
 */
@Builder
@Schema(description = "课时包详情响应结构")
public record CoursePackageDetailVO(
        Long id,
        String packageNo,
        Long enrollmentOrderId,
        Long enrollmentOrderItemId,
        Long studentId,
        String studentName,
        Long courseProductId,
        String courseProductName,
        Long coursePriceOptionId,
        String gradeBand,
        String packageName,
        String subjectName,
        String teachingMode,
        Double purchasedHours,
        Double totalUnits,
        Double consumedUnits,
        Double openingUsedHours,
        Double remainingUnits,
        BigDecimal originalAmount,
        BigDecimal discountAmount,
        BigDecimal receivedAmount,
        String packageStatus,
        LocalDate startDate,
        LocalDate estimatedEndDate,
        String remark,
        Long privateLessonGroupId,
        Integer privateLessonParticipantCount,
        List<PrivateLessonGroupParticipantVO> privateLessonParticipants,
        List<PackageTeacherAssignmentVO> teacherAssignments,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
