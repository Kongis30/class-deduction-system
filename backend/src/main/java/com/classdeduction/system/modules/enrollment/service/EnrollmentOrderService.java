package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentCreateRequest;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderPageQuery;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderUpdateRequest;
import com.classdeduction.system.modules.enrollment.dto.RefundRequest;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentCreateResultVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderDetailVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderListItemVO;
import com.classdeduction.system.modules.enrollment.vo.ReceiptRecordVO;

public interface EnrollmentOrderService {

    /**
     * 分页查询报名订单列表。
     */
    PageResponse<EnrollmentOrderListItemVO> pageOrders(EnrollmentOrderPageQuery query);

    /**
     * 获取报名订单详情。
     */
    EnrollmentOrderDetailVO getOrder(Long id);

    /**
     * 创建报名订单并生成课时包。
     */
    EnrollmentCreateResultVO createOrder(EnrollmentCreateRequest request);

    /**
     * 更新报名订单。
     */
    void updateOrder(Long id, EnrollmentOrderUpdateRequest request);

    /**
     * 取消报名订单。
     */
    void cancelOrder(Long id);

    /**
     * 退款：记录退款流水并更新订单财务快照；全额退款会同时取消报名产物。
     */
    ReceiptRecordVO refundOrder(Long id, RefundRequest request);

    /**
     * 删除误创建且未付款、未上课的报名订单（软删除）。
     */
    void deleteOrder(Long id);

    /**
     * 滚动续期：为所有 ACTIVE 班级按产品完整周期补足共享课次，幂等去重。返回新增课次数。
     */
    int renewActiveClassOccurrences();
}
