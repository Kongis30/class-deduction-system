package com.classdeduction.system.modules.lesson.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.lesson.dto.LessonStatusRulePageQuery;
import com.classdeduction.system.modules.lesson.dto.LessonStatusRuleUpdateRequest;
import com.classdeduction.system.modules.lesson.entity.LessonStatusRuleEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonStatusRuleMapper;
import com.classdeduction.system.modules.lesson.service.impl.LessonStatusRuleServiceImpl;
import com.classdeduction.system.modules.lesson.vo.LessonStatusRuleVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 课次状态规则服务测试：确保规则配置既可维护，也不会把核心业务状态配坏。
 */
@ExtendWith(MockitoExtension.class)
class LessonStatusRuleServiceImplTest {

    @Mock
    private LessonStatusRuleMapper lessonStatusRuleMapper;

    @Captor
    private ArgumentCaptor<LessonStatusRuleEntity> entityCaptor;

    private LessonStatusRuleServiceImpl lessonStatusRuleService;

    @BeforeEach
    void setUp() {
        lessonStatusRuleService = new LessonStatusRuleServiceImpl(lessonStatusRuleMapper);
    }

    @Test
    void shouldPageRulesWithQueryObject() {
        Page<LessonStatusRuleEntity> page = new Page<>(1, 10);
        page.setRecords(List.of(buildCompletedRule()));
        page.setTotal(1);
        when(lessonStatusRuleMapper.selectPage(any(), any())).thenReturn(page);

        LessonStatusRulePageQuery query = new LessonStatusRulePageQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setKeyword("COMPLETED");

        LessonStatusRuleVO result = lessonStatusRuleService.pageRules(query).records().get(0);

        assertThat(result.statusCode()).isEqualTo("COMPLETED");
        assertThat(result.defaultShouldDeduct()).isTrue();
    }

    @Test
    void shouldUpdateFixedUnitRuleSafely() {
        when(lessonStatusRuleMapper.selectById(30003L)).thenReturn(buildTempCancelRule());

        LessonStatusRuleUpdateRequest request = new LessonStatusRuleUpdateRequest();
        request.setStatusName("Temporary cancellation");
        request.setDefaultShouldDeduct(true);
        request.setDeductMode("FIXED_UNITS");
        request.setFixedUnits(2);
        request.setDefaultShouldExtend(true);
        request.setAllowManualOverride(true);
        request.setStatus("ACTIVE");
        request.setRemark("Deduct one hour for late cancellation");

        LessonStatusRuleVO result = lessonStatusRuleService.updateRule(30003L, request, 9001L);

        verify(lessonStatusRuleMapper).updateById(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getFixedUnits()).isEqualTo(2);
        assertThat(entityCaptor.getValue().getDeductMode()).isEqualTo("FIXED_UNITS");
        assertThat(entityCaptor.getValue().getUpdatedBy()).isEqualTo(9001L);
        assertThat(result.fixedUnits()).isEqualTo(2);
        assertThat(result.defaultShouldDeduct()).isTrue();
    }

    @Test
    void shouldRejectInvalidFixedUnitConfiguration() {
        when(lessonStatusRuleMapper.selectById(30003L)).thenReturn(buildTempCancelRule());

        LessonStatusRuleUpdateRequest request = new LessonStatusRuleUpdateRequest();
        request.setStatusName("Temporary cancellation");
        request.setDefaultShouldDeduct(true);
        request.setDeductMode("FIXED_UNITS");
        request.setDefaultShouldExtend(true);
        request.setAllowManualOverride(true);
        request.setStatus("ACTIVE");

        assertThatThrownBy(() -> lessonStatusRuleService.updateRule(30003L, request, 9001L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Fixed deduct mode requires a positive fixed unit value.");
    }

    @Test
    void shouldRejectDisablingCoreRule() {
        when(lessonStatusRuleMapper.selectById(30001L)).thenReturn(buildCompletedRule());

        LessonStatusRuleUpdateRequest request = new LessonStatusRuleUpdateRequest();
        request.setStatusName("Completed");
        request.setDefaultShouldDeduct(true);
        request.setDeductMode("ACTUAL_UNITS");
        request.setDefaultShouldExtend(false);
        request.setAllowManualOverride(true);
        request.setStatus("DISABLED");

        assertThatThrownBy(() -> lessonStatusRuleService.updateRule(30001L, request, 9001L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Core lesson status rules must stay active.");
    }

    private LessonStatusRuleEntity buildCompletedRule() {
        LessonStatusRuleEntity entity = new LessonStatusRuleEntity();
        entity.setId(30001L);
        entity.setStatusCode("COMPLETED");
        entity.setStatusName("Completed");
        entity.setDefaultShouldDeduct(1);
        entity.setDeductMode("ACTUAL_UNITS");
        entity.setDefaultShouldExtend(0);
        entity.setAllowManualOverride(1);
        entity.setStatus("ACTIVE");
        return entity;
    }

    private LessonStatusRuleEntity buildTempCancelRule() {
        LessonStatusRuleEntity entity = new LessonStatusRuleEntity();
        entity.setId(30003L);
        entity.setStatusCode("TEMP_CANCEL");
        entity.setStatusName("Temporary cancellation");
        entity.setDefaultShouldDeduct(0);
        entity.setDeductMode("NONE");
        entity.setFixedUnits(null);
        entity.setDefaultShouldExtend(1);
        entity.setAllowManualOverride(1);
        entity.setStatus("ACTIVE");
        return entity;
    }
}
