package com.classdeduction.system.modules.enrollment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
