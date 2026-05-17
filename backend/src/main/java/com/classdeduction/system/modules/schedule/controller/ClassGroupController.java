package com.classdeduction.system.modules.schedule.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.schedule.service.ClassGroupService;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final LessonRecordMapper lessonRecordMapper;

    @GetMapping
    @Operation(summary = "List all active class groups")
    public ApiResponse<List<Map<String, Object>>> listClasses() {
        return ApiResponse.success(classGroupService.listClasses());
    }

    @GetMapping("/{classId}")
    @Operation(summary = "Get class group detail")
    public ApiResponse<Map<String, Object>> getClassDetail(@PathVariable Long classId) {
        return ApiResponse.success(classGroupService.getClassDetail(classId));
    }

    @GetMapping("/{classId}/students")
    @Operation(summary = "List enrolled students in a class")
    public ApiResponse<List<Map<String, Object>>> listEnrolledStudents(@PathVariable Long classId) {
        return ApiResponse.success(classGroupService.listEnrolledStudents(classId));
    }

    @GetMapping("/{classId}/occurrences")
    @Operation(summary = "List planned lesson occurrences for a class")
    public ApiResponse<List<Map<String, Object>>> listClassOccurrences(@PathVariable Long classId) {
        List<Map<String, Object>> occurrences = classGroupService.listClassOccurrences(classId);
        // 补全教师/教室名称（直接查）
        for (Map<String, Object> occ : occurrences) {
            if (occ.get("plannedTeacherId") != null) {
                // handled in service if needed; minimal enrichment here
            }
        }
        return ApiResponse.success(occurrences);
    }

    @PostMapping("/{classId}/occurrences/{occurrenceId}/confirm")
    @Operation(summary = "Unified confirmation for all active students in a class")
    public ApiResponse<Map<String, Object>> confirmLesson(
            @PathVariable Long classId,
            @PathVariable Long occurrenceId,
            @RequestBody(required = false) Map<String, Object> body) {
        if (body == null) {
            body = Map.of();
        }
        return ApiResponse.success("Class lesson confirmed.", classGroupService.confirmLesson(classId, occurrenceId, body));
    }

    @PostMapping("/{classId}/enroll")
    @Operation(summary = "Enroll a student in a class")
    public ApiResponse<Map<String, Object>> enrollStudent(
            @PathVariable Long classId,
            @RequestBody Map<String, Object> body) {
        Long studentId = body.get("studentId") instanceof Number
                ? ((Number) body.get("studentId")).longValue() : null;
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
    @Operation(summary = "Send expiry reminders for expiring students in a class")
    public ApiResponse<Map<String, Object>> sendExpiryReminders(@PathVariable Long classId) {
        return ApiResponse.success("Reminders sent.", classGroupService.sendExpiryReminders(classId));
    }
}
