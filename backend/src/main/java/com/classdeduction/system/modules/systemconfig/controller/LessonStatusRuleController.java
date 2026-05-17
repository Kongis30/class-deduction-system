package com.classdeduction.system.modules.systemconfig.controller;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import com.classdeduction.system.modules.lesson.dto.LessonStatusRulePageQuery;
import com.classdeduction.system.modules.lesson.dto.LessonStatusRuleUpdateRequest;
import com.classdeduction.system.modules.lesson.service.LessonStatusRuleService;
import com.classdeduction.system.modules.lesson.vo.LessonStatusRuleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课次状态规则控制器：供后台读取不同结果状态的默认规则。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/system/lesson-status-rules")
@Tag(name = "System - Lesson Status Rules")
@RequireRoles({"ADMIN", "STAFF"})
public class LessonStatusRuleController {

    private final LessonStatusRuleService lessonStatusRuleService;

    @GetMapping
    @Operation(summary = "Page lesson status rules")
    public ApiResponse<PageResponse<LessonStatusRuleVO>> pageRules(@ParameterObject LessonStatusRulePageQuery query) {
        return ApiResponse.success(lessonStatusRuleService.pageRules(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get lesson status rule detail")
    public ApiResponse<LessonStatusRuleVO> getRule(@PathVariable Long id) {
        return ApiResponse.success(lessonStatusRuleService.getRule(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update lesson status rule")
    @RequireRoles({"ADMIN"})
    public ApiResponse<LessonStatusRuleVO> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody LessonStatusRuleUpdateRequest request,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser
    ) {
        return ApiResponse.success("Saved successfully.",
                lessonStatusRuleService.updateRule(id, request, currentUser.userId()));
    }
}
