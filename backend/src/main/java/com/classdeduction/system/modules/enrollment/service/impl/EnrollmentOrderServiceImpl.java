package com.classdeduction.system.modules.enrollment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.common.util.UnitConverter;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentCreateRequest;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderPageQuery;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderCorrectionRequest;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderUpdateRequest;
import com.classdeduction.system.modules.enrollment.dto.RegistrationIntakeRequest;
import com.classdeduction.system.modules.enrollment.dto.RefundRequest;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderParticipantEntity;
import com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentMethodEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupMemberEntity;
import com.classdeduction.system.modules.enrollment.entity.ReceiptRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderParticipantMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentMethodMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
import com.classdeduction.system.modules.enrollment.mapper.ReceiptRecordMapper;
import com.classdeduction.system.modules.enrollment.service.ClassBillingService;
import com.classdeduction.system.modules.enrollment.service.EnrollmentOrderService;
import com.classdeduction.system.modules.enrollment.util.BusinessNoGenerator;
import com.classdeduction.system.modules.enrollment.vo.CoursePackageListItemVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentCreateResultVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderDetailVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderItemVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderListItemVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderParticipantVO;
import com.classdeduction.system.modules.enrollment.vo.PackageTeacherAssignmentVO;
import com.classdeduction.system.modules.enrollment.vo.PaymentRecordVO;
import com.classdeduction.system.modules.enrollment.vo.ReceiptRecordVO;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.entity.PackageBalanceSnapshotEntity;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.lesson.mapper.PackageBalanceSnapshotMapper;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.LessonAdjustmentLogEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonAdjustmentLogMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleSlotMapper;
import com.classdeduction.system.modules.systemconfig.entity.NotificationLogEntity;
import com.classdeduction.system.modules.systemconfig.mapper.NotificationLogMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.DependsOn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
import java.util.stream.Collectors;

/**
 * 报名服务：负责报名开单，并同步生成支付记录、课时包和收据。
 *
 * <p>1对1 的老师、课程和逐时段课时分配由排课服务负责，报名不再创建默认老师分配或自动排课。
 */
@Service
@Slf4j
@RequiredArgsConstructor
@DependsOn("privateLessonSchemaCompatibilityInitializer")
public class EnrollmentOrderServiceImpl implements EnrollmentOrderService {

    private static final String DEFAULT_CURRENCY = "USD";
    private static final String DEFAULT_RECEIPT_TEMPLATE = "DEFAULT_RECEIPT";
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final EnrollmentOrderMapper enrollmentOrderMapper;
    private final EnrollmentOrderItemMapper enrollmentOrderItemMapper;
    private final EnrollmentOrderParticipantMapper enrollmentOrderParticipantMapper;
    private final CoursePackageMapper coursePackageMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final PrivateLessonGroupMemberMapper privateLessonGroupMemberMapper;
    private final ReceiptRecordMapper receiptRecordMapper;
    private final ClassBillingService classBillingService;
    private final PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;
    private final StudentMapper studentMapper;
    private final CourseProductMapper courseProductMapper;
    private final TeacherMapper teacherMapper;
    private final JsonHelper jsonHelper;
    private final RecurringScheduleRuleMapper recurringScheduleRuleMapper;
    private final RecurringScheduleSlotMapper recurringScheduleSlotMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final LessonRecordMapper lessonRecordMapper;
    private final HourLedgerMapper hourLedgerMapper;
    private final PackageBalanceSnapshotMapper packageBalanceSnapshotMapper;
    private final LessonAdjustmentLogMapper lessonAdjustmentLogMapper;
    private final NotificationLogMapper notificationLogMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;
    private final PaymentMethodMapper paymentMethodMapper;

    /**
     * Initialize the business number sequence from the database to avoid
     * collisions after a server restart (in-memory AtomicInteger resets to 0).
     */
    @PostConstruct
    void initSequence() {
        String todayStr = LocalDate.now(AppTime.APP_ZONE_ID).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // Init enrollment order sequence (EO prefix)
        LambdaQueryWrapper<EnrollmentOrderEntity> eoWrapper = new LambdaQueryWrapper<EnrollmentOrderEntity>()
                .likeRight(EnrollmentOrderEntity::getEnrollmentNo, "EO" + todayStr)
                .orderByDesc(EnrollmentOrderEntity::getEnrollmentNo)
                .last("LIMIT 1");
        EnrollmentOrderEntity lastEO = enrollmentOrderMapper.selectOne(eoWrapper);
        if (lastEO != null) {
            String no = lastEO.getEnrollmentNo();
            int seq = Integer.parseInt(no.substring(no.length() - 4));
            BusinessNoGenerator.initSequence("EO", seq);
        }
        // Init course package sequence (PK prefix)
        LambdaQueryWrapper<CoursePackageEntity> pkWrapper = new LambdaQueryWrapper<CoursePackageEntity>()
                .likeRight(CoursePackageEntity::getPackageNo, "PK" + todayStr)
                .orderByDesc(CoursePackageEntity::getPackageNo)
                .last("LIMIT 1");
        CoursePackageEntity lastPK = coursePackageMapper.selectOne(pkWrapper);
        if (lastPK != null) {
            String no = lastPK.getPackageNo();
            int seq = Integer.parseInt(no.substring(no.length() - 4));
            BusinessNoGenerator.initSequence("PK", seq);
        }
        // Init payment record sequence (PMT prefix)
        LambdaQueryWrapper<PaymentRecordEntity> pmtWrapper = new LambdaQueryWrapper<PaymentRecordEntity>()
                .likeRight(PaymentRecordEntity::getPaymentNo, "PMT" + todayStr)
                .orderByDesc(PaymentRecordEntity::getPaymentNo)
                .last("LIMIT 1");
        PaymentRecordEntity lastPMT = paymentRecordMapper.selectOne(pmtWrapper);
        if (lastPMT != null) {
            String no = lastPMT.getPaymentNo();
            int seq = Integer.parseInt(no.substring(no.length() - 4));
            BusinessNoGenerator.initSequence("PMT", seq);
        }
        // Init receipt record sequence (RCP prefix)
        LambdaQueryWrapper<ReceiptRecordEntity> rcpWrapper = new LambdaQueryWrapper<ReceiptRecordEntity>()
                .likeRight(ReceiptRecordEntity::getReceiptNo, "RCP" + todayStr)
                .orderByDesc(ReceiptRecordEntity::getReceiptNo)
                .last("LIMIT 1");
        ReceiptRecordEntity lastRCP = receiptRecordMapper.selectOne(rcpWrapper);
        if (lastRCP != null) {
            String no = lastRCP.getReceiptNo();
            int seq = Integer.parseInt(no.substring(no.length() - 4));
            BusinessNoGenerator.initSequence("RCP", seq);
        }
    }

    @Override
    public PageResponse<EnrollmentOrderListItemVO> pageOrders(EnrollmentOrderPageQuery query) {
        Page<EnrollmentOrderEntity> page = new Page<>(query.getPageNum(), query.getPageSize());
        List<Long> matchedStudentIds = findMatchedStudentIds(query.getKeyword());
        List<Long> matchedCourseOrderIds = findMatchedCourseOrderIds(query.getKeyword());
        List<Long> matchedClassIds = findMatchedClassIds(query.getKeyword());
        LambdaQueryWrapper<EnrollmentOrderEntity> wrapper = new LambdaQueryWrapper<EnrollmentOrderEntity>()
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(EnrollmentOrderEntity::getEnrollmentNo, query.getKeyword())
                        .or(!matchedStudentIds.isEmpty(), nested -> nested.in(EnrollmentOrderEntity::getStudentId, matchedStudentIds))
                        .or(!matchedCourseOrderIds.isEmpty(), nested -> nested.in(EnrollmentOrderEntity::getId, matchedCourseOrderIds))
                        .or(!matchedClassIds.isEmpty(), nested -> nested.in(EnrollmentOrderEntity::getClassId, matchedClassIds)))
                .eq(query.getStudentId() != null, EnrollmentOrderEntity::getStudentId, query.getStudentId())
                .eq(query.getClassId() != null, EnrollmentOrderEntity::getClassId, query.getClassId())
                .isNull(query.isExcludeClassOrders(), EnrollmentOrderEntity::getClassId)
                .eq(StringUtils.hasText(query.getOrderStatus()), EnrollmentOrderEntity::getOrderStatus, query.getOrderStatus())
                .eq(StringUtils.hasText(query.getPaymentStatus()), EnrollmentOrderEntity::getPaymentStatus, query.getPaymentStatus())
                .orderByDesc(EnrollmentOrderEntity::getCreatedAt);
        IPage<EnrollmentOrderEntity> result = enrollmentOrderMapper.selectPage(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }

        Set<Long> studentIds = result.getRecords().stream().map(EnrollmentOrderEntity::getStudentId).collect(Collectors.toSet());
        Map<Long, StudentEntity> studentMap = listStudentsByIds(studentIds);

