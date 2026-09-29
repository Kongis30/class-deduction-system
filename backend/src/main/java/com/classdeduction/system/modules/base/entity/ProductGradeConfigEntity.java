package com.classdeduction.system.modules.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 课程产品维度的 grade 名称、显示顺序和共享班级周期配置。
 *
 * <p>grade_band 是散落在 course_price_option / class_group / teacher_pay_rate
 * 中的自由文本，本表保存产品级 grade 配置；改名时由服务层在同一事务内级联更新
 * 业务表的 grade_band，历史订单快照不动。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_grade_config")
public class ProductGradeConfigEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("course_product_id")
    private Long courseProductId;

    @TableField("grade_label")
    private String gradeLabel;

    @TableField("display_order")
    private Integer displayOrder;

    @TableField("class_start_date")
    private LocalDate classStartDate;

    @TableField("class_end_date")
    private LocalDate classEndDate;

    @TableField("class_period_configured")
    private Boolean classPeriodConfigured;
}
