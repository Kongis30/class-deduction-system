package com.classdeduction.system.modules.lesson.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.lesson.dto.LessonStatusRulePageQuery;
import com.classdeduction.system.modules.lesson.dto.LessonStatusRuleUpdateRequest;
import com.classdeduction.system.modules.lesson.vo.LessonStatusRuleVO;

public interface LessonStatusRuleService {

    PageResponse<LessonStatusRuleVO> pageRules(LessonStatusRulePageQuery query);

    LessonStatusRuleVO getRule(Long id);

    LessonStatusRuleVO updateRule(Long id, LessonStatusRuleUpdateRequest request, Long operatorId);
}
