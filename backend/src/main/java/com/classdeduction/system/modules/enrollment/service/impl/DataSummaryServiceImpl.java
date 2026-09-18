package com.classdeduction.system.modules.enrollment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.util.UnitConverter;
import com.classdeduction.system.modules.base.entity.CampusEntity;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.CampusMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.DataSummaryQuery;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderParticipantEntity;
import com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderParticipantMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMapper;
import com.classdeduction.system.modules.enrollment.service.DataSummaryService;
import com.classdeduction.system.modules.enrollment.vo.DataSummaryResponseVO;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataSummaryServiceImpl implements DataSummaryService {

    private static final int EXPIRING_SOON_DAYS = 14;
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final EnrollmentOrderMapper enrollmentOrderMapper;
    private final EnrollmentOrderItemMapper enrollmentOrderItemMapper;
    private final EnrollmentOrderParticipantMapper enrollmentOrderParticipantMapper;
    private final PrivateLessonGroupMapper privateLessonGroupMapper;
    private final CoursePackageMapper coursePackageMapper;
    private final PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final ClassGroupMapper classGroupMapper;
    private final CourseProductMapper courseProductMapper;
    private final TeacherMapper teacherMapper;
    private final HourLedgerMapper hourLedgerMapper;
    private final StudentMapper studentMapper;
    private final CampusMapper campusMapper;

    @Override
    public DataSummaryResponseVO getSummary(DataSummaryQuery query) {
        List<EnrollmentOrderEntity> orders = listOrders(query);
        if (orders.isEmpty()) {
            return emptyResponse();
        }

        List<Long> orderIds = orders.stream().map(EnrollmentOrderEntity::getId).toList();
        Map<Long, EnrollmentOrderEntity> orderMap = orders.stream()
                .collect(Collectors.toMap(EnrollmentOrderEntity::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        List<PaymentRecordEntity> paymentRecords = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecordEntity>()
                        .in(PaymentRecordEntity::getEnrollmentOrderId, orderIds)
                        .orderByAsc(PaymentRecordEntity::getPaidAt)
                        .orderByAsc(PaymentRecordEntity::getId));
        Map<Long, String> latestPaymentMethodByOrderId = paymentRecords.stream()
                .filter(record -> positive(record.getAmount()) && StringUtils.hasText(record.getPaymentMethod()))
                .collect(Collectors.toMap(
                        PaymentRecordEntity::getEnrollmentOrderId,
                        PaymentRecordEntity::getPaymentMethod,
                        (left, right) -> right,
                        LinkedHashMap::new));
        Map<PaymentParticipantKey, String> latestPaymentMethodByParticipant = paymentRecords.stream()
                .filter(record -> record.getParticipantIndex() != null)
                .filter(record -> positive(record.getAmount()) && StringUtils.hasText(record.getPaymentMethod()))
                .collect(Collectors.toMap(
                        record -> new PaymentParticipantKey(record.getEnrollmentOrderId(), record.getParticipantIndex()),
                        PaymentRecordEntity::getPaymentMethod,
                        (left, right) -> right,
                        LinkedHashMap::new));
        Map<PaymentParticipantKey, BigDecimal> paidAmountByParticipant = paymentRecords.stream()
                .filter(record -> record.getParticipantIndex() != null)
                .collect(Collectors.groupingBy(
                        record -> new PaymentParticipantKey(record.getEnrollmentOrderId(), record.getParticipantIndex()),
                        LinkedHashMap::new,
                        Collectors.reducing(ZERO_AMOUNT, record -> amount(record.getAmount()), BigDecimal::add)));

        List<EnrollmentOrderItemEntity> items = enrollmentOrderItemMapper.selectList(
                new LambdaQueryWrapper<EnrollmentOrderItemEntity>()
                        .in(EnrollmentOrderItemEntity::getEnrollmentOrderId, orderIds)
                        .orderByAsc(EnrollmentOrderItemEntity::getLineNo)
                        .orderByAsc(EnrollmentOrderItemEntity::getId));
        Map<Long, List<EnrollmentOrderItemEntity>> itemsByOrderId = items.stream()
                .collect(Collectors.groupingBy(EnrollmentOrderItemEntity::getEnrollmentOrderId, LinkedHashMap::new, Collectors.toList()));

        List<EnrollmentOrderParticipantEntity> orderParticipants = enrollmentOrderParticipantMapper.selectList(
                new LambdaQueryWrapper<EnrollmentOrderParticipantEntity>()
                        .in(EnrollmentOrderParticipantEntity::getEnrollmentOrderId, orderIds)
                        .orderByAsc(EnrollmentOrderParticipantEntity::getParticipantIndex)
                        .orderByAsc(EnrollmentOrderParticipantEntity::getId));
        Map<Long, List<EnrollmentOrderParticipantEntity>> participantsByOrderId = orderParticipants.stream()
                .collect(Collectors.groupingBy(EnrollmentOrderParticipantEntity::getEnrollmentOrderId,
                        LinkedHashMap::new, Collectors.toList()));

        List<PrivateLessonGroupEntity> privateLessonGroups = privateLessonGroupMapper.selectList(
                        new LambdaQueryWrapper<PrivateLessonGroupEntity>()
                                .in(PrivateLessonGroupEntity::getEnrollmentOrderId, orderIds)
                                .eq(PrivateLessonGroupEntity::getIsDeleted, 0));
        Map<Long, Long> privateLessonGroupIdByOrderId = privateLessonGroups.stream()
                .filter(group -> group.getEnrollmentOrderId() != null && group.getId() != null)
                .collect(Collectors.toMap(
                        PrivateLessonGroupEntity::getEnrollmentOrderId,
                        PrivateLessonGroupEntity::getId,
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<Long, Integer> privateLessonParticipantCountByOrderId = privateLessonGroups.stream()
                .filter(group -> group.getEnrollmentOrderId() != null && group.getParticipantCount() != null)
                .collect(Collectors.toMap(
                        PrivateLessonGroupEntity::getEnrollmentOrderId,
                        PrivateLessonGroupEntity::getParticipantCount,
                        Math::max,
                        LinkedHashMap::new));

        List<CoursePackageEntity> packages = coursePackageMapper.selectList(
                new LambdaQueryWrapper<CoursePackageEntity>()
                        .in(CoursePackageEntity::getEnrollmentOrderId, orderIds));
        Map<Long, CoursePackageEntity> packageByOrderItemId = packages.stream()
                .filter(item -> item.getEnrollmentOrderItemId() != null)
                .collect(Collectors.toMap(CoursePackageEntity::getEnrollmentOrderItemId, Function.identity(), (left, right) -> left));
        Map<Long, List<CoursePackageEntity>> packagesByOrderId = packages.stream()
                .collect(Collectors.groupingBy(CoursePackageEntity::getEnrollmentOrderId));

        Map<Long, List<String>> reviewIssuesByOrderId = buildReviewIssues(orders, packages, paymentRecords);

        Set<Long> itemIds = items.stream().map(EnrollmentOrderItemEntity::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> studentIds = orders.stream().map(EnrollmentOrderEntity::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        orderParticipants.stream().map(EnrollmentOrderParticipantEntity::getStudentId)
                .filter(Objects::nonNull)
                .forEach(studentIds::add);
        Map<Long, StudentEntity> studentById = new HashMap<>();
        if (!studentIds.isEmpty()) {
            studentMapper.selectBatchIds(studentIds)
                    .forEach(s -> studentById.put(s.getId(), s));
        }
        Set<Long> campusIds = new LinkedHashSet<>();
        orders.stream().map(EnrollmentOrderEntity::getCampusId).filter(Objects::nonNull).forEach(campusIds::add);
        studentById.values().stream().map(StudentEntity::getCampusId).filter(Objects::nonNull).forEach(campusIds::add);
        Map<Long, CampusEntity> campusById = campusIds.isEmpty() ? Collections.emptyMap()
                : campusMapper.selectBatchIds(campusIds).stream()
                        .collect(Collectors.toMap(CampusEntity::getId, Function.identity(), (left, right) -> left));
        Set<Long> itemClassIds = items.stream().map(EnrollmentOrderItemEntity::getClassId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        orders.stream().map(EnrollmentOrderEntity::getClassId).filter(Objects::nonNull).forEach(itemClassIds::add);
        List<ClassEnrollmentEntity> directClassEnrollments = itemIds.isEmpty() ? Collections.emptyList() : classEnrollmentMapper.selectList(
                new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .in(ClassEnrollmentEntity::getEnrollmentOrderItemId, itemIds));
        List<ClassEnrollmentEntity> studentClassEnrollments = studentIds.isEmpty() || itemClassIds.isEmpty()
                ? Collections.emptyList()
                : classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .in(ClassEnrollmentEntity::getStudentId, studentIds)
                        .in(ClassEnrollmentEntity::getClassId, itemClassIds));
        List<ClassEnrollmentEntity> classEnrollments = mergeClassEnrollments(directClassEnrollments, studentClassEnrollments);
        Map<Long, ClassEnrollmentEntity> classEnrollmentByOrderItemId = classEnrollments.stream()
                .filter(item -> item.getEnrollmentOrderItemId() != null)
                .collect(Collectors.toMap(ClassEnrollmentEntity::getEnrollmentOrderItemId, Function.identity(), (left, right) -> left));
        Map<StudentClassKey, List<ClassEnrollmentEntity>> classEnrollmentsByStudentClass = classEnrollments.stream()
                .filter(item -> item.getStudentId() != null && item.getClassId() != null)
                .collect(Collectors.groupingBy(item -> new StudentClassKey(item.getStudentId(), item.getClassId())));

        Context context = buildContext(orders, items, packages, classEnrollments);
        // 行级金额分摊：同一订单的多行按「参与人 → 订单项」分摊整单金额，避免汇总时金额按行数翻倍。
        Map<Long, Map<AllocationKey, AllocatedAmounts>> allocationByOrderId = new HashMap<>();
        for (EnrollmentOrderEntity order : orders) {
            allocationByOrderId.put(order.getId(), allocateOrderAmounts(order,
                    itemsByOrderId.getOrDefault(order.getId(), Collections.emptyList()),
                    participantsByOrderId.getOrDefault(order.getId(), Collections.emptyList()),
                    paidAmountByParticipant));
        }
        List<DataSummaryResponseVO.RowVO> rows = buildRows(orderMap, itemsByOrderId, packagesByOrderId,
                        participantsByOrderId, privateLessonGroupIdByOrderId, privateLessonParticipantCountByOrderId,
                        packageByOrderItemId,
                        classEnrollmentByOrderItemId, classEnrollmentsByStudentClass, reviewIssuesByOrderId,
                        latestPaymentMethodByOrderId, latestPaymentMethodByParticipant, paidAmountByParticipant,
                        allocationByOrderId,
                        studentById, campusById, context)
                .stream()
                .filter(this::hasDisplayableStudent)
                .filter(row -> matchesQuery(row, query))
                .filter(row -> !Boolean.TRUE.equals(query.getOnlyAlerts()) || hasAlerts(row))
                .filter(row -> !Boolean.TRUE.equals(query.getOnlyNeedsReview()) || hasReviewIssues(row))
                .sorted(Comparator.comparing(DataSummaryResponseVO.RowVO::signupDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DataSummaryResponseVO.RowVO::enrollmentNo, Comparator.nullsLast(String::compareTo))
                        .thenComparing(DataSummaryResponseVO.RowVO::orderItemId, Comparator.nullsLast(Long::compareTo)))
                .toList();

        Set<Long> visibleOrderIds = rows.stream().map(DataSummaryResponseVO.RowVO::orderId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> visibleStudentIds = rows.stream().map(DataSummaryResponseVO.RowVO::studentId).filter(Objects::nonNull).collect(Collectors.toSet());
        BigDecimal totalIncome = sumOrders(visibleOrderIds, orderMap, EnrollmentOrderEntity::getReceivedAmount);
        BigDecimal totalOutstanding = sumOutstandingOrders(visibleOrderIds, orderMap);

        return new DataSummaryResponseVO(
                (long) visibleStudentIds.size(),
                totalIncome,
                totalOutstanding,
                buildGroups(rows, orderMap, GroupKind.CAMPUS),
                buildGroups(rows, orderMap, GroupKind.COURSE_TYPE),
                buildGroups(rows, orderMap, GroupKind.CLASS_COURSE),
                buildGroups(rows, orderMap, GroupKind.PAYMENT_STATUS),
                rows
        );
    }

    private List<EnrollmentOrderEntity> listOrders(DataSummaryQuery query) {
        return enrollmentOrderMapper.selectList(new LambdaQueryWrapper<EnrollmentOrderEntity>()
                .eq(query.getCampusId() != null, EnrollmentOrderEntity::getCampusId, query.getCampusId())
                .eq(StringUtils.hasText(query.getPaymentStatus()), EnrollmentOrderEntity::getPaymentStatus, query.getPaymentStatus())
                .ge(query.getPeriodStartDate() != null, EnrollmentOrderEntity::getPeriodStartDate, query.getPeriodStartDate())
                .le(query.getPeriodEndDate() != null, EnrollmentOrderEntity::getPeriodEndDate, query.getPeriodEndDate())
                .ne(EnrollmentOrderEntity::getOrderStatus, "CANCELLED")
                .ne(EnrollmentOrderEntity::getPaymentStatus, "REFUNDED")
                .orderByDesc(EnrollmentOrderEntity::getSignupDate)
                .orderByDesc(EnrollmentOrderEntity::getCreatedAt));
    }

    private Map<Long, List<String>> buildReviewIssues(List<EnrollmentOrderEntity> orders,
                                                      List<CoursePackageEntity> packages,
                                                      List<PaymentRecordEntity> paymentRecords) {
        Map<Long, List<String>> issuesByOrderId = new HashMap<>();
        Map<Long, BigDecimal> paymentNetByOrderId = paymentRecords.stream()
                .collect(Collectors.groupingBy(PaymentRecordEntity::getEnrollmentOrderId,
                        Collectors.reducing(ZERO_AMOUNT, record -> amount(record.getAmount()), BigDecimal::add)));

        for (EnrollmentOrderEntity order : orders) {
            BigDecimal paymentNet = amount(paymentNetByOrderId.get(order.getId()));
            BigDecimal receivedAmount = amount(order.getReceivedAmount());
            if (paymentNet.compareTo(receivedAmount) != 0) {
                addReviewIssue(issuesByOrderId, order.getId(), "PAYMENT_NET_MISMATCH");
            }

            BigDecimal expectedBalance = amount(order.getAmountDue()).subtract(paymentNet);
            if (expectedBalance.compareTo(amount(order.getBalanceAmount())) != 0) {
                addReviewIssue(issuesByOrderId, order.getId(), "BALANCE_MISMATCH");
            }
        }

        Set<Long> packageIds = packages.stream()
                .map(CoursePackageEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (packageIds.isEmpty()) {
            return issuesByOrderId;
        }

        Map<Long, Integer> ledgerChangesByPackageId = hourLedgerMapper.selectList(
                        new LambdaQueryWrapper<HourLedgerEntity>()
                                .in(HourLedgerEntity::getPackageId, packageIds))
                .stream()
                .collect(Collectors.groupingBy(HourLedgerEntity::getPackageId,
                        Collectors.summingInt(ledger -> safeUnits(ledger.getChangeUnits()))));
        for (CoursePackageEntity coursePackage : packages) {
            int expectedRemaining = safeUnits(coursePackage.getTotalUnits())
                    + ledgerChangesByPackageId.getOrDefault(coursePackage.getId(), 0);
            int expectedConsumed = safeUnits(coursePackage.getTotalUnits()) - expectedRemaining;
            if (expectedRemaining != safeUnits(coursePackage.getRemainingUnits())
                    || expectedConsumed != safeUnits(coursePackage.getConsumedUnits())) {
                addReviewIssue(issuesByOrderId, coursePackage.getEnrollmentOrderId(), "PACKAGE_LEDGER_MISMATCH");
            }
        }
        return issuesByOrderId;
    }

    private void addReviewIssue(Map<Long, List<String>> issuesByOrderId, Long orderId, String issueCode) {
        if (orderId == null) {
            return;
        }
        List<String> issues = issuesByOrderId.computeIfAbsent(orderId, ignored -> new ArrayList<>());
        if (!issues.contains(issueCode)) {
            issues.add(issueCode);
        }
    }

    private Context buildContext(List<EnrollmentOrderEntity> orders,
                                 List<EnrollmentOrderItemEntity> items,
                                 List<CoursePackageEntity> packages,
                                 List<ClassEnrollmentEntity> classEnrollments) {
        Set<Long> classIds = new LinkedHashSet<>();
        Set<Long> productIds = new LinkedHashSet<>();
        Set<Long> packageIds = packages.stream().map(CoursePackageEntity::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> teacherIds = new LinkedHashSet<>();

        orders.stream().map(EnrollmentOrderEntity::getClassId).filter(Objects::nonNull).forEach(classIds::add);
        items.stream().map(EnrollmentOrderItemEntity::getClassId).filter(Objects::nonNull).forEach(classIds::add);
        classEnrollments.stream().map(ClassEnrollmentEntity::getClassId).filter(Objects::nonNull).forEach(classIds::add);
        items.stream().map(EnrollmentOrderItemEntity::getCourseProductId).filter(Objects::nonNull).forEach(productIds::add);
        packages.stream().map(CoursePackageEntity::getCourseProductId).filter(Objects::nonNull).forEach(productIds::add);
        items.stream().map(EnrollmentOrderItemEntity::getTeacherId).filter(Objects::nonNull).forEach(teacherIds::add);

        Map<Long, ClassGroupEntity> classGroups = classIds.isEmpty() ? Collections.emptyMap()
                : classGroupMapper.selectBatchIds(classIds).stream()
                        .collect(Collectors.toMap(ClassGroupEntity::getId, Function.identity()));
        classGroups.values().stream().map(ClassGroupEntity::getProductId).filter(Objects::nonNull).forEach(productIds::add);
        classGroups.values().stream().map(ClassGroupEntity::getTeacherId).filter(Objects::nonNull).forEach(teacherIds::add);

        Map<Long, CourseProductEntity> products = productIds.isEmpty() ? Collections.emptyMap()
                : courseProductMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(CourseProductEntity::getId, Function.identity()));

        Map<Long, PackageTeacherAssignmentEntity> primaryAssignments = packageIds.isEmpty() ? Collections.emptyMap()
                : packageTeacherAssignmentMapper.selectList(new LambdaQueryWrapper<PackageTeacherAssignmentEntity>()
                                .in(PackageTeacherAssignmentEntity::getPackageId, packageIds)
                                .eq(PackageTeacherAssignmentEntity::getIsPrimary, 1)
                                .orderByDesc(PackageTeacherAssignmentEntity::getEffectiveFrom))
                        .stream()
                        .collect(Collectors.toMap(PackageTeacherAssignmentEntity::getPackageId, Function.identity(), (left, right) -> left));
        primaryAssignments.values().stream().map(PackageTeacherAssignmentEntity::getTeacherId).filter(Objects::nonNull).forEach(teacherIds::add);

        Map<Long, TeacherEntity> teachers = teacherIds.isEmpty() ? Collections.emptyMap()
                : teacherMapper.selectBatchIds(teacherIds).stream()
                        .collect(Collectors.toMap(TeacherEntity::getId, Function.identity()));
        return new Context(classGroups, products, primaryAssignments, teachers);
    }

    private List<DataSummaryResponseVO.RowVO> buildRows(
            Map<Long, EnrollmentOrderEntity> orderMap,
            Map<Long, List<EnrollmentOrderItemEntity>> itemsByOrderId,
            Map<Long, List<CoursePackageEntity>> packagesByOrderId,
            Map<Long, List<EnrollmentOrderParticipantEntity>> participantsByOrderId,
            Map<Long, Long> privateLessonGroupIdByOrderId,
            Map<Long, Integer> privateLessonParticipantCountByOrderId,
            Map<Long, CoursePackageEntity> packageByOrderItemId,
            Map<Long, ClassEnrollmentEntity> classEnrollmentByOrderItemId,
            Map<StudentClassKey, List<ClassEnrollmentEntity>> classEnrollmentsByStudentClass,
            Map<Long, List<String>> reviewIssuesByOrderId,
            Map<Long, String> latestPaymentMethodByOrderId,
            Map<PaymentParticipantKey, String> latestPaymentMethodByParticipant,
            Map<PaymentParticipantKey, BigDecimal> paidAmountByParticipant,
            Map<Long, Map<AllocationKey, AllocatedAmounts>> allocationByOrderId,
            Map<Long, StudentEntity> studentById,
            Map<Long, CampusEntity> campusById,
            Context context) {
        LocalDate today = LocalDate.now();
        LocalDate soonLimit = today.plusDays(EXPIRING_SOON_DAYS);
        List<DataSummaryResponseVO.RowVO> rows = new ArrayList<>();

        for (EnrollmentOrderEntity order : orderMap.values()) {
            List<EnrollmentOrderParticipantEntity> participants = participantsByOrderId.get(order.getId());
            List<EnrollmentOrderParticipantEntity> displayParticipants = participants == null || participants.isEmpty()
                    ? Collections.singletonList(null)
                    : participants;
            Integer privateLessonParticipantCount = resolvePrivateLessonParticipantCount(
                    order.getId(), participants, privateLessonParticipantCountByOrderId);
            Long privateLessonGroupId = privateLessonGroupIdByOrderId.get(order.getId());
            boolean sharedPrivateLesson = isSharedPrivateLesson(privateLessonParticipantCount);
            List<EnrollmentOrderItemEntity> orderItems = itemsByOrderId.get(order.getId());
            if (orderItems == null || orderItems.isEmpty()) {
                for (EnrollmentOrderParticipantEntity participant : displayParticipants) {
                    CoursePackageEntity coursePackage = participant == null
                            ? firstPackage(packagesByOrderId.get(order.getId()))
                            : findPackageForParticipant(packagesByOrderId.get(order.getId()), participant, null);
                    rows.add(toRow(order, null, coursePackage, null, participant,
                            privateLessonGroupId,
                            sharedPrivateLesson ? privateLessonParticipantCount : null,
                            reviewIssuesByOrderId.getOrDefault(order.getId(), Collections.emptyList()),
                            latestPaymentMethodByOrderId.get(order.getId()), latestPaymentMethodByParticipant,
                            paidAmountByParticipant,
                            allocationByOrderId,
                            studentById, campusById, context, today, soonLimit));
                }
                continue;
            }
            for (EnrollmentOrderItemEntity item : orderItems) {
                CoursePackageEntity defaultPackage = firstNonNull(packageByOrderItemId.get(item.getId()),
                        findPackageForItem(packagesByOrderId.get(order.getId()), item));
                ClassEnrollmentEntity defaultClassEnrollment = firstNonNull(classEnrollmentByOrderItemId.get(item.getId()),
                        findClassEnrollmentForItem(order, item, order.getStudentId(), classEnrollmentsByStudentClass));
                for (EnrollmentOrderParticipantEntity participant : displayParticipants) {
                    CoursePackageEntity coursePackage = participant == null
                            ? defaultPackage
                            : findPackageForParticipant(packagesByOrderId.get(order.getId()), participant, item);
                    ClassEnrollmentEntity classEnrollment = participant == null
                            ? defaultClassEnrollment
                            : firstNonNull(classEnrollmentByOrderItemId.get(item.getId()),
                                    findClassEnrollmentForItem(order, item, participant.getStudentId(), classEnrollmentsByStudentClass));
                    rows.add(toRow(order, item, coursePackage, classEnrollment, participant,
                            privateLessonGroupId,
                            sharedPrivateLesson ? privateLessonParticipantCount : null,
                            reviewIssuesByOrderId.getOrDefault(order.getId(), Collections.emptyList()),
                            latestPaymentMethodByOrderId.get(order.getId()), latestPaymentMethodByParticipant,
                            paidAmountByParticipant,
                            allocationByOrderId,
                            studentById, campusById, context, today, soonLimit));
                }
            }
        }
        return rows;
    }

    private Integer resolvePrivateLessonParticipantCount(Long orderId,
                                                         List<EnrollmentOrderParticipantEntity> participants,
                                                         Map<Long, Integer> privateLessonParticipantCountByOrderId) {
        Integer groupCount = privateLessonParticipantCountByOrderId.get(orderId);
        if (groupCount != null && groupCount > 0) {
            return groupCount;
        }
        int participantCount = participants == null ? 0 : (int) participants.stream()
                .filter(Objects::nonNull)
                .count();
        return participantCount > 0 ? participantCount : null;
    }

    private boolean isSharedPrivateLesson(Integer participantCount) {
        return participantCount != null && participantCount > 1;
    }

    private List<ClassEnrollmentEntity> mergeClassEnrollments(List<ClassEnrollmentEntity> directClassEnrollments,
                                                              List<ClassEnrollmentEntity> studentClassEnrollments) {
        Map<Long, ClassEnrollmentEntity> byId = new LinkedHashMap<>();
        List<ClassEnrollmentEntity> withoutId = new ArrayList<>();
        for (ClassEnrollmentEntity enrollment : directClassEnrollments) {
            addClassEnrollment(enrollment, byId, withoutId);
        }
        for (ClassEnrollmentEntity enrollment : studentClassEnrollments) {
            addClassEnrollment(enrollment, byId, withoutId);
        }
        List<ClassEnrollmentEntity> merged = new ArrayList<>(byId.values());
        merged.addAll(withoutId);
        return merged;
    }

    private void addClassEnrollment(ClassEnrollmentEntity enrollment,
                                    Map<Long, ClassEnrollmentEntity> byId,
                                    List<ClassEnrollmentEntity> withoutId) {
        if (enrollment == null) {
            return;
        }
        if (enrollment.getId() == null) {
            withoutId.add(enrollment);
            return;
        }
        byId.putIfAbsent(enrollment.getId(), enrollment);
    }

    private CoursePackageEntity findPackageForItem(List<CoursePackageEntity> orderPackages, EnrollmentOrderItemEntity item) {
        if (orderPackages == null || orderPackages.isEmpty() || item == null) {
            return null;
        }
        List<CoursePackageEntity> unlinkedPackages = orderPackages.stream()
                .filter(coursePackage -> coursePackage.getEnrollmentOrderItemId() == null)
                .toList();
        List<CoursePackageEntity> candidates = unlinkedPackages.isEmpty() ? orderPackages : unlinkedPackages;
        if (item.getCourseProductId() != null) {
            List<CoursePackageEntity> sameProductPackages = candidates.stream()
                    .filter(coursePackage -> Objects.equals(coursePackage.getCourseProductId(), item.getCourseProductId()))
                    .toList();
            if (sameProductPackages.size() == 1) {
                return sameProductPackages.get(0);
            }
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    /** 普通订单和多人私教都按参与学生与订单项定位各自的课包。 */
    private CoursePackageEntity findPackageForParticipant(List<CoursePackageEntity> orderPackages,
                                                          EnrollmentOrderParticipantEntity participant,
                                                          EnrollmentOrderItemEntity item) {
        if (participant == null || orderPackages == null || orderPackages.isEmpty()) {
            return null;
        }
        List<CoursePackageEntity> candidates = orderPackages.stream()
                .filter(coursePackage -> Objects.equals(coursePackage.getStudentId(), participant.getStudentId()))
                .toList();
        if (item != null) {
            List<CoursePackageEntity> sameItem = candidates.stream()
                    .filter(coursePackage -> Objects.equals(coursePackage.getEnrollmentOrderItemId(), item.getId()))
                    .toList();
            if (sameItem.size() == 1) {
                return sameItem.get(0);
            }
            List<CoursePackageEntity> sameProduct = candidates.stream()
                    .filter(coursePackage -> Objects.equals(coursePackage.getCourseProductId(), item.getCourseProductId()))
                    .toList();
            if (sameProduct.size() == 1) {
                return sameProduct.get(0);
            }
        }
        return candidates.size() == 1 ? candidates.get(0) : firstPackage(candidates);
    }

    private ClassEnrollmentEntity findClassEnrollmentForItem(EnrollmentOrderEntity order,
                                                             EnrollmentOrderItemEntity item,
                                                             Long studentId,
                                                             Map<StudentClassKey, List<ClassEnrollmentEntity>> classEnrollmentsByStudentClass) {
        if (item == null || studentId == null) {
            return null;
        }
        Long classId = firstNonNull(item.getClassId(), order.getClassId());
        if (classId == null) {
            return null;
        }
        List<ClassEnrollmentEntity> candidates = classEnrollmentsByStudentClass.get(new StudentClassKey(studentId, classId));
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        List<ClassEnrollmentEntity> unlinkedCandidates = candidates.stream()
                .filter(enrollment -> enrollment.getEnrollmentOrderItemId() == null)
                .toList();
        List<ClassEnrollmentEntity> scopedCandidates = unlinkedCandidates.isEmpty() ? candidates : unlinkedCandidates;
        LocalDate signupDate = order.getSignupDate();
        if (signupDate != null) {
            List<ClassEnrollmentEntity> activeOnSignupDate = scopedCandidates.stream()
                    .filter(enrollment -> containsDate(enrollment.getEnrollmentDate(), enrollment.getExpiryDate(), signupDate))
                    .toList();
            if (activeOnSignupDate.size() == 1) {
                return activeOnSignupDate.get(0);
            }
            if (!activeOnSignupDate.isEmpty()) {
                scopedCandidates = activeOnSignupDate;
            }
        }
        return scopedCandidates.stream()
                .max(Comparator.comparing(ClassEnrollmentEntity::getEnrollmentDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(ClassEnrollmentEntity::getId, Comparator.nullsFirst(Long::compareTo)))
                .orElse(null);
    }

    private boolean containsDate(LocalDate startDate, LocalDate endDate, LocalDate targetDate) {
        boolean startsBeforeTarget = startDate == null || !startDate.isAfter(targetDate);
        boolean endsAfterTarget = endDate == null || !endDate.isBefore(targetDate);
        return startsBeforeTarget && endsAfterTarget;
    }

    /**
     * 上课形态（上午/下午/全天）：优先订单项快照，历史订单回退班课报名记录。
     * NONE 表示该课程维度不适用（如 1 对 1），统一返回 null 由前端展示空值。
     */
    private String resolveAttendanceOption(EnrollmentOrderItemEntity item, ClassEnrollmentEntity classEnrollment) {
        String option = firstText(item == null ? null : item.getAttendanceOption(),
                classEnrollment == null ? null : classEnrollment.getAttendanceOption());
        return option == null || "NONE".equals(option) ? null : option;
    }

    private DataSummaryResponseVO.RowVO toRow(EnrollmentOrderEntity order,
                                              EnrollmentOrderItemEntity item,
                                              CoursePackageEntity coursePackage,
                                              ClassEnrollmentEntity classEnrollment,
                                              EnrollmentOrderParticipantEntity participant,
                                              Long privateLessonGroupId,
                                              Integer privateLessonParticipantCount,
                                              List<String> reviewIssueCodes,
                                              String paymentMethod,
                                              Map<PaymentParticipantKey, String> latestPaymentMethodByParticipant,
                                              Map<PaymentParticipantKey, BigDecimal> paidAmountByParticipant,
                                              Map<Long, Map<AllocationKey, AllocatedAmounts>> allocationByOrderId,
                                              Map<Long, StudentEntity> studentById,
                                              Map<Long, CampusEntity> campusById,
                                              Context context,
                                              LocalDate today,
                                              LocalDate soonLimit) {
        Long displayStudentId = participant == null ? order.getStudentId() : participant.getStudentId();
        StudentEntity student = displayStudentId == null ? null : studentById.get(displayStudentId);
        Long resolvedCampusId = firstNonNull(order.getCampusId(), student == null ? null : student.getCampusId());
        CampusEntity campus = resolvedCampusId == null ? null : campusById.get(resolvedCampusId);
        Long classId = firstNonNull(item == null ? null : item.getClassId(),
                classEnrollment == null ? null : classEnrollment.getClassId(),
                order.getClassId());
        ClassGroupEntity classGroup = classId == null ? null : context.classGroups().get(classId);
        Long productId = firstNonNull(item == null ? null : item.getCourseProductId(),
                coursePackage == null ? null : coursePackage.getCourseProductId(),
                classGroup == null ? null : classGroup.getProductId());
        CourseProductEntity product = productId == null ? null : context.products().get(productId);

        Long teacherId = firstNonNull(item == null ? null : item.getTeacherId(),
                coursePackage == null ? null : teacherIdByPackage(coursePackage.getId(), context),
                classGroup == null ? null : classGroup.getTeacherId());
        TeacherEntity teacher = teacherId == null ? null : context.teachers().get(teacherId);

        String courseType = firstText(item == null ? null : item.getCourseType(),
                isOneOnOne(product, coursePackage) ? "ONE_ON_ONE" : null,
                product == null ? null : product.getTeachingMode());
        String registerClass = firstText(item == null ? null : item.getRegisterClass(),
                classGroup == null ? null : classGroup.getClassName(),
                coursePackage == null ? null : coursePackage.getPackageName(),
                product == null ? null : product.getProductName());
        String detail = firstText(item == null ? null : item.getDetail(),
                coursePackage == null ? null : coursePackage.getPackageName(),
                product == null ? null : product.getProductName());

        // Data Summary 展示班课本身的教学周期；session 数量只负责课时扣减，不应缩短班课 Period。
        LocalDate periodStartDate = resolvePeriodStartDate(classGroup, coursePackage, classEnrollment, order);
        LocalDate periodEndDate = resolvePeriodEndDate(classGroup, coursePackage, classEnrollment, order);
        LocalDate expiryDate = periodEndDate;
        boolean expiringSoon = expiryDate != null && !expiryDate.isBefore(today) && !expiryDate.isAfter(soonLimit);
        boolean oneOnOne = "ONE_ON_ONE".equals(courseType) || isOneOnOne(product, coursePackage);
        boolean sharedPrivateLesson = oneOnOne && isSharedPrivateLesson(privateLessonParticipantCount)
                && participant != null;
        boolean primaryParticipant = participant == null
                || Objects.equals(displayStudentId, order.getStudentId())
                || Objects.equals(participant.getParticipantIndex(), 1);
        PaymentParticipantKey paymentParticipantKey = participant == null || participant.getParticipantIndex() == null
                ? null
                : new PaymentParticipantKey(order.getId(), participant.getParticipantIndex());
        String participantPaymentMethod = paymentParticipantKey == null
                ? null
                : latestPaymentMethodByParticipant.get(paymentParticipantKey);
        String resolvedPaymentMethod = sharedPrivateLesson
                ? firstText(participantPaymentMethod, primaryParticipant ? paymentMethod : null)
                : firstText(participantPaymentMethod, paymentMethod);

        BigDecimal rowLinePriceAmount = item == null ? null : amount(item.getPriceAmount());
        BigDecimal rowPickupFeeAmount = amount(order.getPickupFeeAmount());
        BigDecimal rowExtraFeeAmount = amount(order.getExtraFeeAmount());
        BigDecimal rowDiscountAmount = amount(order.getDiscountAmount());
        BigDecimal rowAmountDue = amount(order.getAmountDue());
        BigDecimal rowPaidAmount = amount(order.getReceivedAmount());
        BigDecimal rowBalanceAmount = amount(order.getBalanceAmount());
        if (!sharedPrivateLesson) {
            AllocatedAmounts allocated = allocatedAmounts(allocationByOrderId, order.getId(), participant, item);
            if (allocated != null) {
                // 非共享私教的行使用分摊后的金额，保证同一订单各行金额之和等于订单总额。
                rowPickupFeeAmount = allocated.pickupFeeAmount();
                rowExtraFeeAmount = allocated.extraFeeAmount();
                rowDiscountAmount = allocated.discountAmount();
                rowAmountDue = allocated.amountDue();
                rowPaidAmount = allocated.paidAmount();
                rowBalanceAmount = allocated.amountDue().subtract(allocated.paidAmount())
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }
        if (sharedPrivateLesson) {
            rowLinePriceAmount = resolveParticipantGrossAmount(participant, coursePackage, item, order,
                    privateLessonParticipantCount);
            rowDiscountAmount = resolveParticipantDiscountAmount(participant, order, privateLessonParticipantCount);
            rowPickupFeeAmount = primaryParticipant ? amount(order.getPickupFeeAmount()) : ZERO_AMOUNT;
            rowExtraFeeAmount = primaryParticipant ? amount(order.getExtraFeeAmount()) : ZERO_AMOUNT;
            rowAmountDue = resolveParticipantAmountDue(participant, rowLinePriceAmount, rowPickupFeeAmount,
                    rowExtraFeeAmount, rowDiscountAmount);
            rowPaidAmount = resolveParticipantPaidAmount(participant, paymentParticipantKey,
                    paidAmountByParticipant, order, primaryParticipant);
            rowBalanceAmount = resolveParticipantBalanceAmount(participant, rowAmountDue, rowPaidAmount);
        }
        boolean overdue = positive(rowBalanceAmount);
        boolean lowOneOnOneHours = oneOnOne && coursePackage != null
                && safeUnits(coursePackage.getRemainingUnits()) <= renewalReminderUnits(coursePackage);
        List<String> alerts = new ArrayList<>();
        if (overdue) {
            alerts.add("OVERDUE");
        }
        if (expiringSoon) {
            alerts.add("EXPIRING_SOON");
        }
        if (lowOneOnOneHours) {
            alerts.add("LOW_ONE_ON_ONE_HOURS");
        }

        String studentName = primaryParticipant
                ? firstText(order.getStudentNameSnapshot(), student == null ? null : student.getStudentName())
                : firstText(student == null ? null : student.getStudentName(), participant.getStudentNameSnapshot());
        String studentClass = primaryParticipant
                ? firstText(student == null ? null : student.getStudentClass(), order.getStudentClassSnapshot())
                : firstText(student == null ? null : student.getStudentClass());
        String school = primaryParticipant
                ? firstText(order.getSchoolSnapshot(), student == null ? null : student.getSchool())
                : firstText(student == null ? null : student.getSchool(), participant.getSchoolSnapshot());
        String grade = primaryParticipant
                ? firstText(order.getGradeSnapshot(), student == null ? null : student.getGrade())
                : firstText(student == null ? null : student.getGrade(), participant.getGradeSnapshot());
        String phone = primaryParticipant
                ? order.getParentPhoneSnapshot()
                : firstText(student == null ? null : student.getGuardianPhone(), order.getParentPhoneSnapshot());
        String phone2 = primaryParticipant
                ? order.getParentPhone2Snapshot()
                : firstText(student == null ? null : student.getGuardianPhone2(), order.getParentPhone2Snapshot());
        Boolean pickupRequired = primaryParticipant
                ? Boolean.TRUE.equals(order.getPickupRequiredSnapshot())
                : Boolean.TRUE.equals(student == null ? null : student.getPickupRequired());
        Boolean dropoffRequired = primaryParticipant
                ? Boolean.TRUE.equals(order.getDropoffRequiredSnapshot())
                : Boolean.TRUE.equals(student == null ? null : student.getDropoffRequired());

        return new DataSummaryResponseVO.RowVO(
                order.getId(),
                order.getEnrollmentNo(),
                item == null ? null : item.getId(),
                displayStudentId,
                participant == null ? null : participant.getParticipantIndex(),
                studentName,
                studentClass,
                school,
                grade,
                order.getSignupDate(),
                periodStartDate,
                periodEndDate,
                item == null ? null : item.getPeriodLabel(),
                resolveAttendanceOption(item, classEnrollment),
                item == null ? null : item.getTimeSlotLabel(),
                resolvedCampusId,
                firstText(order.getCampusNameSnapshot(), campus == null ? null : campus.getCampusName()),
                classId,
                classGroup == null ? null : classGroup.getClassName(),
                productId,
                product == null ? null : product.getProductName(),
                registerClass,
                courseType,
                detail,
                phone,
                phone2,
                pickupRequired,
                order.getPickupAddressSnapshot(),
                dropoffRequired,
                order.getDropoffAddressSnapshot(),
                pickupDropoffLabel(order),
                resolvedPaymentMethod,
                order.getPaymentStatus(),
                rowLinePriceAmount,
                rowPickupFeeAmount,
                rowExtraFeeAmount,
                rowDiscountAmount,
                rowAmountDue,
                rowPaidAmount,
                displayBalanceAmount(rowBalanceAmount),
                coursePackage == null ? null : UnitConverter.toHoursDecimal(coursePackage.getTotalUnits()),
                coursePackage == null ? null : UnitConverter.toHoursDecimal(coursePackage.getConsumedUnits()),
                coursePackage == null ? null : UnitConverter.toHoursDecimal(coursePackage.getRemainingUnits()),
                teacherId,
                teacher == null ? null : teacher.getTeacherName(),
                expiryDate,
                overdue,
                expiringSoon,
                lowOneOnOneHours,
                alerts,
                order.getOrderStatus(),
                reviewIssueCodes,
                oneOnOne ? privateLessonGroupId : null,
                oneOnOne ? privateLessonParticipantCount : null
        );
    }

    private BigDecimal resolveParticipantGrossAmount(EnrollmentOrderParticipantEntity participant,
                                                     CoursePackageEntity coursePackage,
                                                     EnrollmentOrderItemEntity item,
                                                     EnrollmentOrderEntity order,
                                                     Integer participantCount) {
        if (participant.getGrossAmount() != null) {
            return amount(participant.getGrossAmount());
        }
        if (coursePackage != null && coursePackage.getOriginalAmount() != null) {
            return amount(coursePackage.getOriginalAmount());
        }
        BigDecimal totalAmount = item == null ? order.getAmountDue() : item.getPriceAmount();
        return splitAmount(totalAmount, participantCount, participant.getParticipantIndex());
    }

    private BigDecimal resolveParticipantDiscountAmount(EnrollmentOrderParticipantEntity participant,
                                                        EnrollmentOrderEntity order,
                                                        Integer participantCount) {
        if (participant.getDiscountAmount() != null) {
            return amount(participant.getDiscountAmount());
        }
        return splitAmount(order.getDiscountAmount(), participantCount, participant.getParticipantIndex());
    }

    private BigDecimal resolveParticipantAmountDue(EnrollmentOrderParticipantEntity participant,
                                                   BigDecimal grossAmount,
                                                   BigDecimal pickupFeeAmount,
                                                   BigDecimal extraFeeAmount,
                                                   BigDecimal discountAmount) {
        if (participant.getAmountDue() != null) {
            return amount(participant.getAmountDue());
        }
        return grossAmount.add(pickupFeeAmount).add(extraFeeAmount)
                .subtract(discountAmount)
                .max(ZERO_AMOUNT)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveParticipantPaidAmount(EnrollmentOrderParticipantEntity participant,
                                                    PaymentParticipantKey paymentParticipantKey,
                                                    Map<PaymentParticipantKey, BigDecimal> paidAmountByParticipant,
                                                    EnrollmentOrderEntity order,
                                                    boolean primaryParticipant) {
        if (participant.getPaidAmount() != null) {
            return amount(participant.getPaidAmount());
        }
        if (paymentParticipantKey != null && paidAmountByParticipant.containsKey(paymentParticipantKey)) {
            return amount(paidAmountByParticipant.get(paymentParticipantKey));
        }
        // 兼容尚未写入参与人分摊字段的历史订单：未标记参与人的付款归主学生，避免每行重复显示。
        return primaryParticipant ? amount(order.getReceivedAmount()) : ZERO_AMOUNT;
    }

    private BigDecimal resolveParticipantBalanceAmount(EnrollmentOrderParticipantEntity participant,
                                                       BigDecimal amountDue,
                                                       BigDecimal paidAmount) {
        if (participant.getBalanceAmount() != null) {
            return amount(participant.getBalanceAmount());
        }
        return amountDue.subtract(paidAmount).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal splitAmount(BigDecimal totalAmount, Integer participantCount, Integer participantIndex) {
        BigDecimal total = amount(totalAmount);
        int count = participantCount == null || participantCount < 1 ? 1 : participantCount;
        if (count == 1) {
            return total;
        }
        BigDecimal base = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        int index = participantIndex == null ? 1 : participantIndex;
        if (index >= count) {
            return total.subtract(base.multiply(BigDecimal.valueOf(count - 1)))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return base;
    }

    /**
     * 班课优先使用 class_group 的配置周期，兼容旧数据时再回退到学生报名和订单快照日期。
     */
    private LocalDate resolvePeriodStartDate(ClassGroupEntity classGroup,
                                             CoursePackageEntity coursePackage,
                                             ClassEnrollmentEntity classEnrollment,
                                             EnrollmentOrderEntity order) {
        return firstNonNull(classGroup == null ? null : classGroup.getStartDate(),
                coursePackage == null ? null : coursePackage.getStartDate(),
                classEnrollment == null ? null : classEnrollment.getEnrollmentDate(),
                order.getPeriodStartDate());
    }

    /**
     * 班课结束日期以 class_group 为准，避免按第 N 次 session 推算出提前结束的错误 Period。
     */
    private LocalDate resolvePeriodEndDate(ClassGroupEntity classGroup,
                                           CoursePackageEntity coursePackage,
                                           ClassEnrollmentEntity classEnrollment,
                                           EnrollmentOrderEntity order) {
        return firstNonNull(classGroup == null ? null : classGroup.getEndDate(),
                coursePackage == null ? null : coursePackage.getEstimatedEndDate(),
                classEnrollment == null ? null : classEnrollment.getExpiryDate(),
                order.getPeriodEndDate());
    }

    private boolean hasDisplayableStudent(DataSummaryResponseVO.RowVO row) {
        return StringUtils.hasText(row.studentName());
    }

    private boolean matchesQuery(DataSummaryResponseVO.RowVO row, DataSummaryQuery query) {
        if (query.getClassId() != null && !Objects.equals(query.getClassId(), row.classId())) {
            return false;
        }
        if (query.getCourseProductId() != null && !Objects.equals(query.getCourseProductId(), row.courseProductId())) {
            return false;
        }
        if (StringUtils.hasText(query.getCourseType()) && !query.getCourseType().equalsIgnoreCase(row.courseType())) {
            return false;
        }
        if (!StringUtils.hasText(query.getKeyword())) {
            return true;
        }
        String keyword = query.getKeyword().trim().toLowerCase();
        return contains(row.enrollmentNo(), keyword)
                || contains(row.studentName(), keyword)
                || contains(row.studentClass(), keyword)
                || contains(row.school(), keyword)
                || contains(row.phone(), keyword)
                || contains(row.phone2(), keyword)
                || contains(row.registerClass(), keyword)
                || contains(row.detail(), keyword)
                || contains(row.className(), keyword)
                || contains(row.courseProductName(), keyword);
    }

    private List<DataSummaryResponseVO.SummaryGroupVO> buildGroups(List<DataSummaryResponseVO.RowVO> rows,
                                                                   Map<Long, EnrollmentOrderEntity> orderMap,
                                                                   GroupKind groupKind) {
        Map<String, GroupBucket> buckets = new LinkedHashMap<>();
        for (DataSummaryResponseVO.RowVO row : rows) {
            GroupLabel label = groupLabel(row, groupKind);
            buckets.computeIfAbsent(label.key(), key -> new GroupBucket(label)).add(row);
        }
        return buckets.values().stream()
                .map(bucket -> bucket.toVO(orderMap))
                .sorted(Comparator.comparing(DataSummaryResponseVO.SummaryGroupVO::groupName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private GroupLabel groupLabel(DataSummaryResponseVO.RowVO row, GroupKind groupKind) {
        return switch (groupKind) {
            case CAMPUS -> new GroupLabel(stringKey(row.campusId()), firstText(row.campusName(), "Unknown Campus"));
            case COURSE_TYPE -> new GroupLabel(firstText(row.courseType(), "UNKNOWN"), firstText(row.courseType(), "Unknown Type"));
            case CLASS_COURSE -> {
                String key = row.classId() != null ? "CLASS:" + row.classId()
                        : row.courseProductId() != null ? "COURSE:" + row.courseProductId()
                        : firstText(row.registerClass(), row.detail(), "UNKNOWN");
                String name = firstText(row.className(), row.courseProductName(), row.registerClass(), row.detail(), "Unknown Course");
                yield new GroupLabel(key, name);
            }
            case PAYMENT_STATUS -> new GroupLabel(firstText(row.paymentStatus(), "UNKNOWN"), firstText(row.paymentStatus(), "Unknown"));
        };
    }

    private BigDecimal sumOrders(Set<Long> orderIds,
                                 Map<Long, EnrollmentOrderEntity> orderMap,
                                 Function<EnrollmentOrderEntity, BigDecimal> getter) {
        return orderIds.stream()
                .map(orderMap::get)
                .filter(Objects::nonNull)
                .map(getter)
                .map(this::amount)
                .reduce(ZERO_AMOUNT, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumOutstandingOrders(Set<Long> orderIds, Map<Long, EnrollmentOrderEntity> orderMap) {
        return orderIds.stream()
                .map(orderMap::get)
                .filter(Objects::nonNull)
                .map(EnrollmentOrderEntity::getBalanceAmount)
                .filter(this::positive)
                .map(this::amount)
                .reduce(ZERO_AMOUNT, BigDecimal::add)
                // The operational workbook uses negative values for unpaid balances.
                .negate()
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Keeps the persisted balance as a positive amount due for payment arithmetic,
     * while returning the customer-approved negative-balance convention to the workbook.
     */
    private BigDecimal displayBalanceAmount(BigDecimal storedBalanceAmount) {
        BigDecimal balance = amount(storedBalanceAmount);
        return positive(balance) ? balance.negate() : balance;
    }

    private DataSummaryResponseVO emptyResponse() {
        return new DataSummaryResponseVO(0L, ZERO_AMOUNT, ZERO_AMOUNT,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private String pickupDropoffLabel(EnrollmentOrderEntity order) {
        List<String> labels = new ArrayList<>();
        if (Boolean.TRUE.equals(order.getPickupRequiredSnapshot())) {
            labels.add("Pick up");
        }
        if (Boolean.TRUE.equals(order.getDropoffRequiredSnapshot())) {
            labels.add("Drop off");
        }
        return labels.isEmpty() ? "None" : String.join(" / ", labels);
    }

    private CoursePackageEntity firstPackage(List<CoursePackageEntity> packages) {
        return packages == null || packages.isEmpty() ? null : packages.get(0);
    }

    private int renewalReminderUnits(CoursePackageEntity coursePackage) {
        return 8;
    }

    private Long teacherIdByPackage(Long packageId, Context context) {
        PackageTeacherAssignmentEntity assignment = context.primaryAssignments().get(packageId);
        return assignment == null ? null : assignment.getTeacherId();
    }

    private boolean isOneOnOne(CourseProductEntity product, CoursePackageEntity coursePackage) {
        return (product != null && "ONE_ON_ONE".equals(product.getTeachingMode()))
                || (coursePackage != null && "ONE_ON_ONE".equals(coursePackage.getTeachingMode()));
    }

    private boolean hasAlerts(DataSummaryResponseVO.RowVO row) {
        return Boolean.TRUE.equals(row.overdue())
                || Boolean.TRUE.equals(row.expiringSoon())
                || Boolean.TRUE.equals(row.lowOneOnOneHours());
    }

    private boolean hasReviewIssues(DataSummaryResponseVO.RowVO row) {
        return row.reviewIssueCodes() != null && !row.reviewIssueCodes().isEmpty();
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private int safeUnits(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? ZERO_AMOUNT : value.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private String stringKey(Long value) {
        return value == null ? "UNKNOWN" : String.valueOf(value);
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private record Context(
            Map<Long, ClassGroupEntity> classGroups,
            Map<Long, CourseProductEntity> products,
            Map<Long, PackageTeacherAssignmentEntity> primaryAssignments,
            Map<Long, TeacherEntity> teachers
    ) {
    }

    private record StudentClassKey(Long studentId, Long classId) {
    }

    private record PaymentParticipantKey(Long orderId, Integer participantIndex) {
    }

    /** 行级金额分摊的定位键：参与人索引（可为 null）+ 订单项 ID（可为 null）。 */
    private record AllocationKey(Integer participantIndex, Long orderItemId) {
    }

    /** 按订单项分摊后的行级金额。 */
    private record AllocatedAmounts(BigDecimal pickupFeeAmount,
                                    BigDecimal extraFeeAmount,
                                    BigDecimal discountAmount,
                                    BigDecimal amountDue,
                                    BigDecimal paidAmount) {
    }

    /**
     * 订单级金额按「参与人 → 订单项」两级分摊到行，保证同一订单所有行的金额之和等于订单总额。
     * 单参与人（或无参与人记录）订单只有一个桶，直接按订单项价格占比拆分；
     * 无订单项的多参与人订单退化为仅按参与人分桶。
     */
    private Map<AllocationKey, AllocatedAmounts> allocateOrderAmounts(EnrollmentOrderEntity order,
                                                                      List<EnrollmentOrderItemEntity> items,
                                                                      List<EnrollmentOrderParticipantEntity> participants,
                                                                      Map<PaymentParticipantKey, BigDecimal> paidAmountByParticipant) {
        Map<AllocationKey, AllocatedAmounts> allocation = new LinkedHashMap<>();
        if (items.isEmpty()) {
            if (participants.size() > 1) {
                participantBucketAmounts(order, participants, paidAmountByParticipant)
                        .forEach((index, amounts) -> allocation.put(new AllocationKey(index, null), amounts));
            }
            return allocation;
        }
        List<BigDecimal> itemWeights = itemPriceWeights(items);
        if (participants.size() <= 1) {
            Integer participantIndex = participants.isEmpty() ? null : participants.get(0).getParticipantIndex();
            putItemAllocation(allocation, participantIndex, items, itemWeights,
                    amount(order.getPickupFeeAmount()), amount(order.getExtraFeeAmount()),
                    amount(order.getDiscountAmount()), amount(order.getAmountDue()),
                    amount(order.getReceivedAmount()), true);
            return allocation;
        }
        participantBucketAmounts(order, participants, paidAmountByParticipant).forEach((index, bucket) ->
                putItemAllocation(allocation, index, items, itemWeights,
                        bucket.pickupFeeAmount(), bucket.extraFeeAmount(), bucket.discountAmount(),
                        bucket.amountDue(), bucket.paidAmount(), false));
        return allocation;
    }

    private void putItemAllocation(Map<AllocationKey, AllocatedAmounts> allocation,
                                   Integer participantIndex,
                                   List<EnrollmentOrderItemEntity> items,
                                   List<BigDecimal> itemWeights,
                                   BigDecimal pickupFee,
                                   BigDecimal extraFee,
                                   BigDecimal discount,
                                   BigDecimal amountDue,
                                   BigDecimal paid,
                                   boolean preferStoredCourseAmounts) {
        List<BigDecimal> pickupShares = allocateByWeights(pickupFee, itemWeights);
        List<BigDecimal> extraFeeShares = allocateByWeights(extraFee, itemWeights);
        List<BigDecimal> discountShares = storedShares(items, EnrollmentOrderItemEntity::getDiscountAmount,
                discount, itemWeights, preferStoredCourseAmounts);
        List<BigDecimal> amountDueShares = storedShares(items, EnrollmentOrderItemEntity::getAmountDue,
                amountDue, itemWeights, preferStoredCourseAmounts);
        List<BigDecimal> paidShares = storedShares(items, EnrollmentOrderItemEntity::getPaidAmount,
                paid, itemWeights, preferStoredCourseAmounts);
        for (int i = 0; i < items.size(); i++) {
            allocation.put(new AllocationKey(participantIndex, items.get(i).getId()), new AllocatedAmounts(
                    pickupShares.get(i), extraFeeShares.get(i), discountShares.get(i),
                    amountDueShares.get(i), paidShares.get(i)));
        }
    }

    /** 优先采用课程明细快照；旧订单快照缺失或合计不一致时，兼容原订单级加权分配。 */
    private List<BigDecimal> storedShares(List<EnrollmentOrderItemEntity> items,
                                          Function<EnrollmentOrderItemEntity, BigDecimal> valueOf,
                                          BigDecimal expectedTotal,
                                          List<BigDecimal> fallbackWeights,
                                          boolean preferStoredCourseAmounts) {
        if (!preferStoredCourseAmounts || items.stream().anyMatch(item -> valueOf.apply(item) == null)) {
            return allocateByWeights(expectedTotal, fallbackWeights);
        }
        List<BigDecimal> shares = items.stream().map(item -> amount(valueOf.apply(item))).toList();
        BigDecimal storedTotal = shares.stream().reduce(ZERO_AMOUNT, BigDecimal::add);
        return storedTotal.subtract(expectedTotal).abs().compareTo(new BigDecimal("0.01")) <= 0
                ? shares
                : allocateByWeights(expectedTotal, fallbackWeights);
    }

    /** 多参与人订单：先按参与人把整单金额切成桶，参与人快照缺失时回退等分。 */
    private Map<Integer, AllocatedAmounts> participantBucketAmounts(EnrollmentOrderEntity order,
                                                                    List<EnrollmentOrderParticipantEntity> participants,
                                                                    Map<PaymentParticipantKey, BigDecimal> paidAmountByParticipant) {
        List<BigDecimal> dueWeights = participantDueWeights(participants);
        List<BigDecimal> paidWeights = participantPaidWeights(order, participants, paidAmountByParticipant, dueWeights);
        List<BigDecimal> pickupShares = allocateByWeights(amount(order.getPickupFeeAmount()), dueWeights);
        List<BigDecimal> extraFeeShares = allocateByWeights(amount(order.getExtraFeeAmount()), dueWeights);
        List<BigDecimal> discountShares = allocateByWeights(amount(order.getDiscountAmount()), dueWeights);
        List<BigDecimal> amountDueShares = allocateByWeights(amount(order.getAmountDue()), dueWeights);
        List<BigDecimal> paidShares = allocateByWeights(amount(order.getReceivedAmount()), paidWeights);
        Map<Integer, AllocatedAmounts> buckets = new LinkedHashMap<>();
        for (int i = 0; i < participants.size(); i++) {
            buckets.put(participants.get(i).getParticipantIndex(), new AllocatedAmounts(
                    pickupShares.get(i), extraFeeShares.get(i), discountShares.get(i),
                    amountDueShares.get(i), paidShares.get(i)));
        }
        return buckets;
    }

    /** 参与人应收权重：全部参与人都有应收快照且合计为正时按快照占比，否则等分。 */
    private List<BigDecimal> participantDueWeights(List<EnrollmentOrderParticipantEntity> participants) {
        BigDecimal dueSum = ZERO_AMOUNT;
        boolean allPresent = true;
        for (EnrollmentOrderParticipantEntity participant : participants) {
            if (participant.getAmountDue() == null) {
                allPresent = false;
                break;
            }
            dueSum = dueSum.add(amount(participant.getAmountDue()));
        }
        if (!allPresent || !positive(dueSum)) {
            return equalWeights(participants.size());
        }
        return participants.stream().map(participant -> amount(participant.getAmountDue())).toList();
    }

    /**
     * 参与人实付权重：优先用参与人实付快照，其次用按参与人记录的付款流水，最后回退应收权重。
     */
    private List<BigDecimal> participantPaidWeights(EnrollmentOrderEntity order,
                                                    List<EnrollmentOrderParticipantEntity> participants,
                                                    Map<PaymentParticipantKey, BigDecimal> paidAmountByParticipant,
                                                    List<BigDecimal> dueWeights) {
        List<BigDecimal> snapshotWeights = new ArrayList<>();
        BigDecimal snapshotSum = ZERO_AMOUNT;
        boolean allPresent = true;
        for (EnrollmentOrderParticipantEntity participant : participants) {
            if (participant.getPaidAmount() == null) {
                allPresent = false;
                break;
            }
            BigDecimal paid = amount(participant.getPaidAmount());
            snapshotWeights.add(paid);
            snapshotSum = snapshotSum.add(paid);
        }
        if (allPresent && positive(snapshotSum)) {
            return snapshotWeights;
        }
        List<BigDecimal> recordWeights = new ArrayList<>();
        BigDecimal recordSum = ZERO_AMOUNT;
        for (EnrollmentOrderParticipantEntity participant : participants) {
            BigDecimal paid = amount(paidAmountByParticipant.get(
                    new PaymentParticipantKey(order.getId(), participant.getParticipantIndex())));
            recordWeights.add(paid);
            recordSum = recordSum.add(paid);
        }
        if (positive(recordSum)) {
            return recordWeights;
        }
        return dueWeights;
    }

    /** 订单项分摊权重：按 priceAmount 占比；价格缺失或存在非正价格时回退等分。 */
    private List<BigDecimal> itemPriceWeights(List<EnrollmentOrderItemEntity> items) {
        List<BigDecimal> weights = new ArrayList<>();
        BigDecimal weightSum = ZERO_AMOUNT;
        boolean allPositive = true;
        for (EnrollmentOrderItemEntity item : items) {
            BigDecimal price = item.getPriceAmount() == null ? ZERO_AMOUNT : amount(item.getPriceAmount());
            if (!positive(price)) {
                allPositive = false;
            }
            weights.add(price);
            weightSum = weightSum.add(price);
        }
        if (!allPositive || !positive(weightSum)) {
            return equalWeights(items.size());
        }
        return weights;
    }

    private List<BigDecimal> equalWeights(int size) {
        List<BigDecimal> weights = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            weights.add(BigDecimal.ONE);
        }
        return weights;
    }

    /**
     * 按权重把金额拆成 n 份：每份 scale=2 向下取整，尾差并入最后一份，保证合计精确等于总额。
     */
    private List<BigDecimal> allocateByWeights(BigDecimal total, List<BigDecimal> weights) {
        BigDecimal normalizedTotal = amount(total);
        int size = weights.size();
        List<BigDecimal> shares = new ArrayList<>();
        if (size == 0) {
            return shares;
        }
        if (size == 1) {
            shares.add(normalizedTotal);
            return shares;
        }
        BigDecimal weightSum = ZERO_AMOUNT;
        for (BigDecimal weight : weights) {
            weightSum = weightSum.add(weight == null ? ZERO_AMOUNT : weight);
        }
        BigDecimal allocated = ZERO_AMOUNT;
        for (int i = 0; i < size; i++) {
            if (i == size - 1) {
                shares.add(normalizedTotal.subtract(allocated).setScale(2, RoundingMode.HALF_UP));
                break;
            }
            BigDecimal weight = weights.get(i) == null ? ZERO_AMOUNT : weights.get(i);
            BigDecimal share = weightSum.compareTo(ZERO_AMOUNT) == 0
                    ? normalizedTotal.divide(BigDecimal.valueOf(size), 2, RoundingMode.DOWN)
                    : normalizedTotal.multiply(weight).divide(weightSum, 2, RoundingMode.DOWN);
            shares.add(share);
            allocated = allocated.add(share);
        }
        return shares;
    }

    private AllocatedAmounts allocatedAmounts(Map<Long, Map<AllocationKey, AllocatedAmounts>> allocationByOrderId,
                                              Long orderId,
                                              EnrollmentOrderParticipantEntity participant,
                                              EnrollmentOrderItemEntity item) {
        Map<AllocationKey, AllocatedAmounts> allocation = allocationByOrderId.get(orderId);
        if (allocation == null || allocation.isEmpty()) {
            return null;
        }
        Integer participantIndex = participant == null ? null : participant.getParticipantIndex();
        Long itemId = item == null ? null : item.getId();
        AllocatedAmounts exact = allocation.get(new AllocationKey(participantIndex, itemId));
        if (exact != null) {
            return exact;
        }
        // 兼容参与人索引缺失的行：回退到仅按订单项匹配的份额。
        if (participantIndex != null) {
            return allocation.get(new AllocationKey(null, itemId));
        }
        return null;
    }

    private record GroupLabel(String key, String name) {
    }

    private enum GroupKind {
        CAMPUS,
        COURSE_TYPE,
        CLASS_COURSE,
        PAYMENT_STATUS
    }

    private class GroupBucket {
        private final GroupLabel label;
        private final List<DataSummaryResponseVO.RowVO> rows = new ArrayList<>();
        private final Set<Long> orderIds = new LinkedHashSet<>();
        private final Set<Long> studentIds = new LinkedHashSet<>();

        private GroupBucket(GroupLabel label) {
            this.label = label;
        }

        private void add(DataSummaryResponseVO.RowVO row) {
            rows.add(row);
            orderIds.add(row.orderId());
            if (row.studentId() != null) {
                studentIds.add(row.studentId());
            }
        }

        private DataSummaryResponseVO.SummaryGroupVO toVO(Map<Long, EnrollmentOrderEntity> orderMap) {
            return new DataSummaryResponseVO.SummaryGroupVO(
                    label.key(),
                    label.name(),
                    (long) rows.size(),
                    (long) studentIds.size(),
                    sumOrders(orderIds, orderMap, EnrollmentOrderEntity::getReceivedAmount),
                    sumOutstandingOrders(orderIds, orderMap)
            );
        }
    }
}
