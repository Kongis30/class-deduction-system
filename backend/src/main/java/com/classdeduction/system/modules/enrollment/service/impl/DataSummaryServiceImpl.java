package com.classdeduction.system.modules.enrollment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.util.UnitConverter;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.base.entity.CampusEntity;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.GroupRegistrationPlanEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.CampusMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.GroupRegistrationPlanMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.DataSummaryQuery;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.util.WholeDollarBilling;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderParticipantEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderCourseRefundStateEntity;
import com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentRecordEntity;
import com.classdeduction.system.modules.enrollment.entity.PaymentAllocationEntity;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderParticipantMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentAllocationMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMapper;
import com.classdeduction.system.modules.enrollment.service.ClassBillingService;
import com.classdeduction.system.modules.enrollment.service.DataSummaryService;
import com.classdeduction.system.modules.enrollment.vo.ParticipantCourseAmountVO;
import com.classdeduction.system.modules.enrollment.vo.DataSummaryResponseVO;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderCourseRefundStateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
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
    private final PaymentAllocationMapper paymentAllocationMapper;
    private final ClassGroupMapper classGroupMapper;
    private final EnrollmentOrderCourseRefundStateMapper enrollmentOrderCourseRefundStateMapper;
    private final ClassBillingService classBillingService;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final CourseProductMapper courseProductMapper;
    private final GroupRegistrationPlanMapper groupRegistrationPlanMapper;
    private final TeacherMapper teacherMapper;
    private final HourLedgerMapper hourLedgerMapper;
    private final StudentMapper studentMapper;
    private final CampusMapper campusMapper;
    private final JsonHelper jsonHelper;

    /** 复用 Data Summary 的逐学生、逐课程分摊口径，避免收款页另算一套金额。 */
    @Override
    public List<ParticipantCourseAmountVO> getParticipantCourseAmounts(Long orderId, Integer participantIndex) {
        DataSummaryQuery query = new DataSummaryQuery();
        query.setOrderId(orderId);
        query.setIncludeInactiveOrders(true);
        return getSummary(query).rows().stream()
                .filter(row -> participantIndex.equals(row.participantIndex()) && row.orderItemId() != null)
                .map(row -> new ParticipantCourseAmountVO(row.orderItemId(), row.linePriceAmount(),
                        row.amountDue(), row.paidAmount(), row.balanceAmount().negate().max(ZERO_AMOUNT)))
                .toList();
    }

    @Override
    public DataSummaryResponseVO getSummary(DataSummaryQuery query) {
        Set<Long> transportOrderIds = Boolean.TRUE.equals(query.getTransportOnly())
                ? new LinkedHashSet<>(enrollmentOrderMapper.selectTransportOrderIds())
                : null;
        if (transportOrderIds != null && transportOrderIds.isEmpty()) {
            return emptyResponse();
        }
        List<EnrollmentOrderEntity> orders = listOrders(query, transportOrderIds);
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
        Set<Long> itemIds = items.stream().map(EnrollmentOrderItemEntity::getId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<ParticipantCourseKey, EnrollmentOrderCourseRefundStateEntity> refundStateByParticipantCourse = itemIds.isEmpty()
                ? Collections.emptyMap()
                : enrollmentOrderCourseRefundStateMapper.selectList(new LambdaQueryWrapper<EnrollmentOrderCourseRefundStateEntity>()
                        .in(EnrollmentOrderCourseRefundStateEntity::getEnrollmentOrderItemId, itemIds)
                        .eq(EnrollmentOrderCourseRefundStateEntity::getIsDeleted, 0))
                    .stream().filter(state -> state.getEnrollmentOrderItemId() != null && state.getStudentId() != null)
                    .collect(Collectors.toMap(state -> new ParticipantCourseKey(
                            state.getEnrollmentOrderItemId(), state.getStudentId()),
                            Function.identity(), (left, right) -> left));

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
        Map<Long, Long> privateLessonGroupIdByOrderItemId = privateLessonGroups.stream()
                .filter(group -> group.getEnrollmentOrderItemId() != null && group.getId() != null)
                .collect(Collectors.toMap(
                        PrivateLessonGroupEntity::getEnrollmentOrderItemId,
                        PrivateLessonGroupEntity::getId,
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<Long, Long> unambiguousPrivateLessonGroupIdByOrderId = privateLessonGroups.stream()
                .filter(group -> group.getEnrollmentOrderId() != null && group.getId() != null)
                .collect(Collectors.groupingBy(PrivateLessonGroupEntity::getEnrollmentOrderId,
                        LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .filter(entry -> entry.getValue().size() == 1)
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get(0).getId(),
                        (left, right) -> left, LinkedHashMap::new));
        Map<Long, Integer> privateLessonParticipantCountByOrderItemId = privateLessonGroups.stream()
                .filter(group -> group.getEnrollmentOrderItemId() != null && group.getParticipantCount() != null)
                .collect(Collectors.toMap(
                        PrivateLessonGroupEntity::getEnrollmentOrderItemId,
                        PrivateLessonGroupEntity::getParticipantCount,
                        Math::max,
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

        Map<Long, PaymentRecordEntity> paymentRecordsById = paymentRecords.stream()
                .collect(Collectors.toMap(PaymentRecordEntity::getId, Function.identity(), (left, right) -> left));
        List<PaymentAllocationEntity> paymentAllocations = itemIds.isEmpty()
                ? Collections.emptyList()
                : paymentAllocationMapper.selectList(new LambdaQueryWrapper<PaymentAllocationEntity>()
                        .in(PaymentAllocationEntity::getEnrollmentOrderItemId, itemIds));
        Map<PaymentParticipantItemKey, BigDecimal> paidAmountByParticipantAndItem = new LinkedHashMap<>();
        Set<PaymentParticipantKey> participantsWithCourseAllocations = new LinkedHashSet<>();
        Set<ParticipantCourseKey> refundedParticipantCourses = new LinkedHashSet<>();
        // 退款分摊标记到学生和课程，供 toRow 保留原报名行并单独归入退款区。
        Map<PaymentParticipantKey, Long> studentIdByPaymentParticipant = orderParticipants.stream()
                .filter(participant -> participant.getParticipantIndex() != null && participant.getStudentId() != null)
                .collect(Collectors.toMap(
                        participant -> new PaymentParticipantKey(participant.getEnrollmentOrderId(), participant.getParticipantIndex()),
                        EnrollmentOrderParticipantEntity::getStudentId,
                        (left, right) -> left,
                        LinkedHashMap::new));
        for (PaymentAllocationEntity allocation : paymentAllocations) {
            PaymentRecordEntity payment = paymentRecordsById.get(allocation.getPaymentRecordId());
            if (payment == null || allocation.getEnrollmentOrderItemId() == null) {
                continue;
            }
            if ("REFUND".equalsIgnoreCase(payment.getPaymentType())
                    && !"VOIDED".equalsIgnoreCase(payment.getPaymentStatus())
                    && amount(allocation.getAmount()).signum() < 0) {
                PaymentParticipantKey participantKey = payment.getParticipantIndex() == null ? null
                        : new PaymentParticipantKey(payment.getEnrollmentOrderId(), payment.getParticipantIndex());
                Long studentId = participantKey == null ? null : studentIdByPaymentParticipant.get(participantKey);
                EnrollmentOrderEntity order = orderMap.get(payment.getEnrollmentOrderId());
                if (studentId == null && (payment.getParticipantIndex() == null || payment.getParticipantIndex() == 1)) {
                    studentId = order == null ? null : order.getStudentId();
                }
                if (studentId != null) {
                    refundedParticipantCourses.add(new ParticipantCourseKey(allocation.getEnrollmentOrderItemId(), studentId));
                }
            }
            if (payment.getParticipantIndex() == null) {
                continue;
            }
            PaymentParticipantKey participantKey = new PaymentParticipantKey(
                    payment.getEnrollmentOrderId(), payment.getParticipantIndex());
            PaymentParticipantItemKey itemKey = new PaymentParticipantItemKey(
                    participantKey, allocation.getEnrollmentOrderItemId());
            paidAmountByParticipantAndItem.merge(itemKey, amount(allocation.getAmount()), BigDecimal::add);
            participantsWithCourseAllocations.add(participantKey);
        }
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
        Context context = buildContext(orders, items, packages);
        // 行级金额分摊：同一订单的多行按「参与人 → 订单项」分摊整单金额，避免汇总时金额按行数翻倍。
        Map<Long, Map<AllocationKey, AllocatedAmounts>> allocationByOrderId = new HashMap<>();
        for (EnrollmentOrderEntity order : orders) {
            allocationByOrderId.put(order.getId(), allocateOrderAmounts(order,
                    itemsByOrderId.getOrDefault(order.getId(), Collections.emptyList()),
                    participantsByOrderId.getOrDefault(order.getId(), Collections.emptyList()),
                    paidAmountByParticipant));
        }
        List<DataSummaryResponseVO.RowVO> rows = buildRows(orderMap, itemsByOrderId, packagesByOrderId,
                        participantsByOrderId, privateLessonGroupIdByOrderItemId, privateLessonParticipantCountByOrderItemId,
                        unambiguousPrivateLessonGroupIdByOrderId, privateLessonParticipantCountByOrderId,
                        refundStateByParticipantCourse,
                        packageByOrderItemId,
                        reviewIssuesByOrderId,
                        latestPaymentMethodByOrderId, latestPaymentMethodByParticipant, paidAmountByParticipant,
                        paidAmountByParticipantAndItem, participantsWithCourseAllocations,
                        refundedParticipantCourses,
                        allocationByOrderId,
                        studentById, campusById, context)
                .stream()
                .filter(this::hasDisplayableStudent)
                // 接送名单只保留仍有效的课程权益；同一收据的其他未退款课程继续保留。
                .filter(row -> !Boolean.TRUE.equals(query.getTransportOnly())
                        || !Boolean.TRUE.equals(row.refunded()))
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
        BigDecimal totalOutstanding = sumOutstandingRows(rows);

        return new DataSummaryResponseVO(
                (long) visibleStudentIds.size(),
                totalIncome,
                totalOutstanding,
                buildGroups(rows, orderMap, GroupKind.CAMPUS),
                buildGroups(rows, orderMap, GroupKind.COURSE_TYPE),
                buildGroups(rows, orderMap, GroupKind.CLASS_COURSE),
                buildGroups(rows, orderMap, GroupKind.PAYMENT_STATUS),
                rows,
                logicalCoursePeriods(rows)
        );
    }

    private List<EnrollmentOrderEntity> listOrders(DataSummaryQuery query, Set<Long> transportOrderIds) {
        return enrollmentOrderMapper.selectList(new LambdaQueryWrapper<EnrollmentOrderEntity>()
                .eq(query.getOrderId() != null, EnrollmentOrderEntity::getId, query.getOrderId())
                .in(transportOrderIds != null, EnrollmentOrderEntity::getId, transportOrderIds)
                .ge(query.getSavedAtFrom() != null, EnrollmentOrderEntity::getCreatedAt, query.getSavedAtFrom())
                .lt(query.getSavedAtBefore() != null, EnrollmentOrderEntity::getCreatedAt, query.getSavedAtBefore())
                .eq(query.getRegistrationOperatorUserId() != null,
                        EnrollmentOrderEntity::getRegistrationOperatorUserId, query.getRegistrationOperatorUserId())
                .eq(query.getCampusId() != null, EnrollmentOrderEntity::getCampusId, query.getCampusId())
                .eq(StringUtils.hasText(query.getPaymentStatus()), EnrollmentOrderEntity::getPaymentStatus, query.getPaymentStatus())
                .ge(query.getPeriodStartDate() != null, EnrollmentOrderEntity::getPeriodStartDate, query.getPeriodStartDate())
                .le(query.getPeriodEndDate() != null, EnrollmentOrderEntity::getPeriodEndDate, query.getPeriodEndDate())
                .and(!Boolean.TRUE.equals(query.getIncludeInactiveOrders()), wrapper -> wrapper
                        .ne(EnrollmentOrderEntity::getOrderStatus, "CANCELLED")
                        .or()
                        .eq(EnrollmentOrderEntity::getPaymentStatus, "REFUNDED"))
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
                                 List<CoursePackageEntity> packages) {
        Set<Long> classIds = new LinkedHashSet<>();
        Set<Long> productIds = new LinkedHashSet<>();
        Set<Long> packageIds = packages.stream().map(CoursePackageEntity::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> teacherIds = new LinkedHashSet<>();

        orders.stream().map(EnrollmentOrderEntity::getClassId).filter(Objects::nonNull).forEach(classIds::add);
        items.stream().map(EnrollmentOrderItemEntity::getClassId).filter(Objects::nonNull).forEach(classIds::add);
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
        Set<Long> planIds = items.stream().map(EnrollmentOrderItemEntity::getGroupRegistrationPlanId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, GroupRegistrationPlanEntity> plans = planIds.isEmpty() ? Collections.emptyMap()
                : groupRegistrationPlanMapper.selectBatchIds(planIds).stream()
                        .collect(Collectors.toMap(GroupRegistrationPlanEntity::getId, Function.identity()));
        Set<Long> v2ItemIds = items.stream()
                .filter(item -> item.getGroupRegistrationPlanId() != null && item.getId() != null)
                .map(EnrollmentOrderItemEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<EnrollmentItemStudentKey, ClassEnrollmentEntity> classEnrollments = v2ItemIds.isEmpty()
                ? Collections.emptyMap()
                : classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .in(ClassEnrollmentEntity::getEnrollmentOrderItemId, v2ItemIds)
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0))
                    .stream()
                    .filter(enrollment -> enrollment.getEnrollmentOrderItemId() != null
                            && enrollment.getStudentId() != null)
                    // 收据更正会保留旧权益为 CANCELLED 并创建新权益；报表必须稳定展示有效的新权益。
                    .sorted(classEnrollmentSelectionOrder())
                    .collect(Collectors.toMap(
                            enrollment -> new EnrollmentItemStudentKey(
                                    enrollment.getEnrollmentOrderItemId(), enrollment.getStudentId()),
                            Function.identity(), (first, ignored) -> first, LinkedHashMap::new));
        return new Context(classGroups, products, primaryAssignments, teachers, plans, classEnrollments);
    }

    private Comparator<ClassEnrollmentEntity> classEnrollmentSelectionOrder() {
        return Comparator.comparing((ClassEnrollmentEntity enrollment) ->
                        !"ACTIVE".equalsIgnoreCase(enrollment.getStatus()))
                .thenComparing(ClassEnrollmentEntity::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ClassEnrollmentEntity::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ClassEnrollmentEntity::getId,
                        Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private List<DataSummaryResponseVO.RowVO> buildRows(
            Map<Long, EnrollmentOrderEntity> orderMap,
            Map<Long, List<EnrollmentOrderItemEntity>> itemsByOrderId,
            Map<Long, List<CoursePackageEntity>> packagesByOrderId,
            Map<Long, List<EnrollmentOrderParticipantEntity>> participantsByOrderId,
            Map<Long, Long> privateLessonGroupIdByOrderItemId,
            Map<Long, Integer> privateLessonParticipantCountByOrderItemId,
            Map<Long, Long> unambiguousPrivateLessonGroupIdByOrderId,
            Map<Long, Integer> privateLessonParticipantCountByOrderId,
            Map<ParticipantCourseKey, EnrollmentOrderCourseRefundStateEntity> refundStateByParticipantCourse,
            Map<Long, CoursePackageEntity> packageByOrderItemId,
            Map<Long, List<String>> reviewIssuesByOrderId,
            Map<Long, String> latestPaymentMethodByOrderId,
            Map<PaymentParticipantKey, String> latestPaymentMethodByParticipant,
            Map<PaymentParticipantKey, BigDecimal> paidAmountByParticipant,
            Map<PaymentParticipantItemKey, BigDecimal> paidAmountByParticipantAndItem,
            Set<PaymentParticipantKey> participantsWithCourseAllocations,
            Set<ParticipantCourseKey> refundedParticipantCourses,
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
            List<EnrollmentOrderItemEntity> orderItems = itemsByOrderId.get(order.getId());
            if (orderItems == null || orderItems.isEmpty()) {
                Integer privateLessonParticipantCount = resolvePrivateLessonParticipantCount(
                        null, participants, privateLessonParticipantCountByOrderItemId);
                boolean sharedPrivateLesson = isSharedPrivateLesson(privateLessonParticipantCount);
                for (EnrollmentOrderParticipantEntity participant : displayParticipants) {
                    CoursePackageEntity coursePackage = participant == null
                            ? firstPackage(packagesByOrderId.get(order.getId()))
                            : findPackageForParticipant(packagesByOrderId.get(order.getId()), participant, null);
                    rows.add(toRow(order, null, Collections.emptyList(), coursePackage, participant,
                            null,
                            unambiguousPrivateLessonGroupIdByOrderId.get(order.getId()),
                            sharedPrivateLesson ? privateLessonParticipantCount : null,
                            reviewIssuesByOrderId.getOrDefault(order.getId(), Collections.emptyList()),
                            latestPaymentMethodByOrderId.get(order.getId()), latestPaymentMethodByParticipant,
                            paidAmountByParticipant, paidAmountByParticipantAndItem,
                            participantsWithCourseAllocations,
                            refundedParticipantCourses,
                            allocationByOrderId,
                            studentById, campusById, context, today, soonLimit));
                }
                continue;
            }
            for (EnrollmentOrderItemEntity item : orderItems) {
                Integer privateLessonParticipantCount = privateLessonParticipantCountByOrderItemId.get(item.getId());
                if (privateLessonParticipantCount == null) {
                    privateLessonParticipantCount = privateLessonParticipantCountByOrderId.get(order.getId());
                }
                if (privateLessonParticipantCount == null && "ONE_ON_ONE".equalsIgnoreCase(item.getCourseType())) {
                    privateLessonParticipantCount = resolvePrivateLessonParticipantCount(
                            null, participants, Collections.emptyMap());
                }
                boolean sharedPrivateLesson = isSharedPrivateLesson(privateLessonParticipantCount);
                Long privateLessonGroupId = privateLessonGroupIdByOrderItemId.get(item.getId());
                if (privateLessonGroupId == null && countOneOnOneItems(orderItems) == 1) {
                    privateLessonGroupId = unambiguousPrivateLessonGroupIdByOrderId.get(order.getId());
                }
                CoursePackageEntity defaultPackage = firstNonNull(packageByOrderItemId.get(item.getId()),
                        findPackageForItem(packagesByOrderId.get(order.getId()), item));
                for (EnrollmentOrderParticipantEntity participant : displayParticipants) {
                    CoursePackageEntity coursePackage = participant == null
                            ? defaultPackage
                            : findPackageForParticipant(packagesByOrderId.get(order.getId()), participant, item);
                    Long displayStudentId = participant == null ? order.getStudentId() : participant.getStudentId();
                    EnrollmentOrderCourseRefundStateEntity refundState = displayStudentId == null ? null
                            : refundStateByParticipantCourse.get(new ParticipantCourseKey(item.getId(), displayStudentId));
                    rows.add(toRow(order, item, orderItems, coursePackage, participant,
                            refundState,
                            privateLessonGroupId,
                            sharedPrivateLesson ? privateLessonParticipantCount : null,
                            reviewIssuesByOrderId.getOrDefault(order.getId(), Collections.emptyList()),
                            latestPaymentMethodByOrderId.get(order.getId()), latestPaymentMethodByParticipant,
                            paidAmountByParticipant, paidAmountByParticipantAndItem,
                            participantsWithCourseAllocations,
                            refundedParticipantCourses,
                            allocationByOrderId,
                            studentById, campusById, context, today, soonLimit));
                }
            }
        }
        return rows;
    }

    private long countOneOnOneItems(List<EnrollmentOrderItemEntity> items) {
        return items.stream().filter(item -> "ONE_ON_ONE".equalsIgnoreCase(item.getCourseType())).count();
    }

    private Integer resolvePrivateLessonParticipantCount(Long orderItemId,
                                                         List<EnrollmentOrderParticipantEntity> participants,
                                                         Map<Long, Integer> privateLessonParticipantCountByOrderItemId) {
        Integer groupCount = orderItemId == null ? null : privateLessonParticipantCountByOrderItemId.get(orderItemId);
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

    /**
     * 上课形态（上午/下午/全天）：优先订单项快照，历史订单回退班课报名记录。
     * NONE 表示该课程维度不适用（如 1 对 1），统一返回 null 由前端展示空值。
     */
    private String resolveAttendanceOption(EnrollmentOrderItemEntity item) {
        String option = item == null ? null : item.getAttendanceOption();
        return option == null || "NONE".equals(option) ? null : option;
    }

    private DataSummaryResponseVO.RowVO toRow(EnrollmentOrderEntity order,
                                              EnrollmentOrderItemEntity item,
                                              List<EnrollmentOrderItemEntity> orderItems,
                                              CoursePackageEntity coursePackage,
                                              EnrollmentOrderParticipantEntity participant,
                                              EnrollmentOrderCourseRefundStateEntity refundState,
                                              Long privateLessonGroupId,
                                              Integer privateLessonParticipantCount,
                                              List<String> reviewIssueCodes,
                                              String paymentMethod,
                                              Map<PaymentParticipantKey, String> latestPaymentMethodByParticipant,
                                              Map<PaymentParticipantKey, BigDecimal> paidAmountByParticipant,
                                              Map<PaymentParticipantItemKey, BigDecimal> paidAmountByParticipantAndItem,
                                              Set<PaymentParticipantKey> participantsWithCourseAllocations,
                                              Set<ParticipantCourseKey> refundedParticipantCourses,
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
                order.getClassId());
        ClassGroupEntity classGroup = classId == null ? null : context.classGroups().get(classId);
        GroupRegistrationPlanEntity registrationPlan = item == null || item.getGroupRegistrationPlanId() == null
                ? null : context.registrationPlans().get(item.getGroupRegistrationPlanId());
        ClassEnrollmentEntity v2Enrollment = item == null || displayStudentId == null
                ? null : context.classEnrollments().get(new EnrollmentItemStudentKey(item.getId(), displayStudentId));
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

        // Data Summary 展示该学生本次报名周期；仅当学生权益有效(ACTIVE/EXPIRED)时信任其日期，
        // 否则回退方案覆盖期/订单数据，避免显示已作废权益(CANCELLED)的过期日期。
        LocalDate periodStartDate = resolveV2PeriodStartDate(registrationPlan, v2Enrollment,
                item, classGroup, coursePackage, order);
        LocalDate periodEndDate = resolveV2PeriodEndDate(registrationPlan, v2Enrollment,
                item, coursePackage, classGroup, order);
        BigDecimal rowStoppedConsumedAmount = resolveStoppedConsumedAmount(item, refundState);
        Integer rowStoppedSessionCount = resolveStoppedSessionCount(item, refundState);
        LocalDate stoppedStart = refundState != null && refundState.getStoppedAt() != null
                ? refundState.getStoppedPeriodStartDate() : item == null ? null : item.getStoppedPeriodStartDate();
        LocalDate stoppedEnd = refundState != null && refundState.getStoppedAt() != null
                ? refundState.getStoppedPeriodEndDate() : item == null ? null : item.getStoppedPeriodEndDate();
        boolean stoppedRecord = "STOPPED".equals(resolveDisplayItemStatus(item, refundState));
        boolean refundedRecord = "REFUNDED".equalsIgnoreCase(order.getPaymentStatus())
                || item != null && refundedParticipantCourses.contains(new ParticipantCourseKey(item.getId(), displayStudentId));
        if (stoppedRecord && item != null) {
            periodStartDate = firstNonNull(stoppedStart, periodStartDate);
            periodEndDate = firstNonNull(stoppedEnd, periodEndDate);
        }
        LocalDate expiryDate = periodEndDate;
        boolean expiringSoon = expiryDate != null && !expiryDate.isBefore(today) && !expiryDate.isAfter(soonLimit);
        boolean oneOnOne = "ONE_ON_ONE".equals(courseType) || isOneOnOne(product, coursePackage);
        boolean sharedParticipantOrder = isSharedPrivateLesson(privateLessonParticipantCount) && participant != null;
        boolean primaryParticipant = participant == null
                || Objects.equals(displayStudentId, order.getStudentId())
                || Objects.equals(participant.getParticipantIndex(), 1);
        PaymentParticipantKey paymentParticipantKey = participant == null || participant.getParticipantIndex() == null
                ? null
                : new PaymentParticipantKey(order.getId(), participant.getParticipantIndex());
        String participantPaymentMethod = paymentParticipantKey == null
                ? null
                : latestPaymentMethodByParticipant.get(paymentParticipantKey);
        String resolvedPaymentMethod = sharedParticipantOrder
                ? firstText(participantPaymentMethod, primaryParticipant ? paymentMethod : null)
                : firstText(participantPaymentMethod, paymentMethod);

        BigDecimal rowLinePriceAmount = item == null ? null : amount(item.getPriceAmount());
        BigDecimal rowPickupFeeAmount = amount(order.getPickupFeeAmount());
        BigDecimal rowExtraFeeAmount = amount(order.getExtraFeeAmount());
        BigDecimal rowDiscountAmount = amount(order.getDiscountAmount());
        BigDecimal rowAmountDue = amount(order.getAmountDue());
        BigDecimal rowPaidAmount = amount(order.getReceivedAmount());
        BigDecimal rowBalanceAmount = amount(order.getBalanceAmount());
        // Data Summary 按报名行展示课时；合并课包会改绑到最新订单，不能用课包总量覆盖历史报名快照。
        Double rowTotalHours = null;
        if (oneOnOne && item != null && item.getOneOnOneHours() != null) {
            rowTotalHours = item.getOneOnOneHours().doubleValue();
        } else if (coursePackage != null) {
            rowTotalHours = UnitConverter.toHoursDecimal(coursePackage.getTotalUnits());
        }
        AllocatedAmounts allocated = allocatedAmounts(allocationByOrderId, order.getId(), participant, item);
        if (!sharedParticipantOrder) {
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
        if (sharedParticipantOrder) {
            rowLinePriceAmount = item == null
                    ? resolveParticipantGrossAmount(participant, coursePackage, null, order, privateLessonParticipantCount)
                    : splitAmount(item.getPriceAmount(), privateLessonParticipantCount,
                            participant.getParticipantIndex(), WholeDollarBilling.appliesTo(order));
            rowDiscountAmount = participantCourseDiscountAmount(order, participant, item, orderItems,
                    privateLessonParticipantCount);
            BigDecimal courseFeeAmount = item == null
                    ? amount(order.getPickupFeeAmount()).add(amount(order.getDropoffFeeAmount())).add(amount(order.getExtraFeeAmount()))
                    : amount(item.getAmountDue()).subtract(amount(item.getPriceAmount()))
                            .add(amount(item.getDiscountAmount())).max(ZERO_AMOUNT);
            rowPickupFeeAmount = primaryParticipant
                    ? item == null ? amount(order.getPickupFeeAmount())
                            : allocateOrderAmountToItem(order, order.getPickupFeeAmount(), orderItems, item)
                    : ZERO_AMOUNT;
            rowExtraFeeAmount = primaryParticipant
                    ? courseFeeAmount.subtract(rowPickupFeeAmount).max(ZERO_AMOUNT)
                    : ZERO_AMOUNT;
            rowAmountDue = rowLinePriceAmount.add(rowPickupFeeAmount).add(rowExtraFeeAmount)
                    .subtract(rowDiscountAmount).max(ZERO_AMOUNT).setScale(2, RoundingMode.HALF_UP);
            rowPaidAmount = participantCoursePaidAmount(participant, paymentParticipantKey, item, orderItems,
                    paidAmountByParticipant, paidAmountByParticipantAndItem, participantsWithCourseAllocations,
                    allocated, order, primaryParticipant);
            rowBalanceAmount = rowAmountDue.subtract(rowPaidAmount).setScale(2, RoundingMode.HALF_UP);
        }
        // 课程明细存在时，接送状态必须以课程自己的快照为准。
        // 不能根据“明细是否有非空接送字段”来决定是否回退订单级字段，否则课程 A 的接送设置
        // 会被错误复制到同一订单的课程 B。只有没有课程明细的旧订单才使用订单级快照兼容展示。
        Boolean pickupRequired = item != null
                ? Boolean.TRUE.equals(item.getPickupRequiredSnapshot())
                : primaryParticipant
                ? Boolean.TRUE.equals(order.getPickupRequiredSnapshot())
                : Boolean.TRUE.equals(student == null ? null : student.getPickupRequired());
        Boolean dropoffRequired = item != null
                ? Boolean.TRUE.equals(item.getDropoffRequiredSnapshot())
                : primaryParticipant
                ? Boolean.TRUE.equals(order.getDropoffRequiredSnapshot())
                : Boolean.TRUE.equals(student == null ? null : student.getDropoffRequired());
        BigDecimal rowDropoffFeeAmount = amount(order.getDropoffFeeAmount());
        String rowPickupAddress = order.getPickupAddressSnapshot();
        String rowDropoffAddress = order.getDropoffAddressSnapshot();
        if (item != null) {
            rowPickupFeeAmount = amount(item.getPickupFeeAmount());
            rowDropoffFeeAmount = amount(item.getDropoffFeeAmount());
            rowExtraFeeAmount = amount(item.getExtraFeeAmount());
            rowPickupAddress = item.getPickupAddressSnapshot();
            rowDropoffAddress = item.getDropoffAddressSnapshot();
        }
        if (stoppedRecord && item != null && rowStoppedConsumedAmount != null) {
            // 停课行按已消耗课时金额展示；接送费和原优惠不属于已消耗课时费用。
            rowLinePriceAmount = amount(rowStoppedConsumedAmount);
            rowPickupFeeAmount = ZERO_AMOUNT;
            rowDropoffFeeAmount = ZERO_AMOUNT;
            rowExtraFeeAmount = ZERO_AMOUNT;
            rowDiscountAmount = ZERO_AMOUNT;
            rowAmountDue = amount(rowStoppedConsumedAmount);
            rowBalanceAmount = rowAmountDue.subtract(rowPaidAmount).setScale(2, RoundingMode.HALF_UP);
        }
        // 退款报名保留原始课程与付款信息，但退款后的余额不再作为学生欠款展示。
        if (refundedRecord) {
            rowBalanceAmount = ZERO_AMOUNT;
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

        // Data Summary 与学生账号页共用当前学生主档姓名；历史报名快照仅作为学生记录缺失时的兜底。
        String studentName = firstText(student == null ? null : student.getStudentName(),
                primaryParticipant ? order.getStudentNameSnapshot()
                        : participant == null ? null : participant.getStudentNameSnapshot());
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
                item == null ? null : item.getGradeBand(),
                order.getSignupDate(),
                periodStartDate,
                periodEndDate,
                item == null ? null : item.getPeriodLabel(),
                registrationPlan == null ? null : registrationPlan.getTermCode(),
                resolveAttendanceOption(item),
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
                rowPickupAddress,
                dropoffRequired,
                rowDropoffAddress,
                pickupDropoffLabel(pickupRequired, dropoffRequired),
                resolvedPaymentMethod,
                item == null ? order.getPaymentStatus() : resolvePaymentStatus(rowAmountDue, rowPaidAmount),
                order.getPaymentStatus(),
                rowLinePriceAmount,
                rowPickupFeeAmount,
                rowDropoffFeeAmount,
                rowExtraFeeAmount,
                rowDiscountAmount,
                rowAmountDue,
                rowPaidAmount,
                displayBalanceAmount(rowBalanceAmount),
                rowTotalHours,
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
                oneOnOne ? privateLessonParticipantCount : null,
                resolveDisplayItemStatus(item, refundState),
                refundedRecord,
                rowStoppedConsumedAmount,
                rowStoppedSessionCount,
                order.getCreatedAt(),
                order.getRegistrationOperatorUserId() != null ? order.getRegistrationOperatorUserId()
                        : (order.getCashierUserId() != null ? order.getCashierUserId() : order.getCreatedBy()),
                order.getRegistrationOperatorName() != null ? order.getRegistrationOperatorName() : order.getHandledByName(),
                v2Enrollment != null && v2Enrollment.getLogicalCourseId() != null
                        ? v2Enrollment.getLogicalCourseId() : item == null ? null : item.getLogicalCourseId()
        );
    }

    private String resolveDisplayItemStatus(EnrollmentOrderItemEntity item,
                                            EnrollmentOrderCourseRefundStateEntity refundState) {
        if (item != null && "STOPPED".equalsIgnoreCase(item.getItemStatus())) {
            return "STOPPED";
        }
        if (refundState != null && "STOPPED".equalsIgnoreCase(refundState.getItemStatus())) {
            return "STOPPED";
        }
        return item == null ? null : item.getItemStatus();
    }

    private BigDecimal resolveStoppedConsumedAmount(EnrollmentOrderItemEntity item,
                                                    EnrollmentOrderCourseRefundStateEntity refundState) {
        if (refundState != null && refundState.getStoppedAt() != null) {
            return amount(refundState.getStoppedConsumedAmount());
        }
        return item == null ? null : item.getStoppedConsumedAmount();
    }

    private Integer resolveStoppedSessionCount(EnrollmentOrderItemEntity item,
                                               EnrollmentOrderCourseRefundStateEntity refundState) {
        if (refundState != null && refundState.getStoppedAt() != null) {
            return refundState.getStoppedSessionCount();
        }
        return item == null ? null : item.getStoppedSessionCount();
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
        return splitAmount(totalAmount, participantCount, participant.getParticipantIndex(),
                WholeDollarBilling.appliesTo(order));
    }

    private BigDecimal participantCourseDiscountAmount(EnrollmentOrderEntity order,
                                                        EnrollmentOrderParticipantEntity participant,
                                                        EnrollmentOrderItemEntity item,
                                                        List<EnrollmentOrderItemEntity> orderItems,
                                                        Integer participantCount) {
        if (participant == null) {
            return item == null ? ZERO_AMOUNT : splitAmount(item.getDiscountAmount(), participantCount, null,
                    WholeDollarBilling.appliesTo(order));
        }
        BigDecimal participantDiscount = amount(participant.getDiscountAmount());
        if (item == null) {
            return participantDiscount;
        }
        List<Map<String, Object>> details = jsonHelper.fromJson(participant.getDiscountDetail(),
                new TypeReference<List<Map<String, Object>>>() { });
        boolean hasCourseAssignments = details != null && details.stream()
                .filter(Objects::nonNull)
                .anyMatch(detail -> detail.get("orderItemLineNo") != null);
        if (hasCourseAssignments) {
            return details.stream()
                    .filter(Objects::nonNull)
                    .filter(detail -> Objects.equals(toInteger(detail.get("orderItemLineNo")), item.getLineNo()))
                    .map(detail -> amount(toBigDecimal(detail.get("amount"))))
                    .reduce(ZERO_AMOUNT, BigDecimal::add);
        }
        if (orderItems == null || orderItems.size() <= 1) {
            return participantDiscount;
        }
        BigDecimal totalWeight = orderItems.stream().map(course -> amount(course.getPriceAmount()))
                .reduce(ZERO_AMOUNT, BigDecimal::add);
        BigDecimal assigned = ZERO_AMOUNT;
        for (int index = 0; index < orderItems.size(); index++) {
            EnrollmentOrderItemEntity course = orderItems.get(index);
            BigDecimal share = index == orderItems.size() - 1
                    ? participantDiscount.subtract(assigned)
                    : totalWeight.compareTo(ZERO_AMOUNT) <= 0
                    ? participantDiscount.divide(BigDecimal.valueOf(orderItems.size()),
                            WholeDollarBilling.appliesTo(order) ? 0 : 2, RoundingMode.DOWN)
                    : participantDiscount.multiply(amount(course.getPriceAmount()))
                    .divide(totalWeight, WholeDollarBilling.appliesTo(order) ? 0 : 2, RoundingMode.DOWN);
            if (Objects.equals(course.getId(), item.getId())) {
                return share.setScale(2, RoundingMode.HALF_UP);
            }
            assigned = assigned.add(share);
        }
        return ZERO_AMOUNT;
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value == null) {
            return ZERO_AMOUNT;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException exception) {
            return ZERO_AMOUNT;
        }
    }

    private BigDecimal allocateOrderAmountToItem(EnrollmentOrderEntity order, BigDecimal totalAmount,
                                                 List<EnrollmentOrderItemEntity> items,
                                                 EnrollmentOrderItemEntity targetItem) {
        if (items == null || items.isEmpty()) {
            return ZERO_AMOUNT;
        }
        List<BigDecimal> shares = allocateByWeights(totalAmount, itemPriceWeights(items),
                WholeDollarBilling.appliesTo(order));
        for (int index = 0; index < items.size(); index++) {
            if (Objects.equals(items.get(index).getId(), targetItem.getId())) {
                return shares.get(index);
            }
        }
        return ZERO_AMOUNT;
    }

    /**
     * 优先使用课程级付款分摊；混有历史未分摊付款时，将差额按课程金额分配，避免丢失或重复统计。
     */
    private BigDecimal participantCoursePaidAmount(EnrollmentOrderParticipantEntity participant,
                                                  PaymentParticipantKey participantKey,
                                                  EnrollmentOrderItemEntity item,
                                                  List<EnrollmentOrderItemEntity> orderItems,
                                                  Map<PaymentParticipantKey, BigDecimal> paidAmountByParticipant,
                                                  Map<PaymentParticipantItemKey, BigDecimal> paidAmountByParticipantAndItem,
                                                  Set<PaymentParticipantKey> participantsWithCourseAllocations,
                                                  AllocatedAmounts allocated,
                                                  EnrollmentOrderEntity order,
                                                  boolean primaryParticipant) {
        if (participantKey == null || item == null
                || !participantsWithCourseAllocations.contains(participantKey)) {
            if (allocated != null) {
                return allocated.paidAmount();
            }
            BigDecimal participantPaid = resolveParticipantPaidAmount(participant, participantKey,
                    paidAmountByParticipant, order, primaryParticipant);
            return item == null ? participantPaid : allocateOrderAmountToItem(order, participantPaid, orderItems, item);
        }

        BigDecimal coursePaid = amount(paidAmountByParticipantAndItem.get(
                new PaymentParticipantItemKey(participantKey, item.getId())));
        BigDecimal explicitPaid = paidAmountByParticipantAndItem.entrySet().stream()
                .filter(entry -> Objects.equals(entry.getKey().participant(), participantKey))
                .map(Map.Entry::getValue)
                .map(value -> amount(value))
                .reduce(ZERO_AMOUNT, BigDecimal::add);
        BigDecimal participantPaid = resolveParticipantPaidAmount(participant, participantKey,
                paidAmountByParticipant, order, primaryParticipant);
        BigDecimal legacyUnallocatedPaid = participantPaid.subtract(explicitPaid).max(ZERO_AMOUNT);
        return coursePaid.add(allocateOrderAmountToItem(order, legacyUnallocatedPaid, orderItems, item));
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

    private BigDecimal splitAmount(BigDecimal totalAmount, Integer participantCount, Integer participantIndex,
                                   boolean wholeDollars) {
        BigDecimal total = amount(totalAmount);
        int count = participantCount == null || participantCount < 1 ? 1 : participantCount;
        if (count == 1) {
            return total;
        }
        BigDecimal base = total.divide(BigDecimal.valueOf(count), wholeDollars ? 0 : 2, RoundingMode.DOWN)
                .setScale(2);
        int index = participantIndex == null ? 1 : participantIndex;
        if (index >= count) {
            return total.subtract(base.multiply(BigDecimal.valueOf(count - 1)))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return base;
    }

    /** 每行对应一条订单课程项；报名起始日优先使用该课程项快照。 */
    private LocalDate resolvePeriodStartDate(EnrollmentOrderItemEntity item,
                                             ClassGroupEntity classGroup,
                                             CoursePackageEntity coursePackage,
                                             EnrollmentOrderEntity order) {
        return firstNonNull(item == null ? null : item.getStartDate(),
                coursePackage == null ? null : coursePackage.getStartDate(),
                order.getPeriodStartDate(),
                classGroup == null ? null : classGroup.getStartDate());
    }

    /** 结束日从本报名项购买内容计算，避免旧在册快照覆盖当前报名数据。 */
    private LocalDate resolvePeriodEndDate(EnrollmentOrderItemEntity item,
                                           CoursePackageEntity coursePackage,
                                           ClassGroupEntity classGroup,
                                           EnrollmentOrderEntity order) {
        LocalDate calculatedExpiry = calculateGroupPackageExpiry(item, classGroup, order);
        if (calculatedExpiry != null) {
            return calculatedExpiry;
        }
        if (coursePackage != null && coursePackage.getEstimatedEndDate() != null) {
            return coursePackage.getEstimatedEndDate();
        }
        return firstNonNull(classGroup == null ? null : classGroup.getEndDate(), order.getPeriodEndDate());
    }

    /** 班课V2以订单权益快照为准，避免旧计费规则重新推算报名周期。 */
    private LocalDate resolveV2PeriodEndDate(GroupRegistrationPlanEntity plan,
                                             ClassEnrollmentEntity enrollment,
                                             EnrollmentOrderItemEntity item,
                                             CoursePackageEntity coursePackage,
                                             ClassGroupEntity classGroup,
                                             EnrollmentOrderEntity order) {
        if (plan != null) {
            if (isActiveEnrollment(enrollment) && enrollment.getExpiryDate() != null) return enrollment.getExpiryDate();
            if ("FIXED_DATES".equalsIgnoreCase(plan.getCoverageType()) && plan.getCoverageEndDate() != null) {
                return plan.getCoverageEndDate();
            }
        }
        return resolvePeriodEndDate(item, coursePackage, classGroup, order);
    }

    /** 报名周期起点：仅信任有效权益(ACTIVE/EXPIRED)的日期；否则回退方案覆盖期/订单数据。 */
    private LocalDate resolveV2PeriodStartDate(GroupRegistrationPlanEntity plan,
                                               ClassEnrollmentEntity enrollment,
                                               EnrollmentOrderItemEntity item,
                                               ClassGroupEntity classGroup,
                                               CoursePackageEntity coursePackage,
                                               EnrollmentOrderEntity order) {
        if (isActiveEnrollment(enrollment) && enrollment.getEnrollmentDate() != null) {
            return enrollment.getEnrollmentDate();
        }
        if (plan != null && "FIXED_DATES".equalsIgnoreCase(plan.getCoverageType())
                && plan.getCoverageStartDate() != null) {
            return plan.getCoverageStartDate();
        }
        return resolvePeriodStartDate(item, classGroup, coursePackage, order);
    }

    /** 权益是否为有效在册（ACTIVE）或历史有效（EXPIRED）；CANCELLED 等作废状态不予信任。 */
    private boolean isActiveEnrollment(ClassEnrollmentEntity enrollment) {
        return enrollment != null && ("ACTIVE".equalsIgnoreCase(enrollment.getStatus())
                || "EXPIRED".equalsIgnoreCase(enrollment.getStatus()));
    }

    /** 缺少有效在册快照时，按订单项购买的计费单位和数量恢复班课周期。 */
    private LocalDate calculateGroupPackageExpiry(EnrollmentOrderItemEntity item,
                                                  ClassGroupEntity classGroup,
                                                  EnrollmentOrderEntity order) {
        if (item == null || classGroup == null || !StringUtils.hasText(item.getPackageUnit())
                || item.getPackageQuantity() == null) {
            return null;
        }
        try {
            ClassBillingService.ClassBilling billing = classBillingService.compute(classGroup,
                    item.getStartDate(), order.getSignupDate(), item.getPackageUnit(), item.getPackageQuantity());
            return billing == null ? null : billing.expiryDate();
        } catch (com.classdeduction.system.common.exception.BusinessException exception) {
            // 旧课包若超出当前计费规则范围，继续使用订单/班级日期作为兼容回退。
            return null;
        }
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
            case PAYMENT_STATUS -> new GroupLabel(firstText(row.orderPaymentStatus(), "UNKNOWN"), firstText(row.orderPaymentStatus(), "Unknown"));
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

    /** 汇总行级欠款，排除退款导致的余额变化并沿用负数表示欠款的报表口径。 */
    private BigDecimal sumOutstandingRows(List<DataSummaryResponseVO.RowVO> rows) {
        return rows.stream()
                .map(DataSummaryResponseVO.RowVO::balanceAmount)
                .filter(Objects::nonNull)
                .map(value -> value.min(ZERO_AMOUNT))
                .reduce(ZERO_AMOUNT, BigDecimal::add)
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

    /** 课程 Period 从本次报名的有效权益读取，避免其他订单或已退款购买延长期限。 */
    private List<DataSummaryResponseVO.LogicalCoursePeriodVO> logicalCoursePeriods(
            List<DataSummaryResponseVO.RowVO> rows) {
        Map<Long, Long> orderIdByItemId = rows.stream()
                .filter(row -> row.orderItemId() != null && row.orderId() != null)
                .collect(Collectors.toMap(DataSummaryResponseVO.RowVO::orderItemId,
                        DataSummaryResponseVO.RowVO::orderId, (first, ignored) -> first));
        Set<Long> logicalIds = rows.stream()
                .filter(row -> row.logicalCourseId() != null && !"ONE_ON_ONE".equals(row.courseType()))
                .map(DataSummaryResponseVO.RowVO::logicalCourseId)
                .collect(Collectors.toSet());
        if (logicalIds.isEmpty()) return List.of();
        List<ClassEnrollmentEntity> validEntitlements = classEnrollmentMapper.selectList(
                new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .in(ClassEnrollmentEntity::getLogicalCourseId, logicalIds)
                        .in(ClassEnrollmentEntity::getStatus, "ACTIVE", "EXPIRED")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0));
        Map<String, List<ClassEnrollmentEntity>> byCourse = validEntitlements.stream()
                .filter(item -> item.getStudentId() != null && item.getEnrollmentDate() != null
                        && item.getExpiryDate() != null
                        && orderIdByItemId.containsKey(item.getEnrollmentOrderItemId()))
                .collect(Collectors.groupingBy(item -> orderIdByItemId.get(item.getEnrollmentOrderItemId())
                        + ":" + item.getStudentId() + ":" + item.getLogicalCourseId()));
        List<DataSummaryResponseVO.LogicalCoursePeriodVO> periods = new ArrayList<>();
        for (List<ClassEnrollmentEntity> course : byCourse.values()) {
            course.sort(Comparator.comparing(ClassEnrollmentEntity::getEnrollmentDate));
            List<DataSummaryResponseVO.PeriodSegmentVO> segments = new ArrayList<>();
            for (ClassEnrollmentEntity entitlement : course) {
                LocalDate start = entitlement.getEnrollmentDate();
                LocalDate end = entitlement.getExpiryDate();
                if (!segments.isEmpty()) {
                    DataSummaryResponseVO.PeriodSegmentVO last = segments.get(segments.size() - 1);
                    boolean contiguous = !start.isAfter(last.endDate().plusDays(1))
                            || "MONTHLY".equals(entitlement.getBillingType())
                            && !YearMonth.from(start).isAfter(YearMonth.from(last.endDate()).plusMonths(1));
                    if (contiguous) {
                        segments.set(segments.size() - 1, new DataSummaryResponseVO.PeriodSegmentVO(
                                last.startDate(), end.isAfter(last.endDate()) ? end : last.endDate()));
                        continue;
                    }
                }
                segments.add(new DataSummaryResponseVO.PeriodSegmentVO(start, end));
            }
            ClassEnrollmentEntity first = course.get(0);
            periods.add(new DataSummaryResponseVO.LogicalCoursePeriodVO(
                    orderIdByItemId.get(first.getEnrollmentOrderItemId()),
                    first.getStudentId(), first.getLogicalCourseId(),
                    segments.get(0).startDate(), segments.get(segments.size() - 1).endDate(), segments));
        }
        return periods;
    }

    private DataSummaryResponseVO emptyResponse() {
        return new DataSummaryResponseVO(0L, ZERO_AMOUNT, ZERO_AMOUNT,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private String pickupDropoffLabel(Boolean pickupRequired, Boolean dropoffRequired) {
        List<String> labels = new ArrayList<>();
        if (Boolean.TRUE.equals(pickupRequired)) {
            labels.add("Pick up");
        }
        if (Boolean.TRUE.equals(dropoffRequired)) {
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
            Map<Long, TeacherEntity> teachers,
            Map<Long, GroupRegistrationPlanEntity> registrationPlans,
            Map<EnrollmentItemStudentKey, ClassEnrollmentEntity> classEnrollments
    ) {
    }

    private record EnrollmentItemStudentKey(Long orderItemId, Long studentId) {
    }

    private record PaymentParticipantKey(Long orderId, Integer participantIndex) {
    }

    private record PaymentParticipantItemKey(PaymentParticipantKey participant, Long orderItemId) {
    }

    private record ParticipantCourseKey(Long orderItemId, Long studentId) {
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
                    amount(order.getReceivedAmount()), true, WholeDollarBilling.appliesTo(order));
            return allocation;
        }
        participantBucketAmounts(order, participants, paidAmountByParticipant).forEach((index, bucket) ->
                putItemAllocation(allocation, index, items, itemWeights,
                        bucket.pickupFeeAmount(), bucket.extraFeeAmount(), bucket.discountAmount(),
                        bucket.amountDue(), bucket.paidAmount(), false, WholeDollarBilling.appliesTo(order)));
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
                                   boolean preferStoredCourseAmounts,
                                   boolean wholeDollars) {
        List<BigDecimal> pickupShares = allocateByWeights(pickupFee, itemWeights, wholeDollars);
        List<BigDecimal> extraFeeShares = allocateByWeights(extraFee, itemWeights, wholeDollars);
        List<BigDecimal> discountShares = storedShares(items, EnrollmentOrderItemEntity::getDiscountAmount,
                discount, itemWeights, preferStoredCourseAmounts, wholeDollars);
        List<BigDecimal> amountDueShares = storedShares(items, EnrollmentOrderItemEntity::getAmountDue,
                amountDue, itemWeights, preferStoredCourseAmounts, wholeDollars);
        List<BigDecimal> paidShares = storedShares(items, EnrollmentOrderItemEntity::getPaidAmount,
                paid, itemWeights, preferStoredCourseAmounts, wholeDollars);
        for (int i = 0; i < items.size(); i++) {
            allocation.put(new AllocationKey(participantIndex, items.get(i).getId()), new AllocatedAmounts(
                    pickupShares.get(i), extraFeeShares.get(i), discountShares.get(i),
                    amountDueShares.get(i), paidShares.get(i)));
        }
    }

    /**
     * 课程行与 Enrollments 共用课程项金额快照；只有旧记录缺少快照时才按课程价格拆分。
     * 不能因为快照合计与收据金额不符而再次分配，否则同一课程在两个页面会有不同已付金额。
     */
    private List<BigDecimal> storedShares(List<EnrollmentOrderItemEntity> items,
                                          Function<EnrollmentOrderItemEntity, BigDecimal> valueOf,
                                          BigDecimal expectedTotal,
                                          List<BigDecimal> fallbackWeights,
                                          boolean preferStoredCourseAmounts,
                                          boolean wholeDollars) {
        if (!preferStoredCourseAmounts || items.stream().anyMatch(item -> valueOf.apply(item) == null)) {
            return allocateByWeights(expectedTotal, fallbackWeights, wholeDollars);
        }
        return items.stream().map(item -> amount(valueOf.apply(item))).toList();
    }

    /** 按课程项应收与净已付金额计算课程状态；收据状态单独保存在 orderPaymentStatus。 */
    private String resolvePaymentStatus(BigDecimal amountDue, BigDecimal paidAmount) {
        if (amountDue.compareTo(ZERO_AMOUNT) <= 0 || paidAmount.compareTo(amountDue) >= 0) {
            return "PAID";
        }
        if (paidAmount.compareTo(ZERO_AMOUNT) <= 0) {
            return "UNPAID";
        }
        return "PARTIAL";
    }

    /** 多参与人订单：先按参与人把整单金额切成桶，参与人快照缺失时回退等分。 */
    private Map<Integer, AllocatedAmounts> participantBucketAmounts(EnrollmentOrderEntity order,
                                                                    List<EnrollmentOrderParticipantEntity> participants,
                                                                    Map<PaymentParticipantKey, BigDecimal> paidAmountByParticipant) {
        List<BigDecimal> dueWeights = participantDueWeights(participants);
        List<BigDecimal> paidWeights = participantPaidWeights(order, participants, paidAmountByParticipant, dueWeights);
        boolean wholeDollars = WholeDollarBilling.appliesTo(order);
        List<BigDecimal> pickupShares = allocateByWeights(amount(order.getPickupFeeAmount()), dueWeights, wholeDollars);
        List<BigDecimal> extraFeeShares = allocateByWeights(amount(order.getExtraFeeAmount()), dueWeights, wholeDollars);
        List<BigDecimal> discountShares = allocateByWeights(amount(order.getDiscountAmount()), dueWeights, wholeDollars);
        List<BigDecimal> amountDueShares = allocateByWeights(amount(order.getAmountDue()), dueWeights, wholeDollars);
        List<BigDecimal> paidShares = allocateByWeights(amount(order.getReceivedAmount()), paidWeights, wholeDollars);
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
        return allocateByWeights(total, weights, false);
    }

    /** 新订单只在整美元边界拆分，最后一行吸收美元余数；历史订单维持分位规则。 */
    private List<BigDecimal> allocateByWeights(BigDecimal total, List<BigDecimal> weights, boolean wholeDollars) {
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
                    ? normalizedTotal.divide(BigDecimal.valueOf(size), wholeDollars ? 0 : 2, RoundingMode.DOWN)
                    : normalizedTotal.multiply(weight).divide(weightSum, wholeDollars ? 0 : 2, RoundingMode.DOWN);
            shares.add(share.setScale(2));
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
                    sumOutstandingRows(rows)
            );
        }
    }
}
