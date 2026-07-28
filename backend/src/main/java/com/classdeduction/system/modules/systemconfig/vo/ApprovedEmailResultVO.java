package com.classdeduction.system.modules.systemconfig.vo;

/**
 * 单封管理员确认邮件的投递结果。
 */
public record ApprovedEmailResultVO(
        Boolean sent,
        String message
) {
}
