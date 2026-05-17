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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 课时流水实体：课时变化的唯一历史账本。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("hour_ledger")
@Schema(description = "Hour ledger entity")
public class HourLedgerEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long packageId;

    private Long lessonRecordId;

    private Long occurrenceId;

    private String ledgerType;

    private Integer changeUnits;

    private Integer balanceAfterUnits;

    private LocalDate businessDate;

    private String reasonCode;

    private String reasonDesc;

    private Long operatedBy;

    private LocalDateTime operatedAt;
}
