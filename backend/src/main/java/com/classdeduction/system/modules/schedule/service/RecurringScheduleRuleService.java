package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleRuleCreateRequest;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleRulePageQuery;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleRuleUpdateRequest;
import com.classdeduction.system.modules.schedule.vo.RecurringScheduleRuleDetailVO;
import com.classdeduction.system.modules.schedule.vo.RecurringScheduleRuleListItemVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleRuleCreateResultVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleRuleUpdateResultVO;

public interface RecurringScheduleRuleService {

    PageResponse<RecurringScheduleRuleListItemVO> pageRules(RecurringScheduleRulePageQuery query);

    RecurringScheduleRuleDetailVO getRule(Long id);

    ScheduleRuleCreateResultVO createRule(RecurringScheduleRuleCreateRequest request);

    ScheduleRuleUpdateResultVO updateRule(Long id, RecurringScheduleRuleUpdateRequest request);
}
