package com.classdeduction.system.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ClassEnrollmentMapper extends BaseMapper<ClassEnrollmentEntity> {

    /**
     * 查询班课下所有报名状态，包含已退出的软删除记录，用于进度页状态过滤。
     */
    @Select("""
            SELECT *
            FROM class_enrollment
            WHERE class_id = #{classId}
            ORDER BY enrollment_date ASC, id ASC
            """)
    List<ClassEnrollmentEntity> selectAllStatusesByClassId(@Param("classId") Long classId);

    /**
     * 批量统计指定班级的活跃在册人数。
     */
    @Select({"<script>",
            "SELECT class_id, COUNT(*) AS cnt",
            "FROM class_enrollment",
            "WHERE class_id IN",
            "<foreach item='id' collection='classIds' open='(' separator=',' close=')'>#{id}</foreach>",
            "AND status = 'ACTIVE'",
            "AND is_deleted = 0",
            "GROUP BY class_id",
            "</script>"})
    List<Map<String, Object>> countActiveByClassIds(@Param("classIds") List<Long> classIds);

    /** 当前读出目标学生在该实际班级的重叠权益；报名/更正前已锁定学生行，确保并发请求顺序可见。 */
    @Select("""
            SELECT id
            FROM class_enrollment
            WHERE student_id = #{studentId}
              AND class_id = #{classId}
              AND status = 'ACTIVE'
              AND is_deleted = 0
              AND enrollment_date <= #{endDate}
              AND expiry_date >= #{startDate}
            FOR UPDATE
            """)
    List<Long> selectActiveOverlappingForUpdate(@Param("studentId") Long studentId,
                                                @Param("classId") Long classId,
                                                @Param("startDate") java.time.LocalDate startDate,
                                                @Param("endDate") java.time.LocalDate endDate);
}
