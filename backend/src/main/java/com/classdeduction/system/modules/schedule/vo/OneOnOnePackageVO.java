package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
@Schema(description = "One-on-one deduction package")
public record OneOnOnePackageVO(
        Long packageId,
        String packageNo,
        Long courseProductId,
        String courseProductName,
        String gradeBand,
        String packageName,
        String subjectName,
        Double totalUnits,
        Double consumedUnits,
        Double remainingUnits,
        Double defaultLessonUnits,
        Boolean renewalReminder,
        String packageStatus,
        LocalDate startDate,
        LocalDate estimatedEndDate,
        List<OneOnOneLessonVO> lessons
) {
}
