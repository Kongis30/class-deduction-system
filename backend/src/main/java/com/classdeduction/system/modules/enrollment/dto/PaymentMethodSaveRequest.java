package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 支付方式保存/更新请求。
 */
@Data
@Schema(description = "支付方式保存/更新请求")
public class PaymentMethodSaveRequest {

    @NotBlank(message = "Method code is required")
    @Schema(description = "支付方式编码", example = "WECHAT_PAY")
    private String methodCode;

    @NotBlank(message = "Method name is required")
    @Schema(description = "支付方式显示名称", example = "WeChat Pay")
    private String methodName;

    @Schema(description = "排序号", example = "7")
    private Integer sortOrder;

    @Schema(description = "状态: ACTIVE / INACTIVE", example = "ACTIVE")
    private String status;
}
