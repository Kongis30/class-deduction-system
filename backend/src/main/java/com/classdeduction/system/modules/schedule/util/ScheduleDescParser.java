package com.classdeduction.system.modules.schedule.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 班课 schedule_desc 文本解析工具。
 * <p>
 * 格式约定："{weekdays} {startTime}-{endTime}"
 * 示例： "Mon/Wed 9:00-10:30" → [{weekday=1, start="09:00", end="10:30"}, {weekday=3, start="09:00", end="10:30"}]
 *       "Sat 10:00-12:00"    → [{weekday=6, start="10:00", end="12:00"}]
 */
public class ScheduleDescParser {

    private static final Map<String, Integer> WEEKDAY_MAP = new HashMap<>();

    static {
        WEEKDAY_MAP.put("MON", 1); WEEKDAY_MAP.put("Mon", 1); WEEKDAY_MAP.put("mon", 1);
        WEEKDAY_MAP.put("TUE", 2); WEEKDAY_MAP.put("Tue", 2); WEEKDAY_MAP.put("tue", 2);
        WEEKDAY_MAP.put("WED", 3); WEEKDAY_MAP.put("Wed", 3); WEEKDAY_MAP.put("wed", 3);
        WEEKDAY_MAP.put("THU", 4); WEEKDAY_MAP.put("Thu", 4); WEEKDAY_MAP.put("thu", 4);
        WEEKDAY_MAP.put("FRI", 5); WEEKDAY_MAP.put("Fri", 5); WEEKDAY_MAP.put("fri", 5);
        WEEKDAY_MAP.put("SAT", 6); WEEKDAY_MAP.put("Sat", 6); WEEKDAY_MAP.put("sat", 6);
        WEEKDAY_MAP.put("SUN", 7); WEEKDAY_MAP.put("Sun", 7); WEEKDAY_MAP.put("sun", 7);
    }

    /**
     * 解析后的时段。
     */
    public record ParsedSlot(int weekday, String startTime, String endTime, int plannedUnits) {
    }

    /**
     * 解析 scheduleDesc 文本，返回时段列表。
     *
     * @param scheduleDesc 排课描述文本，如 "Mon/Wed 9:00-10:30"
     * @return 解析后的时段列表，解析失败返回空列表
     */
    public static List<ParsedSlot> parse(String scheduleDesc) {
        List<ParsedSlot> result = new ArrayList<>();
        if (scheduleDesc == null || scheduleDesc.isBlank()) {
            return result;
        }

        String text = scheduleDesc.trim();
        // 分割：最后一部分是时间范围，前面的是星期
        int lastSpace = text.lastIndexOf(' ');
        if (lastSpace <= 0 || lastSpace >= text.length() - 1) {
            return result;
        }

        String weekdayPart = text.substring(0, lastSpace).trim();
        String timePart = text.substring(lastSpace + 1).trim();

        // 解析时间范围 "9:00-10:30"
        String[] timeParts = timePart.split("-");
        if (timeParts.length != 2) {
            return result;
        }
        String startTime = normalizeTime(timeParts[0].trim());
        String endTime = normalizeTime(timeParts[1].trim());
        if (startTime == null || endTime == null) {
            return result;
        }

        // 计算 plannedUnits (每30分钟=1单位)
        int plannedUnits = calculateUnits(startTime, endTime);
        if (plannedUnits <= 0) {
            return result;
        }

        // 解析星期部分：以 "/" 或 "," 或空格分隔
        String[] weekdayTokens = weekdayPart.split("[/, ]+");
        for (String token : weekdayTokens) {
            Integer wd = WEEKDAY_MAP.get(token.trim());
            if (wd != null) {
                result.add(new ParsedSlot(wd, startTime, endTime, plannedUnits));
            }
        }

        return result;
    }

    /**
     * 将 "9:00" 格式规范化为 "09:00"。
     */
    private static String normalizeTime(String time) {
        String[] parts = time.split(":");
        if (parts.length != 2) {
            return null;
        }
        try {
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            if (h < 0 || h > 24 || m < 0 || m > 59 || (h == 24 && m != 0)) {
                return null;
            }
            return String.format("%02d:%02d", h, m);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 根据 startTime 和 endTime 计算 half-hour units。
     */
    private static int calculateUnits(String startTime, String endTime) {
        try {
            String[] startParts = startTime.split(":");
            String[] endParts = endTime.split(":");
            int startMinutes = Integer.parseInt(startParts[0]) * 60 + Integer.parseInt(startParts[1]);
            int endMinutes = Integer.parseInt(endParts[0]) * 60 + Integer.parseInt(endParts[1]);
            if (endMinutes == 0 && startMinutes > 0) {
                endMinutes = 24 * 60;
            }
            int diff = endMinutes - startMinutes;
            return diff > 0 ? diff / 30 : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
