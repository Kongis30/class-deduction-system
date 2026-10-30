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

    /** V2 教学项目；同一项目各报名方案共用本实体及唯一课表。 */
    private Long groupProjectId;

    private String gradeBand;

    /** 实际上课班级，例如 G1/G2；gradeBand 只代表收费档位。 */
    private String actualClass;

    /** Group-course billing window configured for this grade class. */
    private String billingType;

    private LocalDate startDate;

    private LocalDate endDate;

    /** 旧版假期顺延前保存的用户配置周期终点；当前假期不再修改 endDate。 */
    private LocalDate scheduleBaseEndDate;

    private Integer totalSessions;

    private LocalDate startMonth;

    private LocalDate endMonth;
}
