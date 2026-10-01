package com.classdeduction.system.modules.schedule.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.schedule.dto.ClassHolidaySaveRequest;
import com.classdeduction.system.modules.schedule.service.ClassHolidayService;
import com.classdeduction.system.modules.schedule.vo.ClassHolidayVO;
import com.classdeduction.system.modules.schedule.vo.ClassHolidayPreviewVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Prices 页面按教学项目维护假期；项目下所有实际班级共用假期。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v2/group-course-model/projects/{projectId}/holidays")
@RequireRoles({"ADMIN", "STAFF", "MANAGER"})
public class ClassHolidayController {

    private final ClassHolidayService classHolidayService;

    @GetMapping
    public ApiResponse<List<ClassHolidayVO>> listHolidays(@PathVariable Long projectId) {
        return ApiResponse.success(classHolidayService.listHolidays(projectId));
    }

    @PostMapping("/preview")
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<ClassHolidayPreviewVO> previewHoliday(
            @PathVariable Long projectId,
            @RequestParam(required = false) Long holidayId,
            @Valid @RequestBody ClassHolidaySaveRequest request) {
        return ApiResponse.success(classHolidayService.previewHoliday(projectId, holidayId, request));
    }

    @PostMapping
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<ClassHolidayVO> createHoliday(@PathVariable Long projectId,
                                                      @Valid @RequestBody ClassHolidaySaveRequest request) {
        return ApiResponse.success("Project holiday created.", classHolidayService.createHoliday(projectId, request));
    }

    @PutMapping("/{id}")
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<ClassHolidayVO> updateHoliday(@PathVariable Long projectId,
                                                     @PathVariable Long id,
                                                     @Valid @RequestBody ClassHolidaySaveRequest request) {
        return ApiResponse.success("Project holiday updated.", classHolidayService.updateHoliday(projectId, id, request));
    }

    @DeleteMapping("/{id}")
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<Void> deleteHoliday(@PathVariable Long projectId,
                                           @PathVariable Long id) {
        classHolidayService.deleteHoliday(projectId, id);
        return ApiResponse.success("Project holiday deleted.", null);
    }
}
