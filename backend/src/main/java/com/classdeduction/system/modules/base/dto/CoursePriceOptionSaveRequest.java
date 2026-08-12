package com.classdeduction.system.modules.base.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Course price option save request.
 */
@Data
@Schema(description = "Course price option save request")
public class CoursePriceOptionSaveRequest {

    @Size(max = 64, message = "Option code must not exceed 64 characters")
    @Schema(description = "Unique option code, auto-generated when empty", example = "SATURDAY_G3_G5_FULL_DAY")
    private String optionCode;

    @NotBlank(message = "Course type is required")
    @Size(max = 32, message = "Course type must not exceed 32 characters")
    @Schema(description = "Course type", example = "SATURDAY")
    private String courseType;

    @NotBlank(message = "Register class is required")
    @Size(max = 64, message = "Register class must not exceed 64 characters")
    @Schema(description = "Register Class label", example = "Saturday Class")
    private String registerClass;

    @Size(max = 64, message = "Grade band must not exceed 64 characters")
    @Schema(description = "Grade or level label", example = "G3-G5")
    private String gradeBand;

    @Size(max = 128, message = "Period label must not exceed 128 characters")
    @Schema(description = "Period label", example = "Spring 2026")
    private String periodLabel;

    @Size(max = 128, message = "Duration label must not exceed 128 characters")
    @Schema(description = "Duration label", example = "24 hours")
    private String durationLabel;

    @Size(max = 128, message = "Time slot label must not exceed 128 characters")
    @Schema(description = "Time slot label", example = "Sat 09:00-12:00")
    private String timeSlotLabel;

    @Size(max = 500, message = "Detail text must not exceed 500 characters")
    @Schema(description = "Detail text used by registration and receipt")
    private String detailText;

    @DecimalMin(value = "0.00", message = "One on one hours must be 0 or greater")
    @Schema(description = "One on one purchased hours")
    private BigDecimal oneOnOneHours;

    @NotNull(message = "Price amount is required")
    @DecimalMin(value = "0.00", message = "Price amount must be 0 or greater")
    @Schema(description = "Default price amount", example = "1200.00")
    private BigDecimal priceAmount;

    @Size(max = 16, message = "Currency code must not exceed 16 characters")
    @Schema(description = "Currency code", example = "USD")
    private String currencyCode;

    @Schema(description = "Optional linked course product ID")
    private Long courseProductId;

    @Schema(description = "Generated group class ID for this course and grade")
    private Long classId;

    @Schema(description = "Group class teacher configured for this grade")
    private Long groupTeacherId;

    @Schema(description = "Group class classroom configured for this grade")
    private Long groupClassroomId;

    @Schema(description = "Weekly schedule configured for this grade class")
    private List<GroupScheduleSlotDTO> groupScheduleSlots;

    @Schema(description = "Grade class billing type: PER_SESSION / MONTHLY")
    private String groupBillingType;

    @Schema(description = "Grade class first date for per-session billing")
    private LocalDate groupStartDate;

    @Schema(description = "Grade class total sessions for per-session billing")
    private Integer groupTotalSessions;

    @Schema(description = "Total price used to derive group session and monthly prices")
    private BigDecimal groupTotalPrice;

    @Schema(description = "Grade class first month for monthly billing (yyyy-MM)")
    private String groupStartMonth;

    @Schema(description = "Grade class last month for monthly billing (yyyy-MM)")
    private String groupEndMonth;

    @Schema(description = "Sort order", example = "10")
    private Integer sortOrder;

    @Schema(description = "Status: ACTIVE / INACTIVE", example = "ACTIVE")
    private String status;

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    @Schema(description = "Internal notes")
    private String notes;

    @Data
    public static class GroupScheduleSlotDTO {
        private Long teacherId;
        private Integer weekday;
        private String startTime;
        private String endTime;
    }
}
