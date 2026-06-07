package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 收据保存请求。
 */
@Data
@Schema(description = "收据保存请求")
public class ReceiptRecordSaveRequest {

    @NotNull(message = "Enrollment order is required")
    @Schema(description = "报名订单ID", example = "1880000000000000001")
    private Long enrollmentOrderId;

    @NotNull(message = "Student is required")
    @Schema(description = "学生ID", example = "1880000000000000002")
    private Long studentId;

    @NotBlank(message = "Template code is required")
    @Schema(description = "模板编码", example = "DEFAULT_RECEIPT")
    private String templateCode;

    @Schema(description = "收据载荷JSON", example = "{}")
    private String receiptPayload;

    @Schema(description = "收据文件URL", example = "https://example.com/receipts/RC2026.pdf")
    private String fileUrl;
}
