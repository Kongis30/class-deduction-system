package com.classdeduction.system.modules.finance.entity;

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

/**
 * 课时单价配置 — 每个老师+产品的组合有一个课时单价。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("teacher_pay_rate")
@Schema(description = "Teacher hourly pay rate entity")
public class TeacherPayRateEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long teacherId;

    private Long courseProductId;

    /**
     * Optional student-specific override. A null value is the default product rate.
     */
    private Long studentId;

    private BigDecimal hourlyRate;

    private String notes;
}
