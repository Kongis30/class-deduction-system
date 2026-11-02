package com.classdeduction.system.modules.schedule.controller;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import com.classdeduction.system.modules.schedule.dto.LessonOccurrencePageQuery;
import com.classdeduction.system.modules.schedule.dto.OneOnOneDeductionQuery;
import com.classdeduction.system.modules.schedule.service.LessonOccurrenceService;
import com.classdeduction.system.modules.schedule.service.PrivateSignatureHistoryService;
import com.classdeduction.system.modules.schedule.vo.LessonOccurrenceVO;
import com.classdeduction.system.modules.schedule.vo.DailyPrivateLessonsVO;
import com.classdeduction.system.modules.schedule.vo.OneOnOneTeacherVO;
import com.classdeduction.system.modules.schedule.vo.PrivateSignatureHistoryVO;
import com.classdeduction.system.modules.schedule.vo.StudentScheduleSlotVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 计划课次控制器：负责课次列表查询。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/schedule/occurrences")
@Tag(name = "Schedule - Lesson Occurrences")
@RequireRoles({"ADMIN", "STAFF", "MANAGER", "TEACHER", "STUDENT"})
public class LessonOccurrenceController {

    private final LessonOccurrenceService lessonOccurrenceService;
    private final PrivateSignatureHistoryService privateSignatureHistoryService;

    @GetMapping("/daily-private-lessons")
    @RequireRoles({"ADMIN", "STAFF", "MANAGER"})
    @Operation(summary = "List private lessons on a date")
    public ApiResponse<DailyPrivateLessonsVO> dailyPrivateLessons(@RequestParam LocalDate date) {
        return ApiResponse.success(lessonOccurrenceService.listDailyPrivateLessons(date));
    }

    @GetMapping
    @Operation(summary = "Page lesson occurrences")
    public ApiResponse<PageResponse<LessonOccurrenceVO>> pageOccurrences(
            @ParameterObject LessonOccurrencePageQuery query,
            @RequestAttribute(value = AuthInterceptor.CURRENT_USER_ATTRIBUTE, required = false) CurrentUserResponse currentUser
    ) {
        return ApiResponse.success(lessonOccurrenceService.pageOccurrences(query, currentUser));
    }

    @GetMapping("/one-on-one-deductions")
    @RequireRoles({"ADMIN", "STAFF", "MANAGER", "TEACHER"})
    @Operation(summary = "List one-on-one deduction sheets grouped by teacher, student, and package")
    public ApiResponse<List<OneOnOneTeacherVO>> listOneOnOneDeductions(
            @ParameterObject OneOnOneDeductionQuery query,
            @RequestAttribute(value = AuthInterceptor.CURRENT_USER_ATTRIBUTE, required = false) CurrentUserResponse currentUser
    ) {
        return ApiResponse.success(lessonOccurrenceService.listOneOnOneDeductions(query, currentUser));
    }

    @GetMapping("/private-signature-history/{packageId}")
    @RequireRoles({"ADMIN", "STAFF", "MANAGER"})
    @Operation(summary = "List verified legacy signature rows for a private lesson package")
    public ApiResponse<List<PrivateSignatureHistoryVO>> listPrivateSignatureHistory(@PathVariable Long packageId) {
        return ApiResponse.success(privateSignatureHistoryService.listByPackage(packageId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get lesson occurrence detail by ID")
    public ApiResponse<LessonOccurrenceVO> getOccurrence(
            @PathVariable Long id,
            @RequestAttribute(value = AuthInterceptor.CURRENT_USER_ATTRIBUTE, required = false) CurrentUserResponse currentUser
    ) {
        return ApiResponse.success(lessonOccurrenceService.getOccurrence(id, currentUser));
    }

    @GetMapping("/student/{studentId}/active")
    @RequireRoles({"ADMIN", "STAFF", "MANAGER"})
    @Operation(summary = "Get student's active future schedule slots for calendar display")
    public ApiResponse<List<StudentScheduleSlotVO>> getActiveStudentSchedule(@PathVariable Long studentId) {
        return ApiResponse.success(lessonOccurrenceService.getActiveStudentSchedule(studentId));
    }
}
