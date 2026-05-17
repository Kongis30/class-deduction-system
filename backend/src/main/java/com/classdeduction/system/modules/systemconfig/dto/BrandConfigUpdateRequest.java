package com.classdeduction.system.modules.systemconfig.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 品牌配置更新请求：第一期只维护当前生效品牌。
 */
@Data
@Schema(description = "Brand config update request")
public class BrandConfigUpdateRequest {

    @NotBlank(message = "Brand name is required")
    @Size(max = 128, message = "Brand name must not exceed 128 characters")
    @Schema(description = "Brand name", example = "JIN BEST TUTORING")
    private String brandName;

    @Size(max = 500, message = "Logo URL must not exceed 500 characters")
    @Schema(description = "Logo URL")
    private String logoUrl;

    @Size(max = 32, message = "Theme color must not exceed 32 characters")
    @Schema(description = "Theme color", example = "#18b58a")
    private String themeColor;

    @Email(message = "Support email format is invalid")
    @Schema(description = "Support email", example = "support@example.com")
    private String supportEmail;

    @Size(max = 500, message = "Receipt footer text must not exceed 500 characters")
    @Schema(description = "Receipt footer text")
    private String receiptFooterText;

    @Schema(description = "Config status", example = "ACTIVE")
    private String status;
}
