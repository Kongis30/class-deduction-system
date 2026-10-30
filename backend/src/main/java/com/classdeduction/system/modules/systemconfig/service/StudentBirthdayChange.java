package com.classdeduction.system.modules.systemconfig.service;

import com.classdeduction.system.modules.base.entity.StudentEntity;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Objects;

/** 学生写入入口使用快照比较，只有生日消息相关资料变化时才发布事件。 */
public record StudentBirthdayChange() {
    public record Snapshot(LocalDate birthday, String name, String grade, Long campusId, String status) {}

    public static Snapshot snapshot(StudentEntity student) {
        return student == null ? null : new Snapshot(student.getBirthDate(), student.getStudentName(),
                student.getGrade(), student.getCampusId(), student.getStatus());
    }

    public static void publishIfChanged(ApplicationEventPublisher publisher, Snapshot before, StudentEntity after) {
        Snapshot next = snapshot(after);
        if (publisher != null && !Objects.equals(before, next)
                && ((before != null && before.birthday() != null) || (next != null && next.birthday() != null))) {
            publisher.publishEvent(new StudentBirthdayChange());
        }
    }
}
