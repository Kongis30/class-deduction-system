package com.classdeduction.system.modules.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.finance.dto.TeacherPayRateSaveRequest;
import com.classdeduction.system.modules.finance.entity.TeacherPayRateEntity;
import com.classdeduction.system.modules.finance.mapper.TeacherPayRateMapper;
import com.classdeduction.system.modules.finance.service.TeacherPayRateService;
import com.classdeduction.system.modules.finance.vo.TeacherPayRateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    private final StudentMapper studentMapper;

    @Override
    public List<TeacherPayRateVO> listPayRates(Long teacherId, Long courseProductId) {
        List<TeacherPayRateEntity> rates = payRateMapper.selectList(
                new LambdaQueryWrapper<TeacherPayRateEntity>()
                        .eq(teacherId != null, TeacherPayRateEntity::getTeacherId, teacherId)
                        .eq(courseProductId != null, TeacherPayRateEntity::getCourseProductId, courseProductId)
                        .orderByAsc(TeacherPayRateEntity::getTeacherId)
                        .orderByAsc(TeacherPayRateEntity::getCourseProductId)
                        .orderByAsc(TeacherPayRateEntity::getGradeBand)
                        .orderByAsc(TeacherPayRateEntity::getDeliveryMode)
        );
        if (rates.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> teacherIds = rates.stream().map(TeacherPayRateEntity::getTeacherId).collect(Collectors.toSet());
        Set<Long> productIds = rates.stream().map(TeacherPayRateEntity::getCourseProductId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> studentIds = rates.stream().map(TeacherPayRateEntity::getStudentId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, TeacherEntity> teacherMap = teacherMapper.selectBatchIds(teacherIds).stream()
                .collect(Collectors.toMap(TeacherEntity::getId, t -> t));
        Map<Long, CourseProductEntity> productMap = productIds.isEmpty() ? Collections.emptyMap()
                : courseProductMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(CourseProductEntity::getId, p -> p));
        Map<Long, String> studentNameMap = studentIds.isEmpty() ? Collections.emptyMap()
                : studentMapper.selectBatchIds(studentIds).stream()
                        .collect(Collectors.toMap(item -> item.getId(), item -> item.getStudentName()));

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
                    .studentId(rate.getStudentId())
                    .studentName(studentNameMap.get(rate.getStudentId()))
                    .gradeBand(rate.getGradeBand())
                    .deliveryMode(rate.getDeliveryMode())
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
        if (request.getCourseProductId() != null
                && courseProductMapper.selectById(request.getCourseProductId()) == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "Course product not found.");
        }
        if (request.getStudentId() != null && studentMapper.selectById(request.getStudentId()) == null) {
            throw new BusinessException("STUDENT_NOT_FOUND", "Student not found.");
        }
        String gradeBand = normalizeGradeBand(request.getGradeBand());
        String deliveryMode = normalizeDeliveryMode(request.getDeliveryMode());

        // Upsert: 存在则更新，不存在则创建
        TeacherPayRateEntity existing = payRateMapper.selectOne(
                new LambdaQueryWrapper<TeacherPayRateEntity>()
                        .eq(TeacherPayRateEntity::getTeacherId, request.getTeacherId())
                        .eq(request.getCourseProductId() != null,
                                TeacherPayRateEntity::getCourseProductId, request.getCourseProductId())
                        .isNull(request.getCourseProductId() == null, TeacherPayRateEntity::getCourseProductId)
                        .eq(request.getStudentId() != null, TeacherPayRateEntity::getStudentId, request.getStudentId())
                        .isNull(request.getStudentId() == null, TeacherPayRateEntity::getStudentId)
                        .eq(gradeBand != null, TeacherPayRateEntity::getGradeBand, gradeBand)
                        .isNull(gradeBand == null, TeacherPayRateEntity::getGradeBand)
                        .eq(deliveryMode != null, TeacherPayRateEntity::getDeliveryMode, deliveryMode)
                        .isNull(deliveryMode == null, TeacherPayRateEntity::getDeliveryMode)
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
        entity.setStudentId(request.getStudentId());
        entity.setGradeBand(gradeBand);
        entity.setDeliveryMode(deliveryMode);
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

    private String normalizeGradeBand(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return switch (value.trim().toUpperCase()) {
            case "K-5", "K TO 5", "K-5 GRADE", "ELEMENTARY SCHOOL" -> "K-5";
            case "6-8", "6 TO 8", "MIDDLE SCHOOL" -> "6-8";
            case "9-12", "9 TO 12", "HIGH SCHOOL" -> "9-12";
            case "AP", "AP LEVEL" -> "AP LEVEL";
            default -> throw new BusinessException("INVALID_GRADE_BAND",
                    "Grade band must be K-5, 6-8, 9-12, or AP Level.");
        };
    }

    private String normalizeDeliveryMode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if (!"ONLINE".equals(normalized) && !"OFFLINE".equals(normalized)) {
            throw new BusinessException("INVALID_DELIVERY_MODE", "Delivery mode must be Online or Offline.");
        }
        return normalized;
    }
}
