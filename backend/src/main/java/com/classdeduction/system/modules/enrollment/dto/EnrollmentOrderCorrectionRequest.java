package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Enrollment order correction request.
 *
 * <p>Allows staff to correct the complete saved registration receipt. The service applies
 * the change to the original order and its downstream enrollment artifacts in one
 * transaction; it does not create a second registration.</p>
 */
@Data
@Schema(description = "Enrollment order correction request")
public class EnrollmentOrderCorrectionRequest {

    @Schema(description = "Version of the order when the correction form was opened")
    private Integer expectedVersion;

    @Schema(description = "Why an existing payment record is being corrected")
    private String correctionReason;

    @Schema(description = "Student display name")
    private String studentName;

    /**
     * Shared private lesson participant profiles. The first entry is participant 1;
     * legacy single-student corrections may omit this field.
     */
    @Valid
    @Schema(description = "Participant profiles for a shared private lesson")
    private List<RegistrationIntakeRequest.StudentPayload> participants;

    @Valid
    @Schema(description = "Independent discounts and payments per participant")
    private List<RegistrationIntakeRequest.ParticipantBillingPayload> participantBillings;

    @Schema(description = "Campus ID")
    private Long campusId;

    @Schema(description = "Campus name snapshot")
    private String campusName;

    @Schema(description = "Campus address shown on the receipt")
    private String campusAddress;

    @Schema(description = "Campus phone shown on the receipt")
    private String campusPhone;

    @Schema(description = "Tax ID (EIN) shown on the receipt for this campus")
    private String taxId;

    @Schema(description = "Student grade")
    private String grade;

    @Schema(description = "Registration class level")
    private String studentClass;

    @Schema(description = "Student school")
    private String school;

    @Schema(description = "Guardian phone")
    private String guardianPhone;

    @Schema(description = "Secondary guardian phone")
    private String guardianPhone2;

    @Schema(description = "Whether pickup is required")
    private Boolean pickupRequired;

    @Schema(description = "Pickup address")
    private String pickupAddress;

    @Schema(description = "Whether drop-off is required")
    private Boolean dropoffRequired;

    @Schema(description = "Drop-off address")
    private String dropoffAddress;

    @Schema(description = "Sign-up date")
    private LocalDate signupDate;

    @Schema(description = "Handled by name")
    private String handledByName;

    @Schema(description = "Registration remark")
    private String remark;

    @Schema(description = "Whether pickup fee is charged")
    private Boolean pickupFeeCharged;

    @Schema(description = "Pickup fee amount")
    private BigDecimal pickupFeeAmount;

    @Schema(description = "Whether drop-off fee is charged")
    private Boolean dropoffFeeCharged;

    @Schema(description = "Drop-off fee amount")
    private BigDecimal dropoffFeeAmount;

    @Schema(description = "Other fee total")
    private BigDecimal extraFeeAmount;

    @Schema(description = "Other fee detail lines")
    private List<RegistrationIntakeRequest.FeeLinePayload> extraFees;

    @Schema(description = "Discount total")
    private BigDecimal discountAmount;

    @Schema(description = "Discount detail lines")
    private List<RegistrationIntakeRequest.FeeLinePayload> discounts;

    @Schema(description = "Editable course lines from the original receipt")
    private List<RegistrationIntakeRequest.CourseItemPayload> items;

    /**
     * 死快照行的行号：对应订单行已软删，但收据快照仍保留该行。
     * 按行号把这些行从收据快照 items 中移除；订单金额与排课不受影响
     * （订单行软删时已经处理过）。
     */
    @Schema(description = "Line numbers of receipt snapshot rows whose order item was already soft-deleted")
    private List<Integer> removedReceiptItemLineNos;

    /**
     * 死快照行的原订单行 ID：老快照行可能缺 lineNo，无法按行号定位。
     * 按快照行的 orderItemId / enrollmentOrderItemId 匹配移除（字符串比较，
     * 兼容快照 JSON 中 Snowflake ID 精度丢失）。
     */
    @Schema(description = "Original order item IDs of detached receipt snapshot rows to remove from the snapshot")
    private List<String> removedReceiptItemOrderItemIds;

    @Schema(description = "Editable payment lines. Existing payment rows are updated in place.")
    private List<RegistrationIntakeRequest.PaymentPayload> payments;

    @Schema(description = "Receipt display options")
    private RegistrationIntakeRequest.ReceiptOptionsPayload receiptOptions;
}
