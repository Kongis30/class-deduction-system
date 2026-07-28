package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报名开单请求：一次请求完成报名、支付、课时包、主老师和收据的创建。
 */
@Data
@Schema(description = "报名开单请求")
public class EnrollmentCreateRequest {

    @NotNull(message = "Student is required")
    @Schema(description = "学生ID", example = "1880000000000000001")
    private Long studentId;

    @NotNull(message = "Course product is required")
    @Schema(description = "课程产品ID", example = "1880000000000000002")
    private Long courseProductId;

    @Schema(description = "来源报名订单明细ID", example = "1880000000000000003")
    private Long enrollmentOrderItemId;

    @Schema(description = "主老师ID", example = "1880000000000000003")
    private Long primaryTeacherId;

    @Schema(description = "收银员用户ID", example = "1880000000000000004")
    private Long cashierUserId;

    @NotBlank(message = "Handler name is required")
    @Schema(description = "经手人姓名快照", example = "Amy")
    private String handledByName;

    @Schema(description = "币种", example = "USD")
    private String currencyCode;

    @Schema(description = "课时包名称，默认取课程产品名称")
    private String packageName;

    @Schema(description = "开始日期", example = "2026-04-07")
    private LocalDate startDate;

    @Schema(description = "预计结束日期", example = "2026-07-07")
    private LocalDate estimatedEndDate;

    @Schema(description = "主老师生效时间，默认当前时间", example = "2026-04-07T09:00:00")
    private LocalDateTime assignmentEffectiveFrom;

    @Schema(description = "支付时间，默认当前时间", example = "2026-04-07T10:00:00")
    private LocalDateTime paidAt;

    @Schema(description = "是否生成收据", example = "true")
    private Boolean issueReceipt;

    @Schema(description = "收据模板编码", example = "DEFAULT_RECEIPT")
    private String templateCode;

    @NotNull(message = "Original amount is required")
    @DecimalMin(value = "0.00", message = "Original amount cannot be negative")
    @Schema(description = "原价", example = "2699.00")
    private BigDecimal originalAmount;

    @NotNull(message = "Discount amount is required")
    @DecimalMin(value = "0.00", message = "Discount amount cannot be negative")
    @Schema(description = "优惠金额", example = "100.00")
    private BigDecimal discountAmount;

    @NotNull(message = "Received amount is required")
    @DecimalMin(value = "0.00", message = "Received amount cannot be negative")
    @Schema(description = "实收金额", example = "2599.00")
    private BigDecimal receivedAmount;

    @Schema(description = "覆盖总课时单位，默认取课程产品配置", example = "80")
    @Min(value = 0, message = "Total units cannot be negative")
    private Integer totalUnits;

    @Schema(description = "覆盖默认单节课时单位，默认取课程产品配置", example = "4")
    @Min(value = 1, message = "Default lesson units must be at least 1")
    private Integer defaultLessonUnits;

    @Schema(description = "备注")
    private String remark;

    @Valid
    @Schema(description = "排课槽位列表（可选，填写则自动生成排课）")
    private List<SlotDefinition> slots;

    @Schema(description = "默认教室ID（排课时使用）")
    private Long defaultClassroomId;

    /**
     * 排课槽位定义：指定星期几、时间范围和课时单位。
     */
    @Data
    @Schema(description = "排课槽位定义")
    public static class SlotDefinition {

        @NotNull(message = "weekday is required")
        @Schema(description = "星期几 1=周一 7=周日", example = "1")
        private Integer weekday;

        @NotBlank(message = "startTime is required")
        @Schema(description = "开始时间 HH:mm", example = "15:00")
        private String startTime;

        @NotBlank(message = "endTime is required")
        @Schema(description = "结束时间 HH:mm", example = "16:00")
        private String endTime;

        @NotNull(message = "plannedUnits is required")
        @Min(value = 1, message = "plannedUnits must be at least 1")
        @Schema(description = "本次课时单位数（1单位=0.5小时）", example = "2")
        private Integer plannedUnits;
    }
}
