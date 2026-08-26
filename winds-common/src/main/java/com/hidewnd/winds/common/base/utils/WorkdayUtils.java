package com.hidewnd.winds.common.base.utils;

import com.hidewnd.winds.common.base.entity.Holiday;

import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class WorkdayUtils {
    // 默认工作时间段：上午8:00-12:00，下午13:00-17:00
    private static WorkTimeConfig defaultWorkTime = new WorkTimeConfig(
            LocalTime.of(8, 0), LocalTime.of(12, 0),
            LocalTime.of(13, 0), LocalTime.of(17, 0)
    );

    /**
     * 方法一：计算工作日结束时间（考虑节假日）
     * @param startDate 开始时间
     * @param workDays 需要的工作日天数
     * @param holidays 节假日列表（包含补班和放假）
     * @return 结束时间
     */
    public static Date calculateWorkdayEndTime(Date startDate, int workDays, List<Holiday> holidays) {
        if (workDays <= 0) return startDate;

        LocalDateTime current = toLocalDateTime(startDate);
        long remainingSeconds = workDays * 24L * 3600; // 总秒数 = 天数 * 24h

        while (remainingSeconds > 0) {
            LocalDate currentDate = current.toLocalDate();

            if (isWorkday(currentDate, holidays)) {
                // 计算当日剩余秒数（到次日00:00）
                LocalDateTime endOfDay = currentDate.plusDays(1).atStartOfDay();
                long secondsInDay = Duration.between(current, endOfDay).getSeconds();

                if (remainingSeconds <= secondsInDay) {
                    current = current.plusSeconds(remainingSeconds);
                    break;
                } else {
                    remainingSeconds -= secondsInDay;
                    current = endOfDay; // 跳到下一天
                }
            } else {
                // 非工作日直接跳到下一天00:00
                current = currentDate.plusDays(1).atStartOfDay();
            }
        }
        return toDate(current);
    }

    /**
     * 方法二：考虑暂停时间的工作日计算
     * @param startDate 开始时间
     * @param workDays 需要的工作日天数
     * @param pauseStart 暂停开始时间
     * @param pauseEnd 暂停结束时间
     * @return 最新的结束时间
     */
    public static Date calculateWithPause(Date startDate, int workDays, Date pauseStart, Date pauseEnd) {
        // 计算理论工作秒数（按8小时/工作日）
        long totalWorkSeconds = workDays * 8L * 3600;
        LocalDateTime start = toLocalDateTime(startDate);

        // 步骤1：计算理论结束时间
        LocalDateTime endTime = addWorkSeconds(start, totalWorkSeconds, defaultWorkTime);

        // 步骤2：计算暂停期间的工作时间损失
        long pauseSeconds = calculateWorkSeconds(
                toLocalDateTime(pauseStart),
                toLocalDateTime(pauseEnd),
                defaultWorkTime
        );

        // 步骤3：在理论结束时间上补偿损失时间
        LocalDateTime actualEnd = addWorkSeconds(endTime, pauseSeconds, defaultWorkTime);
        return toDate(actualEnd);
    }

    // ================ 内部工具方法 ================

    /**
     * 判断指定日期是否是工作日
     * @param date 日期
     * @param holidays 节假日列表
     * @return true=工作日, false=非工作日
     */
    private static boolean isWorkday(LocalDate date, List<Holiday> holidays) {
        // 1. 检查节假日配置
        for (Holiday h : holidays) {
            LocalDate hDate = toLocalDateTime(h.getDate()).toLocalDate();
            if (date.isEqual(hDate)) {
                return h.getStatus() == 1; // 1补班=工作日, 2放假=非工作日
            }
        }

        // 2. 默认处理周末
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }

    /**
     * 在工作时间体系下增加秒数
     * @param start 开始时间
     * @param seconds 需要增加的工作秒数
     * @param config 工作时间配置
     * @return 结束时间
     */
    private static LocalDateTime addWorkSeconds(LocalDateTime start, long seconds, WorkTimeConfig config) {
        if (seconds <= 0) return start;

        LocalDateTime current = start;
        long remaining = seconds;

        while (remaining > 0) {
            LocalTime currentTime = current.toLocalTime();
            LocalDate currentDate = current.toLocalDate();

            // 获取当前日期的工作时间段
            LocalTime amStart = config.getMorningStart();
            LocalTime amEnd = config.getMorningEnd();
            LocalTime pmStart = config.getAfternoonStart();
            LocalTime pmEnd = config.getAfternoonEnd();

            // 情况1：在上午工作时段内
            if (isInTimeRange(currentTime, amStart, amEnd)) {
                long amRemain = Duration.between(currentTime, amEnd).getSeconds();
                if (remaining <= amRemain) {
                    return current.plusSeconds(remaining);
                } else {
                    remaining -= amRemain;
                    current = LocalDateTime.of(currentDate, amEnd);
                }
            }
            // 情况2：在下午工作时段内
            else if (isInTimeRange(currentTime, pmStart, pmEnd)) {
                long pmRemain = Duration.between(currentTime, pmEnd).getSeconds();
                if (remaining <= pmRemain) {
                    return current.plusSeconds(remaining);
                } else {
                    remaining -= pmRemain;
                    current = LocalDateTime.of(currentDate, pmEnd);
                }
            }
            // 情况3：在非工作时段
            else {
                // 如果早于上午工作时间，跳到上午起始时间
                if (currentTime.isBefore(amStart)) {
                    current = LocalDateTime.of(currentDate, amStart);
                }
                // 如果处于午休时间，跳到下午起始时间
                else if (currentTime.isBefore(pmStart)) {
                    current = LocalDateTime.of(currentDate, pmStart);
                }
                // 如果当天工作已结束，跳到下个工作日开始
                else {
                    current = LocalDateTime.of(currentDate.plusDays(1), amStart);
                }
            }
        }
        return current;
    }

    /**
     * 计算两个时间点之间的工作时间（秒）
     * @param start 开始时间
     * @param end 结束时间
     * @param config 工作时间配置
     * @return 工作秒数
     */
    private static long calculateWorkSeconds(LocalDateTime start, LocalDateTime end, WorkTimeConfig config) {
        if (!start.isBefore(end)) return 0;

        long totalSeconds = 0;
        LocalDateTime current = start;

        while (current.isBefore(end)) {
            LocalDate date = current.toLocalDate();
            LocalTime currentTime = current.toLocalTime();

            // 定义当日工作时间段
            LocalTime amStart = config.getMorningStart();
            LocalTime amEnd = config.getMorningEnd();
            LocalTime pmStart = config.getAfternoonStart();
            LocalTime pmEnd = config.getAfternoonEnd();

            // 计算上午段工作时间
            if (currentTime.isBefore(amEnd)) {
                LocalDateTime segStart = current.isAfter(LocalDateTime.of(date, amStart)) ?
                        current : LocalDateTime.of(date, amStart);
                LocalDateTime segEnd = LocalDateTime.of(date, amEnd);
                if (segEnd.isAfter(end)) segEnd = end;
                if (segStart.isBefore(segEnd)) {
                    totalSeconds += Duration.between(segStart, segEnd).getSeconds();
                }
            }

            // 计算下午段工作时间
            if (currentTime.isBefore(pmEnd)) {
                LocalDateTime segStart = current.isAfter(LocalDateTime.of(date, pmStart)) ?
                        current : LocalDateTime.of(date, pmStart);
                LocalDateTime segEnd = LocalDateTime.of(date, pmEnd);
                if (segEnd.isAfter(end)) segEnd = end;
                if (segStart.isBefore(segEnd)) {
                    totalSeconds += Duration.between(segStart, segEnd).getSeconds();
                }
            }

            // 跳到下一天
            current = LocalDateTime.of(date.plusDays(1), amStart);
            if (current.isAfter(end)) break;
        }
        return totalSeconds;
    }

    // ================ 时间转换工具 ================
    private static LocalDateTime toLocalDateTime(Date date) {
        return Instant.ofEpochMilli(date.getTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    private static Date toDate(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    private static boolean isInTimeRange(LocalTime time, LocalTime start, LocalTime end) {
        return !time.isBefore(start) && time.isBefore(end);
    }

    // ================ 配置类 ================
    /**
     * 工作时间配置类
     */
    public static class WorkTimeConfig {
        private final LocalTime morningStart;
        private final LocalTime morningEnd;
        private final LocalTime afternoonStart;
        private final LocalTime afternoonEnd;

        public WorkTimeConfig(LocalTime morningStart, LocalTime morningEnd,
                              LocalTime afternoonStart, LocalTime afternoonEnd) {
            this.morningStart = morningStart;
            this.morningEnd = morningEnd;
            this.afternoonStart = afternoonStart;
            this.afternoonEnd = afternoonEnd;
        }

        public LocalTime getMorningStart() { return morningStart; }
        public LocalTime getMorningEnd() { return morningEnd; }
        public LocalTime getAfternoonStart() { return afternoonStart; }
        public LocalTime getAfternoonEnd() { return afternoonEnd; }
    }
}
