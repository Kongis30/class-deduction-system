package com.classdeduction.system.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.finance.dto.UnpaidLessonQuery;
import com.classdeduction.system.modules.finance.entity.TeacherPaymentItemEntity;
import com.classdeduction.system.modules.finance.mapper.TeacherPayRateMapper;
import com.classdeduction.system.modules.finance.mapper.TeacherPaymentItemMapper;
import com.classdeduction.system.modules.finance.mapper.TeacherPaymentMapper;
import com.classdeduction.system.modules.finance.service.impl.TeacherPaymentServiceImpl;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 老师工资结算测试：确保未结算课时只读取管理员终确认后的记录。
 */
@ExtendWith(MockitoExtension.class)
class TeacherPaymentServiceImplTest {

    @Mock
    private LessonRecordMapper lessonRecordMapper;

    @Mock
    private CoursePackageMapper coursePackageMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private CourseProductMapper courseProductMapper;

    @Mock
    private TeacherPayRateMapper payRateMapper;

    @Mock
    private TeacherPaymentMapper paymentMapper;

    @Mock
    private TeacherPaymentItemMapper paymentItemMapper;

    @Mock
    private LessonOccurrenceMapper lessonOccurrenceMapper;

    @Mock
    private ClassGroupMapper classGroupMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<LessonRecordEntity>> recordQueryCaptor;

    private TeacherPaymentServiceImpl teacherPaymentService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        // 单元测试不启动 MyBatis 上下文，手动初始化 LambdaQueryWrapper 字段缓存。
        Configuration configuration = new Configuration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), TeacherPaymentItemEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), LessonRecordEntity.class);
    }

    @BeforeEach
    void setUp() {
        teacherPaymentService = new TeacherPaymentServiceImpl(
                lessonRecordMapper,
                coursePackageMapper,
                studentMapper,
                teacherMapper,
                courseProductMapper,
                payRateMapper,
                paymentMapper,
                paymentItemMapper,
                lessonOccurrenceMapper,
                classGroupMapper
        );
    }

    @Test
    void shouldQueryOnlyConfirmedRecordsForUnpaidLessons() {
        UnpaidLessonQuery query = new UnpaidLessonQuery();
        query.setTeacherId(701L);
        when(paymentItemMapper.selectList(any())).thenReturn(Collections.<TeacherPaymentItemEntity>emptyList());
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of());

        assertThat(teacherPaymentService.queryUnpaidLessons(query)).isEmpty();

        verify(lessonRecordMapper).selectList(recordQueryCaptor.capture());
        LambdaQueryWrapper<LessonRecordEntity> wrapper = recordQueryCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("confirmStage");
        assertThat(wrapper.getParamNameValuePairs()).containsValue("CONFIRMED");
    }
}
