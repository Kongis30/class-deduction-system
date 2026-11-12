package com.classdeduction.system.modules.enrollment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.enrollment.entity.PaymentAllocationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PaymentAllocationMapper extends BaseMapper<PaymentAllocationEntity> {

    /** 含软删行的分摊查询：收据快照需要保留付款的历史行级归属。 */
    @Select("SELECT * FROM payment_allocation WHERE enrollment_order_id = #{orderId}")
    List<PaymentAllocationEntity> selectAllByOrderIdIncludingDeleted(Long orderId);
}
