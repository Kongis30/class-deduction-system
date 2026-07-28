package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.common.model.PageResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.CoursePackagePageQuery;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.service.impl.CoursePackageServiceImpl;
import com.classdeduction.system.modules.enrollment.vo.CoursePackageListItemVO;
import com.classdeduction.system.modules.enrollment.vo.GroupClassPackageProgressVO;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 课时包服务测试：覆盖主老师筛选为空时的短路逻辑，避免分页总数和列表记录不一致。
 */
@ExtendWith(MockitoExtension.class)
class CoursePackageServiceImplTest {

    @Mock
    private CoursePackageMapper coursePackageMapper;

    @Mock
    private PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private CourseProductMapper courseProductMapper;

    @Mock
    private ClassGroupMapper classGroupMapper;

    @Mock
    private ClassEnrollmentMapper classEnrollmentMapper;

    @Mock
    private LessonOccurrenceMapper lessonOccurrenceMapper;

    @Mock
    private LessonRecordMapper lessonRecordMapper;

    private CoursePackageServiceImpl coursePackageService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        Configuration configuration = new Configuration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), LessonRecordEntity.class);
    }

    @BeforeEach
    void setUp() {
        coursePackageService = new CoursePackageServiceImpl(
                coursePackageMapper,
                packageTeacherAssignmentMapper,
                studentMapper,
                teacherMapper,
                courseProductMapper,
                classGroupMapper,
                classEnrollmentMapper,
                lessonOccurrenceMapper,
                lessonRecordMapper
        );
    }

    @Test
    void shouldReturnEmptyPageWhenTeacherFilterMatchesNoPrimaryPackage() {
        CoursePackagePageQuery query = new CoursePackagePageQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setTeacherId(301L);
        when(packageTeacherAssignmentMapper.selectList(any())).thenReturn(Collections.emptyList());

        PageResponse<CoursePackageListItemVO> result = coursePackageService.pagePackages(query);

        assertThat(result.records()).isEmpty();
        assertThat(result.total()).isZero();
        verify(coursePackageMapper, never()).selectPage(any(), any());
    }

    @Test
    void shouldCountOnlyFinallyConfirmedClassLessonsInGroupProgress() {
        CoursePackagePageQuery query = new CoursePackagePageQuery();
        query.setPageNum(1);
        query.setPageSize(10);

        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        classGroup.setProductId(201L);
        classGroup.setClassName("Saturday Math");
        classGroup.setStatus("ACTIVE");
        Page<ClassGroupEntity> page = new Page<>(1, 10);
        page.setRecords(List.of(classGroup));
        page.setTotal(1);

        CourseProductEntity product = new CourseProductEntity();
        product.setId(201L);
        product.setProductName("Saturday Math");
        product.setTeachingMode("GROUP");
        product.setBillingType("PER_SESSION");
        product.setTotalSessions(8);

        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setId(601L);
        occurrence.setClassGroupId(501L);
        occurrence.setPlanningStatus("SCHEDULED");

        when(classGroupMapper.selectPage(any(), any())).thenReturn(page);
        when(courseProductMapper.selectBatchIds(any())).thenReturn(List.of(product));
        when(classEnrollmentMapper.countActiveByClassIds(anyList())).thenReturn(Collections.emptyList());
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(occurrence));
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        PageResponse<GroupClassPackageProgressVO> result = coursePackageService.pageGroupClassPackages(query);

        assertThat(result.records()).singleElement().satisfies(progress -> {
            assertThat(progress.consumedUnits()).isZero();
            assertThat(progress.remainingUnits()).isEqualTo(8);
        });
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<LambdaQueryWrapper<LessonRecordEntity>> queryCaptor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(lessonRecordMapper).selectList(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getSqlSegment()).contains("confirmStage", "is_deleted");
        assertThat(queryCaptor.getValue().getParamNameValuePairs()).containsValue("CONFIRMED");
    }
}
