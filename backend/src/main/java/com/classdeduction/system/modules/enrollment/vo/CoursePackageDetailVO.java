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
        Long studentId,
        String studentName,
        Long courseProductId,
        String courseProductName,
        String packageName,
        String subjectName,
        String teachingMode,
        Integer totalUnits,
        Integer defaultLessonUnits,
        Integer consumedUnits,
        Integer remainingUnits,
        BigDecimal originalAmount,
        BigDecimal discountAmount,
        BigDecimal receivedAmount,
        String packageStatus,
        LocalDate startDate,
        LocalDate estimatedEndDate,
        String remark,
        List<PackageTeacherAssignmentVO> teacherAssignments,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
