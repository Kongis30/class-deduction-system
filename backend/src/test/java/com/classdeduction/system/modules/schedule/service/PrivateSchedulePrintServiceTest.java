package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleStartingBalanceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 打印历史要兼容旧排课中未填写教师或教室的时段。 */
@ExtendWith(MockitoExtension.class)
class PrivateSchedulePrintServiceTest {

    @Mock private CoursePackageMapper coursePackageMapper;
    @Mock private PrivateLessonGroupMemberMapper groupMemberMapper;
    @Mock private RecurringScheduleRuleMapper ruleMapper;
    @Mock private RecurringScheduleSlotMapper slotMapper;
    @Mock private RecurringScheduleStartingBalanceMapper startingBalanceMapper;
    @Mock private TeacherMapper teacherMapper;
    @Mock private ClassroomMapper classroomMapper;

    @Test
    void printHistoryKeepsLegacySlotWithoutTeacherOrClassroom() {
        RecurringScheduleRuleEntity rule = new RecurringScheduleRuleEntity();
        rule.setId(11L);
        rule.setPackageId(7L);
        rule.setStartDate(LocalDate.of(2026, 10, 1));

        RecurringScheduleSlotEntity slot = new RecurringScheduleSlotEntity();
        slot.setRuleId(11L);
        slot.setWeekday(1);
        slot.setStartTime(LocalTime.NOON);
        slot.setEndTime(LocalTime.of(14, 0));
        slot.setSubjectName("ELA");
        slot.setAllocatedUnits(4);

        when(coursePackageMapper.selectById(7L)).thenReturn(new CoursePackageEntity());
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));
        when(slotMapper.selectList(any())).thenReturn(List.of(slot));
        when(startingBalanceMapper.selectList(any())).thenReturn(List.of());

        PrivateSchedulePrintService service = new PrivateSchedulePrintService(coursePackageMapper,
                groupMemberMapper, ruleMapper, slotMapper, startingBalanceMapper, teacherMapper, classroomMapper);
        var revisions = service.listRevisions(7L);

        assertThat(revisions).hasSize(1);
        assertThat(revisions.get(0).slots()).hasSize(1);
        assertThat(revisions.get(0).slots().get(0).subjectName()).isEqualTo("ELA");
        assertThat(revisions.get(0).slots().get(0).teacherName()).isNull();
        assertThat(revisions.get(0).slots().get(0).classroomName()).isNull();
    }
}
