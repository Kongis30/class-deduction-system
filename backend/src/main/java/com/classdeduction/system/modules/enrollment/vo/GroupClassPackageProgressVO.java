package com.classdeduction.system.modules.enrollment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;

/**
 * 班课产品进度列表项。
 */
@Builder
@Schema(description = "Group class package progress list item")
public record GroupClassPackageProgressVO(
        Long classId,
        Long productId,
        String className,
        String productName,
        String subjectName,
        String billingType,
        String scheduleDesc,
        String teacherName,
        Integer totalUnits,
        Integer consumedUnits,
        Integer remainingUnits,
        Long activeStudentCount,
        String status,
        LocalDate startDate,
        LocalDate estimatedEndDate
) {
}
