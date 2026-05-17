package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.enrollment.dto.ClassEnrollmentCreateRequest;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentCreateRequest;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderPageQuery;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderUpdateRequest;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentCreateResultVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderDetailVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderListItemVO;

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
     * 创建班课报名订单。
     */
    void createClassEnrollmentOrder(ClassEnrollmentCreateRequest request);

    /**
     * 删除报名订单（软删除）。
     */
    void deleteOrder(Long id);
}
