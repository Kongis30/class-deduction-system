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
import java.time.LocalDateTime;

/**
 * 周期排课规则实体：定义课时包的默认老师、教室和重复时段。
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

    /** Shared private lesson group; packageId remains for legacy single-student schedules. */
    private Long privateLessonGroupId;

    /** Previous schedule period replaced by this rule, if any. */
    private Long supersedesRuleId;

    /** Replacement rule created when this period was superseded. */
    private Long supersededByRuleId;

    /** 旧规则复用 ID 时固定的分配周期起点，防止余额刷新重建课位后误清零消课。 */
    private LocalDateTime allocationPeriodStartedAt;

    private Long primaryTeacherId;

    private Long defaultClassroomId;

    private String ruleStatus;

    private LocalDate startDate;

    private LocalDate endDate;

    private String notes;
}
