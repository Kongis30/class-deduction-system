package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentCreateRequest;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderPageQuery;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderCorrectionRequest;
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
     * 更正报名单联系信息（姓名、电话、接送、报名日期、经手人、备注），不改动金额与课程。
     * 同步更新订单快照与已生成的收据快照。
     */
    void correctOrder(Long id, EnrollmentOrderCorrectionRequest request);

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
     * 管理员强制作废报名订单（软删除）：用于清理系统改造前遗留、已付款或已消课而无法走常规删除的历史脏数据。
     * 会关闭关联课包、取消未来课次、停用排课，但保留历史流水与消课记录以供审计。
     */
    void forceVoidOrder(Long id, String reason);

    /**
     * 滚动续期：为所有 ACTIVE 班级按产品完整周期补足共享课次，幂等去重。返回新增课次数。
     */
    int renewActiveClassOccurrences();
}
