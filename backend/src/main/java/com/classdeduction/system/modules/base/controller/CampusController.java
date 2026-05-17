package com.classdeduction.system.modules.base.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.base.dto.CampusSaveRequest;
import com.classdeduction.system.modules.base.service.CampusService;
import com.classdeduction.system.modules.base.vo.CampusVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Campus controller.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/base/campuses")
@Tag(name = "Base Data - Campuses")
@RequireRoles({"ADMIN", "STAFF"})
public class CampusController {

    private final CampusService campusService;

    @GetMapping
    @Operation(summary = "List all active campuses")
    public ApiResponse<List<CampusVO>> listCampuses() {
        return ApiResponse.success(campusService.listAllActive());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get campus detail")
    public ApiResponse<CampusVO> getCampus(@PathVariable Long id) {
        return ApiResponse.success(campusService.getCampus(id));
    }

    @PostMapping
    @Operation(summary = "Create a campus")
    public ApiResponse<Long> createCampus(@Valid @RequestBody CampusSaveRequest request) {
        return ApiResponse.success("Created successfully.", campusService.createCampus(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a campus")
    public ApiResponse<Void> updateCampus(@PathVariable Long id,
                                           @Valid @RequestBody CampusSaveRequest request) {
        campusService.updateCampus(id, request);
        return ApiResponse.success("Updated successfully.", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a campus")
    public ApiResponse<Void> deleteCampus(@PathVariable Long id) {
        campusService.deleteCampus(id);
        return ApiResponse.success("Deleted successfully.", null);
    }
}
