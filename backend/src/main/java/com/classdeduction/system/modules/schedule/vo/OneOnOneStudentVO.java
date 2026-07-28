package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "One-on-one deduction student group")
public record OneOnOneStudentVO(
        Long studentId,
        String studentNo,
        String studentName,
        String grade,
        String school,
        Double totalUnits,
        Double consumedUnits,
        Double remainingUnits,
        Boolean renewalReminder,
        List<OneOnOnePackageVO> packages
) {
}
