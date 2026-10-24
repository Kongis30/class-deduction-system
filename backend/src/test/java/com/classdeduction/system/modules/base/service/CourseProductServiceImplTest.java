package com.classdeduction.system.modules.base.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.dto.CourseProductSaveRequest;
import com.classdeduction.system.modules.base.entity.CoursePriceOptionEntity;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.GroupCourseProjectEntity;
import com.classdeduction.system.modules.base.entity.GroupRegistrationPlanEntity;
import com.classdeduction.system.modules.base.mapper.GroupCourseProjectMapper;
import com.classdeduction.system.modules.base.mapper.GroupRegistrationPlanMapper;
import com.classdeduction.system.modules.base.mapper.CoursePriceOptionMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.service.impl.CourseProductServiceImpl;
import com.classdeduction.system.modules.base.vo.CourseProductVO;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentCorrectionLogMapper;
import com.classdeduction.system.modules.enrollment.service.CoursePackageService;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassHolidayMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.lesson.service.LessonRecordService;
import com.classdeduction.system.modules.schedule.service.ClassGroupService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseProductServiceImplTest {

    @Mock
    private CourseProductMapper courseProductMapper;
    @Mock
    private CoursePackageMapper coursePackageMapper;
    @Mock
    private EnrollmentOrderItemMapper enrollmentOrderItemMapper;
    @Mock
    private ClassGroupMapper classGroupMapper;
    @Mock
    private ClassGroupService classGroupService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private CoursePriceOptionMapper coursePriceOptionMapper;
    @Mock
    private ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;
    @Mock
    private GroupCourseProjectMapper groupCourseProjectMapper;
    @Mock
    private GroupRegistrationPlanMapper groupRegistrationPlanMapper;
    @Mock
    private ClassHolidayMapper classHolidayMapper;
    @Mock
    private LessonOccurrenceMapper lessonOccurrenceMapper;
    @Mock
    private LessonRecordMapper lessonRecordMapper;
    @Mock
    private LessonRecordService lessonRecordService;
    @Mock
    private RecurringScheduleRuleMapper recurringScheduleRuleMapper;
    @Mock
    private CoursePackageService coursePackageService;
    @Mock
    private EnrollmentCorrectionLogMapper operationLogMapper;

    private CourseProductServiceImpl courseProductService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        // 单元测试不启动 MyBatis 上下文，手动初始化 LambdaQueryWrapper 字段缓存。
        Configuration configuration = new Configuration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CourseProductEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CoursePriceOptionEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), GroupCourseProjectEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), GroupRegistrationPlanEntity.class);
    }

    @BeforeEach
    void setUp() {
        lenient().when(courseProductMapper.selectByIdForUpdate(any()))
                .thenAnswer(invocation -> courseProductMapper.selectById(invocation.getArgument(0)));
        courseProductService = new CourseProductServiceImpl(
                courseProductMapper,
                coursePackageMapper,
                enrollmentOrderItemMapper,
                classGroupMapper,
                classGroupService,
                transactionManager,
                coursePriceOptionMapper,
                classGroupScheduleSlotMapper,
                groupCourseProjectMapper,
                groupRegistrationPlanMapper,
                classHolidayMapper,
                lessonOccurrenceMapper,
                lessonRecordMapper,
                lessonRecordService,
                recurringScheduleRuleMapper,
                coursePackageService,
                operationLogMapper
        );
    }

    @Test
    void shouldCreateGroupProductWithoutOriginalPriceOrDefaultResources() {
        CourseProductSaveRequest request = buildGroupRequest();
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        doAnswer(invocation -> {
            CourseProductEntity product = invocation.getArgument(0);
            product.setId(201L);
            return 1;
        }).when(courseProductMapper).insert(any(CourseProductEntity.class));

        courseProductService.createCourseProduct(request);

        verify(courseProductMapper).insert(argThat((CourseProductEntity product) ->
                product.getListPrice() == null
                        && product.getDefaultTeacherId() == null
                        && product.getDefaultClassroomId() == null
                        && product.getBillingType() == null
                        && product.getStartDate() == null
                        && product.getTotalSessions() == null));
        verify(classGroupMapper, never()).insert(any(ClassGroupEntity.class));
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void shouldListAllProductsInDisplayOrderWithoutReferenceCounting() {
        CourseProductEntity first = product(1L, "A", 1);
        CourseProductEntity second = product(2L, "B", 2);
        when(courseProductMapper.selectList(any())).thenReturn(List.of(first, second));

        List<CourseProductVO> result = courseProductService.listCourseProducts();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).productName()).isEqualTo("A");
        assertThat(result.get(0).sortOrder()).isEqualTo(1);
        // Tab 列表不做逐条引用计数，避免 N+1 查询。
        verify(coursePackageMapper, never()).selectCount(any());
        verify(classGroupMapper, never()).selectCount(any());
    }

    @Test
    void shouldPersistContiguousOrderAndAppendOmittedProductsWhenReordering() {
        // 全部产品保持默认 sortOrder=99，验证重排后写入连续序号。
        CourseProductEntity first = product(1L, "A", 99);
        CourseProductEntity second = product(2L, "B", 99);
        CourseProductEntity third = product(3L, "C", 99);
        when(courseProductMapper.selectList(any())).thenReturn(List.of(first, second, third));

        // 客户端只提交了 2、1；产品 3 是并发新增后被遗漏的，应按现有顺序追加到末尾。
        courseProductService.reorderCourseProducts(List.of(2L, 1L), 100L);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<CourseProductEntity> captor = ArgumentCaptor.forClass((Class) CourseProductEntity.class);
        verify(courseProductMapper, times(3)).updateById(captor.capture());
        List<CourseProductEntity> updated = captor.getAllValues();
        assertThat(updated.stream().map(CourseProductEntity::getId))
                .containsExactly(2L, 1L, 3L);
        assertThat(updated.stream().map(CourseProductEntity::getSortOrder))
                .containsExactly(1, 2, 3);
        assertThat(updated).allMatch(product -> Long.valueOf(100L).equals(product.getUpdatedBy()));
    }

    @Test
    void shouldSkipUnchangedSortOrderWhenReordering() {
        CourseProductEntity first = product(1L, "A", 1);
        CourseProductEntity second = product(2L, "B", 2);
        when(courseProductMapper.selectList(any())).thenReturn(List.of(first, second));

        courseProductService.reorderCourseProducts(List.of(1L, 2L), 100L);

        // 顺序未变化时不应产生更新写放大。
        verify(courseProductMapper, never()).updateById(any(CourseProductEntity.class));
    }

    @Test
    void shouldRejectDuplicateOrUnknownProductIdsWhenReordering() {
        CourseProductEntity first = product(1L, "A", 1);
        when(courseProductMapper.selectList(any())).thenReturn(List.of(first));

        assertThatThrownBy(() -> courseProductService.reorderCourseProducts(List.of(1L, 1L), 100L))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> courseProductService.reorderCourseProducts(List.of(999L), 100L))
                .isInstanceOf(BusinessException.class);
        verify(courseProductMapper, never()).updateById(any(CourseProductEntity.class));
    }

    @Test
    void shouldRejectEmptyOrderWhileProductsExist() {
        CourseProductEntity first = product(1L, "A", 1);
        when(courseProductMapper.selectList(any())).thenReturn(List.of(first));

        assertThatThrownBy(() -> courseProductService.reorderCourseProducts(List.of(), 100L))
                .isInstanceOf(BusinessException.class);
        verify(courseProductMapper, never()).updateById(any(CourseProductEntity.class));
    }

    @Test
    void shouldPreventDeletingGroupProductThatStillOwnsTeachingProjects() {
        when(courseProductMapper.selectById(4L)).thenReturn(product(4L, "Saturday class", 1));
        when(groupCourseProjectMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> courseProductService.deleteCourseProduct(4L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("teaching-project setup");

        verify(courseProductMapper, never()).deleteById(4L);
    }

    @Test
    void updatingGroupProductDoesNotMutateRetiredPriceOptionRows() {
        CourseProductEntity existing = product(1L, "Weekly Class", 5);
        existing.setCourseType("AFTER_SCHOOL");
        when(courseProductMapper.selectById(1L)).thenReturn(existing);

        CourseProductSaveRequest request = buildGroupRequest();
        request.setCourseType("AFTER_SCHOOL");

        courseProductService.updateCourseProduct(1L, request);

        verify(courseProductMapper).updateById(any(CourseProductEntity.class));
        verify(coursePriceOptionMapper, never()).update(any(), any());
    }

    @Test
    void shouldRejectChangingGroupProductModeAfterProjectSetupExists() {
        CourseProductEntity existing = product(4L, "Saturday class", 1);
        when(courseProductMapper.selectById(4L)).thenReturn(existing);
        when(groupCourseProjectMapper.selectCount(any())).thenReturn(1L);
        CourseProductSaveRequest request = buildGroupRequest();
        request.setTeachingMode("ONE_ON_ONE");

        assertThatThrownBy(() -> courseProductService.updateCourseProduct(4L, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo("PRODUCT_TEACHING_MODE_IN_USE"));

        verify(courseProductMapper, never()).updateById(any(CourseProductEntity.class));
        verify(coursePriceOptionMapper, never()).update(isNull(), any());
    }

    @Test
    void shouldRejectChangingPrivateProductModeAfterPackageReferenceExists() {
        CourseProductEntity existing = product(5L, "Private Math", 1);
        existing.setTeachingMode("ONE_ON_ONE");
        when(courseProductMapper.selectById(5L)).thenReturn(existing);
        when(coursePackageMapper.selectCount(any())).thenReturn(1L);
        CourseProductSaveRequest request = buildGroupRequest();
        request.setTeachingMode("GROUP");

        assertThatThrownBy(() -> courseProductService.updateCourseProduct(5L, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo("PRODUCT_TEACHING_MODE_IN_USE"));

        verify(courseProductMapper, never()).updateById(any(CourseProductEntity.class));
        verify(coursePriceOptionMapper, never()).update(isNull(), any());
    }

    @Test
    void shouldRequireReviewWhenArchiveImpactChanged() {
        CourseProductEntity product = product(8L, "Current course", 1);
        when(courseProductMapper.selectByIdForUpdate(8L)).thenReturn(product);
        when(courseProductMapper.selectArchivePreview(8L)).thenReturn(Map.of(
                "classCount", 1L, "activeEnrollmentCount", 2L, "activePackageCount", 0L,
                "recurringRuleCount", 0L, "unfinishedLessonCount", 0L, "estimatedDeductedHours", 0.0));

        assertThatThrownBy(() -> courseProductService.archiveCourseProduct(8L, 100L, "stale"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo("COURSE_PRODUCT_ARCHIVE_PREVIEW_CHANGED"));

        verify(courseProductMapper, never()).updateById(any(CourseProductEntity.class));
    }

    @Test
    void shouldArchiveProductAsInactiveAfterBlockersAreCleared() {
        CourseProductEntity product = product(9L, "Finished course", 1);
        when(courseProductMapper.selectByIdForUpdate(9L)).thenReturn(product);
        when(courseProductMapper.selectArchivePreview(9L)).thenReturn(Map.of(
                "classCount", 0L, "activeEnrollmentCount", 0L, "activePackageCount", 0L,
                "recurringRuleCount", 0L, "unfinishedLessonCount", 0L, "estimatedDeductedHours", 0.0));
        when(classGroupMapper.selectList(any())).thenReturn(List.of());
        when(coursePackageMapper.selectList(any())).thenReturn(List.of());
        when(coursePriceOptionMapper.selectList(any())).thenReturn(List.of());
        when(groupCourseProjectMapper.selectList(any())).thenReturn(List.of());

        String fingerprint = courseProductService.previewCourseProductArchive(9L).fingerprint();
        courseProductService.archiveCourseProduct(9L, 100L, fingerprint);

        verify(courseProductMapper).updateById(argThat((CourseProductEntity updated) ->
                "INACTIVE".equals(updated.getStatus())
                        && updated.getArchivedAt() != null
                        && Long.valueOf(100L).equals(updated.getArchivedBy())
                        && Long.valueOf(100L).equals(updated.getUpdatedBy())));
    }

    @Test
    void shouldRestoreArchivedProductAndKeepItInactive() {
        CourseProductEntity product = product(10L, "Archived course", 1);
        product.setStatus("INACTIVE");
        product.setArchivedAt(java.time.LocalDateTime.parse("2026-10-01T10:00:00"));
        product.setArchivedBy(99L);
        when(courseProductMapper.selectByIdForUpdate(10L)).thenReturn(product);

        courseProductService.restoreCourseProduct(10L, 100L);

        verify(courseProductMapper).restoreArchivedProduct(eq(10L), eq(100L), any());
        verify(courseProductMapper, never()).updateById(any(CourseProductEntity.class));
    }

    @Test
    void shouldRejectReactivatingProductUntilItIsRestored() {
        CourseProductEntity product = product(11L, "Archived course", 1);
        product.setArchivedAt(java.time.LocalDateTime.parse("2026-10-01T10:00:00"));
        when(courseProductMapper.selectByIdForUpdate(11L)).thenReturn(product);
        CourseProductSaveRequest request = buildGroupRequest();
        request.setStatus("ACTIVE");

        assertThatThrownBy(() -> courseProductService.updateCourseProduct(11L, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo("ARCHIVED_PRODUCT_CANNOT_ACTIVATE"));

        verify(courseProductMapper, never()).updateById(any(CourseProductEntity.class));
    }

    private CourseProductEntity product(Long id, String name, int sortOrder) {
        CourseProductEntity entity = new CourseProductEntity();
        entity.setId(id);
        entity.setProductCode("PRD-" + id);
        entity.setProductName(name);
        entity.setTeachingMode("GROUP");
        entity.setSortOrder(sortOrder);
        entity.setStatus("ACTIVE");
        return entity;
    }

    private CourseProductSaveRequest buildGroupRequest() {
        CourseProductSaveRequest request = new CourseProductSaveRequest();
        request.setProductCode("PRD-0201");
        request.setProductName("Group Math");
        request.setSubjectName("Math");
        request.setTeachingMode("GROUP");
        request.setStatus("ACTIVE");
        return request;
    }

}
