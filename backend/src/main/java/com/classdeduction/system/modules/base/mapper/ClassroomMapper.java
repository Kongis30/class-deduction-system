package com.classdeduction.system.modules.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface ClassroomMapper extends BaseMapper<ClassroomEntity> {

    /**
     * 按校区统计教室数（批量GROUP BY，避免N+1）
     */
    @Select("SELECT campus_id, COUNT(*) AS cnt FROM classroom GROUP BY campus_id")
    List<Map<String, Object>> countGroupByCampus();

    /**
     * 同步更新某个校区下所有教室的冗余 campus 名称字段
     */
    @Update("UPDATE classroom SET campus = #{campusName} WHERE campus_id = #{campusId}")
    int syncCampusName(@Param("campusId") Long campusId, @Param("campusName") String campusName);
}
