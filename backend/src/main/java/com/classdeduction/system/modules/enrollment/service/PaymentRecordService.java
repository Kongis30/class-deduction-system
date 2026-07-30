package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.modules.enrollment.dto.PaymentRecordSaveRequest;
import com.classdeduction.system.modules.enrollment.vo.PaymentRecordVO;
import com.classdeduction.system.modules.enrollment.vo.ReceiptRecordVO;

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
     * 创建支付记录（支持组合支付）、更新报名订单并生成独立付款收据。
     */
    ReceiptRecordVO createPaymentRecord(PaymentRecordSaveRequest request);

    /**
     * 获取课时包余额。
     */
    Integer getPackageBalance(Long packageId);
}
