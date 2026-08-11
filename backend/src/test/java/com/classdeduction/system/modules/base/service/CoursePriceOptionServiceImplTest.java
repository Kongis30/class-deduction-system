package com.classdeduction.system.modules.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.dto.CoursePriceOptionSaveRequest;
import com.classdeduction.system.modules.base.dto.CoursePriceOptionPageQuery;
import com.classdeduction.system.modules.base.entity.CoursePriceOptionEntity;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.mapper.CoursePriceOptionMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
                lessonRecordMapper
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
    void shouldRejectGroupOptionWithoutScheduleNeededForMonthlyConversion() {
        CoursePriceOptionSaveRequest request = groupOption();
        request.setGroupScheduleSlots(List.of());
        when(courseProductMapper.selectById(301L)).thenReturn(groupProduct());

        assertThatThrownBy(() -> coursePriceOptionService.createOption(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Grade classes require at least one weekly time slot to calculate monthly price.");
    }

    @Test
    void shouldCreateGroupOptionWithFutureClassOccurrencesWithoutRecurringSlotReference() {
        CoursePriceOptionSaveRequest request = groupOption();
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
        ArgumentCaptor<LessonOccurrenceEntity> occurrenceCaptor =
                ArgumentCaptor.forClass(LessonOccurrenceEntity.class);
        verify(coursePriceOptionMapper).insert(optionCaptor.capture());
        verify(lessonOccurrenceMapper, times(13)).insert(occurrenceCaptor.capture());
        assertThat(optionCaptor.getValue().getGroupTotalPrice()).isEqualByComparingTo("1300.00");
        assertThat(optionCaptor.getValue().getGroupSessionPrice()).isEqualByComparingTo("100.00");
        assertThat(optionCaptor.getValue().getPriceAmount()).isEqualByComparingTo("433.33");
        assertThat(occurrenceCaptor.getAllValues())
                .allSatisfy(occurrence -> {
                    assertThat(occurrence.getClassGroupId()).isEqualTo(401L);
                    assertThat(occurrence.getScheduleSlotId()).isNull();
                    assertThat(occurrence.getPlannedTeacherId()).isEqualTo(702L);
                    assertThat(occurrence.getPlannedClassroomId()).isEqualTo(801L);
                    assertThat(occurrence.getPlanningStatus()).isEqualTo("SCHEDULED");
                });
    }

    @Test
    void shouldRejectOneOnOneOptionWithoutHours() {
        CoursePriceOptionSaveRequest request = oneOnOneOption();
        request.setOneOnOneHours(null);
        when(courseProductMapper.selectById(201L)).thenReturn(oneOnOneProduct());

        assertThatThrownBy(() -> coursePriceOptionService.createOption(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("One-on-one price option requires purchased hours.");
    }

    @Test
    void shouldRejectOneOnOneHoursOutsideHalfHourIncrements() {
        CoursePriceOptionSaveRequest request = oneOnOneOption();
        request.setOneOnOneHours(new BigDecimal("40.25"));
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
    void shouldAllowActiveZeroPriceTemplateForManualRegistrationPricing() {
        CoursePriceOptionSaveRequest request = oneOnOneOption();
        request.setPriceAmount(BigDecimal.ZERO);
        when(courseProductMapper.selectById(201L)).thenReturn(oneOnOneProduct());
        when(coursePriceOptionMapper.selectCount(any())).thenReturn(0L);

        coursePriceOptionService.createOption(request);
    }

    @Test
    void shouldReturnReferenceStatusForUsedPriceRows() {
        CoursePriceOptionPageQuery query = new CoursePriceOptionPageQuery();
        Page<CoursePriceOptionEntity> mapperPage = new Page<>(1, 10);
        mapperPage.setRecords(List.of(oneOnOneEntity()));
        mapperPage.setTotal(1);
        when(coursePriceOptionMapper.selectPage(any(), any())).thenReturn(mapperPage);
        when(enrollmentOrderItemMapper.selectCount(any())).thenReturn(2L);

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
        request.setGroupTotalSessions(13);
        request.setGroupTotalPrice(new BigDecimal("1300.00"));
        request.setGroupStartMonth("2026-09");
        request.setGroupEndMonth("2026-11");
        request.setGroupTeacherId(701L);
        request.setGroupClassroomId(801L);
        CoursePriceOptionSaveRequest.GroupScheduleSlotDTO slot =
                new CoursePriceOptionSaveRequest.GroupScheduleSlotDTO();
        slot.setTeacherId(702L);
        slot.setWeekday(6);
        slot.setStartTime("09:00");
        slot.setEndTime("12:00");
        request.setGroupScheduleSlots(List.of(slot));
        request.setStatus("ACTIVE");
        return request;
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
