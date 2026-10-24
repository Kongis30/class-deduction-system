package com.classdeduction.system.modules.systemconfig.vo;

import java.util.List;

/** 当前管理员的站内消息和未读数量。 */
public record SystemMessageInboxVO(
        Long unreadCount,
        List<SystemMessageVO> messages
) {
}
