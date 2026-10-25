package com.classdeduction.system.modules.lesson.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface LessonRecordMapper extends BaseMapper<LessonRecordEntity> {

    /** 班课记录撤销先锁实际记录，再恢复课次，和班级确认共用班级行锁。 */
    @Select("SELECT * FROM lesson_record WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    LessonRecordEntity selectByIdForUpdate(@Param("id") Long id);

    /** 共享私教撤销按固定顺序锁定所有成员记录，保持整组状态原子一致。 */
    @Select("SELECT * FROM lesson_record WHERE occurrence_id = #{occurrenceId} AND is_deleted = 0 ORDER BY id FOR UPDATE")
    List<LessonRecordEntity> selectByOccurrenceIdForUpdate(@Param("occurrenceId") Long occurrenceId);

    /** 改排课回收待处理课次时保留记录审计行，只软删除尚未最终确认的提交。 */
    @Update({"<script>",
            "UPDATE lesson_record SET is_deleted = 1, deleted_at = #{effectiveAt}, updated_at = #{effectiveAt}",
            "WHERE id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "AND is_deleted = 0 AND COALESCE(confirm_stage, '') &lt;&gt; 'CONFIRMED'",
            "</script>"})
    int softDeleteUnconfirmedByIds(@Param("ids") List<Long> ids,
                                   @Param("effectiveAt") java.time.LocalDateTime effectiveAt);

    /** 教师结算和消课撤销共用课次记录锁，避免一边付款一边回滚。 */
    @Select("<script>SELECT * FROM lesson_record WHERE is_deleted = 0 AND id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "ORDER BY id FOR UPDATE</script>")
    List<LessonRecordEntity> selectByIdsForUpdate(@Param("ids") List<Long> ids);
}
