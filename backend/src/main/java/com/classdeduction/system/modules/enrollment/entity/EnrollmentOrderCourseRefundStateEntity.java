package com.classdeduction.system.modules.enrollment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A refund stop decision belongs to one receipt, participant, and course line. */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("enrollment_order_course_refund_state")
public class EnrollmentOrderCourseRefundStateEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long enrollmentOrderId;

    private Long enrollmentOrderItemId;

    private Long studentId;

    private Integer participantIndex;

    private String itemStatus;

    private BigDecimal stoppedConsumedAmount;

    private Integer stoppedSessionCount;

    private LocalDate stoppedPeriodStartDate;

    private LocalDate stoppedPeriodEndDate;

    private LocalDateTime stoppedAt;
}
