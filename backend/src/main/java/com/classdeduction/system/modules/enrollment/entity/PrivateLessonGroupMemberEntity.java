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

/** 私教课组成员：关联学生和该学生独立使用的课时包。 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("private_lesson_group_member")
@Schema(description = "Private lesson group member entity")
public class PrivateLessonGroupMemberEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long privateLessonGroupId;

    private Long studentId;

    private Long packageId;

    private Integer participantIndex;

    private BigDecimal priceMultiplier;

    private BigDecimal priceAmount;

    private String memberStatus;
}
