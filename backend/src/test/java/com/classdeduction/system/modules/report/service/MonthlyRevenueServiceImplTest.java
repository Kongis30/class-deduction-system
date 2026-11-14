package com.classdeduction.system.modules.report.service;

import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.entity.CampusEntity;
import com.classdeduction.system.modules.base.mapper.CampusMapper;
import com.classdeduction.system.modules.report.dto.MonthlyRevenueQuery;
import com.classdeduction.system.modules.report.mapper.OrderItemWeightRow;
import com.classdeduction.system.modules.report.mapper.ProductLabelRow;
import com.classdeduction.system.modules.report.mapper.RevenueAggregateRow;
import com.classdeduction.system.modules.report.mapper.RevenueReportMapper;
import com.classdeduction.system.modules.report.mapper.UnallocatedPaymentRow;
import com.classdeduction.system.modules.report.service.impl.MonthlyRevenueServiceImpl;
import com.classdeduction.system.modules.report.vo.MonthlyRevenueVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyRevenueServiceImplTest {

    @Mock
    private RevenueReportMapper revenueReportMapper;

    @Mock
    private CampusMapper campusMapper;

    @InjectMocks
    private MonthlyRevenueServiceImpl service;

    @Test
    void shouldGroupRevenueByCampusAndProductKeepingRefundMonth() {
        when(revenueReportMapper.aggregateByMonthCampusProduct(any(), any())).thenReturn(List.of(
                aggregate("2026-07", 11L, 5L, "2600.00"),
                // 退款按发生月份冲减，不回冲原支付月份。
                aggregate("2026-08", 11L, 5L, "-400.00"),
                aggregate("2026-07", 11L, 9L, "800.00"),
                aggregate("2026-08", 12L, 7L, "300.00")));
        when(revenueReportMapper.listUnallocatedPayments(any(), any())).thenReturn(List.of());
        when(revenueReportMapper.selectProductLabels(anyCollection())).thenReturn(List.of(
                product(5L, "After School", "GROUP", 10),
                product(7L, "1-on-1", "ONE_ON_ONE", 20),
                product(9L, "Townsend Harris", "GROUP", 30)));
        when(campusMapper.selectList(any())).thenReturn(List.of(campus(11L, "South", 1), campus(12L, "North", 2)));

        MonthlyRevenueVO result = service.getMonthlyRevenue(query("2026-07", "2026-08"));

        assertThat(result.months()).extracting(MonthlyRevenueVO.MonthColumnVO::label)
                .containsExactly("26-Jul", "26-Aug");
        assertThat(result.unassignedPaymentCount()).isZero();
        // North 只有 1 对 1 收入，因此不产生校区块，只并入末尾的 1on1 区块。
        assertThat(result.sections()).extracting(MonthlyRevenueVO.SectionVO::title)
                .containsExactly("South", "1on1");

        MonthlyRevenueVO.SectionVO south = result.sections().get(0);
        assertThat(south.rows()).extracting(MonthlyRevenueVO.RowVO::productName)
                .containsExactly("After School", "Townsend Harris");
        assertThat(south.rows().get(0).amounts())
                .containsExactly(new BigDecimal("2600.00"), new BigDecimal("-400.00"));
        assertThat(south.rows().get(1).amounts().get(1)).isEqualByComparingTo("0.00");

        MonthlyRevenueVO.SectionVO oneOnOne = result.sections().get(1);
        assertThat(oneOnOne.oneOnOne()).isTrue();
        assertThat(oneOnOne.rows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.productName()).isEqualTo("1-on-1");
                    assertThat(row.amounts().get(1)).isEqualByComparingTo("300.00");
                });
    }

    @Test
    void shouldSplitLegacyPaymentsWithoutAllocationByCoursePriceWeight() {
        when(revenueReportMapper.aggregateByMonthCampusProduct(any(), any())).thenReturn(List.of());
        UnallocatedPaymentRow payment = new UnallocatedPaymentRow();
        payment.setPaymentId(1L);
        payment.setEnrollmentOrderId(100L);
        payment.setAmount(new BigDecimal("300.00"));
        payment.setMonthKey("2026-07");
        payment.setCampusId(11L);
        when(revenueReportMapper.listUnallocatedPayments(any(), any())).thenReturn(List.of(payment));
        when(revenueReportMapper.selectOrderItemWeights(anyCollection())).thenReturn(List.of(
                weight(100L, 1L, 5L, "100.00"),
                weight(100L, 2L, 9L, "200.00")));
        when(revenueReportMapper.selectProductLabels(anyCollection())).thenReturn(List.of(
                product(5L, "After School", "GROUP", 10),
                product(9L, "Townsend Harris", "GROUP", 20)));
        when(campusMapper.selectList(any())).thenReturn(List.of(campus(11L, "South", 1)));

        MonthlyRevenueVO result = service.getMonthlyRevenue(query("2026-07", "2026-07"));

        assertThat(result.unassignedPaymentCount()).isZero();
        assertThat(result.sections()).singleElement()
                .satisfies(section -> {
                    assertThat(section.rows()).extracting(MonthlyRevenueVO.RowVO::productName)
                            .containsExactly("After School", "Townsend Harris");
                    assertThat(section.rows().get(0).amounts().get(0)).isEqualByComparingTo("100.00");
                    assertThat(section.rows().get(1).amounts().get(0)).isEqualByComparingTo("200.00");
                });
    }

    /** 课程项已被删除的历史流水不能静默丢弃，必须落到 Unassigned product 行。 */
    @Test
    void shouldKeepLegacyPaymentAsUnassignedWhenOrderHasNoCourseItems() {
        when(revenueReportMapper.aggregateByMonthCampusProduct(any(), any())).thenReturn(List.of());
        UnallocatedPaymentRow payment = new UnallocatedPaymentRow();
        payment.setPaymentId(1L);
        payment.setEnrollmentOrderId(100L);
        payment.setAmount(new BigDecimal("50.00"));
        payment.setMonthKey("2026-07");
        payment.setCampusId(11L);
        when(revenueReportMapper.listUnallocatedPayments(any(), any())).thenReturn(List.of(payment));
        when(revenueReportMapper.selectOrderItemWeights(anyCollection())).thenReturn(List.of());

        MonthlyRevenueVO result = service.getMonthlyRevenue(query("2026-07", "2026-07"));

        assertThat(result.unassignedPaymentCount()).isEqualTo(1);
        assertThat(result.sections()).singleElement().satisfies(section -> {
            assertThat(section.rows()).singleElement().satisfies(row -> {
                assertThat(row.productName()).isEqualTo("Unassigned product");
                assertThat(row.amounts().get(0)).isEqualByComparingTo("50.00");
            });
        });
    }

    @Test
    void shouldRejectReversedOrOversizedMonthRange() {
        assertThatThrownBy(() -> service.getMonthlyRevenue(query("2026-08", "2026-07")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must not be after");

        assertThatThrownBy(() -> service.getMonthlyRevenue(query("2023-01", "2026-12")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot exceed");
    }

    private MonthlyRevenueQuery query(String fromMonth, String toMonth) {
        MonthlyRevenueQuery query = new MonthlyRevenueQuery();
        query.setFromMonth(fromMonth);
        query.setToMonth(toMonth);
        return query;
    }

    private RevenueAggregateRow aggregate(String monthKey, Long campusId, Long productId, String amount) {
        RevenueAggregateRow row = new RevenueAggregateRow();
        row.setMonthKey(monthKey);
        row.setCampusId(campusId);
        row.setCourseProductId(productId);
        row.setAmount(new BigDecimal(amount));
        return row;
    }

    private ProductLabelRow product(Long id, String name, String teachingMode, int sortOrder) {
        ProductLabelRow row = new ProductLabelRow();
        row.setProductId(id);
        row.setProductName(name);
        row.setTeachingMode(teachingMode);
        row.setSortOrder(sortOrder);
        return row;
    }

    private OrderItemWeightRow weight(Long orderId, Long itemId, Long productId, String amount) {
        OrderItemWeightRow row = new OrderItemWeightRow();
        row.setEnrollmentOrderId(orderId);
        row.setEnrollmentOrderItemId(itemId);
        row.setCourseProductId(productId);
        row.setWeightAmount(new BigDecimal(amount));
        return row;
    }

    private CampusEntity campus(Long id, String name, int displayOrder) {
        CampusEntity campus = new CampusEntity();
        campus.setId(id);
        campus.setCampusName(name);
        campus.setDisplayOrder(displayOrder);
        return campus;
    }
}
