package com.classdeduction.system.common.time;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class AppTime {

    public static final ZoneId APP_ZONE_ID = ZoneId.of("America/New_York");

    private AppTime() {
    }

    public static LocalDateTime nowLocalDateTime() {
        return LocalDateTime.now(APP_ZONE_ID);
    }

    public static OffsetDateTime nowOffsetDateTime() {
        return OffsetDateTime.now(APP_ZONE_ID);
    }
}
