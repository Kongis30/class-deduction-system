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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Prices 页面按班课 Course 维护假期；一个 Course 下的实际班级共用假期。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/base/course-products/{courseProductId}/holidays")
@RequireRoles({"ADMIN", "STAFF", "MANAGER"})
public class ClassHolidayController {

    private final ClassHolidayService classHolidayService;

    @GetMapping
    public ApiResponse<List<ClassHolidayVO>> listHolidays(@PathVariable Long courseProductId) {
        return ApiResponse.success(classHolidayService.listHolidays(courseProductId));
    }

    @PostMapping("/preview")
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<ClassHolidayPreviewVO> previewHoliday(
            @PathVariable Long courseProductId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long holidayId,
            @Valid @RequestBody ClassHolidaySaveRequest request) {
        return ApiResponse.success(classHolidayService.previewHoliday(courseProductId, holidayId, request));
    }

    @PostMapping
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<ClassHolidayVO> createHoliday(@PathVariable Long courseProductId,
                                                      @Valid @RequestBody ClassHolidaySaveRequest request) {
        return ApiResponse.success("Course holiday created.", classHolidayService.createHoliday(courseProductId, request));
    }

    @PutMapping("/{id}")
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<ClassHolidayVO> updateHoliday(@PathVariable Long courseProductId, @PathVariable Long id,
                                                     @Valid @RequestBody ClassHolidaySaveRequest request) {
        return ApiResponse.success("Course holiday updated.", classHolidayService.updateHoliday(courseProductId, id, request));
    }

    @DeleteMapping("/{id}")
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<Void> deleteHoliday(@PathVariable Long courseProductId, @PathVariable Long id) {
        classHolidayService.deleteHoliday(courseProductId, id);
        return ApiResponse.success("Course holiday deleted.", null);
    }
}
