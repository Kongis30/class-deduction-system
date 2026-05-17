package com.classdeduction.system.modules.enrollment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface CoursePackageMapper extends BaseMapper<CoursePackageEntity> {

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
