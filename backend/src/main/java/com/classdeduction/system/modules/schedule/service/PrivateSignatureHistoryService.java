package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.util.UnitConverter;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.schedule.vo.PrivateSignatureHistoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/** 为签字表读取有来源的历史行；真实消课和课包余额仍由原有服务负责。 */
@Service
@RequiredArgsConstructor
public class PrivateSignatureHistoryService {

    private final CoursePackageMapper coursePackageMapper;
    private final JdbcTemplate jdbcTemplate;

    /** 管理员按课包查看迁移历史，调用方只能在签字表中合并这些日期。 */
    public List<PrivateSignatureHistoryVO> listByPackage(Long packageId) {
        if (coursePackageMapper.selectById(packageId) == null) {
            throw new BusinessException("PACKAGE_NOT_FOUND", "Course package not found.");
        }
        return jdbcTemplate.query("""
                SELECT id, package_id, lesson_date, teacher_id, teacher_name,
                       subject_name, planned_units, signature_text
                FROM private_signature_history
                WHERE package_id = ?
                ORDER BY lesson_date, id
                """, (result, rowNum) -> new PrivateSignatureHistoryVO(
                result.getLong("id"), result.getLong("package_id"),
                result.getDate("lesson_date").toLocalDate(),
                result.getObject("teacher_id", Long.class), result.getString("teacher_name"),
                result.getString("subject_name"),
                UnitConverter.toHoursDecimal(result.getInt("planned_units")),
                result.getString("signature_text")), packageId);
    }
}
