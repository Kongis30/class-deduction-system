package com.classdeduction.system.modules.enrollment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.vo.ActiveOneOnOneStudentCourseGradeRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface CoursePackageMapper extends BaseMapper<CoursePackageEntity> {

    /** 查询排课侧栏分组所需的课程与年级信息，一次读取所有有效 1 对 1 课包。 */
    @Select("""
            SELECT s.id AS studentId,
                   s.student_name AS studentName,
                   COALESCE(NULLIF(TRIM(cp.product_name), ''), NULLIF(TRIM(pkg.package_name), ''), 'Unassigned course') AS courseName,
                   COALESCE(NULLIF(TRIM(oi.grade_band), ''), 'General') AS gradeBand,
                   EXISTS (
                       SELECT 1
                       FROM recurring_schedule_rule r
                       WHERE r.package_id = pkg.id
                         AND r.rule_status = 'ACTIVE'
                         AND r.is_deleted = 0
                   ) AS hasSchedule
            FROM course_package pkg
            JOIN student s ON s.id = pkg.student_id
            LEFT JOIN course_product cp ON cp.id = pkg.course_product_id
            LEFT JOIN enrollment_order_item oi ON oi.id = pkg.enrollment_order_item_id AND oi.is_deleted = 0
            WHERE s.status = 'ACTIVE'
              AND s.is_deleted = 0
              AND pkg.teaching_mode = 'ONE_ON_ONE'
              AND pkg.package_status = 'ACTIVE'
              AND pkg.is_deleted = 0
            ORDER BY s.student_no, s.id, courseName, gradeBand
            """)
    List<ActiveOneOnOneStudentCourseGradeRow> selectActiveOneOnOneStudentCourseGrades();

    /**
     * 按校区统计低余量课包数（通过 student JOIN 推算校区归属，批量GROUP BY，避免N+1）
     */
    @Select("SELECT s.campus_id, COUNT(*) AS cnt " +
            "FROM course_package pkg " +
            "JOIN student s ON pkg.student_id = s.id " +
            "WHERE pkg.package_status = 'ACTIVE' " +
            "AND pkg.remaining_units <= #{threshold} " +
            "GROUP BY s.campus_id")
    List<Map<String, Object>> countLowBalanceByCampus(@Param("threshold") Integer threshold);
}
