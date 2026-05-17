package com.classdeduction.system.modules.systemconfig.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 当前品牌配置响应。
 */
@Builder
@Schema(description = "Brand config response")
public record BrandConfigVO(
        Long id,
        String brandName,
        String logoUrl,
        String themeColor,
        String supportEmail,
        String receiptFooterText,
        String status
) {
}
