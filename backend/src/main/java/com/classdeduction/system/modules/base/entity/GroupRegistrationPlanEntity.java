package com.classdeduction.system.modules.base.entity;

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

/** 班课报名方案：定义报名覆盖范围、购买数量与价格，不拥有独立课表。 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("group_registration_plan")
public class GroupRegistrationPlanEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long projectId;

    private String planCode;

    private String planName;

    private String coverageType;

    private String termCode;

    private LocalDate coverageStartDate;

    private LocalDate coverageEndDate;

    private String packageUnit;

    private BigDecimal packageQuantity;

    private BigDecimal fullDayPrice;

    private BigDecimal halfDayPrice;

    private String currencyCode;

    private Integer displayOrder;

    private String status;
}
