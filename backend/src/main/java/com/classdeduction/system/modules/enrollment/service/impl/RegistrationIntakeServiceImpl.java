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
import com.classdeduction.system.modules.base.entity.CourseProductScheduleSlotEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.CampusMapper;
import com.classdeduction.system.modules.base.mapper.CoursePriceOptionMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductScheduleSlotMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.RegistrationIntakeRequest;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentMethodEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.entity.ReceiptRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
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
    private final CourseProductScheduleSlotMapper courseProductScheduleSlotMapper;
    private final TeacherMapper teacherMapper;
    private final EnrollmentOrderMapper enrollmentOrderMapper;
    private final EnrollmentOrderItemMapper enrollmentOrderItemMapper;
    private final CoursePackageMapper coursePackageMapper;
    private final PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;
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
        List<RegistrationIntakeResultVO.AccountResultVO> teacherAccounts = Collections.emptyList();
        if (lines.stream().anyMatch(line -> line.oneOnOne)) {
            studentAccount = ensureStudentAccount(studentResolution.student());
            teacherAccounts = ensureTeacherAccounts(lines);
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
                .teacherAccounts(teacherAccounts)
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
            BigDecimal configuredUnitPrice = requireNonNegativeAmount(option.getPriceAmount(), "COURSE_PRICE_REQUIRED",
                    "Configured course price is required.");

            ResolvedCourseLine line = new ResolvedCourseLine();
            line.lineNo = item.getLineNo() == null ? nextLineNo : item.getLineNo();
            nextLineNo = Math.max(nextLineNo + 1, line.lineNo + 1);
            line.coursePriceOptionId = item.getCoursePriceOptionId();
            line.courseProductId = productId;
            line.registerClass = firstText(item.getRegisterClass(), option == null ? null : option.getRegisterClass());
            line.courseType = firstText(item.getCourseType(), option == null ? null : option.getCourseType());
            line.detail = firstText(item.getDetail(), option == null ? null : option.getDetailText());
            line.gradeBand = firstText(item.getGradeBand(), option == null ? null : option.getGradeBand());
            line.periodLabel = firstText(item.getPeriodLabel(), option == null ? null : option.getPeriodLabel());
            line.timeSlotLabel = firstText(item.getTimeSlotLabel(), option == null ? null : option.getTimeSlotLabel());
            line.oneOnOneHours = firstNonNull(item.getOneOnOneHours(), option == null ? null : option.getOneOnOneHours());
            line.oneOnOneUnits = item.getOneOnOneUnits();
            line.initialLessonStartAt = item.getInitialLessonStartAt();
            line.initialLessonEndAt = item.getInitialLessonEndAt();
            line.teacherId = item.getTeacherId();
            line.classId = firstNonNull(item.getClassId(), option.getClassId());
            line.sessions = item.getSessions();
            line.startMonth = item.getStartMonth();
            line.endMonth = item.getEndMonth();
            line.remark = item.getRemark();
            line.product = product;
            line.oneOnOne = isOneOnOneLine(line, product);
            applyLinePricing(line, item, configuredUnitPrice);

            validateResolvedLine(line);
            lines.add(line);
        }
        return lines;
    }

    private void applyLinePricing(ResolvedCourseLine line,
                                  RegistrationIntakeRequest.CourseItemPayload item,
                                  BigDecimal configuredUnitPrice) {
        boolean manualPriceOverride = Boolean.TRUE.equals(item.getManualPriceOverride());
        BigDecimal configuredPrice = line.oneOnOne ? configuredUnitPrice : configuredUnitPrice.multiply(
                BigDecimal.valueOf(resolveGroupPurchasedQuantity(line)));
        if (!manualPriceOverride && configuredPrice.compareTo(ZERO_AMOUNT) <= 0) {
            throw new BusinessException("COURSE_LINE_PRICE_REQUIRED",
                    "Manual course line price is required for a $0 course template.");
        }
        line.originalPriceAmount = defaultAmount(firstNonNull(item.getOriginalPriceAmount(), configuredPrice));
        line.priceAmount = manualPriceOverride
                ? requirePositiveAmount(item.getPriceAmount(), "COURSE_LINE_PRICE_REQUIRED",
                        "Manual course line price is required.")
                : defaultAmount(configuredPrice);
        line.manualPriceOverride = manualPriceOverride;
    }

    private int resolveGroupPurchasedQuantity(ResolvedCourseLine line) {
        if (line.oneOnOne) {
            return 1;
        }
        String billingType = line.product == null ? null : line.product.getBillingType();
        if ("MONTHLY".equals(billingType)
                || (!"PER_SESSION".equals(billingType)
                && (StringUtils.hasText(line.startMonth) || StringUtils.hasText(line.endMonth)))) {
            if (!StringUtils.hasText(line.startMonth) || !StringUtils.hasText(line.endMonth)) {
                throw new BusinessException("REGISTRATION_DURATION_MISSING",
                        "Enter purchased months on the registration form.");
            }
            YearMonth start = parseRegistrationMonth(line.startMonth, "start month");
            YearMonth end = parseRegistrationMonth(line.endMonth, "end month");
            long months = ChronoUnit.MONTHS.between(start, end) + 1;
            if (months < 1) {
                throw new BusinessException("REGISTRATION_DURATION_INVALID",
                        "Selected end month cannot be earlier than start month.");
            }
            return Math.toIntExact(months);
        }
        if (line.sessions == null || line.sessions < 1) {
            throw new BusinessException("REGISTRATION_DURATION_MISSING",
                    "Enter purchased sessions on the registration form.");
        }
        return line.sessions;
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
        if (!StringUtils.hasText(line.detail)) {
            throw new BusinessException("DETAIL_REQUIRED", "Course detail is required for every course line.");
        }
        if (line.priceAmount.compareTo(ZERO_AMOUNT) < 0) {
            throw new BusinessException("INVALID_LINE_PRICE", "Course line price cannot be negative.");
        }
        if (line.manualPriceOverride && !StringUtils.hasText(line.remark)) {
            throw new BusinessException("MANUAL_PRICE_REASON_REQUIRED",
                    "Manual course price requires a reason.");
        }
        if (line.oneOnOne) {
            if (line.teacherId != null) {
                requireTeacher(line.teacherId);
            }
            if (line.product == null || !"ONE_ON_ONE".equals(line.product.getTeachingMode()) || !"ACTIVE".equals(line.product.getStatus())) {
                throw new BusinessException("ONE_ON_ONE_PRODUCT_REQUIRED", "Active one-on-one course product is required.");
            }
            if (resolveOneOnOneUnits(line) <= 0) {
                throw new BusinessException("ONE_ON_ONE_UNITS_REQUIRED", "One-on-one purchased hours or units are required.");
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
        BigDecimal pickupFee = defaultAmount(request.getPickupFeeAmount());
        BigDecimal dropoffFee = defaultAmount(request.getDropoffFeeAmount());
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
        item.setPriceAmount(line.priceAmount);
        item.setOriginalPriceAmount(line.originalPriceAmount);
        item.setManualPriceOverride(line.manualPriceOverride);
        item.setOneOnOneHours(line.oneOnOneHours);
        item.setOneOnOneUnits(line.oneOnOne ? resolveOneOnOneUnits(line) : line.oneOnOneUnits);
        item.setInitialLessonStartAt(line.initialLessonStartAt);
        item.setInitialLessonEndAt(line.initialLessonEndAt);
        item.setTeacherId(line.teacherId);
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
        coursePackage.setSubjectName(line.product.getSubjectName());
        coursePackage.setTeachingMode("ONE_ON_ONE");
        int totalUnits = resolveOneOnOneUnits(line);
        coursePackage.setTotalUnits(totalUnits);
        coursePackage.setDefaultLessonUnits(resolveDefaultLessonUnits(line.product, totalUnits));
        coursePackage.setConsumedUnits(0);
        coursePackage.setRemainingUnits(totalUnits);
        coursePackage.setOriginalAmount(line.priceAmount);
        coursePackage.setDiscountAmount(ZERO_AMOUNT);
        coursePackage.setReceivedAmount(allocatedPaid);
        coursePackage.setPackageStatus("ACTIVE");
        coursePackage.setStartDate(order.getPeriodStartDate());
        coursePackage.setEstimatedEndDate(order.getPeriodEndDate());
        coursePackage.setRemark(line.remark);
        coursePackageMapper.insert(coursePackage);

        if (line.teacherId != null) {
            PackageTeacherAssignmentEntity assignment = new PackageTeacherAssignmentEntity();
            assignment.setPackageId(coursePackage.getId());
            assignment.setTeacherId(line.teacherId);
            assignment.setIsPrimary(1);
            assignment.setEffectiveFrom(AppTime.nowLocalDateTime());
            assignment.setAssignmentStatus("ACTIVE");
            assignment.setChangeReason("REGISTRATION_INTAKE");
            packageTeacherAssignmentMapper.insert(assignment);
        }

        if (line.initialLessonStartAt != null) {
            LessonOccurrenceEntity occurrence = buildInitialOneOnOneOccurrence(coursePackage, student, line, order);
            ensureNoConflicts(List.of(occurrence), student.getId(), line.teacherId, null);
            lessonOccurrenceMapper.insert(occurrence);
        }

        return RegistrationIntakeResultVO.CourseArtifactVO.builder()
                .orderItemId(line.orderItemId)
                .lineNo(line.lineNo)
                .courseType(line.courseType)
                .packageId(coursePackage.getId())
                .packageNo(coursePackage.getPackageNo())
                .build();
    }

    private LessonOccurrenceEntity buildInitialOneOnOneOccurrence(CoursePackageEntity coursePackage,
                                                                   StudentEntity student,
                                                                   ResolvedCourseLine line,
                                                                   EnrollmentOrderEntity order) {
        InitialLessonTime lessonTime = resolveInitialOneOnOneLessonTime(order, line, coursePackage.getDefaultLessonUnits());
        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setLessonNo(generateOneOnOneLessonNo());
        occurrence.setPackageId(coursePackage.getId());
        occurrence.setStudentId(student.getId());
        occurrence.setOccurrenceType("REGULAR");
        occurrence.setPlannedTeacherId(line.teacherId);
        occurrence.setPlannedStartAt(lessonTime.startAt());
        occurrence.setPlannedEndAt(lessonTime.endAt());
        occurrence.setPlannedUnits(lessonTime.units());
        occurrence.setPlanningStatus("SCHEDULED");
        occurrence.setConflictChecked(1);
        return occurrence;
    }

    private InitialLessonTime resolveInitialOneOnOneLessonTime(EnrollmentOrderEntity order,
                                                               ResolvedCourseLine line,
                                                               Integer defaultLessonUnits) {
        if (line.initialLessonStartAt == null) {
            throw new BusinessException("ONE_ON_ONE_INITIAL_LESSON_REQUIRED",
                    "Please select the first one-on-one lesson start time.");
        }
        int units = defaultLessonUnits == null ? 2 : defaultLessonUnits;
        LocalDateTime endAt = line.initialLessonStartAt.plusMinutes(units * 30L);
        return new InitialLessonTime(line.initialLessonStartAt, endAt, units);
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
                billing.occurrenceWindowStart(), billing.occurrenceWindowEnd());

        ClassEnrollmentEntity enrollment = new ClassEnrollmentEntity();
        enrollment.setClassId(line.classId);
        enrollment.setStudentId(student.getId());
        enrollment.setEnrollmentOrderItemId(line.orderItemId);
        enrollment.setEnrollmentDate(billing.enrollmentDate());
        enrollment.setExpiryDate(billing.expiryDate());
        enrollment.setBillingType(line.classGroup.getBillingType());
        enrollment.setPurchasedQuantity(billing.purchasedQuantity());
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
                                                         LocalDate windowEnd) {
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
            LessonOccurrenceEntity conflict = firstOverlap(target, oneOnOneOccurrences);
            if (conflict != null) {
                throw new BusinessException("SCHEDULE_CONFLICT", "Student conflict detected for the selected class.",
                        List.of(buildConflictDetail("STUDENT", studentId, resolveStudentName(studentId), conflict)));
            }
            conflict = existingClassOccurrences.stream()
                    .filter(existing -> isEnrollmentEffectiveOn(
                            enrollmentsByClassId.get(existing.getClassGroupId()),
                            target.getPlannedStartAt().toLocalDate()))
                    .filter(existing -> overlaps(target, existing))
                    .findFirst()
                    .orElse(null);
            if (conflict != null) {
                throw new BusinessException("SCHEDULE_CONFLICT", "Student conflict detected for the selected class.",
                        List.of(buildConflictDetail("STUDENT", studentId, resolveStudentName(studentId), conflict)));
            }
        }
    }

    private boolean isEnrollmentEffectiveOn(List<ClassEnrollmentEntity> enrollments, LocalDate date) {
        return enrollments != null && enrollments.stream().anyMatch(enrollment ->
                (enrollment.getEnrollmentDate() == null || !date.isBefore(enrollment.getEnrollmentDate()))
                        && (enrollment.getExpiryDate() == null || !date.isAfter(enrollment.getExpiryDate())));
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
        payload.put("pickupFeeAmount", money.pickupFeeAmount());
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
        item.put("detail", line.detail);
        item.put("gradeBand", line.gradeBand);
        item.put("periodLabel", line.periodLabel);
        item.put("timeSlotLabel", line.timeSlotLabel);
        item.put("priceAmount", line.priceAmount);
        item.put("originalPriceAmount", line.originalPriceAmount);
        item.put("manualPriceOverride", line.manualPriceOverride);
        item.put("oneOnOneHours", line.oneOnOneHours);
        item.put("oneOnOneUnits", line.oneOnOne ? resolveOneOnOneUnits(line) : line.oneOnOneUnits);
        item.put("initialLessonStartAt", line.initialLessonStartAt);
        item.put("initialLessonEndAt", line.initialLessonEndAt);
        item.put("teacherId", line.teacherId);
        item.put("classId", line.classId);
        if (!line.oneOnOne) {
            item.put("groupBillingType", line.classGroup == null ? null : line.classGroup.getBillingType());
            item.put("groupTotalSessions", line.classGroup == null ? null : line.classGroup.getTotalSessions());
            item.put("groupStartMonth", line.classGroup == null ? null : formatRegistrationMonth(line.classGroup.getStartMonth()));
            item.put("groupEndMonth", line.classGroup == null ? null : formatRegistrationMonth(line.classGroup.getEndMonth()));
            item.put("sessions", line.sessions);
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

        String rawPassword = StringUtils.hasText(normalizePhone(student.getGuardianPhone()))
                ? normalizePhone(student.getGuardianPhone())
                : DEFAULT_ACCOUNT_PASSWORD;
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

    private List<RegistrationIntakeResultVO.AccountResultVO> ensureTeacherAccounts(List<ResolvedCourseLine> lines) {
        List<Long> teacherIds = lines.stream()
                .filter(line -> line.oneOnOne)
                .map(line -> line.teacherId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<RegistrationIntakeResultVO.AccountResultVO> result = new ArrayList<>();
        for (Long teacherId : teacherIds) {
            TeacherEntity teacher = requireTeacher(teacherId);
            UserAccountEntity existing = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccountEntity>()
                    .eq(UserAccountEntity::getUserType, "TEACHER")
                    .eq(UserAccountEntity::getTeacherId, teacherId)
                    .eq(UserAccountEntity::getIsDeleted, 0)
                    .last("LIMIT 1"));
            if (existing != null) {
                activateExistingAccount(existing);
                ensureRoleBinding(existing.getId(), "TEACHER");
                result.add(toAccountResult(existing, false, null));
                continue;
            }
            String rawPassword = StringUtils.hasText(teacher.getMobile())
                    ? teacher.getMobile().trim()
                    : DEFAULT_ACCOUNT_PASSWORD;
            UserAccountEntity account = new UserAccountEntity();
            account.setUsername(generateUniqueJinUsername(teacher.getTeacherName(), teacher.getTeacherNo()));
            account.setPasswordHash(passwordEncoder.encode(rawPassword));
            account.setUserType("TEACHER");
            account.setDisplayName(teacher.getTeacherName());
            account.setMobile(teacher.getMobile());
            account.setEmail(teacher.getEmail());
            account.setTeacherId(teacher.getId());
            account.setStatus("ACTIVE");
            userAccountMapper.insert(account);
            ensureRoleBinding(account.getId(), "TEACHER");
            result.add(toAccountResult(account, true, rawPassword));
        }
        return result;
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
     * Phone digits are used for the initial student password. Formatting characters
     * must never become part of a credential or break profile reuse matching.
     */
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
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setClassName(product.getProductName() + " · " + normalizedGradeBand);
        classGroup.setGradeBand(normalizedGradeBand);
        classGroup.setListPrice(ZERO_AMOUNT);
        classGroup.setProductId(product.getId());
        classGroup.setTeacherId(product.getDefaultTeacherId());
        classGroup.setClassroomId(product.getDefaultClassroomId());
        classGroup.setStatus("ACTIVE");
        classGroupMapper.insert(classGroup);
        return classGroup;
    }

    private ClassBillingResult computeClassBilling(ClassGroupEntity classGroup,
                                                    ResolvedCourseLine line,
                                                    LocalDate signupDate) {
        String billingType = classGroup.getBillingType();
        LocalDate today = signupDate == null ? LocalDate.now(AppTime.APP_ZONE_ID) : signupDate;
        List<CourseProductScheduleSlotEntity> slots = loadClassSlots(classGroup);
        if (slots.isEmpty()) {
            throw new BusinessException("GRADE_CLASS_NO_SLOTS", "Grade class has no schedule slots.");
        }

        if ("PER_SESSION".equals(billingType)) {
            Integer totalSessions = classGroup.getTotalSessions();
            if (totalSessions == null || totalSessions < 1 || classGroup.getStartDate() == null) {
                throw new BusinessException("GRADE_CLASS_BILLING_INCOMPLETE",
                        "Per-session grade class is missing start date or total sessions.");
            }
            if (line.sessions == null || line.sessions < 1) {
                throw new BusinessException("REGISTRATION_DURATION_MISSING",
                        "Enter purchased sessions on the registration form.");
            }
            if (line.sessions > totalSessions) {
                throw new BusinessException("SESSIONS_EXCEED_TOTAL", "Selected sessions exceed the grade class total sessions.");
            }
            LocalDate expiry = computeNthSessionDate(classGroup.getStartDate(), slots, line.sessions);
            LocalDate windowStart = classGroup.getStartDate().isAfter(today) ? classGroup.getStartDate() : today;
            LocalDate windowEnd = computeNthSessionDate(classGroup.getStartDate(), slots, totalSessions);
            return new ClassBillingResult(classGroup.getStartDate(), expiry, windowStart, windowEnd, line.sessions);
        }
        if ("MONTHLY".equals(billingType)) {
            if (classGroup.getStartMonth() == null || classGroup.getEndMonth() == null) {
                throw new BusinessException("GRADE_CLASS_BILLING_INCOMPLETE",
                        "Monthly grade class is missing start or end month.");
            }
            YearMonth productStart = YearMonth.from(classGroup.getStartMonth());
            YearMonth productEnd = YearMonth.from(classGroup.getEndMonth());
            if (!StringUtils.hasText(line.startMonth) || !StringUtils.hasText(line.endMonth)) {
                throw new BusinessException("REGISTRATION_DURATION_MISSING",
                        "Enter purchased months on the registration form.");
            }
            YearMonth selectedStart = parseRegistrationMonth(line.startMonth, "start month");
            YearMonth selectedEnd = parseRegistrationMonth(line.endMonth, "end month");
            if (selectedStart.isBefore(productStart) || selectedEnd.isAfter(productEnd)) {
                throw new BusinessException("REGISTRATION_DURATION_OUT_OF_RANGE",
                        "Selected months extend beyond the grade class month range.");
            }
            if (selectedEnd.isBefore(selectedStart)) {
                throw new BusinessException("REGISTRATION_DURATION_INVALID",
                        "Selected end month cannot be earlier than start month.");
            }
            LocalDate productStartDay = productStart.atDay(1);
            LocalDate windowStart = productStartDay.isAfter(today) ? productStartDay : today;
            LocalDate windowEnd = productEnd.atEndOfMonth();
            int months = (int) ChronoUnit.MONTHS.between(selectedStart, selectedEnd) + 1;
            return new ClassBillingResult(selectedStart.atDay(1), selectedEnd.atEndOfMonth(), windowStart, windowEnd, months);
        }
        throw new BusinessException("INVALID_BILLING_TYPE", "Unknown billing type for group product.");
    }

    private int ensureClassOccurrencesThrough(ClassGroupEntity classGroup,
                                               CourseProductEntity product,
                                               LocalDate windowStart,
                                               LocalDate horizonEnd) {
        List<CourseProductScheduleSlotEntity> slots = loadClassSlots(classGroup);
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
            for (CourseProductScheduleSlotEntity slot : slots) {
                if (!Objects.equals(slot.getWeekday(), weekday) || !slot.getEndTime().isAfter(slot.getStartTime())) {
                    continue;
                }
                LocalDateTime plannedStartAt = LocalDateTime.of(date, slot.getStartTime());
                if (existingStarts.contains(plannedStartAt)) {
                    continue;
                }
                long minutes = ChronoUnit.MINUTES.between(slot.getStartTime(), slot.getEndTime());
                LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
                occurrence.setLessonNo(generateClassLessonNo());
                occurrence.setClassGroupId(classGroup.getId());
                occurrence.setOccurrenceType("REGULAR");
                occurrence.setPlannedTeacherId(classGroup.getTeacherId());
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
            ensureNoConflicts(occurrences, null, classGroup.getTeacherId(), classGroup.getClassroomId());
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

    private List<CourseProductScheduleSlotEntity> loadProductSlots(Long productId) {
        return courseProductScheduleSlotMapper.selectList(new LambdaQueryWrapper<CourseProductScheduleSlotEntity>()
                .eq(CourseProductScheduleSlotEntity::getProductId, productId)
                .eq(CourseProductScheduleSlotEntity::getIsDeleted, 0));
    }

    private List<CourseProductScheduleSlotEntity> loadClassSlots(ClassGroupEntity classGroup) {
        return classGroupScheduleSlotMapper.selectList(
                        new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                                .eq(ClassGroupScheduleSlotEntity::getClassGroupId, classGroup.getId())
                                .eq(ClassGroupScheduleSlotEntity::getIsDeleted, 0))
                .stream()
                .map(slot -> {
                    CourseProductScheduleSlotEntity mapped = new CourseProductScheduleSlotEntity();
                    mapped.setWeekday(slot.getWeekday());
                    mapped.setStartTime(slot.getStartTime());
                    mapped.setEndTime(slot.getEndTime());
                    return mapped;
                })
                .toList();
    }

    private LocalDate computeNthSessionDate(LocalDate startDate, List<CourseProductScheduleSlotEntity> slots, int n) {
        Set<Integer> weekdays = slots.stream()
                .map(CourseProductScheduleSlotEntity::getWeekday)
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

    private String generateClassLessonNo() {
        return "CLS" + AppTime.nowLocalDateTime().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(1000, 10000));
    }

    private String generateOneOnOneLessonNo() {
        return "O2O" + AppTime.nowLocalDateTime().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(1000, 10000));
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

    private int resolveOneOnOneUnits(ResolvedCourseLine line) {
        if (line.oneOnOneHours != null && line.oneOnOneHours.compareTo(BigDecimal.ZERO) > 0) {
            return line.oneOnOneHours.multiply(BigDecimal.valueOf(2))
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValue();
        }
        if (line.oneOnOneUnits != null && line.oneOnOneUnits > 0) {
            return line.oneOnOneUnits;
        }
        if (line.product != null && line.product.getDefaultTotalUnits() != null) {
            return line.product.getDefaultTotalUnits() * 2;
        }
        return 0;
    }

    private int resolveDefaultLessonUnits(CourseProductEntity product, int totalUnits) {
        int defaultUnits = product.getDefaultLessonUnits() == null ? 2 : product.getDefaultLessonUnits();
        if (defaultUnits <= 0 || defaultUnits > totalUnits) {
            throw new BusinessException("INVALID_LESSON_UNITS", "Default lesson units must be greater than zero and not exceed total units.");
        }
        return defaultUnits;
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

    private record InitialLessonTime(
            LocalDateTime startAt,
            LocalDateTime endAt,
            int units
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
        private BigDecimal priceAmount;
        private BigDecimal originalPriceAmount;
        private Boolean manualPriceOverride;
        private BigDecimal oneOnOneHours;
        private Integer oneOnOneUnits;
        private LocalDateTime initialLessonStartAt;
        private LocalDateTime initialLessonEndAt;
        private Long teacherId;
        private String remark;
        private Integer sessions;
        private String startMonth;
        private String endMonth;
        private boolean oneOnOne;
        private CourseProductEntity product;
        private ClassGroupEntity classGroup;
    }
}
