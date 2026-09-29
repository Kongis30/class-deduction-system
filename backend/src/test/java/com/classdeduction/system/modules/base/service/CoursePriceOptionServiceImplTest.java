package com.classdeduction.system.modules.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.dto.CoursePriceOptionSaveRequest;
import com.classdeduction.system.modules.base.dto.CoursePriceOptionPageQuery;
import com.classdeduction.system.modules.base.entity.CoursePriceOptionEntity;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.CampusMapper;
import com.classdeduction.system.modules.base.mapper.CoursePriceOptionMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.base.service.impl.CoursePriceOptionServiceImpl;
import com.classdeduction.system.modules.base.vo.CoursePriceOptionVO;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoursePriceOptionServiceImplTest {

    @Mock
    private CoursePriceOptionMapper coursePriceOptionMapper;

    @Mock
    private CourseProductMapper courseProductMapper;

    @Mock
    private EnrollmentOrderItemMapper enrollmentOrderItemMapper;

    @Mock
    private ClassGroupMapper classGroupMapper;

    @Mock
    private ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;

    @Mock
    private LessonOccurrenceMapper lessonOccurrenceMapper;

    @Mock
    private LessonRecordMapper lessonRecordMapper;

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private ClassroomMapper classroomMapper;

    @Mock
    private CampusMapper campusMapper;

    @Mock
    private com.classdeduction.system.modules.schedule.service.ClassHolidayService classHolidayService;

    private CoursePriceOptionServiceImpl coursePriceOptionService;

    @BeforeEach
    void setUp() {
        coursePriceOptionService = new CoursePriceOptionServiceImpl(
                coursePriceOptionMapper,
                courseProductMapper,
                enrollmentOrderItemMapper,
                classGroupMapper,
                classGroupScheduleSlotMapper,
                lessonOccurrenceMapper,
                lessonRecordMapper,
                teacherMapper,
                classroomMapper,
                campusMapper,
                classHolidayService
        );
    }

    @Test
    void shouldRejectActiveOptionWithoutLinkedProduct() {
        CoursePriceOptionSaveRequest request = oneOnOneOption();
        request.setCourseProductId(null);

        assertThatThrownBy(() -> coursePriceOptionService.createOption(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Course price option must link to a course product.");
    }

    @Test
    void shouldRejectModeMismatchBetweenOptionAndProduct() {
        CoursePriceOptionSaveRequest request = oneOnOneOption();
        when(courseProductMapper.selectById(201L)).thenReturn(groupProduct());

        assertThatThrownBy(() -> coursePriceOptionService.createOption(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Course price option teaching mode does not match the linked course product.");
    }

    @Test
    void shouldCreateActualClassesFromBillingGradeWithoutSharedSchedule() {
        CoursePriceOptionSaveRequest request = groupOption();
        request.setGroupScheduleSlots(List.of());
        when(courseProductMapper.selectById(301L)).thenReturn(groupProduct());
        when(coursePriceOptionMapper.selectCount(any())).thenReturn(0L);
        when(classGroupMapper.selectOne(any())).thenReturn(null);

        coursePriceOptionService.createOption(request);

        ArgumentCaptor<ClassGroupEntity> classCaptor = ArgumentCaptor.forClass(ClassGroupEntity.class);
        verify(classGroupMapper, times(4)).insert(classCaptor.capture());
        assertThat(classCaptor.getAllValues())
                .extracting(ClassGroupEntity::getActualClass)
                .containsExactly("G3", "G4", "G5", "G6");
        assertThat(classCaptor.getAllValues())
                .extracting(ClassGroupEntity::getClassName)
                .containsExactly("G3", "G4", "G5", "G6");
    }

    @Test
    void shouldPreserveCustomActualClassDisplayNameWhenSavingGroupOption() {
        CoursePriceOptionSaveRequest request = groupOption();
        request.setActualClasses(List.of("G3"));
        request.setGroupScheduleSlots(List.of());
        ClassGroupEntity existingClass = new ClassGroupEntity();
        existingClass.setId(401L);
        existingClass.setProductId(301L);
        existingClass.setGradeBand("G3-G6");
        existingClass.setActualClass("G3");
        existingClass.setClassName("Advanced Math G3");

        when(courseProductMapper.selectById(301L)).thenReturn(groupProduct());
        when(coursePriceOptionMapper.selectCount(any())).thenReturn(0L);
        when(classGroupMapper.selectOne(any())).thenReturn(existingClass);

        coursePriceOptionService.createOption(request);

        ArgumentCaptor<ClassGroupEntity> classCaptor = ArgumentCaptor.forClass(ClassGroupEntity.class);
        verify(classGroupMapper).updateById(classCaptor.capture());
        assertThat(classCaptor.getValue().getClassName()).isEqualTo("Advanced Math G3");
    }

    @Test
    void shouldCreateGroupOptionWithFutureClassOccurrencesWithoutPayRateSnapshot() {
        CoursePriceOptionSaveRequest request = groupOption();
        java.time.YearMonth startMonth = java.time.YearMonth.parse(request.getGroupStartMonth());
        when(courseProductMapper.selectById(301L)).thenReturn(groupProduct());
        when(coursePriceOptionMapper.selectCount(any())).thenReturn(0L);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(), List.of());
        doAnswer(invocation -> {
            ClassGroupEntity classGroup = invocation.getArgument(0);
            classGroup.setId(401L);
            return 1;
        }).when(classGroupMapper).insert(any(ClassGroupEntity.class));
        doAnswer(invocation -> {
            ClassGroupScheduleSlotEntity slot = invocation.getArgument(0);
            slot.setId(501L);
            return 1;
        }).when(classGroupScheduleSlotMapper).insert(any(ClassGroupScheduleSlotEntity.class));

        coursePriceOptionService.createOption(request);

        ArgumentCaptor<CoursePriceOptionEntity> optionCaptor =
                ArgumentCaptor.forClass(CoursePriceOptionEntity.class);
        ArgumentCaptor<ClassGroupScheduleSlotEntity> slotCaptor =
                ArgumentCaptor.forClass(ClassGroupScheduleSlotEntity.class);
        ArgumentCaptor<LessonOccurrenceEntity> occurrenceCaptor =
                ArgumentCaptor.forClass(LessonOccurrenceEntity.class);
        verify(coursePriceOptionMapper).insert(optionCaptor.capture());
        verify(classGroupScheduleSlotMapper).insert(slotCaptor.capture());
        verify(lessonOccurrenceMapper, times(countSaturdays(startMonth, startMonth.plusMonths(2))))
                .insert(occurrenceCaptor.capture());
        assertThat(optionCaptor.getValue().getGroupTotalPrice()).isEqualByComparingTo("1300.00");
        assertThat(optionCaptor.getValue().getGroupSessionPrice()).isEqualByComparingTo("433.33");
        assertThat(optionCaptor.getValue().getPriceAmount()).isEqualByComparingTo("1300.00");
        assertThat(slotCaptor.getValue().getTeacherId()).isEqualTo(702L);
        assertThat(slotCaptor.getValue().getSubjectName()).isEqualTo("Chemistry");
        assertThat(occurrenceCaptor.getAllValues())
                .allSatisfy(occurrence -> {
                    assertThat(occurrence.getClassGroupId()).isEqualTo(401L);
                    assertThat(occurrence.getScheduleSlotId()).isEqualTo(501L);
                    assertThat(occurrence.getPlannedTeacherId()).isEqualTo(702L);
                    assertThat(occurrence.getPlannedClassroomId()).isEqualTo(801L);
                    assertThat(occurrence.getPlanningStatus()).isEqualTo("SCHEDULED");
                });
    }

    @Test
    void shouldTreatPerSessionGroupScheduleAsClassDaysWhenGeneratingOccurrences() {
        CoursePriceOptionSaveRequest request = groupOption();
        request.setGroupBillingType("PER_SESSION");
        request.setPackageUnit("SESSION");
        request.setPackageQuantity(new BigDecimal("2"));
        request.setGroupTotalSessions(2);
        request.setGroupStartMonth(null);
        request.setGroupEndMonth(null);
        java.time.LocalDate firstSessionDate = com.classdeduction.system.common.time.AppTime.nowLocalDateTime()
                .toLocalDate()
                .with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.SATURDAY));
        request.setGroupStartDate(firstSessionDate);
        request.setGroupEndDate(firstSessionDate.plusWeeks(4));
        CoursePriceOptionSaveRequest.GroupScheduleSlotDTO afternoon =
                new CoursePriceOptionSaveRequest.GroupScheduleSlotDTO();
        afternoon.setTeacherId(703L);
        afternoon.setSubjectName(" Chemistry ");
        afternoon.setWeekday(6);
        afternoon.setStartTime("13:00");
        afternoon.setEndTime("16:00");
        request.setGroupScheduleSlots(List.of(request.getGroupScheduleSlots().get(0), afternoon));

        when(courseProductMapper.selectById(301L)).thenReturn(groupProduct());
        when(coursePriceOptionMapper.selectCount(any())).thenReturn(0L);
        when(lessonOccurrenceMapper.selectList(any())).thenReturn(List.of(), List.of());
        doAnswer(invocation -> {
            ClassGroupEntity classGroup = invocation.getArgument(0);
            classGroup.setId(401L);
            return 1;
        }).when(classGroupMapper).insert(any(ClassGroupEntity.class));
        doAnswer(invocation -> {
            ClassGroupScheduleSlotEntity slot = invocation.getArgument(0);
            slot.setId(slot.getStartTime().getHour() < 12 ? 501L : 502L);
            return 1;
        }).when(classGroupScheduleSlotMapper).insert(any(ClassGroupScheduleSlotEntity.class));

        coursePriceOptionService.createOption(request);

        ArgumentCaptor<LessonOccurrenceEntity> occurrenceCaptor =
                ArgumentCaptor.forClass(LessonOccurrenceEntity.class);
        verify(lessonOccurrenceMapper, times(4)).insert(occurrenceCaptor.capture());
        assertThat(occurrenceCaptor.getAllValues())
                .extracting(occurrence -> occurrence.getPlannedStartAt().toLocalDate())
                .containsExactly(
                        firstSessionDate,
                        firstSessionDate,
                        firstSessionDate.plusWeeks(1),
                        firstSessionDate.plusWeeks(1));
    }

    @Test
    void shouldRejectOneOnOneOptionWithoutHours() {
        CoursePriceOptionSaveRequest request = oneOnOneOption();
        request.setOneOnOneHours(null);
        request.setPackageQuantity(null);
        when(courseProductMapper.selectById(201L)).thenReturn(oneOnOneProduct());

        assertThatThrownBy(() -> coursePriceOptionService.createOption(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Active course price option requires a package quantity greater than zero.");
    }

    @Test
    void shouldRejectOneOnOneHoursOutsideHalfHourIncrements() {
        CoursePriceOptionSaveRequest request = oneOnOneOption();
        request.setOneOnOneHours(new BigDecimal("40.25"));
        request.setPackageQuantity(new BigDecimal("40.25"));
        when(courseProductMapper.selectById(201L)).thenReturn(oneOnOneProduct());

        assertThatThrownBy(() -> coursePriceOptionService.createOption(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("One-on-one purchased hours must use 0.5-hour increments.");
    }

    @Test
    void shouldAcceptCompleteActiveOneOnOneMatrixRow() {
        CoursePriceOptionSaveRequest request = oneOnOneOption();
        when(courseProductMapper.selectById(201L)).thenReturn(oneOnOneProduct());
        when(coursePriceOptionMapper.selectCount(any())).thenReturn(0L);

        coursePriceOptionService.createOption(request);
    }

    @Test
    void shouldSaveBlankDetailTextAsEmptyString() {
        CoursePriceOptionSaveRequest request = oneOnOneOption();
        request.setDetailText(null);
        when(courseProductMapper.selectById(201L)).thenReturn(oneOnOneProduct());
        when(coursePriceOptionMapper.selectCount(any())).thenReturn(0L);

        coursePriceOptionService.createOption(request);

        ArgumentCaptor<CoursePriceOptionEntity> optionCaptor =
                ArgumentCaptor.forClass(CoursePriceOptionEntity.class);
        verify(coursePriceOptionMapper).insert(optionCaptor.capture());
        assertThat(optionCaptor.getValue().getDetailText()).isEqualTo("");
    }

    @Test
    void shouldAllowActiveZeroPriceTemplateForManualRegistrationPricing() {
        CoursePriceOptionSaveRequest request = oneOnOneOption();
        request.setPriceAmount(BigDecimal.ZERO);
        when(courseProductMapper.selectById(201L)).thenReturn(oneOnOneProduct());
        when(coursePriceOptionMapper.selectCount(any())).thenReturn(0L);

        coursePriceOptionService.createOption(request);
    }

    @Test
    void shouldTranslateDuplicateCodeFromSoftDeletedRowIntoBusinessError() {
        // The application-level uniqueness check only sees active rows, but the DB unique
        // key spans soft-deleted rows too. Simulate the insert hitting that constraint.
        CoursePriceOptionSaveRequest request = oneOnOneOption();
        when(courseProductMapper.selectById(201L)).thenReturn(oneOnOneProduct());
        when(coursePriceOptionMapper.selectCount(any())).thenReturn(0L);
        doThrow(new DuplicateKeyException("Duplicate entry for key 'uk_course_price_option_code'"))
                .when(coursePriceOptionMapper).insert(any(CoursePriceOptionEntity.class));

        assertThatThrownBy(() -> coursePriceOptionService.createOption(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Course price option code already exists");
    }

    @Test
    void shouldReleaseOptionCodeWhenSoftDeletingSoItCanBeReused() {
        when(coursePriceOptionMapper.selectById(9001L)).thenReturn(oneOnOneEntity());
        when(enrollmentOrderItemMapper.countActiveReferences(any())).thenReturn(0L);

        coursePriceOptionService.deleteOption(9001L);

        // Before the soft delete, the code should be tombstoned via updateById so the DB
        // unique key no longer blocks recreating the same option code.
        ArgumentCaptor<CoursePriceOptionEntity> patchCaptor =
                ArgumentCaptor.forClass(CoursePriceOptionEntity.class);
        verify(coursePriceOptionMapper).updateById(patchCaptor.capture());
        assertThat(patchCaptor.getValue().getId()).isEqualTo(9001L);
        assertThat(patchCaptor.getValue().getOptionCode())
                .isEqualTo("ONE_ON_ONE_G6_G8_60_DEL_9001");
        verify(coursePriceOptionMapper).deleteById(9001L);
    }

    @Test
    void shouldReturnReferenceStatusForUsedPriceRows() {
        CoursePriceOptionPageQuery query = new CoursePriceOptionPageQuery();
        Page<CoursePriceOptionEntity> mapperPage = new Page<>(1, 10);
        mapperPage.setRecords(List.of(oneOnOneEntity()));
        mapperPage.setTotal(1);
        when(coursePriceOptionMapper.selectPage(any(), any())).thenReturn(mapperPage);
        when(enrollmentOrderItemMapper.countActiveReferences(any())).thenReturn(2L);

        PageResponse<CoursePriceOptionVO> result = coursePriceOptionService.pageOptions(query);

        CoursePriceOptionVO row = result.records().get(0);
        assertThat(row.referenced()).isTrue();
        assertThat(row.referenceCount()).isEqualTo(2L);
        assertThat(row.referenceSources()).containsExactly("Enrollment order items");
    }

    private CoursePriceOptionSaveRequest oneOnOneOption() {
        CoursePriceOptionSaveRequest request = new CoursePriceOptionSaveRequest();
        request.setCourseType("ONE_ON_ONE");
        request.setRegisterClass("1 on 1");
        request.setGradeBand("G6-G8");
        request.setDurationLabel("60 hours");
        request.setPeriodLabel("60 hours");
        request.setTimeSlotLabel("By schedule");
        request.setDetailText("1 on 1 G6-G8 60 hours");
        request.setPackageUnit("HOUR");
        request.setPackageQuantity(new BigDecimal("60.00"));
        request.setTotalPrice(new BigDecimal("3600.00"));
        request.setOneOnOneHours(new BigDecimal("60.00"));
        request.setPriceAmount(new BigDecimal("3600.00"));
        request.setCourseProductId(201L);
        request.setStatus("ACTIVE");
        return request;
    }

    private CoursePriceOptionEntity oneOnOneEntity() {
        CoursePriceOptionEntity entity = new CoursePriceOptionEntity();
        entity.setId(9001L);
        entity.setOptionCode("ONE_ON_ONE_G6_G8_60");
        entity.setCourseType("ONE_ON_ONE");
        entity.setRegisterClass("1 on 1");
        entity.setGradeBand("G6-G8");
        entity.setDurationLabel("60 hours");
        entity.setPeriodLabel("60 hours");
        entity.setTimeSlotLabel("By schedule");
        entity.setDetailText("1 on 1 G6-G8 60 hours");
        entity.setOneOnOneHours(new BigDecimal("60.00"));
        entity.setPriceAmount(new BigDecimal("3600.00"));
        entity.setCurrencyCode("USD");
        entity.setCourseProductId(201L);
        entity.setSortOrder(10);
        entity.setStatus("ACTIVE");
        return entity;
    }

    private CoursePriceOptionSaveRequest groupOption() {
        CoursePriceOptionSaveRequest request = new CoursePriceOptionSaveRequest();
        request.setCourseType("SATURDAY");
        request.setRegisterClass("Saturday Class");
        request.setGradeBand("G3-G6");
        request.setDurationLabel("Fall 13 weeks");
        request.setPeriodLabel("Fall");
        request.setTimeSlotLabel("Sat 9am-12pm");
        request.setDetailText("Saturday G3-G6 Fall half day");
        request.setPriceAmount(new BigDecimal("433.33"));
        request.setCourseProductId(301L);
        request.setGroupBillingType("MONTHLY");
        request.setPackageUnit("MONTH");
        request.setPackageQuantity(new BigDecimal("3"));
        request.setTotalPrice(new BigDecimal("1300.00"));
        request.setGroupTotalSessions(13);
        request.setGroupTotalPrice(new BigDecimal("1300.00"));
        java.time.YearMonth startMonth = java.time.YearMonth.now(
                com.classdeduction.system.common.time.AppTime.APP_ZONE_ID).plusMonths(1);
        request.setGroupStartMonth(startMonth.toString());
        request.setGroupEndMonth(startMonth.plusMonths(2).toString());
        request.setGroupTeacherId(701L);
        request.setGroupClassroomId(801L);
        CoursePriceOptionSaveRequest.GroupScheduleSlotDTO slot =
                new CoursePriceOptionSaveRequest.GroupScheduleSlotDTO();
        slot.setTeacherId(702L);
        slot.setSubjectName(" Chemistry ");
        slot.setWeekday(6);
        slot.setStartTime("09:00");
        slot.setEndTime("12:00");
        request.setGroupScheduleSlots(List.of(slot));
        request.setStatus("ACTIVE");
        return request;
    }

    private int countSaturdays(java.time.YearMonth startMonth, java.time.YearMonth endMonth) {
        java.time.LocalDate start = startMonth.atDay(1);
        java.time.LocalDate end = endMonth.atEndOfMonth();
        int count = 0;
        for (java.time.LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == java.time.DayOfWeek.SATURDAY) {
                count++;
            }
        }
        return count;
    }

    private CourseProductEntity oneOnOneProduct() {
        CourseProductEntity product = new CourseProductEntity();
        product.setId(201L);
        product.setCourseType("ONE_ON_ONE");
        product.setTeachingMode("ONE_ON_ONE");
        product.setStatus("ACTIVE");
        return product;
    }

    private CourseProductEntity groupProduct() {
        CourseProductEntity product = new CourseProductEntity();
        product.setId(301L);
        product.setCourseType("SATURDAY");
        product.setTeachingMode("GROUP");
        product.setStatus("ACTIVE");
        return product;
    }
}
