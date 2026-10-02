package com.classdeduction.system.modules.schedule.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.schedule.service.TeacherCalendarRosterService;
import com.classdeduction.system.modules.schedule.vo.TeacherCalendarCategoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Teacher Calendar 左侧课程分组数据。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/schedule/teachers")
@Tag(name = "Schedule - Teacher Calendar")
@RequireRoles({"ADMIN", "STAFF", "MANAGER"})
public class TeacherCalendarRosterController {

    private final TeacherCalendarRosterService teacherCalendarRosterService;

    @GetMapping("/calendar-categories")
    @Operation(summary = "List course categories associated with teachers")
    public ApiResponse<List<TeacherCalendarCategoryVO>> listCalendarCategories() {
        return ApiResponse.success(teacherCalendarRosterService.listCategories());
    }
}
