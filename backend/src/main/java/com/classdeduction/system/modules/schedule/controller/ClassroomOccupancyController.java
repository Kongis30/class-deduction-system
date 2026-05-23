package com.classdeduction.system.modules.schedule.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.schedule.dto.ClassroomOccupancyPageQuery;
import com.classdeduction.system.modules.schedule.service.LessonOccurrenceService;
import com.classdeduction.system.modules.schedule.vo.ClassroomOccupancyVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教室占用控制器：负责教室视角的占用查询。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/schedule/classrooms")
@Tag(name = "Schedule - Classroom Occupancy")
@RequireRoles({"ADMIN", "STAFF", "TEACHER"})
public class ClassroomOccupancyController {

    private final LessonOccurrenceService lessonOccurrenceService;

    @GetMapping("/occupancy")
    @Operation(summary = "Page classroom occupancy records")
    public ApiResponse<PageResponse<ClassroomOccupancyVO>> pageOccupancy(@ParameterObject ClassroomOccupancyPageQuery query) {
        return ApiResponse.success(lessonOccurrenceService.pageClassroomOccupancy(query));
    }
}
