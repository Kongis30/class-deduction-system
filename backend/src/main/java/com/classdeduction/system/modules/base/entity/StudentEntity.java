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
 * Student entity mapped to the student master table.
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("student")
@Schema(description = "Student entity mapped to the student table")
public class StudentEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String studentNo;

    private String studentName;

    private String englishName;

    private String gender;

    private LocalDate birthDate;

    private String grade;

    private String school;

    private String sourceChannel;

    private String status;

    @TableField("campus_id")
    private Long campusId;

    private String notes;

    private String guardianName;

    private String guardianPhone;

    @TableField("guardian_phone_2")
    private String guardianPhone2;

    private String guardianEmail;

    private String guardianRelationType;

    private Boolean pickupRequired;

    private String pickupAddress;

    private Boolean dropoffRequired;

    private String dropoffAddress;
}
