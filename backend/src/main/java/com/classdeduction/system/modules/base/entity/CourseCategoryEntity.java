package com.classdeduction.system.modules.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Course category configuration entity.
 *
 * <p>The set of category codes is fixed by the system; only the display name and
 * display order are editable so staff can rename and reorder the top-level course
 * groups shown in the course-creation and registration screens.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course_category")
public class CourseCategoryEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("type_code")
    private String typeCode;

    @TableField("display_name")
    private String displayName;

    @TableField("display_order")
    private Integer displayOrder;

    @TableField("status")
    private String status;
}
