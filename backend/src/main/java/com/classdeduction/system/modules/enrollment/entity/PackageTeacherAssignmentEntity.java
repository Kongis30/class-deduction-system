package com.classdeduction.system.modules.enrollment.entity;

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
import java.time.LocalDateTime;

/**
 * 主老师分配实体：记录课时包的主老师变化历史。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("package_teacher_assignment")
@Schema(description = "课时包主老师分配实体")
public class PackageTeacherAssignmentEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long packageId;

    private Long teacherId;

    private Integer allocatedUnits;

    private String subjectName;

    private BigDecimal hourlyRateSnapshot;

    private Integer isPrimary;

    private LocalDateTime effectiveFrom;

    private LocalDateTime effectiveTo;

    private String assignmentStatus;

    private String changeReason;
}
