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
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderParticipantEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentMethodEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupEntity;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupMemberEntity;
import com.classdeduction.system.modules.enrollment.entity.ReceiptRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderParticipantMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentMethodMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
import com.classdeduction.system.modules.enrollment.mapper.ReceiptRecordMapper;
import com.classdeduction.system.modules.enrollment.service.ClassBillingService;
import com.classdeduction.system.modules.enrollment.service.RegistrationIntakeService;
import com.classdeduction.system.modules.enrollment.service.allocation.PaymentAllocationService;
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
import java.util.function.Function;
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
    private final EnrollmentOrderParticipantMapper enrollmentOrderParticipantMapper;
    private final CoursePackageMapper coursePackageMapper;
    private final PrivateLessonGroupMapper privateLessonGroupMapper;
    private final PrivateLessonGroupMemberMapper privateLessonGroupMemberMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final PaymentMethodMapper paymentMethodMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final ReceiptRecordMapper receiptRecordMapper;
    private final PaymentAllocationService paymentAllocationService;
    private final ClassBillingService classBillingService;
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
        validateRenewalRequestShape(request);
        List<StudentResolution> participantResolutions = resolveParticipantStudents(request);
        StudentResolution studentResolution = participantResolutions.get(0);
        List<ResolvedCourseLine> lines = resolveCourseLines(request.getItems());
        validatePrivateLessonParticipants(request, participantResolutions, lines);
        applyParticipantPricing(lines, participantResolutions.size());
        validateRenewalLines(request, lines);
        MoneySnapshot money = calculateMoney(request, lines, participantResolutions.size());
        prepareCourseBilling(request, lines, money, participantResolutions.size());

        EnrollmentOrderEntity order = createOrder(request, studentResolution.student(), lines, money);
        createOrderParticipants(order, participantResolutions, lines, request, money);

        List<RegistrationIntakeResultVO.CourseArtifactVO> artifacts = new ArrayList<>();
        Long privateLessonGroupId = null;
        List<RegistrationIntakeResultVO.ParticipantResultVO> participantResults = new ArrayList<>();
        BigDecimal unallocatedPaid = money.paidAmount();
        for (ResolvedCourseLine line : lines) {
            EnrollmentOrderItemEntity orderItem = createOrderItem(order.getId(), line);
            line.orderItemId = orderItem.getId();

            BigDecimal allocatedPaid = line.initialPaidAmount == null
                    ? unallocatedPaid.min(line.amountDue)
                    : line.initialPaidAmount;
            unallocatedPaid = unallocatedPaid.subtract(allocatedPaid);
            if (line.oneOnOne && participantResolutions.size() > 1) {
                PrivateLessonRegistrationResult groupResult = isSharedPrivateLessonRenewal(request)
                        ? renewPrivateLessonGroup(order, orderItem, line, participantResolutions, allocatedPaid,
                        findPrivateLessonGroupIdByPackageId(request.getRenewPackageId()))
                        : createPrivateLessonGroup(order, orderItem, line, participantResolutions, allocatedPaid);
                privateLessonGroupId = groupResult.groupId();
                artifacts.add(groupResult.primaryArtifact());
                participantResults.addAll(groupResult.participants());
            } else {
                RegistrationIntakeResultVO.CourseArtifactVO artifact = line.oneOnOne
                        ? createOneOnOneArtifact(order, studentResolution.student(), line, allocatedPaid, request.getRenewPackageId())
                        : createGroupClassArtifact(order, studentResolution.student(), line, request.getRenewClassEnrollmentId());
                artifacts.add(artifact);
                if (line.oneOnOne) {
                    participantResults.add(toParticipantResult(
                            studentResolution, artifact.packageId(), artifact.packageNo(), line.priceAmount,
                            ensureStudentAccount(studentResolution.student())));
                }
            }
        }

        List<RegistrationIntakeResultVO.PaymentResultVO> payments = createPayments(
                order, request, money, participantResolutions.size(), lines);
        // 课程级付款分摊：优先保留请求中的逐课金额，旧请求才回退到历史分配规则。
        if (participantResolutions.size() <= 1 || request.getParticipantBillings() == null
                || request.getParticipantBillings().isEmpty()) {
            paymentAllocationService.replayAllocations(order);
        }
        RegistrationIntakeResultVO.AccountResultVO studentAccount = participantResults.isEmpty()
                ? null : participantResults.get(0).studentAccount();

        ReceiptResult receipt = createReceiptIfNeeded(order, studentResolution.student(), participantResolutions,
                participantResults, request, lines, money, payments, artifacts);

        return RegistrationIntakeResultVO.builder()
                .enrollmentOrderId(order.getId())
                .enrollmentNo(order.getEnrollmentNo())
                .studentId(studentResolution.student().getId())
                .studentNo(studentResolution.student().getStudentNo())
                .studentName(studentResolution.student().getStudentName())
                .studentReused(studentResolution.reused())
                .studentAccount(studentAccount)
                .privateLessonGroupId(privateLessonGroupId)
                .participants(participantResults)
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
        StudentEntity renewalStudent = resolveRenewalStudent(request);
        if (renewalStudent != null) {
            applyStudentPayload(renewalStudent, payload, request.getCampusId());
            studentMapper.updateById(renewalStudent);
            return new StudentResolution(renewalStudent, true);
        }
        return createOrReuseStudent(payload, request.getCampusId());
    }

    private StudentResolution createOrReuseStudent(RegistrationIntakeRequest.StudentPayload payload, Long campusId) {
        if (payload.getStudentId() != null) {
            StudentEntity existing = requireStudent(payload.getStudentId());
            return new StudentResolution(existing, true);
        }
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

        applyStudentPayload(student, payload, campusId);
        if (reused) {
            studentMapper.updateById(student);
        } else {
            studentMapper.insert(student);
        }
        return new StudentResolution(student, reused);
    }

    private List<StudentResolution> resolveParticipantStudents(RegistrationIntakeRequest request) {
        List<RegistrationIntakeRequest.StudentPayload> payloads = request.getParticipants();
        if (payloads == null || payloads.isEmpty()) {
            StudentResolution primary = createOrReuseStudent(request);
            return appendSharedRenewalParticipants(request, primary);
        }
        if (payloads.size() > 3) {
            throw new BusinessException("PRIVATE_LESSON_PARTICIPANT_LIMIT", "A private lesson can have at most 3 students.");
        }
        List<StudentResolution> result = new ArrayList<>(payloads.size());
        for (int index = 0; index < payloads.size(); index++) {
            RegistrationIntakeRequest.StudentPayload payload = payloads.get(index);
            if (payload == null) {
                throw new BusinessException("PRIVATE_LESSON_PARTICIPANT_REQUIRED", "Every private lesson participant is required.");
            }
            result.add(index == 0 ? createOrReuseStudent(request) : createOrReuseStudent(payload, request.getCampusId()));
        }
        return appendSharedRenewalParticipants(request, result);
    }

    private List<StudentResolution> appendSharedRenewalParticipants(RegistrationIntakeRequest request,
                                                                      StudentResolution primary) {
        return appendSharedRenewalParticipants(request, new ArrayList<>(List.of(primary)));
    }

    private List<StudentResolution> appendSharedRenewalParticipants(RegistrationIntakeRequest request,
                                                                      List<StudentResolution> resolved) {
        Long groupId = request.getRenewPackageId() == null
                ? null
                : findPrivateLessonGroupIdByPackageId(request.getRenewPackageId());
        if (groupId == null) {
            return resolved;
        }
        List<PrivateLessonGroupMemberEntity> members = activePrivateLessonGroupMembers(groupId);
        if (members.size() <= 1) {
            return resolved;
        }
        Set<Long> resolvedStudentIds = resolved.stream()
                .map(item -> item.student().getId())
                .collect(Collectors.toSet());
        for (PrivateLessonGroupMemberEntity member : members) {
            if (resolvedStudentIds.contains(member.getStudentId())) {
                continue;
            }
            resolved.add(new StudentResolution(requireStudent(member.getStudentId()), true));
        }
        resolved.sort(Comparator.comparingInt(item -> members.stream()
                .filter(member -> Objects.equals(member.getStudentId(), item.student().getId()))
                .map(PrivateLessonGroupMemberEntity::getParticipantIndex)
                .findFirst().orElse(Integer.MAX_VALUE)));
        return resolved;
    }

    private void validatePrivateLessonParticipants(RegistrationIntakeRequest request,
                                                   List<StudentResolution> participants,
                                                   List<ResolvedCourseLine> lines) {
        if (participants.size() <= 1) {
            return;
        }
        if (request.getRenewClassEnrollmentId() != null) {
            throw new BusinessException("PRIVATE_LESSON_RENEWAL_UNSUPPORTED",
                    "Renewal of a shared private lesson must be handled from its private lesson group.");
        }
        if (request.getRenewPackageId() != null && !isSharedPrivateLessonRenewal(request)) {
            throw new BusinessException("PRIVATE_LESSON_RENEWAL_UNSUPPORTED",
                    "The selected renewal package is not a member of a shared private lesson.");
        }
        if (lines.size() != 1 || !lines.get(0).oneOnOne) {
            throw new BusinessException("PRIVATE_LESSON_SINGLE_LINE_REQUIRED",
                    "A shared private lesson registration must contain exactly one one-on-one course line.");
        }
        Set<Long> studentIds = participants.stream().map(item -> item.student().getId()).collect(Collectors.toSet());
        if (studentIds.size() != participants.size()) {
            throw new BusinessException("PRIVATE_LESSON_DUPLICATE_STUDENT", "The same student cannot be added twice to one private lesson.");
        }
    }

    private void applyParticipantPricing(List<ResolvedCourseLine> lines, int participantCount) {
        if (participantCount <= 1) {
            return;
        }
        BigDecimal multiplier = BigDecimal.ONE.add(
                BigDecimal.valueOf(participantCount - 1).multiply(new BigDecimal("0.50")));
        for (ResolvedCourseLine line : lines) {
            if (!line.oneOnOne) {
                throw new BusinessException("PRIVATE_LESSON_MODE_REQUIRED", "Only one-on-one courses support multiple participants.");
            }
            line.basePriceAmount = line.priceAmount;
            line.priceAmount = line.priceAmount.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
            line.originalPriceAmount = defaultAmount(line.originalPriceAmount)
                    .multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        }
    }

    /**
     * 共享私教的订单总价由上游先计算，这里只负责平均分摊到每个学生；最后一位吸收分币误差。
     */
    private BigDecimal splitSharedTuition(BigDecimal totalTuition, int participantCount, int participantIndex) {
        if (participantCount <= 1) {
            return defaultAmount(totalTuition);
        }
        BigDecimal share = defaultAmount(totalTuition).divide(
                BigDecimal.valueOf(participantCount), 2, RoundingMode.DOWN);
        if (participantIndex == participantCount - 1) {
            return defaultAmount(totalTuition).subtract(
                            share.multiply(BigDecimal.valueOf(participantCount - 1)))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return share;
    }

    private void validateRenewalRequestShape(RegistrationIntakeRequest request) {
        if (request.getRenewPackageId() != null && request.getRenewClassEnrollmentId() != null) {
            throw new BusinessException("RENEWAL_TARGET_CONFLICT",
                    "Renew one existing package or one existing class enrollment at a time.");
        }
        if ((request.getRenewPackageId() != null || request.getRenewClassEnrollmentId() != null)
                && (request.getItems() == null || request.getItems().size() != 1)) {
            throw new BusinessException("RENEWAL_SINGLE_LINE_REQUIRED",
                    "Renewal registration must contain exactly one course line.");
        }
    }

    private void validateRenewalLines(RegistrationIntakeRequest request, List<ResolvedCourseLine> lines) {
        if (request.getRenewPackageId() != null && lines.stream().anyMatch(line -> !line.oneOnOne)) {
            throw new BusinessException("RENEWAL_PACKAGE_MODE_MISMATCH",
                    "One-on-one package renewal requires a one-on-one course line.");
        }
        if (request.getRenewClassEnrollmentId() != null && lines.stream().anyMatch(line -> line.oneOnOne)) {
            throw new BusinessException("RENEWAL_CLASS_MODE_MISMATCH",
                    "Group class renewal requires a group class course line.");
        }
    }

    private StudentEntity resolveRenewalStudent(RegistrationIntakeRequest request) {
        if (request.getRenewPackageId() != null) {
            CoursePackageEntity coursePackage = requireRenewableOneOnOnePackage(request.getRenewPackageId());
            return requireStudent(coursePackage.getStudentId());
        }
        if (request.getRenewClassEnrollmentId() != null) {
            ClassEnrollmentEntity enrollment = requireRenewableClassEnrollment(request.getRenewClassEnrollmentId());
            return requireStudent(enrollment.getStudentId());
        }
        return null;
    }

    private StudentEntity requireStudent(Long studentId) {
        StudentEntity student = studentId == null ? null : studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException("STUDENT_NOT_FOUND", "Student not found.");
        }
        return student;
    }

    private void applyStudentPayload(StudentEntity student,
                                     RegistrationIntakeRequest.StudentPayload payload,
                                     Long campusId) {
        student.setStudentName(payload.getStudentName().trim());
        student.setEnglishName(payload.getEnglishName());
        student.setGender(payload.getGender());
        student.setBirthDate(payload.getBirthDate());
        student.setGrade(payload.getGrade());
        student.setStudentClass(payload.getStudentClass());
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
        if (StringUtils.hasText(line.gradeBand)
                && StringUtils.hasText(classGroup.getGradeBand())
                && !normalizedGrade(line.gradeBand).equals(normalizedGrade(classGroup.getGradeBand()))) {
            throw new BusinessException("CLASS_GRADE_MISMATCH",
                    "Selected class does not belong to the selected billing grade.");
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

    private MoneySnapshot calculateMoney(RegistrationIntakeRequest request,
                                         List<ResolvedCourseLine> lines,
                                         int participantCount) {
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
        BigDecimal participantDiscount = participantCount > 1 && request.getParticipantBillings() != null
                ? request.getParticipantBillings().stream()
                .filter(Objects::nonNull)
                .map(item -> defaultAmount(firstNonNull(item.getDiscountAmount(), sumFeeLines(item.getDiscounts()))))
                .reduce(ZERO_AMOUNT, BigDecimal::add)
                : ZERO_AMOUNT;
        BigDecimal discount = participantCount > 1 && request.getParticipantBillings() != null
                ? participantDiscount
                : defaultAmount(firstNonNull(request.getDiscountAmount(), sumFeeLines(request.getDiscounts())));
        BigDecimal originalAmount = tuitionSubtotal.add(pickupFee).add(dropoffFee).add(extraFee).setScale(2, RoundingMode.HALF_UP);
        if (discount.compareTo(originalAmount) > 0) {
            throw new BusinessException("DISCOUNT_EXCEEDS_AMOUNT", "Discount cannot exceed original amount.");
        }
        BigDecimal paid = participantCount > 1 && request.getParticipantBillings() != null
                ? request.getParticipantBillings().stream()
                .filter(Objects::nonNull)
                .flatMap(item -> item.getPayments() == null ? java.util.stream.Stream.empty() : item.getPayments().stream())
                .filter(Objects::nonNull)
                .map(RegistrationIntakeRequest.PaymentPayload::getAmount)
                .map(this::defaultAmount)
                .reduce(ZERO_AMOUNT, BigDecimal::add)
                : request.getPayments() == null ? ZERO_AMOUNT : request.getPayments().stream()
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

    /** 把课程优惠、应收和首次实付固定到报名明细，供后续付款与汇总复用。 */
    private void prepareCourseBilling(RegistrationIntakeRequest request,
                                      List<ResolvedCourseLine> lines,
                                      MoneySnapshot money,
                                      int participantCount) {
        assignCourseDiscounts(request, lines, money.discountAmount());
        List<BigDecimal> feeShares = allocateByCoursePrice(
                money.pickupFeeAmount().add(money.dropoffFeeAmount()).add(money.extraFeeAmount()), lines);
        BigDecimal assignedDue = ZERO_AMOUNT;
        for (int index = 0; index < lines.size(); index++) {
            ResolvedCourseLine line = lines.get(index);
            BigDecimal due = line.priceAmount.add(feeShares.get(index)).subtract(line.discountAmount);
            if (index == lines.size() - 1) {
                due = due.add(money.amountDue().subtract(assignedDue.add(due)));
            }
            if (due.compareTo(ZERO_AMOUNT) < 0) {
                throw new BusinessException("COURSE_DISCOUNT_EXCEEDS_AMOUNT",
                        "A course discount cannot exceed that course's price and allocated fees.");
            }
            line.amountDue = due.setScale(2, RoundingMode.HALF_UP);
            assignedDue = assignedDue.add(line.amountDue);
        }

        assignInitialCoursePayments(request, lines, money, participantCount);
    }

    private void assignCourseDiscounts(RegistrationIntakeRequest request,
                                       List<ResolvedCourseLine> lines,
                                       BigDecimal totalDiscount) {
        Map<Integer, ResolvedCourseLine> linesByNo = lines.stream().collect(Collectors.toMap(
                line -> line.lineNo,
                line -> line,
                (left, right) -> {
                    throw new BusinessException("COURSE_LINE_NUMBER_DUPLICATE",
                            "Course line numbers must be unique when assigning discounts.");
                },
                LinkedHashMap::new));
        List<RegistrationIntakeRequest.FeeLinePayload> discounts = request.getDiscounts() == null
                ? Collections.emptyList() : request.getDiscounts();
        boolean hasCourseAssignments = discounts.stream()
                .filter(Objects::nonNull)
                .anyMatch(discount -> discount.getOrderItemLineNo() != null);
        Map<Integer, BigDecimal> discountByLine = new LinkedHashMap<>();
        if (hasCourseAssignments) {
            for (RegistrationIntakeRequest.FeeLinePayload discount : discounts) {
                if (discount == null) {
                    throw new BusinessException("COURSE_DISCOUNT_INVALID", "Discount detail rows cannot be empty.");
                }
                BigDecimal amount = defaultAmount(discount.getAmount());
                Integer lineNo = discount.getOrderItemLineNo();
                if (amount.compareTo(ZERO_AMOUNT) > 0 && lineNo == null) {
                    throw new BusinessException("COURSE_DISCOUNT_ASSIGNMENT_REQUIRED",
                            "Assign every discount to a course.");
                }
                if (lineNo != null && !linesByNo.containsKey(lineNo)) {
                    throw new BusinessException("COURSE_DISCOUNT_LINE_NOT_FOUND",
                            "A discount references a course line that is not in this registration.");
                }
                if (lineNo != null) {
                    discountByLine.merge(lineNo, amount, BigDecimal::add);
                }
            }
        } else if (lines.stream().anyMatch(line -> line.discountAmount != null)) {
            for (ResolvedCourseLine line : lines) {
                discountByLine.put(line.lineNo, defaultAmount(line.discountAmount));
            }
        } else {
            List<BigDecimal> legacyShares = allocateByCoursePrice(totalDiscount, lines);
            for (int index = 0; index < lines.size(); index++) {
                discountByLine.put(lines.get(index).lineNo, legacyShares.get(index));
            }
        }

        BigDecimal assignedDiscount = ZERO_AMOUNT;
        for (ResolvedCourseLine line : lines) {
            line.discountAmount = defaultAmount(discountByLine.get(line.lineNo));
            assignedDiscount = assignedDiscount.add(line.discountAmount);
        }
        if (assignedDiscount.compareTo(totalDiscount) != 0) {
            throw new BusinessException("COURSE_DISCOUNT_TOTAL_MISMATCH",
                    "Course discounts must add up to the registration discount total.");
        }
    }

    private void assignInitialCoursePayments(RegistrationIntakeRequest request,
                                             List<ResolvedCourseLine> lines,
                                             MoneySnapshot money,
                                             int participantCount) {
        if (participantCount > 1 && request.getParticipantBillings() != null
                && !request.getParticipantBillings().isEmpty()) {
            if (lines.size() == 1) {
                lines.get(0).initialPaidAmount = money.paidAmount();
            }
            return;
        }
        Map<Integer, BigDecimal> paidByLine = new LinkedHashMap<>();
        boolean hasExplicitAllocations = request.getPayments() != null && request.getPayments().stream()
                .filter(Objects::nonNull)
                .anyMatch(payment -> payment.getAllocations() != null && !payment.getAllocations().isEmpty());
        if (hasExplicitAllocations) {
            Map<Integer, ResolvedCourseLine> linesByNo = lines.stream().collect(Collectors.toMap(
                    line -> line.lineNo, line -> line));
            BigDecimal totalAllocated = ZERO_AMOUNT;
            for (RegistrationIntakeRequest.PaymentPayload payment : request.getPayments()) {
                List<RegistrationIntakeRequest.CoursePaymentAllocationPayload> allocations = payment.getAllocations();
                BigDecimal paymentAllocated = allocations == null ? ZERO_AMOUNT : allocations.stream()
                        .filter(Objects::nonNull)
                        .map(RegistrationIntakeRequest.CoursePaymentAllocationPayload::getAmount)
                        .map(this::defaultAmount)
                        .reduce(ZERO_AMOUNT, BigDecimal::add);
                if (paymentAllocated.compareTo(defaultAmount(payment.getAmount())) != 0) {
                    throw new BusinessException("COURSE_PAYMENT_ALLOCATION_TOTAL_MISMATCH",
                            "Course amounts must add up to each initial payment.");
                }
                if (allocations != null) {
                    for (RegistrationIntakeRequest.CoursePaymentAllocationPayload allocation : allocations) {
                        if (allocation == null) {
                            throw new BusinessException("COURSE_PAYMENT_ALLOCATION_INVALID",
                                    "Each initial payment allocation must reference a course and be greater than zero.");
                        }
                        ResolvedCourseLine line = linesByNo.get(allocation.getOrderItemLineNo());
                        if (line == null || allocation.getAmount() == null
                                || allocation.getAmount().compareTo(ZERO_AMOUNT) <= 0) {
                            throw new BusinessException("COURSE_PAYMENT_ALLOCATION_INVALID",
                                    "Each initial payment allocation must reference a course and be greater than zero.");
                        }
                        paidByLine.merge(line.lineNo, allocation.getAmount(), BigDecimal::add);
                        totalAllocated = totalAllocated.add(allocation.getAmount());
                    }
                }
            }
            if (totalAllocated.compareTo(money.paidAmount()) != 0) {
                throw new BusinessException("COURSE_PAYMENT_TOTAL_MISMATCH",
                        "Course payment allocations must add up to the amount received at registration.");
            }
            for (ResolvedCourseLine line : lines) {
                line.initialPaidAmount = defaultAmount(paidByLine.get(line.lineNo));
                if (line.initialPaidAmount.compareTo(line.amountDue) > 0) {
                    throw new BusinessException("COURSE_PAYMENT_EXCEEDS_AMOUNT_DUE",
                            "A course's initial payment cannot exceed its amount due.");
                }
            }
            return;
        }

        BigDecimal unallocated = money.paidAmount();
        for (ResolvedCourseLine line : lines) {
            line.initialPaidAmount = unallocated.min(line.amountDue);
            unallocated = unallocated.subtract(line.initialPaidAmount);
        }
    }

    private List<BigDecimal> allocateByCoursePrice(BigDecimal total, List<ResolvedCourseLine> lines) {
        if (lines.isEmpty()) {
            return List.of();
        }
        BigDecimal totalAmount = defaultAmount(total);
        BigDecimal weightSum = lines.stream().map(line -> defaultAmount(line.priceAmount))
                .reduce(ZERO_AMOUNT, BigDecimal::add);
        BigDecimal assigned = ZERO_AMOUNT;
        List<BigDecimal> amounts = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            BigDecimal amount = index == lines.size() - 1
                    ? totalAmount.subtract(assigned)
                    : weightSum.compareTo(ZERO_AMOUNT) <= 0
                    ? totalAmount.divide(BigDecimal.valueOf(lines.size()), 2, RoundingMode.DOWN)
                    : totalAmount.multiply(lines.get(index).priceAmount)
                    .divide(weightSum, 2, RoundingMode.DOWN);
            amounts.add(amount);
            assigned = assigned.add(amount);
        }
        return amounts;
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
        order.setStudentClassSnapshot(cleanText(request.getStudent().getStudentClass()));
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

    private void createOrderParticipants(EnrollmentOrderEntity order,
                                         List<StudentResolution> participants,
                                         List<ResolvedCourseLine> lines,
                                         RegistrationIntakeRequest request,
                                         MoneySnapshot money) {
        BigDecimal sharedTuition = lines.size() == 1 && lines.get(0).oneOnOne
                ? defaultAmount(lines.get(0).priceAmount)
                : money.tuitionSubtotal();
        for (int index = 0; index < participants.size(); index++) {
            StudentEntity student = participants.get(index).student();
            EnrollmentOrderParticipantEntity participant = new EnrollmentOrderParticipantEntity();
            participant.setEnrollmentOrderId(order.getId());
            participant.setStudentId(student.getId());
            participant.setParticipantIndex(index + 1);
            participant.setStudentNameSnapshot(student.getStudentName());
            participant.setStudentNoSnapshot(student.getStudentNo());
            participant.setGradeSnapshot(student.getGrade());
            participant.setSchoolSnapshot(student.getSchool());
            participant.setStudentClassSnapshot(student.getStudentClass());
            participant.setGuardianNameSnapshot(student.getGuardianName());
            participant.setGuardianPhoneSnapshot(student.getGuardianPhone());
            participant.setGuardianPhone2Snapshot(student.getGuardianPhone2());
            participant.setGuardianEmailSnapshot(student.getGuardianEmail());
            participant.setPickupRequiredSnapshot(student.getPickupRequired());
            participant.setPickupAddressSnapshot(student.getPickupAddress());
            participant.setDropoffRequiredSnapshot(student.getDropoffRequired());
            participant.setDropoffAddressSnapshot(student.getDropoffAddress());
            BigDecimal grossAmount = participants.size() > 1
                    ? splitSharedTuition(sharedTuition, participants.size(), index)
                    : sharedTuition;
            BigDecimal discountAmount = participantDiscountAmount(request, index + 1,
                    participants.size() > 1 ? null : money.discountAmount());
            BigDecimal sharedFees = money.pickupFeeAmount().add(money.dropoffFeeAmount()).add(money.extraFeeAmount());
            BigDecimal participantFees = index == 0 ? sharedFees : ZERO_AMOUNT;
            BigDecimal amountDue = grossAmount.add(participantFees).subtract(discountAmount)
                    .max(ZERO_AMOUNT).setScale(2, RoundingMode.HALF_UP);
            BigDecimal paidAmount = participantPaidAmount(request, index + 1,
                    participants.size() > 1 ? null : money.paidAmount());
            participant.setGrossAmount(grossAmount);
            participant.setDiscountAmount(discountAmount);
            participant.setDiscountDetail(jsonHelper.toJson(participantDiscounts(request, index + 1)));
            participant.setAmountDue(amountDue);
            participant.setPaidAmount(paidAmount);
            participant.setBalanceAmount(amountDue.subtract(paidAmount).setScale(2, RoundingMode.HALF_UP));
            enrollmentOrderParticipantMapper.insert(participant);
        }
    }

    private RegistrationIntakeRequest.ParticipantBillingPayload participantBilling(
            RegistrationIntakeRequest request, int participantIndex) {
        if (request.getParticipantBillings() == null) {
            return null;
        }
        return request.getParticipantBillings().stream()
                .filter(item -> item != null && Objects.equals(item.getParticipantIndex(), participantIndex))
                .findFirst()
                .orElse(null);
    }

    private BigDecimal participantDiscountAmount(RegistrationIntakeRequest request,
                                                 int participantIndex,
                                                 BigDecimal fallback) {
        RegistrationIntakeRequest.ParticipantBillingPayload billing = participantBilling(request, participantIndex);
        if (billing == null) {
            return defaultAmount(fallback);
        }
        return defaultAmount(firstNonNull(billing.getDiscountAmount(), sumFeeLines(billing.getDiscounts())));
    }

    private List<RegistrationIntakeRequest.FeeLinePayload> participantDiscounts(
            RegistrationIntakeRequest request, int participantIndex) {
        RegistrationIntakeRequest.ParticipantBillingPayload billing = participantBilling(request, participantIndex);
        return billing == null || billing.getDiscounts() == null
                ? Collections.emptyList()
                : billing.getDiscounts();
    }

    private BigDecimal participantPaidAmount(RegistrationIntakeRequest request,
                                             int participantIndex,
                                             BigDecimal fallback) {
        RegistrationIntakeRequest.ParticipantBillingPayload billing = participantBilling(request, participantIndex);
        if (billing == null || billing.getPayments() == null) {
            return defaultAmount(fallback);
        }
        return billing.getPayments().stream()
                .filter(Objects::nonNull)
                .map(RegistrationIntakeRequest.PaymentPayload::getAmount)
                .map(this::defaultAmount)
                .reduce(ZERO_AMOUNT, BigDecimal::add);
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
        item.setDiscountAmount(line.discountAmount);
        item.setAmountDue(line.amountDue);
        item.setPaidAmount(defaultAmount(line.initialPaidAmount));
        item.setBalanceAmount(line.amountDue.subtract(defaultAmount(line.initialPaidAmount)));
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
                                                                                BigDecimal allocatedPaid,
                                                                                Long renewPackageId) {
        return createOneOnOneArtifact(order, student, line, allocatedPaid, renewPackageId, line.priceAmount);
    }

    private RegistrationIntakeResultVO.CourseArtifactVO createOneOnOneArtifact(EnrollmentOrderEntity order,
                                                                                StudentEntity student,
                                                                                ResolvedCourseLine line,
                                                                                BigDecimal allocatedPaid,
                                                                                Long renewPackageId,
                                                                                BigDecimal packagePriceAmount) {
        return createOneOnOneArtifact(order, student, line, allocatedPaid, renewPackageId,
                packagePriceAmount, true);
    }

    private RegistrationIntakeResultVO.CourseArtifactVO createOneOnOneArtifact(EnrollmentOrderEntity order,
                                                                                StudentEntity student,
                                                                                ResolvedCourseLine line,
                                                                                BigDecimal allocatedPaid,
                                                                                Long renewPackageId,
                                                                                BigDecimal packagePriceAmount,
                                                                                boolean allowExistingPackage) {
        CoursePackageEntity renewalTarget = renewPackageId == null ? null : requireRenewableOneOnOnePackage(renewPackageId);
        if (renewalTarget != null && !Objects.equals(renewalTarget.getStudentId(), student.getId())) {
            throw new BusinessException("RENEWAL_STUDENT_MISMATCH",
                    "Renewal package does not belong to the selected student.");
        }

        CoursePackageEntity coursePackage = renewalTarget != null
                ? renewalTarget
                : allowExistingPackage ? resolveExistingOneOnOnePackageForLine(student.getId(), line, null) : null;
        if (coursePackage != null) {
            int addedUnits = resolveOneOnOneUnits(line);
            coursePackage.setEnrollmentOrderId(order.getId());
            coursePackage.setEnrollmentOrderItemId(line.orderItemId);
            coursePackage.setTotalUnits(safeUnits(coursePackage.getTotalUnits()) + addedUnits);
            coursePackage.setRemainingUnits(safeUnits(coursePackage.getRemainingUnits()) + addedUnits);
            coursePackage.setOriginalAmount(defaultAmount(coursePackage.getOriginalAmount()).add(packagePriceAmount));
            coursePackage.setReceivedAmount(defaultAmount(coursePackage.getReceivedAmount()).add(allocatedPaid));
            coursePackage.setPackageStatus("ACTIVE");
            coursePackage.setCompletedAt(null);
            if (order.getPeriodEndDate() != null && (coursePackage.getEstimatedEndDate() == null
                    || order.getPeriodEndDate().isAfter(coursePackage.getEstimatedEndDate()))) {
                coursePackage.setEstimatedEndDate(order.getPeriodEndDate());
            }
            if (StringUtils.hasText(line.remark)) {
                coursePackage.setRemark(line.remark);
            }
            coursePackageMapper.updateById(coursePackage);
            return RegistrationIntakeResultVO.CourseArtifactVO.builder()
                    .orderItemId(line.orderItemId)
                    .lineNo(line.lineNo)
                    .courseType(line.courseType)
                    .packageId(coursePackage.getId())
                    .packageNo(coursePackage.getPackageNo())
                    .build();
        }

        coursePackage = new CoursePackageEntity();
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
        coursePackage.setOriginalAmount(packagePriceAmount);
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

    /**
     * 创建共享私教课组，并为每个参与人生成独立课包。
     * 共享课组只保存关系和价格快照，后续消课仍分别更新成员课包余额。
     */
    private PrivateLessonRegistrationResult createPrivateLessonGroup(EnrollmentOrderEntity order,
                                                                       EnrollmentOrderItemEntity orderItem,
                                                                       ResolvedCourseLine line,
                                                                       List<StudentResolution> participants,
                                                                       BigDecimal allocatedPaid) {
        PrivateLessonGroupEntity group = new PrivateLessonGroupEntity();
        group.setGroupNo("PLG" + BusinessNoGenerator.generateLessonOccurrenceNo().substring(2));
        group.setEnrollmentOrderId(order.getId());
        group.setEnrollmentOrderItemId(orderItem.getId());
        group.setCourseProductId(line.courseProductId);
        group.setGradeBand(line.gradeBand);
        group.setParticipantCount(participants.size());
        group.setSecondParticipantMultiplier(new BigDecimal("0.5000"));
        group.setThirdParticipantMultiplier(new BigDecimal("0.5000"));
        group.setGroupStatus("ACTIVE");
        privateLessonGroupMapper.insert(group);

        List<RegistrationIntakeResultVO.ParticipantResultVO> participantResults = new ArrayList<>();
        RegistrationIntakeResultVO.CourseArtifactVO primaryArtifact = null;
        for (int index = 0; index < participants.size(); index++) {
            StudentResolution participant = participants.get(index);
            BigDecimal memberPrice = splitSharedTuition(line.priceAmount, participants.size(), index);
            BigDecimal multiplier = defaultAmount(line.basePriceAmount).compareTo(ZERO_AMOUNT) == 0
                    ? ZERO_AMOUNT
                    : memberPrice.divide(line.basePriceAmount, 8, RoundingMode.HALF_UP);
            BigDecimal memberPaid = index == 0 ? allocatedPaid : ZERO_AMOUNT;
            RegistrationIntakeResultVO.CourseArtifactVO artifact = createOneOnOneArtifact(
                    order, participant.student(), line, memberPaid, null, memberPrice, false);
            if (index == 0) {
                primaryArtifact = artifact;
            }

            PrivateLessonGroupMemberEntity member = new PrivateLessonGroupMemberEntity();
            member.setPrivateLessonGroupId(group.getId());
            member.setStudentId(participant.student().getId());
            member.setPackageId(artifact.packageId());
            member.setParticipantIndex(index + 1);
            member.setPriceMultiplier(multiplier);
            member.setPriceAmount(memberPrice);
            member.setMemberStatus("ACTIVE");
            privateLessonGroupMemberMapper.insert(member);

            participantResults.add(toParticipantResult(
                    participant, artifact.packageId(), artifact.packageNo(), memberPrice,
                    ensureStudentAccount(participant.student())));
        }
        return new PrivateLessonRegistrationResult(group.getId(), primaryArtifact, participantResults);
    }

    /**
     * 共享私教续费只新增一张订单，课组和成员课包都复用原记录。
     * 这样原有排课规则/未来课次继续指向同一个 group，不会因为续费产生第二套排课。
     */
    private PrivateLessonRegistrationResult renewPrivateLessonGroup(EnrollmentOrderEntity order,
                                                                      EnrollmentOrderItemEntity orderItem,
                                                                      ResolvedCourseLine line,
                                                                      List<StudentResolution> participants,
                                                                      BigDecimal allocatedPaid,
                                                                      Long groupId) {
        PrivateLessonGroupEntity group = groupId == null ? null : privateLessonGroupMapper.selectById(groupId);
        if (group == null || Integer.valueOf(1).equals(group.getIsDeleted())
                || !"ACTIVE".equals(group.getGroupStatus())) {
            throw new BusinessException("PRIVATE_LESSON_GROUP_NOT_FOUND", "Shared private lesson group not found or inactive.");
        }
        if (!Objects.equals(group.getCourseProductId(), line.courseProductId)
                || !normalizedGrade(group.getGradeBand()).equals(normalizedGrade(line.gradeBand))) {
            throw new BusinessException("PRIVATE_LESSON_RENEWAL_MISMATCH",
                    "The renewal course and grade must match the existing shared private lesson.");
        }

        List<PrivateLessonGroupMemberEntity> members = activePrivateLessonGroupMembers(groupId);
        validateSharedRenewalParticipants(members, participants);
        RegistrationIntakeResultVO.CourseArtifactVO primaryArtifact = null;
        List<RegistrationIntakeResultVO.ParticipantResultVO> participantResults = new ArrayList<>();
        for (PrivateLessonGroupMemberEntity member : members) {
            StudentResolution participant = participants.stream()
                    .filter(item -> Objects.equals(item.student().getId(), member.getStudentId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("PRIVATE_LESSON_PARTICIPANT_REQUIRED",
                            "Every existing shared private lesson participant must be renewed together."));
            CoursePackageEntity coursePackage = requireRenewableOneOnOnePackage(member.getPackageId());
            int addedUnits = resolveOneOnOneUnits(line);
            BigDecimal memberPrice = splitSharedTuition(line.priceAmount, members.size(),
                    member.getParticipantIndex() - 1);
            BigDecimal multiplier = defaultAmount(line.basePriceAmount).compareTo(ZERO_AMOUNT) == 0
                    ? ZERO_AMOUNT
                    : memberPrice.divide(line.basePriceAmount, 8, RoundingMode.HALF_UP);
            BigDecimal memberPaid = member.getParticipantIndex() == 1 ? allocatedPaid : ZERO_AMOUNT;

            coursePackage.setEnrollmentOrderId(order.getId());
            coursePackage.setEnrollmentOrderItemId(orderItem.getId());
            coursePackage.setTotalUnits(safeUnits(coursePackage.getTotalUnits()) + addedUnits);
            coursePackage.setRemainingUnits(safeUnits(coursePackage.getRemainingUnits()) + addedUnits);
            coursePackage.setOriginalAmount(defaultAmount(coursePackage.getOriginalAmount()).add(memberPrice));
            coursePackage.setReceivedAmount(defaultAmount(coursePackage.getReceivedAmount()).add(memberPaid));
            coursePackage.setPackageStatus("ACTIVE");
            coursePackage.setCompletedAt(null);
            if (order.getPeriodEndDate() != null && (coursePackage.getEstimatedEndDate() == null
                    || order.getPeriodEndDate().isAfter(coursePackage.getEstimatedEndDate()))) {
                coursePackage.setEstimatedEndDate(order.getPeriodEndDate());
            }
            if (StringUtils.hasText(line.remark)) {
                coursePackage.setRemark(line.remark);
            }
            coursePackageMapper.updateById(coursePackage);

            RegistrationIntakeResultVO.CourseArtifactVO artifact = RegistrationIntakeResultVO.CourseArtifactVO.builder()
                    .orderItemId(orderItem.getId())
                    .lineNo(line.lineNo)
                    .courseType(line.courseType)
                    .packageId(coursePackage.getId())
                    .packageNo(coursePackage.getPackageNo())
                    .build();
            if (member.getParticipantIndex() == 1) {
                primaryArtifact = artifact;
            }
            participantResults.add(toParticipantResult(
                    participant, coursePackage.getId(), coursePackage.getPackageNo(), memberPrice,
                    ensureStudentAccount(participant.student())));
        }

        group.setEnrollmentOrderId(order.getId());
        group.setEnrollmentOrderItemId(orderItem.getId());
        group.setParticipantCount(members.size());
        privateLessonGroupMapper.updateById(group);
        return new PrivateLessonRegistrationResult(group.getId(), primaryArtifact, participantResults);
    }

    private boolean isSharedPrivateLessonRenewal(RegistrationIntakeRequest request) {
        return request.getRenewPackageId() != null && findPrivateLessonGroupIdByPackageId(request.getRenewPackageId()) != null;
    }

    private Long findPrivateLessonGroupIdByPackageId(Long packageId) {
        if (packageId == null) {
            return null;
        }
        PrivateLessonGroupMemberEntity member = privateLessonGroupMemberMapper.selectOne(
                new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                        .eq(PrivateLessonGroupMemberEntity::getPackageId, packageId)
                        .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                        .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0)
                        .last("LIMIT 1"));
        return member == null ? null : member.getPrivateLessonGroupId();
    }

    private List<PrivateLessonGroupMemberEntity> activePrivateLessonGroupMembers(Long groupId) {
        return privateLessonGroupMemberMapper.selectList(new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                .eq(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId, groupId)
                .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0)
                .orderByAsc(PrivateLessonGroupMemberEntity::getParticipantIndex));
    }

    private void validateSharedRenewalParticipants(List<PrivateLessonGroupMemberEntity> members,
                                                   List<StudentResolution> participants) {
        Set<Long> expected = members.stream().map(PrivateLessonGroupMemberEntity::getStudentId).collect(Collectors.toSet());
        Set<Long> actual = participants.stream().map(item -> item.student().getId()).collect(Collectors.toSet());
        if (expected.size() != actual.size() || !expected.equals(actual)) {
            throw new BusinessException("PRIVATE_LESSON_PARTICIPANTS_MISMATCH",
                    "A shared private lesson renewal must include the existing students together.");
        }
    }

    private RegistrationIntakeResultVO.ParticipantResultVO toParticipantResult(StudentResolution participant,
                                                                                 Long packageId,
                                                                                 String packageNo,
                                                                                 BigDecimal priceAmount,
                                                                                 RegistrationIntakeResultVO.AccountResultVO account) {
        return RegistrationIntakeResultVO.ParticipantResultVO.builder()
                .studentId(participant.student().getId())
                .studentNo(participant.student().getStudentNo())
                .studentName(participant.student().getStudentName())
                .studentReused(participant.reused())
                .packageId(packageId)
                .packageNo(packageNo)
                .priceAmount(priceAmount)
                .studentAccount(account)
                .build();
    }

    /**
     * 1v1 报名/续费共用的合并入口：同学生、同课程产品、同 grade 的有效课包直接叠加课时。
     */
    private CoursePackageEntity resolveExistingOneOnOnePackageForLine(Long studentId,
                                                                      ResolvedCourseLine line,
                                                                      CoursePackageEntity renewalTarget) {
        if (renewalTarget != null && isSameOneOnOnePackageCourse(renewalTarget, line)) {
            return renewalTarget;
        }

        List<CoursePackageEntity> candidates = coursePackageMapper.selectList(new LambdaQueryWrapper<CoursePackageEntity>()
                .eq(CoursePackageEntity::getStudentId, studentId)
                .eq(CoursePackageEntity::getCourseProductId, line.courseProductId)
                .eq(CoursePackageEntity::getTeachingMode, "ONE_ON_ONE")
                .ne(CoursePackageEntity::getPackageStatus, "CLOSED")
                .eq(CoursePackageEntity::getIsDeleted, 0)
                .orderByDesc(CoursePackageEntity::getCreatedAt));

        return candidates.stream()
                .filter(candidate -> isSameOneOnOnePackageCourse(candidate, line))
                .findFirst()
                .orElse(null);
    }

    private boolean isSameOneOnOnePackageCourse(CoursePackageEntity coursePackage, ResolvedCourseLine line) {
        return Objects.equals(coursePackage.getCourseProductId(), line.courseProductId)
                && normalizedGrade(packageGradeBand(coursePackage)).equals(normalizedGrade(line.gradeBand));
    }

    private String packageGradeBand(CoursePackageEntity coursePackage) {
        if (coursePackage.getEnrollmentOrderItemId() == null) {
            return null;
        }
        EnrollmentOrderItemEntity item = enrollmentOrderItemMapper.selectById(coursePackage.getEnrollmentOrderItemId());
        return item == null ? null : item.getGradeBand();
    }

    private String normalizedGrade(String gradeBand) {
        return StringUtils.hasText(gradeBand) ? gradeBand.trim().toUpperCase(Locale.ROOT) : "GENERAL";
    }

    private RegistrationIntakeResultVO.CourseArtifactVO createGroupClassArtifact(EnrollmentOrderEntity order,
                                                                                 StudentEntity student,
                                                                                 ResolvedCourseLine line,
                                                                                 Long renewClassEnrollmentId) {
        if (renewClassEnrollmentId != null) {
            ClassEnrollmentEntity enrollment = requireRenewableClassEnrollment(renewClassEnrollmentId);
            if (!Objects.equals(enrollment.getStudentId(), student.getId())) {
                throw new BusinessException("RENEWAL_STUDENT_MISMATCH",
                        "Renewal class enrollment does not belong to the selected student.");
            }
            if (!Objects.equals(enrollment.getClassId(), line.classId)) {
                throw new BusinessException("RENEWAL_CLASS_MISMATCH",
                        "Renewal class must match the existing enrollment.");
            }
            if (line.startDate == null && enrollment.getExpiryDate() != null) {
                LocalDate tomorrow = order.getSignupDate().plusDays(1);
                line.startDate = enrollment.getExpiryDate().isBefore(order.getSignupDate())
                        ? order.getSignupDate()
                        : enrollment.getExpiryDate().plusDays(1);
                if (line.startDate.isBefore(tomorrow) && !enrollment.getExpiryDate().isBefore(order.getSignupDate())) {
                    line.startDate = tomorrow;
                }
            }
            ClassBillingResult billing = computeClassBilling(line.classGroup, line, order.getSignupDate());
            ensureClassOccurrencesThrough(line.classGroup, line.product,
                    billing.occurrenceWindowStart(), billing.occurrenceWindowEnd());
            ensureNoStudentClassEnrollmentConflicts(student.getId(), line.classId,
                    billing.occurrenceWindowStart(), billing.occurrenceWindowEnd(), line.attendanceOption);

            enrollment.setEnrollmentOrderItemId(line.orderItemId);
            if (enrollment.getEnrollmentDate() == null || billing.enrollmentDate().isBefore(enrollment.getEnrollmentDate())) {
                enrollment.setEnrollmentDate(billing.enrollmentDate());
            }
            if (enrollment.getExpiryDate() == null || billing.expiryDate().isAfter(enrollment.getExpiryDate())) {
                enrollment.setExpiryDate(billing.expiryDate());
            }
            enrollment.setBillingType(line.packageUnit);
            enrollment.setPurchasedQuantity(safeQuantity(enrollment.getPurchasedQuantity()) + billing.purchasedQuantity());
            enrollment.setAttendanceOption(normalizeAttendanceOption(line.attendanceOption));
            enrollment.setStatus("ACTIVE");
            classEnrollmentMapper.updateById(enrollment);

            return RegistrationIntakeResultVO.CourseArtifactVO.builder()
                    .orderItemId(line.orderItemId)
                    .lineNo(line.lineNo)
                    .courseType(line.courseType)
                    .classEnrollmentId(enrollment.getId())
                    .classId(line.classId)
                    .build();
        }

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

    private CoursePackageEntity requireRenewableOneOnOnePackage(Long packageId) {
        CoursePackageEntity coursePackage = coursePackageMapper.selectById(packageId);
        if (coursePackage == null || Integer.valueOf(1).equals(coursePackage.getIsDeleted())) {
            throw new BusinessException("COURSE_PACKAGE_NOT_FOUND", "Course package not found.");
        }
        if (!"ONE_ON_ONE".equals(coursePackage.getTeachingMode())) {
            throw new BusinessException("RENEWAL_PACKAGE_MODE_MISMATCH",
                    "Only one-on-one packages can be renewed with a one-on-one course line.");
        }
        if ("CLOSED".equals(coursePackage.getPackageStatus())) {
            throw new BusinessException("RENEWAL_PACKAGE_CLOSED",
                    "Closed packages cannot be renewed. Reopen or create a new package instead.");
        }
        return coursePackage;
    }

    private ClassEnrollmentEntity requireRenewableClassEnrollment(Long enrollmentId) {
        ClassEnrollmentEntity enrollment = classEnrollmentMapper.selectById(enrollmentId);
        if (enrollment == null || Integer.valueOf(1).equals(enrollment.getIsDeleted())) {
            throw new BusinessException("CLASS_ENROLLMENT_NOT_FOUND", "Class enrollment not found.");
        }
        if (!"ACTIVE".equals(enrollment.getStatus())) {
            throw new BusinessException("RENEWAL_CLASS_INACTIVE",
                    "Only active group class enrollments can be renewed.");
        }
        return enrollment;
    }

    private int safeUnits(Integer value) {
        return value == null ? 0 : value;
    }

    private int safeQuantity(Integer value) {
        return value == null ? 0 : value;
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
                                                                            MoneySnapshot money,
                                                                            int participantCount,
                                                                            List<ResolvedCourseLine> lines) {
        List<RegistrationIntakeRequest.PaymentPayload> paymentPayloads = registrationPayments(request, participantCount);
        if (paymentPayloads.isEmpty()) {
            return Collections.emptyList();
        }
        List<RegistrationIntakeResultVO.PaymentResultVO> result = new ArrayList<>();
        for (RegistrationIntakeRequest.PaymentPayload payment : paymentPayloads) {
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
            entity.setParticipantIndex(payment.getParticipantIndex());
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
            if (payment.getAllocations() != null && !payment.getAllocations().isEmpty()) {
                Map<Integer, ResolvedCourseLine> linesByNo = lines.stream().collect(Collectors.toMap(
                        line -> line.lineNo, line -> line));
                Map<Long, BigDecimal> allocations = new LinkedHashMap<>();
                for (RegistrationIntakeRequest.CoursePaymentAllocationPayload allocation : payment.getAllocations()) {
                    if (allocation == null) {
                        throw new BusinessException("COURSE_PAYMENT_ALLOCATION_INVALID",
                                "An initial payment allocation cannot be empty.");
                    }
                    ResolvedCourseLine line = linesByNo.get(allocation.getOrderItemLineNo());
                    if (line == null || line.orderItemId == null) {
                        throw new BusinessException("COURSE_PAYMENT_ALLOCATION_INVALID",
                                "An initial payment references a course that was not saved.");
                    }
                    allocations.merge(line.orderItemId, allocation.getAmount(), BigDecimal::add);
                }
                paymentAllocationService.recordExplicitAllocations(entity, order.getId(), allocations);
            }
            result.add(RegistrationIntakeResultVO.PaymentResultVO.builder()
                    .paymentId(entity.getId())
                    .paymentNo(entity.getPaymentNo())
                    .participantIndex(entity.getParticipantIndex())
                    .paymentMethod(entity.getPaymentMethod())
                    .amount(entity.getAmount())
                    .paidAt(entity.getPaidAt())
                    .handledByName(entity.getHandledByName())
                    .remark(entity.getRemark())
                    .build());
        }
        if (!Objects.equals(order.getPaymentStatus(), money.paymentStatus())) {
            throw new BusinessException("PAYMENT_STATUS_MISMATCH", "Payment status calculation mismatch.");
        }
        return result;
    }

    private List<RegistrationIntakeRequest.PaymentPayload> registrationPayments(
            RegistrationIntakeRequest request, int participantCount) {
        if (participantCount <= 1 || request.getParticipantBillings() == null) {
            return request.getPayments() == null ? Collections.emptyList() : request.getPayments();
        }
        List<RegistrationIntakeRequest.PaymentPayload> result = new ArrayList<>();
        for (RegistrationIntakeRequest.ParticipantBillingPayload billing : request.getParticipantBillings()) {
            if (billing == null || billing.getPayments() == null) {
                continue;
            }
            for (RegistrationIntakeRequest.PaymentPayload payment : billing.getPayments()) {
                if (payment != null) {
                    payment.setParticipantIndex(billing.getParticipantIndex());
                    result.add(payment);
                }
            }
        }
        return result;
    }

    private ReceiptResult createReceiptIfNeeded(EnrollmentOrderEntity order,
                                                StudentEntity student,
                                                List<StudentResolution> participants,
                                                List<RegistrationIntakeResultVO.ParticipantResultVO> participantResults,
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
        Map<String, Object> previewData = buildReceiptPreview(order, student, participants,
                participantResults, request, lines, money, payments, artifacts);
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
                                                    List<StudentResolution> participants,
                                                    List<RegistrationIntakeResultVO.ParticipantResultVO> participantResults,
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
        Map<Long, RegistrationIntakeResultVO.ParticipantResultVO> participantResultsByStudentId =
                participantResults.stream().collect(Collectors.toMap(
                        RegistrationIntakeResultVO.ParticipantResultVO::studentId,
                        Function.identity(),
                        (left, right) -> left));
        Map<Long, EnrollmentOrderParticipantEntity> participantChargesByStudentId =
                enrollmentOrderParticipantMapper.selectList(new LambdaQueryWrapper<EnrollmentOrderParticipantEntity>()
                                .eq(EnrollmentOrderParticipantEntity::getEnrollmentOrderId, order.getId())
                                .eq(EnrollmentOrderParticipantEntity::getIsDeleted, 0))
                .stream()
                .collect(Collectors.toMap(EnrollmentOrderParticipantEntity::getStudentId,
                        Function.identity(), (left, right) -> left));
        payload.put("participants", participants.stream().map(item -> {
            Map<String, Object> participant = new LinkedHashMap<>();
            StudentEntity participantStudent = item.student();
            RegistrationIntakeResultVO.ParticipantResultVO participantResult =
                    participantResultsByStudentId.get(participantStudent.getId());
            EnrollmentOrderParticipantEntity participantCharge = participantChargesByStudentId.get(participantStudent.getId());
            participant.put("studentId", participantStudent.getId());
            participant.put("studentNo", participantStudent.getStudentNo());
            participant.put("studentName", participantStudent.getStudentName());
            participant.put("grade", participantStudent.getGrade());
            participant.put("school", participantStudent.getSchool());
            participant.put("studentClass", participantStudent.getStudentClass());
            participant.put("guardianName", participantStudent.getGuardianName());
            participant.put("guardianPhone", participantStudent.getGuardianPhone());
            participant.put("guardianPhone2", participantStudent.getGuardianPhone2());
            participant.put("guardianEmail", participantStudent.getGuardianEmail());
            participant.put("pickupRequired", participantStudent.getPickupRequired());
            participant.put("pickupAddress", participantStudent.getPickupAddress());
            participant.put("dropoffRequired", participantStudent.getDropoffRequired());
            participant.put("dropoffAddress", participantStudent.getDropoffAddress());
            if (participantResult != null) {
                participant.put("packageId", participantResult.packageId());
                participant.put("packageNo", participantResult.packageNo());
                participant.put("priceAmount", participantResult.priceAmount());
            }
            if (participantCharge != null) {
                participant.put("participantIndex", participantCharge.getParticipantIndex());
                participant.put("grossAmount", participantCharge.getGrossAmount());
                participant.put("discountAmount", participantCharge.getDiscountAmount());
                participant.put("discounts", participantCharge.getDiscountDetail() == null
                        ? Collections.emptyList()
                        : jsonHelper.fromJson(participantCharge.getDiscountDetail(), new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {}));
                participant.put("amountDue", participantCharge.getAmountDue());
                participant.put("paidAmount", participantCharge.getPaidAmount());
                participant.put("balanceAmount", participantCharge.getBalanceAmount());
            }
            return participant;
        }).toList());
        payload.put("studentClass", order.getStudentClassSnapshot());
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
        payload.put("registrationRemark", order.getRemark());
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
        item.put("discountAmount", line.discountAmount);
        item.put("amountDue", line.amountDue);
        item.put("paidAmount", line.initialPaidAmount);
        item.put("balanceAmount", line.amountDue.subtract(defaultAmount(line.initialPaidAmount)));
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
        int quantity = line.packageQuantity == null
                ? resolveGroupPurchasedQuantity(line)
                : line.packageQuantity.setScale(0, RoundingMode.CEILING).intValue();
        ClassBillingService.ClassBilling billing = classBillingService.compute(
                classGroup, line.startDate, signupDate, line.packageUnit, line.packageQuantity);
        return new ClassBillingResult(billing.enrollmentDate(), billing.expiryDate(),
                billing.enrollmentDate(), billing.expiryDate(), quantity);
    }

    private int ensureClassOccurrencesThrough(ClassGroupEntity classGroup,
                                               CourseProductEntity product,
                                               LocalDate windowStart,
                                               LocalDate horizonEnd) {
        List<ClassGroupScheduleSlotEntity> slots = loadClassSlots(classGroup);
        if (slots.isEmpty()) {
            // 班课允许先报名、后排课；没有 weekly slot 时只保存 enrollment，不生成日历 occurrence。
            return 0;
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

    private String cleanText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
        private BigDecimal discountAmount;
        private BigDecimal amountDue;
        private BigDecimal initialPaidAmount;
        private BigDecimal basePriceAmount;
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

    private record PrivateLessonRegistrationResult(
            Long groupId,
            RegistrationIntakeResultVO.CourseArtifactVO primaryArtifact,
            List<RegistrationIntakeResultVO.ParticipantResultVO> participants
    ) {
    }
}
