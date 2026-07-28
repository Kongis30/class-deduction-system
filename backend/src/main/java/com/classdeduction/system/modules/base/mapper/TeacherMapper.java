package com.classdeduction.system.modules.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface TeacherMapper extends BaseMapper<TeacherEntity> {

    /**
     * 按校区统计教师数（批量GROUP BY，避免N+1）
     */
    @Select("SELECT campus_id, COUNT(*) AS cnt FROM teacher GROUP BY campus_id")
    List<Map<String, Object>> countGroupByCampus();

    /**
     * 查询已软删除的教师档案（绕过 @TableLogic 自动过滤）。
     */
    @Select("SELECT * FROM teacher WHERE is_deleted = 1 ORDER BY deleted_at DESC, updated_at DESC")
    List<TeacherEntity> selectDeleted();

    /**
     * 按 ID 批量查询教师档案，忽略逻辑删除标记，用于展示已删除账号关联的档案信息。
     */
    @Select("<script>"
            + "SELECT * FROM teacher WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<TeacherEntity> selectByIdsIgnoreLogicDelete(@Param("ids") Collection<Long> ids);

    /**
     * 查询指定前缀的最大教师编号，忽略逻辑删除标记。
     * 软删除的档案仍占用 uk_teacher_no 唯一索引，生成新编号时必须把它们算进来，避免重复键。
     */
    @Select("SELECT teacher_no FROM teacher WHERE teacher_no LIKE CONCAT(#{prefix}, '%') "
            + "ORDER BY teacher_no DESC LIMIT 1")
    String selectMaxTeacherNoByPrefix(@Param("prefix") String prefix);

    /**
     * 锁定教师行以串行化对同一教师排课时间段的并发调整，避免冲突检查被并发穿透。
     */
    @Select("SELECT * FROM teacher WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    TeacherEntity selectByIdForUpdate(@Param("id") Long id);
}
