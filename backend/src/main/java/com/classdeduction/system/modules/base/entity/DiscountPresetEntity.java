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
 * Discount preset entity.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("discount_preset")
public class DiscountPresetEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("preset_name")
    private String presetName;

    @TableField("discount_type")
    private String discountType;

    @TableField("discount_value")
    private BigDecimal discountValue;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("status")
    private String status;
}
