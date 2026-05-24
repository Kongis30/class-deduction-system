package com.classdeduction.system.modules.base.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

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

    @Schema(description = "Subject name", example = "Math")
    private String subjectName;

    @NotBlank(message = "Teaching mode is required")
    @Schema(description = "Teaching mode", example = "ONE_ON_ONE")
    private String teachingMode;

    @Min(value = 1, message = "Default total units must be at least 1")
    @Schema(description = "Default total units in hours (required for ONE_ON_ONE, null for GROUP)", example = "24")
    private Integer defaultTotalUnits;

    @Min(value = 1, message = "Default lesson units must be at least 1")
    @Schema(description = "Default units per lesson (required for ONE_ON_ONE, null for GROUP)", example = "1")
    private Integer defaultLessonUnits;

    @Min(value = 1, message = "Expiry duration days must be at least 1")
    @Schema(description = "Default enrollment expiry duration in days (for GROUP)", example = "180")
    private Integer expiryDurationDays;

    @Schema(description = "Default classroom ID (for GROUP)", example = "6101")
    private Long defaultClassroomId;

    @Schema(description = "Default teacher ID (for GROUP)", example = "7001")
    private Long defaultTeacherId;

    @DecimalMin(value = "0.00", message = "Original price must be 0 or greater")
    @Schema(description = "Original price", example = "2699.00")
    private BigDecimal listPrice;

    @Schema(description = "Course product status", example = "ACTIVE")
    private String status;

    @Schema(description = "Internal notes")
    private String notes;

    @Schema(description = "Schedule slots for class group products")
    private List<ProductScheduleSlotDTO> slots;

    @Data
    @Schema(description = "Schedule slot definition")
    public static class ProductScheduleSlotDTO {
        @Schema(description = "Weekday: 1=Mon..7=Sun", example = "1")
        private Integer weekday;

        @Schema(description = "Start time (HH:mm)", example = "09:00")
        private String startTime;

        @Schema(description = "End time (HH:mm)", example = "10:30")
        private String endTime;
    }
}
