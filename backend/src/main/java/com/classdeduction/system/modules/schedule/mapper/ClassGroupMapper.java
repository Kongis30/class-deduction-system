package com.classdeduction.system.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ClassGroupMapper extends BaseMapper<ClassGroupEntity> {

    /** 班级报名、排课修改和删除共用此行锁，确保报名权益基于同一份实际课表。 */
    @Select("SELECT * FROM class_group WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    ClassGroupEntity selectByIdForUpdate(@Param("id") Long id);

    /** 项目假期会重排所有实际班级，按固定顺序锁住项目内班级以串行化报名。 */
    @Select("SELECT * FROM class_group WHERE group_project_id = #{projectId} AND is_deleted = 0 ORDER BY id FOR UPDATE")
    List<ClassGroupEntity> selectByProjectIdForUpdate(@Param("projectId") Long projectId);
}
