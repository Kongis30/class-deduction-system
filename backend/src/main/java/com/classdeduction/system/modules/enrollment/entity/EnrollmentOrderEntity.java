package com.classdeduction.system.modules.enrollment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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

    private Long campusId;

    private String campusNameSnapshot;

    private String orderStatus;

    private Long cashierUserId;

    private String handledByName;

    private Long registrationOperatorUserId;

    private String registrationOperatorName;

    private LocalDate signupDate;

    private LocalDate periodStartDate;

    private LocalDate periodEndDate;

    private String studentNameSnapshot;

    private String studentClassSnapshot;

    private String gradeSnapshot;

    private String schoolSnapshot;

    private String parentPhoneSnapshot;

    @TableField("parent_phone_2_snapshot")
    private String parentPhone2Snapshot;

    private Boolean pickupRequiredSnapshot;

    private String pickupAddressSnapshot;

    private Boolean dropoffRequiredSnapshot;

    private String dropoffAddressSnapshot;

    private String currencyCode;

    private BigDecimal tuitionSubtotalAmount;

    private BigDecimal pickupFeeAmount;

    private BigDecimal dropoffFeeAmount;

    private BigDecimal extraFeeAmount;

    private String extraFeeDetail;

    private BigDecimal originalAmount;

    private BigDecimal discountAmount;

    private String discountDetail;

    private BigDecimal receivedAmount;

    private BigDecimal amountDue;

    private BigDecimal balanceAmount;

    private String paymentStatus;

    private String remark;
}
