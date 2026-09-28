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

/** Prices 页面使用的全局班课假期维护接口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/classes/holidays")
@RequireRoles({"ADMIN", "STAFF", "MANAGER"})
public class ClassHolidayController {

    private final ClassHolidayService classHolidayService;

    @GetMapping
    public ApiResponse<List<ClassHolidayVO>> listHolidays() {
        return ApiResponse.success(classHolidayService.listHolidays());
    }

    @PostMapping("/preview")
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<ClassHolidayPreviewVO> previewHoliday(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long holidayId,
            @Valid @RequestBody ClassHolidaySaveRequest request) {
        return ApiResponse.success(classHolidayService.previewHoliday(holidayId, request));
    }

    @PostMapping
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<ClassHolidayVO> createHoliday(@Valid @RequestBody ClassHolidaySaveRequest request) {
        return ApiResponse.success("Class holiday created.", classHolidayService.createHoliday(request));
    }

    @PutMapping("/{id}")
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<ClassHolidayVO> updateHoliday(@PathVariable Long id,
                                                     @Valid @RequestBody ClassHolidaySaveRequest request) {
        return ApiResponse.success("Class holiday updated.", classHolidayService.updateHoliday(id, request));
    }

    @DeleteMapping("/{id}")
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<Void> deleteHoliday(@PathVariable Long id) {
        classHolidayService.deleteHoliday(id);
        return ApiResponse.success("Class holiday deleted.", null);
    }
}
