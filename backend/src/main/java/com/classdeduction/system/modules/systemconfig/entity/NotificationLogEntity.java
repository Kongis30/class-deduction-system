package com.classdeduction.system.modules.systemconfig.entity;

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

import java.time.LocalDateTime;

/**
 * 通知日志实体：第一阶段只做出站日志，不让通知能力阻塞主业务事务。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("notification_log")
@Schema(description = "Notification outbox log entity")
public class NotificationLogEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String channelType;

    private Long templateId;

    private Long studentId;

    private Long packageId;

    private Long lessonRecordId;

    private String recipient;

    private String sendStatus;

    @TableField("request_payload")
    private String requestPayload;

    @TableField("response_payload")
    private String responsePayload;

    private String errorMessage;

    private LocalDateTime scheduledAt;

    private LocalDateTime sentAt;

    private Integer retryCount;
}
