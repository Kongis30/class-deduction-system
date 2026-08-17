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

    private String packageUnit;

    private BigDecimal packageQuantity;

    private BigDecimal packageTotalPrice;

    private String attendanceOption;

    private String attendanceScheduleJson;

    private BigDecimal priceAmount;

    private BigDecimal originalPriceAmount;

    private Boolean manualPriceOverride;

    private BigDecimal oneOnOneHours;

    private Integer oneOnOneUnits;

    private LocalDateTime initialLessonStartAt;

    private LocalDateTime initialLessonEndAt;

    private Long teacherId;

    private String itemStatus;

    private String remark;
}
