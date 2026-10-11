package com.li.lipicturecloud.support;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;

/** Test-only compatibility for the exact expressions used by SpaceAnalyzeServiceImpl. */
public final class H2MySqlAnalyticsFunctions {
    private static final WeekFields MYSQL_WEEK = WeekFields.of(DayOfWeek.SUNDAY, 7);

    private H2MySqlAnalyticsFunctions() { }

    public static String dateFormat(Timestamp value, String format) {
        if (value == null || format == null) return null;
        String pattern = switch (format) {
            case "%Y-%m-%d" -> "uuuu-MM-dd";
            case "%Y-%m" -> "uuuu-MM";
            default -> throw new IllegalArgumentException("Unsupported analytics fixture date format: " + format);
        };
        return value.toLocalDateTime().format(DateTimeFormatter.ofPattern(pattern));
    }

    public static Integer yearWeek(Timestamp value) {
        if (value == null) return null;
        // MySQL's one-argument YEARWEEK always uses mode 0: weeks start Sunday,
        // with the first Sunday of the year starting week 1 (not ISO weeks).
        // https://dev.mysql.com/doc/refman/8.4/en/date-and-time-functions.html#function_yearweek
        var date = value.toLocalDateTime().toLocalDate();
        return date.get(MYSQL_WEEK.weekBasedYear()) * 100 + date.get(MYSQL_WEEK.weekOfWeekBasedYear());
    }
}
