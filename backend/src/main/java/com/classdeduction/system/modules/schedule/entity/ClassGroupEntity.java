package com.classdeduction.system.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 班课实体 — 对应 class_group 表。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("class_group")
public class ClassGroupEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String className;

    private String description;

    private Long teacherId;

    private Long classroomId;

    /** 班级所属校区；历史数据允许为空，需由管理员确认后维护。 */
    private Long campusId;

    private BigDecimal listPrice;

    private String scheduleDesc;

    private String status;

    private Long productId;

    private String gradeBand;

    /** 实际上课班级，例如 G1/G2；gradeBand 只代表收费档位。 */
    private String actualClass;

    /** Group-course billing window configured for this grade class. */
    private String billingType;

    private LocalDate startDate;

    private LocalDate endDate;

    /** 用户配置的周期终点；endDate 会因假期顺延，不能作为下次计算基准。 */
    private LocalDate scheduleBaseEndDate;

    private Integer totalSessions;

    private LocalDate startMonth;

    private LocalDate endMonth;
}
