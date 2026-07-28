package com.classdeduction.system.modules.auth.interceptor;

import com.classdeduction.system.modules.auth.annotation.RequireRoles;
import com.classdeduction.system.modules.base.controller.ClassroomController;
import com.classdeduction.system.modules.base.controller.StudentController;
import com.classdeduction.system.modules.base.controller.TeacherController;
import com.classdeduction.system.modules.enrollment.controller.CoursePackageController;
import com.classdeduction.system.modules.lesson.controller.LessonRecordController;
import com.classdeduction.system.modules.schedule.controller.ClassGroupController;
import com.classdeduction.system.modules.schedule.controller.ScheduleAdjustmentController;
import com.classdeduction.system.modules.systemconfig.controller.NotificationConfigController;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerRoleBoundaryTest {

    @Test
    void managementControllersShouldNotExposeGlobalDataToSelfServiceRoles() {
        assertClassRoles(StudentController.class, "ADMIN", "STAFF");
        assertClassRoles(TeacherController.class, "ADMIN", "STAFF");
        assertClassRoles(ClassroomController.class, "ADMIN", "STAFF");
        assertClassRoles(CoursePackageController.class, "ADMIN", "STAFF");
        assertClassRoles(ScheduleAdjustmentController.class, "ADMIN", "STAFF");
        assertClassRoles(ClassGroupController.class, "ADMIN", "STAFF");
    }

    @Test
    void notificationConfigShouldBeAdminOnly() {
        // SMTP 配置含授权码等敏感信息，任意登录用户不得读写或触发测试邮件。
        assertClassRoles(NotificationConfigController.class, "ADMIN");
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
        assertThat(Set.of(confirm.value())).containsExactlyInAnyOrder("ADMIN", "STAFF", "TEACHER");
        assertThat(Set.of(classConfirm.value())).containsExactlyInAnyOrder("ADMIN", "STAFF", "TEACHER");
        assertThat(Set.of(revoke.value())).containsExactly("ADMIN");
    }

    private void assertClassRoles(Class<?> controller, String... expectedRoles) {
        assertThat(Set.of(controller.getAnnotation(RequireRoles.class).value()))
                .containsExactlyInAnyOrder(expectedRoles);
    }
}
