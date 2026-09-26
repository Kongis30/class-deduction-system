package com.classdeduction.system.modules.enrollment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EnrollmentOrderMapper extends BaseMapper<EnrollmentOrderEntity> {

    /** 仅定位有接送需求的订单，供接送名单复用 Data Summary 行数据组装逻辑。 */
    @Select("""
            SELECT o.id AS order_id
            FROM enrollment_order o
            WHERE o.is_deleted = 0
              AND (o.pickup_required_snapshot = 1 OR o.dropoff_required_snapshot = 1)
            UNION
            SELECT oi.enrollment_order_id AS order_id
            FROM enrollment_order_item oi
            WHERE oi.is_deleted = 0
              AND (oi.pickup_required_snapshot = 1 OR oi.dropoff_required_snapshot = 1)
            UNION
            SELECT op.enrollment_order_id AS order_id
            FROM enrollment_order_participant op
            WHERE op.is_deleted = 0
              AND (op.pickup_required_snapshot = 1 OR op.dropoff_required_snapshot = 1)
            UNION
            SELECT o.id AS order_id
            FROM enrollment_order o
            JOIN student s ON s.id = o.student_id AND s.is_deleted = 0
            WHERE o.is_deleted = 0
              AND (s.pickup_required = 1 OR s.dropoff_required = 1)
            UNION
            SELECT op.enrollment_order_id AS order_id
            FROM enrollment_order_participant op
            JOIN student s ON s.id = op.student_id AND s.is_deleted = 0
            WHERE op.is_deleted = 0
              AND (s.pickup_required = 1 OR s.dropoff_required = 1)
            """)
    List<Long> selectTransportOrderIds();
}
