package com.classdeduction.system.modules.lesson.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.lesson.dto.LessonStatusRulePageQuery;
import com.classdeduction.system.modules.lesson.dto.LessonStatusRuleUpdateRequest;
import com.classdeduction.system.modules.lesson.entity.LessonStatusRuleEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonStatusRuleMapper;
import com.classdeduction.system.modules.lesson.service.LessonStatusRuleService;
import com.classdeduction.system.modules.lesson.vo.LessonStatusRuleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 课次状态规则读取服务：给后台配置页和确认逻辑提供统一规则视图。
 */
@Service
@RequiredArgsConstructor
public class LessonStatusRuleServiceImpl implements LessonStatusRuleService {

    private static final Set<String> ALLOWED_RULE_STATUSES = Set.of("ACTIVE", "DISABLED");
    private static final Set<String> ALLOWED_DEDUCT_MODES = Set.of("NONE", "ACTUAL_UNITS", "PLANNED_UNITS", "FIXED_UNITS");
    private static final Set<String> CORE_STATUS_CODES = Set.of(
            "COMPLETED",
            "FORMAL_LEAVE",
            "TEMP_CANCEL",
            "NO_SHOW",
            "TEACHER_CANCEL",
            "MAKEUP_COMPLETED",
            "SUBSTITUTED",
            "CANCELLED"
    );

    private final LessonStatusRuleMapper lessonStatusRuleMapper;

    @Override
    public PageResponse<LessonStatusRuleVO> pageRules(LessonStatusRulePageQuery query) {
        IPage<LessonStatusRuleEntity> page = lessonStatusRuleMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<LessonStatusRuleEntity>()
                        .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                                .like(LessonStatusRuleEntity::getStatusCode, query.getKeyword())
                                .or()
                                .like(LessonStatusRuleEntity::getStatusName, query.getKeyword()))
                        .eq(StringUtils.hasText(query.getStatus()), LessonStatusRuleEntity::getStatus, query.getStatus())
                        .orderByAsc(LessonStatusRuleEntity::getId)
        );
        if (page.getRecords().isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }
        return PageResponse.of(
                page.getRecords().stream().map(this::toVO).toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize()
        );
    }

    @Override
    public LessonStatusRuleVO getRule(Long id) {
        return toVO(requireRule(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LessonStatusRuleVO updateRule(Long id, LessonStatusRuleUpdateRequest request, Long operatorId) {
        LessonStatusRuleEntity entity = requireRule(id);
        String targetStatus = normalizeStatus(request.getStatus());
        String deductMode = normalizeDeductMode(request.getDeductMode());
        validateCoreRuleStatus(entity.getStatusCode(), targetStatus);
        validateDeductConfiguration(request.getDefaultShouldDeduct(), deductMode, request.getFixedUnits());

        entity.setStatusName(request.getStatusName());
        entity.setDefaultShouldDeduct(Boolean.TRUE.equals(request.getDefaultShouldDeduct()) ? 1 : 0);
        entity.setDeductMode(deductMode);
        entity.setFixedUnits("FIXED_UNITS".equals(deductMode) ? request.getFixedUnits() : null);
        entity.setDefaultShouldExtend(Boolean.TRUE.equals(request.getDefaultShouldExtend()) ? 1 : 0);
        entity.setAllowManualOverride(Boolean.TRUE.equals(request.getAllowManualOverride()) ? 1 : 0);
        entity.setStatus(targetStatus);
        entity.setRemark(request.getRemark());
        entity.setUpdatedBy(operatorId);
        lessonStatusRuleMapper.updateById(entity);
        return toVO(entity);
    }

    private LessonStatusRuleEntity requireRule(Long id) {
        LessonStatusRuleEntity entity = lessonStatusRuleMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("LESSON_STATUS_RULE_NOT_FOUND", "Lesson status rule not found.");
        }
        return entity;
    }

    /**
     * 这些核心状态会被实际排课和消课链路依赖，不能被直接停用，否则老师端和教务端都会断链。
     */
    private void validateCoreRuleStatus(String statusCode, String targetStatus) {
        if (CORE_STATUS_CODES.contains(statusCode) && !"ACTIVE".equals(targetStatus)) {
            throw new BusinessException("CORE_LESSON_STATUS_RULE_MUST_STAY_ACTIVE",
                    "Core lesson status rules must stay active.");
        }
    }

    private void validateDeductConfiguration(Boolean defaultShouldDeduct, String deductMode, Integer fixedUnits) {
        if (Boolean.TRUE.equals(defaultShouldDeduct)) {
            if ("NONE".equals(deductMode)) {
                throw new BusinessException("INVALID_DEDUCT_CONFIGURATION",
                        "Rules that deduct units cannot use the NONE deduct mode.");
            }
            if ("FIXED_UNITS".equals(deductMode) && (fixedUnits == null || fixedUnits <= 0)) {
                throw new BusinessException("INVALID_FIXED_UNITS",
                        "Fixed deduct mode requires a positive fixed unit value.");
            }
            if (!"FIXED_UNITS".equals(deductMode) && fixedUnits != null) {
                throw new BusinessException("UNEXPECTED_FIXED_UNITS",
                        "Fixed units can only be set when the deduct mode is FIXED_UNITS.");
            }
            return;
        }
        if (!"NONE".equals(deductMode)) {
            throw new BusinessException("INVALID_NON_DEDUCT_RULE",
                    "Rules that do not deduct units must use the NONE deduct mode.");
        }
        if (fixedUnits != null) {
            throw new BusinessException("UNEXPECTED_FIXED_UNITS",
                    "Fixed units must be empty when deduction is disabled.");
        }
    }

    private String normalizeStatus(String status) {
        String resolvedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase() : "ACTIVE";
        if (!ALLOWED_RULE_STATUSES.contains(resolvedStatus)) {
            throw new BusinessException("UNSUPPORTED_RULE_STATUS", "Unsupported lesson status rule status.");
        }
        return resolvedStatus;
    }

    private String normalizeDeductMode(String deductMode) {
        String resolvedMode = deductMode == null ? null : deductMode.trim().toUpperCase();
        if (!StringUtils.hasText(resolvedMode) || !ALLOWED_DEDUCT_MODES.contains(resolvedMode)) {
            throw new BusinessException("UNSUPPORTED_DEDUCT_MODE", "Unsupported lesson status deduct mode.");
        }
        return resolvedMode;
    }

    private LessonStatusRuleVO toVO(LessonStatusRuleEntity entity) {
        return LessonStatusRuleVO.builder()
                .id(entity.getId())
                .statusCode(entity.getStatusCode())
                .statusName(entity.getStatusName())
                .defaultShouldDeduct(entity.getDefaultShouldDeduct() != null && entity.getDefaultShouldDeduct() == 1)
                .deductMode(entity.getDeductMode())
                .fixedUnits(entity.getFixedUnits())
                .defaultShouldExtend(entity.getDefaultShouldExtend() != null && entity.getDefaultShouldExtend() == 1)
                .allowManualOverride(entity.getAllowManualOverride() != null && entity.getAllowManualOverride() == 1)
                .status(entity.getStatus())
                .remark(entity.getRemark())
                .build();
    }
}
