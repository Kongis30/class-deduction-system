package com.classdeduction.system.modules.schedule.util;

import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassSlotRotationTest {

    @Test
    void alternatesTeacherSubjectAndClassroomByActualLessonAfterHoliday() {
        ClassGroupScheduleSlotEntity slot = new ClassGroupScheduleSlotEntity();
        slot.setTeacherId(10L);
        slot.setSubjectName("English");
        slot.setClassroomId(100L);
        slot.setDeliveryMode("IN_PERSON");
        slot.setAlternateTeacherId(20L);
        slot.setAlternateSubjectName("Math");
        slot.setAlternateClassroomId(200L);
        slot.setAlternateDeliveryMode("IN_PERSON");
        slot.setRotationAnchorDate(LocalDate.of(2026, 9, 12));
        slot.setWeekday(6);
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(12, 0));
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 9, 19));

        ClassSlotRotation.Assignment first = ClassSlotRotation.resolve(slot,
                LocalDate.of(2026, 9, 12), null, holidays::contains);
        ClassSlotRotation.Assignment afterHoliday = ClassSlotRotation.resolve(slot,
                LocalDate.of(2026, 9, 26), null, holidays::contains);

        assertFalse(first.alternate());
        assertEquals(10L, first.teacherId());
        assertEquals("English", first.subjectName());
        assertEquals(100L, first.classroomId());
        assertEquals("IN_PERSON", first.deliveryMode());
        assertTrue(afterHoliday.alternate());
        assertEquals(20L, afterHoliday.teacherId());
        assertEquals("Math", afterHoliday.subjectName());
        assertEquals(200L, afterHoliday.classroomId());
        assertEquals("IN_PERSON", afterHoliday.deliveryMode());
    }
}
