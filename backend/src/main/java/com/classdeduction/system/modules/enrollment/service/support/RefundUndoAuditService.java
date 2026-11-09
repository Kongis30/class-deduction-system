package com.classdeduction.system.modules.enrollment.service.support;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentCorrectionLogEntity;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentCorrectionLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 退款撤销的失败审计。
 *
 * <p>撤销在业务事务中失败时，整个退款/课程/排课恢复都会回滚；审计必须独立持久化，
 * 因此这里使用 {@code REQUIRES_NEW}，在业务事务回滚前单独提交一条失败记录。
 * 审计本身失败不得吞掉原始业务异常，也不得伪装成功。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundUndoAuditService {

    static final String BIZ_TYPE = "REFUND_RECEIPT";
    static final String FAILURE_CODE = "UNDO_REFUND_FAILED";

    private final EnrollmentCorrectionLogMapper operationLogMapper;
    private final JsonHelper jsonHelper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordFailure(Long receiptId, CurrentUserResponse operator, RuntimeException failure) {
        try {
            EnrollmentCorrectionLogEntity entry = new EnrollmentCorrectionLogEntity();
            entry.setOperatorUserId(operator == null ? null : operator.userId());
            entry.setOperatorNameSnapshot(operator == null ? "System" : operator.displayName());
            entry.setBizType(BIZ_TYPE);
            entry.setBizId(receiptId);
            entry.setOperationCode(FAILURE_CODE);
            String code = failure instanceof BusinessException businessException
                    ? businessException.getCode() : failure.getClass().getSimpleName();
            entry.setOperationDesc(truncate("Refund undo failed: " + code));
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("receiptId", receiptId);
            snapshot.put("errorCode", code);
            snapshot.put("errorMessage", failure.getMessage());
            entry.setRequestSnapshot(jsonHelper.toJson(snapshot));
            entry.setOperateAt(AppTime.nowLocalDateTime());
            operationLogMapper.insert(entry);
        } catch (RuntimeException auditFailure) {
            // 审计写入失败只记录日志，原业务异常继续向上抛出，避免掩盖真实的撤销失败原因。
            log.error("Failed to persist refund undo failure audit for receipt {}: {}",
                    receiptId, auditFailure.getMessage(), auditFailure);
        }
    }

    private String truncate(String value) {
        return value == null || value.length() <= 200 ? value : value.substring(0, 200);
    }
}
