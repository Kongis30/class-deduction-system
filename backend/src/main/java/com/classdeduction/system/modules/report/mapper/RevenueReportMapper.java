package com.classdeduction.system.modules.report.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 收入报表聚合查询。
 *
 * <p>口径为 payment_record 的实际流水：退款流水金额本身为负数，直接累加即为净收入；
 * VOIDED（已撤销）与软删除流水不计入。已软删除的报名订单整单排除，
 * 与 Data Summary 的收入口径保持一致（订单删除后不再计为收入）。</p>
 */
@Mapper
public interface RevenueReportMapper {

    /** 按“月份 + 收据校区 + 课程产品”汇总已分摊的收款与退款。 */
    @Select("""
            SELECT DATE_FORMAT(COALESCE(p.paid_at, p.created_at), '%Y-%m') AS month_key,
                   o.campus_id AS campus_id,
                   o.campus_name_snapshot AS campus_name_snapshot,
                   i.course_product_id AS course_product_id,
                   SUM(a.amount) AS amount
            FROM payment_allocation a
            JOIN payment_record p
              ON p.id = a.payment_record_id AND p.is_deleted = 0 AND p.payment_status <> 'VOIDED'
            JOIN enrollment_order o ON o.id = a.enrollment_order_id AND o.is_deleted = 0
            LEFT JOIN enrollment_order_item i ON i.id = a.enrollment_order_item_id
            WHERE a.is_deleted = 0
              AND COALESCE(p.paid_at, p.created_at) >= #{fromAt}
              AND COALESCE(p.paid_at, p.created_at) < #{toAt}
            GROUP BY month_key, o.campus_id, o.campus_name_snapshot, i.course_product_id
            """)
    List<RevenueAggregateRow> aggregateByMonthCampusProduct(@Param("fromAt") LocalDateTime fromAt,
                                                            @Param("toAt") LocalDateTime toAt);

    /**
     * 历史流水可能缺少课程级分摊。此类流水单独返回，由服务层按课程价格权重回退分摊，
     * 避免报表漏统计早期收入。
     */
    @Select("""
            SELECT p.id AS payment_id,
                   p.enrollment_order_id AS enrollment_order_id,
                   p.amount AS amount,
                   DATE_FORMAT(COALESCE(p.paid_at, p.created_at), '%Y-%m') AS month_key,
                   o.campus_id AS campus_id,
                   o.campus_name_snapshot AS campus_name_snapshot
            FROM payment_record p
            JOIN enrollment_order o ON o.id = p.enrollment_order_id AND o.is_deleted = 0
            WHERE p.is_deleted = 0
              AND p.payment_status <> 'VOIDED'
              AND COALESCE(p.paid_at, p.created_at) >= #{fromAt}
              AND COALESCE(p.paid_at, p.created_at) < #{toAt}
              AND NOT EXISTS (
                    SELECT 1 FROM payment_allocation a
                    WHERE a.payment_record_id = p.id AND a.is_deleted = 0
              )
            """)
    List<UnallocatedPaymentRow> listUnallocatedPayments(@Param("fromAt") LocalDateTime fromAt,
                                                        @Param("toAt") LocalDateTime toAt);

    /** 读取产品展示信息（含已归档/软删产品），历史收入仍需显示原产品名。 */
    @Select("""
            <script>
            SELECT id AS product_id,
                   product_name,
                   teaching_mode,
                   sort_order
            FROM course_product
            WHERE id IN
            <foreach collection="productIds" item="productId" open="(" separator="," close=")">
              #{productId}
            </foreach>
            </script>
            """)
    List<ProductLabelRow> selectProductLabels(@Param("productIds") Collection<Long> productIds);

    /** 读取订单内课程项的价格权重，供历史未分摊流水按权重回退分摊。 */
    @Select("""
            <script>
            SELECT oi.enrollment_order_id AS enrollment_order_id,
                   oi.id AS enrollment_order_item_id,
                   oi.course_product_id AS course_product_id,
                   GREATEST(COALESCE(oi.price_amount, 0), 0) AS weight_amount
            FROM enrollment_order_item oi
            WHERE oi.is_deleted = 0
              AND oi.enrollment_order_id IN
            <foreach collection="orderIds" item="orderId" open="(" separator="," close=")">
              #{orderId}
            </foreach>
            ORDER BY oi.enrollment_order_id, oi.line_no, oi.id
            </script>
            """)
    List<OrderItemWeightRow> selectOrderItemWeights(@Param("orderIds") Collection<Long> orderIds);
}
