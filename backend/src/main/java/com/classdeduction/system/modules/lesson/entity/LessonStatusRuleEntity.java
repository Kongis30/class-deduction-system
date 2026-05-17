package com.classdeduction.system.modules.lesson.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 课次状态规则实体：定义不同结果状态默认是否扣课与如何扣课。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("lesson_status_rule")
@Schema(description = "Lesson status rule entity")
public class LessonStatusRuleEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String statusCode;

    private String statusName;

    private Integer defaultShouldDeduct;

    private String deductMode;

    private Integer fixedUnits;

    private Integer defaultShouldExtend;

    private Integer allowManualOverride;

    private String status;

    private String remark;
}
