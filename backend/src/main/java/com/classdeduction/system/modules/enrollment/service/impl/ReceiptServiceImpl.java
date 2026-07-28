package com.classdeduction.system.modules.enrollment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.enrollment.dto.ReceiptPageQuery;
import com.classdeduction.system.modules.enrollment.entity.ReceiptRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.ReceiptRecordMapper;
import com.classdeduction.system.modules.enrollment.service.ReceiptService;
import com.classdeduction.system.modules.enrollment.vo.ReceiptRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 收据服务：负责收据列表和详情查询。
 */
@Service
@RequiredArgsConstructor
public class ReceiptServiceImpl implements ReceiptService {

    private final ReceiptRecordMapper receiptRecordMapper;
    private final StudentMapper studentMapper;

    @Override
    public PageResponse<ReceiptRecordVO> pageReceipts(ReceiptPageQuery query) {
        Page<ReceiptRecordEntity> page = new Page<>(query.getPageNum(), query.getPageSize());
        List<Long> matchedStudentIds = findMatchedStudentIds(query.getKeyword());
        LambdaQueryWrapper<ReceiptRecordEntity> wrapper = new LambdaQueryWrapper<ReceiptRecordEntity>()
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(ReceiptRecordEntity::getReceiptNo, query.getKeyword())
                        .or(!matchedStudentIds.isEmpty(), nested -> nested.in(ReceiptRecordEntity::getStudentId, matchedStudentIds)))
                .eq(query.getStudentId() != null, ReceiptRecordEntity::getStudentId, query.getStudentId())
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

        return PageResponse.of(
                result.getRecords().stream()
                        .map(item -> toVO(item, studentMap.get(item.getStudentId())))
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
        return toVO(receipt, student);
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

    private ReceiptRecordVO toVO(ReceiptRecordEntity item, StudentEntity student) {
        return ReceiptRecordVO.builder()
                .id(item.getId())
                .receiptNo(item.getReceiptNo())
                .enrollmentOrderId(item.getEnrollmentOrderId())
                .studentId(item.getStudentId())
                .studentName(student == null ? null : student.getStudentName())
                .templateCode(item.getTemplateCode())
                .receiptPayload(item.getReceiptPayload())
                .fileUrl(item.getFileUrl())
                .issuedAt(item.getIssuedAt())
                .build();
    }
}
