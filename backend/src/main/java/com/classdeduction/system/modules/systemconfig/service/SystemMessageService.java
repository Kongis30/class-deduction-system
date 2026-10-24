package com.classdeduction.system.modules.systemconfig.service;

import com.classdeduction.system.modules.systemconfig.vo.SystemMessageInboxVO;

public interface SystemMessageService {

    SystemMessageInboxVO getInbox(Long userId);

    void markRead(Long messageId, Long userId);

    int refreshCurrentWeekBirthdayMessages();
}
