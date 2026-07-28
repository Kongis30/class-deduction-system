package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Course line payload shared by future registration intake requests.
 */
@Getter
@Setter
@Schema(description = "Enrollment order item payload")
public class EnrollmentOrderItemDTO {

    @Min(value = 1, message = "Line number must be at least 1")
    @Schema(description = "Line number inside the enrollment order", example = "1")
    private Integer lineNo;

    @Schema(description = "Register Class label", example = "Saturday")
    private String registerClass;

    @Schema(description = "Course type", example = "SATURDAY")
    private String courseType;

    @Schema(description = "Course product ID", example = "1880000000000000002")
    private Long courseProductId;

    @Schema(description = "Course price option ID", example = "1880000000000000003")
    private Long coursePriceOptionId;

    @Schema(description = "Class ID for group course lines", example = "1880000000000000004")
    private Long classId;

    @Schema(description = "Course detail snapshot", example = "G6 Saturday 10:00-12:00")
    private String detail;

    @Schema(description = "Grade or level label", example = "Grade 6")
    private String gradeBand;

    @Schema(description = "Period or package label", example = "Fall")
    private String periodLabel;

    @Schema(description = "Time slot label", example = "10:00-12:00")
    private String timeSlotLabel;

    @DecimalMin(value = "0.00", message = "Price amount cannot be negative")
    @Schema(description = "Final line price", example = "1299.00")
    private BigDecimal priceAmount;

    @DecimalMin(value = "0.00", message = "Original price amount cannot be negative")
    @Schema(description = "Original configured price before manual override", example = "1499.00")
    private BigDecimal originalPriceAmount;

    @Schema(description = "Whether the price was manually overridden", example = "false")
    private Boolean manualPriceOverride;

    @DecimalMin(value = "0.00", message = "One-on-one hours cannot be negative")
    @Schema(description = "One-on-one purchased hours", example = "20")
    private BigDecimal oneOnOneHours;

    @Min(value = 0, message = "One-on-one units cannot be negative")
    @Schema(description = "One-on-one units, 1 unit = 0.5 hour", example = "40")
    private Integer oneOnOneUnits;

    @Schema(description = "Structured first one-on-one lesson start time")
    private LocalDateTime initialLessonStartAt;

    @Schema(description = "Structured first one-on-one lesson end time")
    private LocalDateTime initialLessonEndAt;

    @Schema(description = "Required one-on-one teacher ID", example = "1880000000000000005")
    private Long teacherId;

    @Schema(description = "Line item remark")
    private String remark;
}
