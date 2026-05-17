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

/**
 * 报名单实体：记录学生一次报名交易的商业信息。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("enrollment_order")
@Schema(description = "报名订单实体")
public class EnrollmentOrderEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String enrollmentNo;

    private Long studentId;

    private Long classId;

    private String orderStatus;

    private Long cashierUserId;

    private String handledByName;

    private String currencyCode;

    private BigDecimal originalAmount;

    private BigDecimal discountAmount;

    private BigDecimal receivedAmount;

    private String paymentStatus;

    private String remark;
}
