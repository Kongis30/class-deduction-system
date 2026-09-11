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

/** 报名单参与学生快照：一张 receipt 可以关联多个学生。 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("enrollment_order_participant")
@Schema(description = "Enrollment order participant entity")
public class EnrollmentOrderParticipantEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long enrollmentOrderId;

    private Long studentId;

    private Integer participantIndex;

    private String studentNameSnapshot;

    private String studentNoSnapshot;

    private String gradeSnapshot;

    private String schoolSnapshot;

    private String studentClassSnapshot;

    private String guardianNameSnapshot;

    private String guardianPhoneSnapshot;

    private String guardianPhone2Snapshot;

    private String guardianEmailSnapshot;

    private Boolean pickupRequiredSnapshot;

    private String pickupAddressSnapshot;

    private Boolean dropoffRequiredSnapshot;

    private String dropoffAddressSnapshot;

    /** 该学生在本次报名中的独立账务快照。 */
    private BigDecimal grossAmount;

    private BigDecimal discountAmount;

    private String discountDetail;

    private BigDecimal amountDue;

    private BigDecimal paidAmount;

    private BigDecimal balanceAmount;
}