        // Separate 1v1 and class orders
        List<Long> oneOnOneOrderIds = result.getRecords().stream()
                .filter(o -> o.getClassId() == null)
                .map(EnrollmentOrderEntity::getId).toList();
        Set<Long> classIds = result.getRecords().stream()
                .map(EnrollmentOrderEntity::getClassId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        // Load packages for 1v1 orders
        List<CoursePackageEntity> allPackages = oneOnOneOrderIds.isEmpty()
                ? Collections.emptyList()
                : coursePackageMapper.selectList(new LambdaQueryWrapper<CoursePackageEntity>()
                        .in(CoursePackageEntity::getEnrollmentOrderId, oneOnOneOrderIds));
        Map<Long, Integer> packageCountMap = allPackages.stream()
                .collect(Collectors.groupingBy(CoursePackageEntity::getEnrollmentOrderId, Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        Map<Long, CoursePackageEntity> firstPackageMap = allPackages.stream()
                .collect(Collectors.groupingBy(CoursePackageEntity::getEnrollmentOrderId,
                        Collectors.collectingAndThen(Collectors.toList(), list -> list.isEmpty() ? null : list.get(0))));

        Set<Long> courseProductIds = allPackages.stream()
                .map(CoursePackageEntity::getCourseProductId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, CourseProductEntity> productMap = courseProductIds.isEmpty()
                ? Collections.emptyMap()
                : courseProductMapper.selectBatchIds(courseProductIds).stream()
                        .collect(Collectors.toMap(CourseProductEntity::getId, p -> p));

        // Load class names for class orders
        Map<Long, ClassGroupEntity> classMap = classIds.isEmpty()
                ? Collections.emptyMap()
                : classGroupMapper.selectBatchIds(classIds).stream()
                        .collect(Collectors.toMap(ClassGroupEntity::getId, c -> c));
        List<Long> orderIds = result.getRecords().stream()
                .map(EnrollmentOrderEntity::getId)
                .toList();
        Map<Long, List<EnrollmentOrderItemVO>> itemsByOrderId = orderIds.isEmpty()
                ? Collections.emptyMap()
                : enrollmentOrderItemMapper.selectList(new LambdaQueryWrapper<EnrollmentOrderItemEntity>()
                        .in(EnrollmentOrderItemEntity::getEnrollmentOrderId, orderIds)
                        .orderByAsc(EnrollmentOrderItemEntity::getLineNo)
                        .orderByAsc(EnrollmentOrderItemEntity::getId))
                .stream()
                .collect(Collectors.groupingBy(EnrollmentOrderItemEntity::getEnrollmentOrderId,
                        Collectors.mapping(this::toOrderItemVO, Collectors.toList())));

        List<EnrollmentOrderParticipantEntity> orderParticipants = orderIds.isEmpty()
                ? Collections.emptyList()
                : enrollmentOrderParticipantMapper.selectList(new LambdaQueryWrapper<EnrollmentOrderParticipantEntity>()
                        .in(EnrollmentOrderParticipantEntity::getEnrollmentOrderId, orderIds)
                        .eq(EnrollmentOrderParticipantEntity::getIsDeleted, 0)
                        .orderByAsc(EnrollmentOrderParticipantEntity::getParticipantIndex)
                        .orderByAsc(EnrollmentOrderParticipantEntity::getId));
        Map<Long, List<EnrollmentOrderParticipantVO>> participantsByOrderId = orderParticipants.stream()
                .collect(Collectors.groupingBy(EnrollmentOrderParticipantEntity::getEnrollmentOrderId,
                        Collectors.mapping(this::toParticipantVO, Collectors.toList())));

        List<EnrollmentOrderListItemVO> list = result.getRecords().stream()
                .map(order -> {
                    StudentEntity student = studentMap.get(order.getStudentId());
                    List<EnrollmentOrderItemVO> items = itemsByOrderId.getOrDefault(order.getId(), Collections.emptyList());
                    List<EnrollmentOrderParticipantVO> participants = participantsByOrderId.getOrDefault(
                            order.getId(), Collections.emptyList());
                    if (order.getClassId() != null) {
                        // Class order: no course_package, show class name instead
                        ClassGroupEntity clz = classMap.get(order.getClassId());
                        return toClassOrderListItem(order, student, clz, items, participants);
                    }
                    CoursePackageEntity firstPackage = firstPackageMap.get(order.getId());
                    CourseProductEntity product = firstPackage == null ? null : productMap.get(firstPackage.getCourseProductId());
                    return toOrderListItem(order, student, firstPackage, product, packageCountMap.getOrDefault(order.getId(), 0), items, participants);
                })
                .toList();

        return PageResponse.of(list, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public EnrollmentOrderDetailVO getOrder(Long id) {
        EnrollmentOrderEntity order = requireOrder(id);
        StudentEntity student = requireStudent(order.getStudentId());
        List<EnrollmentOrderItemEntity> orderItems = listOrderItemsByOrderId(id);
        List<PaymentRecordEntity> payments = listPaymentsByOrderId(id);
        List<ReceiptRecordEntity> receipts = listReceiptsByOrderIds(List.of(id));
        List<EnrollmentOrderParticipantEntity> participants = enrollmentOrderParticipantMapper.selectList(
                new LambdaQueryWrapper<EnrollmentOrderParticipantEntity>()
                        .eq(EnrollmentOrderParticipantEntity::getEnrollmentOrderId, id)
                        .eq(EnrollmentOrderParticipantEntity::getIsDeleted, 0)
                        .orderByAsc(EnrollmentOrderParticipantEntity::getParticipantIndex));

        // Resolve class name for class orders
        String className = null;
        if (order.getClassId() != null) {
            ClassGroupEntity clz = classGroupMapper.selectById(order.getClassId());
            className = clz != null ? clz.getClassName() : null;
        }

        // Load packages only for 1v1 orders
        List<CoursePackageEntity> packages = order.getClassId() == null
                ? listPackagesByOrderIds(List.of(id))
                : Collections.emptyList();
        Map<Long, String> primaryTeacherNameMap = packages.isEmpty()
                ? Collections.emptyMap()
                : buildPrimaryTeacherNameMap(packages.stream().map(CoursePackageEntity::getId).toList());

        Set<Long> courseProductIds = packages.stream()
                .map(CoursePackageEntity::getCourseProductId)
                .filter(pid -> pid != null)
                .collect(Collectors.toSet());
        Map<Long, CourseProductEntity> productMap = courseProductIds.isEmpty()
                ? Collections.emptyMap()
                : courseProductMapper.selectBatchIds(courseProductIds).stream()
                        .collect(Collectors.toMap(CourseProductEntity::getId, p -> p));

        return EnrollmentOrderDetailVO.builder()
                .id(order.getId())
                .enrollmentNo(order.getEnrollmentNo())
                .studentId(student.getId())
                .studentNo(student.getStudentNo())
                .studentName(student.getStudentName())
                .classId(order.getClassId())
                .className(className)
                .campusId(order.getCampusId())
                .campusNameSnapshot(order.getCampusNameSnapshot())
                .orderStatus(order.getOrderStatus())
                .cashierUserId(order.getCashierUserId())
                .handledByName(order.getHandledByName())
                .signupDate(order.getSignupDate())
                .periodStartDate(order.getPeriodStartDate())
                .periodEndDate(order.getPeriodEndDate())
                .studentNameSnapshot(order.getStudentNameSnapshot())
                .studentClassSnapshot(order.getStudentClassSnapshot())
                .gradeSnapshot(order.getGradeSnapshot())
                .schoolSnapshot(order.getSchoolSnapshot())
                .parentPhoneSnapshot(order.getParentPhoneSnapshot())
                .parentPhone2Snapshot(order.getParentPhone2Snapshot())
                .pickupRequiredSnapshot(Boolean.TRUE.equals(order.getPickupRequiredSnapshot()))
                .pickupAddressSnapshot(order.getPickupAddressSnapshot())
                .dropoffRequiredSnapshot(Boolean.TRUE.equals(order.getDropoffRequiredSnapshot()))
                .dropoffAddressSnapshot(order.getDropoffAddressSnapshot())
                .currencyCode(order.getCurrencyCode())
                .tuitionSubtotalAmount(order.getTuitionSubtotalAmount())
                .pickupFeeAmount(order.getPickupFeeAmount())
                .dropoffFeeAmount(order.getDropoffFeeAmount())
                .extraFeeAmount(order.getExtraFeeAmount())
                .extraFeeDetail(order.getExtraFeeDetail())
                .originalAmount(order.getOriginalAmount())
                .discountAmount(order.getDiscountAmount())
                .discountDetail(order.getDiscountDetail())
                .receivedAmount(order.getReceivedAmount())
                .amountDue(order.getAmountDue())
                .balanceAmount(order.getBalanceAmount())
                .paymentStatus(order.getPaymentStatus())
                .remark(order.getRemark())
                .items(orderItems.stream().map(this::toOrderItemVO).toList())
                .participants(participants.stream().map(this::toParticipantVO).toList())
                .payments(payments.stream().map(this::toPaymentVO).toList())
                .packages(packages.stream().map(item -> toPackageListItem(item, student, primaryTeacherNameMap.get(item.getId()), productMap.get(item.getCourseProductId()))).toList())
                .receipts(receipts.stream().map(item -> toReceiptVO(item, student.getStudentName())).toList())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private EnrollmentOrderParticipantVO toParticipantVO(EnrollmentOrderParticipantEntity item) {
        return new EnrollmentOrderParticipantVO(
                item.getId(), item.getStudentId(), item.getParticipantIndex(),
                item.getStudentNameSnapshot(), item.getStudentNoSnapshot(), item.getGradeSnapshot(),
                item.getSchoolSnapshot(), item.getStudentClassSnapshot(), item.getGuardianNameSnapshot(),
                item.getGuardianPhoneSnapshot(), item.getGuardianPhone2Snapshot(), item.getGuardianEmailSnapshot(),
                item.getPickupRequiredSnapshot(), item.getPickupAddressSnapshot(),
                item.getDropoffRequiredSnapshot(), item.getDropoffAddressSnapshot(),
                item.getGrossAmount(), item.getDiscountAmount(), item.getDiscountDetail(),
                item.getAmountDue(), item.getPaidAmount(), item.getBalanceAmount());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnrollmentCreateResultVO createOrder(EnrollmentCreateRequest request) {
        validateAmounts(request);
        StudentEntity student = requireStudent(request.getStudentId());
        CourseProductEntity product = requireCourseProduct(request.getCourseProductId());
        validateOneOnOneProduct(product);
        validateOrderItemExists(request.getEnrollmentOrderItemId());
        EnrollmentOrderEntity order = new EnrollmentOrderEntity();
        order.setEnrollmentNo(BusinessNoGenerator.generateEnrollmentNo());
        order.setStudentId(student.getId());
        order.setOrderStatus("CONFIRMED");
        order.setCashierUserId(request.getCashierUserId());
        order.setHandledByName(request.getHandledByName());
        order.setCurrencyCode(StringUtils.hasText(request.getCurrencyCode()) ? request.getCurrencyCode() : DEFAULT_CURRENCY);
        order.setOriginalAmount(request.getOriginalAmount());
        order.setDiscountAmount(request.getDiscountAmount());
        order.setReceivedAmount(request.getReceivedAmount());
        applyAmountSnapshot(order, request.getOriginalAmount(), ZERO_AMOUNT, ZERO_AMOUNT,
                request.getDiscountAmount(), request.getReceivedAmount());
        order.setPaymentStatus(resolvePaymentStatus(request.getOriginalAmount(), request.getDiscountAmount(), request.getReceivedAmount()));
        order.setRemark(request.getRemark());
        enrollmentOrderMapper.insert(order);

        CoursePackageEntity coursePackage = createCoursePackage(order, product, request, student);
        List<Long> paymentIds = new ArrayList<>();
        List<String> paymentNos = new ArrayList<>();
        List<Long> receiptIds = new ArrayList<>();
        List<String> receiptNos = new ArrayList<>();

        if (request.getReceivedAmount().compareTo(BigDecimal.ZERO) > 0) {
            PaymentRecordEntity paymentRecord = createPaymentRecord(order, request);
            paymentIds.add(paymentRecord.getId());
            paymentNos.add(paymentRecord.getPaymentNo());
        }
        if (!Boolean.FALSE.equals(request.getIssueReceipt())) {
            ReceiptRecordEntity receiptRecord = createReceipt(order, coursePackage, student, product, request);
            receiptIds.add(receiptRecord.getId());
            receiptNos.add(receiptRecord.getReceiptNo());
        }

        return EnrollmentCreateResultVO.builder()
                .enrollmentOrderId(order.getId())
                .enrollmentNo(order.getEnrollmentNo())
                .packageIds(List.of(coursePackage.getId()))
                .packageNos(List.of(coursePackage.getPackageNo()))
                .paymentIds(paymentIds)
                .paymentNos(paymentNos)
                .receiptIds(receiptIds)
                .receiptNos(receiptNos)
                .scheduleRuleId(null)
                .generatedOccurrenceCount(null)
                .firstLessonAt(null)
                .lastLessonAt(null)
                .build();
    }

    private void validateAmounts(EnrollmentCreateRequest request) {
        BigDecimal discountedAmount = request.getOriginalAmount().subtract(request.getDiscountAmount());
        if (discountedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("DISCOUNT_EXCEEDS_AMOUNT", "Discount cannot exceed original amount.");
        }
        if (request.getReceivedAmount().compareTo(discountedAmount) > 0) {
            throw new BusinessException("RECEIVED_EXCEEDS_AMOUNT", "Received amount cannot exceed amount due.");
        }
        validatePackageUnits(request);
    }

    private void validateOneOnOneProduct(CourseProductEntity product) {
        if (!"ACTIVE".equals(product.getStatus())) {
            throw new BusinessException("COURSE_PRODUCT_INACTIVE", "Course product is not active.");
        }
        if (!"ONE_ON_ONE".equals(product.getTeachingMode())) {
            throw new BusinessException("INVALID_COURSE_PRODUCT_TYPE", "Only 1:1 course products can be used for new enrollment.");
        }
    }

    private void validatePackageUnits(EnrollmentCreateRequest request) {
        if (request.getTotalUnits() != null && request.getTotalUnits() <= 0) {
            throw new BusinessException("INVALID_TOTAL_UNITS", "Total units must be greater than 0.");
        }
    }

    private CoursePackageEntity createCoursePackage(EnrollmentOrderEntity order,
                                                    CourseProductEntity product,
                                                    EnrollmentCreateRequest request,
                                                    StudentEntity student) {
        int totalUnits = request.getTotalUnits();
        if (totalUnits <= 0) {
            throw new BusinessException("INVALID_TOTAL_UNITS", "Total units must be greater than 0.");
        }
        // totalUnits 从请求传入的是小时数，内部存储统一转为半课时单位（1单位=0.5h）。
        int halfHourUnits = totalUnits * 2;

        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setPackageNo(BusinessNoGenerator.generatePackageNo());
        coursePackage.setEnrollmentOrderId(order.getId());
        coursePackage.setEnrollmentOrderItemId(request.getEnrollmentOrderItemId());
        coursePackage.setStudentId(student.getId());
        coursePackage.setCourseProductId(product.getId());
        coursePackage.setPackageName(StringUtils.hasText(request.getPackageName()) ? request.getPackageName() : product.getProductName());
        coursePackage.setTeachingMode(product.getTeachingMode());
        coursePackage.setTotalUnits(halfHourUnits);
        coursePackage.setConsumedUnits(0);
        coursePackage.setRemainingUnits(halfHourUnits);
        coursePackage.setOriginalAmount(order.getOriginalAmount());
        coursePackage.setDiscountAmount(order.getDiscountAmount());
        coursePackage.setReceivedAmount(order.getReceivedAmount());
        coursePackage.setPackageStatus("ACTIVE");
        coursePackage.setStartDate(request.getStartDate());
        coursePackage.setEstimatedEndDate(request.getEstimatedEndDate());
        coursePackage.setRemark(request.getRemark());
        coursePackageMapper.insert(coursePackage);
        return coursePackage;
    }

    private void ensureNoConflicts(List<LessonOccurrenceEntity> generatedOccurrences,
                                   Long studentId,
                                   Long teacherId,
                                   Long classroomId) {
        if (generatedOccurrences.isEmpty()) {
            return;
        }
        LocalDateTime windowStart = generatedOccurrences.stream()
                .map(LessonOccurrenceEntity::getPlannedStartAt).min(Comparator.naturalOrder()).orElseThrow();
        LocalDateTime windowEnd = generatedOccurrences.stream()
                .map(LessonOccurrenceEntity::getPlannedEndAt).max(Comparator.naturalOrder()).orElseThrow();

        var studentOccurrences = studentId == null ? Collections.<LessonOccurrenceEntity>emptyList()
                : lessonOccurrenceMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getStudentId, studentId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));
        var teacherOccurrences = teacherId == null ? Collections.<LessonOccurrenceEntity>emptyList()
                : lessonOccurrenceMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPlannedTeacherId, teacherId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));
        var classroomOccurrences = classroomId == null ? Collections.<LessonOccurrenceEntity>emptyList()
                : lessonOccurrenceMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPlannedClassroomId, classroomId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));

        List<String> conflicts = new ArrayList<>();
        for (LessonOccurrenceEntity candidate : generatedOccurrences) {
            if (studentId != null && hasOverlap(candidate, studentOccurrences)) {
                conflicts.add("Student conflict at " + candidate.getPlannedStartAt());
            }
            if (teacherId != null && hasOverlap(candidate, teacherOccurrences)) {
                conflicts.add("Teacher conflict at " + candidate.getPlannedStartAt());
            }
            if (classroomId != null && hasOverlap(candidate, classroomOccurrences)) {
                conflicts.add("Classroom conflict at " + candidate.getPlannedStartAt());
            }
            if (conflicts.size() >= 3) {
                break;
            }
        }
        if (!conflicts.isEmpty()) {
            throw new BusinessException("SCHEDULE_CONFLICT", String.join("; ", conflicts));
        }
    }

    private boolean hasOverlap(LessonOccurrenceEntity candidate, List<LessonOccurrenceEntity> existingOccurrences) {
        return existingOccurrences.stream().anyMatch(existing ->
                candidate.getPlannedStartAt().isBefore(existing.getPlannedEndAt())
                        && candidate.getPlannedEndAt().isAfter(existing.getPlannedStartAt()));
    }

    private int toWeekday(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> 1;
            case TUESDAY -> 2;
            case WEDNESDAY -> 3;
            case THURSDAY -> 4;
            case FRIDAY -> 5;
            case SATURDAY -> 6;
            case SUNDAY -> 7;
        };
    }

    private PaymentRecordEntity createPaymentRecord(EnrollmentOrderEntity order, EnrollmentCreateRequest request) {
        PaymentRecordEntity paymentRecord = new PaymentRecordEntity();
        paymentRecord.setEnrollmentOrderId(order.getId());
        paymentRecord.setPaymentNo(BusinessNoGenerator.generatePaymentNo());
        paymentRecord.setPaymentMethod("CASH");
        paymentRecord.setPaymentStatus(order.getPaymentStatus());
        paymentRecord.setAmount(request.getReceivedAmount());
        paymentRecord.setPaidAt(request.getPaidAt() != null ? request.getPaidAt() : AppTime.nowLocalDateTime());
        paymentRecord.setHandledByUserId(request.getCashierUserId());
        paymentRecord.setHandledByName(request.getHandledByName());
        paymentRecord.setRemark(request.getRemark());
        paymentRecordMapper.insert(paymentRecord);
        return paymentRecord;
    }

    private ReceiptRecordEntity createReceipt(EnrollmentOrderEntity order,
                                              CoursePackageEntity coursePackage,
                                              StudentEntity student,
                                              CourseProductEntity product,
                                              EnrollmentCreateRequest request) {
        ReceiptRecordEntity receiptRecord = new ReceiptRecordEntity();
        receiptRecord.setReceiptNo(BusinessNoGenerator.generateReceiptNo());
        receiptRecord.setEnrollmentOrderId(order.getId());
        receiptRecord.setStudentId(student.getId());
        receiptRecord.setTemplateCode(StringUtils.hasText(request.getTemplateCode()) ? request.getTemplateCode() : DEFAULT_RECEIPT_TEMPLATE);
        receiptRecord.setReceiptPayload(buildReceiptPayload(order, coursePackage, student, product, request));
        receiptRecord.setIssuedAt(AppTime.nowLocalDateTime());
        receiptRecord.setIssuedBy(request.getCashierUserId());
        receiptRecordMapper.insert(receiptRecord);
        return receiptRecord;
    }

    private String buildReceiptPayload(EnrollmentOrderEntity order,
                                       CoursePackageEntity coursePackage,
                                       StudentEntity student,
                                       CourseProductEntity product,
                                       EnrollmentCreateRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enrollmentNo", order.getEnrollmentNo());
        payload.put("packageNo", coursePackage.getPackageNo());
        payload.put("studentNo", student.getStudentNo());
        payload.put("studentName", student.getStudentName());
        payload.put("courseProductCode", product.getProductCode());
        payload.put("courseProductName", product.getProductName());
        payload.put("packageName", coursePackage.getPackageName());
        payload.put("handledByName", request.getHandledByName());
        payload.put("currencyCode", order.getCurrencyCode());
        payload.put("originalAmount", order.getOriginalAmount());
        payload.put("discountAmount", order.getDiscountAmount());
        payload.put("receivedAmount", order.getReceivedAmount());
        payload.put("totalUnits", coursePackage.getTotalUnits());
        return jsonHelper.toJson(payload);
    }

    private String resolvePaymentStatus(BigDecimal originalAmount, BigDecimal discountAmount, BigDecimal receivedAmount) {
        BigDecimal dueAmount = originalAmount.subtract(discountAmount);
        if (dueAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return "PAID";
        }
        if (receivedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return "UNPAID";
        }
        if (receivedAmount.compareTo(dueAmount) >= 0) {
            return "PAID";
        }
        return "PARTIAL";
    }

