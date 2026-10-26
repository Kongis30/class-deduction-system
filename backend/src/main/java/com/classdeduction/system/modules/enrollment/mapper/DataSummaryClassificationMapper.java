package com.classdeduction.system.modules.enrollment.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 独立保存 Data Summary 的可逆归类，不复用退款、停课或课时状态。 */
@Mapper
public interface DataSummaryClassificationMapper {

    @Select({"<script>",
            "SELECT record_key FROM data_summary_record_classification WHERE order_id IN",
            "<foreach collection='orderIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"})
    List<String> selectRecordKeysByOrderIds(@Param("orderIds") List<Long> orderIds);

    @Insert("INSERT INTO data_summary_record_classification "
            + "(record_key, order_id, order_item_id, student_id, participant_index) "
            + "VALUES (#{recordKey}, #{orderId}, #{orderItemId}, #{studentId}, #{participantIndex}) "
            + "ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP")
    int insertDiscontinued(@Param("recordKey") String recordKey,
                           @Param("orderId") Long orderId,
                           @Param("orderItemId") Long orderItemId,
                           @Param("studentId") Long studentId,
                           @Param("participantIndex") Integer participantIndex);

    @Delete({"<script>",
            "DELETE FROM data_summary_record_classification WHERE record_key IN",
            "<foreach collection='recordKeys' item='key' open='(' separator=',' close=')'>#{key}</foreach>",
            "</script>"})
    int deleteRecordKeys(@Param("recordKeys") List<String> recordKeys);
}
