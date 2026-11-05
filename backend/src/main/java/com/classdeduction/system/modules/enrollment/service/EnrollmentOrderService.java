package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentCreateRequest;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderPageQuery;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderCorrectionRequest;
import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderUpdateRequest;
import com.classdeduction.system.modules.enrollment.dto.RefundRequest;
import com.classdeduction.system.modules.enrollment.dto.EndCourseRequest;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentCreateResultVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderDetailVO;
import com.classdeduction.system.modules.enrollment.vo.EnrollmentOrderListItemVO;
import com.classdeduction.system.modules.enrollment.vo.ReceiptRecordVO;
import com.classdeduction.system.modules.enrollment.vo.RefundCoursePreviewVO;
import com.classdeduction.system.common.dto.CurrentUserResponse;

import java.math.BigDecimal;

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

    EnrollmentCreateResultVO createOrder(EnrollmentCreateRequest request, CurrentUserResponse operator);

    /**
     * 更新报名订单。
     */
    void updateOrder(Long id, EnrollmentOrderUpdateRequest request);

    /** 更正已保存收据，并同步订单、课包、班级报名和收据快照。 */
    void correctOrder(Long id, EnrollmentOrderCorrectionRequest request);

    /** 由控制器传入可信的当前用户，写入更正审计。 */
    void correctOrder(Long id, EnrollmentOrderCorrectionRequest request, CurrentUserResponse operator);

    /**
     * 取消报名订单。
     */
    void cancelOrder(Long id);

    /**
     * 退款：记录负数退款流水并回写原报名收据；全额退款会同时取消报名产物。
     */
    ReceiptRecordVO refundOrder(Long id, RefundRequest request);

    /** 结束指定学生的课程，保留财务和历史记录。 */
    void endCourse(Long orderId, EndCourseRequest request);

    /** 查询退款窗口中每个课程项的已消耗课时与金额。 */
    RefundCoursePreviewVO previewRefund(Long id, Integer participantIndex);

    /** 整单删除：须先退款至净收款为零，并级联清理所有课程产物。 */
    void deleteOrder(Long id);

    /** 删除报名单中的一门课程及其个人排课，保留同单其他课程与收据。 */
    void deleteOrderItem(Long orderId, Long itemId);

    /** 删除独立充值收据；返回是否同时撤销了整笔充值交易。 */
    boolean deleteCreditTopUpReceipt(Long orderId, Long receiptId, BigDecimal expectedCreditBalance,
                                     CurrentUserResponse operator);

    /** 撤销退款收据对应的测试退款，并恢复退款前的财务、课程和排课状态。 */
    void deleteRefundReceipt(Long receiptId, CurrentUserResponse operator);

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
