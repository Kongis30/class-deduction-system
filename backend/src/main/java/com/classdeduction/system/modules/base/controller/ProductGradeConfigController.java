package com.classdeduction.system.modules.base.controller;

import com.classdeduction.system.common.model.ApiResponse;
import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.base.dto.ProductGradeConfigSaveRequest;
import com.classdeduction.system.modules.base.service.ProductGradeConfigService;
import com.classdeduction.system.modules.base.vo.ProductGradeConfigVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 课程产品维度的 grade 名称与显示顺序配置。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/base/course-products/{productId}/grade-configs")
@Tag(name = "Base Data - Product Grade Configs")
public class ProductGradeConfigController {

    private final ProductGradeConfigService productGradeConfigService;

    @GetMapping
    @Operation(summary = "List grade configs of a course product (auto-discovered from price rows)")
    @RequireRoles({"ADMIN", "STAFF"})
    public ApiResponse<List<ProductGradeConfigVO>> listGradeConfigs(@PathVariable Long productId) {
        return ApiResponse.success(productGradeConfigService.listGradeConfigs(productId));
    }

    @PutMapping
    @Operation(summary = "Batch save grade configs (rename with cascade + reorder)")
    @RequireRoles({"ADMIN"})
    public ApiResponse<Void> saveGradeConfigs(@PathVariable Long productId,
                                              @Valid @RequestBody ProductGradeConfigSaveRequest request) {
        productGradeConfigService.saveGradeConfigs(productId, request);
        return ApiResponse.success("Updated successfully.", null);
    }
}
