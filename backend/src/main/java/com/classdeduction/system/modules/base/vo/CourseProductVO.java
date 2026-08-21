package com.classdeduction.system.modules.base.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Course product response payload.
 */
@Builder
@Schema(description = "Course product response payload")
public record CourseProductVO(
        Long id,
        String productCode,
        String productName,
        String courseType,
        String registerClass,
        String subjectName,
        String teachingMode,
        Integer sortOrder,
        String status,
        String notes,
        Boolean referenced,
        Long referenceCount,
        List<String> referenceSources,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
