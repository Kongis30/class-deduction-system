package com.classdeduction.system.modules.systemconfig.dto;

import lombok.Builder;

/**
 * 已由管理员预览并确认的单封邮件。
 *
 * <p>仅供受控业务流程在服务层调用，不提供通用公开发送接口。</p>
 */
@Builder
public record ApprovedEmailRequest(
        String recipient,
        String subject,
        String body,
        Long studentId,
        Long packageId,
        String source
) {
}
