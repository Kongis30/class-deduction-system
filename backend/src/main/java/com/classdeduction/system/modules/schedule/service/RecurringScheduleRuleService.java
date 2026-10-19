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
    List<ScheduleStudentOptionVO> listOneOnOneScheduleStudents();

    PageResponse<RecurringScheduleRuleListItemVO> pageRules(RecurringScheduleRulePageQuery query);

    RecurringScheduleRuleDetailVO getRule(Long id);

    ScheduleRuleCreateResultVO createRule(RecurringScheduleRuleCreateRequest request);

    ScheduleRuleUpdateResultVO updateRule(Long id, RecurringScheduleRuleUpdateRequest request);

    /** 按当前课包余额重建未来未确认课次，保留已确认和人工调整过的课次。 */
    void refreshPackageSchedule(Long packageId);

    /** 按当前周期各课位已保存额度校准未来普通课次，保留历史及有记录的课次。 */
    void reconcilePackageSchedule(Long packageId);

    /** 只校准一个课位的未来未处理课次，供消课页单课时长调整使用。 */
    void reconcileScheduleSlot(Long packageId, Long scheduleSlotId);

    /** 更新管理员明确录入的当前排课周期起始课时。 */
    void setPackageStartingHours(Long packageId, int startingUnits);

    /**
     * 滚动续期：为所有 ACTIVE 规则补齐尚未生成的分配课次，幂等去重。返回新增课次数。
     */
    int renewActiveRuleOccurrences();
}
