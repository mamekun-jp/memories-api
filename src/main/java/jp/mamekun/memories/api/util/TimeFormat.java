package jp.mamekun.memories.api.util;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Formats timestamps the way the iOS app parses them: ISO-8601 with milliseconds and offset. */
public final class TimeFormat {
    private static final DateTimeFormatter ISO_WITH_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private TimeFormat() {
    }

    public static String iso(ZonedDateTime time) {
        return time == null ? null : ISO_WITH_MILLIS.format(time);
    }
}
