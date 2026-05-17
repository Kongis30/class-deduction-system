package com.classdeduction.system.modules.systemconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 通知触发规则：控制何时、给谁发送邮件通知。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("notification_trigger_rule")
@Schema(description = "Notification trigger rule entity")
public class NotificationTriggerRuleEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 触发类型：LESSON_CONFIRMED / LOW_BALANCE */
    private String triggerType;

    /** 关联的通知模板 ID */
    private Long templateId;

    /** 是否启用 */
    private Integer enabled;

    /** 收件人类型：GUARDIAN_EMAIL / CUSTOM_EMAIL */
    private String recipientType;

    /** 自定义收件人 JSON 数组（recipient_type=CUSTOM_EMAIL 时使用） */
    private String customRecipients;

    /** 阈值（LOW_BALANCE 用，单位：0.5h） */
    private Integer thresholdValue;

    /** 备注 */
    private String remark;
}