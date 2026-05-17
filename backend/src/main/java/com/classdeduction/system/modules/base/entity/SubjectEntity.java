package com.classdeduction.system.modules.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Subject entity.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("subject")
public class SubjectEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("subject_name")
    private String subjectName;

    @TableField("display_order")
    private Integer displayOrder;

    @TableField("status")
    private String status;

    @TableField("notes")
    private String notes;
}
