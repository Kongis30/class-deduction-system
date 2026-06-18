package com.classdeduction.system.modules.enrollment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;

/**
 * 班课学生个人进度列表项。
 */
@Builder
@Schema(description = "Group class student progress list item")
public record GroupClassStudentProgressVO(
        Long enrollmentId,
        Long classId,
        Long studentId,
        String studentName,
        String billingType,
        Integer totalUnits,
        Integer consumedUnits,
        Integer remainingUnits,
        LocalDate enrollmentDate,
        LocalDate expiryDate,
        String enrollmentStatus
) {
}
