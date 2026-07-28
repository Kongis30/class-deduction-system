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
import com.classdeduction.system.modules.base.entity.CourseProductScheduleSlotEntity;
import com.classdeduction.system.modules.base.entity.CampusEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.CampusMapper;
import com.classdeduction.system.modules.base.mapper.CoursePriceOptionMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductScheduleSlotMapper;
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
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentMethodMapper;
import com.classdeduction.system.modules.enrollment.mapper.PaymentRecordMapper;
import com.classdeduction.system.modules.enrollment.mapper.ReceiptRecordMapper;
import com.classdeduction.system.modules.enrollment.service.impl.RegistrationIntakeServiceImpl;
import com.classdeduction.system.modules.enrollment.vo.RegistrationIntakeResultVO;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.vo.ScheduleConflictDetailVO;
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
    private CourseProductScheduleSlotMapper courseProductScheduleSlotMapper;

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private EnrollmentOrderMapper enrollmentOrderMapper;

    @Mock
    private EnrollmentOrderItemMapper enrollmentOrderItemMapper;

    @Mock
    private CoursePackageMapper coursePackageMapper;

    @Mock
    private PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;

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
    private ArgumentCaptor<LessonOccurrenceEntity> occurrenceCaptor;

    @Captor
    private ArgumentCaptor<CoursePackageEntity> coursePackageCaptor;

    @Captor
    private ArgumentCaptor<EnrollmentOrderItemEntity> orderItemCaptor;

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
                courseProductScheduleSlotMapper,
                teacherMapper,
                enrollmentOrderMapper,
                enrollmentOrderItemMapper,
                coursePackageMapper,
                packageTeacherAssignmentMapper,
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
    void shouldCreateOneOnOneOccurrenceAndStudentAccountFromIntake() {
        stubSuccessfulIntake(false);

        RegistrationIntakeResultVO result = registrationIntakeService.submitIntake(buildOneOnOneRequest());

        verify(lessonOccurrenceMapper).insert(occurrenceCaptor.capture());
        verify(coursePackageMapper).insert(coursePackageCaptor.capture());
        verify(enrollmentOrderItemMapper).insert(orderItemCaptor.capture());
        LessonOccurrenceEntity occurrence = occurrenceCaptor.getValue();
        assertThat(occurrence.getPackageId()).isEqualTo(3001L);
        assertThat(occurrence.getStudentId()).isEqualTo(101L);
        assertThat(occurrence.getPlannedTeacherId()).isEqualTo(301L);
        assertThat(occurrence.getPlannedStartAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 15, 30));
        assertThat(occurrence.getPlannedEndAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 17, 0));
        assertThat(occurrence.getPlannedUnits()).isEqualTo(3);
        assertThat(occurrence.getPlanningStatus()).isEqualTo("SCHEDULED");
        assertThat(coursePackageCaptor.getValue().getTotalUnits()).isEqualTo(20);
        assertThat(coursePackageCaptor.getValue().getRemainingUnits()).isEqualTo(20);
        assertThat(coursePackageCaptor.getValue().getOriginalAmount()).isEqualByComparingTo("800.00");
        assertThat(orderItemCaptor.getValue().getOriginalPriceAmount()).isEqualByComparingTo("800.00");
        assertThat(orderItemCaptor.getValue().getPriceAmount()).isEqualByComparingTo("800.00");
        assertThat(orderItemCaptor.getValue().getOneOnOneUnits()).isEqualTo(20);
        assertThat(orderItemCaptor.getValue().getInitialLessonStartAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 15, 30));
        assertThat(orderItemCaptor.getValue().getInitialLessonEndAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 17, 0));

        verify(userAccountMapper).insert(userAccountCaptor.capture());
        assertThat(userAccountCaptor.getValue().getUsername()).isEqualTo("jint@jin");
        assertThat(result.studentAccount().username()).isEqualTo("jint@jin");
    }

    @Test
    void shouldCreateOneOnOneCourseWithoutInitialTeacherAssignment() {
        stubSuccessfulIntake(false);
        RegistrationIntakeRequest request = buildOneOnOneRequest();
        request.getItems().get(0).setTeacherId(null);

        registrationIntakeService.submitIntake(request);

        verify(lessonOccurrenceMapper).insert(occurrenceCaptor.capture());
        verify(packageTeacherAssignmentMapper, never()).insert(any(com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity.class));
        assertThat(occurrenceCaptor.getValue().getPlannedTeacherId()).isNull();
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
        RegistrationIntakeRequest request = buildOneOnOneRequest();
        request.getItems().get(0).setInitialLessonStartAt(null);
        request.getItems().get(0).setInitialLessonEndAt(null);

        RegistrationIntakeResultVO result = registrationIntakeService.submitIntake(request);

        assertThat(result.courseArtifacts()).singleElement()
                .extracting(RegistrationIntakeResultVO.CourseArtifactVO::packageId)
                .isEqualTo(3001L);
        verify(lessonOccurrenceMapper, never()).insert(any(LessonOccurrenceEntity.class));
    }

    @Test
    void shouldDeriveInitialLessonEndFromProductDefaultDuration() {
        stubSuccessfulIntake(true);
        RegistrationIntakeRequest request = buildOneOnOneRequest();
        request.getItems().get(0).setInitialLessonEndAt(LocalDateTime.of(2026, 9, 1, 20, 0));

        registrationIntakeService.submitIntake(request);

        verify(lessonOccurrenceMapper).insert(occurrenceCaptor.capture());
        // Product defaultLessonUnits=3, so the 15:30 start always produces a 17:00 end.
        assertThat(occurrenceCaptor.getValue().getPlannedEndAt())
                .isEqualTo(LocalDateTime.of(2026, 9, 1, 17, 0));
        assertThat(occurrenceCaptor.getValue().getPlannedUnits()).isEqualTo(3);
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
    void shouldRejectInitialOneOnOneLessonWhenStudentScheduleConflicts() {
        stubSuccessfulIntakeUntilInitialConflict();
        LessonOccurrenceEntity existing = new LessonOccurrenceEntity();
        existing.setId(9001L);
        existing.setLessonNo("O2O-CONFLICT");
        existing.setPlannedStartAt(LocalDateTime.of(2026, 9, 1, 16, 0));
        existing.setPlannedEndAt(LocalDateTime.of(2026, 9, 1, 17, 30));
        when(lessonOccurrenceMapper.selectList(any()))
                .thenReturn(Collections.singletonList(existing))
                .thenReturn(Collections.emptyList());
        when(studentMapper.selectById(101L)).thenReturn(buildExistingStudent());

        assertThatThrownBy(() -> registrationIntakeService.submitIntake(buildOneOnOneRequest()))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException businessError = (BusinessException) error;
                    assertThat(businessError.getCode()).isEqualTo("SCHEDULE_CONFLICT");
                    assertThat((java.util.List<?>) businessError.getData())
                            .singleElement()
                            .satisfies(detail -> {
                                ScheduleConflictDetailVO conflict = (ScheduleConflictDetailVO) detail;
                                assertThat(conflict.resourceType()).isEqualTo("STUDENT");
                                assertThat(conflict.resourceId()).isEqualTo(101L);
                                assertThat(conflict.resourceName()).isEqualTo("Jin Tang");
                                assertThat(conflict.existingOccurrenceId()).isEqualTo(9001L);
                            });
                });
    }

    @Test
    void shouldRejectGroupClassIntakeWhenStudentScheduleConflicts() {
        stubStudentCreationOnly();
        CoursePriceOptionEntity option = buildOption("15:30-17:00");
        option.setCourseType("GROUP");
        option.setRegisterClass("Group class");
        CourseProductEntity product = buildProduct();
        product.setTeachingMode("GROUP");
        product.setBillingType("PER_SESSION");
        product.setStartDate(LocalDate.of(2026, 9, 1));
        product.setTotalSessions(1);
        product.setDefaultTeacherId(301L);
        product.setDefaultClassroomId(401L);
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(501L);
        classGroup.setProductId(202L);
        classGroup.setStatus("ACTIVE");
        classGroup.setTeacherId(301L);
        classGroup.setClassroomId(401L);
        classGroup.setBillingType("PER_SESSION");
        classGroup.setStartDate(LocalDate.of(2026, 9, 1));
        classGroup.setTotalSessions(1);
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
        when(classEnrollmentMapper.selectCount(any())).thenReturn(0L);
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

    private void stubSuccessfulIntake(boolean duplicateStudentUsername) {
        stubSuccessfulIntake(duplicateStudentUsername, "15:30-17:00");
    }

    private void stubSuccessfulIntake(boolean duplicateStudentUsername, String timeSlotLabel) {
        when(studentNoGenerator.generate()).thenReturn("STU202609010001");
        when(studentMapper.selectReusableStudent("Jin Tang", "9175550101")).thenReturn(null);
        when(coursePriceOptionMapper.selectById(201L)).thenReturn(buildOption(timeSlotLabel));
        when(courseProductMapper.selectById(202L)).thenReturn(buildProduct());
        lenient().when(teacherMapper.selectById(301L)).thenReturn(buildTeacher());
        when(campusMapper.selectById(1L)).thenReturn(buildCampus());
        when(jsonHelper.toJson(any())).thenReturn("{}");
        when(passwordEncoder.encode("9175550101")).thenReturn("encoded");
        when(roleMapper.selectOne(any())).thenReturn(buildRole(1L, "STUDENT"), buildRole(2L, "TEACHER"));
        when(userAccountMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(buildExistingTeacherAccount());
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
        lenient().doAnswer(invocation -> {
            LessonOccurrenceEntity occurrence = invocation.getArgument(0);
            occurrence.setId(4001L);
            return 1;
        }).when(lessonOccurrenceMapper).insert(any(LessonOccurrenceEntity.class));
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

    private void stubSuccessfulIntakeUntilInitialConflict() {
        when(studentNoGenerator.generate()).thenReturn("STU202609010001");
        when(studentMapper.selectReusableStudent("Jin Tang", "9175550101")).thenReturn(null);
        when(coursePriceOptionMapper.selectById(201L)).thenReturn(buildOption("15:30-17:00"));
        when(courseProductMapper.selectById(202L)).thenReturn(buildProduct());
        when(teacherMapper.selectById(301L)).thenReturn(buildTeacher());
        when(campusMapper.selectById(1L)).thenReturn(buildCampus());
        when(jsonHelper.toJson(any())).thenReturn("{}");
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
    }

    private void stubStudentCreationOnly() {
        when(studentNoGenerator.generate()).thenReturn("STU202609010001");
        when(studentMapper.selectReusableStudent("Jin Tang", "9175550101")).thenReturn(null);
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
        item.setTeacherId(null);
        item.setInitialLessonStartAt(null);
        item.setInitialLessonEndAt(null);
        item.setClassId(501L);
        item.setOneOnOneUnits(0);
        item.setSessions(1);
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
        item.setTeacherId(301L);
        item.setInitialLessonStartAt(LocalDateTime.of(2026, 9, 1, 15, 30));
        item.setInitialLessonEndAt(LocalDateTime.of(2026, 9, 1, 17, 0));
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
        product.setDefaultLessonUnits(3);
        product.setStatus("ACTIVE");
        return product;
    }

    private TeacherEntity buildTeacher() {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(301L);
        teacher.setTeacherNo("T301");
        teacher.setTeacherName("Ms Lee");
        teacher.setMobile("9175550202");
        teacher.setStatus("ACTIVE");
        return teacher;
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

    private UserAccountEntity buildExistingTeacherAccount() {
        UserAccountEntity account = new UserAccountEntity();
        account.setId(7001L);
        account.setUsername("teacher");
        account.setUserType("TEACHER");
        account.setTeacherId(301L);
        return account;
    }
}
