package com.classdeduction.system.common.time;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class AppTime {

    public static final String APP_TIME_ZONE = "America/New_York";
    public static final ZoneId APP_ZONE_ID = ZoneId.of(APP_TIME_ZONE);

    private AppTime() {
    }

    public static LocalDateTime nowLocalDateTime() {
        return LocalDateTime.now(APP_ZONE_ID);
    }

    public static OffsetDateTime nowOffsetDateTime() {
        return OffsetDateTime.now(APP_ZONE_ID);
    }
}
