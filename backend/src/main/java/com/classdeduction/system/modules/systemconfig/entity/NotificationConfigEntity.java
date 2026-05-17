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
 * 邮件发送配置：系统级发件人信息与白名单开关（单行，id=1）。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("notification_config")
@Schema(description = "Notification config entity")
public class NotificationConfigEntity extends BaseEntity {

    @TableId
    private Long id;

    /** 邮件发送总开关 */
    private Integer enabled;

    /** SMTP 发件人地址 */
    private String fromAddress;

    /** SMTP 发件人显示名称 */
    private String fromName;

    /** SMTP 授权码 */
    private String authCode;

    /** 严格白名单模式：1=只允许 allowed_recipients，0=禁用白名单 */
    private Integer strictAllowlist;

    /** 允许的收件人 JSON 数组 */
    private String allowedRecipients;

    /** 课时不足预警阈值（单位：0.5h，即 2=1小时） */
    private Integer lowBalanceThreshold;

    /** 课时不足通知收件人 JSON 数组 */
    private String lowBalanceRecipients;
}