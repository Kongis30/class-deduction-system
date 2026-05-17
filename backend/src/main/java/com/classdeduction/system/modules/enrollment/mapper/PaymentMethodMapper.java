package com.classdeduction.system.modules.enrollment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.enrollment.entity.PaymentMethodEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付方式 Mapper。
 */
@Mapper
public interface PaymentMethodMapper extends BaseMapper<PaymentMethodEntity> {
}
