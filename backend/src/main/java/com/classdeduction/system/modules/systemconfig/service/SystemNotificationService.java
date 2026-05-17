package com.classdeduction.system.modules.systemconfig.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.systemconfig.dto.BrandConfigUpdateRequest;
import com.classdeduction.system.modules.systemconfig.dto.NotificationBroadcastRequest;
import com.classdeduction.system.modules.systemconfig.dto.NotificationConfigUpdateRequest;
import com.classdeduction.system.modules.systemconfig.dto.NotificationDispatchRequest;
import com.classdeduction.system.modules.systemconfig.dto.NotificationLogPageQuery;
import com.classdeduction.system.modules.systemconfig.dto.NotificationTemplatePageQuery;
import com.classdeduction.system.modules.systemconfig.dto.NotificationTemplateUpdateRequest;
import com.classdeduction.system.modules.systemconfig.dto.NotificationTriggerRuleUpdateRequest;
import com.classdeduction.system.modules.systemconfig.vo.BalanceConsistencyVO;
import com.classdeduction.system.modules.systemconfig.vo.BroadcastResultVO;
import com.classdeduction.system.modules.systemconfig.vo.BrandConfigVO;
import com.classdeduction.system.modules.systemconfig.vo.NotificationConfigVO;
import com.classdeduction.system.modules.systemconfig.vo.NotificationDispatchResultVO;
import com.classdeduction.system.modules.systemconfig.vo.NotificationLogVO;
import com.classdeduction.system.modules.systemconfig.vo.NotificationTemplateVO;
import com.classdeduction.system.modules.systemconfig.vo.NotificationTriggerRuleVO;

import java.util.List;

public interface SystemNotificationService {

    BrandConfigVO getCurrentBrandConfig();

    BrandConfigVO upsertCurrentBrandConfig(BrandConfigUpdateRequest request, Long operatorId);

    PageResponse<NotificationTemplateVO> pageTemplates(NotificationTemplatePageQuery query);

    NotificationTemplateVO getTemplate(Long id);

    NotificationTemplateVO updateTemplate(Long id, NotificationTemplateUpdateRequest request, Long operatorId);

    PageResponse<NotificationLogVO> pageLogs(NotificationLogPageQuery query);

    NotificationLogVO getLog(Long id);

    void requeueFailedLog(Long id, Long operatorId);

    NotificationDispatchResultVO dispatchPendingEmails(NotificationDispatchRequest request, Long operatorId);

    BalanceConsistencyVO checkBalanceConsistency();

    // ── Sender Config ───────────────────────────────────

    NotificationConfigVO getNotificationConfig();

    NotificationConfigVO updateNotificationConfig(NotificationConfigUpdateRequest request, Long operatorId);

    void sendTestEmail(Long operatorId);

    // ── Trigger Rules ───────────────────────────────────

    List<NotificationTriggerRuleVO> listTriggerRules();

    NotificationTriggerRuleVO updateTriggerRule(Long id, NotificationTriggerRuleUpdateRequest request, Long operatorId);

    // ── Broadcast ───────────────────────────────────────

    BroadcastResultVO broadcast(NotificationBroadcastRequest request, Long operatorId);
}
