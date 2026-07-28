package com.classdeduction.system.modules.schedule.controller;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import com.classdeduction.system.modules.schedule.service.ClassGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/classes")
@Tag(name = "Schedule - Class Groups")
@RequireRoles({"ADMIN", "STAFF"})
public class ClassGroupController {

    private final ClassGroupService classGroupService;

    @GetMapping
    @RequireRoles({"ADMIN", "STAFF", "TEACHER"})
    @Operation(summary = "List all active class groups")
    public ApiResponse<List<Map<String, Object>>> listClasses(
            @RequestAttribute(value = AuthInterceptor.CURRENT_USER_ATTRIBUTE, required = false) CurrentUserResponse currentUser) {
        return ApiResponse.success(classGroupService.listClasses(currentUser));
    }

    @GetMapping("/{classId}")
    @RequireRoles({"ADMIN", "STAFF", "TEACHER"})
    @Operation(summary = "Get class group detail")
    public ApiResponse<Map<String, Object>> getClassDetail(
            @PathVariable Long classId,
            @RequestAttribute(value = AuthInterceptor.CURRENT_USER_ATTRIBUTE, required = false) CurrentUserResponse currentUser) {
        return ApiResponse.success(classGroupService.getClassDetail(classId, currentUser));
    }

    @GetMapping("/{classId}/students")
    @RequireRoles({"ADMIN", "STAFF", "TEACHER"})
    @Operation(summary = "List enrolled students in a class")
    public ApiResponse<List<Map<String, Object>>> listEnrolledStudents(
            @PathVariable Long classId,
            @RequestAttribute(value = AuthInterceptor.CURRENT_USER_ATTRIBUTE, required = false) CurrentUserResponse currentUser) {
        return ApiResponse.success(classGroupService.listEnrolledStudents(classId, currentUser));
    }

    @GetMapping("/{classId}/occurrences")
    @RequireRoles({"ADMIN", "STAFF", "TEACHER"})
    @Operation(summary = "List planned lesson occurrences for a class")
    public ApiResponse<List<Map<String, Object>>> listClassOccurrences(
            @PathVariable Long classId,
            @RequestAttribute(value = AuthInterceptor.CURRENT_USER_ATTRIBUTE, required = false) CurrentUserResponse currentUser) {
        return ApiResponse.success(classGroupService.listClassOccurrences(classId, currentUser));
    }

    @PostMapping("/{classId}/occurrences/{occurrenceId}/confirm")
    @RequireRoles({"ADMIN", "TEACHER"})
    @Operation(summary = "Unified confirmation for all active students in a class")
    public ApiResponse<Map<String, Object>> confirmLesson(
            @PathVariable Long classId,
            @PathVariable Long occurrenceId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestAttribute(value = AuthInterceptor.CURRENT_USER_ATTRIBUTE, required = false) CurrentUserResponse currentUser) {
        if (body == null) {
            body = Map.of();
        }
        return ApiResponse.success("Class lesson confirmed.", classGroupService.confirmLesson(classId, occurrenceId, body, currentUser));
    }

    @PostMapping("/{classId}/occurrences/{occurrenceId}/reject")
    @RequireRoles({"ADMIN"})
    @Operation(summary = "Reject pending class lesson confirmation")
    public ApiResponse<Void> rejectLesson(
            @PathVariable Long classId,
            @PathVariable Long occurrenceId,
            @RequestBody Map<String, Object> body,
            @RequestAttribute(value = AuthInterceptor.CURRENT_USER_ATTRIBUTE, required = false) CurrentUserResponse currentUser) {
        Object reason = body == null ? null : body.get("rejectReason");
        classGroupService.rejectLesson(classId, occurrenceId, reason == null ? null : String.valueOf(reason), currentUser);
        return ApiResponse.success("Rejected successfully.", null);
    }

    @PutMapping("/{classId}/schedule")
    @RequireRoles({"ADMIN", "STAFF"})
    @Operation(summary = "Update class weekly schedule and rebuild future planned lessons")
    public ApiResponse<Map<String, Object>> updateClassSchedule(
            @PathVariable Long classId,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success("Class schedule updated.", classGroupService.updateClassSchedule(classId, body == null ? Map.of() : body));
    }

    @PostMapping("/{classId}/enroll")
    @RequireRoles({"ADMIN", "STAFF"})
    @Operation(summary = "Enroll a student in a class")
    public ApiResponse<Map<String, Object>> enrollStudent(
            @PathVariable Long classId,
            @RequestBody Map<String, Object> body) {
        // 支持 Number 和 String（前端 transformResponse 会将大数字 ID 转字符串）
        Long studentId = parseLongFromBody(body, "studentId");
        if (studentId == null) {
            throw new IllegalArgumentException("studentId is required.");
        }
        LocalDate enrollmentDate = body.get("enrollmentDate") instanceof String
                ? LocalDate.parse((String) body.get("enrollmentDate")) : LocalDate.now();
        LocalDate expiryDate = body.get("expiryDate") instanceof String
                ? LocalDate.parse((String) body.get("expiryDate"))
                : enrollmentDate.plusMonths(6);
        return ApiResponse.success("Student enrolled.", classGroupService.enrollStudent(classId, studentId, enrollmentDate, expiryDate));
    }

    @PostMapping("/{classId}/send-expiry-reminders")
    @RequireRoles({"ADMIN", "STAFF"})
    @Operation(summary = "Send expiry reminders for expiring students in a class")
    public ApiResponse<Map<String, Object>> sendExpiryReminders(@PathVariable Long classId) {
        return ApiResponse.success("Reminders sent.", classGroupService.sendExpiryReminders(classId));
    }

    @GetMapping("/enrolled/{studentId}")
    @RequireRoles({"ADMIN", "STAFF"})
    @Operation(summary = "List class groups enrolled by a student")
    public ApiResponse<List<Map<String, Object>>> listEnrolledByStudent(@PathVariable Long studentId) {
        return ApiResponse.success(classGroupService.listEnrolledByStudent(studentId));
    }

    @GetMapping("/teacher/{teacherId}")
    @RequireRoles({"ADMIN", "STAFF"})
    @Operation(summary = "List active class groups taught by a teacher")
    public ApiResponse<List<Map<String, Object>>> listByTeacher(@PathVariable Long teacherId) {
        return ApiResponse.success(classGroupService.listByTeacher(teacherId));
    }

    @DeleteMapping("/{classId}")
    @Operation(summary = "Delete a class group (cancel future occurrences, remove enrollments, soft-delete)")
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<String> deleteClassGroup(@PathVariable Long classId) {
        classGroupService.deleteClassGroup(classId);
        return ApiResponse.success("Class group deleted.");
    }

    @GetMapping("/classroom/{classroomId}")
    @RequireRoles({"ADMIN", "STAFF"})
    @Operation(summary = "List active class groups using a classroom")
    public ApiResponse<List<Map<String, Object>>> listByClassroom(@PathVariable Long classroomId) {
        return ApiResponse.success(classGroupService.listByClassroom(classroomId));
    }

    /**
     * 从 body Map 解析 Long 参数，支持 Number 和 String。
     * 前端 transformResponse 会将大数字 ID 转为字符串防止 JS 精度丢失。
     */
    private static Long parseLongFromBody(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
