package com.classdeduction.system.modules.enrollment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.enrollment.dto.PaymentMethodSaveRequest;
import com.classdeduction.system.modules.enrollment.entity.PaymentMethodEntity;
import com.classdeduction.system.modules.enrollment.mapper.PaymentMethodMapper;
import com.classdeduction.system.modules.enrollment.service.PaymentMethodService;
import com.classdeduction.system.modules.enrollment.vo.PaymentMethodVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 支付方式服务实现。
 */
@Service
@RequiredArgsConstructor
public class PaymentMethodServiceImpl implements PaymentMethodService {

    private final PaymentMethodMapper paymentMethodMapper;

    @Override
    @Cacheable(value = "reference:paymentMethods", key = "'listActiveMethods'")
    public List<PaymentMethodVO> listActiveMethods() {
        return paymentMethodMapper.selectList(
                new LambdaQueryWrapper<PaymentMethodEntity>()
                        .eq(PaymentMethodEntity::getStatus, "ACTIVE")
                        .orderByAsc(PaymentMethodEntity::getSortOrder)
        ).stream().map(this::toVO).toList();
    }

    @Override
    @Cacheable(value = "reference:paymentMethodsAll", key = "'listAllMethods'")
    public List<PaymentMethodVO> listAllMethods() {
        return paymentMethodMapper.selectList(
                new LambdaQueryWrapper<PaymentMethodEntity>()
                        .orderByAsc(PaymentMethodEntity::getSortOrder)
        ).stream().map(this::toVO).toList();
    }

    @Override
    @Cacheable(value = "reference:paymentMethod", key = "#id")
    public PaymentMethodVO getMethod(Long id) {
        PaymentMethodEntity entity = requireEntity(id);
        return toVO(entity);
    }

    @Override
    @CacheEvict(value = {"reference:paymentMethods", "reference:paymentMethodsAll", "reference:paymentMethod"}, allEntries = true)
    public Long createMethod(PaymentMethodSaveRequest request) {
        // Check code uniqueness
        Long count = paymentMethodMapper.selectCount(
                new LambdaQueryWrapper<PaymentMethodEntity>()
                        .eq(PaymentMethodEntity::getMethodCode, request.getMethodCode())
        );
        if (count > 0) {
            throw new BusinessException("METHOD_CODE_EXISTS",
                    "Payment method code already exists: " + request.getMethodCode());
        }

        PaymentMethodEntity entity = new PaymentMethodEntity();
        entity.setMethodCode(request.getMethodCode());
        entity.setMethodName(request.getMethodName());
        entity.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        entity.setStatus(request.getStatus() != null ? request.getStatus() : "ACTIVE");
        paymentMethodMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @CacheEvict(value = {"reference:paymentMethods", "reference:paymentMethodsAll", "reference:paymentMethod"}, allEntries = true)
    public void updateMethod(Long id, PaymentMethodSaveRequest request) {
        PaymentMethodEntity entity = requireEntity(id);

        // Check code uniqueness (exclude self)
        Long count = paymentMethodMapper.selectCount(
                new LambdaQueryWrapper<PaymentMethodEntity>()
                        .eq(PaymentMethodEntity::getMethodCode, request.getMethodCode())
                        .ne(PaymentMethodEntity::getId, id)
        );
        if (count > 0) {
            throw new BusinessException("METHOD_CODE_EXISTS",
                    "Payment method code already exists: " + request.getMethodCode());
        }

        entity.setMethodCode(request.getMethodCode());
        entity.setMethodName(request.getMethodName());
        entity.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        entity.setStatus(request.getStatus() != null ? request.getStatus() : "ACTIVE");
        paymentMethodMapper.updateById(entity);
    }

    @Override
    @CacheEvict(value = {"reference:paymentMethods", "reference:paymentMethodsAll", "reference:paymentMethod"}, allEntries = true)
    public void deleteMethod(Long id) {
        PaymentMethodEntity entity = requireEntity(id);
        paymentMethodMapper.deleteById(entity.getId());
    }

    private PaymentMethodEntity requireEntity(Long id) {
        PaymentMethodEntity entity = paymentMethodMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("PAYMENT_METHOD_NOT_FOUND", "Payment method not found.");
        }
        return entity;
    }

    private PaymentMethodVO toVO(PaymentMethodEntity entity) {
        return PaymentMethodVO.builder()
                .id(entity.getId())
                .methodCode(entity.getMethodCode())
                .methodName(entity.getMethodName())
                .sortOrder(entity.getSortOrder())
                .status(entity.getStatus())
                .build();
    }
}
