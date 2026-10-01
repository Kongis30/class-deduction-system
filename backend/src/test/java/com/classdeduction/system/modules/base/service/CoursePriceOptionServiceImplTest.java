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
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
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
import static org.mockito.Mockito.lenient;
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
    private TeacherMapper teacherMapper;

    @Mock
    private ClassroomMapper classroomMapper;

    @Mock
    private CampusMapper campusMapper;

    private CoursePriceOptionServiceImpl coursePriceOptionService;

    @BeforeEach
    void setUp() {
        lenient().when(courseProductMapper.selectByIdForUpdate(any()))
                .thenAnswer(invocation -> courseProductMapper.selectById(invocation.getArgument(0)));
        coursePriceOptionService = new CoursePriceOptionServiceImpl(
                coursePriceOptionMapper,
                courseProductMapper,
                enrollmentOrderItemMapper,
                classGroupMapper,
                classGroupScheduleSlotMapper,
                teacherMapper,
                classroomMapper,
                campusMapper
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
        request.setCourseType("GROUP");
        when(courseProductMapper.selectById(201L)).thenReturn(oneOnOneProduct());

        assertThatThrownBy(() -> coursePriceOptionService.createOption(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Course price option teaching mode does not match the linked course product.");
    }

    @Test
    void shouldKeepLegacyGroupPriceOptionsReadOnly() {
        CoursePriceOptionSaveRequest request = new CoursePriceOptionSaveRequest();
        request.setCourseProductId(301L);
        when(courseProductMapper.selectById(301L)).thenReturn(groupProduct());

        assertThatThrownBy(() -> coursePriceOptionService.createOption(request))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("GROUP_LEGACY_PRICE_READ_ONLY");

        verify(coursePriceOptionMapper, org.mockito.Mockito.never()).insert(any(CoursePriceOptionEntity.class));
        verify(classGroupMapper, org.mockito.Mockito.never()).insert(any(ClassGroupEntity.class));
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
