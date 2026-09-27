package com.classdeduction.system.modules.lesson.controller;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import com.classdeduction.system.modules.lesson.dto.LessonRecordConfirmRequest;
import com.classdeduction.system.modules.lesson.dto.LessonRecordPageQuery;
import com.classdeduction.system.modules.lesson.dto.LessonRecordRejectRequest;
import com.classdeduction.system.modules.lesson.service.LessonRecordService;
import com.classdeduction.system.modules.lesson.vo.LessonRecordConfirmResultVO;
import com.classdeduction.system.modules.lesson.vo.LessonRecordDetailVO;
import com.classdeduction.system.modules.lesson.vo.LessonRecordListItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 实际课次记录控制器：负责课次确认与查询。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/lessons/records")
@Tag(name = "Lesson - Records")
@RequireRoles({"ADMIN", "STAFF", "TEACHER"})
public class LessonRecordController {

    private final LessonRecordService lessonRecordService;

    @GetMapping
    @RequireRoles({"ADMIN", "STAFF", "MANAGER"})
    @Operation(summary = "Page lesson records")
    public ApiResponse<PageResponse<LessonRecordListItemVO>> pageRecords(@ParameterObject LessonRecordPageQuery query) {
        return ApiResponse.success(lessonRecordService.pageRecords(query));
    }

    @GetMapping("/{id}")
    @RequireRoles({"ADMIN", "STAFF", "MANAGER"})
    @Operation(summary = "Get lesson record detail")
    public ApiResponse<LessonRecordDetailVO> getRecord(@PathVariable Long id) {
        return ApiResponse.success(lessonRecordService.getRecord(id));
    }

    @PostMapping
    @RequireRoles({"ADMIN", "STAFF", "MANAGER", "TEACHER"})
    @Operation(summary = "Confirm a lesson by current role")
    public ApiResponse<LessonRecordConfirmResultVO> confirmLesson(
            @Valid @RequestBody LessonRecordConfirmRequest request,
            @RequestAttribute(value = AuthInterceptor.CURRENT_USER_ATTRIBUTE, required = false) CurrentUserResponse currentUser
    ) {
        return ApiResponse.success("Confirmed successfully.", lessonRecordService.confirmLesson(request, currentUser));
    }

    @PostMapping("/{id}/admin-confirm")
    @RequireRoles({"ADMIN", "STAFF", "MANAGER"})
    @Operation(summary = "Admin finally confirms a pending lesson record")
    public ApiResponse<LessonRecordConfirmResultVO> adminConfirmLesson(
            @PathVariable Long id,
            @RequestBody(required = false) LessonRecordConfirmRequest request,
            @RequestAttribute(value = AuthInterceptor.CURRENT_USER_ATTRIBUTE, required = false) CurrentUserResponse currentUser
    ) {
        return ApiResponse.success("Confirmed successfully.", lessonRecordService.adminConfirmLesson(id, request, currentUser));
    }

    @PostMapping("/{id}/reject")
    @RequireRoles({"ADMIN"})
    @Operation(summary = "Admin rejects a pending lesson record")
    public ApiResponse<Void> rejectLesson(
            @PathVariable Long id,
            @Valid @RequestBody LessonRecordRejectRequest request,
            @RequestAttribute(value = AuthInterceptor.CURRENT_USER_ATTRIBUTE, required = false) CurrentUserResponse currentUser
    ) {
        lessonRecordService.rejectLesson(id, request.getRejectReason(), currentUser);
        return ApiResponse.success("Rejected successfully.", null);
    }

    @PostMapping("/{id}/revoke")
    @RequireRoles({"ADMIN"})
    @Operation(summary = "Revoke a confirmed lesson record and roll back the deducted units")
    public ApiResponse<Void> revokeLesson(
            @PathVariable Long id,
            @RequestAttribute(value = AuthInterceptor.CURRENT_USER_ATTRIBUTE, required = false) CurrentUserResponse currentUser
    ) {
        lessonRecordService.revokeLesson(id, currentUser);
        return ApiResponse.success("Revoked successfully.", null);
    }
}
