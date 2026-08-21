package com.classdeduction.system.modules.enrollment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 课时包主老师分配响应结构。
 */
@Builder
@Schema(description = "课时包主老师分配响应结构")
public record PackageTeacherAssignmentVO(
        Long id,
        Long teacherId,
        String teacherNo,
        String teacherName,
        String subjectName,
        Double allocatedHours,
        Double consumedHours,
        Double remainingHours,
        boolean primary,
        String assignmentStatus,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        String changeReason
) {
}
