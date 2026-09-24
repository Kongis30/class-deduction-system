package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleRuleCreateRequest;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleRulePageQuery;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleRuleUpdateRequest;
import com.classdeduction.system.modules.schedule.vo.RecurringScheduleRuleDetailVO;
import com.classdeduction.system.modules.schedule.vo.RecurringScheduleRuleListItemVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleStudentOptionVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleRuleCreateResultVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleRuleUpdateResultVO;

import java.util.List;

public interface RecurringScheduleRuleService {

    /** 查询排课页应显示的在读 1 对 1 学生，避免前端逐个学生检查课时包。 */
    List<ScheduleStudentOptionVO> listStudentsWithActiveOneOnOnePackages();

    PageResponse<RecurringScheduleRuleListItemVO> pageRules(RecurringScheduleRulePageQuery query);

    RecurringScheduleRuleDetailVO getRule(Long id);

    ScheduleRuleCreateResultVO createRule(RecurringScheduleRuleCreateRequest request);

    ScheduleRuleUpdateResultVO updateRule(Long id, RecurringScheduleRuleUpdateRequest request);

    /**
     * 滚动续期：为所有 ACTIVE 规则补足到 horizon 的未来课次，幂等去重。返回新增课次数。
     */
    int renewActiveRuleOccurrences();
}
