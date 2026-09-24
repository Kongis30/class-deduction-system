package com.classdeduction.system.modules.enrollment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface CoursePackageMapper extends BaseMapper<CoursePackageEntity> {

    /** 查询有有效 1 对 1 课时包的在读学生，供排课页一次性填充学生列表。 */
    @Select("""
            SELECT s.id AS id, s.student_name AS studentName
            FROM student s
            WHERE s.status = 'ACTIVE'
              AND s.is_deleted = 0
              AND EXISTS (
                  SELECT 1
                  FROM course_package pkg
                  WHERE pkg.student_id = s.id
                    AND pkg.teaching_mode = 'ONE_ON_ONE'
                    AND pkg.package_status = 'ACTIVE'
                    AND pkg.is_deleted = 0
              )
            ORDER BY s.student_no, s.id
            """)
    List<StudentEntity> selectActiveOneOnOneStudents();

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
