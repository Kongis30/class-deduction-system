package com.classdeduction.system.modules.schedule.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.classdeduction.system.modules.base.mapper.GroupCourseProjectMapper;
import com.classdeduction.system.modules.base.entity.GroupCourseProjectEntity;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.ClassHolidayEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.dto.ClassHolidaySaveRequest;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.service.ClassAdjustedSlotReservations;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassHolidayServiceImplTest {

    private static final Long PROJECT_ID = 41L;

    @Mock private ClassHolidayMapper classHolidayMapper;
    @Mock private GroupCourseProjectMapper projectMapper;
    @Mock private LessonOccurrenceMapper lessonOccurrenceMapper;
    @Mock private ClassEnrollmentMapper classEnrollmentMapper;
    @Mock private PrivateLessonGroupMemberMapper privateLessonGroupMemberMapper;
    @Mock private ClassGroupMapper classGroupMapper;
    @Mock private ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;
    @Mock private ClassAdjustedSlotReservations adjustedSlotReservations;
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
        lenient().when(adjustedSlotReservations.load(any())).thenReturn(Set.of());
        service = new ClassHolidayServiceImpl(classHolidayMapper, projectMapper, lessonOccurrenceMapper,
                classEnrollmentMapper, privateLessonGroupMemberMapper, classGroupMapper,
                classGroupScheduleSlotMapper, adjustedSlotReservations, lessonRecordMapper);
    }

    @Test
    void listHolidaysIsScopedToTeachingProject() {
        ClassHolidayEntity holiday = new ClassHolidayEntity();
        holiday.setId(12L);
        holiday.setGroupProjectId(PROJECT_ID);
        holiday.setHolidayName("Fall break");
        holiday.setStartDate(LocalDate.of(2026, 10, 1));
        holiday.setEndDate(LocalDate.of(2026, 10, 4));
        when(classHolidayMapper.selectList(any())).thenReturn(List.of(holiday));

        var result = service.listHolidays(PROJECT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).holidayName()).isEqualTo("Fall break");
        ArgumentCaptor<LambdaQueryWrapper<ClassHolidayEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(classHolidayMapper).selectList(captor.capture());
        assertThat(captor.getValue().getCustomSqlSegment()).contains("group_project_id");
        assertThat(captor.getValue().getParamNameValuePairs().values()).contains(PROJECT_ID, 0);
    }

    @Test
    void holidayLookupUsesProjectAndDateRange() {
        when(classHolidayMapper.selectCount(any())).thenReturn(1L);

        boolean result = service.isHoliday(PROJECT_ID, LocalDate.of(2026, 10, 2));

        assertThat(result).isTrue();
        ArgumentCaptor<LambdaQueryWrapper<ClassHolidayEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(classHolidayMapper).selectCount(captor.capture());
        assertThat(captor.getValue().getCustomSqlSegment()).contains("group_project_id");
        assertThat(captor.getValue().getParamNameValuePairs().values())
                .contains(PROJECT_ID, 0, LocalDate.of(2026, 10, 2));
    }

    @Test
    void holidayMutationLocksClassesAndProjectBeforeItsFirstConsistentRead() {
        GroupCourseProjectEntity project = new GroupCourseProjectEntity();
        project.setId(PROJECT_ID);
        project.setIsDeleted(0);
        project.setStatus("ACTIVE");
        when(projectMapper.selectByIdForUpdate(PROJECT_ID)).thenReturn(project);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        when(classGroupMapper.selectByProjectIdForUpdate(PROJECT_ID)).thenReturn(List.of());
        when(classGroupMapper.selectList(any())).thenReturn(List.of());
        when(classHolidayMapper.selectCount(any())).thenReturn(0L);
        when(classHolidayMapper.selectList(any())).thenReturn(List.of());

        ClassHolidaySaveRequest request = new ClassHolidaySaveRequest();
        request.setHolidayName("Winter break");
        request.setStartDate(LocalDate.of(2035, 12, 24));
        request.setEndDate(LocalDate.of(2035, 12, 31));

        service.createHoliday(PROJECT_ID, request);

        var lockOrder = inOrder(projectMapper, classGroupMapper);
        lockOrder.verify(projectMapper).selectByIdForUpdate(PROJECT_ID);
        lockOrder.verify(classGroupMapper).selectByProjectIdForUpdate(PROJECT_ID);
    }

    @Test
    void recalculatesPurchasedSessionCoverageAcrossMultiplePeriods() {
        ClassGroupEntity group = sessionClass(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 28));
        ClassEnrollmentEntity enrollment = sessionEnrollment(4);
        ClassGroupScheduleSlotEntity firstPeriod = mondaySlot(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12));
        ClassGroupScheduleSlotEntity secondPeriod = weekdaySlot(3, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 28));
        when(classGroupMapper.selectById(501L)).thenReturn(group);
        when(classGroupScheduleSlotMapper.selectList(any())).thenReturn(List.of(firstPeriod, secondPeriod));
        when(classHolidayMapper.selectList(any())).thenReturn(List.of());
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));

        service.recalculateClassSessionEnrollments(501L);

        assertThat(enrollment.getExpiryDate()).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(enrollment.getStatus()).isEqualTo("ACTIVE");
        verify(classEnrollmentMapper).updateById(enrollment);
    }

    @Test
    void explainsHowToFixPeriodsThatCannotCoverPurchasedSessions() {
        ClassGroupEntity group = sessionClass(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 26));
        ClassEnrollmentEntity enrollment = sessionEnrollment(4);
        ClassGroupScheduleSlotEntity onlyPeriod = mondaySlot(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12));
        when(classGroupMapper.selectById(501L)).thenReturn(group);
        when(classGroupScheduleSlotMapper.selectList(any())).thenReturn(List.of(onlyPeriod));
        when(classHolidayMapper.selectList(any())).thenReturn(List.of());
        when(classEnrollmentMapper.selectList(any())).thenReturn(List.of(enrollment));

        assertThatThrownBy(() -> service.recalculateClassSessionEnrollments(501L))
                .isInstanceOfSatisfying(com.classdeduction.system.common.exception.BusinessException.class,
                        error -> assertThat(error.getMessage())
                                .isEqualTo("The current periods do not provide enough class dates for some students’ "
                                        + "purchased sessions. Add weekly class times or extend the class end date, then preview again."));
    }

    private ClassGroupEntity sessionClass(LocalDate startDate, LocalDate endDate) {
        ClassGroupEntity group = new ClassGroupEntity();
        group.setId(501L);
        group.setGroupProjectId(PROJECT_ID);
        group.setStartDate(startDate);
        group.setEndDate(endDate);
        group.setScheduleBaseEndDate(endDate);
        return group;
    }

    private ClassEnrollmentEntity sessionEnrollment(int purchasedQuantity) {
        ClassEnrollmentEntity enrollment = new ClassEnrollmentEntity();
        enrollment.setId(601L);
        enrollment.setClassId(501L);
        enrollment.setIsDeleted(0);
        enrollment.setBillingType("PER_SESSION");
        enrollment.setStatus("ACTIVE");
        enrollment.setEnrollmentDate(LocalDate.of(2026, 10, 5));
        enrollment.setExpiryDate(LocalDate.of(2026, 10, 26));
        enrollment.setPurchasedQuantity(purchasedQuantity);
        return enrollment;
    }

    private ClassGroupScheduleSlotEntity mondaySlot(LocalDate startDate, LocalDate endDate) {
        return weekdaySlot(1, startDate, endDate);
    }

    private ClassGroupScheduleSlotEntity weekdaySlot(int weekday, LocalDate startDate, LocalDate endDate) {
        ClassGroupScheduleSlotEntity slot = new ClassGroupScheduleSlotEntity();
        slot.setWeekday(weekday);
        slot.setEffectiveStartDate(startDate);
        slot.setEffectiveEndDate(endDate);
        return slot;
    }
}
