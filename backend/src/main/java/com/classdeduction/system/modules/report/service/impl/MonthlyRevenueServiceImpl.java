package com.classdeduction.system.modules.report.service.impl;

import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.base.entity.CampusEntity;
import com.classdeduction.system.modules.base.mapper.CampusMapper;
import com.classdeduction.system.modules.report.dto.MonthlyRevenueQuery;
import com.classdeduction.system.modules.report.mapper.OrderItemWeightRow;
import com.classdeduction.system.modules.report.mapper.ProductLabelRow;
import com.classdeduction.system.modules.report.mapper.RevenueAggregateRow;
import com.classdeduction.system.modules.report.mapper.RevenueReportMapper;
import com.classdeduction.system.modules.report.mapper.UnallocatedPaymentRow;
import com.classdeduction.system.modules.report.service.MonthlyRevenueService;
import com.classdeduction.system.modules.report.vo.MonthlyRevenueVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 月度收入报表。
 *
 * <p>口径：payment_record 实际流水（退款金额为负数，按其发生月份冲减），
 * 归属月份取 paid_at（缺失时取 created_at）在 America/New_York 的自然月，
 * 归属校区取收据（enrollment_order）的校区，
 * 归属项目取 payment_allocation 指向的课程产品（产品级粒度）。</p>
 */
@Service
@RequiredArgsConstructor
public class MonthlyRevenueServiceImpl implements MonthlyRevenueService {

    private static final int DEFAULT_MONTH_SPAN = 6;
    private static final int MAX_MONTH_SPAN = 36;
    private static final DateTimeFormatter MONTH_KEY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter LABEL_FORMAT = DateTimeFormatter.ofPattern("yy-MMM", Locale.ENGLISH);
    /** 无校区（历史收据未记录校区）统一归入该虚拟校区，避免金额丢失。 */
    private static final long NO_CAMPUS_ID = 0L;
    private static final String ONE_ON_ONE_MODE = "ONE_ON_ONE";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final RevenueReportMapper revenueReportMapper;
    private final CampusMapper campusMapper;

    @Override
    public MonthlyRevenueVO getMonthlyRevenue(MonthlyRevenueQuery query) {
        YearMonth toMonth = parseMonth(query == null ? null : query.getToMonth(),
                YearMonth.now(AppTime.APP_ZONE_ID));
        YearMonth fromMonth = parseMonth(query == null ? null : query.getFromMonth(),
                toMonth.minusMonths(DEFAULT_MONTH_SPAN - 1L));
        validateRange(fromMonth, toMonth);

        List<YearMonth> months = new ArrayList<>();
        for (YearMonth cursor = fromMonth; !cursor.isAfter(toMonth); cursor = cursor.plusMonths(1)) {
            months.add(cursor);
        }
        Map<String, Integer> monthIndex = new LinkedHashMap<>();
        for (int index = 0; index < months.size(); index++) {
            monthIndex.put(months.get(index).format(MONTH_KEY_FORMAT), index);
        }

        Bucket bucket = new Bucket(months.size());
        for (RevenueAggregateRow row : revenueReportMapper.aggregateByMonthCampusProduct(
                startOfMonth(fromMonth), startOfNextMonth(toMonth))) {
            Integer index = monthIndex.get(row.getMonthKey());
            if (index != null) {
                bucket.add(row.getCampusId(), row.getCourseProductId(), index, row.getAmount());
            }
        }
        int unassignedPaymentCount = applyUnallocatedFallback(fromMonth, toMonth, monthIndex, bucket);

        Map<Long, ProductLabelRow> products = loadProductLabels(bucket.productIds());
        Map<Long, CampusEntity> campuses = loadCampuses(bucket.campusIds());

        List<MonthlyRevenueVO.MonthColumnVO> monthColumns = months.stream()
                .map(month -> new MonthlyRevenueVO.MonthColumnVO(month.format(MONTH_KEY_FORMAT),
                        month.format(LABEL_FORMAT)))
                .toList();
        List<MonthlyRevenueVO.SectionVO> sections = buildSections(bucket, products, campuses, months.size());
        return new MonthlyRevenueVO(monthColumns, sections, unassignedPaymentCount);
    }

