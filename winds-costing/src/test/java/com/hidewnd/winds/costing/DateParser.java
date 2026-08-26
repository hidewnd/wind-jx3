package com.hidewnd.winds.costing;

import lombok.Getter;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Pattern;

public class DateParser {

    private static final Map<String, DayOfWeek> DAY_OF_WEEK_MAP = new HashMap<>();

    static {
        // 英文简写（小写）
        DAY_OF_WEEK_MAP.put("mon", DayOfWeek.MONDAY);
        DAY_OF_WEEK_MAP.put("tue", DayOfWeek.TUESDAY);
        DAY_OF_WEEK_MAP.put("wed", DayOfWeek.WEDNESDAY);
        DAY_OF_WEEK_MAP.put("thu", DayOfWeek.THURSDAY);
        DAY_OF_WEEK_MAP.put("fri", DayOfWeek.FRIDAY);
        DAY_OF_WEEK_MAP.put("sat", DayOfWeek.SATURDAY);
        DAY_OF_WEEK_MAP.put("sun", DayOfWeek.SUNDAY);

        // 英文全称（小写）
        DAY_OF_WEEK_MAP.put("monday", DayOfWeek.MONDAY);
        DAY_OF_WEEK_MAP.put("tuesday", DayOfWeek.TUESDAY);
        DAY_OF_WEEK_MAP.put("wednesday", DayOfWeek.WEDNESDAY);
        DAY_OF_WEEK_MAP.put("thursday", DayOfWeek.THURSDAY);
        DAY_OF_WEEK_MAP.put("friday", DayOfWeek.FRIDAY);
        DAY_OF_WEEK_MAP.put("saturday", DayOfWeek.SATURDAY);
        DAY_OF_WEEK_MAP.put("sunday", DayOfWeek.SUNDAY);

        // 中文常见表示
        DAY_OF_WEEK_MAP.put("周一", DayOfWeek.MONDAY);
        DAY_OF_WEEK_MAP.put("周二", DayOfWeek.TUESDAY);
        DAY_OF_WEEK_MAP.put("周三", DayOfWeek.WEDNESDAY);
        DAY_OF_WEEK_MAP.put("周四", DayOfWeek.THURSDAY);
        DAY_OF_WEEK_MAP.put("周五", DayOfWeek.FRIDAY);
        DAY_OF_WEEK_MAP.put("周六", DayOfWeek.SATURDAY);
        DAY_OF_WEEK_MAP.put("周日", DayOfWeek.SUNDAY);
        DAY_OF_WEEK_MAP.put("星期天", DayOfWeek.SUNDAY);
        DAY_OF_WEEK_MAP.put("星期一", DayOfWeek.MONDAY);
        DAY_OF_WEEK_MAP.put("星期二", DayOfWeek.TUESDAY);
        DAY_OF_WEEK_MAP.put("星期三", DayOfWeek.WEDNESDAY);
        DAY_OF_WEEK_MAP.put("星期四", DayOfWeek.THURSDAY);
        DAY_OF_WEEK_MAP.put("星期五", DayOfWeek.FRIDAY);
        DAY_OF_WEEK_MAP.put("星期六", DayOfWeek.SATURDAY);
        DAY_OF_WEEK_MAP.put("星期日", DayOfWeek.SUNDAY);
    }

    public static DateParm parse(Date date, String expression) {
        ParsedResult parsedResult = parseExpression(expression);
        if (parsedResult == null) {
            return null;
        }

        LocalDateTime baseDateTime = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDate baseDate = baseDateTime.toLocalDate();

        // 获取下一周的星期几
        LocalDate nextWeekDate = baseDate.with(TemporalAdjusters.next(parsedResult.dayOfWeek));

        LocalDateTime startDateTime = LocalDateTime.of(nextWeekDate, parsedResult.startTime);
        LocalDateTime endDateTime = LocalDateTime.of(nextWeekDate, parsedResult.endTime);

        if (!endDateTime.isAfter(startDateTime)) {
            return null; // 结束时间不晚于开始时间
        }

        return new DateParm(startDateTime, endDateTime);
    }

    private static ParsedResult parseExpression(String expression) {
        String[] parts = expression.split("\\s+", 2);
        if (parts.length != 2) {
            return null;
        }

        String dayPart = parts[0].trim().toLowerCase();
        DayOfWeek dayOfWeek = DAY_OF_WEEK_MAP.get(dayPart);
        if (dayOfWeek == null) {
            // 尝试原样匹配中文
            dayOfWeek = DAY_OF_WEEK_MAP.get(parts[0].trim());
            if (dayOfWeek == null) {
                return null;
            }
        }

        String timeRange = parts[1].trim();
        if (!Pattern.matches("^\\d{1,2}:\\d{2}(?::\\d{2})?-\\d{1,2}:\\d{2}(?::\\d{2})?$", timeRange)) {
            return null;
        }

        String[] timeParts = timeRange.split("-");
        if (timeParts.length != 2) {
            return null;
        }

        LocalTime startTime = parseTime(timeParts[0].trim());
        LocalTime endTime = parseTime(timeParts[1].trim());
        if (startTime == null || endTime == null) {
            return null;
        }

        return new ParsedResult(dayOfWeek, startTime, endTime);
    }

    private static LocalTime parseTime(String timeStr) {
        List<DateTimeFormatter> formatters = Arrays.asList(
                DateTimeFormatter.ofPattern("H:mm:ss"),
                DateTimeFormatter.ofPattern("H:mm")
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalTime.parse(timeStr, formatter);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private static class ParsedResult {
        final DayOfWeek dayOfWeek;
        final LocalTime startTime;
        final LocalTime endTime;

        ParsedResult(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
            this.dayOfWeek = dayOfWeek;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

    @Getter
    public static class DateParm {
        private final LocalDateTime timeStart;
        private final LocalDateTime timeEnd;

        public DateParm(LocalDateTime timeStart, LocalDateTime timeEnd) {
            this.timeStart = timeStart;
            this.timeEnd = timeEnd;
        }

    }
}