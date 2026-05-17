package com.classdeduction.system.modules.lesson.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 实际课次记录实体：记录一节计划课最终发生了什么。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("lesson_record")
@Schema(description = "Lesson record entity")
public class LessonRecordEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long occurrenceId;

    private Long packageId;

    private Long studentId;

    private Long plannedTeacherId;

    private Long actualTeacherId;

    private Long plannedClassroomId;

    private Long actualClassroomId;

    private String resultStatus;

    private LocalDateTime actualStartAt;

    private LocalDateTime actualEndAt;

    private Integer actualUnits;

    private Integer shouldDeduct;

    private Integer deductedUnits;

    private Integer shouldExtendEndDate;

    private Long confirmedByUserId;

    private LocalDateTime confirmedAt;

    private String recordSource;

    private String notes;
}
