package com.classdeduction.system.modules.enrollment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.enrollment.entity.PaymentMethodEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 支付方式 Mapper。
 */
@Mapper
public interface PaymentMethodMapper extends BaseMapper<PaymentMethodEntity> {

    /**
     * Query by unique method code while bypassing @TableLogic, so startup
     * bootstrap can recover rows that were soft deleted in production.
     */
    @Select("SELECT * FROM payment_method WHERE method_code = #{methodCode} LIMIT 1")
    PaymentMethodEntity selectByMethodCodeIgnoreLogicDelete(@Param("methodCode") String methodCode);

    @Update("UPDATE payment_method "
            + "SET method_name = #{methodName}, sort_order = #{sortOrder}, status = #{status}, "
            + "is_deleted = 0, deleted_at = NULL, deleted_by = NULL, updated_at = NOW() "
            + "WHERE id = #{id}")
    int restoreDefaultMethodById(@Param("id") Long id,
                                 @Param("methodName") String methodName,
                                 @Param("sortOrder") Integer sortOrder,
                                 @Param("status") String status);
}
