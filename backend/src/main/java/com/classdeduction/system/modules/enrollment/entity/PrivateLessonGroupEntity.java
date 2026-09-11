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

/**
 * 私教课组：多个学生共享同一套老师、时间和课次。
 * 课时余额仍保存在成员自己的 course_package 中，课组只负责共享关系。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("private_lesson_group")
@Schema(description = "Private lesson group entity")
public class PrivateLessonGroupEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String groupNo;

    private Long enrollmentOrderId;

    private Long enrollmentOrderItemId;

    private Long courseProductId;

    private String gradeBand;

    private Integer participantCount;

    private BigDecimal secondParticipantMultiplier;

    private BigDecimal thirdParticipantMultiplier;

    private String groupStatus;
}
