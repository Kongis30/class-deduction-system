package com.classdeduction.system.modules.base.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.dto.ProductGradeConfigSaveRequest;
import com.classdeduction.system.modules.base.entity.CoursePriceOptionEntity;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.ProductGradeConfigEntity;
import com.classdeduction.system.modules.base.mapper.CoursePriceOptionMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.ProductGradeConfigMapper;
import com.classdeduction.system.modules.base.service.impl.ProductGradeConfigServiceImpl;
import com.classdeduction.system.modules.base.vo.ProductGradeConfigVO;
import com.classdeduction.system.modules.finance.entity.TeacherPayRateEntity;
import com.classdeduction.system.modules.finance.mapper.TeacherPayRateMapper;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassHolidayEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassHolidayMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductGradeConfigServiceImplTest {

    private static final Long PRODUCT_ID = 100L;

    @Mock
    private ProductGradeConfigMapper productGradeConfigMapper;

    @Mock
    private CourseProductMapper courseProductMapper;

    @Mock
    private CoursePriceOptionMapper coursePriceOptionMapper;

    @Mock
    private ClassGroupMapper classGroupMapper;

    @Mock
    private ClassHolidayMapper classHolidayMapper;

    @Mock
    private TeacherPayRateMapper teacherPayRateMapper;

    private ProductGradeConfigServiceImpl service;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CoursePriceOptionEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ClassGroupEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ClassHolidayEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), TeacherPayRateEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ProductGradeConfigEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = new ProductGradeConfigServiceImpl(
                productGradeConfigMapper,
                courseProductMapper,
                coursePriceOptionMapper,
                classGroupMapper,
                classHolidayMapper,
                teacherPayRateMapper
        );
    }

    private void mockProductExists() {
        when(courseProductMapper.selectById(PRODUCT_ID)).thenReturn(new CourseProductEntity());
    }

    private CoursePriceOptionEntity priceOption(String gradeBand) {
        CoursePriceOptionEntity entity = new CoursePriceOptionEntity();
        entity.setCourseProductId(PRODUCT_ID);
        entity.setGradeBand(gradeBand);
        return entity;
    }

    private ProductGradeConfigEntity config(String gradeLabel, int order) {
        ProductGradeConfigEntity entity = new ProductGradeConfigEntity();
        entity.setId(900L);
        entity.setCourseProductId(PRODUCT_ID);
        entity.setGradeLabel(gradeLabel);
        entity.setDisplayOrder(order);
        return entity;
    }

    @Test
    void listMergesConfiguredAndDiscoveredGradesAndSorts() {
        mockProductExists();
        // 配置表已有 G6-G8(order 1)、G1-G5(order 2)；价格行另有一个未配置的 ESL。
        when(productGradeConfigMapper.selectList(any())).thenReturn(List.of(
                config("G6-G8", 1),
                config("G1-G5", 2)
        ));
        when(coursePriceOptionMapper.selectList(any())).thenReturn(List.of(
                priceOption("G1-G5"),
                priceOption("ESL"),
                priceOption("G6-G8")
        ));

        List<ProductGradeConfigVO> result = service.listGradeConfigs(PRODUCT_ID);

        assertThat(result).extracting(ProductGradeConfigVO::gradeLabel)
                .containsExactly("G6-G8", "G1-G5", "ESL");
        assertThat(result.get(2).id()).isNull();
        assertThat(result.get(2).displayOrder()).isEqualTo(99);
    }

    @Test
    void saveCascadesRenameToPriceOptionsClassGroupsHolidaysAndPayRates() {
        mockProductExists();
        when(productGradeConfigMapper.selectList(any())).thenReturn(List.of(config("G3-G6", 99)));
        when(coursePriceOptionMapper.selectList(any())).thenReturn(List.of(priceOption("G3-G6")));

        service.saveGradeConfigs(PRODUCT_ID, request(item("G3-G6", "G3-6", 1)));

        // 三张业务表都按旧名匹配、改成新名。
        verify(coursePriceOptionMapper).update(isNull(), argThat(wrapper -> wrapperSqlContains(wrapper, "G3-6")));
        verify(classGroupMapper).update(isNull(), argThat(wrapper -> wrapperSqlContains(wrapper, "G3-6")));
        verify(classHolidayMapper).update(isNull(), argThat(wrapper -> wrapperSqlContains(wrapper, "G3-6")));
        verify(teacherPayRateMapper).update(isNull(), argThat(wrapper -> wrapperSqlContains(wrapper, "G3-6")));
        // 配置表旧行软删、新行插入。
        verify(productGradeConfigMapper).deleteById(900L);
        ArgumentCaptor<ProductGradeConfigEntity> captor = ArgumentCaptor.forClass(ProductGradeConfigEntity.class);
        verify(productGradeConfigMapper).insert(captor.capture());
        assertThat(captor.getValue().getGradeLabel()).isEqualTo("G3-6");
        assertThat(captor.getValue().getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void saveWithoutRenameSkipsCascadeUpdates() {
        mockProductExists();
        when(productGradeConfigMapper.selectList(any())).thenReturn(List.of(config("G3-G6", 99)));
        when(coursePriceOptionMapper.selectList(any())).thenReturn(List.of(priceOption("G3-G6")));

        service.saveGradeConfigs(PRODUCT_ID, request(item("G3-G6", null, 1)));

        verify(coursePriceOptionMapper, never()).update(any(), any());
        verify(classGroupMapper, never()).update(any(), any());
        verify(classHolidayMapper, never()).update(any(), any());
        verify(teacherPayRateMapper, never()).update(any(), any());
        verify(productGradeConfigMapper).insert(any(ProductGradeConfigEntity.class));
    }

    @Test
    void saveRejectsRenameToExistingGrade() {
        mockProductExists();
        when(productGradeConfigMapper.selectList(any())).thenReturn(List.of());
        when(coursePriceOptionMapper.selectList(any())).thenReturn(List.of(
                priceOption("G1-G5"),
                priceOption("ESL")
        ));

        assertThatThrownBy(() -> service.saveGradeConfigs(PRODUCT_ID, request(
                item("G1-G5", "ESL", 1)
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");

        verify(coursePriceOptionMapper, never()).update(any(), any());
    }

    @Test
    void saveRejectsDuplicateFinalLabelsInsideRequest() {
        mockProductExists();
        when(productGradeConfigMapper.selectList(any())).thenReturn(List.of());
        when(coursePriceOptionMapper.selectList(any())).thenReturn(List.of(
                priceOption("G1-G5"),
                priceOption("ESL")
        ));

        // 两个不同 grade 都改成同一个新名 → 拒绝。
        assertThatThrownBy(() -> service.saveGradeConfigs(PRODUCT_ID, request(
                item("G1-G5", "G1", 1),
                item("ESL", "g1", 2)
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void saveRejectsUnknownCurrentLabel() {
        mockProductExists();
        when(productGradeConfigMapper.selectList(any())).thenReturn(List.of());
        when(coursePriceOptionMapper.selectList(any())).thenReturn(List.of(priceOption("G1-G5")));

        assertThatThrownBy(() -> service.saveGradeConfigs(PRODUCT_ID, request(
                item("G9-G12", "G9", 1)
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void saveRejectsBlankNewLabel() {
        mockProductExists();
        // blank 校验发生在读取配置/价格行之前，无需 stub 数据查询。

        assertThatThrownBy(() -> service.saveGradeConfigs(PRODUCT_ID, request(
                item("G1-G5", "   ", 1)
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void saveRebuildsConfigRowsWithTombstone() {
        mockProductExists();
        when(productGradeConfigMapper.selectList(any())).thenReturn(List.of(config("G1-G5", 3)));
        when(coursePriceOptionMapper.selectList(any())).thenReturn(List.of(priceOption("G1-G5")));

        service.saveGradeConfigs(PRODUCT_ID, request(item("G1-G5", null, 7)));

        // 软删前先把旧行 label 改写为墓碑名，避免软删行撞唯一键。
        ArgumentCaptor<ProductGradeConfigEntity> patchCaptor = ArgumentCaptor.forClass(ProductGradeConfigEntity.class);
        verify(productGradeConfigMapper).updateById(patchCaptor.capture());
        assertThat(patchCaptor.getValue().getGradeLabel()).isEqualTo("G1-G5_DEL_900");
        assertThat(patchCaptor.getValue().getId()).isEqualTo(900L);
    }

    @Test
    void listRejectsMissingProduct() {
        when(courseProductMapper.selectById(PRODUCT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.listGradeConfigs(PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");
    }

    private static ProductGradeConfigSaveRequest request(ProductGradeConfigSaveRequest.Item... items) {
        ProductGradeConfigSaveRequest request = new ProductGradeConfigSaveRequest();
        request.setItems(List.of(items));
        return request;
    }

    private static ProductGradeConfigSaveRequest.Item item(String currentLabel, String newLabel, Integer order) {
        ProductGradeConfigSaveRequest.Item item = new ProductGradeConfigSaveRequest.Item();
        item.setCurrentLabel(currentLabel);
        item.setNewLabel(newLabel);
        item.setDisplayOrder(order);
        return item;
    }

    private static boolean wrapperSqlContains(Wrapper<?> wrapper, String keyword) {
        return ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().values().stream()
                .anyMatch(value -> String.valueOf(value).contains(keyword));
    }
}
