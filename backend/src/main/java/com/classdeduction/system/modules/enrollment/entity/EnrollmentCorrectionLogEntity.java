package com.classdeduction.system.modules.enrollment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 原报名更正审计，沿用系统现有的 sys_operation_log 表。 */
@Getter
@Setter
@TableName("sys_operation_log")
public class EnrollmentCorrectionLogEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long operatorUserId;
    private String operatorNameSnapshot;
    private String bizType;
    private Long bizId;
    private String operationCode;
    private String operationDesc;
    private String requestSnapshot;
    private String responseSnapshot;
    private LocalDateTime operateAt;
}
