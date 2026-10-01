package com.classdeduction.system.modules.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.base.entity.GroupCourseProjectEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GroupCourseProjectMapper extends BaseMapper<GroupCourseProjectEntity> {

    /** 报名/维护项目共用行锁；调用方必须处于事务中。 */
    @Select("SELECT * FROM group_course_project WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    GroupCourseProjectEntity selectByIdForUpdate(@Param("id") Long id);
}
