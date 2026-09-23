package com.classdeduction.system.modules.transport.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 接送司机档案，仅保存接送名单需要的司机名称。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("transport_driver")
public class TransportDriverEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("driver_name")
    private String driverName;

    @TableField("display_order")
    private Integer displayOrder;
}
