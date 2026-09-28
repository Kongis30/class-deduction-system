package com.classdeduction.system.modules.schedule.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.CoursePriceOptionMapper;
import com.classdeduction.system.modules.base.mapper.ProductGradeConfigMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.ClassHolidayEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassHolidayMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.service.impl.ClassHolidayServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassHolidayServiceImplTest {

    private static final Long PRODUCT_ID = 41L;
    private static final String GRADE = "G1-G5";

    @Mock private ClassHolidayMapper classHolidayMapper;
    @Mock private CourseProductMapper courseProductMapper;
    @Mock private CoursePriceOptionMapper coursePriceOptionMapper;
    @Mock private ProductGradeConfigMapper productGradeConfigMapper;
    @Mock private LessonOccurrenceMapper lessonOccurrenceMapper;
    @Mock private ClassEnrollmentMapper classEnrollmentMapper;
    @Mock private PrivateLessonGroupMemberMapper privateLessonGroupMemberMapper;
    @Mock private ClassGroupMapper classGroupMapper;
    @Mock private ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;
    @Mock private LessonRecordMapper lessonRecordMapper;

    private ClassHolidayServiceImpl service;

    @BeforeAll
    static void initializeMapperMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, ClassHolidayEntity.class);
        TableInfoHelper.initTableInfo(assistant, ClassGroupEntity.class);
        TableInfoHelper.initTableInfo(assistant, LessonOccurrenceEntity.class);
        TableInfoHelper.initTableInfo(assistant, ClassEnrollmentEntity.class);
        TableInfoHelper.initTableInfo(assistant, ClassGroupScheduleSlotEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = new ClassHolidayServiceImpl(classHolidayMapper, courseProductMapper, coursePriceOptionMapper,
                productGradeConfigMapper, lessonOccurrenceMapper,
                classEnrollmentMapper, privateLessonGroupMemberMapper, classGroupMapper,
                classGroupScheduleSlotMapper, lessonRecordMapper);
    }

    @Test
    void listHolidaysIsScopedToCourseAndGrade() {
        ClassHolidayEntity holiday = new ClassHolidayEntity();
        holiday.setId(12L);
        holiday.setCourseProductId(PRODUCT_ID);
        holiday.setGradeBand(GRADE);
        holiday.setHolidayName("Fall break");
        holiday.setStartDate(LocalDate.of(2026, 10, 1));
        holiday.setEndDate(LocalDate.of(2026, 10, 4));
        when(classHolidayMapper.selectList(any())).thenReturn(List.of(holiday));

        var result = service.listHolidays(PRODUCT_ID, GRADE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).holidayName()).isEqualTo("Fall break");
        ArgumentCaptor<LambdaQueryWrapper<ClassHolidayEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(classHolidayMapper).selectList(captor.capture());
        assertThat(captor.getValue().getCustomSqlSegment()).contains("grade_band");
        assertThat(captor.getValue().getParamNameValuePairs().values())
                .contains(PRODUCT_ID, GRADE, 0);
    }

    @Test
    void isHolidayChecksOnlySelectedGrade() {
        when(classHolidayMapper.selectCount(any())).thenReturn(1L);

        boolean result = service.isHoliday(PRODUCT_ID, GRADE, LocalDate.of(2026, 10, 2));

        assertThat(result).isTrue();
        ArgumentCaptor<LambdaQueryWrapper<ClassHolidayEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(classHolidayMapper).selectCount(captor.capture());
        assertThat(captor.getValue().getCustomSqlSegment()).contains("grade_band");
        assertThat(captor.getValue().getParamNameValuePairs().values())
                .contains(PRODUCT_ID, GRADE, 0, LocalDate.of(2026, 10, 2));
    }
}
