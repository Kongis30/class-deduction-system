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
 * 通知模板实体：保存邮件标题与正文模板，供课后通知出站日志复用。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("notification_template")
@Schema(description = "Notification template entity")
public class NotificationTemplateEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String templateCode;

    private String templateName;

    private String channelType;

    private String subjectTemplate;

    private String bodyTemplate;

    private String status;

    private String remark;
}
