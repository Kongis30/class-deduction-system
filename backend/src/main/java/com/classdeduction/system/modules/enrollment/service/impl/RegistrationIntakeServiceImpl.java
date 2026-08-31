package com.classdeduction.system.modules.enrollment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.common.util.StudentNoGenerator;
import com.classdeduction.system.modules.auth.entity.RoleEntity;
import com.classdeduction.system.modules.auth.entity.UserAccountEntity;
import com.classdeduction.system.modules.auth.entity.UserRoleEntity;
import com.classdeduction.system.modules.auth.mapper.RoleMapper;
import com.classdeduction.system.modules.auth.mapper.UserAccountMapper;
import com.classdeduction.system.modules.auth.mapper.UserRoleMapper;
import com.classdeduction.system.modules.base.entity.CampusEntity;
import com.classdeduction.system.modules.base.entity.CoursePriceOptionEntity;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.CampusMapper;
import com.classdeduction.system.modules.base.mapper.CoursePriceOptionMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.RegistrationIntakeRequest;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentMethodEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.entity.ReceiptRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentMethodMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import com.classdeduction.system.modules.enrollment.mapper.ReceiptRecordMapper;
import com.classdeduction.system.modules.enrollment.service.RegistrationIntakeService;
import com.classdeduction.system.modules.enrollment.util.BusinessNoGenerator;
import com.classdeduction.system.modules.enrollment.vo.RegistrationIntakeResultVO;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.vo.ScheduleConflictDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Transactional backend closure for the new registration form.
 */
@Service
@RequiredArgsConstructor
public class RegistrationIntakeServiceImpl implements RegistrationIntakeService {

    private static final String DEFAULT_CURRENCY = "USD";
    private static final String DEFAULT_RECEIPT_TEMPLATE = "DEFAULT_RECEIPT";
    private static final String DEFAULT_ACCOUNT_PASSWORD = "9297019872";
    private static final String JIN_USERNAME_DOMAIN = "@jin";
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final StudentMapper studentMapper;
    private final StudentNoGenerator studentNoGenerator;
    private final CampusMapper campusMapper;
    private final CoursePriceOptionMapper coursePriceOptionMapper;
    private final CourseProductMapper courseProductMapper;
    private final TeacherMapper teacherMapper;
    private final EnrollmentOrderMapper enrollmentOrderMapper;
    private final EnrollmentOrderItemMapper enrollmentOrderItemMapper;
    private final CoursePackageMapper coursePackageMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final PaymentMethodMapper paymentMethodMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final ReceiptRecordMapper receiptRecordMapper;
    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JsonHelper jsonHelper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegistrationIntakeResultVO submitIntake(RegistrationIntakeRequest request) {
        validateRegistrationPeriod(request);
        validateCampusSelection(request);
        StudentResolution studentResolution = createOrReuseStudent(request);
        List<ResolvedCourseLine> lines = resolveCourseLines(request.getItems());
        MoneySnapshot money = calculateMoney(request, lines);

        EnrollmentOrderEntity order = createOrder(request, studentResolution.student(), lines, money);

        List<RegistrationIntakeResultVO.CourseArtifactVO> artifacts = new ArrayList<>();
        BigDecimal unallocatedPaid = money.paidAmount();
        for (ResolvedCourseLine line : lines) {
            EnrollmentOrderItemEntity orderItem = createOrderItem(order.getId(), line);
            line.orderItemId = orderItem.getId();

            BigDecimal allocatedPaid = unallocatedPaid.min(line.priceAmount);
            unallocatedPaid = unallocatedPaid.subtract(allocatedPaid);
            artifacts.add(line.oneOnOne
                    ? createOneOnOneArtifact(order, studentResolution.student(), line, allocatedPaid)
                    : createGroupClassArtifact(order, studentResolution.student(), line));
        }

        List<RegistrationIntakeResultVO.PaymentResultVO> payments = createPayments(order, request, money);
        RegistrationIntakeResultVO.AccountResultVO studentAccount = null;
        if (lines.stream().anyMatch(line -> line.oneOnOne)) {
            studentAccount = ensureStudentAccount(studentResolution.student());
        }

        ReceiptResult receipt = createReceiptIfNeeded(order, studentResolution.student(), request, lines, money, payments, artifacts);

        return RegistrationIntakeResultVO.builder()
                .enrollmentOrderId(order.getId())
                .enrollmentNo(order.getEnrollmentNo())
                .studentId(studentResolution.student().getId())
                .studentNo(studentResolution.student().getStudentNo())
                .studentName(studentResolution.student().getStudentName())
                .studentReused(studentResolution.reused())
                .studentAccount(studentAccount)
                .teacherAccounts(Collections.emptyList())
                .courseArtifacts(artifacts)
                .payments(payments)
                .receiptId(receipt.receiptId())
                .receiptNo(receipt.receiptNo())
                .receiptPreviewData(receipt.previewData())
                .amountDue(money.amountDue())
                .paidAmount(money.paidAmount())
                .balanceAmount(money.balanceAmount())
                .paymentStatus(money.paymentStatus())
                .build();
    }

    private StudentResolution createOrReuseStudent(RegistrationIntakeRequest request) {
        RegistrationIntakeRequest.StudentPayload payload = request.getStudent();
        StudentEntity student = null;
        String guardianPhone = normalizePhone(payload.getGuardianPhone());
        if (StringUtils.hasText(guardianPhone)) {
            student = studentMapper.selectReusableStudent(
                    payload.getStudentName().trim(),
                    guardianPhone);
        }
        boolean reused = student != null;
        if (!reused) {
            student = new StudentEntity();
            student.setStudentNo(studentNoGenerator.generate());
        }

        applyStudentPayload(student, payload, request.getCampusId());
        if (reused) {
            studentMapper.updateById(student);
        } else {
            studentMapper.insert(student);
        }
        return new StudentResolution(student, reused);
    }

    private void applyStudentPayload(StudentEntity student,
                                     RegistrationIntakeRequest.StudentPayload payload,
                                     Long campusId) {
        student.setStudentName(payload.getStudentName().trim());
        student.setEnglishName(payload.getEnglishName());
        student.setGender(payload.getGender());
        student.setBirthDate(payload.getBirthDate());
        student.setGrade(payload.getGrade());
        student.setSchool(payload.getSchool());
        student.setSourceChannel(payload.getSourceChannel());
        student.setStatus("ACTIVE");
        student.setGuardianName(payload.getGuardianName());
        student.setGuardianPhone(normalizePhone(payload.getGuardianPhone()));
        student.setGuardianPhone2(normalizePhone(payload.getGuardianPhone2()));
        student.setGuardianEmail(payload.getGuardianEmail());
        student.setGuardianRelationType(payload.getGuardianRelationType());
        student.setPickupRequired(Boolean.TRUE.equals(payload.getPickupRequired()));
        student.setPickupAddress(payload.getPickupAddress());
        student.setDropoffRequired(Boolean.TRUE.equals(payload.getDropoffRequired()));
        student.setDropoffAddress(payload.getDropoffAddress());
        student.setCampusId(campusId);
    }

    private List<ResolvedCourseLine> resolveCourseLines(List<RegistrationIntakeRequest.CourseItemPayload> items) {
        List<ResolvedCourseLine> lines = new ArrayList<>();
        int nextLineNo = 1;
        for (RegistrationIntakeRequest.CourseItemPayload item : items) {
            CoursePriceOptionEntity option = resolvePriceOption(item.getCoursePriceOptionId());
            if (option == null) {
                throw new BusinessException("COURSE_PRICE_OPTION_REQUIRED",
                        "Please select a configured course price option.");
            }
            if (option.getCourseProductId() == null) {
                throw new BusinessException("COURSE_PRODUCT_REQUIRED",
                        "Configured course price option must link to a course product.");
            }
            if (item.getCourseProductId() != null && option.getCourseProductId() != null
                    && !Objects.equals(item.getCourseProductId(), option.getCourseProductId())) {
                throw new BusinessException("COURSE_PRODUCT_OPTION_MISMATCH",
                        "Selected course product does not match the price option.");
            }
            Long productId = firstNonNull(item.getCourseProductId(), option.getCourseProductId());
            CourseProductEntity product = productId == null ? null : requireCourseProduct(productId);
            validatePriceOptionProduct(option, product);
            BigDecimal configuredUnitPrice = requireNonNegativeAmount(resolveOptionBasePrice(option, item.getAttendanceOption()), "COURSE_PRICE_REQUIRED",
                    "Configured course price is required.");

            ResolvedCourseLine line = new ResolvedCourseLine();
            line.lineNo = item.getLineNo() == null ? nextLineNo : item.getLineNo();
            nextLineNo = Math.max(nextLineNo + 1, line.lineNo + 1);
            line.coursePriceOptionId = item.getCoursePriceOptionId();
            line.courseProductId = productId;
            line.registerClass = firstText(item.getRegisterClass(), option == null ? null : option.getRegisterClass());
            line.courseType = firstText(item.getCourseType(), option == null ? null : option.getCourseType());
            line.detail = firstText(
                    firstText(item.getDetail(), option == null ? null : option.getDetailText()),
                    product == null ? null : product.getProductName());
            line.gradeBand = firstText(item.getGradeBand(), option == null ? null : option.getGradeBand());
            line.periodLabel = firstText(item.getPeriodLabel(), option == null ? null : option.getPeriodLabel());
            line.timeSlotLabel = firstText(item.getTimeSlotLabel(), option == null ? null : option.getTimeSlotLabel());
            line.startDate = item.getStartDate();
            line.packageUnit = firstText(item.getPackageUnit(), resolveOptionPackageUnit(option));
            line.packageQuantity = firstNonNull(item.getPackageQuantity(), resolveOptionPackageQuantity(option));
            line.packageTotalPrice = firstNonNull(item.getPackageTotalPrice(), resolveOptionTotalPrice(option));
            line.attendanceOption = normalizeAttendanceOption(item.getAttendanceOption());
            line.attendanceScheduleJson = item.getAttendanceScheduleJson();
            line.oneOnOneHours = firstNonNull(item.getOneOnOneHours(), option == null ? null : option.getOneOnOneHours());
            line.oneOnOneBonusHours = item.getOneOnOneBonusHours();
            line.openingUsedHours = item.getOpeningUsedHours();
            line.oneOnOneUnits = item.getOneOnOneUnits();
            line.classId = firstNonNull(item.getClassId(), option.getClassId());
            line.sessions = item.getSessions();
            line.purchasedMonths = item.getPurchasedMonths();
            line.startMonth = item.getStartMonth();
            line.endMonth = item.getEndMonth();
            line.remark = item.getRemark();
            line.product = product;
            line.oneOnOne = isOneOnOneLine(line, product);
            applyLinePricing(line, item, configuredUnitPrice, option.getOneOnOneHours());

            validateResolvedLine(line);
            lines.add(line);
        }
        return lines;
    }

