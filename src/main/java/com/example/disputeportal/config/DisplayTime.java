package com.example.disputeportal.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Shows times to people in South African Standard Time (SAST, UTC+2, no daylight saving).
 *
 * Everything is stored in UTC (see AppConfig). This is the one place that converts for display, used from templates as
 * {@code @displayTime.format(value, 'd MMM yyyy HH:mm')}. Patterns that include the time of day get " SAST" added, so
 * nobody has to guess which zone a time is in. It also converts the admin queue's date filter, so "from 24 Oct" means
 * midnight in South Africa, not midnight in UTC.
 */
@Component("displayTime")
public class DisplayTime {

    /** The zone shown to users. */
    static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    /** Formats a stored (UTC) time in SAST. Null gives an empty string. */
    public String format(LocalDateTime utc, String pattern) {
        if (utc == null) {
            return "";
        }
        String text = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
                .format(utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZONE));
        return pattern.contains("H") ? text + " SAST" : text;
    }

    /** The UTC time at which the given SAST calendar day begins, for comparing with stored times. */
    public static LocalDateTime startOfDayUtc(LocalDate sastDay) {
        return sastDay.atStartOfDay(ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