    private EnrollmentOrderEntity requireOrder(Long id) {
        EnrollmentOrderEntity order = enrollmentOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("ENROLLMENT_ORDER_NOT_FOUND", "Enrollment order not found.");
        }
        return order;
    }

    private StudentEntity requireStudent(Long studentId) {
        StudentEntity student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException("STUDENT_NOT_FOUND", "Student not found.");
        }
        return student;
    }

    private CourseProductEntity requireCourseProduct(Long courseProductId) {
        CourseProductEntity product = courseProductMapper.selectById(courseProductId);
        if (product == null) {
            throw new BusinessException("COURSE_PRODUCT_NOT_FOUND", "Course product not found.");
        }
        return product;
    }

    private TeacherEntity requireTeacher(Long teacherId) {
        TeacherEntity teacher = teacherMapper.selectById(teacherId);
        if (teacher == null) {
            throw new BusinessException("TEACHER_NOT_FOUND", "Teacher not found.");
        }
        return teacher;
    }

    private void validateOrderItemExists(Long orderItemId) {
        if (orderItemId == null) {
            return;
        }
        if (enrollmentOrderItemMapper.selectById(orderItemId) == null) {
            throw new BusinessException("ENROLLMENT_ORDER_ITEM_NOT_FOUND", "Enrollment order item not found.");
        }
    }

    private void applyAmountSnapshot(EnrollmentOrderEntity order,
                                     BigDecimal tuitionSubtotal,
                                     BigDecimal pickupFee,
                                     BigDecimal extraFee,
                                     BigDecimal discount,
                                     BigDecimal received) {
        BigDecimal subtotal = defaultAmount(tuitionSubtotal);
        BigDecimal pickup = defaultAmount(pickupFee);
        BigDecimal extra = defaultAmount(extraFee);
        BigDecimal discountAmount = defaultAmount(discount);
        BigDecimal receivedAmount = defaultAmount(received);
        BigDecimal amountDue = subtotal.add(pickup).add(extra).subtract(discountAmount);
        if (amountDue.compareTo(BigDecimal.ZERO) < 0) {
            amountDue = ZERO_AMOUNT;
        }
        order.setTuitionSubtotalAmount(subtotal);
        order.setPickupFeeAmount(pickup);
        order.setExtraFeeAmount(extra);
        order.setAmountDue(amountDue);
        order.setBalanceAmount(amountDue.subtract(receivedAmount));
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? ZERO_AMOUNT : amount;
    }

    private Map<Long, StudentEntity> listStudentsByIds(Set<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return studentMapper.selectBatchIds(studentIds)
                .stream()
                .collect(Collectors.toMap(StudentEntity::getId, item -> item));
    }

    private List<CoursePackageEntity> listPackagesByOrderIds(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Collections.emptyList();
        }
        return coursePackageMapper.selectList(new LambdaQueryWrapper<CoursePackageEntity>()
                .in(CoursePackageEntity::getEnrollmentOrderId, orderIds)
                .orderByDesc(CoursePackageEntity::getCreatedAt));
    }

    private List<EnrollmentOrderItemEntity> listOrderItemsByOrderId(Long orderId) {
        return enrollmentOrderItemMapper.selectList(new LambdaQueryWrapper<EnrollmentOrderItemEntity>()
                .eq(EnrollmentOrderItemEntity::getEnrollmentOrderId, orderId)
                .orderByAsc(EnrollmentOrderItemEntity::getLineNo)
                .orderByAsc(EnrollmentOrderItemEntity::getId));
    }

    private List<PaymentRecordEntity> listPaymentsByOrderId(Long orderId) {
        return paymentRecordMapper.selectList(new LambdaQueryWrapper<PaymentRecordEntity>()
                .eq(PaymentRecordEntity::getEnrollmentOrderId, orderId)
                .orderByDesc(PaymentRecordEntity::getPaidAt));
    }

    private List<ReceiptRecordEntity> listReceiptsByOrderIds(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Collections.emptyList();
        }
        return receiptRecordMapper.selectList(new LambdaQueryWrapper<ReceiptRecordEntity>()
                .in(ReceiptRecordEntity::getEnrollmentOrderId, orderIds)
                .orderByDesc(ReceiptRecordEntity::getIssuedAt));
    }

    private Map<Long, String> buildPrimaryTeacherNameMap(List<Long> packageIds) {
        if (packageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PackageTeacherAssignmentEntity> assignments = packageTeacherAssignmentMapper.selectList(new LambdaQueryWrapper<PackageTeacherAssignmentEntity>()
                .in(PackageTeacherAssignmentEntity::getPackageId, packageIds)
                .eq(PackageTeacherAssignmentEntity::getAssignmentStatus, "ACTIVE")
                .eq(PackageTeacherAssignmentEntity::getIsPrimary, 1)
                .orderByDesc(PackageTeacherAssignmentEntity::getEffectiveFrom));
        if (assignments.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, TeacherEntity> teacherMap = teacherMapper.selectBatchIds(assignments.stream().map(PackageTeacherAssignmentEntity::getTeacherId).toList())
                .stream()
                .collect(Collectors.toMap(TeacherEntity::getId, item -> item));
        Map<Long, String> result = new LinkedHashMap<>();
        for (PackageTeacherAssignmentEntity assignment : assignments) {
            if (result.containsKey(assignment.getPackageId())) {
                continue;
            }
            TeacherEntity teacher = teacherMap.get(assignment.getTeacherId());
            result.put(assignment.getPackageId(), teacher == null ? null : teacher.getTeacherName());
        }
        return result;
    }

    private EnrollmentOrderListItemVO toOrderListItem(EnrollmentOrderEntity order,
                                                      StudentEntity student,
                                                      CoursePackageEntity firstPackage,
                                                      CourseProductEntity product,
                                                      int packageCount,
                                                      List<EnrollmentOrderItemVO> items,
                                                      List<EnrollmentOrderParticipantVO> participants) {
        return EnrollmentOrderListItemVO.builder()
                .id(order.getId())
                .enrollmentNo(order.getEnrollmentNo())
                .studentId(order.getStudentId())
                .studentName(student == null ? null : student.getStudentName())
                .courseProductId(firstPackage == null ? null : firstPackage.getCourseProductId())
                .courseProductName(product == null ? null : product.getProductName())
                .campusId(order.getCampusId())
                .campusNameSnapshot(order.getCampusNameSnapshot())
                .handledByName(order.getHandledByName())
                .signupDate(order.getSignupDate())
                .periodStartDate(order.getPeriodStartDate())
                .periodEndDate(order.getPeriodEndDate())
                .studentNameSnapshot(order.getStudentNameSnapshot())
                .studentClassSnapshot(order.getStudentClassSnapshot())
                .gradeSnapshot(order.getGradeSnapshot())
                .schoolSnapshot(order.getSchoolSnapshot())
                .parentPhoneSnapshot(order.getParentPhoneSnapshot())
                .parentPhone2Snapshot(order.getParentPhone2Snapshot())
                .pickupRequiredSnapshot(Boolean.TRUE.equals(order.getPickupRequiredSnapshot()))
                .pickupAddressSnapshot(order.getPickupAddressSnapshot())
                .dropoffRequiredSnapshot(Boolean.TRUE.equals(order.getDropoffRequiredSnapshot()))
                .dropoffAddressSnapshot(order.getDropoffAddressSnapshot())
                .currencyCode(order.getCurrencyCode())
                .tuitionSubtotalAmount(order.getTuitionSubtotalAmount())
                .pickupFeeAmount(order.getPickupFeeAmount())
                .dropoffFeeAmount(order.getDropoffFeeAmount())
                .extraFeeAmount(order.getExtraFeeAmount())
                .originalAmount(order.getOriginalAmount())
                .discountAmount(order.getDiscountAmount())
                .receivedAmount(order.getReceivedAmount())
                .amountDue(order.getAmountDue())
                .balanceAmount(order.getBalanceAmount())
                .paymentStatus(order.getPaymentStatus())
                .orderStatus(order.getOrderStatus())
                .packageCount(packageCount)
                .items(items)
                .participants(participants)
                .createdAt(order.getCreatedAt())
                .build();
    }

    private EnrollmentOrderListItemVO toClassOrderListItem(EnrollmentOrderEntity order,
                                                           StudentEntity student,
                                                           ClassGroupEntity clz,
                                                           List<EnrollmentOrderItemVO> items,
                                                           List<EnrollmentOrderParticipantVO> participants) {
        return EnrollmentOrderListItemVO.builder()
                .id(order.getId())
                .enrollmentNo(order.getEnrollmentNo())
                .studentId(order.getStudentId())
                .studentName(student == null ? null : student.getStudentName())
                .classId(order.getClassId())
                .className(clz == null ? null : clz.getClassName())
                .campusId(order.getCampusId())
                .campusNameSnapshot(order.getCampusNameSnapshot())
                .handledByName(order.getHandledByName())
                .signupDate(order.getSignupDate())
                .periodStartDate(order.getPeriodStartDate())
                .periodEndDate(order.getPeriodEndDate())
                .studentNameSnapshot(order.getStudentNameSnapshot())
                .studentClassSnapshot(order.getStudentClassSnapshot())
                .gradeSnapshot(order.getGradeSnapshot())
                .schoolSnapshot(order.getSchoolSnapshot())
                .parentPhoneSnapshot(order.getParentPhoneSnapshot())
                .parentPhone2Snapshot(order.getParentPhone2Snapshot())
                .pickupRequiredSnapshot(Boolean.TRUE.equals(order.getPickupRequiredSnapshot()))
                .pickupAddressSnapshot(order.getPickupAddressSnapshot())
                .dropoffRequiredSnapshot(Boolean.TRUE.equals(order.getDropoffRequiredSnapshot()))
                .dropoffAddressSnapshot(order.getDropoffAddressSnapshot())
                .currencyCode(order.getCurrencyCode())
                .tuitionSubtotalAmount(order.getTuitionSubtotalAmount())
                .pickupFeeAmount(order.getPickupFeeAmount())
                .dropoffFeeAmount(order.getDropoffFeeAmount())
                .extraFeeAmount(order.getExtraFeeAmount())
                .originalAmount(order.getOriginalAmount())
                .discountAmount(order.getDiscountAmount())
                .receivedAmount(order.getReceivedAmount())
                .amountDue(order.getAmountDue())
                .balanceAmount(order.getBalanceAmount())
                .paymentStatus(order.getPaymentStatus())
                .orderStatus(order.getOrderStatus())
                .packageCount(0)
                .items(items)
                .participants(participants)
                .createdAt(order.getCreatedAt())
                .build();
    }

    private EnrollmentOrderItemVO toOrderItemVO(EnrollmentOrderItemEntity item) {
        return EnrollmentOrderItemVO.builder()
                .id(item.getId())
                .enrollmentOrderId(item.getEnrollmentOrderId())
                .lineNo(item.getLineNo())
                .registerClass(item.getRegisterClass())
                .courseType(item.getCourseType())
                .courseProductId(item.getCourseProductId())
                .coursePriceOptionId(item.getCoursePriceOptionId())
                .classId(item.getClassId())
                .detail(item.getDetail())
                .gradeBand(item.getGradeBand())
                .periodLabel(item.getPeriodLabel())
                .timeSlotLabel(item.getTimeSlotLabel())
                .startDate(item.getStartDate())
                .priceAmount(item.getPriceAmount())
                .originalPriceAmount(item.getOriginalPriceAmount())
                .manualPriceOverride(Boolean.TRUE.equals(item.getManualPriceOverride()))
                .oneOnOneHours(item.getOneOnOneHours())
                .oneOnOneUnits(item.getOneOnOneUnits())
                .itemStatus(item.getItemStatus())
                .remark(item.getRemark())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private CoursePackageListItemVO toPackageListItem(CoursePackageEntity item, StudentEntity student, String primaryTeacherName, CourseProductEntity product) {
        return CoursePackageListItemVO.builder()
                .id(item.getId())
                .packageNo(item.getPackageNo())
                .enrollmentOrderId(item.getEnrollmentOrderId())
                .enrollmentOrderItemId(item.getEnrollmentOrderItemId())
                .studentId(item.getStudentId())
                .studentName(student == null ? null : student.getStudentName())
                .courseProductId(item.getCourseProductId())
                .courseProductName(product == null ? null : product.getProductName())
                .packageName(item.getPackageName())
                .subjectName(item.getSubjectName())
                .teachingMode(item.getTeachingMode())
                .totalUnits(UnitConverter.toHoursDecimal(item.getTotalUnits()))
                .consumedUnits(UnitConverter.toHoursDecimal(item.getConsumedUnits()))
                .remainingUnits(UnitConverter.toHoursDecimal(item.getRemainingUnits()))
                .receivedAmount(item.getReceivedAmount())
                .packageStatus(item.getPackageStatus())
                .startDate(item.getStartDate())
                .estimatedEndDate(item.getEstimatedEndDate())
                .primaryTeacherName(primaryTeacherName)
                .createdAt(item.getCreatedAt())
                .build();
    }

    private PaymentRecordVO toPaymentVO(PaymentRecordEntity item) {
        return PaymentRecordVO.builder()
                .id(item.getId())
                .paymentNo(item.getPaymentNo())
                .participantIndex(item.getParticipantIndex())
                .paymentMethod(item.getPaymentMethod())
                .paymentType(item.getPaymentType())
                .paymentStatus(item.getPaymentStatus())
                .amount(item.getAmount())
                .paidAt(item.getPaidAt())
                .handledByName(item.getHandledByName())
                .remark(item.getRemark())
                .build();
    }

    private ReceiptRecordVO toReceiptVO(ReceiptRecordEntity item, String studentName) {
        return ReceiptRecordVO.builder()
                .id(item.getId())
                .receiptNo(item.getReceiptNo())
                .enrollmentOrderId(item.getEnrollmentOrderId())
                .studentId(item.getStudentId())
                .studentName(studentName)
                .templateCode(item.getTemplateCode())
                .receiptPayload(item.getReceiptPayload())
                .fileUrl(item.getFileUrl())
                .issuedAt(item.getIssuedAt())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(Long id, EnrollmentOrderUpdateRequest request) {
        EnrollmentOrderEntity order = requireOrder(id);

        // Order status must never be changed through the generic update:
        // cancellation goes through cancelOrder (paid-order guard, package close,
        // future-lesson cancel, class eligibility revoke) and refunds through refundOrder.
        if (StringUtils.hasText(request.getOrderStatus())
                && !request.getOrderStatus().equals(order.getOrderStatus())) {
            throw new BusinessException("ORDER_STATUS_UPDATE_NOT_ALLOWED",
                    "Order status cannot be changed here. Use cancel or refund instead.");
        }

        if (request.getCashierUserId() != null) {
            order.setCashierUserId(request.getCashierUserId());
        }

        if (request.getRemark() != null) {
            order.setRemark(request.getRemark());
        }

        enrollmentOrderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void correctOrder(Long id, EnrollmentOrderCorrectionRequest request) {
        EnrollmentOrderEntity order = requireOrder(id);
        if ("CANCELLED".equals(order.getOrderStatus())) {
            throw new BusinessException("ORDER_CANCELLED_NOT_EDITABLE",
                    "Cancelled orders cannot be edited.");
        }

        StudentEntity student = requireStudent(order.getStudentId());
        if (StringUtils.hasText(request.getStudentName())) {
            order.setStudentNameSnapshot(request.getStudentName().trim());
            student.setStudentName(request.getStudentName().trim());
        }
        if (request.getGrade() != null) {
            order.setGradeSnapshot(request.getGrade().trim());
            student.setGrade(request.getGrade().trim());
        }
        if (request.getStudentClass() != null) {
            order.setStudentClassSnapshot(request.getStudentClass().trim());
            student.setStudentClass(request.getStudentClass().trim());
        }
        if (request.getSchool() != null) {
            order.setSchoolSnapshot(request.getSchool().trim());
            student.setSchool(request.getSchool().trim());
        }
        if (request.getGuardianPhone() != null) {
            order.setParentPhoneSnapshot(request.getGuardianPhone().trim());
            student.setGuardianPhone(request.getGuardianPhone().trim());
        }
        if (request.getGuardianPhone2() != null) {
            order.setParentPhone2Snapshot(request.getGuardianPhone2().trim());
            student.setGuardianPhone2(request.getGuardianPhone2().trim());
        }
        boolean campusChanged = request.getCampusName() != null;
        if (campusChanged) {
            order.setCampusNameSnapshot(request.getCampusName().trim());
            order.setCampusId(request.getCampusId());
        }
        if (request.getPickupRequired() != null) {
            order.setPickupRequiredSnapshot(request.getPickupRequired());
            student.setPickupRequired(request.getPickupRequired());
        }
        if (request.getPickupAddress() != null) {
            order.setPickupAddressSnapshot(request.getPickupAddress().trim());
            student.setPickupAddress(request.getPickupAddress().trim());
        }
        if (request.getDropoffRequired() != null) {
            order.setDropoffRequiredSnapshot(request.getDropoffRequired());
            student.setDropoffRequired(request.getDropoffRequired());
        }
        if (request.getDropoffAddress() != null) {
            order.setDropoffAddressSnapshot(request.getDropoffAddress().trim());
            student.setDropoffAddress(request.getDropoffAddress().trim());
        }
        if (request.getSignupDate() != null) {
            order.setSignupDate(request.getSignupDate());
        }
        if (request.getHandledByName() != null) {
            order.setHandledByName(request.getHandledByName().trim());
        }
        if (request.getRemark() != null) {
            order.setRemark(request.getRemark());
        }

        // 课程、价格、课包和班级报名必须跟原订单行一起更正，不能重新创建报名单。
        if (request.getParticipants() != null && !request.getParticipants().isEmpty()) {
            applyCorrectedParticipants(order, request.getParticipants());
        }
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            applyCorrectedItems(order, student, request.getItems());
        }
        if (request.getParticipantBillings() != null && !request.getParticipantBillings().isEmpty()) {
            reconcileCorrectedParticipantPayments(order, request.getParticipantBillings());
            applyCorrectedParticipantBillingSnapshots(order, request.getParticipantBillings());
        } else if (request.getPayments() != null) {
            reconcileCorrectedPayments(order, request.getPayments());
        }
        if (request.getItems() != null || request.getPayments() != null
                || request.getParticipantBillings() != null
                || request.getExtraFees() != null || request.getDiscounts() != null
                || request.getPickupFeeAmount() != null || request.getDropoffFeeAmount() != null) {
            applyCorrectedMoney(order, request);
        }
        studentMapper.updateById(student);
        enrollmentOrderMapper.updateById(order);
        // updateById skips null fields, so a campus switch to a no-id campus (e.g. Online)
        // would keep the stale campus_id. Apply the campus snapshot explicitly so it can be nulled.
        if (campusChanged) {
            enrollmentOrderMapper.update(null, new LambdaUpdateWrapper<EnrollmentOrderEntity>()
                    .eq(EnrollmentOrderEntity::getId, order.getId())
                    .set(EnrollmentOrderEntity::getCampusNameSnapshot, order.getCampusNameSnapshot())
                    .set(EnrollmentOrderEntity::getCampusId, order.getCampusId()));
        }

        // Keep every saved receipt snapshot in sync so print/preview and the order detail agree.
        syncReceiptSnapshots(order, request.getCampusAddress(), request.getCampusPhone(), request.getTaxId(), request);
    }

    /**
     * 更新共享私教的独立学生资料。这里不改变成员关系或课包归属，避免编辑收据时意外创建第二个学生。
     */
    private void applyCorrectedParticipants(EnrollmentOrderEntity order,
                                            List<RegistrationIntakeRequest.StudentPayload> payloads) {
        List<EnrollmentOrderParticipantEntity> stored = enrollmentOrderParticipantMapper.selectList(
                new LambdaQueryWrapper<EnrollmentOrderParticipantEntity>()
                        .eq(EnrollmentOrderParticipantEntity::getEnrollmentOrderId, order.getId())
                        .eq(EnrollmentOrderParticipantEntity::getIsDeleted, 0)
                        .orderByAsc(EnrollmentOrderParticipantEntity::getParticipantIndex));
        if (stored.isEmpty() || stored.size() != payloads.size()) {
            throw new BusinessException("PRIVATE_LESSON_PARTICIPANTS_MISMATCH",
                    "The saved private lesson participants cannot be changed from this receipt.");
        }
        for (int index = 0; index < payloads.size(); index++) {
            RegistrationIntakeRequest.StudentPayload payload = payloads.get(index);
            EnrollmentOrderParticipantEntity participant = stored.get(index);
            if (payload == null || !Objects.equals(payload.getStudentId(), participant.getStudentId())) {
                throw new BusinessException("PRIVATE_LESSON_PARTICIPANT_ID_MISMATCH",
                        "A receipt edit cannot replace a private lesson participant.");
            }
            StudentEntity student = requireStudent(participant.getStudentId());
            applyCorrectedStudentProfile(student, payload);
            studentMapper.updateById(student);
            copyParticipantSnapshot(participant, student);
            enrollmentOrderParticipantMapper.updateById(participant);
            if (index == 0) {
                order.setStudentNameSnapshot(student.getStudentName());
                order.setStudentClassSnapshot(student.getStudentClass());
                order.setGradeSnapshot(student.getGrade());
                order.setSchoolSnapshot(student.getSchool());
                order.setParentPhoneSnapshot(student.getGuardianPhone());
                order.setParentPhone2Snapshot(student.getGuardianPhone2());
                order.setPickupRequiredSnapshot(student.getPickupRequired());
                order.setPickupAddressSnapshot(student.getPickupAddress());
                order.setDropoffRequiredSnapshot(student.getDropoffRequired());
                order.setDropoffAddressSnapshot(student.getDropoffAddress());
            }
        }
    }

    private void applyCorrectedStudentProfile(StudentEntity student,
                                              RegistrationIntakeRequest.StudentPayload payload) {
        if (payload.getStudentName() != null) student.setStudentName(payload.getStudentName().trim());
        if (payload.getEnglishName() != null) student.setEnglishName(payload.getEnglishName());
        if (payload.getGender() != null) student.setGender(payload.getGender());
        if (payload.getBirthDate() != null) student.setBirthDate(payload.getBirthDate());
        if (payload.getGrade() != null) student.setGrade(payload.getGrade());
        if (payload.getStudentClass() != null) student.setStudentClass(payload.getStudentClass());
        if (payload.getSchool() != null) student.setSchool(payload.getSchool());
        if (payload.getGuardianName() != null) student.setGuardianName(payload.getGuardianName());
        if (payload.getGuardianPhone() != null) student.setGuardianPhone(payload.getGuardianPhone());
        if (payload.getGuardianPhone2() != null) student.setGuardianPhone2(payload.getGuardianPhone2());
        if (payload.getGuardianEmail() != null) student.setGuardianEmail(payload.getGuardianEmail());
        if (payload.getGuardianRelationType() != null) student.setGuardianRelationType(payload.getGuardianRelationType());
        if (payload.getPickupRequired() != null) student.setPickupRequired(payload.getPickupRequired());
        if (payload.getPickupAddress() != null) student.setPickupAddress(payload.getPickupAddress());
        if (payload.getDropoffRequired() != null) student.setDropoffRequired(payload.getDropoffRequired());
        if (payload.getDropoffAddress() != null) student.setDropoffAddress(payload.getDropoffAddress());
    }

    private void copyParticipantSnapshot(EnrollmentOrderParticipantEntity target, StudentEntity student) {
        target.setStudentNameSnapshot(student.getStudentName());
        target.setStudentNoSnapshot(student.getStudentNo());
        target.setGradeSnapshot(student.getGrade());
        target.setSchoolSnapshot(student.getSchool());
        target.setStudentClassSnapshot(student.getStudentClass());
        target.setGuardianNameSnapshot(student.getGuardianName());
        target.setGuardianPhoneSnapshot(student.getGuardianPhone());
        target.setGuardianPhone2Snapshot(student.getGuardianPhone2());
        target.setGuardianEmailSnapshot(student.getGuardianEmail());
        target.setPickupRequiredSnapshot(student.getPickupRequired());
        target.setPickupAddressSnapshot(student.getPickupAddress());
        target.setDropoffRequiredSnapshot(student.getDropoffRequired());
        target.setDropoffAddressSnapshot(student.getDropoffAddress());
    }

    private void reconcileCorrectedParticipantPayments(EnrollmentOrderEntity order,
                                                       List<RegistrationIntakeRequest.ParticipantBillingPayload> billings) {
        List<PaymentRecordEntity> existing = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecordEntity>()
                        .eq(PaymentRecordEntity::getEnrollmentOrderId, order.getId())
                        .eq(PaymentRecordEntity::getPaymentType, "PAYMENT")
                        .orderByAsc(PaymentRecordEntity::getPaidAt)
                        .orderByAsc(PaymentRecordEntity::getId));
        for (RegistrationIntakeRequest.ParticipantBillingPayload billing : billings) {
            if (billing == null || billing.getParticipantIndex() == null) continue;
            List<PaymentRecordEntity> participantPayments = existing.stream()
                    .filter(item -> Objects.equals(item.getParticipantIndex(), billing.getParticipantIndex()))
                    .toList();
            List<RegistrationIntakeRequest.PaymentPayload> payloads = billing.getPayments() == null
                    ? Collections.emptyList() : billing.getPayments();
            for (int i = 0; i < Math.max(participantPayments.size(), payloads.size()); i++) {
                if (i < payloads.size()) {
                    RegistrationIntakeRequest.PaymentPayload payload = payloads.get(i);
                    PaymentRecordEntity payment = i < participantPayments.size()
                            ? participantPayments.get(i) : new PaymentRecordEntity();
                    if (payment.getId() == null) {
                        payment.setEnrollmentOrderId(order.getId());
                        payment.setPaymentNo(BusinessNoGenerator.generatePaymentNo());
                        payment.setPaymentType("PAYMENT");
                    }
                    payment.setParticipantIndex(billing.getParticipantIndex());
                    payment.setPaymentMethod(payload.getPaymentMethod());
                    payment.setPaymentStatus("PAID");
                    payment.setAmount(defaultAmount(payload.getAmount()));
                    payment.setPaidAt(payload.getPaidAt() == null ? AppTime.nowLocalDateTime() : payload.getPaidAt());
                    payment.setHandledByUserId(payload.getHandledByUserId());
                    payment.setHandledByName(payload.getHandledByName());
                    payment.setRemark(payload.getRemark());
                    if (payment.getId() == null) paymentRecordMapper.insert(payment);
                    else paymentRecordMapper.updateById(payment);
                } else {
                    PaymentRecordEntity payment = participantPayments.get(i);
                    payment.setPaymentStatus("VOIDED");
                    payment.setAmount(ZERO_AMOUNT);
                    paymentRecordMapper.updateById(payment);
                }
            }
        }
    }

    private void applyCorrectedParticipantBillingSnapshots(
            EnrollmentOrderEntity order,
            List<RegistrationIntakeRequest.ParticipantBillingPayload> billings) {
        List<EnrollmentOrderParticipantEntity> participants = enrollmentOrderParticipantMapper.selectList(
                new LambdaQueryWrapper<EnrollmentOrderParticipantEntity>()
                        .eq(EnrollmentOrderParticipantEntity::getEnrollmentOrderId, order.getId())
                        .eq(EnrollmentOrderParticipantEntity::getIsDeleted, 0));
        BigDecimal sharedFees = defaultAmount(order.getPickupFeeAmount())
                .add(defaultAmount(order.getDropoffFeeAmount()))
                .add(defaultAmount(order.getExtraFeeAmount()));
        for (EnrollmentOrderParticipantEntity participant : participants) {
            RegistrationIntakeRequest.ParticipantBillingPayload billing = billings.stream()
                    .filter(item -> item != null && Objects.equals(item.getParticipantIndex(), participant.getParticipantIndex()))
                    .findFirst().orElse(null);
            if (billing == null) continue;
            BigDecimal discount = defaultAmount(billing.getDiscountAmount() == null
                    ? sumFeeLines(billing.getDiscounts()) : billing.getDiscountAmount());
            BigDecimal paid = billing.getPayments() == null ? ZERO_AMOUNT : billing.getPayments().stream()
                    .map(RegistrationIntakeRequest.PaymentPayload::getAmount)
                    .map(this::defaultAmount)
                    .reduce(ZERO_AMOUNT, BigDecimal::add);
            BigDecimal fees = Objects.equals(participant.getParticipantIndex(), 1) ? sharedFees : ZERO_AMOUNT;
            BigDecimal due = defaultAmount(participant.getGrossAmount()).add(fees).subtract(discount)
                    .max(ZERO_AMOUNT).setScale(2, RoundingMode.HALF_UP);
            participant.setDiscountAmount(discount);
            participant.setDiscountDetail(jsonHelper.toJson(billing.getDiscounts()));
            participant.setAmountDue(due);
            participant.setPaidAmount(paid);
            participant.setBalanceAmount(due.subtract(paid).setScale(2, RoundingMode.HALF_UP));
            enrollmentOrderParticipantMapper.updateById(participant);
        }
    }

    /**
     * 将原订单项更新到新的课程/班级，并迁移班课在册关系。历史课次不改写，未来课次通过新的
     * class_enrollment 关系自然进入新班级；1 对 1 课包只调整总量，已消耗课时永远保留。
     */
    private void applyCorrectedItems(EnrollmentOrderEntity order,
                                     StudentEntity student,
                                     List<RegistrationIntakeRequest.CourseItemPayload> payloads) {
        List<EnrollmentOrderItemEntity> existingItems = listOrderItemsByOrderId(order.getId());
        Map<Long, EnrollmentOrderItemEntity> itemMap = existingItems.stream()
                .collect(Collectors.toMap(EnrollmentOrderItemEntity::getId, item -> item));
        for (RegistrationIntakeRequest.CourseItemPayload payload : payloads) {
            if (payload.getOrderItemId() == null) {
                throw new BusinessException("CORRECTION_ORDER_ITEM_REQUIRED", "Each edited course row must identify its original order item.");
            }
            EnrollmentOrderItemEntity item = itemMap.get(payload.getOrderItemId());
            if (item == null) {
                throw new BusinessException("CORRECTION_ORDER_ITEM_NOT_FOUND", "Edited course row does not belong to this order.");
            }
            Long oldClassId = item.getClassId();
            copyCorrectionItem(item, payload);
            enrollmentOrderItemMapper.updateById(item);
            if (isGroupItem(item)) {
                migrateCorrectedClassEnrollment(order, student, item, oldClassId);
            } else {
                updateCorrectedOneOnOnePackage(order, item);
            }
        }
        order.setClassId(resolveCorrectedOrderClassId(existingItems));
    }

    private void copyCorrectionItem(EnrollmentOrderItemEntity item,
                                     RegistrationIntakeRequest.CourseItemPayload payload) {
        item.setLineNo(payload.getLineNo() == null ? item.getLineNo() : payload.getLineNo());
        item.setRegisterClass(payload.getRegisterClass());
        item.setCourseType(payload.getCourseType());
        item.setCourseProductId(payload.getCourseProductId());
        item.setCoursePriceOptionId(payload.getCoursePriceOptionId());
        item.setClassId(payload.getClassId());
        item.setDetail(payload.getDetail());
        item.setGradeBand(payload.getGradeBand());
        item.setPeriodLabel(payload.getPeriodLabel());
        item.setTimeSlotLabel(payload.getTimeSlotLabel());
        item.setStartDate(payload.getStartDate());
        item.setPackageUnit(payload.getPackageUnit());
        item.setPackageQuantity(payload.getPackageQuantity());
        item.setPackageTotalPrice(payload.getPackageTotalPrice());
        item.setAttendanceOption(payload.getAttendanceOption());
        item.setAttendanceScheduleJson(payload.getAttendanceScheduleJson());
        item.setPriceAmount(defaultAmount(payload.getPriceAmount()));
        item.setOriginalPriceAmount(defaultAmount(payload.getOriginalPriceAmount()));
        item.setManualPriceOverride(payload.getManualPriceOverride());
        item.setOneOnOneHours(payload.getOneOnOneHours());
        item.setOpeningUsedHours(payload.getOpeningUsedHours());
        item.setOneOnOneUnits(payload.getOneOnOneUnits());
        item.setRemark(payload.getRemark());
    }

    private boolean isGroupItem(EnrollmentOrderItemEntity item) {
        return !"ONE_ON_ONE".equalsIgnoreCase(item.getCourseType());
    }

    private void migrateCorrectedClassEnrollment(EnrollmentOrderEntity order,
                                                 StudentEntity student,
                                                 EnrollmentOrderItemEntity item,
                                                 Long oldClassId) {
        ClassGroupEntity targetClass = item.getClassId() == null ? null : classGroupMapper.selectById(item.getClassId());
        if (targetClass != null && (Integer.valueOf(1).equals(targetClass.getIsDeleted()) || !"ACTIVE".equals(targetClass.getStatus()))) {
            throw new BusinessException("CORRECTION_CLASS_INVALID", "The selected class is no longer active.");
        }
        if (targetClass != null && item.getCourseProductId() != null
                && !Objects.equals(targetClass.getProductId(), item.getCourseProductId())) {
            throw new BusinessException("CORRECTION_CLASS_PRODUCT_MISMATCH", "The selected class does not belong to the selected course.");
        }
        if (targetClass != null && StringUtils.hasText(item.getGradeBand()) && StringUtils.hasText(targetClass.getGradeBand())
                && !item.getGradeBand().trim().equalsIgnoreCase(targetClass.getGradeBand().trim())) {
            throw new BusinessException("CORRECTION_CLASS_GRADE_MISMATCH", "The selected class does not belong to the selected grade.");
        }
        List<ClassEnrollmentEntity> enrollments = classEnrollmentMapper.selectList(
                new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getEnrollmentOrderItemId, item.getId())
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0));
        ClassEnrollmentEntity current = enrollments.stream()
                .filter(enrollment -> "ACTIVE".equals(enrollment.getStatus()))
                .findFirst().orElse(null);
        if (Objects.equals(oldClassId, item.getClassId()) && current != null) {
            // 班级未变：以原在册起始日（或更正后的 start）为基线重算有效期，
            // 保证购买数量等字段调整后 expiry_date 同步刷新。
            LocalDate baseline = item.getStartDate() == null ? current.getEnrollmentDate() : item.getStartDate();
            ClassBillingService.ClassBilling billing = computeCorrectedClassBilling(targetClass, baseline, item);
            current.setEnrollmentDate(billing.enrollmentDate());
            current.setExpiryDate(billing.expiryDate());
            current.setBillingType(item.getPackageUnit());
            current.setPurchasedQuantity(billing.purchasedQuantity());
            current.setAttendanceOption(item.getAttendanceOption());
            classEnrollmentMapper.updateById(current);
            return;
        }
        for (ClassEnrollmentEntity enrollment : enrollments) {
            if ("ACTIVE".equals(enrollment.getStatus())) {
                enrollment.setStatus("CANCELLED");
                classEnrollmentMapper.updateById(enrollment);
            }
        }
        if (targetClass == null) {
            return;
        }
        ClassEnrollmentEntity replacement = new ClassEnrollmentEntity();
        // 换班/换年级：按新班级同一套计费规则重建在册记录，expiry_date 取自计算结果而非订单/班级的冗余字段。
        ClassBillingService.ClassBilling billing = computeCorrectedClassBilling(targetClass,
                item.getStartDate() == null ? order.getSignupDate() : item.getStartDate(), item);
        replacement.setClassId(targetClass.getId());
        replacement.setStudentId(student.getId());
        replacement.setEnrollmentOrderItemId(item.getId());
        replacement.setEnrollmentDate(billing.enrollmentDate());
        replacement.setExpiryDate(billing.expiryDate());
        replacement.setBillingType(item.getPackageUnit());
        replacement.setPurchasedQuantity(billing.purchasedQuantity());
        replacement.setAttendanceOption(item.getAttendanceOption());
        replacement.setStatus("ACTIVE");
        classEnrollmentMapper.insert(replacement);
    }

    /**
     * 更正后的班课在册有效期：与报名新增链路共用同一套计费规则（ClassBillingService），
     * 保证 expiry_date 口径一致且永不为空。
     *
     * @param targetClass 新班级
     * @param baseline    起始日期：item 更正后的 start；为空时由调用方回退（换班用 signupDate，原班保留原在册起始日）
     * @param item        更正后的订单课程项
     */
    private ClassBillingService.ClassBilling computeCorrectedClassBilling(ClassGroupEntity targetClass,
                                                                          LocalDate baseline,
                                                                          EnrollmentOrderItemEntity item) {
        return classBillingService.compute(targetClass, baseline, null,
                item.getPackageUnit(), item.getPackageQuantity());
    }

    private void updateCorrectedOneOnOnePackage(EnrollmentOrderEntity order, EnrollmentOrderItemEntity item) {
        CoursePackageEntity coursePackage = coursePackageMapper.selectOne(new LambdaQueryWrapper<CoursePackageEntity>()
                .eq(CoursePackageEntity::getEnrollmentOrderItemId, item.getId())
                .eq(CoursePackageEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (coursePackage == null) {
            return;
        }
        int consumed = safeInt(coursePackage.getConsumedUnits());
        int requested = item.getOneOnOneUnits() == null
                ? Math.max(1, item.getOneOnOneHours() == null ? coursePackage.getTotalUnits() : item.getOneOnOneHours().multiply(BigDecimal.valueOf(2)).intValue())
                : item.getOneOnOneUnits();
        if (requested < consumed) {
            throw new BusinessException("CORRECTION_HOURS_BELOW_CONSUMED", "Package hours cannot be lower than hours already used.");
        }
        coursePackage.setCourseProductId(item.getCourseProductId());
        coursePackage.setPackageName(StringUtils.hasText(item.getDetail()) ? item.getDetail() : coursePackage.getPackageName());
        coursePackage.setTotalUnits(requested);
        coursePackage.setRemainingUnits(requested - consumed);
        coursePackage.setOriginalAmount(defaultAmount(item.getPriceAmount()));
        coursePackage.setStartDate(item.getStartDate() == null ? coursePackage.getStartDate() : item.getStartDate());
        coursePackageMapper.updateById(coursePackage);
    }

    private Long resolveCorrectedOrderClassId(List<EnrollmentOrderItemEntity> items) {
        List<Long> classIds = items.stream().map(EnrollmentOrderItemEntity::getClassId)
                .filter(Objects::nonNull).distinct().toList();
        return classIds.size() == 1 ? classIds.get(0) : null;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private void reconcileCorrectedPayments(EnrollmentOrderEntity order,
                                            List<RegistrationIntakeRequest.PaymentPayload> payloads) {
        List<PaymentRecordEntity> existing = paymentRecordMapper.selectList(new LambdaQueryWrapper<PaymentRecordEntity>()
                .eq(PaymentRecordEntity::getEnrollmentOrderId, order.getId())
                .eq(PaymentRecordEntity::getPaymentType, "PAYMENT")
                .orderByAsc(PaymentRecordEntity::getPaidAt)
                .orderByAsc(PaymentRecordEntity::getId));
        for (int i = 0; i < Math.max(existing.size(), payloads.size()); i++) {
            if (i < payloads.size()) {
                RegistrationIntakeRequest.PaymentPayload payload = payloads.get(i);
                PaymentRecordEntity payment = i < existing.size() ? existing.get(i) : new PaymentRecordEntity();
                if (payment.getId() == null) {
                    payment.setEnrollmentOrderId(order.getId());
                    payment.setPaymentNo(BusinessNoGenerator.generatePaymentNo());
                    payment.setPaymentType("PAYMENT");
                }
                payment.setPaymentMethod(payload.getPaymentMethod());
                payment.setPaymentStatus("PAID");
                payment.setAmount(defaultAmount(payload.getAmount()));
                payment.setPaidAt(payload.getPaidAt() == null ? AppTime.nowLocalDateTime() : payload.getPaidAt());
                payment.setHandledByUserId(payload.getHandledByUserId());
                payment.setHandledByName(payload.getHandledByName());
                payment.setRemark(payload.getRemark());
                if (payment.getId() == null) paymentRecordMapper.insert(payment); else paymentRecordMapper.updateById(payment);
            } else {
                PaymentRecordEntity payment = existing.get(i);
                payment.setPaymentStatus("VOIDED");
                payment.setAmount(ZERO_AMOUNT);
                paymentRecordMapper.updateById(payment);
            }
        }
    }

    private void applyCorrectedMoney(EnrollmentOrderEntity order, EnrollmentOrderCorrectionRequest request) {
        BigDecimal tuition = request.getItems() == null ? defaultAmount(order.getTuitionSubtotalAmount()) : request.getItems().stream()
                .map(item -> defaultAmount(item.getPriceAmount())).reduce(ZERO_AMOUNT, BigDecimal::add);
        BigDecimal pickup = Boolean.TRUE.equals(request.getPickupFeeCharged()) ? defaultAmount(request.getPickupFeeAmount()) : ZERO_AMOUNT;
        BigDecimal dropoff = Boolean.TRUE.equals(request.getDropoffFeeCharged()) ? defaultAmount(request.getDropoffFeeAmount()) : ZERO_AMOUNT;
        BigDecimal extra = request.getExtraFeeAmount() != null ? defaultAmount(request.getExtraFeeAmount()) : sumFeeLines(request.getExtraFees());
        BigDecimal discount = request.getParticipantBillings() != null && !request.getParticipantBillings().isEmpty()
                ? request.getParticipantBillings().stream()
                .filter(Objects::nonNull)
                .map(RegistrationIntakeRequest.ParticipantBillingPayload::getDiscountAmount)
                .map(this::defaultAmount)
                .reduce(ZERO_AMOUNT, BigDecimal::add)
                : request.getDiscountAmount() != null ? defaultAmount(request.getDiscountAmount()) : sumFeeLines(request.getDiscounts());
        BigDecimal original = tuition.add(pickup).add(dropoff).add(extra).setScale(2, RoundingMode.HALF_UP);
        if (discount.compareTo(original) > 0) throw new BusinessException("DISCOUNT_EXCEEDS_AMOUNT", "Discount cannot exceed original amount.");
        BigDecimal received = request.getParticipantBillings() != null && !request.getParticipantBillings().isEmpty()
                ? request.getParticipantBillings().stream()
                .filter(Objects::nonNull)
                .flatMap(item -> item.getPayments() == null ? java.util.stream.Stream.empty() : item.getPayments().stream())
                .map(RegistrationIntakeRequest.PaymentPayload::getAmount)
                .map(this::defaultAmount)
                .reduce(ZERO_AMOUNT, BigDecimal::add)
                : request.getPayments() == null ? defaultAmount(order.getReceivedAmount()) : request.getPayments().stream()
                .map(RegistrationIntakeRequest.PaymentPayload::getAmount).map(this::defaultAmount).reduce(ZERO_AMOUNT, BigDecimal::add);
        BigDecimal due = original.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        if (received.compareTo(due) > 0) {
            throw new BusinessException("PAYMENT_EXCEEDS_AMOUNT_DUE", "Paid amount cannot exceed amount due.");
        }
        order.setTuitionSubtotalAmount(tuition);
        order.setPickupFeeAmount(pickup);
        order.setDropoffFeeAmount(dropoff);
        order.setExtraFeeAmount(extra);
        order.setExtraFeeDetail(jsonHelper.toJson(request.getExtraFees()));
        order.setOriginalAmount(original);
        order.setDiscountAmount(discount);
        order.setDiscountDetail(jsonHelper.toJson(request.getDiscounts()));
        order.setReceivedAmount(received);
        order.setAmountDue(due);
        order.setBalanceAmount(due.subtract(received));
        order.setPaymentStatus(resolvePaymentStatus(original, discount, received));
    }

    private BigDecimal sumFeeLines(List<RegistrationIntakeRequest.FeeLinePayload> lines) {
        return lines == null ? ZERO_AMOUNT : lines.stream().map(RegistrationIntakeRequest.FeeLinePayload::getAmount)
                .map(this::defaultAmount).reduce(ZERO_AMOUNT, BigDecimal::add);
    }

    /**
     * Re-write the matching keys inside each stored receipt payload for this order so
     * corrected contact info is reflected on the saved/printable receipt.
     */
    private void syncReceiptSnapshots(EnrollmentOrderEntity order,
                                      String campusAddress,
                                      String campusPhone,
                                      String taxId,
                                      EnrollmentOrderCorrectionRequest request) {
        List<ReceiptRecordEntity> receipts = receiptRecordMapper.selectList(
                new LambdaQueryWrapper<ReceiptRecordEntity>()
                        .eq(ReceiptRecordEntity::getEnrollmentOrderId, order.getId()));
        for (ReceiptRecordEntity receipt : receipts) {
            Map<String, Object> payload = jsonHelper.fromJson(
                    receipt.getReceiptPayload(),
                    new TypeReference<LinkedHashMap<String, Object>>() {});
            if (payload == null) {
                continue;
            }
            payload.put("studentName", order.getStudentNameSnapshot());
            payload.put("studentClass", order.getStudentClassSnapshot());
            payload.put("grade", order.getGradeSnapshot());
            payload.put("school", order.getSchoolSnapshot());
            payload.put("guardianPhone", order.getParentPhoneSnapshot());
            payload.put("guardianPhone2", order.getParentPhone2Snapshot());
            payload.put("campusId", order.getCampusId());
            payload.put("campusName", order.getCampusNameSnapshot());
            if (campusAddress != null) {
                payload.put("campusAddress", campusAddress);
            }
            if (campusPhone != null) {
                payload.put("campusPhone", campusPhone);
            }
            if (taxId != null) {
                payload.put("taxId", taxId);
            }
            payload.put("pickupRequired", order.getPickupRequiredSnapshot());
            payload.put("pickupAddress", order.getPickupAddressSnapshot());
            payload.put("dropoffRequired", order.getDropoffRequiredSnapshot());
            payload.put("dropoffAddress", order.getDropoffAddressSnapshot());
            payload.put("signupDate", order.getSignupDate());
            payload.put("handledByName", order.getHandledByName());
            payload.put("registrationRemark", order.getRemark());
            if (request.getItems() != null) {
                payload.put("items", request.getItems());
            }
            if (request.getExtraFees() != null) {
                payload.put("extraFees", request.getExtraFees());
            }
            if (request.getDiscounts() != null) {
                payload.put("discounts", request.getDiscounts());
            }
            if (request.getPayments() != null) {
                payload.put("payments", request.getPayments());
            }
            payload.put("participants", receiptParticipantPayloads(order.getId()));
            payload.put("payments", receiptPaymentPayloads(order.getId()));
            payload.put("pickupFeeCharged", request.getPickupFeeCharged());
            payload.put("pickupFeeAmount", order.getPickupFeeAmount());
            payload.put("dropoffFeeCharged", request.getDropoffFeeCharged());
            payload.put("dropoffFeeAmount", order.getDropoffFeeAmount());
            payload.put("tuitionSubtotalAmount", order.getTuitionSubtotalAmount());
            payload.put("extraFeeAmount", order.getExtraFeeAmount());
            payload.put("originalAmount", order.getOriginalAmount());
            payload.put("discountAmount", order.getDiscountAmount());
            payload.put("amountDue", order.getAmountDue());
            payload.put("paidAmount", order.getReceivedAmount());
            payload.put("balanceAmount", order.getBalanceAmount());
            payload.put("paymentStatus", order.getPaymentStatus());
            if (request.getReceiptOptions() != null) {
                payload.put("showTaxId", request.getReceiptOptions().getShowTaxId());
                payload.put("taxId", request.getReceiptOptions().getTaxId());
                payload.put("campusAddress", request.getReceiptOptions().getCampusAddress());
                payload.put("campusPhone", request.getReceiptOptions().getCampusPhone());
                payload.put("brandName", request.getReceiptOptions().getBrandName());
                payload.put("logoUrl", request.getReceiptOptions().getLogoUrl());
                payload.put("zelleQrCodeUrl", request.getReceiptOptions().getZelleQrCodeUrl());
                payload.put("policyText", request.getReceiptOptions().getPolicyText());
            }
            receipt.setReceiptPayload(jsonHelper.toJson(payload));
            receiptRecordMapper.updateById(receipt);
        }
    }

    private List<Map<String, Object>> receiptParticipantPayloads(Long orderId) {
        List<EnrollmentOrderParticipantEntity> participants = enrollmentOrderParticipantMapper.selectList(
                        new LambdaQueryWrapper<EnrollmentOrderParticipantEntity>()
                                .eq(EnrollmentOrderParticipantEntity::getEnrollmentOrderId, orderId)
                                .eq(EnrollmentOrderParticipantEntity::getIsDeleted, 0)
                                .orderByAsc(EnrollmentOrderParticipantEntity::getParticipantIndex));
        if (participants == null) {
            participants = Collections.emptyList();
        }
        return participants.stream().map(item -> {
                    Map<String, Object> participant = new LinkedHashMap<>();
                    participant.put("studentId", item.getStudentId());
                    participant.put("studentNo", item.getStudentNoSnapshot());
                    participant.put("studentName", item.getStudentNameSnapshot());
                    participant.put("participantIndex", item.getParticipantIndex());
                    participant.put("grade", item.getGradeSnapshot());
                    participant.put("school", item.getSchoolSnapshot());
                    participant.put("studentClass", item.getStudentClassSnapshot());
                    participant.put("guardianName", item.getGuardianNameSnapshot());
                    participant.put("guardianPhone", item.getGuardianPhoneSnapshot());
                    participant.put("guardianPhone2", item.getGuardianPhone2Snapshot());
                    participant.put("guardianEmail", item.getGuardianEmailSnapshot());
                    participant.put("pickupRequired", item.getPickupRequiredSnapshot());
                    participant.put("pickupAddress", item.getPickupAddressSnapshot());
                    participant.put("dropoffRequired", item.getDropoffRequiredSnapshot());
                    participant.put("dropoffAddress", item.getDropoffAddressSnapshot());
                    participant.put("grossAmount", item.getGrossAmount());
                    participant.put("priceAmount", item.getGrossAmount());
                    participant.put("discountAmount", item.getDiscountAmount());
                    participant.put("discounts", item.getDiscountDetail() == null ? Collections.emptyList()
                            : jsonHelper.fromJson(item.getDiscountDetail(), new TypeReference<List<Map<String, Object>>>() {}));
                    participant.put("amountDue", item.getAmountDue());
                    participant.put("paidAmount", item.getPaidAmount());
                    participant.put("balanceAmount", item.getBalanceAmount());
                    return participant;
                }).toList();
    }

    private List<Map<String, Object>> receiptPaymentPayloads(Long orderId) {
        List<PaymentRecordEntity> payments = paymentRecordMapper.selectList(new LambdaQueryWrapper<PaymentRecordEntity>()
                        .eq(PaymentRecordEntity::getEnrollmentOrderId, orderId)
                        .orderByAsc(PaymentRecordEntity::getPaidAt)
                        .orderByAsc(PaymentRecordEntity::getId));
        if (payments == null) {
            payments = Collections.emptyList();
        }
        return payments.stream()
                .filter(item -> item.getAmount() != null && item.getAmount().compareTo(ZERO_AMOUNT) != 0
                        && !"VOIDED".equals(item.getPaymentStatus()))
                .map(item -> {
                    Map<String, Object> payment = new LinkedHashMap<>();
                    payment.put("paymentId", item.getId());
                    payment.put("participantIndex", item.getParticipantIndex());
                    payment.put("paymentNo", item.getPaymentNo());
                    payment.put("paymentMethod", item.getPaymentMethod());
                    payment.put("paymentType", item.getPaymentType());
                    payment.put("paymentStatus", item.getPaymentStatus());
                    payment.put("amount", item.getAmount());
                    payment.put("paidAt", item.getPaidAt());
                    payment.put("handledByName", item.getHandledByName());
                    payment.put("remark", item.getRemark());
                    return payment;
                }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long id) {
        EnrollmentOrderEntity order = requireOrder(id);

        if ("CANCELLED".equals(order.getOrderStatus())) {
            throw new BusinessException("ORDER_ALREADY_CANCELLED", "Order is already cancelled.");
        }
        if (defaultAmount(order.getReceivedAmount()).compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("PAID_ORDER_REQUIRES_REFUND",
                    "Orders with received payments cannot be cancelled directly. Use full refund or contact staff for manual review.");
        }

        cancelEnrollmentArtifacts(order);

        order.setOrderStatus("CANCELLED");
        int updated = enrollmentOrderMapper.updateById(order);
        if (updated != 1) {
            throw new BusinessException("ORDER_CONCURRENT_UPDATE",
                    "The order was updated by another request. Please retry.");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReceiptRecordVO refundOrder(Long id, RefundRequest request) {
        EnrollmentOrderEntity order = requireOrder(id);

        if ("REFUNDED".equals(order.getPaymentStatus())) {
            throw new BusinessException("ORDER_ALREADY_REFUNDED", "Order has already been refunded.");
        }
        BigDecimal received = order.getReceivedAmount() != null ? order.getReceivedAmount() : BigDecimal.ZERO;
        Integer participantIndex = request.getParticipantIndex();
        if (participantIndex != null && participantIndex < 1) {
            throw new BusinessException("INVALID_PARTICIPANT_INDEX", "The selected student is invalid.");
        }
        EnrollmentOrderParticipantEntity targetParticipant = participantIndex == null
                ? null
                : findOrderParticipant(order.getId(), participantIndex);
        if (participantIndex != null && targetParticipant == null) {
            throw new BusinessException("PAYMENT_PARTICIPANT_NOT_FOUND",
                    "The selected student is not part of this enrollment order.");
        }
        List<EnrollmentOrderParticipantEntity> participants = listOrderParticipants(order.getId());
        BigDecimal participantReceived = targetParticipant == null
                ? received
                : resolveParticipantReceivedAmount(order.getId(), targetParticipant);
        if (participantReceived.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("NO_PAYMENT_TO_REFUND", "There is no received payment to refund.");
        }
        BigDecimal refundAmount = defaultAmount(request.getRefundAmount());
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_REFUND_AMOUNT", "Refund amount must be greater than zero.");
        }
        if (refundAmount.compareTo(participantReceived) > 0) {
            throw new BusinessException("REFUND_AMOUNT_EXCEEDS_RECEIVED",
                    targetParticipant == null
                            ? "Refund amount cannot exceed the currently received amount."
                    : "Refund amount cannot exceed this student's received amount.");
        }
        if (targetParticipant == null && participants.size() > 1
                && refundAmount.compareTo(received) != 0) {
            throw new BusinessException("REFUND_PARTICIPANT_REQUIRED",
                    "Select the student for a partial refund on a multi-student enrollment.");
        }
        if (targetParticipant != null && refundAmount.compareTo(received) > 0) {
            throw new BusinessException("REFUND_AMOUNT_EXCEEDS_RECEIVED",
                    "Refund amount cannot exceed the order's currently received amount.");
        }
        requireActivePaymentMethod(request.getPaymentMethod());
        boolean fullParticipantRefund = targetParticipant != null
                && refundAmount.compareTo(participantReceived) == 0;
        boolean hasOtherParticipants = targetParticipant != null && participants.stream()
                .anyMatch(item -> !Objects.equals(item.getParticipantIndex(), targetParticipant.getParticipantIndex()));
        boolean fullOrderRefund = targetParticipant == null
                ? refundAmount.compareTo(received) == 0
                : fullParticipantRefund && !hasOtherParticipants;
        if (fullOrderRefund) {
            validateFullRefundHasNoLessonRecords(order);
        } else if (fullParticipantRefund) {
            validateFullParticipantRefundHasNoLessonRecords(order, targetParticipant);
        }

        // Full refunds close enrollment artifacts. Partial refunds preserve courses and schedules.
        if (fullOrderRefund && !"CANCELLED".equals(order.getOrderStatus())) {
            cancelEnrollmentArtifacts(order);
            order.setOrderStatus("CANCELLED");
        }

        // Refunds are immutable negative ledger entries; never overwrite payment history.
        PaymentRecordEntity refundRecord = new PaymentRecordEntity();
        refundRecord.setEnrollmentOrderId(order.getId());
        refundRecord.setParticipantIndex(participantIndex);
        refundRecord.setPaymentNo(BusinessNoGenerator.generatePaymentNo());
        refundRecord.setPaymentMethod(request.getPaymentMethod());
        refundRecord.setPaymentType("REFUND");
        refundRecord.setPaymentStatus("REFUNDED");
        refundRecord.setAmount(refundAmount.negate());
        refundRecord.setPaidAt(AppTime.nowLocalDateTime());
        refundRecord.setHandledByUserId(request.getHandledByUserId());
        refundRecord.setHandledByName(request.getHandledByName());
        refundRecord.setRemark(request.getRemark());
        paymentRecordMapper.insert(refundRecord);

        BigDecimal remainingReceived = received.subtract(refundAmount);
        order.setReceivedAmount(remainingReceived);
        if (fullOrderRefund) {
            // A fully refunded order is closed and has no collectible balance.
            order.setBalanceAmount(BigDecimal.ZERO);
            order.setPaymentStatus("REFUNDED");
        } else {
            BigDecimal amountDue = defaultAmount(order.getAmountDue());
            order.setBalanceAmount(amountDue.subtract(remainingReceived));
            order.setPaymentStatus(resolvePaymentStatus(
                    amountDue,
                    ZERO_AMOUNT,
                    remainingReceived));
        }
        int updated = enrollmentOrderMapper.updateById(order);
        if (updated != 1) {
            throw new BusinessException("ORDER_CONCURRENT_UPDATE", "The order was updated by another request. Please retry.");
        }

        syncParticipantPaymentSnapshots(order.getId());
        if (fullOrderRefund && participants.size() > 1) {
            resetParticipantPaymentSnapshots(participants);
        }
        if (fullParticipantRefund && !fullOrderRefund) {
            closeRefundedParticipantArtifacts(order, targetParticipant);
        }
        if (fullOrderRefund) {
            List<CoursePackageEntity> packages = coursePackageMapper.selectList(
                    new LambdaQueryWrapper<CoursePackageEntity>()
                            .eq(CoursePackageEntity::getEnrollmentOrderId, order.getId()));
            if (packages == null) {
                packages = Collections.emptyList();
            }
            for (CoursePackageEntity coursePackage : packages) {
                coursePackage.setReceivedAmount(BigDecimal.ZERO);
                coursePackageMapper.updateById(coursePackage);
            }
        }
        // 退款也回写原报名收据，不再创建第二张退款收据；收据内保留负数退款流水。
        return syncRegistrationReceiptAfterRefund(order);
    }

    private EnrollmentOrderParticipantEntity findOrderParticipant(Long orderId, Integer participantIndex) {
        return enrollmentOrderParticipantMapper.selectOne(
                new LambdaQueryWrapper<EnrollmentOrderParticipantEntity>()
                        .eq(EnrollmentOrderParticipantEntity::getEnrollmentOrderId, orderId)
                        .eq(EnrollmentOrderParticipantEntity::getParticipantIndex, participantIndex)
                        .eq(EnrollmentOrderParticipantEntity::getIsDeleted, 0));
    }

    private List<EnrollmentOrderParticipantEntity> listOrderParticipants(Long orderId) {
        List<EnrollmentOrderParticipantEntity> participants = enrollmentOrderParticipantMapper.selectList(
                new LambdaQueryWrapper<EnrollmentOrderParticipantEntity>()
                        .eq(EnrollmentOrderParticipantEntity::getEnrollmentOrderId, orderId)
                        .eq(EnrollmentOrderParticipantEntity::getIsDeleted, 0)
                        .orderByAsc(EnrollmentOrderParticipantEntity::getParticipantIndex));
        return participants == null ? Collections.emptyList() : participants;
    }

    /**
     * 参与学生的退款额度以带 participant_index 的支付流水为准，兼容历史主学生未带序号的流水。
     */
    private BigDecimal resolveParticipantReceivedAmount(Long orderId,
                                                        EnrollmentOrderParticipantEntity participant) {
        List<PaymentRecordEntity> payments = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecordEntity>()
                        .eq(PaymentRecordEntity::getEnrollmentOrderId, orderId));
        if (payments == null) {
            payments = Collections.emptyList();
        }
        BigDecimal scoped = payments.stream()
                .filter(item -> !"VOIDED".equals(item.getPaymentStatus()))
                .filter(item -> Objects.equals(item.getParticipantIndex(), participant.getParticipantIndex()))
                .map(item -> defaultAmount(item.getAmount()))
                .reduce(ZERO_AMOUNT, BigDecimal::add);
        if (Objects.equals(participant.getParticipantIndex(), 1)) {
            return scoped.add(payments.stream()
                    .filter(item -> item.getParticipantIndex() == null)
                    .filter(item -> !"VOIDED".equals(item.getPaymentStatus()))
                    .map(item -> defaultAmount(item.getAmount()))
                    .reduce(ZERO_AMOUNT, BigDecimal::add))
                    .max(ZERO_AMOUNT);
        }
        return scoped.compareTo(ZERO_AMOUNT) != 0
                ? scoped.max(ZERO_AMOUNT)
                : defaultAmount(participant.getPaidAmount()).max(ZERO_AMOUNT);
    }

    private void syncParticipantPaymentSnapshots(Long orderId) {
        List<EnrollmentOrderParticipantEntity> participants = listOrderParticipants(orderId);
        if (participants.isEmpty()) {
            return;
        }
        List<PaymentRecordEntity> payments = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecordEntity>()
                        .eq(PaymentRecordEntity::getEnrollmentOrderId, orderId));
        if (payments == null) {
            payments = Collections.emptyList();
        }
        for (EnrollmentOrderParticipantEntity participant : participants) {
            BigDecimal paid = payments.stream()
                    .filter(item -> !"VOIDED".equals(item.getPaymentStatus()))
                    .filter(item -> Objects.equals(item.getParticipantIndex(), participant.getParticipantIndex())
                            || (participant.getParticipantIndex() == 1 && item.getParticipantIndex() == null))
                    .map(item -> defaultAmount(item.getAmount()))
                    .reduce(ZERO_AMOUNT, BigDecimal::add);
            participant.setPaidAmount(paid);
            participant.setBalanceAmount(defaultAmount(participant.getAmountDue()).subtract(paid));
            enrollmentOrderParticipantMapper.updateById(participant);
        }
    }

    private void resetParticipantPaymentSnapshots(List<EnrollmentOrderParticipantEntity> participants) {
        for (EnrollmentOrderParticipantEntity participant : participants) {
            participant.setPaidAmount(ZERO_AMOUNT);
            participant.setBalanceAmount(defaultAmount(participant.getAmountDue()));
            enrollmentOrderParticipantMapper.updateById(participant);
        }
    }

    private void closeRefundedParticipantArtifacts(EnrollmentOrderEntity order,
                                                    EnrollmentOrderParticipantEntity participant) {
        List<CoursePackageEntity> packages = coursePackageMapper.selectList(
                new LambdaQueryWrapper<CoursePackageEntity>()
                        .eq(CoursePackageEntity::getEnrollmentOrderId, order.getId())
                        .eq(CoursePackageEntity::getStudentId, participant.getStudentId()));
        if (packages == null) {
            packages = Collections.emptyList();
        }
        for (CoursePackageEntity coursePackage : packages) {
            coursePackage.setReceivedAmount(ZERO_AMOUNT);
            coursePackage.setPackageStatus("CLOSED");
            coursePackageMapper.updateById(coursePackage);
            PrivateLessonGroupMemberEntity member = privateLessonGroupMemberMapper.selectOne(
                    new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                            .eq(PrivateLessonGroupMemberEntity::getPackageId, coursePackage.getId())
                            .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                            .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0));
            if (member != null) {
                member.setMemberStatus("INACTIVE");
                privateLessonGroupMemberMapper.updateById(member);
            }
        }
    }

    private void validateFullParticipantRefundHasNoLessonRecords(EnrollmentOrderEntity order,
                                                                  EnrollmentOrderParticipantEntity participant) {
        List<CoursePackageEntity> packages = coursePackageMapper.selectList(
                        new LambdaQueryWrapper<CoursePackageEntity>()
                                .eq(CoursePackageEntity::getEnrollmentOrderId, order.getId())
                                .eq(CoursePackageEntity::getStudentId, participant.getStudentId()));
        if (packages == null) {
            packages = Collections.emptyList();
        }
        List<Long> packageIds = packages.stream()
                .map(CoursePackageEntity::getId)
                .toList();
        if (packageIds.isEmpty()) {
            return;
        }
        Long lessonRecordCount = lessonRecordMapper.selectCount(
                new LambdaQueryWrapper<LessonRecordEntity>()
                        .in(LessonRecordEntity::getPackageId, packageIds));
        if (lessonRecordCount != null && lessonRecordCount > 0) {
            throw new BusinessException("FULL_REFUND_LESSON_RECORD_EXISTS",
                    "Full refund is unavailable after lessons are recorded. Contact staff for manual review.");
        }
    }

    /** 退款后刷新报名收据的累计金额、参与学生余额和完整支付/退款流水。 */
    private ReceiptRecordVO syncRegistrationReceiptAfterRefund(EnrollmentOrderEntity order) {
        List<ReceiptRecordEntity> receipts = receiptRecordMapper.selectList(
                        new LambdaQueryWrapper<ReceiptRecordEntity>()
                                .eq(ReceiptRecordEntity::getEnrollmentOrderId, order.getId())
                                .eq(ReceiptRecordEntity::getTemplateCode, DEFAULT_RECEIPT_TEMPLATE)
                                .orderByDesc(ReceiptRecordEntity::getIssuedAt));
        if (receipts == null || receipts.isEmpty()) {
            return null;
        }
        ReceiptRecordEntity receipt = receipts
                .stream()
                .findFirst()
                .orElse(null);
        if (receipt == null) {
            return null;
        }
        Map<String, Object> payload = jsonHelper.fromJson(
                receipt.getReceiptPayload(), new TypeReference<LinkedHashMap<String, Object>>() {});
        if (payload == null) {
            payload = new LinkedHashMap<>();
        }
        payload.put("amountDue", order.getAmountDue());
        payload.put("receivedAmount", order.getReceivedAmount());
        payload.put("paidAmount", order.getReceivedAmount());
        payload.put("balanceAmount", order.getBalanceAmount());
        payload.put("paymentStatus", order.getPaymentStatus());
        payload.put("participants", receiptParticipantPayloads(order.getId()));
        payload.put("payments", receiptPaymentPayloads(order.getId()));
        receipt.setReceiptPayload(jsonHelper.toJson(payload));
        receiptRecordMapper.updateById(receipt);
        return toReceiptVO(receipt, order.getStudentNameSnapshot());
    }

    private void requireActivePaymentMethod(String methodCode) {
        Long count = paymentMethodMapper.selectCount(new LambdaQueryWrapper<PaymentMethodEntity>()
                .eq(PaymentMethodEntity::getMethodCode, methodCode)
                .eq(PaymentMethodEntity::getStatus, "ACTIVE")
                .eq(PaymentMethodEntity::getIsDeleted, 0));
        if (count == null || count == 0) {
            throw new BusinessException("PAYMENT_METHOD_NOT_AVAILABLE",
                    "Payment method not found or inactive.");
        }
    }

    private void cancelEnrollmentArtifacts(EnrollmentOrderEntity order) {
        cancelOneOnOneEnrollment(order);
        cancelClassEnrollment(order);
    }

    private void cancelClassEnrollment(EnrollmentOrderEntity order) {
        List<Long> orderItemIds = listOrderItemsByOrderId(order.getId()).stream()
                .map(EnrollmentOrderItemEntity::getId)
                .toList();
        markClassEnrollmentsWithdrawn(collectClassEnrollments(order, orderItemIds, true));
    }

    /**
     * 收集本单关联的班课在册记录：兼容 legacy class_id 直连与 enrollment_order_item 模型，按 id 去重。
     *
     * @param onlyActive 仅收集 ACTIVE 在册（Cancel / ForceVoid 流程沿用旧行为）
     */
    private List<ClassEnrollmentEntity> collectClassEnrollments(EnrollmentOrderEntity order,
                                                                List<Long> orderItemIds,
                                                                boolean onlyActive) {
        Map<Long, ClassEnrollmentEntity> byId = new LinkedHashMap<>();
        if (order.getClassId() != null) {
            // Legacy direct class orders do not have an order-item reference.
            LambdaQueryWrapper<ClassEnrollmentEntity> legacy = new LambdaQueryWrapper<ClassEnrollmentEntity>()
                    .eq(ClassEnrollmentEntity::getClassId, order.getClassId())
                    .eq(ClassEnrollmentEntity::getStudentId, order.getStudentId());
            if (onlyActive) {
                legacy.eq(ClassEnrollmentEntity::getStatus, "ACTIVE");
            }
            legacy.eq(ClassEnrollmentEntity::getIsDeleted, 0);
            for (ClassEnrollmentEntity enrollment : classEnrollmentMapper.selectList(legacy)) {
                byId.put(enrollment.getId(), enrollment);
            }
        }

        if (!orderItemIds.isEmpty()) {
            LambdaQueryWrapper<ClassEnrollmentEntity> itemBased = new LambdaQueryWrapper<ClassEnrollmentEntity>()
                    .in(ClassEnrollmentEntity::getEnrollmentOrderItemId, orderItemIds);
            if (onlyActive) {
                itemBased.eq(ClassEnrollmentEntity::getStatus, "ACTIVE");
            }
            itemBased.eq(ClassEnrollmentEntity::getIsDeleted, 0);
            for (ClassEnrollmentEntity enrollment : classEnrollmentMapper.selectList(itemBased)) {
                byId.put(enrollment.getId(), enrollment);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private void markClassEnrollmentsWithdrawn(List<ClassEnrollmentEntity> enrollments) {
        for (ClassEnrollmentEntity enrollment : enrollments) {
            classEnrollmentMapper.update(
                    new LambdaUpdateWrapper<ClassEnrollmentEntity>()
                            .eq(ClassEnrollmentEntity::getId, enrollment.getId())
                            .set(ClassEnrollmentEntity::getStatus, "WITHDRAWN")
                            .set(ClassEnrollmentEntity::getIsDeleted, 1)
            );
        }
    }

    private void cancelOneOnOneEnrollment(EnrollmentOrderEntity order) {
        List<CoursePackageEntity> packages = coursePackageMapper.selectList(
                new LambdaQueryWrapper<CoursePackageEntity>()
                        .eq(CoursePackageEntity::getEnrollmentOrderId, order.getId())
        );
        if (packages.isEmpty()) {
            return;
        }

        List<Long> packageIds = packages.stream().map(CoursePackageEntity::getId).toList();
        cancelFutureOccurrences(packageIds);
        deactivateRecurringSchedules(packageIds);

        // Closing the package prevents further deduction while preserving consumed lessons and payments.
        for (CoursePackageEntity coursePackage : packages) {
            if (!"CLOSED".equals(coursePackage.getPackageStatus())) {
                coursePackage.setPackageStatus("CLOSED");
                coursePackageMapper.updateById(coursePackage);
            }
        }
    }

    private void cancelFutureOccurrences(List<Long> packageIds) {
        lessonOccurrenceMapper.update(
                new UpdateWrapper<LessonOccurrenceEntity>()
                        .in("package_id", packageIds)
                        .in("planning_status", List.of("SCHEDULED", "ADJUSTED"))
                        .gt("planned_start_at", AppTime.nowLocalDateTime())
                        .set("planning_status", "CANCELLED")
        );
    }

    private void deactivateRecurringSchedules(List<Long> packageIds) {
        List<RecurringScheduleRuleEntity> rules = recurringScheduleRuleMapper.selectList(
                new QueryWrapper<RecurringScheduleRuleEntity>()
                        .in("package_id", packageIds)
                        .ne("rule_status", "INACTIVE")
        );
        if (rules.isEmpty()) {
            return;
        }

        List<Long> ruleIds = rules.stream().map(RecurringScheduleRuleEntity::getId).toList();
        recurringScheduleRuleMapper.update(
                new UpdateWrapper<RecurringScheduleRuleEntity>()
                        .in("id", ruleIds)
                        .set("rule_status", "INACTIVE")
        );
        recurringScheduleSlotMapper.update(
                new UpdateWrapper<RecurringScheduleSlotEntity>()
                        .in("rule_id", ruleIds)
                        .set("slot_status", "INACTIVE")
        );
    }

    private void validateFullRefundHasNoLessonRecords(EnrollmentOrderEntity order) {
        List<CoursePackageEntity> packages = coursePackageMapper.selectList(
                new LambdaQueryWrapper<CoursePackageEntity>()
                        .eq(CoursePackageEntity::getEnrollmentOrderId, order.getId()));
        List<Long> packageIds = packages.stream().map(CoursePackageEntity::getId).toList();
        if (!packageIds.isEmpty()) {
            Long lessonRecordCount = lessonRecordMapper.selectCount(new LambdaQueryWrapper<LessonRecordEntity>()
                    .in(LessonRecordEntity::getPackageId, packageIds));
            if (lessonRecordCount != null && lessonRecordCount > 0) {
                throw new BusinessException("FULL_REFUND_LESSON_RECORD_EXISTS",
                        "Full refund is unavailable after lessons are recorded. Contact staff for manual review.");
            }
        }

        List<Long> classIds = new ArrayList<>();
        if (order.getClassId() != null) {
            classIds.add(order.getClassId());
        }
        listOrderItemsByOrderId(order.getId()).stream()
                .map(EnrollmentOrderItemEntity::getClassId)
                .filter(Objects::nonNull)
                .forEach(classIds::add);
        if (classIds.isEmpty()) {
            return;
        }

        List<LessonOccurrenceEntity> occurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .select(LessonOccurrenceEntity::getId)
                        .in(LessonOccurrenceEntity::getClassGroupId, classIds.stream().distinct().toList()));
        if (occurrences.isEmpty()) {
            return;
        }
        Long lessonRecordCount = lessonRecordMapper.selectCount(new LambdaQueryWrapper<LessonRecordEntity>()
                .eq(LessonRecordEntity::getStudentId, order.getStudentId())
                .in(LessonRecordEntity::getOccurrenceId, occurrences.stream().map(LessonOccurrenceEntity::getId).toList()));
        if (lessonRecordCount != null && lessonRecordCount > 0) {
            throw new BusinessException("FULL_REFUND_LESSON_RECORD_EXISTS",
                    "Full refund is unavailable after lessons are recorded. Contact staff for manual review.");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(Long id) {
        EnrollmentOrderEntity order = requireOrder(id);
        List<Long> itemIds = listOrderItemsByOrderId(id).stream()
                .map(EnrollmentOrderItemEntity::getId)
                .toList();

        // 全量级联软删：任意状态订单均可删，子表数据一并软删，最后软删父表。
        if (order.getClassId() != null) {
            deleteClassOrderCascade(order, itemIds);
        } else {
            deleteOneOnOneOrderCascade(order, itemIds);
        }
        enrollmentOrderMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void forceVoidOrder(Long id, String reason) {
        EnrollmentOrderEntity order = requireOrder(id);

        // 强制作废保留财务与消课历史，只关闭报名产物并软删订单，供清理历史脏数据。
        cancelEnrollmentArtifacts(order);

        if (!"CANCELLED".equals(order.getOrderStatus())) {
            order.setOrderStatus("CANCELLED");
        }
        String note = "[FORCE-VOID] " + (StringUtils.hasText(reason) ? reason.trim() : "Legacy record cleanup");
        String existingRemark = order.getRemark();
        order.setRemark(StringUtils.hasText(existingRemark) ? existingRemark + " | " + note : note);
        int updated = enrollmentOrderMapper.updateById(order);
        if (updated != 1) {
            throw new BusinessException("ORDER_CONCURRENT_UPDATE",
                    "The order was updated by another request. Please retry.");
        }
        enrollmentOrderMapper.deleteById(id);
    }

    // ─── 全量级联软删辅助 ──────────────────────────────────────────────

    private void deleteOneOnOneOrderCascade(EnrollmentOrderEntity order, List<Long> itemIds) {
        List<CoursePackageEntity> packages = coursePackageMapper.selectList(
                new LambdaQueryWrapper<CoursePackageEntity>()
                        .eq(CoursePackageEntity::getEnrollmentOrderId, order.getId()));
        deleteOneOnOnePackageCascade(packages);
        deleteOrderFinancialArtifacts(order, itemIds);
    }

    private void deleteClassOrderCascade(EnrollmentOrderEntity order, List<Long> itemIds) {
        // 1) 撤销本单关联的班课在册（兼容 item 模型与 legacy class_id+student_id，不限定 status）
        List<ClassEnrollmentEntity> classEnrollments = collectClassEnrollments(order, itemIds, false);
        List<Long> classEnrollmentIds = classEnrollments.stream()
                .map(ClassEnrollmentEntity::getId)
                .toList();
        markClassEnrollmentsWithdrawn(classEnrollments);

        // 2) 软删该学生个人的班课课时核销记录；共享排课 occurrence 不删
        List<Long> classRecordIds = listClassOrderLessonRecordIds(order);
        if (!classRecordIds.isEmpty()) {
            lessonRecordMapper.delete(new LambdaQueryWrapper<LessonRecordEntity>()
                    .in(LessonRecordEntity::getId, classRecordIds));
            notificationLogMapper.delete(new LambdaQueryWrapper<NotificationLogEntity>()
                    .in(NotificationLogEntity::getLessonRecordId, classRecordIds));
        }
        // class 课时流水：当前无此类行（class 课时接线未启用），按在册 / 课时记录预留级联
        if (!classEnrollmentIds.isEmpty() || !classRecordIds.isEmpty()) {
            QueryWrapper<HourLedgerEntity> ledgerWrapper = new QueryWrapper<>();
            boolean hasClause = false;
            if (!classEnrollmentIds.isEmpty()) {
                // HourLedgerEntity 尚无 class_enrollment_id 字段，使用原列名
                ledgerWrapper.in("class_enrollment_id", classEnrollmentIds);
                hasClause = true;
            }
            if (!classRecordIds.isEmpty()) {
                if (hasClause) {
                    ledgerWrapper.or().in("lesson_record_id", classRecordIds);
                } else {
                    ledgerWrapper.in("lesson_record_id", classRecordIds);
                }
            }
            hourLedgerMapper.delete(ledgerWrapper);
        }

        // 3) 班课订单如有 course_package（当前为 0，item 模型未来可能产生），一并级联其下游
        List<CoursePackageEntity> packages = coursePackageMapper.selectList(
                new LambdaQueryWrapper<CoursePackageEntity>()
                        .eq(CoursePackageEntity::getEnrollmentOrderId, order.getId()));
        deleteOneOnOnePackageCascade(packages);

        deleteOrderFinancialArtifacts(order, itemIds);
    }

    private void deleteOneOnOnePackageCascade(List<CoursePackageEntity> packages) {
        if (packages.isEmpty()) {
            return;
        }
        List<Long> packageIds = packages.stream().map(CoursePackageEntity::getId).toList();

        // 收集下游 id
        List<Long> occurrenceIds = lessonOccurrenceMapper.selectList(
                        new LambdaQueryWrapper<LessonOccurrenceEntity>()
                                .select(LessonOccurrenceEntity::getId)
                                .in(LessonOccurrenceEntity::getPackageId, packageIds))
                .stream().map(LessonOccurrenceEntity::getId).toList();
        List<Long> recordIds = lessonRecordMapper.selectList(
                        new LambdaQueryWrapper<LessonRecordEntity>()
                                .select(LessonRecordEntity::getId)
                                .in(LessonRecordEntity::getPackageId, packageIds))
                .stream().map(LessonRecordEntity::getId).toList();
        List<Long> ruleIds = recurringScheduleRuleMapper.selectList(
                        new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                                .select(RecurringScheduleRuleEntity::getId)
                                .in(RecurringScheduleRuleEntity::getPackageId, packageIds))
                .stream().map(RecurringScheduleRuleEntity::getId).toList();

        // 子表 → 父表删除
        if (!occurrenceIds.isEmpty()) {
            // lesson_adjustment_log 无 is_deleted → 物理删
            lessonAdjustmentLogMapper.delete(new LambdaQueryWrapper<LessonAdjustmentLogEntity>()
                    .in(LessonAdjustmentLogEntity::getOccurrenceId, occurrenceIds));
        }
        lessonOccurrenceMapper.delete(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .in(LessonOccurrenceEntity::getPackageId, packageIds));

        if (!ruleIds.isEmpty()) {
            recurringScheduleSlotMapper.delete(new LambdaQueryWrapper<RecurringScheduleSlotEntity>()
                    .in(RecurringScheduleSlotEntity::getRuleId, ruleIds));
        }
        recurringScheduleRuleMapper.delete(new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                .in(RecurringScheduleRuleEntity::getPackageId, packageIds));

        if (!recordIds.isEmpty()) {
            lessonRecordMapper.delete(new LambdaQueryWrapper<LessonRecordEntity>()
                    .in(LessonRecordEntity::getId, recordIds));
            hourLedgerMapper.delete(new LambdaQueryWrapper<HourLedgerEntity>()
                    .in(HourLedgerEntity::getLessonRecordId, recordIds));
            notificationLogMapper.delete(new LambdaQueryWrapper<NotificationLogEntity>()
                    .in(NotificationLogEntity::getLessonRecordId, recordIds));
        }
        // hour_ledger / notification_log 按 package 关联一并软删
        hourLedgerMapper.delete(new LambdaQueryWrapper<HourLedgerEntity>()
                .in(HourLedgerEntity::getPackageId, packageIds));
        notificationLogMapper.delete(new LambdaQueryWrapper<NotificationLogEntity>()
                .in(NotificationLogEntity::getPackageId, packageIds));

        // package_balance_snapshot 无 is_deleted → 物理删（PK = package_id）
        for (Long packageId : packageIds) {
            packageBalanceSnapshotMapper.deleteById(packageId);
        }

        packageTeacherAssignmentMapper.delete(new LambdaQueryWrapper<PackageTeacherAssignmentEntity>()
                .in(PackageTeacherAssignmentEntity::getPackageId, packageIds));
        coursePackageMapper.delete(new LambdaQueryWrapper<CoursePackageEntity>()
                .in(CoursePackageEntity::getId, packageIds));
    }

    private void deleteOrderFinancialArtifacts(EnrollmentOrderEntity order, List<Long> itemIds) {
        // 软删前把业务唯一号追加行级唯一后缀：uk_xxx_no(receipt_no/payment_no, is_deleted)
        // 复合唯一键只允许一行 (no,1)，若历史软删行占用键位，批量软删会撞 Duplicate entry。
        paymentRecordMapper.update(null, new LambdaUpdateWrapper<PaymentRecordEntity>()
                .eq(PaymentRecordEntity::getEnrollmentOrderId, order.getId())
                .eq(PaymentRecordEntity::getIsDeleted, 0)
                .setSql("payment_no = CONCAT(payment_no, '-DEL-', id)"));
        receiptRecordMapper.update(null, new LambdaUpdateWrapper<ReceiptRecordEntity>()
                .eq(ReceiptRecordEntity::getEnrollmentOrderId, order.getId())
                .eq(ReceiptRecordEntity::getIsDeleted, 0)
                .setSql("receipt_no = CONCAT(receipt_no, '-DEL-', id)"));
        if (!itemIds.isEmpty()) {
            enrollmentOrderItemMapper.delete(new LambdaQueryWrapper<EnrollmentOrderItemEntity>()
                    .in(EnrollmentOrderItemEntity::getId, itemIds));
        }
        paymentRecordMapper.delete(new LambdaQueryWrapper<PaymentRecordEntity>()
                .eq(PaymentRecordEntity::getEnrollmentOrderId, order.getId()));
        receiptRecordMapper.delete(new LambdaQueryWrapper<ReceiptRecordEntity>()
                .eq(ReceiptRecordEntity::getEnrollmentOrderId, order.getId()));
    }

    private List<Long> listClassOrderLessonRecordIds(EnrollmentOrderEntity order) {
        List<Long> occurrenceIds = lessonOccurrenceMapper.selectList(
                        new LambdaQueryWrapper<LessonOccurrenceEntity>()
                                .select(LessonOccurrenceEntity::getId)
                                .eq(LessonOccurrenceEntity::getClassGroupId, order.getClassId()))
                .stream().map(LessonOccurrenceEntity::getId).toList();
        if (occurrenceIds.isEmpty()) {
            return Collections.emptyList();
        }
        return lessonRecordMapper.selectList(
                        new LambdaQueryWrapper<LessonRecordEntity>()
                                .select(LessonRecordEntity::getId)
                                .eq(LessonRecordEntity::getStudentId, order.getStudentId())
                                .in(LessonRecordEntity::getOccurrenceId, occurrenceIds))
                .stream().map(LessonRecordEntity::getId).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int renewActiveClassOccurrences() {
        List<ClassGroupEntity> activeGroups = classGroupMapper.selectList(
                new LambdaQueryWrapper<ClassGroupEntity>()
                        .eq(ClassGroupEntity::getStatus, "ACTIVE")
                        .eq(ClassGroupEntity::getBillingType, "MONTHLY")
                        .isNotNull(ClassGroupEntity::getStartMonth)
                        .isNotNull(ClassGroupEntity::getEndMonth));
        LocalDate today = LocalDate.now(AppTime.APP_ZONE_ID);
        int inserted = 0;
        for (ClassGroupEntity group : activeGroups) {
            try {
                LocalDate[] window = resolveClassOccurrenceWindow(group, today);
                if (window == null) {
                    continue;
                }
                inserted += ensureClassOccurrencesThrough(group, window[0], window[1]);
            } catch (RuntimeException ex) {
                log.warn("Failed to renew occurrences for class group id={}: {}", group.getId(), ex.getMessage());
            }
        }
        return inserted;
    }

    private LocalDate[] resolveClassOccurrenceWindow(ClassGroupEntity group, LocalDate today) {
        if (group.getStartDate() != null && group.getEndDate() != null) {
            LocalDate start = group.getStartDate().isAfter(today) ? group.getStartDate() : today;
            return group.getEndDate().isBefore(start) ? null : new LocalDate[]{start, group.getEndDate()};
        }
        LocalDate classStartDay = group.getStartMonth();
        LocalDate classEndDay = group.getEndMonth();
        if (classStartDay == null || classEndDay == null) {
            return null;
        }
        LocalDate start = classStartDay.isAfter(today) ? classStartDay : today;
        LocalDate end = java.time.YearMonth.from(classEndDay).atEndOfMonth();
        return end.isBefore(start) ? null : new LocalDate[]{start, end};
    }

    private int ensureClassOccurrencesThrough(ClassGroupEntity classGroup,
                                               LocalDate windowStart,
                                               LocalDate horizonEnd) {
        List<ClassGroupScheduleSlotEntity> slots = loadClassSlots(classGroup.getId());
        if (slots.isEmpty()) {
            return 0;
        }

        LocalDateTime windowStartDt = windowStart.atStartOfDay();
        LocalDateTime windowEnd = horizonEnd.plusDays(1).atStartOfDay();

        Set<LocalDateTime> existingStarts = lessonOccurrenceMapper.selectList(
                        new LambdaQueryWrapper<LessonOccurrenceEntity>()
                                .eq(LessonOccurrenceEntity::getClassGroupId, classGroup.getId())
                                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                                .ge(LessonOccurrenceEntity::getPlannedStartAt, windowStartDt)
                                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd))
                .stream()
                .map(LessonOccurrenceEntity::getPlannedStartAt)
                .collect(Collectors.toSet());

        List<LessonOccurrenceEntity> occurrences = new ArrayList<>();
        for (LocalDate date = windowStart; !date.isAfter(horizonEnd); date = date.plusDays(1)) {
            int weekday = date.getDayOfWeek().getValue(); // Monday=1, Sunday=7
            for (ClassGroupScheduleSlotEntity slot : slots) {
                if (!Objects.equals(slot.getWeekday(), weekday) || !slot.getEndTime().isAfter(slot.getStartTime())) {
                    continue;
                }
                LocalDateTime plannedStartAt = LocalDateTime.of(date, slot.getStartTime());
                if (existingStarts.contains(plannedStartAt)) {
                    continue;
                }
                long minutes = ChronoUnit.MINUTES.between(slot.getStartTime(), slot.getEndTime());
                LessonOccurrenceEntity occ = new LessonOccurrenceEntity();
                occ.setLessonNo(BusinessNoGenerator.generateClassLessonOccurrenceNo());
                occ.setClassGroupId(classGroup.getId());
                occ.setOccurrenceType("REGULAR");
                occ.setPlannedTeacherId(slot.getTeacherId() == null ? classGroup.getTeacherId() : slot.getTeacherId());
                occ.setPlannedClassroomId(classGroup.getClassroomId());
                occ.setPlannedStartAt(plannedStartAt);
                occ.setPlannedEndAt(LocalDateTime.of(date, slot.getEndTime()));
                occ.setPlannedUnits((int) (minutes / 30));
                occ.setPlanningStatus("SCHEDULED");
                occ.setConflictChecked(1);
                occurrences.add(occ);
            }
        }

        if (!occurrences.isEmpty()) {
            ensureNoConflicts(occurrences, null, null, classGroup.getClassroomId());
            occurrences.stream()
                    .filter(item -> item.getPlannedTeacherId() != null)
                    .collect(Collectors.groupingBy(LessonOccurrenceEntity::getPlannedTeacherId))
                    .forEach((teacherId, teacherOccurrences) ->
                            ensureNoConflicts(teacherOccurrences, null, teacherId, null));
            occurrences.forEach(lessonOccurrenceMapper::insert);
        }

        String scheduleDesc = buildScheduleDesc(slots);
        if (!scheduleDesc.equals(classGroup.getScheduleDesc())) {
            classGroup.setScheduleDesc(scheduleDesc);
            classGroupMapper.updateById(classGroup);
        }
        return occurrences.size();
    }

    private List<Long> findMatchedStudentIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        return studentMapper.selectList(new LambdaQueryWrapper<StudentEntity>()
                        .and(w -> w.like(StudentEntity::getStudentName, keyword)
                                .or()
                                .like(StudentEntity::getStudentNo, keyword)))
                .stream()
                .map(StudentEntity::getId)
                .toList();
    }

    private List<Long> findMatchedCourseOrderIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        List<Long> productIds = courseProductMapper.selectList(new LambdaQueryWrapper<CourseProductEntity>()
                        .and(w -> w.like(CourseProductEntity::getProductName, keyword)
                                .or()
                                .like(CourseProductEntity::getRegisterClass, keyword)))
                .stream()
                .map(CourseProductEntity::getId)
                .toList();
        if (productIds.isEmpty()) {
            return Collections.emptyList();
        }
        return coursePackageMapper.selectList(new LambdaQueryWrapper<CoursePackageEntity>()
                        .in(CoursePackageEntity::getCourseProductId, productIds))
                .stream()
                .map(CoursePackageEntity::getEnrollmentOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<Long> findMatchedClassIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        return classGroupMapper.selectList(new LambdaQueryWrapper<ClassGroupEntity>()
                        .like(ClassGroupEntity::getClassName, keyword))
                .stream()
                .map(ClassGroupEntity::getId)
                .toList();
    }

    private List<ClassGroupScheduleSlotEntity> loadClassSlots(Long classGroupId) {
        return classGroupScheduleSlotMapper.selectList(
                new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                        .eq(ClassGroupScheduleSlotEntity::getClassGroupId, classGroupId)
                        .eq(ClassGroupScheduleSlotEntity::getIsDeleted, 0));
    }

    private String buildScheduleDesc(List<ClassGroupScheduleSlotEntity> slots) {
        // 按 (startTime, endTime) 分组，每组的星期合并
        Map<String, List<Integer>> timeToWeekdays = new LinkedHashMap<>();
        for (ClassGroupScheduleSlotEntity slot : slots) {
            String key = slot.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                    + "-" + slot.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm"));
            timeToWeekdays.computeIfAbsent(key, k -> new ArrayList<>()).add(slot.getWeekday());
        }
        String[] weekdayAbbr = {"", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : timeToWeekdays.entrySet()) {
            String weekPart = entry.getValue().stream()
                    .sorted()
                    .map(w -> weekdayAbbr[w])
                    .collect(Collectors.joining("/"));
            lines.add(weekPart + " " + entry.getKey());
        }
        return String.join("\n", lines);
    }
}
