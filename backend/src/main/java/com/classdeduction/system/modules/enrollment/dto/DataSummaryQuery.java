package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
@Schema(description = "Data summary filter")
public class DataSummaryQuery {

    @Schema(description = "Keyword for student, phone, order, or course")
    private String keyword;

    @Schema(description = "Campus ID")
    private Long campusId;

    @Schema(description = "Course type")
    private String courseType;

    @Schema(description = "Class ID")
    private Long classId;

    @Schema(description = "Course product ID")
    private Long courseProductId;

    @Schema(description = "Period start date")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate periodStartDate;

    @Schema(description = "Period end date")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate periodEndDate;

    @Schema(description = "Payment status")
    private String paymentStatus;

    @Schema(description = "Only return rows with alerts")
    private Boolean onlyAlerts;

    @Schema(description = "Only return rows with data consistency issues")
    private Boolean onlyNeedsReview;
}
