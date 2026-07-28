package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "One-on-one deduction teacher group")
public record OneOnOneTeacherVO(
        Long teacherId,
        String teacherNo,
        String teacherName,
        Double totalUnits,
        Double consumedUnits,
        Double remainingUnits,
        Integer studentCount,
        Integer packageCount,
        List<OneOnOneStudentVO> students
) {
}
