package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * New registration intake payload. One request creates the student, order,
 * course lines, downstream enrollments, payments, and receipt snapshot.
 */
@Getter
@Setter
@Schema(description = "Registration intake request")
public class RegistrationIntakeRequest {

    @NotNull(message = "Student information is required")
    @Valid
    @Schema(description = "Student profile submitted from the registration form")
    private StudentPayload student;

    /**
     * Optional participants for a shared private lesson. The legacy student field remains
     * required for backward-compatible clients and is treated as participant 1.
     */
    @Size(max = 3, message = "A private lesson can have at most 3 students")
    @Valid
    @Schema(description = "Private lesson participants, including participant 1")
    private List<StudentPayload> participants;

    @Schema(description = "Campus ID selected during registration", example = "1")
    private Long campusId;

    @Schema(description = "Campus name snapshot", example = "South Campus")
    private String campusName;

    @Schema(description = "Signup date, defaults to today", example = "2026-07-15")
    private LocalDate signupDate;

    @Schema(description = "Registration period start date", example = "2026-09-01")
    private LocalDate periodStartDate;

    @Schema(description = "Registration period end date", example = "2026-12-31")
    private LocalDate periodEndDate;

    @Schema(description = "Existing one-on-one course package ID to renew")
    private Long renewPackageId;

    @Schema(description = "Existing group class enrollment ID to renew")
    private Long renewClassEnrollmentId;

    @Schema(description = "Cashier user ID")
    private Long cashierUserId;

    @NotBlank(message = "Handler name is required")
    @Schema(description = "Handler name snapshot", example = "Amy")
    private String handledByName;

    @Schema(description = "Currency code", example = "USD")
    private String currencyCode;

    @NotEmpty(message = "At least one course item is required")
    @Valid
    @Schema(description = "Course line items")
    private List<CourseItemPayload> items;

    @Schema(description = "Whether pickup fee is charged on this registration")
    private Boolean pickupFeeCharged;

    @DecimalMin(value = "0.00", message = "Pickup fee amount cannot be negative")
    @Schema(description = "Pickup service fee amount", example = "50.00")
    private BigDecimal pickupFeeAmount;

    @Schema(description = "Whether drop-off fee is charged on this registration")
    private Boolean dropoffFeeCharged;

    @DecimalMin(value = "0.00", message = "Drop-off fee amount cannot be negative")
    @Schema(description = "Drop-off service fee amount", example = "50.00")
    private BigDecimal dropoffFeeAmount;

    @DecimalMin(value = "0.00", message = "Extra fee amount cannot be negative")
    @Schema(description = "Extra fee amount", example = "20.00")
    private BigDecimal extraFeeAmount;

    @Valid
    @Schema(description = "Extra fee detail lines")
    private List<FeeLinePayload> extraFees;

    @DecimalMin(value = "0.00", message = "Discount amount cannot be negative")
    @Schema(description = "Discount amount", example = "100.00")
    private BigDecimal discountAmount;

    @Valid
    @Schema(description = "Discount detail lines")
    private List<FeeLinePayload> discounts;

    @Valid
    @Schema(description = "Payment detail lines")
    private List<PaymentPayload> payments;

    /**
     * 1 对 N 私教的按学生账务。订单和 Receipt 仍然只有一份，但每个学生的折扣、付款方式和已付金额独立记录。
     */
    @Valid
    @Schema(description = "Per-participant billing details for shared private lessons")
    private List<ParticipantBillingPayload> participantBillings;

    @Valid
    @Schema(description = "Receipt options")
    private ReceiptOptionsPayload receiptOptions;

    @Schema(description = "Registration remark")
    private String remark;

    @Getter
    @Setter
    @Schema(description = "Registration student payload")
    public static class StudentPayload {

        @Schema(description = "Existing student ID when selecting a saved participant")
        private Long studentId;

        @NotBlank(message = "Student name is required")
        @Schema(description = "Student name", example = "Ava Lin")
        private String studentName;

        @Schema(description = "English name", example = "Ava")
        private String englishName;

        @Schema(description = "Gender", example = "FEMALE")
        private String gender;

        @Schema(description = "Birth date", example = "2012-08-16")
        private LocalDate birthDate;

        @Schema(description = "Current grade", example = "Grade 6")
        private String grade;

        @Schema(description = "Registration class level selected on the intake form", example = "G6")
        private String studentClass;

        @Schema(description = "Current school", example = "PS 101")
        private String school;

        @Schema(description = "Lead source", example = "WALK_IN")
        private String sourceChannel;

        @Schema(description = "Guardian name", example = "Mei Lin")
        private String guardianName;

        @Schema(description = "Primary guardian phone", example = "917-555-0101")
        private String guardianPhone;

        @Schema(description = "Secondary guardian phone", example = "917-555-0102")
        private String guardianPhone2;

        @Schema(description = "Guardian email", example = "parent@example.com")
        private String guardianEmail;

