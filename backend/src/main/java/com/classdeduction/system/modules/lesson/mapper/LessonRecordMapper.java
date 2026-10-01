package com.classdeduction.system.modules.lesson.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LessonRecordMapper extends BaseMapper<LessonRecordEntity> {

    /** 班课记录撤销先锁实际记录，再恢复课次，和班级确认共用班级行锁。 */
    @Select("SELECT * FROM lesson_record WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    LessonRecordEntity selectByIdForUpdate(@Param("id") Long id);
}
