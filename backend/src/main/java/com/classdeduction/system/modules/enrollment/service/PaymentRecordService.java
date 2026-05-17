package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.modules.enrollment.dto.PaymentRecordSaveRequest;
import com.classdeduction.system.modules.enrollment.vo.PaymentRecordVO;

import java.util.List;

/**
 * 支付记录服务接口。
 */
public interface PaymentRecordService {

    /**
     * 根据报名订单ID查询支付记录列表。
     */
    List<PaymentRecordVO> listByEnrollmentOrderId(Long enrollmentOrderId);

    /**
     * 获取支付记录详情。
     */
    PaymentRecordVO getPaymentRecord(Long id);

    /**
     * 创建支付记录（支持组合支付）并更新报名订单的已收金额和支付状态。
     */
    void createPaymentRecord(PaymentRecordSaveRequest request);

    /**
     * 获取课时包余额。
     */
    Integer getPackageBalance(Long packageId);
}
