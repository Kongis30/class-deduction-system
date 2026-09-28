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

/** Prices 页面按 Course + Grade 维护班课假期；同 Grade 下的实际班级共用假期。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/base/course-products/{courseProductId}/holidays")
@RequireRoles({"ADMIN", "STAFF", "MANAGER"})
public class ClassHolidayController {

    private final ClassHolidayService classHolidayService;

    @GetMapping
    public ApiResponse<List<ClassHolidayVO>> listHolidays(@PathVariable Long courseProductId,
                                                           @RequestParam String gradeBand) {
        return ApiResponse.success(classHolidayService.listHolidays(courseProductId, gradeBand));
    }

    @PostMapping("/preview")
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<ClassHolidayPreviewVO> previewHoliday(
            @PathVariable Long courseProductId,
            @RequestParam String gradeBand,
            @RequestParam(required = false) Long holidayId,
            @Valid @RequestBody ClassHolidaySaveRequest request) {
        return ApiResponse.success(classHolidayService.previewHoliday(courseProductId, gradeBand, holidayId, request));
    }

    @PostMapping
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<ClassHolidayVO> createHoliday(@PathVariable Long courseProductId,
                                                      @RequestParam String gradeBand,
                                                      @Valid @RequestBody ClassHolidaySaveRequest request) {
        return ApiResponse.success("Grade holiday created.", classHolidayService.createHoliday(courseProductId, gradeBand, request));
    }

    @PutMapping("/{id}")
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<ClassHolidayVO> updateHoliday(@PathVariable Long courseProductId, @RequestParam String gradeBand,
                                                     @PathVariable Long id,
                                                     @Valid @RequestBody ClassHolidaySaveRequest request) {
        return ApiResponse.success("Grade holiday updated.", classHolidayService.updateHoliday(courseProductId, gradeBand, id, request));
    }

    @DeleteMapping("/{id}")
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<Void> deleteHoliday(@PathVariable Long courseProductId, @RequestParam String gradeBand,
                                           @PathVariable Long id) {
        classHolidayService.deleteHoliday(courseProductId, gradeBand, id);
        return ApiResponse.success("Grade holiday deleted.", null);
    }
}
