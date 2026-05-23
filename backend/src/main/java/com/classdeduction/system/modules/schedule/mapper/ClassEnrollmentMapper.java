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
}
