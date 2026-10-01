package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Course line payload shared by future registration intake requests.
 */
@Getter
@Setter
@Schema(description = "Enrollment order item payload")
public class EnrollmentOrderItemDTO {

    /** 历史报名单更正时用于定位原订单行；新报名时为空。 */
    private Long orderItemId;

    @Min(value = 1, message = "Line number must be at least 1")
    @Schema(description = "Line number inside the enrollment order", example = "1")
    private Integer lineNo;

    @Schema(description = "Register Class label", example = "Saturday")
    private String registerClass;

    @Schema(description = "Course type", example = "SATURDAY")
    private String courseType;

    @Schema(description = "Course product ID", example = "1880000000000000002")
    private Long courseProductId;

    /** 新班课模型下的教学项目；与 planId、classId 一起明确业务归属。 */
    private Long groupProjectId;

    /** 新班课模型下的报名权益方案；私教和历史班课为空。 */
    private Long groupRegistrationPlanId;

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

    @Schema(description = "Enrollment start date for this course line", example = "2026-03-01")
    private LocalDate startDate;

    @Schema(description = "Package unit snapshot: MONTH/WEEK/SESSION/HOUR")
    private String packageUnit;

    @Schema(description = "Package quantity snapshot")
    private BigDecimal packageQuantity;

    @Schema(description = "Package total price snapshot")
    private BigDecimal packageTotalPrice;

    @Schema(description = "Attendance option: FULL_DAY/MORNING/AFTERNOON/CUSTOM/NONE")
    private String attendanceOption;

    @Schema(description = "Per-day attendance schedule JSON")
    private String attendanceScheduleJson;

    @DecimalMin(value = "0.00", message = "Price amount cannot be negative")
    @Schema(description = "Final line price", example = "1299.00")
    private BigDecimal priceAmount;

    @DecimalMin(value = "0.00", message = "Original price amount cannot be negative")
    @Schema(description = "Original configured price before manual override", example = "1499.00")
    private BigDecimal originalPriceAmount;

    @Schema(description = "Whether the price was manually overridden", example = "false")
    private Boolean manualPriceOverride;

    @DecimalMin(value = "0.00", message = "Course discount cannot be negative")
    @Schema(description = "Discount assigned to this course line", example = "50.00")
    private BigDecimal discountAmount;

    @DecimalMin(value = "0.00", message = "One-on-one hours cannot be negative")
    @Schema(description = "One-on-one purchased hours", example = "20")
    private BigDecimal oneOnOneHours;

    @DecimalMin(value = "0.00", message = "One-on-one bonus hours cannot be negative")
    @Schema(description = "One-on-one bonus (gifted) hours included in the package total", example = "2")
    private BigDecimal oneOnOneBonusHours;

    @DecimalMin(value = "0.00", message = "Opening used hours cannot be negative")
    @Schema(description = "Already deducted one-on-one hours before this system starts tracking lessons", example = "12")
    private BigDecimal openingUsedHours;

    @Min(value = 0, message = "One-on-one units cannot be negative")
    @Schema(description = "One-on-one units, 1 unit = 0.5 hour", example = "40")
    private Integer oneOnOneUnits;

    /** 课程级接送快照；旧客户端不传时由订单级字段兼容计算。 */
    private Boolean pickupRequired;

    private String pickupAddress;

    private Boolean dropoffRequired;

    private String dropoffAddress;

    private Boolean pickupFeeCharged;

    private BigDecimal pickupFeeAmount;

    private Boolean dropoffFeeCharged;

    private BigDecimal dropoffFeeAmount;

    /** 课程级其他费用，最终会写入订单明细的费用快照。 */
    private List<RegistrationIntakeRequest.FeeLinePayload> serviceFees;

    @Schema(description = "Line item remark")
    private String remark;
}
