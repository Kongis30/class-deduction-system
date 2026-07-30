package com.classdeduction.system.modules.base.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.auth.entity.UserAccountEntity;
import com.classdeduction.system.modules.auth.mapper.AuthSessionMapper;
import com.classdeduction.system.modules.auth.mapper.UserAccountMapper;
import com.classdeduction.system.modules.auth.mapper.UserRoleMapper;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.base.service.impl.TeacherServiceImpl;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.finance.mapper.TeacherPayRateMapper;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherServiceImplTest {

    @Mock
    private TeacherMapper teacherMapper;
    @Mock
    private LessonOccurrenceMapper lessonOccurrenceMapper;
    @Mock
    private RecurringScheduleRuleMapper recurringScheduleRuleMapper;
    @Mock
    private UserAccountMapper userAccountMapper;
    @Mock
    private ClassGroupMapper classGroupMapper;
    @Mock
    private PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;
    @Mock
    private AuthSessionMapper authSessionMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private CourseProductMapper courseProductMapper;
    @Mock
    private TeacherPayRateMapper teacherPayRateMapper;

    private TeacherServiceImpl teacherService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        Configuration configuration = new Configuration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CourseProductEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ClassGroupEntity.class);
    }

    @BeforeEach
    void setUp() {
        teacherService = new TeacherServiceImpl(
                teacherMapper,
                lessonOccurrenceMapper,
                recurringScheduleRuleMapper,
                userAccountMapper,
                classGroupMapper,
                packageTeacherAssignmentMapper,
                new JsonHelper(new ObjectMapper()),
                authSessionMapper,
                userRoleMapper,
                courseProductMapper,
                teacherPayRateMapper
        );
    }

    @Test
    void shouldCascadeDeleteLoginAndOperationalLinksWhenDeletingTeacher() {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(701L);
        teacher.setTeacherName("Demo Teacher");
        UserAccountEntity account = new UserAccountEntity();
        account.setId(9001L);
        account.setTeacherId(701L);
        when(teacherMapper.selectById(701L)).thenReturn(teacher);
        when(userAccountMapper.selectList(any())).thenReturn(List.of(account));

        teacherService.deleteTeacher(701L);

        verify(userRoleMapper).delete(any());
        verify(authSessionMapper).delete(any());
        verify(userAccountMapper).delete(any());
        verify(lessonOccurrenceMapper).delete(any());
        verify(recurringScheduleRuleMapper).delete(any());
        verify(packageTeacherAssignmentMapper).delete(any());
        verify(teacherPayRateMapper).delete(any());
        verify(courseProductMapper).update(any(), any());
        verify(classGroupMapper).update(any(), any());
        verify(teacherMapper).deleteById(701L);
    }
}