    private void validateRange(YearMonth fromMonth, YearMonth toMonth) {
        if (fromMonth.isAfter(toMonth)) {
            throw new BusinessException("REVENUE_MONTH_RANGE_INVALID",
                    "The start month must not be after the end month.");
        }
        long span = ChronoUnit.MONTHS.between(fromMonth, toMonth) + 1;
        if (span > MAX_MONTH_SPAN) {
            throw new BusinessException("REVENUE_MONTH_RANGE_TOO_WIDE",
                    "The report range cannot exceed " + MAX_MONTH_SPAN + " months.");
        }
    }

    /**
     * 历史流水缺少课程分摊时，按订单课程价格权重回退分摊；
     * 连课程项都缺失的流水归入 Unassigned product，保证报表合计等于流水净额。
     */
    private int applyUnallocatedFallback(YearMonth fromMonth, YearMonth toMonth, Map<String, Integer> monthIndex,
                                         Bucket bucket) {
        List<UnallocatedPaymentRow> unallocated = revenueReportMapper.listUnallocatedPayments(
                startOfMonth(fromMonth), startOfNextMonth(toMonth));
        if (unallocated.isEmpty()) {
            return 0;
        }
        Set<Long> orderIds = unallocated.stream().map(UnallocatedPaymentRow::getEnrollmentOrderId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (orderIds.isEmpty()) {
            return 0;
        }
        Map<Long, List<OrderItemWeightRow>> weightsByOrder = revenueReportMapper.selectOrderItemWeights(orderIds)
                .stream()
                .collect(Collectors.groupingBy(OrderItemWeightRow::getEnrollmentOrderId,
                        LinkedHashMap::new, Collectors.toList()));

        int unassigned = 0;
        for (UnallocatedPaymentRow payment : unallocated) {
            Integer index = monthIndex.get(payment.getMonthKey());
            if (index == null) {
                continue;
            }
            List<OrderItemWeightRow> weights = weightsByOrder.get(payment.getEnrollmentOrderId());
            if (weights == null || weights.isEmpty()) {
                // 课程项已被删除：金额仍属于实收，归入未分配产品行，不静默丢弃。
                bucket.add(payment.getCampusId(), null, index, payment.getAmount());
                unassigned++;
                continue;
            }
            allocateByWeight(payment.getAmount(), weights)
                    .forEach((productId, amount) -> bucket.add(payment.getCampusId(), productId, index, amount));
        }
        return unassigned;
    }

    /** 按课程价格权重拆分金额，余差归最后一项，保证合计等于原金额。 */
    private Map<Long, BigDecimal> allocateByWeight(BigDecimal total, List<OrderItemWeightRow> weights) {
        BigDecimal normalized = total == null ? ZERO : total.setScale(2, RoundingMode.HALF_UP);
        BigDecimal weightSum = weights.stream()
                .map(row -> row.getWeightAmount() == null ? ZERO : row.getWeightAmount())
                .reduce(ZERO, BigDecimal::add);
        Map<Long, BigDecimal> shares = new LinkedHashMap<>();
        BigDecimal allocated = ZERO;
        for (int index = 0; index < weights.size(); index++) {
            BigDecimal share;
            if (index == weights.size() - 1) {
                share = normalized.subtract(allocated).setScale(2, RoundingMode.HALF_UP);
            } else if (weightSum.signum() == 0) {
                share = normalized.divide(BigDecimal.valueOf(weights.size()), 2, RoundingMode.DOWN);
            } else {
                BigDecimal weight = weights.get(index).getWeightAmount();
                share = normalized.multiply(weight == null ? ZERO : weight)
                        .divide(weightSum, 2, RoundingMode.DOWN);
            }
            allocated = allocated.add(share);
            shares.merge(weights.get(index).getCourseProductId(), share, BigDecimal::add);
        }
        return shares;
    }

    private List<MonthlyRevenueVO.SectionVO> buildSections(Bucket bucket, Map<Long, ProductLabelRow> products,
                                                           Map<Long, CampusEntity> campuses, int monthCount) {
        List<Long> campusOrder = new ArrayList<>(bucket.campusIds());
        campusOrder.sort(Comparator
                .comparingInt((Long campusId) -> campusOrderRank(campusId, campuses))
                .thenComparing(campusId -> campusLabel(campusId, campuses)));

        List<MonthlyRevenueVO.SectionVO> sections = new ArrayList<>();
        for (Long campusId : campusOrder) {
            List<MonthlyRevenueVO.RowVO> rows = buildRows(bucket, campusId, products, monthCount, false);
            if (!rows.isEmpty()) {
                sections.add(new MonthlyRevenueVO.SectionVO(String.valueOf(campusId),
                        campusLabel(campusId, campuses), false, rows));
            }
        }
        // 1 对 1 独立成块排在最后，按产品行汇总（与其他区块保持同一产品级口径）。
        Map<Long, BigDecimal[]> oneOnOne = new LinkedHashMap<>();
        bucket.amounts().entrySet().stream()
                .filter(entry -> isOneOnOne(entry.getKey().productId(), products))
                .sorted(Comparator.comparing(entry -> sortOrderOf(entry.getKey().productId(), products)))
                .forEach(entry -> {
                    BigDecimal[] target = oneOnOne.computeIfAbsent(entry.getKey().productId(),
                            ignored -> zeroRow(monthCount));
                    BigDecimal[] source = entry.getValue();
                    for (int index = 0; index < monthCount; index++) {
                        target[index] = target[index].add(source[index]);
                    }
                });
        if (!oneOnOne.isEmpty()) {
            List<MonthlyRevenueVO.RowVO> rows = oneOnOne.entrySet().stream()
                    .map(entry -> new MonthlyRevenueVO.RowVO(entry.getKey(),
                            productNameOf(entry.getKey(), products), List.copyOf(Arrays.asList(entry.getValue()))))
                    .toList();
            sections.add(new MonthlyRevenueVO.SectionVO("ONE_ON_ONE", "1on1", true, rows));
        }
        return sections;
    }

    private List<MonthlyRevenueVO.RowVO> buildRows(Bucket bucket, Long campusId, Map<Long, ProductLabelRow> products,
                                                   int monthCount, boolean oneOnOne) {
        Map<Long, BigDecimal[]> rows = new LinkedHashMap<>();
        bucket.amounts().entrySet().stream()
                .filter(entry -> Objects.equals(entry.getKey().campusId(), campusId))
                .filter(entry -> isOneOnOne(entry.getKey().productId(), products) == oneOnOne)
                .sorted(Comparator.comparing(entry -> sortOrderOf(entry.getKey().productId(), products)))
                .forEach(entry -> {
                    BigDecimal[] target = rows.computeIfAbsent(entry.getKey().productId(),
                            ignored -> zeroRow(monthCount));
                    BigDecimal[] source = entry.getValue();
                    for (int index = 0; index < monthCount; index++) {
                        target[index] = target[index].add(source[index]);
                    }
                });
        return rows.entrySet().stream()
                .map(entry -> new MonthlyRevenueVO.RowVO(entry.getKey(),
                        productNameOf(entry.getKey(), products),
                        List.copyOf(Arrays.asList(entry.getValue()))))
                .toList();
    }

    private static BigDecimal[] zeroRow(int monthCount) {
        BigDecimal[] row = new BigDecimal[monthCount];
        Arrays.fill(row, ZERO);
        return row;
    }

    private boolean isOneOnOne(Long productId, Map<Long, ProductLabelRow> products) {
        ProductLabelRow product = productId == null ? null : products.get(productId);
        return product != null && ONE_ON_ONE_MODE.equalsIgnoreCase(product.getTeachingMode());
    }

    private int sortOrderOf(Long productId, Map<Long, ProductLabelRow> products) {
        ProductLabelRow product = productId == null ? null : products.get(productId);
        return product == null || product.getSortOrder() == null ? Integer.MAX_VALUE : product.getSortOrder();
    }

    private String productNameOf(Long productId, Map<Long, ProductLabelRow> products) {
        ProductLabelRow product = productId == null ? null : products.get(productId);
        return product == null || !StringUtils.hasText(product.getProductName())
                ? "Unassigned product" : product.getProductName();
    }

    private String campusLabel(Long campusId, Map<Long, CampusEntity> campuses) {
        CampusEntity campus = campusId == null || campusId == NO_CAMPUS_ID ? null : campuses.get(campusId);
        return campus == null || !StringUtils.hasText(campus.getCampusName()) ? "No campus" : campus.getCampusName();
    }

    private int campusOrderRank(Long campusId, Map<Long, CampusEntity> campuses) {
        CampusEntity campus = campusId == null || campusId == NO_CAMPUS_ID ? null : campuses.get(campusId);
        if (campus == null || campus.getDisplayOrder() == null) {
            return Integer.MAX_VALUE;
        }
        return campus.getDisplayOrder();
    }

    private Map<Long, ProductLabelRow> loadProductLabels(Set<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return revenueReportMapper.selectProductLabels(productIds).stream()
                .collect(Collectors.toMap(ProductLabelRow::getProductId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, CampusEntity> loadCampuses(Set<Long> campusIds) {
        if (campusIds.isEmpty()) {
            return Map.of();
        }
        return campusMapper.selectList(null).stream()
                .filter(campus -> campusIds.contains(campus.getId()))
                .collect(Collectors.toMap(CampusEntity::getId, Function.identity(), (left, right) -> left));
    }

    private YearMonth parseMonth(String value, YearMonth fallback) {
        return StringUtils.hasText(value) ? YearMonth.parse(value.trim(), MONTH_KEY_FORMAT) : fallback;
    }

    private LocalDateTime startOfMonth(YearMonth month) {
        return month.atDay(1).atStartOfDay(AppTime.APP_ZONE_ID).toLocalDateTime();
    }

    private LocalDateTime startOfNextMonth(YearMonth month) {
        return month.plusMonths(1).atDay(1).atStartOfDay(AppTime.APP_ZONE_ID).toLocalDateTime();
    }

    /** 报表累加桶：校区 + 产品 → 各月金额。 */
    private static final class Bucket {

        private final int monthCount;
        private final Map<Key, BigDecimal[]> amounts = new LinkedHashMap<>();
        private final Set<Long> campusIds = new LinkedHashSet<>();
        private final Set<Long> productIds = new LinkedHashSet<>();

        private Bucket(int monthCount) {
            this.monthCount = monthCount;
        }

        private void add(Long campusId, Long productId, int monthIndex, BigDecimal amount) {
            if (monthIndex < 0 || monthIndex >= monthCount) {
                return;
            }
            Long normalizedCampusId = campusId == null ? NO_CAMPUS_ID : campusId;
            campusIds.add(normalizedCampusId);
            if (productId != null) {
                productIds.add(productId);
            }
            BigDecimal[] row = amounts.computeIfAbsent(new Key(normalizedCampusId, productId),
                    ignored -> zeroRow(monthCount));
            row[monthIndex] = row[monthIndex]
                    .add(amount == null ? ZERO : amount.setScale(2, RoundingMode.HALF_UP));
        }

        private Map<Key, BigDecimal[]> amounts() {
            return amounts;
        }

        private Set<Long> campusIds() {
            return campusIds;
        }

        private Set<Long> productIds() {
            return productIds;
        }

        private record Key(Long campusId, Long productId) {
        }
    }
}
