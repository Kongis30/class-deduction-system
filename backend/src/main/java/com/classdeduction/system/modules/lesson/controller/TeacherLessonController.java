package com.classdeduction.system.modules.lesson.controller;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import com.classdeduction.system.modules.lesson.dto.TeacherLessonConfirmRequest;
import com.classdeduction.system.modules.lesson.service.TeacherSelfLessonService;
import com.classdeduction.system.modules.lesson.vo.LessonRecordConfirmResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 老师端课次控制器：只开放老师本人可用的确认入口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/teacher/lessons")
@Tag(name = "Teacher - Lessons")
@RequireRoles({"TEACHER"})
public class TeacherLessonController {

    private final TeacherSelfLessonService teacherSelfLessonService;

    @PostMapping("/records")
    @Operation(summary = "Teacher confirms one of their own lessons")
    public ApiResponse<LessonRecordConfirmResultVO> confirmOwnLesson(
            @Valid @RequestBody TeacherLessonConfirmRequest request,
            @RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) CurrentUserResponse currentUser
    ) {
        return ApiResponse.success("Confirmed successfully.", teacherSelfLessonService.confirmOwnLesson(request, currentUser));
    }
}
