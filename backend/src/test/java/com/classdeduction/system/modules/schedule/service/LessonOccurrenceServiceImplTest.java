package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonStatusRuleMapper;
import com.classdeduction.system.modules.schedule.dto.LessonOccurrencePageQuery;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.service.impl.LessonOccurrenceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 课次查询服务测试：覆盖查询窗口保护，避免生产环境出现超大时间范围扫描。
 */
@ExtendWith(MockitoExtension.class)
class LessonOccurrenceServiceImplTest {

    @Mock
    private LessonOccurrenceMapper lessonOccurrenceMapper;

    @Mock
    private LessonRecordMapper lessonRecordMapper;

    @Mock
    private LessonStatusRuleMapper lessonStatusRuleMapper;

    @Mock
    private CoursePackageMapper coursePackageMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private ClassroomMapper classroomMapper;

    @Mock
    private com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper classGroupMapper;

    private LessonOccurrenceServiceImpl lessonOccurrenceService;

    @BeforeEach
    void setUp() {
        lessonOccurrenceService = new LessonOccurrenceServiceImpl(
                lessonOccurrenceMapper,
                lessonRecordMapper,
                lessonStatusRuleMapper,
                coursePackageMapper,
                studentMapper,
                teacherMapper,
                classroomMapper,
                classGroupMapper
        );
    }

    @Test
    void shouldRejectTooLargeOccurrenceQueryWindow() {
        LessonOccurrencePageQuery query = new LessonOccurrencePageQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setStartAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        query.setEndAt(LocalDateTime.of(2026, 8, 1, 0, 0));

        assertThatThrownBy(() -> lessonOccurrenceService.pageOccurrences(query))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Query window cannot exceed 180 days.");
    }
}
