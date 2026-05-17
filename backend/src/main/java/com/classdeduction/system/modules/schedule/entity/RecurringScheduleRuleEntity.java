package com.classdeduction.system.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

/**
 * 周期排课规则实体：定义课时包的默认老师、教室和生成窗口。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("recurring_schedule_rule")
@Schema(description = "Recurring schedule rule entity")
public class RecurringScheduleRuleEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long packageId;

    private Long primaryTeacherId;

    private Long defaultClassroomId;

    private String ruleStatus;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer generationHorizonDays;

    private String notes;
}
