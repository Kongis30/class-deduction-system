package com.classdeduction.system.modules.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface StudentMapper extends BaseMapper<StudentEntity> {

    /**
     * 按校区统计学生数（批量GROUP BY，避免N+1）
     */
    @Select("SELECT campus_id, COUNT(*) AS cnt FROM student GROUP BY campus_id")
    List<Map<String, Object>> countGroupByCampus();

    /**
     * 查询指定前缀的最大学号（忽略逻辑删除），用于学号生成。
     */
    @Select("SELECT student_no FROM student WHERE student_no LIKE CONCAT(#{prefix}, '%') ORDER BY student_no DESC LIMIT 1")
    String selectMaxStudentNoByPrefix(@Param("prefix") String prefix);

    /**
     * 查询已软删除的学生档案（绕过 @TableLogic 自动过滤）。
     */
    @Select("SELECT * FROM student WHERE is_deleted = 1 ORDER BY deleted_at DESC, updated_at DESC")
    List<StudentEntity> selectDeleted();

    /**
     * 按 ID 批量查询学生档案，忽略逻辑删除标记，用于展示已删除账号关联的档案信息。
     */
    @Select("<script>"
            + "SELECT * FROM student WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<StudentEntity> selectByIdsIgnoreLogicDelete(@Param("ids") Collection<Long> ids);
}