    private void applyLinePricing(ResolvedCourseLine line,
                                  RegistrationIntakeRequest.CourseItemPayload item,
                                  BigDecimal configuredUnitPrice,
                                  BigDecimal configuredOneOnOneHours) {
        boolean manualPriceOverride = Boolean.TRUE.equals(item.getManualPriceOverride());
        BigDecimal configuredPrice = line.oneOnOne
                ? calculateOneOnOnePrice(configuredUnitPrice, configuredOneOnOneHours, line.oneOnOneHours)
                : configuredUnitPrice;
        if (!manualPriceOverride && configuredPrice.compareTo(ZERO_AMOUNT) <= 0) {
            throw new BusinessException("COURSE_LINE_PRICE_REQUIRED",
                    "Manual course line price is required for a $0 course template.");
        }
        line.originalPriceAmount = defaultAmount(configuredPrice);
        line.priceAmount = manualPriceOverride
                ? requirePositiveAmount(item.getPriceAmount(), "COURSE_LINE_PRICE_REQUIRED",
                        "Manual course line price is required.")
                : defaultAmount(configuredPrice);
        line.manualPriceOverride = manualPriceOverride;
    }

    private BigDecimal calculateOneOnOnePrice(BigDecimal configuredPrice,
                                               BigDecimal configuredHours,
                                               BigDecimal purchasedHours) {
        if (configuredHours == null || configuredHours.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("ONE_ON_ONE_HOURS_REQUIRED",
                    "Configured one-on-one hours must be greater than zero.");
        }
        if (purchasedHours == null || purchasedHours.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("ONE_ON_ONE_HOURS_REQUIRED",
                    "Enter purchased one-on-one hours.");
        }
        return configuredPrice
                .divide(configuredHours, 8, RoundingMode.HALF_UP)
                .multiply(purchasedHours)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private int resolveGroupPurchasedQuantity(ResolvedCourseLine line) {
        if (line.oneOnOne) {
            return 1;
        }
        if (line.purchasedMonths == null) {
            throw new BusinessException("REGISTRATION_DURATION_MISSING",
                    "Enter purchased months on the registration form.");
        }
        if (line.purchasedMonths < 1 || line.purchasedMonths > 36) {
            throw new BusinessException("REGISTRATION_DURATION_INVALID",
                    "Purchased months must be between 1 and 36.");
        }
        return line.purchasedMonths;
    }

    private BigDecimal requirePositiveAmount(BigDecimal amount, String code, String message) {
        if (amount == null || amount.compareTo(ZERO_AMOUNT) <= 0) {
            throw new BusinessException(code, message);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal requireNonNegativeAmount(BigDecimal amount, String code, String message) {
        if (amount == null || amount.compareTo(ZERO_AMOUNT) < 0) {
            throw new BusinessException(code, message);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private void validatePriceOptionProduct(CoursePriceOptionEntity option, CourseProductEntity product) {
        if (product == null || !"ACTIVE".equals(product.getStatus())) {
            throw new BusinessException("COURSE_PRODUCT_INVALID",
                    "Configured course price option must link to an active course product.");
        }
        String expectedMode = "ONE_ON_ONE".equals(option.getCourseType()) ? "ONE_ON_ONE" : "GROUP";
        if (!expectedMode.equals(product.getTeachingMode())) {
            throw new BusinessException("COURSE_PRODUCT_MODE_MISMATCH",
                    "Course price option teaching mode does not match the linked product.");
        }
        if (StringUtils.hasText(product.getCourseType())
                && StringUtils.hasText(option.getCourseType())
                && !option.getCourseType().equals(product.getCourseType())) {
            throw new BusinessException("COURSE_PRODUCT_TYPE_MISMATCH",
                    "Course price option type does not match the linked product.");
        }
    }

    private void validateResolvedLine(ResolvedCourseLine line) {
        if (!StringUtils.hasText(line.registerClass)) {
            throw new BusinessException("REGISTER_CLASS_REQUIRED", "Register Class is required for every course line.");
        }
        if (line.priceAmount.compareTo(ZERO_AMOUNT) < 0) {
            throw new BusinessException("INVALID_LINE_PRICE", "Course line price cannot be negative.");
        }
        if (line.manualPriceOverride && !StringUtils.hasText(line.remark)) {
            throw new BusinessException("MANUAL_PRICE_REASON_REQUIRED",
                    "Manual course price requires a reason.");
        }
        if (line.oneOnOne) {
            if (line.oneOnOneHours == null
                    || line.oneOnOneHours.compareTo(new BigDecimal("0.5")) < 0
                    || line.oneOnOneHours.compareTo(new BigDecimal("400")) > 0
                    || line.oneOnOneHours.multiply(BigDecimal.valueOf(2)).stripTrailingZeros().scale() > 0) {
                throw new BusinessException("ONE_ON_ONE_HOURS_INVALID",
                        "One-on-one hours must be between 0.5 and 400 in 0.5-hour increments.");
            }
            if (line.oneOnOneBonusHours != null
                    && (line.oneOnOneBonusHours.compareTo(BigDecimal.ZERO) < 0
                        || line.oneOnOneBonusHours.compareTo(new BigDecimal("400")) > 0
                        || line.oneOnOneBonusHours.multiply(BigDecimal.valueOf(2)).stripTrailingZeros().scale() > 0)) {
                throw new BusinessException("ONE_ON_ONE_BONUS_HOURS_INVALID",
                        "One-on-one bonus hours must be between 0 and 400 in 0.5-hour increments.");
            }
            if (line.product == null || !"ONE_ON_ONE".equals(line.product.getTeachingMode()) || !"ACTIVE".equals(line.product.getStatus())) {
                throw new BusinessException("ONE_ON_ONE_PRODUCT_REQUIRED", "Active one-on-one course product is required.");
            }
            if (resolveOneOnOnePurchasedUnits(line) <= 0) {
                throw new BusinessException("ONE_ON_ONE_UNITS_REQUIRED", "One-on-one purchased hours or units are required.");
            }
            int openingUsedUnits = resolveOpeningUsedUnits(line);
            if (openingUsedUnits > resolveOneOnOneUnits(line)) {
                throw new BusinessException("OPENING_USED_HOURS_EXCEED_PACKAGE",
                        "Already used hours cannot exceed total package hours (purchased + bonus).");
            }
            if (line.openingUsedHours != null
                    && line.openingUsedHours.multiply(BigDecimal.valueOf(2)).stripTrailingZeros().scale() > 0) {
                throw new BusinessException("OPENING_USED_HOURS_INVALID",
                        "Already used hours must use 0.5-hour increments.");
            }
            return;
        }
        resolveGroupClass(line);
    }

    private void resolveGroupClass(ResolvedCourseLine line) {
        if (line.classId == null) {
            throw new BusinessException("GRADE_CLASS_REQUIRED",
                    "Select a price row with a configured grade class.");
        }
        ClassGroupEntity classGroup = requireActiveClassGroup(line.classId);
        if (line.courseProductId != null && !Objects.equals(line.courseProductId, classGroup.getProductId())) {
            throw new BusinessException("CLASS_PRODUCT_MISMATCH", "Class group is not linked to the selected course product.");
        }
        if (classGroup.getProductId() == null) {
            throw new BusinessException("CLASS_PRODUCT_REQUIRED", "Class group must be linked to a group course product.");
        }
        CourseProductEntity product = requireCourseProduct(classGroup.getProductId());
        if (!"GROUP".equals(product.getTeachingMode()) || !"ACTIVE".equals(product.getStatus())) {
            throw new BusinessException("GROUP_PRODUCT_INVALID", "Class group product is not active or not a group product.");
        }
        line.courseProductId = product.getId();
        line.product = product;
        line.classGroup = classGroup;
        line.gradeBand = classGroup.getGradeBand();
    }

    private MoneySnapshot calculateMoney(RegistrationIntakeRequest request, List<ResolvedCourseLine> lines) {
        BigDecimal tuitionSubtotal = lines.stream()
                .map(line -> line.priceAmount)
                .reduce(ZERO_AMOUNT, BigDecimal::add);
        BigDecimal pickupFee = isPickupFeeCharged(request)
                ? defaultAmount(request.getPickupFeeAmount())
                : ZERO_AMOUNT;
        BigDecimal dropoffFee = isDropoffFeeCharged(request)
                ? defaultAmount(request.getDropoffFeeAmount())
                : ZERO_AMOUNT;
        BigDecimal extraFee = defaultAmount(firstNonNull(request.getExtraFeeAmount(), sumFeeLines(request.getExtraFees())));
        BigDecimal discount = defaultAmount(firstNonNull(request.getDiscountAmount(), sumFeeLines(request.getDiscounts())));
        BigDecimal originalAmount = tuitionSubtotal.add(pickupFee).add(dropoffFee).add(extraFee).setScale(2, RoundingMode.HALF_UP);
        if (discount.compareTo(originalAmount) > 0) {
            throw new BusinessException("DISCOUNT_EXCEEDS_AMOUNT", "Discount cannot exceed original amount.");
        }
        BigDecimal paid = request.getPayments() == null ? ZERO_AMOUNT : request.getPayments().stream()
                .map(RegistrationIntakeRequest.PaymentPayload::getAmount)
                .map(this::defaultAmount)
                .reduce(ZERO_AMOUNT, BigDecimal::add);
        BigDecimal amountDue = originalAmount.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        if (paid.compareTo(amountDue) > 0) {
            throw new BusinessException("PAYMENT_EXCEEDS_AMOUNT_DUE", "Paid amount cannot exceed amount due.");
        }
        String paymentStatus = resolvePaymentStatus(amountDue, paid);
        return new MoneySnapshot(tuitionSubtotal, pickupFee, dropoffFee, extraFee, originalAmount,
                discount, paid, amountDue, amountDue.subtract(paid), paymentStatus);
    }

    private boolean isPickupFeeCharged(RegistrationIntakeRequest request) {
        return Boolean.TRUE.equals(request.getPickupFeeCharged())
                && request.getStudent() != null
                && Boolean.TRUE.equals(request.getStudent().getPickupRequired());
    }

    private boolean isDropoffFeeCharged(RegistrationIntakeRequest request) {
        return Boolean.TRUE.equals(request.getDropoffFeeCharged())
                && request.getStudent() != null
                && Boolean.TRUE.equals(request.getStudent().getDropoffRequired());
    }

    private EnrollmentOrderEntity createOrder(RegistrationIntakeRequest request,
                                              StudentEntity student,
                                              List<ResolvedCourseLine> lines,
                                              MoneySnapshot money) {
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setEnrollmentNo(BusinessNoGenerator.generateEnrollmentNo());
        order.setStudentId(student.getId());
        order.setClassId(resolveSingleClassId(lines));
        order.setCampusId(request.getCampusId());
        order.setCampusNameSnapshot(resolveCampusName(request));
        order.setOrderStatus("CONFIRMED");
        order.setCashierUserId(request.getCashierUserId());
        order.setHandledByName(request.getHandledByName());
        order.setSignupDate(request.getSignupDate() == null ? LocalDate.now(AppTime.APP_ZONE_ID) : request.getSignupDate());
        order.setPeriodStartDate(request.getPeriodStartDate());
        order.setPeriodEndDate(request.getPeriodEndDate());
        order.setStudentNameSnapshot(student.getStudentName());
        order.setGradeSnapshot(student.getGrade());
        order.setSchoolSnapshot(student.getSchool());
        order.setParentPhoneSnapshot(student.getGuardianPhone());
        order.setParentPhone2Snapshot(student.getGuardianPhone2());
        order.setPickupRequiredSnapshot(Boolean.TRUE.equals(student.getPickupRequired()));
        order.setPickupAddressSnapshot(student.getPickupAddress());
        order.setDropoffRequiredSnapshot(Boolean.TRUE.equals(student.getDropoffRequired()));
        order.setDropoffAddressSnapshot(student.getDropoffAddress());
        order.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY);
        order.setTuitionSubtotalAmount(money.tuitionSubtotal());
        order.setPickupFeeAmount(money.pickupFeeAmount());
        order.setDropoffFeeAmount(money.dropoffFeeAmount());
        order.setExtraFeeAmount(money.extraFeeAmount());
        order.setExtraFeeDetail(jsonHelper.toJson(request.getExtraFees()));
        order.setOriginalAmount(money.originalAmount());
        order.setDiscountAmount(money.discountAmount());
        order.setDiscountDetail(jsonHelper.toJson(request.getDiscounts()));
        order.setReceivedAmount(money.paidAmount());
        order.setAmountDue(money.amountDue());
        order.setBalanceAmount(money.balanceAmount());
        order.setPaymentStatus(money.paymentStatus());
        order.setRemark(request.getRemark());
        enrollmentOrderMapper.insert(order);
        return order;
    }

    private EnrollmentOrderItemEntity createOrderItem(Long orderId, ResolvedCourseLine line) {
        EnrollmentOrderItemEntity item = new EnrollmentOrderItemEntity();
        item.setEnrollmentOrderId(orderId);
        item.setLineNo(line.lineNo);
        item.setRegisterClass(line.registerClass);
        item.setCourseType(line.courseType);
        item.setCourseProductId(line.courseProductId);
        item.setCoursePriceOptionId(line.coursePriceOptionId);
        item.setClassId(line.classId);
        item.setDetail(line.detail);
        item.setGradeBand(line.gradeBand);
        item.setPeriodLabel(line.periodLabel);
        item.setTimeSlotLabel(line.timeSlotLabel);
        item.setStartDate(line.startDate);
        item.setPackageUnit(line.packageUnit);
        item.setPackageQuantity(line.packageQuantity);
        item.setPackageTotalPrice(line.packageTotalPrice);
        item.setAttendanceOption(line.attendanceOption);
        item.setAttendanceScheduleJson(line.attendanceScheduleJson);
        item.setPriceAmount(line.priceAmount);
        item.setOriginalPriceAmount(line.originalPriceAmount);
        item.setManualPriceOverride(line.manualPriceOverride);
        item.setOneOnOneHours(line.oneOnOneHours);
        item.setOpeningUsedHours(line.openingUsedHours);
        item.setOneOnOneUnits(resolveLineOneOnOneUnits(line));
        item.setInitialLessonStartAt(null);
        item.setInitialLessonEndAt(null);
        item.setTeacherId(null);
        item.setItemStatus("ACTIVE");
        item.setRemark(line.remark);
        enrollmentOrderItemMapper.insert(item);
        return item;
    }

    private RegistrationIntakeResultVO.CourseArtifactVO createOneOnOneArtifact(EnrollmentOrderEntity order,
                                                                                StudentEntity student,
                                                                                ResolvedCourseLine line,
                                                                                BigDecimal allocatedPaid) {
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setPackageNo(BusinessNoGenerator.generatePackageNo());
        coursePackage.setEnrollmentOrderId(order.getId());
        coursePackage.setEnrollmentOrderItemId(line.orderItemId);
        coursePackage.setStudentId(student.getId());
        coursePackage.setCourseProductId(line.courseProductId);
        coursePackage.setPackageName(StringUtils.hasText(line.detail) ? line.detail : line.product.getProductName());
        coursePackage.setTeachingMode("ONE_ON_ONE");
        int totalUnits = resolveOneOnOneUnits(line);
        int openingUsedUnits = resolveOpeningUsedUnits(line);
        coursePackage.setTotalUnits(totalUnits);
        coursePackage.setConsumedUnits(openingUsedUnits);
        coursePackage.setRemainingUnits(totalUnits - openingUsedUnits);
        coursePackage.setOriginalAmount(line.priceAmount);
        coursePackage.setDiscountAmount(ZERO_AMOUNT);
        coursePackage.setReceivedAmount(allocatedPaid);
        coursePackage.setPackageStatus("ACTIVE");
        coursePackage.setStartDate(order.getPeriodStartDate());
        coursePackage.setEstimatedEndDate(order.getPeriodEndDate());
        coursePackage.setRemark(line.remark);
        coursePackageMapper.insert(coursePackage);

        return RegistrationIntakeResultVO.CourseArtifactVO.builder()
                .orderItemId(line.orderItemId)
                .lineNo(line.lineNo)
                .courseType(line.courseType)
                .packageId(coursePackage.getId())
                .packageNo(coursePackage.getPackageNo())
                .build();
    }

    private RegistrationIntakeResultVO.CourseArtifactVO createGroupClassArtifact(EnrollmentOrderEntity order,
                                                                                 StudentEntity student,
                                                                                 ResolvedCourseLine line) {
        ClassBillingResult billing = computeClassBilling(line.classGroup, line, order.getSignupDate());

        Long duplicateCount = classEnrollmentMapper.selectCount(
                new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getClassId, line.classId)
                        .eq(ClassEnrollmentEntity::getStudentId, student.getId())
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0));
        if (duplicateCount != null && duplicateCount > 0) {
            throw new BusinessException("ALREADY_ENROLLED", "Student is already enrolled in this class.");
        }

        ensureClassOccurrencesThrough(line.classGroup, line.product,
                billing.occurrenceWindowStart(), billing.occurrenceWindowEnd());
        ensureNoStudentClassEnrollmentConflicts(student.getId(), line.classId,
                billing.occurrenceWindowStart(), billing.occurrenceWindowEnd(), line.attendanceOption);

        ClassEnrollmentEntity enrollment = new ClassEnrollmentEntity();
        enrollment.setClassId(line.classId);
        enrollment.setStudentId(student.getId());
        enrollment.setEnrollmentOrderItemId(line.orderItemId);
        enrollment.setEnrollmentDate(billing.enrollmentDate());
        enrollment.setExpiryDate(billing.expiryDate());
        enrollment.setBillingType(line.packageUnit);
        enrollment.setPurchasedQuantity(billing.purchasedQuantity());
        enrollment.setAttendanceOption(normalizeAttendanceOption(line.attendanceOption));
        enrollment.setStatus("ACTIVE");
        classEnrollmentMapper.insert(enrollment);

        return RegistrationIntakeResultVO.CourseArtifactVO.builder()
                .orderItemId(line.orderItemId)
                .lineNo(line.lineNo)
                .courseType(line.courseType)
                .classEnrollmentId(enrollment.getId())
                .classId(line.classId)
                .build();
    }

    private void ensureNoStudentClassEnrollmentConflicts(Long studentId,
                                                         Long targetClassId,
                                                         LocalDate windowStart,
                                                         LocalDate windowEnd,
                                                         String attendanceOption) {
        LocalDateTime startAt = windowStart.atStartOfDay();
        LocalDateTime endAt = windowEnd.plusDays(1).atStartOfDay();
        List<LessonOccurrenceEntity> targetOccurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getClassGroupId, targetClassId)
                        .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                        .ge(LessonOccurrenceEntity::getPlannedStartAt, startAt)
                        .lt(LessonOccurrenceEntity::getPlannedStartAt, endAt));
        if (targetOccurrences.isEmpty()) {
            return;
        }

        List<LessonOccurrenceEntity> oneOnOneOccurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getStudentId, studentId)
                        .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                        .lt(LessonOccurrenceEntity::getPlannedStartAt, endAt)
                        .gt(LessonOccurrenceEntity::getPlannedEndAt, startAt));
        List<ClassEnrollmentEntity> existingEnrollments = classEnrollmentMapper.selectList(
                new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getStudentId, studentId)
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0));
        Map<Long, List<ClassEnrollmentEntity>> enrollmentsByClassId = existingEnrollments.stream()
                .filter(item -> !Objects.equals(item.getClassId(), targetClassId))
                .collect(Collectors.groupingBy(ClassEnrollmentEntity::getClassId));
        List<LessonOccurrenceEntity> existingClassOccurrences = enrollmentsByClassId.isEmpty()
                ? Collections.emptyList()
                : lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .in(LessonOccurrenceEntity::getClassGroupId, enrollmentsByClassId.keySet())
                        .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                        .lt(LessonOccurrenceEntity::getPlannedStartAt, endAt)
                        .gt(LessonOccurrenceEntity::getPlannedEndAt, startAt));

        for (LessonOccurrenceEntity target : targetOccurrences) {
            ClassGroupScheduleSlotEntity targetSlot = target.getScheduleSlotId() == null
                    ? null
                    : classGroupScheduleSlotMapper.selectById(target.getScheduleSlotId());
            if (!attendanceMatches(attendanceOption, targetSlot)) {
                continue;
            }
            LessonOccurrenceEntity conflict = firstOverlap(target, oneOnOneOccurrences);
            if (conflict != null) {
                throw new BusinessException("SCHEDULE_CONFLICT", "Student conflict detected for the selected class.",
                        List.of(buildConflictDetail("STUDENT", studentId, resolveStudentName(studentId), conflict)));
            }
            conflict = existingClassOccurrences.stream()
                    .filter(existing -> isEnrollmentEffectiveOnAndMatchesAttendance(
                            enrollmentsByClassId.get(existing.getClassGroupId()),
                            target.getPlannedStartAt().toLocalDate(),
                            existing))
                    .filter(existing -> overlaps(target, existing))
                    .findFirst()
                    .orElse(null);
            if (conflict != null) {
                throw new BusinessException("SCHEDULE_CONFLICT", "Student conflict detected for the selected class.",
                        List.of(buildConflictDetail("STUDENT", studentId, resolveStudentName(studentId), conflict)));
            }
        }
    }

    private boolean isEnrollmentEffectiveOnAndMatchesAttendance(List<ClassEnrollmentEntity> enrollments,
                                                                LocalDate date,
                                                                LessonOccurrenceEntity occurrence) {
        if (enrollments == null || enrollments.isEmpty()) {
            return false;
        }
        ClassGroupScheduleSlotEntity slot = occurrence.getScheduleSlotId() == null
                ? null
                : classGroupScheduleSlotMapper.selectById(occurrence.getScheduleSlotId());
        return enrollments.stream().anyMatch(enrollment ->
                (enrollment.getEnrollmentDate() == null || !date.isBefore(enrollment.getEnrollmentDate()))
                        && (enrollment.getExpiryDate() == null || !date.isAfter(enrollment.getExpiryDate()))
                        && attendanceMatches(enrollment.getAttendanceOption(), slot));
    }

    private boolean isEnrollmentEffectiveOn(List<ClassEnrollmentEntity> enrollments, LocalDate date) {
        return enrollments != null && enrollments.stream().anyMatch(enrollment ->
                (enrollment.getEnrollmentDate() == null || !date.isBefore(enrollment.getEnrollmentDate()))
                        && (enrollment.getExpiryDate() == null || !date.isAfter(enrollment.getExpiryDate())));
    }

    private boolean attendanceMatches(String enrollmentAttendance, ClassGroupScheduleSlotEntity slot) {
        String attendance = normalizeAttendanceOption(enrollmentAttendance);
        String segment = slot == null ? null : slot.getAttendanceOptions();
        if (!StringUtils.hasText(segment)) {
            return true;
        }
        String normalizedSegment = segment.trim().toUpperCase(Locale.ROOT);
        Set<String> segments = Arrays.stream(normalizedSegment.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (segments.isEmpty() || segments.contains("ALL")) {
            return true;
        }
        if ("FULL_DAY".equals(attendance)) {
            return segments.contains("FULL_DAY")
                    || segments.contains("MORNING")
                    || segments.contains("AFTERNOON");
        }
        return segments.contains(attendance);
    }

    private boolean overlaps(LessonOccurrenceEntity left, LessonOccurrenceEntity right) {
        return left.getPlannedStartAt().isBefore(right.getPlannedEndAt())
                && left.getPlannedEndAt().isAfter(right.getPlannedStartAt());
    }

    private List<RegistrationIntakeResultVO.PaymentResultVO> createPayments(EnrollmentOrderEntity order,
                                                                            RegistrationIntakeRequest request,
                                                                            MoneySnapshot money) {
        if (request.getPayments() == null || request.getPayments().isEmpty()) {
            return Collections.emptyList();
        }
        List<RegistrationIntakeResultVO.PaymentResultVO> result = new ArrayList<>();
        for (RegistrationIntakeRequest.PaymentPayload payment : request.getPayments()) {
            if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0
                    || payment.getAmount().stripTrailingZeros().scale() > 2) {
                throw new BusinessException("INVALID_PAYMENT_AMOUNT",
                        "Payment amount must be greater than zero and use at most 2 decimal places.");
            }
            requireActivePaymentMethod(payment.getPaymentMethod());
            if ("OTHER".equalsIgnoreCase(payment.getPaymentMethod())
                    && !StringUtils.hasText(payment.getRemark())) {
                throw new BusinessException("PAYMENT_METHOD_DETAIL_REQUIRED",
                        "Payment method details are required when using Other.");
            }
            PaymentRecordEntity entity = new PaymentRecordEntity();
            entity.setEnrollmentOrderId(order.getId());
            entity.setPaymentNo(BusinessNoGenerator.generatePaymentNo());
            entity.setPaymentMethod(payment.getPaymentMethod());
            entity.setPaymentType("PAYMENT");
            entity.setPaymentStatus("PAID");
            entity.setAmount(payment.getAmount());
            entity.setPaidAt(payment.getPaidAt() == null ? AppTime.nowLocalDateTime() : payment.getPaidAt());
            entity.setHandledByUserId(firstNonNull(payment.getHandledByUserId(), order.getCashierUserId()));
            entity.setHandledByName(firstText(payment.getHandledByName(), order.getHandledByName()));
            entity.setRemark(payment.getRemark());
            paymentRecordMapper.insert(entity);
            result.add(RegistrationIntakeResultVO.PaymentResultVO.builder()
                    .paymentId(entity.getId())
                    .paymentNo(entity.getPaymentNo())
                    .paymentMethod(entity.getPaymentMethod())
                    .amount(entity.getAmount())
                    .build());
        }
        if (!Objects.equals(order.getPaymentStatus(), money.paymentStatus())) {
            throw new BusinessException("PAYMENT_STATUS_MISMATCH", "Payment status calculation mismatch.");
        }
        return result;
    }

    private ReceiptResult createReceiptIfNeeded(EnrollmentOrderEntity order,
                                                StudentEntity student,
                                                RegistrationIntakeRequest request,
                                                List<ResolvedCourseLine> lines,
                                                MoneySnapshot money,
                                                List<RegistrationIntakeResultVO.PaymentResultVO> payments,
                                                List<RegistrationIntakeResultVO.CourseArtifactVO> artifacts) {
        RegistrationIntakeRequest.ReceiptOptionsPayload options = request.getReceiptOptions();
        if (options != null && Boolean.FALSE.equals(options.getIssueReceipt())) {
            return new ReceiptResult(null, null, null);
        }
        String receiptNo = BusinessNoGenerator.generateReceiptNo();
        Map<String, Object> previewData = buildReceiptPreview(order, student, request, lines, money, payments, artifacts);
        previewData.put("receiptNo", receiptNo);
        ReceiptRecordEntity receipt = new ReceiptRecordEntity();
        receipt.setReceiptNo(receiptNo);
        receipt.setEnrollmentOrderId(order.getId());
        receipt.setStudentId(student.getId());
        receipt.setTemplateCode(options != null && StringUtils.hasText(options.getTemplateCode())
                ? options.getTemplateCode()
                : DEFAULT_RECEIPT_TEMPLATE);
        receipt.setReceiptPayload(jsonHelper.toJson(previewData));
        receipt.setIssuedAt(AppTime.nowLocalDateTime());
        receipt.setIssuedBy(order.getCashierUserId());
        receiptRecordMapper.insert(receipt);
        return new ReceiptResult(receipt.getId(), receipt.getReceiptNo(), previewData);
    }

    private Map<String, Object> buildReceiptPreview(EnrollmentOrderEntity order,
                                                    StudentEntity student,
                                                    RegistrationIntakeRequest request,
                                                    List<ResolvedCourseLine> lines,
                                                    MoneySnapshot money,
                                                    List<RegistrationIntakeResultVO.PaymentResultVO> payments,
                                                    List<RegistrationIntakeResultVO.CourseArtifactVO> artifacts) {
        RegistrationIntakeRequest.ReceiptOptionsPayload options = request.getReceiptOptions();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("receiptNo", null);
        payload.put("enrollmentNo", order.getEnrollmentNo());
        payload.put("studentNo", student.getStudentNo());
        payload.put("studentName", student.getStudentName());
        payload.put("grade", student.getGrade());
        payload.put("school", student.getSchool());
        payload.put("guardianPhone", student.getGuardianPhone());
        payload.put("guardianPhone2", student.getGuardianPhone2());
        payload.put("campusId", order.getCampusId());
        payload.put("campusName", order.getCampusNameSnapshot());
        payload.put("signupDate", order.getSignupDate());
        payload.put("periodStartDate", order.getPeriodStartDate());
        payload.put("periodEndDate", order.getPeriodEndDate());
        payload.put("pickupRequired", order.getPickupRequiredSnapshot());
        payload.put("pickupAddress", order.getPickupAddressSnapshot());
        payload.put("dropoffRequired", order.getDropoffRequiredSnapshot());
        payload.put("dropoffAddress", order.getDropoffAddressSnapshot());
        payload.put("handledByName", order.getHandledByName());
        payload.put("currencyCode", order.getCurrencyCode());
        payload.put("items", lines.stream().map(this::toReceiptItem).toList());
        payload.put("extraFees", request.getExtraFees());
        payload.put("discounts", request.getDiscounts());
        payload.put("payments", payments);
        payload.put("artifacts", artifacts);
        payload.put("tuitionSubtotalAmount", money.tuitionSubtotal());
        payload.put("pickupFeeCharged", isPickupFeeCharged(request));
        payload.put("pickupFeeAmount", money.pickupFeeAmount());
        payload.put("dropoffFeeCharged", isDropoffFeeCharged(request));
        payload.put("dropoffFeeAmount", money.dropoffFeeAmount());
        payload.put("extraFeeAmount", money.extraFeeAmount());
        payload.put("originalAmount", money.originalAmount());
        payload.put("discountAmount", money.discountAmount());
        payload.put("amountDue", money.amountDue());
        payload.put("paidAmount", money.paidAmount());
        payload.put("balanceAmount", money.balanceAmount());
        payload.put("paymentStatus", money.paymentStatus());
        payload.put("showTaxId", options != null && Boolean.TRUE.equals(options.getShowTaxId()));
        payload.put("taxId", options == null ? null : options.getTaxId());
        payload.put("campusAddress", options == null ? null : options.getCampusAddress());
        payload.put("campusPhone", options == null ? null : options.getCampusPhone());
        payload.put("brandName", options == null ? null : options.getBrandName());
        payload.put("logoUrl", options == null ? null : options.getLogoUrl());
        payload.put("zelleQrCodeUrl", options == null ? null : options.getZelleQrCodeUrl());
        payload.put("policyText", options == null ? null : options.getPolicyText());
        return payload;
    }

    private Map<String, Object> toReceiptItem(ResolvedCourseLine line) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("orderItemId", line.orderItemId);
        item.put("lineNo", line.lineNo);
        item.put("registerClass", line.registerClass);
        item.put("courseType", line.courseType);
        item.put("courseProductId", line.courseProductId);
        item.put("courseProductName", line.product == null ? null : line.product.getProductName());
        item.put("detail", line.detail);
        item.put("gradeBand", firstText(line.gradeBand, line.classGroup == null ? null : line.classGroup.getGradeBand()));
        item.put("periodLabel", line.periodLabel);
        item.put("timeSlotLabel", line.timeSlotLabel);
        item.put("startDate", line.startDate);
        item.put("packageUnit", line.packageUnit);
        item.put("packageQuantity", line.packageQuantity);
        item.put("packageTotalPrice", line.packageTotalPrice);
        item.put("attendanceOption", line.attendanceOption);
        item.put("attendanceScheduleJson", line.attendanceScheduleJson);
        item.put("priceAmount", line.priceAmount);
        item.put("originalPriceAmount", line.originalPriceAmount);
        item.put("manualPriceOverride", line.manualPriceOverride);
        item.put("oneOnOneHours", line.oneOnOneHours);
        item.put("oneOnOneBonusHours", line.oneOnOneBonusHours);
        item.put("openingUsedHours", line.openingUsedHours);
        item.put("openingRemainingHours", line.oneOnOne ? BigDecimal.valueOf(resolveOneOnOneUnits(line) - resolveOpeningUsedUnits(line))
                .divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP) : null);
        item.put("oneOnOneUnits", resolveLineOneOnOneUnits(line));
        item.put("classId", line.classId);
        item.put("className", line.classGroup == null ? null : line.classGroup.getClassName());
        if (!line.oneOnOne) {
            item.put("groupBillingType", line.classGroup == null ? null : line.classGroup.getBillingType());
            item.put("groupTotalSessions", line.classGroup == null ? null : line.classGroup.getTotalSessions());
            item.put("groupStartMonth", line.classGroup == null ? null : formatRegistrationMonth(line.classGroup.getStartMonth()));
            item.put("groupEndMonth", line.classGroup == null ? null : formatRegistrationMonth(line.classGroup.getEndMonth()));
            item.put("sessions", line.sessions);
            item.put("purchasedMonths", line.purchasedMonths);
            item.put("startMonth", line.startMonth);
            item.put("endMonth", line.endMonth);
        }
        return item;
    }

    private RegistrationIntakeResultVO.AccountResultVO ensureStudentAccount(StudentEntity student) {
        UserAccountEntity existing = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccountEntity>()
                .eq(UserAccountEntity::getUserType, "STUDENT")
                .eq(UserAccountEntity::getStudentId, student.getId())
                .eq(UserAccountEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (existing != null) {
            activateExistingAccount(existing);
            ensureRoleBinding(existing.getId(), "STUDENT");
            return toAccountResult(existing, false, null);
        }

        String rawPassword = resolveInitialAccountPassword(student.getGuardianPhone());
        UserAccountEntity account = new UserAccountEntity();
        account.setUsername(generateUniqueJinUsername(student.getStudentName(), student.getStudentNo()));
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        account.setUserType("STUDENT");
        account.setDisplayName(student.getStudentName());
        account.setMobile(student.getGuardianPhone());
        account.setEmail(student.getGuardianEmail());
        account.setStudentId(student.getId());
        account.setStatus("ACTIVE");
        userAccountMapper.insert(account);
        ensureRoleBinding(account.getId(), "STUDENT");
        return toAccountResult(account, true, rawPassword);
    }

    private RegistrationIntakeResultVO.AccountResultVO toAccountResult(UserAccountEntity account,
                                                                       boolean created,
                                                                       String initialPassword) {
        return RegistrationIntakeResultVO.AccountResultVO.builder()
                .userId(account.getId())
                .username(account.getUsername())
                .displayName(account.getDisplayName())
                .userType(account.getUserType())
                .created(created)
                .initialPassword(initialPassword)
                .build();
    }

    private void activateExistingAccount(UserAccountEntity account) {
        if (!"ACTIVE".equals(account.getStatus())) {
            account.setStatus("ACTIVE");
            userAccountMapper.updateById(account);
        }
    }

    private void ensureRoleBinding(Long userId, String roleCode) {
        RoleEntity role = roleMapper.selectOne(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getRoleCode, roleCode)
                .eq(RoleEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (role == null) {
            throw new BusinessException(roleCode + "_ROLE_NOT_FOUND", roleCode + " role is missing from the system.");
        }
        Long existingCount = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getUserId, userId)
                .eq(UserRoleEntity::getRoleId, role.getId()));
        if (existingCount != null && existingCount > 0) {
            return;
        }
        UserRoleEntity userRole = new UserRoleEntity();
        userRole.setUserId(userId);
        userRole.setRoleId(role.getId());
        userRole.setCreatedAt(AppTime.nowLocalDateTime());
        userRoleMapper.insert(userRole);
    }

    private String generateUniqueJinUsername(String personName, String fallbackSeed) {
        String base = buildJinUsernameBase(personName);
        if (!StringUtils.hasText(base)) {
            base = sanitizeUsername(fallbackSeed).toLowerCase();
        }
        String candidate = base + JIN_USERNAME_DOMAIN;
        int suffix = 2;
        while (usernameExists(candidate)) {
            String suffixText = String.valueOf(suffix++);
            int maxBaseLength = Math.max(1, 64 - JIN_USERNAME_DOMAIN.length() - suffixText.length());
            candidate = base.substring(0, Math.min(base.length(), maxBaseLength)) + suffixText + JIN_USERNAME_DOMAIN;
        }
        return candidate;
    }

    private String buildJinUsernameBase(String personName) {
        if (!StringUtils.hasText(personName)) {
            return "";
        }
        String[] parts = personName.trim().split("\\s+");
        String firstName = sanitizeUsername(parts[0]).toLowerCase();
        if (!StringUtils.hasText(firstName)) {
            return "";
        }
        String lastInitial = "";
        if (parts.length > 1) {
            String lastName = sanitizeUsername(parts[parts.length - 1]).toLowerCase();
            if (StringUtils.hasText(lastName)) {
                lastInitial = lastName.substring(0, 1);
            }
        }
        int maxBaseLength = Math.max(1, 64 - JIN_USERNAME_DOMAIN.length());
        String base = firstName + lastInitial;
        return base.substring(0, Math.min(base.length(), maxBaseLength));
    }

    private String sanitizeUsername(String seed) {
        String value = StringUtils.hasText(seed) ? seed.trim() : "user";
        value = value.replaceAll("[^A-Za-z0-9._-]", "");
        if (!StringUtils.hasText(value)) {
            value = "user";
        }
        return value.substring(0, Math.min(value.length(), 64));
    }

    /**
     * Phone digits are preferred for 1-on-1 student and teacher accounts.
     * The fixed fallback keeps account creation available when a phone is missing.
     */
    private String resolveInitialAccountPassword(String phone) {
        String normalizedPhone = normalizePhone(phone);
        return StringUtils.hasText(normalizedPhone) ? normalizedPhone : DEFAULT_ACCOUNT_PASSWORD;
    }

    private String normalizePhone(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        return StringUtils.hasText(digits) ? digits : null;
    }

    private boolean usernameExists(String username) {
        return userAccountMapper.selectCount(new LambdaQueryWrapper<UserAccountEntity>()
                .eq(UserAccountEntity::getUsername, username)
                .eq(UserAccountEntity::getIsDeleted, 0)) > 0;
    }

    private ClassGroupEntity ensureClassGroupForGrade(CourseProductEntity product, String gradeBand) {
        String normalizedGradeBand = StringUtils.hasText(gradeBand) ? gradeBand.trim() : "General";
        ClassGroupEntity existing = classGroupMapper.selectOne(new LambdaQueryWrapper<ClassGroupEntity>()
                .eq(ClassGroupEntity::getProductId, product.getId())
                .eq(ClassGroupEntity::getGradeBand, normalizedGradeBand)
                .eq(ClassGroupEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (existing != null) {
            if (!"ACTIVE".equals(existing.getStatus())) {
                throw new BusinessException("CLASS_INACTIVE", "Linked class group is not active.");
            }
            return existing;
        }
        throw new BusinessException("GRADE_CLASS_NOT_CONFIGURED",
                "Grade class is not configured. Add the grade class in Price Matrix before registration.");
    }

    private ClassBillingResult computeClassBilling(ClassGroupEntity classGroup,
                                                    ResolvedCourseLine line,
                                                    LocalDate signupDate) {
        LocalDate today = signupDate == null ? LocalDate.now(AppTime.APP_ZONE_ID) : signupDate;
        List<ClassGroupScheduleSlotEntity> slots = loadClassSlots(classGroup);
        if (slots.isEmpty()) {
            throw new BusinessException("GRADE_CLASS_NO_SLOTS", "Grade class has no schedule slots.");
        }
        String unit = StringUtils.hasText(line.packageUnit) ? line.packageUnit : "MONTH";
        int quantity = line.packageQuantity == null
                ? resolveGroupPurchasedQuantity(line)
                : line.packageQuantity.setScale(0, RoundingMode.CEILING).intValue();
        if (quantity < 1) {
            throw new BusinessException("REGISTRATION_DURATION_INVALID",
                    "Selected package quantity must be greater than zero.");
        }
        LocalDate enrollmentDate = line.startDate == null ? today : line.startDate;
        LocalDate expiryDate;
        LocalDate configuredStart = classGroup.getStartDate();
        LocalDate configuredEnd = classGroup.getEndDate();
        if (configuredStart != null && configuredEnd != null && configuredEnd.isBefore(configuredStart)) {
            throw new BusinessException("INVALID_GROUP_DATE_RANGE",
                    "Class end date cannot be earlier than the start date.");
        }
        if (configuredStart != null) {
            if (line.startDate != null && line.startDate.isBefore(configuredStart)) {
                throw new BusinessException("ENROLLMENT_START_OUT_OF_RANGE",
                        "Enrollment start date must be within the class date range.");
            }
            if (line.startDate == null && enrollmentDate.isBefore(configuredStart)) {
                enrollmentDate = configuredStart;
            }
        }
        if (configuredEnd != null) {
            if (enrollmentDate.isAfter(configuredEnd)) {
                throw new BusinessException("ENROLLMENT_START_OUT_OF_RANGE",
                        "Enrollment start date must be within the class date range.");
            }
        }
        if ("MONTH".equals(unit)) {
            YearMonth selectedEnd = YearMonth.from(enrollmentDate).plusMonths(quantity - 1L);
            expiryDate = selectedEnd.atEndOfMonth();
        } else if ("WEEK".equals(unit)) {
            expiryDate = enrollmentDate.plusWeeks(quantity).minusDays(1);
        } else if ("SESSION".equals(unit)) {
            expiryDate = computeNthSessionDate(enrollmentDate, slots, quantity);
        } else {
            throw new BusinessException("INVALID_PACKAGE_UNIT",
                    "Group class package unit must be MONTH, WEEK, or SESSION.");
        }
        if (configuredEnd != null && expiryDate.isAfter(configuredEnd)) {
            throw new BusinessException("ENROLLMENT_DURATION_OUT_OF_RANGE",
                    "Selected package duration exceeds the class date range.");
        }
        return new ClassBillingResult(enrollmentDate, expiryDate, enrollmentDate, expiryDate, quantity);
    }

    private int ensureClassOccurrencesThrough(ClassGroupEntity classGroup,
                                               CourseProductEntity product,
                                               LocalDate windowStart,
                                               LocalDate horizonEnd) {
        List<ClassGroupScheduleSlotEntity> slots = loadClassSlots(classGroup);
        if (slots.isEmpty()) {
            throw new BusinessException("GRADE_CLASS_NO_SLOTS", "Grade class has no schedule slots.");
        }

        LocalDateTime windowStartAt = windowStart.atStartOfDay();
        LocalDateTime windowEndAt = horizonEnd.plusDays(1).atStartOfDay();
        Set<LocalDateTime> existingStarts = lessonOccurrenceMapper.selectList(
                        new LambdaQueryWrapper<LessonOccurrenceEntity>()
                                .eq(LessonOccurrenceEntity::getClassGroupId, classGroup.getId())
                                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                                .ge(LessonOccurrenceEntity::getPlannedStartAt, windowStartAt)
                                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEndAt))
                .stream()
                .map(LessonOccurrenceEntity::getPlannedStartAt)
                .collect(Collectors.toSet());

        List<LessonOccurrenceEntity> occurrences = new ArrayList<>();
        for (LocalDate date = windowStart; !date.isAfter(horizonEnd); date = date.plusDays(1)) {
            int weekday = date.getDayOfWeek().getValue();
            for (ClassGroupScheduleSlotEntity slot : slots) {
                if (!Objects.equals(slot.getWeekday(), weekday) || !slot.getEndTime().isAfter(slot.getStartTime())) {
                    continue;
                }
                LocalDateTime plannedStartAt = LocalDateTime.of(date, slot.getStartTime());
                if (existingStarts.contains(plannedStartAt)) {
                    continue;
                }
                long minutes = ChronoUnit.MINUTES.between(slot.getStartTime(), slot.getEndTime());
                LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
                occurrence.setLessonNo(BusinessNoGenerator.generateClassLessonOccurrenceNo());
                occurrence.setClassGroupId(classGroup.getId());
                occurrence.setScheduleSlotId(slot.getId());
                occurrence.setOccurrenceType("REGULAR");
                occurrence.setPlannedTeacherId(slot.getTeacherId() == null
                        ? classGroup.getTeacherId()
                        : slot.getTeacherId());
                occurrence.setPlannedClassroomId(classGroup.getClassroomId());
                occurrence.setPlannedStartAt(plannedStartAt);
                occurrence.setPlannedEndAt(LocalDateTime.of(date, slot.getEndTime()));
                occurrence.setPlannedUnits((int) (minutes / 30));
                occurrence.setPlanningStatus("SCHEDULED");
                occurrence.setConflictChecked(1);
                occurrences.add(occurrence);
            }
        }

        if (!occurrences.isEmpty()) {
            occurrences.sort(Comparator.comparing(LessonOccurrenceEntity::getPlannedStartAt));
            ensureNoConflicts(occurrences, null, null, classGroup.getClassroomId());
            occurrences.stream()
                    .filter(item -> item.getPlannedTeacherId() != null)
                    .collect(Collectors.groupingBy(LessonOccurrenceEntity::getPlannedTeacherId))
                    .forEach((teacherId, teacherOccurrences) ->
                            ensureNoConflicts(teacherOccurrences, null, teacherId, null));
            occurrences.forEach(lessonOccurrenceMapper::insert);
        }
        return occurrences.size();
    }

    private void ensureNoConflicts(List<LessonOccurrenceEntity> generatedOccurrences,
                                   Long studentId,
                                   Long teacherId,
                                   Long classroomId) {
        LocalDateTime windowStart = generatedOccurrences.stream()
                .map(LessonOccurrenceEntity::getPlannedStartAt)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        LocalDateTime windowEnd = generatedOccurrences.stream()
                .map(LessonOccurrenceEntity::getPlannedEndAt)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        List<LessonOccurrenceEntity> studentOccurrences = studentId == null ? Collections.emptyList()
                : lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getStudentId, studentId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));
        List<LessonOccurrenceEntity> teacherOccurrences = teacherId == null ? Collections.emptyList()
                : lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPlannedTeacherId, teacherId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));
        List<LessonOccurrenceEntity> classroomOccurrences = classroomId == null ? Collections.emptyList()
                : lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPlannedClassroomId, classroomId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));

        List<ScheduleConflictDetailVO> conflicts = new ArrayList<>();
        for (LessonOccurrenceEntity candidate : generatedOccurrences) {
            LessonOccurrenceEntity studentConflict = firstOverlap(candidate, studentOccurrences);
            if (studentId != null && studentConflict != null) {
                conflicts.add(buildConflictDetail("STUDENT", studentId, resolveStudentName(studentId), studentConflict));
            }
            LessonOccurrenceEntity teacherConflict = firstOverlap(candidate, teacherOccurrences);
            if (teacherId != null && teacherConflict != null) {
                conflicts.add(buildConflictDetail("TEACHER", teacherId, resolveTeacherName(teacherId), teacherConflict));
            }
            LessonOccurrenceEntity classroomConflict = firstOverlap(candidate, classroomOccurrences);
            if (classroomId != null && classroomConflict != null) {
                conflicts.add(buildConflictDetail("CLASSROOM", classroomId, null, classroomConflict));
            }
            if (conflicts.size() >= 3) {
                break;
            }
        }
        if (!conflicts.isEmpty()) {
            throw new BusinessException("SCHEDULE_CONFLICT", "Schedule conflict detected.", conflicts);
        }
    }

    private LessonOccurrenceEntity firstOverlap(LessonOccurrenceEntity candidate, List<LessonOccurrenceEntity> existingOccurrences) {
        return existingOccurrences.stream().filter(existing ->
                candidate.getPlannedStartAt().isBefore(existing.getPlannedEndAt())
                        && candidate.getPlannedEndAt().isAfter(existing.getPlannedStartAt()))
                .findFirst()
                .orElse(null);
    }

    private ScheduleConflictDetailVO buildConflictDetail(String resourceType,
                                                         Long resourceId,
                                                         String resourceName,
                                                         LessonOccurrenceEntity existing) {
        return ScheduleConflictDetailVO.builder()
                .resourceType(resourceType)
                .resourceId(resourceId)
                .resourceName(resourceName)
                .conflictStartAt(existing.getPlannedStartAt())
                .conflictEndAt(existing.getPlannedEndAt())
                .existingOccurrenceId(existing.getId())
                .existingLessonNo(existing.getLessonNo())
                .build();
    }

    private String resolveStudentName(Long studentId) {
        StudentEntity student = studentMapper.selectById(studentId);
        return student == null ? null : student.getStudentName();
    }

    private String resolveTeacherName(Long teacherId) {
        TeacherEntity teacher = teacherMapper.selectById(teacherId);
        return teacher == null ? null : teacher.getTeacherName();
    }

    private List<ClassGroupScheduleSlotEntity> loadClassSlots(ClassGroupEntity classGroup) {
        return classGroupScheduleSlotMapper.selectList(
                new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                        .eq(ClassGroupScheduleSlotEntity::getClassGroupId, classGroup.getId())
                        .eq(ClassGroupScheduleSlotEntity::getIsDeleted, 0));
    }

    private LocalDate computeNthSessionDate(LocalDate startDate, List<ClassGroupScheduleSlotEntity> slots, int n) {
        Set<Integer> weekdays = slots.stream()
                .map(ClassGroupScheduleSlotEntity::getWeekday)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        int count = 0;
        LocalDate limit = startDate.plusYears(10);
        for (LocalDate date = startDate; !date.isAfter(limit); date = date.plusDays(1)) {
            if (weekdays.contains(date.getDayOfWeek().getValue())) {
                count++;
                if (count >= n) {
                    return date;
                }
            }
        }
        throw new BusinessException("SESSION_DATE_UNRESOLVED", "Cannot resolve the date for the selected sessions.");
    }

    private YearMonth parseRequestMonth(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("INVALID_MONTH", "Schedule " + fieldName + " is required.");
        }
        try {
            return YearMonth.parse(value);
        } catch (RuntimeException ex) {
            throw new BusinessException("INVALID_MONTH", "Schedule " + fieldName + " must use yyyy-MM format.");
        }
    }

    private CoursePriceOptionEntity resolvePriceOption(Long id) {
        if (id == null) {
            return null;
        }
        CoursePriceOptionEntity option = coursePriceOptionMapper.selectById(id);
        if (option == null || !"ACTIVE".equals(option.getStatus())) {
            throw new BusinessException("COURSE_PRICE_OPTION_NOT_FOUND", "Course price option not found or inactive.");
        }
        return option;
    }

    private BigDecimal resolveOptionBasePrice(CoursePriceOptionEntity option, String attendanceOption) {
        if (isHalfDay(attendanceOption) && option.getHalfDayPrice() != null) {
            return option.getHalfDayPrice();
        }
        if (isFullDay(attendanceOption) && option.getFullDayPrice() != null) {
            return option.getFullDayPrice();
        }
        if (option.getTotalPrice() != null) {
            return option.getTotalPrice();
        }
        if (option.getGroupTotalPrice() != null) {
            return option.getGroupTotalPrice();
        }
        return option.getPriceAmount();
    }

    private String resolveOptionPackageUnit(CoursePriceOptionEntity option) {
        if (StringUtils.hasText(option.getPackageUnit())) {
            return option.getPackageUnit();
        }
        if ("ONE_ON_ONE".equals(option.getCourseType())) {
            return "HOUR";
        }
        return "MONTH";
    }

    private BigDecimal resolveOptionPackageQuantity(CoursePriceOptionEntity option) {
        if (option.getPackageQuantity() != null && option.getPackageQuantity().compareTo(BigDecimal.ZERO) > 0) {
            return option.getPackageQuantity();
        }
        if ("HOUR".equals(resolveOptionPackageUnit(option))) {
            return option.getOneOnOneHours();
        }
        return BigDecimal.ONE;
    }

    private BigDecimal resolveOptionTotalPrice(CoursePriceOptionEntity option) {
        if (option.getTotalPrice() != null) {
            return option.getTotalPrice();
        }
        if (option.getGroupTotalPrice() != null) {
            return option.getGroupTotalPrice();
        }
        return option.getPriceAmount();
    }

    private String normalizeAttendanceOption(String value) {
        if (!StringUtils.hasText(value)) {
            return "NONE";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isHalfDay(String attendanceOption) {
        String normalized = normalizeAttendanceOption(attendanceOption);
        return "MORNING".equals(normalized) || "AFTERNOON".equals(normalized) || "CUSTOM".equals(normalized);
    }

    private boolean isFullDay(String attendanceOption) {
        return "FULL_DAY".equals(normalizeAttendanceOption(attendanceOption));
    }

    private CourseProductEntity requireCourseProduct(Long id) {
        CourseProductEntity product = courseProductMapper.selectById(id);
        if (product == null) {
            throw new BusinessException("COURSE_PRODUCT_NOT_FOUND", "Course product not found.");
        }
        return product;
    }

    private TeacherEntity requireTeacher(Long id) {
        TeacherEntity teacher = teacherMapper.selectById(id);
        if (teacher == null || !"ACTIVE".equals(teacher.getStatus())) {
            throw new BusinessException("TEACHER_NOT_AVAILABLE", "Teacher not found or inactive.");
        }
        return teacher;
    }

    private void requireActivePaymentMethod(String methodCode) {
        PaymentMethodEntity method = paymentMethodMapper.selectOne(new LambdaQueryWrapper<PaymentMethodEntity>()
                .eq(PaymentMethodEntity::getMethodCode, methodCode)
                .eq(PaymentMethodEntity::getStatus, "ACTIVE")
                .eq(PaymentMethodEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (method == null) {
            throw new BusinessException("PAYMENT_METHOD_NOT_AVAILABLE",
                    "Payment method not found or inactive.");
        }
    }

    private void validateRegistrationPeriod(RegistrationIntakeRequest request) {
        if (request.getPeriodStartDate() != null
                && request.getPeriodEndDate() != null
                && request.getPeriodEndDate().isBefore(request.getPeriodStartDate())) {
            throw new BusinessException("INVALID_REGISTRATION_PERIOD",
                    "Registration period end date cannot be earlier than start date.");
        }
    }

    private void validateCampusSelection(RegistrationIntakeRequest request) {
        resolveCampusName(request);
    }

    private ClassGroupEntity requireActiveClassGroup(Long id) {
        ClassGroupEntity classGroup = classGroupMapper.selectById(id);
        if (classGroup == null || !"ACTIVE".equals(classGroup.getStatus())) {
            throw new BusinessException("CLASS_NOT_FOUND", "Class not found or not active.");
        }
        return classGroup;
    }

    private Long resolveSingleClassId(List<ResolvedCourseLine> lines) {
        List<Long> classIds = lines.stream()
                .map(line -> line.classId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return classIds.size() == 1 ? classIds.get(0) : null;
    }

    private String resolveCampusName(RegistrationIntakeRequest request) {
        if (request.getCampusId() != null) {
            CampusEntity campus = campusMapper.selectById(request.getCampusId());
            if (campus == null || !"ACTIVE".equals(campus.getStatus())) {
                throw new BusinessException("CAMPUS_NOT_AVAILABLE", "Campus not found or inactive.");
            }
            return campus.getCampusName();
        }
        if (StringUtils.hasText(request.getCampusName())
                && request.getCampusName().trim().toUpperCase().contains("ONLINE")) {
            return request.getCampusName().trim();
        }
        throw new BusinessException("CAMPUS_REQUIRED", "Please select an active campus or Online Class.");
    }

    private boolean isOneOnOneLine(ResolvedCourseLine line, CourseProductEntity product) {
        String registerClass = line.registerClass == null ? "" : line.registerClass.toUpperCase().replaceAll("[^A-Z0-9]", "");
        return "ONE_ON_ONE".equals(line.courseType)
                || "ONE_ON_ONE".equals(product == null ? null : product.getTeachingMode())
                || "1V1".equals(registerClass)
                || "11".equals(registerClass)
                || "ONEONONE".equals(registerClass);
    }

    private int resolveOneOnOnePurchasedUnits(ResolvedCourseLine line) {
        if (line.oneOnOneHours != null && line.oneOnOneHours.compareTo(BigDecimal.ZERO) > 0) {
            return line.oneOnOneHours.multiply(BigDecimal.valueOf(2))
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValue();
        }
        return 0;
    }

    private int resolveOneOnOneBonusUnits(ResolvedCourseLine line) {
        if (line.oneOnOneBonusHours != null && line.oneOnOneBonusHours.compareTo(BigDecimal.ZERO) > 0) {
            return line.oneOnOneBonusHours.multiply(BigDecimal.valueOf(2))
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValue();
        }
        return 0;
    }

    private int resolveOneOnOneUnits(ResolvedCourseLine line) {
        return resolveOneOnOnePurchasedUnits(line) + resolveOneOnOneBonusUnits(line);
    }

    private int resolveLineOneOnOneUnits(ResolvedCourseLine line) {
        return line.oneOnOne ? resolveOneOnOneUnits(line) : 0;
    }

    private int resolveOpeningUsedUnits(ResolvedCourseLine line) {
        if (!line.oneOnOne || line.openingUsedHours == null || line.openingUsedHours.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        return line.openingUsedHours.multiply(BigDecimal.valueOf(2))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    private BigDecimal sumFeeLines(List<RegistrationIntakeRequest.FeeLinePayload> lines) {
        if (lines == null || lines.isEmpty()) {
            return ZERO_AMOUNT;
        }
        return lines.stream()
                .map(RegistrationIntakeRequest.FeeLinePayload::getAmount)
                .map(this::defaultAmount)
                .reduce(ZERO_AMOUNT, BigDecimal::add);
    }

    private String resolvePaymentStatus(BigDecimal amountDue, BigDecimal paidAmount) {
        if (amountDue.compareTo(ZERO_AMOUNT) <= 0) {
            return "PAID";
        }
        if (paidAmount.compareTo(ZERO_AMOUNT) <= 0) {
            return "UNPAID";
        }
        if (paidAmount.compareTo(amountDue) >= 0) {
            return "PAID";
        }
        return "PARTIAL";
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? ZERO_AMOUNT : amount.setScale(2, RoundingMode.HALF_UP);
    }

    private YearMonth parseRegistrationMonth(String value, String fieldName) {
        try {
            return YearMonth.parse(value, DateTimeFormatter.ofPattern("yyyy-MM"));
        } catch (RuntimeException ex) {
            throw new BusinessException("REGISTRATION_MONTH_INVALID",
                    "Invalid " + fieldName + ". Use yyyy-MM format.");
        }
    }

    private String formatRegistrationMonth(LocalDate value) {
        return value == null ? null : YearMonth.from(value).format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private record StudentResolution(StudentEntity student, boolean reused) {
    }

    private record MoneySnapshot(
            BigDecimal tuitionSubtotal,
            BigDecimal pickupFeeAmount,
            BigDecimal dropoffFeeAmount,
            BigDecimal extraFeeAmount,
            BigDecimal originalAmount,
            BigDecimal discountAmount,
            BigDecimal paidAmount,
            BigDecimal amountDue,
            BigDecimal balanceAmount,
            String paymentStatus
    ) {
    }

    private record ReceiptResult(Long receiptId, String receiptNo, Map<String, Object> previewData) {
    }

    private record ClassBillingResult(
            LocalDate enrollmentDate,
            LocalDate expiryDate,
            LocalDate occurrenceWindowStart,
            LocalDate occurrenceWindowEnd,
            Integer purchasedQuantity
    ) {
    }

    private static class ResolvedCourseLine {
        private Long orderItemId;
        private Integer lineNo;
        private String registerClass;
        private String courseType;
        private Long courseProductId;
        private Long coursePriceOptionId;
        private Long classId;
        private String detail;
        private String gradeBand;
        private String periodLabel;
        private String timeSlotLabel;
        private LocalDate startDate;
        private String packageUnit;
        private BigDecimal packageQuantity;
        private BigDecimal packageTotalPrice;
        private String attendanceOption;
        private String attendanceScheduleJson;
        private BigDecimal priceAmount;
        private BigDecimal originalPriceAmount;
        private Boolean manualPriceOverride;
        private BigDecimal oneOnOneHours;
        private BigDecimal oneOnOneBonusHours;
        private BigDecimal openingUsedHours;
        private Integer oneOnOneUnits;
        private String remark;
        private Integer sessions;
        private Integer purchasedMonths;
        private String startMonth;
        private String endMonth;
        private boolean oneOnOne;
        private CourseProductEntity product;
        private ClassGroupEntity classGroup;
    }
}
