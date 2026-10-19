package com.classdeduction.system.modules.lesson.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/** 管理员撤销课次最终处理的审计记录；原课次记录仍以软删除保留。 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("lesson_record_revoke_audit")
public class LessonRecordRevokeAuditEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long lessonRecordId;

    private Long occurrenceId;

    private Long packageId;

    private String resultStatus;

    private Integer deductedUnits;

    private String reason;

    private Long revokedByUserId;

    private LocalDateTime revokedAt;
}
