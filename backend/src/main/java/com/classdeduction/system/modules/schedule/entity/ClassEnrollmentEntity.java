package com.classdeduction.system.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

/**
 * 班课报名记录 — 对应 class_enrollment 表。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("class_enrollment")
public class ClassEnrollmentEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long classId;

    private Long studentId;

    private Long enrollmentOrderItemId;

    /** 同一学生同一班级续课时，指向首段权益；多人订单中的每位学生独立归属。 */
    private Long logicalCourseId;

    /** 每个订单项目独立生成一份班课权益，续费不得覆盖既有权益。 */
    private Long groupProjectId;

    private Long groupRegistrationPlanId;

    private LocalDate enrollmentDate;

    private LocalDate expiryDate;

    /** 报名时的计费类型快照：PER_SESSION / MONTHLY。 */
    private String billingType;

    /** 购买数量快照：课次数（PER_SESSION）或月数（MONTHLY）。 */
    private Integer purchasedQuantity;

    private String attendanceOption;

    private String status;
}
