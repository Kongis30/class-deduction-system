package com.classdeduction.system.modules.enrollment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.enrollment.dto.ReceiptPageQuery;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.entity.ReceiptRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.ReceiptRecordMapper;
import com.classdeduction.system.modules.enrollment.service.ReceiptService;
import com.classdeduction.system.modules.enrollment.vo.ReceiptRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 收据服务：负责收据列表和详情查询。
 */
@Service
@RequiredArgsConstructor
public class ReceiptServiceImpl implements ReceiptService {

    private final ReceiptRecordMapper receiptRecordMapper;
    private final StudentMapper studentMapper;
    private final EnrollmentOrderMapper enrollmentOrderMapper;
    private final EnrollmentOrderItemMapper enrollmentOrderItemMapper;

    @Override
    public PageResponse<ReceiptRecordVO> pageReceipts(ReceiptPageQuery query) {
        Page<ReceiptRecordEntity> page = new Page<>(query.getPageNum(), query.getPageSize());
        List<Long> matchedStudentIds = findMatchedStudentIds(query.getKeyword());
        LambdaQueryWrapper<ReceiptRecordEntity> wrapper = new LambdaQueryWrapper<ReceiptRecordEntity>()
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(ReceiptRecordEntity::getReceiptNo, query.getKeyword())
                        .or(!matchedStudentIds.isEmpty(), nested -> nested.in(ReceiptRecordEntity::getStudentId, matchedStudentIds)))
                .eq(query.getStudentId() != null, ReceiptRecordEntity::getStudentId, query.getStudentId())
                .eq(query.getEnrollmentOrderId() != null,
                        ReceiptRecordEntity::getEnrollmentOrderId, query.getEnrollmentOrderId())
                .eq(StringUtils.hasText(query.getTemplateCode()), ReceiptRecordEntity::getTemplateCode, query.getTemplateCode())
                .orderByDesc(ReceiptRecordEntity::getIssuedAt);
        IPage<ReceiptRecordEntity> result = receiptRecordMapper.selectPage(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }

        Map<Long, StudentEntity> studentMap = studentMapper.selectBatchIds(
                        result.getRecords().stream().map(ReceiptRecordEntity::getStudentId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(StudentEntity::getId, item -> item));
        Set<Long> activeOrderIds = findActiveOrderIds(result.getRecords());
        Map<Long, List<Long>> activeItemsByOrderId = findActiveItemIds(activeOrderIds);

        return PageResponse.of(
                result.getRecords().stream()
                        .map(item -> toVO(item, studentMap.get(item.getStudentId()),
                                activeOrderIds.contains(item.getEnrollmentOrderId()),
                                activeItemsByOrderId.getOrDefault(item.getEnrollmentOrderId(), List.of())))
                        .toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }

    @Override
    public ReceiptRecordVO getReceipt(Long id) {
        ReceiptRecordEntity receipt = receiptRecordMapper.selectById(id);
        if (receipt == null) {
            throw new BusinessException("RECEIPT_NOT_FOUND", "Receipt not found.");
        }
        StudentEntity student = studentMapper.selectById(receipt.getStudentId());
        boolean orderActive = receipt.getEnrollmentOrderId() != null
                && enrollmentOrderMapper.selectById(receipt.getEnrollmentOrderId()) != null;
        List<Long> activeItemIds = orderActive
                ? findActiveItemIds(Set.of(receipt.getEnrollmentOrderId()))
                        .getOrDefault(receipt.getEnrollmentOrderId(), List.of())
                : List.of();
        return toVO(receipt, student, orderActive, activeItemIds);
    }

    /** 批量判断收据关联的报名单是否仍有效，避免历史收据触发已删除订单的操作。 */
    private Set<Long> findActiveOrderIds(List<ReceiptRecordEntity> receipts) {
        List<Long> orderIds = receipts.stream()
                .map(ReceiptRecordEntity::getEnrollmentOrderId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (orderIds.isEmpty()) {
            return Set.of();
        }
        return enrollmentOrderMapper.selectBatchIds(orderIds).stream()
                .map(EnrollmentOrderEntity::getId)
                .collect(Collectors.toSet());
    }

    /** 只把仍属于有效订单的课程项开放给收据页删除选择器。 */
    private Map<Long, List<Long>> findActiveItemIds(Set<Long> activeOrderIds) {
        if (activeOrderIds.isEmpty()) {
            return Map.of();
        }
        return enrollmentOrderItemMapper.selectList(new LambdaQueryWrapper<EnrollmentOrderItemEntity>()
                        .in(EnrollmentOrderItemEntity::getEnrollmentOrderId, activeOrderIds))
                .stream()
                .filter(item -> item.getId() != null && item.getEnrollmentOrderId() != null)
                .collect(Collectors.groupingBy(EnrollmentOrderItemEntity::getEnrollmentOrderId,
                        Collectors.mapping(EnrollmentOrderItemEntity::getId, Collectors.toList())));
    }

    private List<Long> findMatchedStudentIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        return studentMapper.selectList(new LambdaQueryWrapper<StudentEntity>()
                        .and(w -> w.like(StudentEntity::getStudentName, keyword)
                                .or()
                                .like(StudentEntity::getStudentNo, keyword)))
                .stream()
                .map(StudentEntity::getId)
                .toList();
    }

    private ReceiptRecordVO toVO(ReceiptRecordEntity item, StudentEntity student, boolean orderActive,
                                 List<Long> activeCourseItemIds) {
        return ReceiptRecordVO.builder()
                .id(item.getId())
                .receiptNo(item.getReceiptNo())
                .enrollmentOrderId(item.getEnrollmentOrderId())
                .studentId(item.getStudentId())
                .paymentRecordId(item.getPaymentRecordId())
                .enrollmentOrderActive(orderActive)
                .activeCourseItemIds(activeCourseItemIds)
                .studentName(student == null ? null : student.getStudentName())
                .templateCode(item.getTemplateCode())
                .receiptPayload(item.getReceiptPayload())
                .fileUrl(item.getFileUrl())
                .issuedAt(item.getIssuedAt())
                .build();
    }
}
