package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    @Schema(description = "Limit source orders to registrations with pickup or drop-off requirements")
    private Boolean transportOnly;

    @Schema(description = "Include cancelled and refunded orders (used by the historical registration log)")
    private Boolean includeInactiveOrders;

    @Schema(description = "Registration saved at or after this timestamp")
    private LocalDateTime savedAtFrom;

    @Schema(description = "Registration saved before this timestamp")
    private LocalDateTime savedAtBefore;

    @Schema(description = "Registration operator user ID")
    private Long registrationOperatorUserId;
}
