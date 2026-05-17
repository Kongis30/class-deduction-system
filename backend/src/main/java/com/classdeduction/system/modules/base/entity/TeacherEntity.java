package com.classdeduction.system.modules.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
 * Teacher entity reused by staff scheduling and future teacher login features.
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("teacher")
@Schema(description = "Teacher entity mapped to the teacher table")
public class TeacherEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String teacherNo;

    private String teacherName;

    private String email;

    private String mobile;

    private String subjectTags;

    private String status;

    @TableField("campus_id")
    private Long campusId;

    private LocalDate hireDate;

    private String notes;
}
