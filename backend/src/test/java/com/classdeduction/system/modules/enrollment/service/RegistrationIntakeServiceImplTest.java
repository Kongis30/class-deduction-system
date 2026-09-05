package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.common.util.StudentNoGenerator;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.auth.entity.RoleEntity;
import com.classdeduction.system.modules.auth.entity.UserAccountEntity;
import com.classdeduction.system.modules.auth.mapper.RoleMapper;
import com.classdeduction.system.modules.auth.mapper.UserAccountMapper;
import com.classdeduction.system.modules.auth.mapper.UserRoleMapper;
import com.classdeduction.system.modules.base.entity.CoursePriceOptionEntity;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.CampusEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.mapper.CampusMapper;
import com.classdeduction.system.modules.base.mapper.CoursePriceOptionMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.RegistrationIntakeRequest;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.entity.ReceiptRecordEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentMethodMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import com.classdeduction.system.modules.enrollment.mapper.ReceiptRecordMapper;
import com.classdeduction.system.modules.enrollment.service.impl.RegistrationIntakeServiceImpl;
import com.classdeduction.system.modules.enrollment.vo.RegistrationIntakeResultVO;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationIntakeServiceImplTest {

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private StudentNoGenerator studentNoGenerator;

    @Mock
    private CampusMapper campusMapper;

    @Mock
    private CoursePriceOptionMapper coursePriceOptionMapper;

    @Mock
    private CourseProductMapper courseProductMapper;

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private EnrollmentOrderMapper enrollmentOrderMapper;

    @Mock
    private EnrollmentOrderItemMapper enrollmentOrderItemMapper;

    @Mock
    private CoursePackageMapper coursePackageMapper;

    @Mock
    private ClassGroupMapper classGroupMapper;

    @Mock
    private ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;

    @Mock
    private ClassEnrollmentMapper classEnrollmentMapper;

    @Mock
    private LessonOccurrenceMapper lessonOccurrenceMapper;

    @Mock
    private PaymentMethodMapper paymentMethodMapper;

    @Mock
    private PaymentRecordMapper paymentRecordMapper;

    @Mock
    private ReceiptRecordMapper receiptRecordMapper;

    @Mock
    private UserAccountMapper userAccountMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JsonHelper jsonHelper;

    @Captor
    private ArgumentCaptor<CoursePackageEntity> coursePackageCaptor;

    @Captor
    private ArgumentCaptor<EnrollmentOrderItemEntity> orderItemCaptor;

    @Captor
    private ArgumentCaptor<EnrollmentOrderEntity> enrollmentOrderCaptor;

    @Captor
    private ArgumentCaptor<UserAccountEntity> userAccountCaptor;

    private RegistrationIntakeServiceImpl registrationIntakeService;

    @BeforeEach
    void setUp() {
        registrationIntakeService = new RegistrationIntakeServiceImpl(
                studentMapper,
                studentNoGenerator,
                campusMapper,
                coursePriceOptionMapper,
                courseProductMapper,
                teacherMapper,
                enrollmentOrderMapper,
                enrollmentOrderItemMapper,
                coursePackageMapper,
                classGroupMapper,
                classGroupScheduleSlotMapper,
                classEnrollmentMapper,
                lessonOccurrenceMapper,
                paymentMethodMapper,
                paymentRecordMapper,
                receiptRecordMapper,
                userAccountMapper,
                roleMapper,
                userRoleMapper,
                passwordEncoder,
                jsonHelper
        );
    }

    @Test
    void shouldCreateOneOnOnePackageAndStudentAccountWithoutScheduling() {
        stubSuccessfulIntake(false);

        RegistrationIntakeRequest request = buildOneOnOneRequest();
        RegistrationIntakeResultVO result = registrationIntakeService.submitIntake(request);

        verify(coursePackageMapper).insert(coursePackageCaptor.capture());
        verify(enrollmentOrderItemMapper).insert(orderItemCaptor.capture());
        verify(lessonOccurrenceMapper, never()).insert(any(LessonOccurrenceEntity.class));
        assertThat(coursePackageCaptor.getValue().getTotalUnits()).isEqualTo(20);
        assertThat(coursePackageCaptor.getValue().getRemainingUnits()).isEqualTo(20);
        assertThat(coursePackageCaptor.getValue().getOriginalAmount()).isEqualByComparingTo("800.00");
        assertThat(orderItemCaptor.getValue().getOriginalPriceAmount()).isEqualByComparingTo("800.00");
        assertThat(orderItemCaptor.getValue().getPriceAmount()).isEqualByComparingTo("800.00");
        assertThat(orderItemCaptor.getValue().getOneOnOneUnits()).isEqualTo(20);

        verify(userAccountMapper).insert(userAccountCaptor.capture());
        assertThat(userAccountCaptor.getValue().getUsername()).isEqualTo("jint@jin");
        assertThat(userAccountCaptor.getValue().getPasswordHash()).isEqualTo("encoded");
        assertThat(result.studentAccount().username()).isEqualTo("jint@jin");
        assertThat(result.studentAccount().initialPassword()).isEqualTo("9175550101");
        assertThat(result.teacherAccounts()).isEmpty();
    }

    @Test
    void shouldUseProductNameWhenCourseOptionDetailIsBlank() {
        stubSuccessfulIntake(false);
        CoursePriceOptionEntity option = buildOption("15:30-17:00");
        option.setDetailText("");
        when(coursePriceOptionMapper.selectById(201L)).thenReturn(option);

        registrationIntakeService.submitIntake(buildOneOnOneRequest());

        verify(enrollmentOrderItemMapper).insert(orderItemCaptor.capture());
        assertThat(orderItemCaptor.getValue().getDetail()).isEqualTo("1:1 Algebra");
    }

    @Test
    void shouldScaleOneOnOnePriceFromConfiguredHourlyRate() {
        stubSuccessfulIntake(false);
        RegistrationIntakeRequest request = buildOneOnOneRequest();
        request.getItems().get(0).setOneOnOneHours(new BigDecimal("15.0"));

        registrationIntakeService.submitIntake(request);

        verify(enrollmentOrderItemMapper).insert(orderItemCaptor.capture());
        verify(coursePackageMapper).insert(coursePackageCaptor.capture());
        assertThat(orderItemCaptor.getValue().getPriceAmount()).isEqualByComparingTo("1200.00");
        assertThat(coursePackageCaptor.getValue().getTotalUnits()).isEqualTo(30);
        assertThat(coursePackageCaptor.getValue().getOriginalAmount()).isEqualByComparingTo("1200.00");
    }

    @Test
    void shouldAddBonusHoursIntoOneOnOneTotalUnits() {
        stubSuccessfulIntake(false);
        RegistrationIntakeRequest request = buildOneOnOneRequest();
        request.getItems().get(0).setOneOnOneBonusHours(new BigDecimal("3.0"));

        registrationIntakeService.submitIntake(request);

        verify(enrollmentOrderItemMapper).insert(orderItemCaptor.capture());
        verify(coursePackageMapper).insert(coursePackageCaptor.capture());
        assertThat(coursePackageCaptor.getValue().getTotalUnits()).isEqualTo(26);
        assertThat(coursePackageCaptor.getValue().getRemainingUnits()).isEqualTo(26);
        // bonus hours are baked into totalUnits, not stored on the item
        assertThat(orderItemCaptor.getValue().getOneOnOneUnits()).isEqualTo(26);
    }

    @Test
    void shouldRenewExistingOneOnOnePackageWithPurchasedAndBonusHours() {
        StudentEntity student = buildExistingStudent();
        CoursePackageEntity existingPackage = new CoursePackageEntity();
        existingPackage.setId(3001L);
        existingPackage.setPackageNo("PK-RENEW");
        existingPackage.setStudentId(101L);
        existingPackage.setCourseProductId(202L);
        existingPackage.setTeachingMode("ONE_ON_ONE");
        existingPackage.setPackageStatus("COMPLETED");
        existingPackage.setTotalUnits(20);
        existingPackage.setConsumedUnits(3);
        existingPackage.setRemainingUnits(17);
        existingPackage.setOriginalAmount(new BigDecimal("1000.00"));
        existingPackage.setReceivedAmount(new BigDecimal("50.00"));
        when(coursePackageMapper.selectById(3001L)).thenReturn(existingPackage);
        when(studentMapper.selectById(101L)).thenReturn(student);
        when(coursePriceOptionMapper.selectById(201L)).thenReturn(buildOption("15:30-17:00"));
        when(courseProductMapper.selectById(202L)).thenReturn(buildProduct());
        when(campusMapper.selectById(1L)).thenReturn(buildCampus());
        when(jsonHelper.toJson(any())).thenReturn("{}");
        lenient().when(userAccountMapper.selectOne(any())).thenReturn(null);
        lenient().when(roleMapper.selectOne(any())).thenReturn(buildRole(1L, "STUDENT"));
        lenient().when(passwordEncoder.encode("9175550101")).thenReturn("encoded");
        lenient().when(userAccountMapper.selectCount(any())).thenReturn(0L);
        lenient().when(lessonOccurrenceMapper.selectList(any())).thenReturn(Collections.emptyList());
        doAnswer(invocation -> {
            EnrollmentOrderEntity order = invocation.getArgument(0);
            order.setId(2001L);
            return 1;
        }).when(enrollmentOrderMapper).insert(any(EnrollmentOrderEntity.class));
        doAnswer(invocation -> {
            EnrollmentOrderItemEntity item = invocation.getArgument(0);
            item.setId(2101L);
            return 1;
        }).when(enrollmentOrderItemMapper).insert(any(EnrollmentOrderItemEntity.class));

        RegistrationIntakeRequest request = buildOneOnOneRequest();
        request.setRenewPackageId(3001L);
        request.getItems().get(0).setOneOnOneHours(new BigDecimal("4.0"));
        request.getItems().get(0).setOneOnOneBonusHours(new BigDecimal("2.5"));

        RegistrationIntakeResultVO result = registrationIntakeService.submitIntake(request);

        verify(studentMapper).updateById(student);
        verify(studentMapper, never()).insert(any(StudentEntity.class));
        verify(coursePackageMapper, never()).insert(any(CoursePackageEntity.class));
        verify(coursePackageMapper).updateById(coursePackageCaptor.capture());
        CoursePackageEntity renewedPackage = coursePackageCaptor.getValue();
        assertThat(renewedPackage.getId()).isEqualTo(3001L);
        assertThat(renewedPackage.getTotalUnits()).isEqualTo(33);
        assertThat(renewedPackage.getRemainingUnits()).isEqualTo(30);
        assertThat(renewedPackage.getConsumedUnits()).isEqualTo(3);
        assertThat(renewedPackage.getPackageStatus()).isEqualTo("ACTIVE");
        assertThat(renewedPackage.getOriginalAmount()).isEqualByComparingTo("1320.00");
        assertThat(result.courseArtifacts()).singleElement()
                .extracting(RegistrationIntakeResultVO.CourseArtifactVO::packageId)
                .isEqualTo(3001L);
    }

    @Test
    void shouldClampOpeningUsedHoursAgainstTotalIncludingBonus() {
        stubSuccessfulIntake(false);
        RegistrationIntakeRequest request = buildOneOnOneRequest();
        request.getItems().get(0).setOneOnOneBonusHours(new BigDecimal("2.0"));
        request.getItems().get(0).setOpeningUsedHours(new BigDecimal("11.0"));

        registrationIntakeService.submitIntake(request);

        verify(coursePackageMapper).insert(coursePackageCaptor.capture());
        assertThat(coursePackageCaptor.getValue().getTotalUnits()).isEqualTo(24);
        assertThat(coursePackageCaptor.getValue().getConsumedUnits()).isEqualTo(22);
        assertThat(coursePackageCaptor.getValue().getRemainingUnits()).isEqualTo(2);
    }

    @Test
    void shouldRejectNegativeBonusHours() {
        stubStudentCreationOnly();
        when(coursePriceOptionMapper.selectById(201L)).thenReturn(buildOption("15:30-17:00"));
        when(courseProductMapper.selectById(202L)).thenReturn(buildProduct());
        RegistrationIntakeRequest request = buildOneOnOneRequest();
        request.getItems().get(0).setOneOnOneBonusHours(new BigDecimal("-1.0"));

        assertThatThrownBy(() -> registrationIntakeService.submitIntake(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("ONE_ON_ONE_BONUS_HOURS_INVALID");
    }

    @Test
    void shouldRejectNonHalfHourBonusHours() {
        stubStudentCreationOnly();
        when(coursePriceOptionMapper.selectById(201L)).thenReturn(buildOption("15:30-17:00"));
        when(courseProductMapper.selectById(202L)).thenReturn(buildProduct());
        RegistrationIntakeRequest request = buildOneOnOneRequest();
        request.getItems().get(0).setOneOnOneBonusHours(new BigDecimal("1.3"));

        assertThatThrownBy(() -> registrationIntakeService.submitIntake(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("ONE_ON_ONE_BONUS_HOURS_INVALID");
    }

    @Test
    void shouldInitializeOneOnOneOpeningBalanceFromAlreadyUsedHours() {
        stubSuccessfulIntake(false);
        RegistrationIntakeRequest request = buildOneOnOneRequest();
        request.getItems().get(0).setOpeningUsedHours(new BigDecimal("3.5"));

        registrationIntakeService.submitIntake(request);

        verify(enrollmentOrderItemMapper).insert(orderItemCaptor.capture());
        verify(coursePackageMapper).insert(coursePackageCaptor.capture());
        assertThat(orderItemCaptor.getValue().getOpeningUsedHours()).isEqualByComparingTo("3.5");
        assertThat(coursePackageCaptor.getValue().getTotalUnits()).isEqualTo(20);
        assertThat(coursePackageCaptor.getValue().getConsumedUnits()).isEqualTo(7);
        assertThat(coursePackageCaptor.getValue().getRemainingUnits()).isEqualTo(13);
        verify(lessonOccurrenceMapper, never()).insert(any(LessonOccurrenceEntity.class));
    }

    @Test
    void shouldAllowOpeningUsedHoursEqualToPurchasedHours() {
        stubSuccessfulIntake(false);
        RegistrationIntakeRequest request = buildOneOnOneRequest();
        request.getItems().get(0).setOpeningUsedHours(new BigDecimal("10.0"));

        registrationIntakeService.submitIntake(request);

        verify(coursePackageMapper).insert(coursePackageCaptor.capture());
        assertThat(coursePackageCaptor.getValue().getConsumedUnits()).isEqualTo(20);
        assertThat(coursePackageCaptor.getValue().getRemainingUnits()).isZero();
    }

    @Test
    void shouldRejectOpeningUsedHoursExceedingPurchasedHours() {
        stubStudentCreationOnly();
        when(coursePriceOptionMapper.selectById(201L)).thenReturn(buildOption("15:30-17:00"));
        when(courseProductMapper.selectById(202L)).thenReturn(buildProduct());
        RegistrationIntakeRequest request = buildOneOnOneRequest();
        request.getItems().get(0).setOpeningUsedHours(new BigDecimal("10.5"));

        assertThatThrownBy(() -> registrationIntakeService.submitIntake(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("OPENING_USED_HOURS_EXCEED_PACKAGE");
        verify(coursePackageMapper, never()).insert(any(CoursePackageEntity.class));
    }

    @Test
    void shouldRejectOpeningUsedHoursOutsideHalfHourIncrement() {
        stubStudentCreationOnly();
        when(coursePriceOptionMapper.selectById(201L)).thenReturn(buildOption("15:30-17:00"));
        when(courseProductMapper.selectById(202L)).thenReturn(buildProduct());
        RegistrationIntakeRequest request = buildOneOnOneRequest();
        request.getItems().get(0).setOpeningUsedHours(new BigDecimal("3.25"));

        assertThatThrownBy(() -> registrationIntakeService.submitIntake(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("OPENING_USED_HOURS_INVALID");
        verify(coursePackageMapper, never()).insert(any(CoursePackageEntity.class));
    }

    @Test
    void shouldAcceptHistoricalStartDateForLegacyIntake() {
        stubSuccessfulIntake(false);
        RegistrationIntakeRequest request = buildOneOnOneRequest();
        request.setPeriodStartDate(LocalDate.of(2025, 1, 15));
        request.setPeriodEndDate(LocalDate.of(2026, 12, 31));
        request.getItems().get(0).setOpeningUsedHours(new BigDecimal("2.0"));

        registrationIntakeService.submitIntake(request);

        verify(coursePackageMapper).insert(coursePackageCaptor.capture());
        assertThat(coursePackageCaptor.getValue().getStartDate()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(coursePackageCaptor.getValue().getConsumedUnits()).isEqualTo(4);
        verify(lessonOccurrenceMapper, never()).insert(any(LessonOccurrenceEntity.class));
    }

    @Test
    void shouldRejectInvalidCampusBeforeWritingStudent() {
        RegistrationIntakeRequest request = buildOneOnOneRequest();
        request.setCampusId(999L);
        when(campusMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> registrationIntakeService.submitIntake(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("CAMPUS_NOT_AVAILABLE");
        verify(studentMapper, never()).insert(any(StudentEntity.class));
        verify(studentMapper, never()).updateById(any(StudentEntity.class));
    }

    @Test
    void shouldIgnoreLegacyTeacherAndInitialLessonFieldsForOneOnOneRegistration() {
        stubSuccessfulIntake(false);
        RegistrationIntakeRequest request = buildOneOnOneRequest();

        registrationIntakeService.submitIntake(request);

        verify(lessonOccurrenceMapper, never()).insert(any(LessonOccurrenceEntity.class));
        verify(enrollmentOrderItemMapper).insert(orderItemCaptor.capture());
    }

    @Test
    void shouldAppendNumberBeforeJinDomainWhenStudentUsernameExists() {
        stubSuccessfulIntake(true);

        RegistrationIntakeResultVO result = registrationIntakeService.submitIntake(buildOneOnOneRequest());

        verify(userAccountMapper).insert(userAccountCaptor.capture());
        assertThat(userAccountCaptor.getValue().getUsername()).isEqualTo("jint2@jin");
        assertThat(result.studentAccount().username()).isEqualTo("jint2@jin");
    }

    @Test
    void shouldCreateOneOnOnePackageWithoutInitialLesson() {
        stubSuccessfulIntake(false);

        RegistrationIntakeResultVO result = registrationIntakeService.submitIntake(buildOneOnOneRequest());

        assertThat(result.courseArtifacts()).singleElement()
                .extracting(RegistrationIntakeResultVO.CourseArtifactVO::packageId)
                .isEqualTo(3001L);
        verify(lessonOccurrenceMapper, never()).insert(any(LessonOccurrenceEntity.class));
    }

    @Test
    void shouldRejectIntakeWhenSelectedPriceOptionHasNoConfiguredPrice() {
        stubStudentCreationOnly();
        CoursePriceOptionEntity option = buildOption("15:30-17:00");
        option.setPriceAmount(null);
        when(coursePriceOptionMapper.selectById(201L)).thenReturn(option);
        when(courseProductMapper.selectById(202L)).thenReturn(buildProduct());

        assertThatThrownBy(() -> registrationIntakeService.submitIntake(buildOneOnOneRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Configured course price is required.");
    }

    @Test
    void shouldRequireManualLinePriceWhenSelectedTemplatePriceIsZero() {
        stubStudentCreationOnly();
        CoursePriceOptionEntity option = buildOption("15:30-17:00");
        option.setPriceAmount(BigDecimal.ZERO);
        when(coursePriceOptionMapper.selectById(201L)).thenReturn(option);
        when(courseProductMapper.selectById(202L)).thenReturn(buildProduct());

        assertThatThrownBy(() -> registrationIntakeService.submitIntake(buildOneOnOneRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Manual course line price is required for a $0 course template.");
    }

    @Test
    void shouldAcceptManualLinePriceWhenSelectedTemplatePriceIsZero() {
        stubSuccessfulIntake(false);
        CoursePriceOptionEntity option = buildOption("15:30-17:00");
        option.setPriceAmount(BigDecimal.ZERO);
        when(coursePriceOptionMapper.selectById(201L)).thenReturn(option);
        RegistrationIntakeRequest request = buildOneOnOneRequest();
        RegistrationIntakeRequest.CourseItemPayload item = request.getItems().get(0);
        item.setManualPriceOverride(true);
        item.setOriginalPriceAmount(BigDecimal.ZERO);
        item.setPriceAmount(new BigDecimal("850.00"));
        item.setRemark("PPT template price pending; charged approved counter price.");

        registrationIntakeService.submitIntake(request);

        verify(enrollmentOrderItemMapper).insert(orderItemCaptor.capture());
        assertThat(orderItemCaptor.getValue().getOriginalPriceAmount()).isEqualByComparingTo("0.00");
        assertThat(orderItemCaptor.getValue().getPriceAmount()).isEqualByComparingTo("850.00");
        assertThat(orderItemCaptor.getValue().getManualPriceOverride()).isTrue();
    }

    @Test
    void shouldNotChargePickupFeeUnlessThisRegistrationExplicitlyChargesIt() {
        stubSuccessfulIntake(false);
        RegistrationIntakeRequest request = buildOneOnOneRequest();
        request.getStudent().setPickupRequired(true);
        request.getStudent().setPickupAddress("Main St");
        request.setPickupFeeAmount(new BigDecimal("50.00"));

        registrationIntakeService.submitIntake(request);

        verify(enrollmentOrderMapper).insert(enrollmentOrderCaptor.capture());
        assertThat(enrollmentOrderCaptor.getValue().getPickupRequiredSnapshot()).isTrue();
        assertThat(enrollmentOrderCaptor.getValue().getPickupFeeAmount()).isEqualByComparingTo("0.00");
        assertThat(enrollmentOrderCaptor.getValue().getOriginalAmount()).isEqualByComparingTo("800.00");
        assertThat(enrollmentOrderCaptor.getValue().getAmountDue()).isEqualByComparingTo("800.00");
    }

    @Test
    void shouldRejectGroupClassIntakeWhenStudentScheduleConflicts() {
        stubStudentCreationOnly();
        CoursePriceOptionEntity option = buildOption("15:30-17:00");
        option.setCourseType("GROUP");
        option.setRegisterClass("Group class");
        CourseProductEntity product = buildProduct();
        product.setTeachingMode("GROUP");
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        classGroup.setProductId(202L);
        classGroup.setStatus("ACTIVE");
        classGroup.setTeacherId(301L);
        classGroup.setClassroomId(401L);
        classGroup.setBillingType("MONTHLY");
        classGroup.setStartMonth(LocalDate.of(2026, 9, 1));
        classGroup.setEndMonth(LocalDate.of(2026, 12, 1));
        ClassGroupScheduleSlotEntity slot = new ClassGroupScheduleSlotEntity();
        slot.setClassGroupId(501L);
        slot.setWeekday(2);
        slot.setStartTime(java.time.LocalTime.of(15, 30));
        slot.setEndTime(java.time.LocalTime.of(17, 0));
        LessonOccurrenceEntity targetOccurrence = new LessonOccurrenceEntity();
        targetOccurrence.setId(4001L);
        targetOccurrence.setClassGroupId(501L);
        targetOccurrence.setPlannedStartAt(LocalDateTime.of(2026, 9, 1, 15, 30));
        targetOccurrence.setPlannedEndAt(LocalDateTime.of(2026, 9, 1, 17, 0));
        LessonOccurrenceEntity existingOccurrence = new LessonOccurrenceEntity();
        existingOccurrence.setId(9001L);
        existingOccurrence.setLessonNo("O2O-CONFLICT");
        existingOccurrence.setPlannedStartAt(LocalDateTime.of(2026, 9, 1, 16, 0));
        existingOccurrence.setPlannedEndAt(LocalDateTime.of(2026, 9, 1, 17, 30));

        when(coursePriceOptionMapper.selectById(201L)).thenReturn(option);
        when(courseProductMapper.selectById(202L)).thenReturn(product);
        when(classGroupMapper.selectById(501L)).thenReturn(classGroup);
        when(campusMapper.selectById(1L)).thenReturn(buildCampus());
        when(classGroupScheduleSlotMapper.selectList(any())).thenReturn(List.of(slot));
        when(lessonOccurrenceMapper.selectList(any()))
                .thenReturn(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                        List.of(targetOccurrence), List.of(existingOccurrence));
        lenient().when(classEnrollmentMapper.selectCount(any())).thenReturn(0L);
        when(studentMapper.selectById(101L)).thenReturn(buildExistingStudent());
        doAnswer(invocation -> {
            EnrollmentOrderEntity order = invocation.getArgument(0);
            order.setId(2001L);
            return 1;
        }).when(enrollmentOrderMapper).insert(any(EnrollmentOrderEntity.class));
        doAnswer(invocation -> {
            EnrollmentOrderItemEntity item = invocation.getArgument(0);
            item.setId(2101L);
            return 1;
        }).when(enrollmentOrderItemMapper).insert(any(EnrollmentOrderItemEntity.class));
        doAnswer(invocation -> {
            LessonOccurrenceEntity occurrence = invocation.getArgument(0);
            occurrence.setId(4001L);
            return 1;
        }).when(lessonOccurrenceMapper).insert(any(LessonOccurrenceEntity.class));

        assertThatThrownBy(() -> registrationIntakeService.submitIntake(buildGroupClassRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("SCHEDULE_CONFLICT");
        verify(classEnrollmentMapper, never()).insert(any(com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity.class));
    }

    @Test
    void shouldCreateGroupClassIntakeWhenOneOnOneUnitsIsMissing() {
        stubGroupClassIntake();

        registrationIntakeService.submitIntake(buildGroupClassRequest());

        verify(enrollmentOrderItemMapper).insert(orderItemCaptor.capture());
        assertThat(orderItemCaptor.getValue().getOneOnOneUnits()).isZero();
        verify(classEnrollmentMapper).insert(any(com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity.class));
        verify(coursePackageMapper, never()).insert(any(CoursePackageEntity.class));
    }

    @Test
    void shouldCreateCalendarMonthGroupEnrollmentFromSelectedPackage() {
        stubMonthlyGroupClassIntake();
        RegistrationIntakeRequest request = buildGroupClassRequest();
        request.setSignupDate(LocalDate.of(2026, 5, 15));
        RegistrationIntakeRequest.CourseItemPayload item = request.getItems().get(0);
        item.setPackageUnit("MONTH");
        item.setPackageQuantity(new BigDecimal("2"));

        registrationIntakeService.submitIntake(request);

        ArgumentCaptor<ClassEnrollmentEntity> enrollmentCaptor =
                ArgumentCaptor.forClass(ClassEnrollmentEntity.class);
        verify(enrollmentOrderItemMapper).insert(orderItemCaptor.capture());
        verify(classEnrollmentMapper).insert(enrollmentCaptor.capture());
        assertThat(orderItemCaptor.getValue().getPriceAmount()).isEqualByComparingTo("800.00");
        assertThat(enrollmentCaptor.getValue().getPurchasedQuantity()).isEqualTo(2);
        assertThat(enrollmentCaptor.getValue().getEnrollmentDate()).isEqualTo(LocalDate.of(2026, 5, 15));
        assertThat(enrollmentCaptor.getValue().getExpiryDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(enrollmentCaptor.getValue().getBillingType()).isEqualTo("MONTH");
    }

    @Test
    void shouldUseCourseLineStartDateForMidTermGroupEnrollment() {
        stubMonthlyGroupClassIntake();
        RegistrationIntakeRequest request = buildGroupClassRequest();
        request.setSignupDate(LocalDate.of(2026, 1, 10));
        RegistrationIntakeRequest.CourseItemPayload item = request.getItems().get(0);
        item.setStartDate(LocalDate.of(2026, 3, 15));
        item.setPackageUnit("MONTH");
        item.setPackageQuantity(new BigDecimal("2"));

        registrationIntakeService.submitIntake(request);

        ArgumentCaptor<ClassEnrollmentEntity> enrollmentCaptor =
                ArgumentCaptor.forClass(ClassEnrollmentEntity.class);
        verify(enrollmentOrderItemMapper).insert(orderItemCaptor.capture());
        verify(classEnrollmentMapper).insert(enrollmentCaptor.capture());
        assertThat(orderItemCaptor.getValue().getStartDate()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(enrollmentCaptor.getValue().getEnrollmentDate()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(enrollmentCaptor.getValue().getExpiryDate()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    void shouldRenewExistingGroupClassEnrollmentPeriodWithoutCreatingDuplicateEnrollment() {
        stubMonthlyGroupClassRenewal();
        StudentEntity student = buildExistingStudent();
        ClassEnrollmentEntity existingEnrollment = new ClassEnrollmentEntity();
        existingEnrollment.setId(3101L);
        existingEnrollment.setStudentId(101L);
        existingEnrollment.setClassId(501L);
        existingEnrollment.setEnrollmentDate(LocalDate.of(2026, 9, 1));
        existingEnrollment.setExpiryDate(LocalDate.of(2026, 10, 31));
        existingEnrollment.setBillingType("MONTH");
        existingEnrollment.setPurchasedQuantity(2);
        existingEnrollment.setAttendanceOption("NONE");
        existingEnrollment.setStatus("ACTIVE");
        when(classEnrollmentMapper.selectById(3101L)).thenReturn(existingEnrollment);
        when(studentMapper.selectById(101L)).thenReturn(student);

        RegistrationIntakeRequest request = buildGroupClassRequest();
        request.setRenewClassEnrollmentId(3101L);
        request.setSignupDate(LocalDate.of(2026, 9, 20));
        RegistrationIntakeRequest.CourseItemPayload item = request.getItems().get(0);
        item.setStartDate(LocalDate.of(2026, 11, 1));
        item.setPackageUnit("MONTH");
        item.setPackageQuantity(new BigDecimal("2"));

        RegistrationIntakeResultVO result = registrationIntakeService.submitIntake(request);

        verify(studentMapper).updateById(student);
        verify(studentMapper, never()).insert(any(StudentEntity.class));
        verify(classEnrollmentMapper, never()).insert(any(ClassEnrollmentEntity.class));
        ArgumentCaptor<ClassEnrollmentEntity> enrollmentCaptor =
                ArgumentCaptor.forClass(ClassEnrollmentEntity.class);
        verify(classEnrollmentMapper).updateById(enrollmentCaptor.capture());
        ClassEnrollmentEntity renewedEnrollment = enrollmentCaptor.getValue();
        assertThat(renewedEnrollment.getId()).isEqualTo(3101L);
        assertThat(renewedEnrollment.getEnrollmentDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(renewedEnrollment.getExpiryDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(renewedEnrollment.getPurchasedQuantity()).isEqualTo(4);
        assertThat(result.courseArtifacts()).singleElement()
                .extracting(RegistrationIntakeResultVO.CourseArtifactVO::classEnrollmentId)
                .isEqualTo(3101L);
    }

    @Test
    void shouldRenewMorningGroupClassWhenAfternoonStudentConflictExists() {
        stubMonthlyGroupClassRenewal();
        StudentEntity student = buildExistingStudent();
        ClassEnrollmentEntity existingEnrollment = buildRenewableClassEnrollment();
        when(classEnrollmentMapper.selectById(3101L)).thenReturn(existingEnrollment);
        when(studentMapper.selectById(101L)).thenReturn(student);

        ClassGroupScheduleSlotEntity morningSlot = buildAttendanceSlot(7101L, "MORNING", 9, 0, 12, 0);
        ClassGroupScheduleSlotEntity afternoonSlot = buildAttendanceSlot(7102L, "AFTERNOON", 13, 0, 16, 0);
        when(classGroupScheduleSlotMapper.selectList(any())).thenReturn(List.of(morningSlot, afternoonSlot));
        when(classGroupScheduleSlotMapper.selectById(7101L)).thenReturn(morningSlot);
        when(classGroupScheduleSlotMapper.selectById(7102L)).thenReturn(afternoonSlot);

        LessonOccurrenceEntity morningOccurrence = buildClassOccurrence(8101L, 7101L, 9, 0, 12, 0);
        LessonOccurrenceEntity afternoonOccurrence = buildClassOccurrence(8102L, 7102L, 13, 0, 16, 0);
        LessonOccurrenceEntity afternoonOneOnOne = new LessonOccurrenceEntity();
        afternoonOneOnOne.setId(9101L);
        afternoonOneOnOne.setLessonNo("O2O-AFTERNOON");
        afternoonOneOnOne.setPlannedStartAt(LocalDateTime.of(2026, 11, 3, 13, 30));
        afternoonOneOnOne.setPlannedEndAt(LocalDateTime.of(2026, 11, 3, 15, 0));
        when(lessonOccurrenceMapper.selectList(any()))
                .thenReturn(List.of(morningOccurrence, afternoonOccurrence),
                        List.of(morningOccurrence, afternoonOccurrence),
                        List.of(afternoonOneOnOne));

        RegistrationIntakeRequest request = buildGroupClassRequest();
        request.setRenewClassEnrollmentId(3101L);
        request.setSignupDate(LocalDate.of(2026, 9, 20));
        RegistrationIntakeRequest.CourseItemPayload item = request.getItems().get(0);
        item.setStartDate(LocalDate.of(2026, 11, 1));
        item.setPackageUnit("MONTH");
        item.setPackageQuantity(new BigDecimal("2"));
        item.setPurchasedMonths(2);
        item.setAttendanceOption("MORNING");

        registrationIntakeService.submitIntake(request);

        ArgumentCaptor<ClassEnrollmentEntity> enrollmentCaptor =
                ArgumentCaptor.forClass(ClassEnrollmentEntity.class);
        verify(classEnrollmentMapper).updateById(enrollmentCaptor.capture());
        assertThat(enrollmentCaptor.getValue().getAttendanceOption()).isEqualTo("MORNING");
        assertThat(enrollmentCaptor.getValue().getPurchasedQuantity()).isEqualTo(4);
    }

    @Test
    void shouldRejectGroupEnrollmentStartDateOutsideClassRange() {
        stubMonthlyGroupClassIntake();
        RegistrationIntakeRequest request = buildGroupClassRequest();
        RegistrationIntakeRequest.CourseItemPayload item = request.getItems().get(0);
        item.setStartDate(LocalDate.of(2025, 12, 31));
        item.setPackageUnit("MONTH");
        item.setPackageQuantity(new BigDecimal("2"));

        assertThatThrownBy(() -> registrationIntakeService.submitIntake(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("ENROLLMENT_START_OUT_OF_RANGE");
        verify(classEnrollmentMapper, never()).insert(any(ClassEnrollmentEntity.class));
    }

    private void stubSuccessfulIntake(boolean duplicateStudentUsername) {
        stubSuccessfulIntake(duplicateStudentUsername, "15:30-17:00");
    }

    private void stubSuccessfulIntake(boolean duplicateStudentUsername, String timeSlotLabel) {
        when(studentNoGenerator.generate()).thenReturn("STU202609010001");
        when(studentMapper.selectReusableStudent("Jin Tang", "9175550101")).thenReturn(null);
        when(coursePriceOptionMapper.selectById(201L)).thenReturn(buildOption(timeSlotLabel));
        when(courseProductMapper.selectById(202L)).thenReturn(buildProduct());
        when(campusMapper.selectById(1L)).thenReturn(buildCampus());
        when(jsonHelper.toJson(any())).thenReturn("{}");
        when(passwordEncoder.encode("9175550101")).thenReturn("encoded");
        when(roleMapper.selectOne(any())).thenReturn(buildRole(1L, "STUDENT"), buildRole(2L, "TEACHER"));
        when(userAccountMapper.selectOne(any())).thenReturn(null);
        lenient().when(lessonOccurrenceMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(userAccountMapper.selectCount(any())).thenReturn(duplicateStudentUsername ? 1L : 0L, 0L);

        doAnswer(invocation -> {
            StudentEntity student = invocation.getArgument(0);
            student.setId(101L);
            return 1;
        }).when(studentMapper).insert(any(StudentEntity.class));
        doAnswer(invocation -> {
            EnrollmentOrderEntity order = invocation.getArgument(0);
            order.setId(2001L);
            return 1;
        }).when(enrollmentOrderMapper).insert(any(EnrollmentOrderEntity.class));
        doAnswer(invocation -> {
            EnrollmentOrderItemEntity item = invocation.getArgument(0);
            item.setId(2101L);
            return 1;
        }).when(enrollmentOrderItemMapper).insert(any(EnrollmentOrderItemEntity.class));
        doAnswer(invocation -> {
            CoursePackageEntity coursePackage = invocation.getArgument(0);
            coursePackage.setId(3001L);
            return 1;
        }).when(coursePackageMapper).insert(any(CoursePackageEntity.class));
        doAnswer(invocation -> {
            ReceiptRecordEntity receipt = invocation.getArgument(0);
            receipt.setId(5001L);
            return 1;
        }).when(receiptRecordMapper).insert(any(ReceiptRecordEntity.class));
        doAnswer(invocation -> {
            UserAccountEntity account = invocation.getArgument(0);
            account.setId(6001L);
            return 1;
        }).when(userAccountMapper).insert(any(UserAccountEntity.class));
    }

    private void stubGroupClassIntake() {
        stubStudentCreationOnly();
        CoursePriceOptionEntity option = buildOption("15:30-17:00");
        option.setCourseType("GROUP");
        option.setRegisterClass("Group class");
        option.setPackageUnit("MONTH");
        option.setPackageQuantity(new BigDecimal("2"));
        CourseProductEntity product = buildProduct();
        product.setTeachingMode("GROUP");
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        classGroup.setProductId(202L);
        classGroup.setStatus("ACTIVE");
        classGroup.setBillingType("MONTHLY");
        classGroup.setStartDate(LocalDate.of(2026, 9, 1));
        classGroup.setEndDate(LocalDate.of(2026, 12, 31));
        ClassGroupScheduleSlotEntity slot = new ClassGroupScheduleSlotEntity();
        slot.setClassGroupId(501L);
        slot.setWeekday(2);
        slot.setStartTime(java.time.LocalTime.of(15, 30));
        slot.setEndTime(java.time.LocalTime.of(17, 0));
        LessonOccurrenceEntity targetOccurrence = new LessonOccurrenceEntity();
        targetOccurrence.setId(4001L);
        targetOccurrence.setClassGroupId(501L);
        targetOccurrence.setPlannedStartAt(LocalDateTime.of(2026, 9, 1, 15, 30));
        targetOccurrence.setPlannedEndAt(LocalDateTime.of(2026, 9, 1, 17, 0));

        when(coursePriceOptionMapper.selectById(201L)).thenReturn(option);
        when(courseProductMapper.selectById(202L)).thenReturn(product);
        when(classGroupMapper.selectById(501L)).thenReturn(classGroup);
        when(classGroupScheduleSlotMapper.selectList(any())).thenReturn(List.of(slot));
        when(jsonHelper.toJson(any())).thenReturn("{}");
        lenient().when(classEnrollmentMapper.selectCount(any())).thenReturn(0L);
        lenient().when(lessonOccurrenceMapper.selectList(any()))
                .thenReturn(Collections.emptyList(), List.of(targetOccurrence), Collections.emptyList(), Collections.emptyList());
        doAnswer(invocation -> {
            EnrollmentOrderEntity order = invocation.getArgument(0);
            order.setId(2001L);
            return 1;
        }).when(enrollmentOrderMapper).insert(any(EnrollmentOrderEntity.class));
        doAnswer(invocation -> {
            EnrollmentOrderItemEntity item = invocation.getArgument(0);
            item.setId(2101L);
            return 1;
        }).when(enrollmentOrderItemMapper).insert(any(EnrollmentOrderItemEntity.class));
        lenient().doAnswer(invocation -> {
            com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity enrollment = invocation.getArgument(0);
            enrollment.setId(3101L);
            return 1;
        }).when(classEnrollmentMapper).insert(any(com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity.class));
    }

    private void stubMonthlyGroupClassIntake() {
        stubGroupClassIntake();
        CoursePriceOptionEntity option = buildOption("15:30-17:00");
        option.setCourseType("GROUP");
        option.setRegisterClass("Group class");
        CourseProductEntity product = buildProduct();
        product.setTeachingMode("GROUP");
        product.setBillingType("MONTHLY");
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        classGroup.setProductId(202L);
        classGroup.setStatus("ACTIVE");
        classGroup.setBillingType("MONTHLY");
        classGroup.setStartDate(LocalDate.of(2026, 1, 1));
        classGroup.setEndDate(LocalDate.of(2026, 7, 31));
        when(coursePriceOptionMapper.selectById(201L)).thenReturn(option);
        when(courseProductMapper.selectById(202L)).thenReturn(product);
        when(classGroupMapper.selectById(501L)).thenReturn(classGroup);
    }

    private void stubMonthlyGroupClassRenewal() {
        CoursePriceOptionEntity option = buildOption("15:30-17:00");
        option.setCourseType("GROUP");
        option.setRegisterClass("Group class");
        option.setPackageUnit("MONTH");
        option.setPackageQuantity(new BigDecimal("2"));
        CourseProductEntity product = buildProduct();
        product.setTeachingMode("GROUP");
        product.setBillingType("MONTHLY");
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        classGroup.setProductId(202L);
        classGroup.setStatus("ACTIVE");
        classGroup.setBillingType("MONTHLY");
        classGroup.setStartDate(LocalDate.of(2026, 1, 1));
        classGroup.setEndDate(LocalDate.of(2026, 12, 31));
        ClassGroupScheduleSlotEntity slot = new ClassGroupScheduleSlotEntity();
        slot.setClassGroupId(501L);
        slot.setWeekday(2);
        slot.setStartTime(java.time.LocalTime.of(15, 30));
        slot.setEndTime(java.time.LocalTime.of(17, 0));
        LessonOccurrenceEntity targetOccurrence = new LessonOccurrenceEntity();
        targetOccurrence.setId(4001L);
        targetOccurrence.setClassGroupId(501L);
        targetOccurrence.setPlannedStartAt(LocalDateTime.of(2026, 11, 3, 15, 30));
        targetOccurrence.setPlannedEndAt(LocalDateTime.of(2026, 11, 3, 17, 0));

        when(coursePriceOptionMapper.selectById(201L)).thenReturn(option);
        when(courseProductMapper.selectById(202L)).thenReturn(product);
        when(classGroupMapper.selectById(501L)).thenReturn(classGroup);
        when(campusMapper.selectById(1L)).thenReturn(buildCampus());
        when(classGroupScheduleSlotMapper.selectList(any())).thenReturn(List.of(slot));
        when(jsonHelper.toJson(any())).thenReturn("{}");
        when(classEnrollmentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(lessonOccurrenceMapper.selectList(any()))
                .thenReturn(Collections.emptyList(), List.of(targetOccurrence), Collections.emptyList(), Collections.emptyList());
        doAnswer(invocation -> {
            EnrollmentOrderEntity order = invocation.getArgument(0);
            order.setId(2001L);
            return 1;
        }).when(enrollmentOrderMapper).insert(any(EnrollmentOrderEntity.class));
        doAnswer(invocation -> {
            EnrollmentOrderItemEntity item = invocation.getArgument(0);
            item.setId(2101L);
            return 1;
        }).when(enrollmentOrderItemMapper).insert(any(EnrollmentOrderItemEntity.class));
    }

    private ClassEnrollmentEntity buildRenewableClassEnrollment() {
        ClassEnrollmentEntity existingEnrollment = new ClassEnrollmentEntity();
        existingEnrollment.setId(3101L);
        existingEnrollment.setStudentId(101L);
        existingEnrollment.setClassId(501L);
        existingEnrollment.setEnrollmentDate(LocalDate.of(2026, 9, 1));
        existingEnrollment.setExpiryDate(LocalDate.of(2026, 10, 31));
        existingEnrollment.setBillingType("MONTH");
        existingEnrollment.setPurchasedQuantity(2);
        existingEnrollment.setAttendanceOption("FULL_DAY");
        existingEnrollment.setStatus("ACTIVE");
        return existingEnrollment;
    }

    private ClassGroupScheduleSlotEntity buildAttendanceSlot(Long id,
                                                             String attendanceOptions,
                                                             int startHour,
                                                             int startMinute,
                                                             int endHour,
                                                             int endMinute) {
        ClassGroupScheduleSlotEntity slot = new ClassGroupScheduleSlotEntity();
        slot.setId(id);
        slot.setClassGroupId(501L);
        slot.setWeekday(2);
        slot.setStartTime(java.time.LocalTime.of(startHour, startMinute));
        slot.setEndTime(java.time.LocalTime.of(endHour, endMinute));
        slot.setAttendanceOptions(attendanceOptions);
        return slot;
    }

    private LessonOccurrenceEntity buildClassOccurrence(Long id,
                                                        Long scheduleSlotId,
                                                        int startHour,
                                                        int startMinute,
                                                        int endHour,
                                                        int endMinute) {
        LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
        occurrence.setId(id);
        occurrence.setLessonNo("GROUP-" + id);
        occurrence.setClassGroupId(501L);
        occurrence.setScheduleSlotId(scheduleSlotId);
        occurrence.setPlannedStartAt(LocalDateTime.of(2026, 11, 3, startHour, startMinute));
        occurrence.setPlannedEndAt(LocalDateTime.of(2026, 11, 3, endHour, endMinute));
        occurrence.setPlanningStatus("SCHEDULED");
        return occurrence;
    }

    private void stubStudentCreationOnly() {
        when(studentNoGenerator.generate()).thenReturn("STU202609010001");
        when(studentMapper.selectReusableStudent("Jin Tang", "9175550101")).thenReturn(null);
        when(campusMapper.selectById(1L)).thenReturn(buildCampus());
        doAnswer(invocation -> {
            StudentEntity student = invocation.getArgument(0);
            student.setId(101L);
            return 1;
        }).when(studentMapper).insert(any(StudentEntity.class));
    }

    private RegistrationIntakeRequest buildOneOnOneRequest() {
        RegistrationIntakeRequest request = new RegistrationIntakeRequest();
        request.setCampusId(1L);
        request.setHandledByName("Amy");
        request.setSignupDate(LocalDate.of(2026, 8, 20));
        request.setPeriodStartDate(LocalDate.of(2026, 9, 1));
        request.setPeriodEndDate(LocalDate.of(2026, 12, 31));
        request.setStudent(buildStudentPayload());
        request.setItems(Collections.singletonList(buildOneOnOneItem()));
        return request;
    }

    private RegistrationIntakeRequest buildGroupClassRequest() {
        RegistrationIntakeRequest request = buildOneOnOneRequest();
        RegistrationIntakeRequest.CourseItemPayload item = buildOneOnOneItem();
        item.setClassId(501L);
        item.setOneOnOneUnits(null);
        request.setItems(List.of(item));
        return request;
    }

    private RegistrationIntakeRequest.StudentPayload buildStudentPayload() {
        RegistrationIntakeRequest.StudentPayload student = new RegistrationIntakeRequest.StudentPayload();
        student.setStudentName("Jin Tang");
        student.setGuardianPhone("9175550101");
        return student;
    }

    private StudentEntity buildExistingStudent() {
        StudentEntity student = new StudentEntity();
        student.setId(101L);
        student.setStudentName("Jin Tang");
        return student;
    }

    private RegistrationIntakeRequest.CourseItemPayload buildOneOnOneItem() {
        RegistrationIntakeRequest.CourseItemPayload item = new RegistrationIntakeRequest.CourseItemPayload();
        item.setCoursePriceOptionId(201L);
        return item;
    }

    private CoursePriceOptionEntity buildOption(String timeSlotLabel) {
        CoursePriceOptionEntity option = new CoursePriceOptionEntity();
        option.setId(201L);
        option.setCourseProductId(202L);
        option.setCourseType("ONE_ON_ONE");
        option.setRegisterClass("1V1");
        option.setDetailText("1:1 Algebra");
        option.setTimeSlotLabel(timeSlotLabel);
        option.setOneOnOneHours(new BigDecimal("10.0"));
        option.setPriceAmount(new BigDecimal("800.00"));
        option.setStatus("ACTIVE");
        return option;
    }

    private CourseProductEntity buildProduct() {
        CourseProductEntity product = new CourseProductEntity();
        product.setId(202L);
        product.setProductName("1:1 Algebra");
        product.setSubjectName("Math");
        product.setTeachingMode("ONE_ON_ONE");
        product.setStatus("ACTIVE");
        return product;
    }

    private CampusEntity buildCampus() {
        CampusEntity campus = new CampusEntity();
        campus.setId(1L);
        campus.setCampusName("South Campus");
        campus.setStatus("ACTIVE");
        return campus;
    }

    private RoleEntity buildRole(Long id, String roleCode) {
        RoleEntity role = new RoleEntity();
        role.setId(id);
        role.setRoleCode(roleCode);
        return role;
    }

}
