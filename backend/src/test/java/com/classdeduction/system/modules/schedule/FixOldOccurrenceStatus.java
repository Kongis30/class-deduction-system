package com.classdeduction.system.modules.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * 数据修正：将已有 lesson_record 关联但 planning_status 仍为 SCHEDULED 的 occurrence 标记为 MERGED。
 *
 * 运行：mvn test -Dtest=FixOldOccurrenceStatus#fixMergedStatus
 */
@SpringBootTest
public class FixOldOccurrenceStatus {

    @Autowired
    private LessonOccurrenceMapper lessonOccurrenceMapper;

    @Autowired
    private LessonRecordMapper lessonRecordMapper;

    @Test
    public void fixMergedStatus() {
        // 找出所有 planning_status = 'SCHEDULED' 的 occurrence，检查它们是否有关联的 lesson_record
        List<LessonOccurrenceEntity> scheduled = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getPlanningStatus, "SCHEDULED")
                        .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt)
        );

        int fixed = 0;
        for (LessonOccurrenceEntity occ : scheduled) {
            // 检查是否有对应的 lesson_record（通过 occurrenceId 关联）
            var record = lessonRecordMapper.selectOne(
                    new LambdaQueryWrapper<com.classdeduction.system.modules.lesson.entity.LessonRecordEntity>()
                            .eq(com.classdeduction.system.modules.lesson.entity.LessonRecordEntity::getOccurrenceId, occ.getId())
            );
            if (record != null) {
                occ.setPlanningStatus("MERGED");
                lessonOccurrenceMapper.updateById(occ);
                fixed++;
                System.out.printf("Fixed [%d/%d]: id=%d, lessonNo=%s, resultStatus=%s%n",
                        fixed, scheduled.size(), occ.getId(), occ.getLessonNo(), record.getResultStatus());
            }
        }

        System.out.println("\n✅ Total fixed: " + fixed + " / " + scheduled.size());
    }
}