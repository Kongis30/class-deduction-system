package com.classdeduction.system.modules.enrollment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Line-level snapshot for a multi-course enrollment order.
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("enrollment_order_item")
@Schema(description = "Enrollment order item entity")
public class EnrollmentOrderItemEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long enrollmentOrderId;

    private Integer lineNo;

    private String registerClass;

    private String courseType;

    private Long courseProductId;

    private Long coursePriceOptionId;

    private Long classId;

    private String detail;

    private String gradeBand;

    private String periodLabel;

    private String timeSlotLabel;

    private LocalDate startDate;

    private String packageUnit;

    private BigDecimal packageQuantity;

    private BigDecimal packageTotalPrice;

    private String attendanceOption;

    private String attendanceScheduleJson;

    private BigDecimal priceAmount;

    private BigDecimal originalPriceAmount;

    private Boolean manualPriceOverride;

    /** 课程级优惠快照；订单级 discount_amount 为所有课程优惠之和。 */
    private BigDecimal discountAmount;

    /** 课程项应收：课程价格 + 该课程的附加费 - 该课程的折扣 */
    private BigDecimal amountDue;

    /** 课程项累计已付（按 payment_allocation 汇总，退款为负数回冲） */
    private BigDecimal paidAmount;

    /** 课程项余额：amount_due - paid_amount */
    private BigDecimal balanceAmount;

    private BigDecimal oneOnOneHours;

    private BigDecimal openingUsedHours;

    private Integer oneOnOneUnits;

    private LocalDateTime initialLessonStartAt;

    private LocalDateTime initialLessonEndAt;

    private Long teacherId;

    private Boolean pickupRequiredSnapshot;

    private String pickupAddressSnapshot;

    private Boolean dropoffRequiredSnapshot;

    private String dropoffAddressSnapshot;

    private BigDecimal pickupFeeAmount;

    private BigDecimal dropoffFeeAmount;

    private BigDecimal extraFeeAmount;

    private String extraFeeDetail;

    private String itemStatus;

    /** 停课判定时已确认消耗金额快照，供收据和停课记录追溯。 */
    private BigDecimal stoppedConsumedAmount;

    private Integer stoppedSessionCount;

    private LocalDate stoppedPeriodStartDate;

    private LocalDate stoppedPeriodEndDate;

    private LocalDateTime stoppedAt;

    private String remark;
}
