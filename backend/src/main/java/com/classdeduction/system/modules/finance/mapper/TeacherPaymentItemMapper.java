package com.classdeduction.system.modules.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.finance.entity.TeacherPaymentItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TeacherPaymentItemMapper extends BaseMapper<TeacherPaymentItemEntity> {

    @Select("SELECT COUNT(*) FROM teacher_payment_item WHERE lesson_record_id = #{lessonRecordId}")
    long countByLessonRecordId(@Param("lessonRecordId") Long lessonRecordId);
}
