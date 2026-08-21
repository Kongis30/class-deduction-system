package com.classdeduction.system.modules.base.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Course product save request.
 */
@Data
@Schema(description = "Course product save request")
public class CourseProductSaveRequest {

    @Schema(description = "Course product code (auto-generated if empty)", example = "MATH-24-1ON1")
    private String productCode;

    @NotBlank(message = "Course product name is required")
    @Schema(description = "Course product name", example = "Math 1:1 - 24 units")
    private String productName;

    @Schema(description = "Parent course type: AFTER_SCHOOL/SATURDAY/SUMMER/SPRINT/ONE_ON_ONE", example = "SATURDAY")
    private String courseType;

    @Schema(description = "Register Class label shown on registration", example = "Saturday Class")
    private String registerClass;

    @Schema(description = "Subject name", example = "Math")
    private String subjectName;

    @NotBlank(message = "Teaching mode is required")
    @Schema(description = "Teaching mode", example = "ONE_ON_ONE")
    private String teachingMode;

    @Min(value = 0, message = "Sort must be zero or greater")
    @Schema(description = "Display sort order. Lower numbers appear first.", example = "10")
    private Integer sortOrder;

    @Schema(description = "Course product status", example = "ACTIVE")
    private String status;

    @Schema(description = "Internal notes")
    private String notes;
}
