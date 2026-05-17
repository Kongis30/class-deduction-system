package com.classdeduction.system.modules.schedule.controller;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import com.classdeduction.system.modules.schedule.dto.LessonOccurrencePageQuery;
import com.classdeduction.system.modules.schedule.service.LessonOccurrenceService;
import com.classdeduction.system.modules.schedule.vo.LessonOccurrenceVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 老师端课表控制器：只返回老师本人名下的计划课次。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/teacher/schedule")
@Tag(name = "Teacher - Schedule")
@RequireRoles({"TEACHER"})
public class TeacherScheduleController {

    private final LessonOccurrenceService lessonOccurrenceService;

    @GetMapping("/occurrences")
    @Operation(summary = "Page the current teacher's lesson occurrences")
    public ApiResponse<PageResponse<LessonOccurrenceVO>> pageOwnOccurrences(
            @ParameterObject LessonOccurrencePageQuery query,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser
    ) {
        if (currentUser.teacherId() == null) {
            throw new BusinessException("TEACHER_BINDING_REQUIRED", "This teacher account is not linked to a teacher profile.");
        }
        query.setTeacherId(currentUser.teacherId());
        return ApiResponse.success(lessonOccurrenceService.pageOccurrences(query));
    }
}
