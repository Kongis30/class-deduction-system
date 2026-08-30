package com.classdeduction.system.modules.base.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * grade 配置响应项。未入库（自动发现）的行 id 为 null。
 */
@Builder
@Schema(description = "Product grade config response payload")
public record ProductGradeConfigVO(
        Long id,
        String gradeLabel,
        Integer displayOrder
) {
}
