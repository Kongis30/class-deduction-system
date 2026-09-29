package com.classdeduction.system.modules.auth.interceptor;

import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.base.controller.ClassroomController;
import com.classdeduction.system.modules.base.controller.CampusController;
import com.classdeduction.system.modules.base.controller.CoursePriceOptionController;
import com.classdeduction.system.modules.base.controller.CourseProductController;
import com.classdeduction.system.modules.base.controller.StudentController;
import com.classdeduction.system.modules.base.controller.TeacherController;
import com.classdeduction.system.modules.enrollment.controller.CoursePackageController;
import com.classdeduction.system.modules.enrollment.controller.DailyRegistrationController;
import com.classdeduction.system.modules.finance.controller.FinanceController;
import com.classdeduction.system.modules.lesson.controller.LessonRecordController;
import com.classdeduction.system.modules.schedule.controller.ClassGroupController;
import com.classdeduction.system.modules.schedule.controller.LessonOccurrenceController;
import com.classdeduction.system.modules.schedule.controller.ScheduleAdjustmentController;
import com.classdeduction.system.modules.systemconfig.controller.NotificationConfigController;
import com.classdeduction.system.modules.systemconfig.controller.NotificationTemplateController;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerRoleBoundaryTest {

    @Test
    void managementControllersShouldNotExposeGlobalDataToSelfServiceRoles() {
        assertClassRoles(StudentController.class, "ADMIN", "STAFF", "MANAGER");
        assertClassRoles(TeacherController.class, "ADMIN", "STAFF", "MANAGER");
        assertClassRoles(ClassroomController.class, "ADMIN", "STAFF", "MANAGER");
        assertClassRoles(CoursePackageController.class, "ADMIN", "STAFF", "MANAGER");
        assertClassRoles(ScheduleAdjustmentController.class, "ADMIN", "STAFF", "MANAGER");
        assertClassRoles(ClassGroupController.class, "ADMIN", "STAFF", "MANAGER");
    }

    @Test
    void teacherMayOnlyReachTheSingleLessonAdjustmentEndpoint() throws NoSuchMethodException {
        RequireRoles adjust = ScheduleAdjustmentController.class
                .getMethod("adjust", com.classdeduction.system.modules.schedule.dto.ScheduleAdjustmentRequest.class,
                        com.classdeduction.system.common.dto.CurrentUserResponse.class)
                .getAnnotation(RequireRoles.class);
        RequireRoles adjustWeekly = ScheduleAdjustmentController.class
                .getMethod("adjustWeekly", com.classdeduction.system.modules.schedule.dto.WeeklyScheduleAdjustmentRequest.class)
                .getAnnotation(RequireRoles.class);

        assertThat(Set.of(adjust.value())).contains("TEACHER");
        assertThat(Set.of(adjustWeekly.value())).containsExactlyInAnyOrder("ADMIN", "STAFF", "MANAGER");
    }

    @Test
    void notificationConfigShouldBeAdminOnly() {
        // SMTP 配置含授权码等敏感信息，任意登录用户不得读写或触发测试邮件。
        assertClassRoles(NotificationConfigController.class, "ADMIN");
    }

    @Test
    void dailyRegistrationReportShouldExcludeManagers() {
        assertClassRoles(DailyRegistrationController.class, "ADMIN", "STAFF");
    }

    @Test
    void managerShouldNotAccessBackOfficeFinanceSystemOrCatalogManagement() throws NoSuchMethodException {
        assertMethodRoles(FinanceController.class, "listPayRates", Long.class, Long.class);
        assertMethodRoles(CourseProductController.class, "createCourseProduct",
                com.classdeduction.system.modules.base.dto.CourseProductSaveRequest.class);
        assertMethodRoles(CoursePriceOptionController.class, "pageOptions",
                com.classdeduction.system.modules.base.dto.CoursePriceOptionPageQuery.class);
        assertMethodRoles(CampusController.class, "createCampus",
                com.classdeduction.system.modules.base.dto.CampusSaveRequest.class);
        assertClassRoles(NotificationTemplateController.class, "ADMIN", "STAFF");
    }

    @Test
    void lessonMutationEndpointsShouldKeepDoubleConfirmationBoundary() throws NoSuchMethodException {
        RequireRoles confirm = LessonRecordController.class
                .getMethod("confirmLesson",
                        com.classdeduction.system.modules.lesson.dto.LessonRecordConfirmRequest.class,
                        com.classdeduction.system.common.dto.CurrentUserResponse.class)
                .getAnnotation(RequireRoles.class);
        RequireRoles classConfirm = ClassGroupController.class
                .getMethod("confirmLesson",
                        Long.class,
                        Long.class,
                        java.util.Map.class,
                        com.classdeduction.system.common.dto.CurrentUserResponse.class)
                .getAnnotation(RequireRoles.class);
        RequireRoles revoke = LessonRecordController.class
                .getMethod("revokeLesson", Long.class, com.classdeduction.system.common.dto.CurrentUserResponse.class)
                .getAnnotation(RequireRoles.class);

        // 普通确认接口需允许管理员/运营直确认，也允许老师初确认。
        assertThat(Set.of(confirm.value())).containsExactlyInAnyOrder("ADMIN", "STAFF", "MANAGER", "TEACHER");
        assertThat(Set.of(classConfirm.value())).containsExactlyInAnyOrder("ADMIN", "STAFF", "TEACHER");
        assertThat(Set.of(revoke.value())).containsExactly("ADMIN");
    }

    @Test
    void registrationClassOptionsShouldBeAvailableToManagers() throws NoSuchMethodException {
        RequireRoles listClasses = ClassGroupController.class
                .getMethod("listClasses", boolean.class, com.classdeduction.system.common.dto.CurrentUserResponse.class)
                .getAnnotation(RequireRoles.class);

        assertThat(Set.of(listClasses.value())).containsExactlyInAnyOrder("ADMIN", "STAFF", "MANAGER", "TEACHER");
    }

    @Test
    void oneOnOneDeductionShouldBeAvailableToManagers() throws NoSuchMethodException {
        RequireRoles listDeductions = LessonOccurrenceController.class
                .getMethod("listOneOnOneDeductions",
                        com.classdeduction.system.modules.schedule.dto.OneOnOneDeductionQuery.class,
                        com.classdeduction.system.common.dto.CurrentUserResponse.class)
                .getAnnotation(RequireRoles.class);
        RequireRoles confirmLesson = LessonRecordController.class
                .getMethod("confirmLesson",
                        com.classdeduction.system.modules.lesson.dto.LessonRecordConfirmRequest.class,
                        com.classdeduction.system.common.dto.CurrentUserResponse.class)
                .getAnnotation(RequireRoles.class);

        assertThat(Set.of(listDeductions.value())).containsExactlyInAnyOrder("ADMIN", "STAFF", "MANAGER", "TEACHER");
        assertThat(Set.of(confirmLesson.value())).containsExactlyInAnyOrder("ADMIN", "STAFF", "MANAGER", "TEACHER");
    }

    @Test
    void teacherCalendarShouldBeAvailableToManagers() {
        assertClassRoles(LessonOccurrenceController.class, "ADMIN", "STAFF", "MANAGER", "TEACHER", "STUDENT");
    }

    private void assertClassRoles(Class<?> controller, String... expectedRoles) {
        assertThat(Set.of(controller.getAnnotation(RequireRoles.class).value()))
                .containsExactlyInAnyOrder(expectedRoles);
    }

    private void assertMethodRoles(Class<?> controller, String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        RequireRoles roles = controller.getMethod(methodName, parameterTypes).getAnnotation(RequireRoles.class);
        assertThat(Set.of(roles.value())).containsExactlyInAnyOrder("ADMIN", "STAFF");
    }
}
