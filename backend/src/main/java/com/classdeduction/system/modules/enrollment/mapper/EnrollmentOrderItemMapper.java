package com.classdeduction.system.modules.enrollment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EnrollmentOrderItemMapper extends BaseMapper<EnrollmentOrderItemEntity> {

    /**
     * Count order items referencing a price option whose parent order is still valid
     * (not cancelled and not soft-deleted). Cancelled/refunded registrations must not
     * make a price option appear as "Used".
     */
    @Select("SELECT COUNT(*) FROM enrollment_order_item oi "
            + "JOIN enrollment_order o ON o.id = oi.enrollment_order_id "
            + "WHERE oi.course_price_option_id = #{optionId} "
            + "AND oi.is_deleted = 0 "
            + "AND o.is_deleted = 0 "
            + "AND o.order_status != 'CANCELLED'")
    Long countActiveReferences(@Param("optionId") Long optionId);

    /** 接送字段允许主动清空；单靠 updateById 会因 null 字段策略而保留旧快照。 */
    @Update("""
            UPDATE enrollment_order_item
            SET pickup_required_snapshot = #{pickupRequired},
                pickup_address_snapshot = #{pickupAddress},
                dropoff_required_snapshot = #{dropoffRequired},
                dropoff_address_snapshot = #{dropoffAddress}
            WHERE id = #{itemId}
              AND is_deleted = 0
            """)
    int updateTransportSnapshot(@Param("itemId") Long itemId,
                                @Param("pickupRequired") boolean pickupRequired,
                                @Param("pickupAddress") String pickupAddress,
                                @Param("dropoffRequired") boolean dropoffRequired,
                                @Param("dropoffAddress") String dropoffAddress);
}
