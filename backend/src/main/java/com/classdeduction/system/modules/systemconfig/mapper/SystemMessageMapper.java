package com.classdeduction.system.modules.systemconfig.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.classdeduction.system.modules.systemconfig.entity.SystemMessageEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SystemMessageMapper extends BaseMapper<SystemMessageEntity> {

    @Select("""
            SELECT *
            FROM system_message
            WHERE recipient_user_id = #{recipientUserId}
              AND is_deleted = 0
            ORDER BY week_start_date DESC, created_at DESC
            LIMIT #{limit}
            """)
    List<SystemMessageEntity> selectRecentForUser(@Param("recipientUserId") Long recipientUserId,
                                                   @Param("limit") int limit);

    @Select("""
            SELECT *
            FROM system_message
            WHERE recipient_user_id = #{recipientUserId}
              AND message_type = #{messageType}
              AND week_start_date = #{weekStartDate}
              AND is_deleted = 0
            LIMIT 1
            """)
    SystemMessageEntity selectForWeek(@Param("recipientUserId") Long recipientUserId,
                                      @Param("messageType") String messageType,
                                      @Param("weekStartDate") LocalDate weekStartDate);

    /** 同一管理员同一周只保留一条；名单变化时重置已读状态。 */
    @Insert("""
            INSERT INTO system_message (
                id, recipient_user_id, message_type, week_start_date, title, item_count,
                students_payload, read_at, created_by, updated_by, created_at, updated_at, is_deleted, version
            ) VALUES (
                #{message.id}, #{message.recipientUserId}, #{message.messageType},
                #{message.weekStartDate}, #{message.title}, #{message.itemCount},
                #{message.studentsPayload}, NULL, #{message.updatedBy}, #{message.updatedBy},
                CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0, 0
            )
            ON DUPLICATE KEY UPDATE
                read_at = IF(students_payload <> VALUES(students_payload), NULL, read_at),
                title = VALUES(title),
                item_count = VALUES(item_count),
                students_payload = VALUES(students_payload),
                updated_by = VALUES(updated_by),
                updated_at = CURRENT_TIMESTAMP(3)
            """)
    int upsertWeeklyMessage(@Param("message") SystemMessageEntity message);

    @Update("""
            UPDATE system_message
            SET read_at = #{readAt}, updated_at = #{readAt}, updated_by = #{userId}
            WHERE id = #{messageId}
              AND recipient_user_id = #{userId}
              AND is_deleted = 0
              AND read_at IS NULL
            """)
    int markRead(@Param("messageId") Long messageId,
                 @Param("userId") Long userId,
                 @Param("readAt") LocalDateTime readAt);
}
