package com.classdeduction.system.modules.base.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.base.dto.DiscountPresetSaveRequest;
import com.classdeduction.system.modules.base.service.DiscountPresetService;
import com.classdeduction.system.modules.base.vo.DiscountPresetVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Discount preset controller.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/base/discount-presets")
@Tag(name = "Base Data - Discount Presets")
@RequireRoles({"ADMIN", "STAFF", "MANAGER"})
public class DiscountPresetController {

    private final DiscountPresetService discountPresetService;

    @GetMapping
    @Operation(summary = "List all discount presets")
    public ApiResponse<List<DiscountPresetVO>> listPresets() {
        return ApiResponse.success(discountPresetService.listAllActive());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get discount preset detail")
    public ApiResponse<DiscountPresetVO> getPreset(@PathVariable Long id) {
        return ApiResponse.success(discountPresetService.getPreset(id));
    }

    @PostMapping
    @Operation(summary = "Create a discount preset")
    public ApiResponse<Long> createPreset(@Valid @RequestBody DiscountPresetSaveRequest request) {
        return ApiResponse.success("Created successfully.", discountPresetService.createPreset(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a discount preset")
    public ApiResponse<Void> updatePreset(@PathVariable Long id,
                                           @Valid @RequestBody DiscountPresetSaveRequest request) {
        discountPresetService.updatePreset(id, request);
        return ApiResponse.success("Updated successfully.", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a discount preset")
    public ApiResponse<Void> deletePreset(@PathVariable Long id) {
        discountPresetService.deletePreset(id);
        return ApiResponse.success("Deleted successfully.", null);
    }
}
