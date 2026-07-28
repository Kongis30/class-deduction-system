package com.classdeduction.system.modules.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * Campus entity.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("campus")
public class CampusEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("campus_name")
    private String campusName;

    @TableField("display_order")
    private Integer displayOrder;

    @TableField("status")
    private String status;

    @TableField("default_pickup_fee_amount")
    private BigDecimal defaultPickupFeeAmount;

    @TableField("default_dropoff_fee_amount")
    private BigDecimal defaultDropoffFeeAmount;

    @TableField("notes")
    private String notes;
}
