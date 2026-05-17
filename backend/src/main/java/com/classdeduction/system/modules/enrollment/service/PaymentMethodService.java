package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.modules.enrollment.dto.PaymentMethodSaveRequest;
import com.classdeduction.system.modules.enrollment.vo.PaymentMethodVO;

import java.util.List;

/**
 * 支付方式服务接口。
 */
public interface PaymentMethodService {

    /**
     * 获取所有激活的支付方式列表。
     */
    List<PaymentMethodVO> listActiveMethods();

    /**
     * 获取所有支付方式列表（含未激活）。
     */
    List<PaymentMethodVO> listAllMethods();

    /**
     * 获取支付方式详情。
     */
    PaymentMethodVO getMethod(Long id);

    /**
     * 创建支付方式。
     */
    Long createMethod(PaymentMethodSaveRequest request);

    /**
     * 更新支付方式。
     */
    void updateMethod(Long id, PaymentMethodSaveRequest request);

    /**
     * 删除支付方式（软删除）。
     */
    void deleteMethod(Long id);
}
