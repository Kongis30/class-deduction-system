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

    /**
     * 软删除记录仍受 room_code 唯一键约束，自动生成编码时必须把历史记录一并纳入计算。
     */
    @Select("""
            SELECT room_code
            FROM classroom
            WHERE room_code REGEXP '^R[0-9]+$'
            ORDER BY CAST(SUBSTRING(room_code, 2) AS UNSIGNED) DESC
            LIMIT 1
            """)
    String selectMaxGeneratedRoomCodeIncludingDeleted();

    /**
     * 数据库唯一键不区分软删除，手动编码校验也需要覆盖历史记录，避免插入时抛 500。
     */
    @Select("""
            SELECT COUNT(*)
            FROM classroom
            WHERE room_code = #{roomCode}
              AND (#{currentId} IS NULL OR id <> #{currentId})
            """)
    Long countRoomCodeIncludingDeleted(@Param("currentId") Long currentId, @Param("roomCode") String roomCode);

    /**
     * Locks a classroom row while assigning pending lesson occurrences so that
     * overlapping backfill requests for the same room are serialized.
     */
    @Select("SELECT * FROM classroom WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    ClassroomEntity selectByIdForUpdate(@Param("id") Long id);
}
