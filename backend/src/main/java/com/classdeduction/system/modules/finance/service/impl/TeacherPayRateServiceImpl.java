package com.classdeduction.system.modules.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.finance.dto.TeacherPayRateSaveRequest;
import com.classdeduction.system.modules.finance.entity.TeacherPayRateEntity;
import com.classdeduction.system.modules.finance.mapper.TeacherPayRateMapper;
import com.classdeduction.system.modules.finance.service.TeacherPayRateService;
import com.classdeduction.system.modules.finance.vo.TeacherPayRateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherPayRateServiceImpl implements TeacherPayRateService {

    private final TeacherPayRateMapper payRateMapper;
    private final TeacherMapper teacherMapper;
    private final CourseProductMapper courseProductMapper;

    @Override
    public List<TeacherPayRateVO> listPayRates(Long teacherId, Long courseProductId) {
        List<TeacherPayRateEntity> rates = payRateMapper.selectList(
                new LambdaQueryWrapper<TeacherPayRateEntity>()
                        .eq(teacherId != null, TeacherPayRateEntity::getTeacherId, teacherId)
                        .eq(courseProductId != null, TeacherPayRateEntity::getCourseProductId, courseProductId)
                        .orderByAsc(TeacherPayRateEntity::getTeacherId)
        );
        if (rates.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> teacherIds = rates.stream().map(TeacherPayRateEntity::getTeacherId).collect(Collectors.toSet());
        Set<Long> productIds = rates.stream().map(TeacherPayRateEntity::getCourseProductId).collect(Collectors.toSet());

        Map<Long, TeacherEntity> teacherMap = teacherMapper.selectBatchIds(teacherIds).stream()
                .collect(Collectors.toMap(TeacherEntity::getId, t -> t));
        Map<Long, CourseProductEntity> productMap = courseProductMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(CourseProductEntity::getId, p -> p));

        return rates.stream().map(rate -> {
            TeacherEntity teacher = teacherMap.get(rate.getTeacherId());
            CourseProductEntity product = productMap.get(rate.getCourseProductId());
            return TeacherPayRateVO.builder()
                    .id(rate.getId())
                    .teacherId(rate.getTeacherId())
                    .teacherNo(teacher == null ? null : teacher.getTeacherNo())
                    .teacherName(teacher == null ? null : teacher.getTeacherName())
                    .courseProductId(rate.getCourseProductId())
                    .productCode(product == null ? null : product.getProductCode())
                    .productName(product == null ? null : product.getProductName())
                    .subjectName(product == null ? null : product.getSubjectName())
                    .hourlyRate(rate.getHourlyRate())
                    .notes(rate.getNotes())
                    .build();
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long savePayRate(TeacherPayRateSaveRequest request) {
        // 检查老师存在
        if (teacherMapper.selectById(request.getTeacherId()) == null) {
            throw new BusinessException("TEACHER_NOT_FOUND", "Teacher not found.");
        }
        // 检查产品存在
        if (courseProductMapper.selectById(request.getCourseProductId()) == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "Course product not found.");
        }

        // Upsert: 存在则更新，不存在则创建
        TeacherPayRateEntity existing = payRateMapper.selectOne(
                new LambdaQueryWrapper<TeacherPayRateEntity>()
                        .eq(TeacherPayRateEntity::getTeacherId, request.getTeacherId())
                        .eq(TeacherPayRateEntity::getCourseProductId, request.getCourseProductId())
                        .last("LIMIT 1"));

        if (existing != null) {
            existing.setHourlyRate(request.getHourlyRate());
            existing.setNotes(request.getNotes());
            payRateMapper.updateById(existing);
            return existing.getId();
        }

        TeacherPayRateEntity entity = new TeacherPayRateEntity();
        entity.setTeacherId(request.getTeacherId());
        entity.setCourseProductId(request.getCourseProductId());
        entity.setHourlyRate(request.getHourlyRate());
        entity.setNotes(request.getNotes());
        payRateMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePayRate(Long id) {
        TeacherPayRateEntity existing = payRateMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("PAY_RATE_NOT_FOUND", "Pay rate not found.");
        }
        payRateMapper.deleteById(id);
    }
}
