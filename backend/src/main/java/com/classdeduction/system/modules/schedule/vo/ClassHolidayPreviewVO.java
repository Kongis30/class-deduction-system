package com.classdeduction.system.modules.schedule.vo;

import java.util.List;

public record ClassHolidayPreviewVO(int affectedOccurrenceCount,
                                    int affectedSessionEnrollmentCount,
                                    List<String> conflicts) {
}