        @Schema(description = "Guardian relation type", example = "MOTHER")
        private String guardianRelationType;

        @Schema(description = "Whether pickup is required")
        private Boolean pickupRequired;

        @Schema(description = "Pickup address")
        private String pickupAddress;

        @Schema(description = "Whether dropoff is required")
        private Boolean dropoffRequired;

        @Schema(description = "Dropoff address")
        private String dropoffAddress;
    }

    @Getter
    @Setter
    @Schema(description = "Registration course item payload")
    public static class CourseItemPayload extends EnrollmentOrderItemDTO {

        @Schema(description = "Per-session group class purchased sessions", example = "10")
        private Integer sessions;

        @Schema(description = "Monthly group class purchased rolling months", example = "6")
        private Integer purchasedMonths;

        @Schema(description = "Monthly group class start month yyyy-MM", example = "2026-09")
        private String startMonth;

        @Schema(description = "Monthly group class end month yyyy-MM", example = "2026-12")
        private String endMonth;
    }

    @Getter
    @Setter
    @Schema(description = "Fee or discount detail line")
    public static class FeeLinePayload {

        @NotBlank(message = "Fee line label is required")
        @Size(max = 128, message = "Fee line label must not exceed 128 characters")
        @Schema(description = "Fee label", example = "Overstay")
        private String label;

        @NotNull(message = "Fee line amount is required")
        @DecimalMin(value = "0.00", message = "Fee line amount cannot be negative")
        @Schema(description = "Fee amount", example = "20.00")
        private BigDecimal amount;

        @Schema(description = "Fee note")
        private String note;

        @Min(value = 1, message = "Course line number must be at least 1")
        @Schema(description = "Course line receiving this discount; omitted only for legacy clients")
        private Integer orderItemLineNo;
    }

    @Getter
    @Setter
    @Schema(description = "Registration payment payload")
    public static class PaymentPayload {

        @Schema(description = "Participant index within the shared private lesson", example = "2")
        private Integer participantIndex;

        @NotBlank(message = "Payment method is required")
        @Schema(description = "Payment method code", example = "ZELLE")
        private String paymentMethod;

        @NotNull(message = "Payment amount is required")
        @DecimalMin(value = "0.01", message = "Payment amount must be greater than zero")
        @Digits(integer = 12, fraction = 2, message = "Payment amount must use at most 2 decimal places")
        @Schema(description = "Payment amount", example = "500.00")
        private BigDecimal amount;

        @Schema(description = "Payment time, defaults to now")
        private LocalDateTime paidAt;

        @Schema(description = "Handler user ID")
        private Long handledByUserId;

        @Schema(description = "Handler name snapshot")
        private String handledByName;

        @Schema(description = "Payment remark")
        private String remark;

        @Valid
        @Schema(description = "Course-level portions of this payment")
        private List<CoursePaymentAllocationPayload> allocations;
    }

    @Getter
    @Setter
    @Schema(description = "A payment portion assigned to one course line")
    public static class CoursePaymentAllocationPayload {

        @NotNull(message = "Course line number is required")
        @Min(value = 1, message = "Course line number must be at least 1")
        private Integer orderItemLineNo;

        @NotNull(message = "Course payment amount is required")
        @DecimalMin(value = "0.01", message = "Course payment amount must be greater than zero")
        @Digits(integer = 12, fraction = 2, message = "Course payment amount must use at most 2 decimal places")
        private BigDecimal amount;
    }

    @Getter
    @Setter
    @Schema(description = "Private lesson participant billing")
    public static class ParticipantBillingPayload {

        @NotNull(message = "Participant index is required")
        private Integer participantIndex;

        @DecimalMin(value = "0.00", message = "Participant discount cannot be negative")
        private BigDecimal discountAmount;

        @Valid
        private List<FeeLinePayload> discounts;

        @Valid
        private List<PaymentPayload> payments;
    }

    @Getter
    @Setter
    @Schema(description = "Receipt options payload")
    public static class ReceiptOptionsPayload {

        @Schema(description = "Whether to issue a receipt, defaults to true")
        private Boolean issueReceipt;

        @Schema(description = "Receipt template code", example = "DEFAULT_RECEIPT")
        private String templateCode;

        @Schema(description = "Whether the receipt preview should show EIN by default")
        private Boolean showTaxId;

        @Schema(description = "Campus tax ID / EIN")
        private String taxId;

        @Schema(description = "Campus address snapshot")
        private String campusAddress;

        @Schema(description = "Campus phone snapshot")
        private String campusPhone;

        @Schema(description = "Brand name snapshot")
        private String brandName;

        @Schema(description = "Logo URL snapshot")
        private String logoUrl;

        @Schema(description = "Zelle QR code resource")
        private String zelleQrCodeUrl;

        @Schema(description = "Policy text snapshot")
        private String policyText;
    }
}
