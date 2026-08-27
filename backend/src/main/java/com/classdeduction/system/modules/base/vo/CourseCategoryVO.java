package com.classdeduction.system.modules.base.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Course category response payload.
 */
@Builder
@Schema(description = "Course category response payload")
public record CourseCategoryVO(
        Long id,
        String typeCode,
        String displayName,
        Integer displayOrder,
        String status
) {
}
