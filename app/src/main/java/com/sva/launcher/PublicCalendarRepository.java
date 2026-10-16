package com.sva.launcher;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Fetches a public iCalendar feed and expands common Google Calendar recurrence rules. */
final class PublicCalendarRepository {
    static final int MAX_EVENTS = 10000;
    private static final int MAX_FEED_BYTES = 6 * 1024 * 1024;
    private static final long DISPLAY_LOOKAHEAD_MS = 400L * 24L * 60L * 60L * 1000L;
    private static final long DISPLAY_LOOKBACK_MS = 400L * 24L * 60L * 60L * 1000L;

    private PublicCalendarRepository() { }

    static String normalizeUrl(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.regionMatches(true, 0, "webcal://", 0, 9)) value = "https://" + value.substring(9);
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme(), host = uri.getHost();
            if (scheme == null || host == null || uri.getUserInfo() != null
                    || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) return null;
            String hostValue = host.toLowerCase(Locale.ROOT);
            if (!hostValue.equals("google.com") && !hostValue.endsWith(".google.com")
                    && !hostValue.equals("googleusercontent.com") && !hostValue.endsWith(".googleusercontent.com")) return null;
            if (uri.getPath() == null || !uri.getPath().toLowerCase(Locale.ROOT).contains("/calendar/ical/")) return null;
            return "https".equalsIgnoreCase(scheme) ? uri.toString()
                    : new URI("https", uri.getUserInfo(), host, uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
        } catch (Exception ignored) { return null; }
    }

    static String download(String publicUrl) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(publicUrl).openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "text/calendar, text/plain;q=0.9, */*;q=0.5");
            connection.setRequestProperty("User-Agent", "SvaLauncher/1.0 (Android TV)");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IllegalStateException("Calendar feed returned HTTP " + status + ".");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0, count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_FEED_BYTES) throw new IllegalArgumentException("Calendar feed is larger than 6 MB.");
                    output.write(buffer, 0, count);
                }
                String feed = new String(output.toByteArray(), StandardCharsets.UTF_8);
                if (!feed.contains("BEGIN:VCALENDAR") || !feed.contains("END:VCALENDAR")) {
                    throw new IllegalArgumentException("This link did not return a public iCalendar feed.");
                }
                return feed;
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static List<Event> parse(String source, long nowMs) {
        ArrayList<Event> result = new ArrayList<>();
        if (source == null || source.trim().isEmpty()) return result;
        List<String> lines = unfold(source);
        ArrayList<String> eventLines = null;
        for (String line : lines) {
            if ("BEGIN:VEVENT".equalsIgnoreCase(line.trim())) eventLines = new ArrayList<>();
            else if ("END:VEVENT".equalsIgnoreCase(line.trim())) {
                if (eventLines != null) parseEvent(eventLines, nowMs, result);
                if (result.size() >= MAX_EVENTS) break;
                eventLines = null;
            } else if (eventLines != null) eventLines.add(line);
        }
        Collections.sort(result, Comparator.comparingLong(event -> event.startMs));
        return result;
    }

    private static List<String> unfold(String source) {
        ArrayList<String> lines = new ArrayList<>();
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        for (String line : normalized.split("\n")) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && !lines.isEmpty()) {
                int last = lines.size() - 1;
                lines.set(last, lines.get(last) + line.substring(1));
            } else lines.add(line);
        }
        return lines;
    }

    private static void parseEvent(List<String> lines, long nowMs, List<Event> output) {
        Map<String, Property> properties = new LinkedHashMap<>();
        for (String line : lines) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String[] head = line.substring(0, colon).split(";");
            Map<String, String> parameters = new HashMap<>();
            for (int i = 1; i < head.length; i++) {
                int equals = head[i].indexOf('=');
                if (equals > 0) parameters.put(head[i].substring(0, equals).toUpperCase(Locale.ROOT), head[i].substring(equals + 1).replace("\"", ""));
            }
            properties.put(head[0].toUpperCase(Locale.ROOT), new Property(parameters, line.substring(colon + 1)));
        }
        Property startProperty = properties.get("DTSTART");
        if (startProperty == null) return;
        if ("CANCELLED".equalsIgnoreCase(value(properties, "STATUS"))) return;
        String title = unescape(value(properties, "SUMMARY"));
        if (title.trim().isEmpty()) title = "Calendar event";
        try {
            ZonedDateTime start = parseDate(startProperty);
            boolean allDay = "DATE".equalsIgnoreCase(startProperty.parameters.get("VALUE"))
                    || startProperty.value.length() == 8;
            Property endProperty = properties.get("DTEND");
            ZonedDateTime end = endProperty == null ? (allDay ? start.plusDays(1) : start.plusHours(1)) : parseDate(endProperty);
            if (!end.isAfter(start)) return;
            Duration duration = Duration.between(start.toInstant(), end.toInstant());
            Property recurrence = properties.get("RRULE");
            if (recurrence == null) {
                addIfRelevant(title, start, end, allDay, nowMs, output);
            } else {
                expand(title, start, duration, allDay, recurrence.value, nowMs, output);
            }
        } catch (Exception ignored) { }
    }

    private static void expand(String title, ZonedDateTime first, Duration duration, boolean allDay, String rule,
                               long nowMs, List<Event> output) {
        Map<String, String> parts = new HashMap<>();
        for (String token : rule.split(";")) {
            int equals = token.indexOf('=');
            if (equals > 0) parts.put(token.substring(0, equals).toUpperCase(Locale.ROOT), token.substring(equals + 1).toUpperCase(Locale.ROOT));
        }
        String frequency = parts.get("FREQ");
        if (frequency == null) return;
        int interval = positiveInt(parts.get("INTERVAL"), 1);
        int count = positiveInt(parts.get("COUNT"), Integer.MAX_VALUE);
        ZonedDateTime until = parseUntil(parts.get("UNTIL"), first.getZone());
        Set<java.time.DayOfWeek> byDays = parseByDays(parts.get("BYDAY"));
        LocalDate firstDate = first.toLocalDate();
        LocalDate limit = Instant.ofEpochMilli(nowMs + DISPLAY_LOOKAHEAD_MS).atZone(first.getZone()).toLocalDate();
        long days = Math.max(0, ChronoUnit.DAYS.between(firstDate, limit));
        long firstOffset = 0;
        if (count == Integer.MAX_VALUE) {
            // Unbounded birthdays and anniversaries can start far before the display window.
            LocalDate earliest = Instant.ofEpochMilli(nowMs - DISPLAY_LOOKBACK_MS).atZone(first.getZone())
                    .toLocalDate().minusDays(duration.toDays() + 1);
            firstOffset = Math.max(0, ChronoUnit.DAYS.between(firstDate, earliest));
        } else if (days > 20000) days = 20000;
        int emitted = 0;
        for (long offset = firstOffset; offset <= days && emitted < count && output.size() < MAX_EVENTS; offset++) {
            LocalDate date = firstDate.plusDays(offset);
            if (!isOccurrenceDate(firstDate, date, frequency, interval, byDays)) continue;
            ZonedDateTime occurrence = ZonedDateTime.of(date, first.toLocalTime(), first.getZone());
            if (until != null && occurrence.isAfter(until)) break;
            emitted++;
            ZonedDateTime occurrenceEnd = occurrence.plus(duration);
            addIfRelevant(title, occurrence, occurrenceEnd, allDay, nowMs, output);
        }
    }

    private static boolean isOccurrenceDate(LocalDate first, LocalDate date, String frequency,
                                           int interval, Set<java.time.DayOfWeek> byDays) {
        long dayOffset = ChronoUnit.DAYS.between(first, date);
        switch (frequency) {
            case "DAILY": return dayOffset % interval == 0;
            case "WEEKLY":
                LocalDate firstWeek = first.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                LocalDate thisWeek = date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                long weekOffset = ChronoUnit.WEEKS.between(firstWeek, thisWeek);
                return weekOffset % interval == 0 && (byDays.isEmpty()
                        ? date.getDayOfWeek() == first.getDayOfWeek() : byDays.contains(date.getDayOfWeek()));
            case "MONTHLY": {
                long monthOffset = ChronoUnit.MONTHS.between(first.withDayOfMonth(1), date.withDayOfMonth(1));
                return monthOffset % interval == 0 && date.getDayOfMonth() == first.getDayOfMonth();
            }
            case "YEARLY":
                return (date.getYear() - first.getYear()) % interval == 0
                        && date.getMonth() == first.getMonth() && date.getDayOfMonth() == first.getDayOfMonth();
            default: return false;
        }
    }

    private static Set<java.time.DayOfWeek> parseByDays(String raw) {
        HashSet<java.time.DayOfWeek> result = new HashSet<>();
        if (raw == null) return result;
        for (String token : raw.split(",")) {
            String day = token.length() >= 2 ? token.substring(token.length() - 2) : token;
            switch (day) {
                case "MO": result.add(java.time.DayOfWeek.MONDAY); break;
                case "TU": result.add(java.time.DayOfWeek.TUESDAY); break;
                case "WE": result.add(java.time.DayOfWeek.WEDNESDAY); break;
                case "TH": result.add(java.time.DayOfWeek.THURSDAY); break;
                case "FR": result.add(java.time.DayOfWeek.FRIDAY); break;
                case "SA": result.add(java.time.DayOfWeek.SATURDAY); break;
                case "SU": result.add(java.time.DayOfWeek.SUNDAY); break;
                default: return Collections.emptySet();
            }
        }
        return result;
    }

    private static int positiveInt(String value, int fallback) {
        try { int parsed = Integer.parseInt(value); return parsed > 0 ? parsed : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private static ZonedDateTime parseUntil(String value, ZoneId fallbackZone) {
        if (value == null) return null;
        try {
            if (value.endsWith("Z")) return parseDateValue(value, ZoneOffset.UTC, false);
            if (value.length() == 8) return parseDateValue(value, fallbackZone, true).with(LocalTime.MAX);
            return parseDateValue(value, fallbackZone, false);
        } catch (Exception ignored) { return null; }
    }

    private static ZonedDateTime parseDate(Property property) {
        String tzid = property.parameters.get("TZID");
        ZoneId zone = tzid == null || tzid.isEmpty() ? ZoneId.systemDefault() : safeZone(tzid);
        boolean allDay = "DATE".equalsIgnoreCase(property.parameters.get("VALUE")) || property.value.length() == 8;
        if (property.value.endsWith("Z")) zone = ZoneOffset.UTC;
        return parseDateValue(property.value, zone, allDay);
    }

    private static ZoneId safeZone(String value) {
        try { return ZoneId.of(value); } catch (Exception ignored) { return ZoneId.systemDefault(); }
    }

    private static ZonedDateTime parseDateValue(String value, ZoneId zone, boolean allDay) {
        if (allDay) {
            LocalDate date = LocalDate.parse(value.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
            return date.atStartOfDay(zone);
        }
        String format = value.endsWith("Z") ? "uuuuMMdd'T'HHmmssX" : value.length() == 13 ? "uuuuMMdd'T'HHmm" : "uuuuMMdd'T'HHmmss";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format, Locale.ROOT);
        if (value.endsWith("Z")) return java.time.OffsetDateTime.parse(value, formatter).atZoneSameInstant(zone);
        LocalDateTime dateTime = LocalDateTime.parse(value, formatter);
        return dateTime.atZone(zone);
    }

    private static void addIfRelevant(String title, ZonedDateTime start, ZonedDateTime end, boolean allDay,
                                      long nowMs, List<Event> output) {
        long startMs = start.toInstant().toEpochMilli(), endMs = end.toInstant().toEpochMilli();
        if (endMs <= nowMs - DISPLAY_LOOKBACK_MS || startMs > nowMs + DISPLAY_LOOKAHEAD_MS) return;
        output.add(new Event(title, startMs, endMs, allDay));
    }

    private static String value(Map<String, Property> properties, String key) {
        Property property = properties.get(key);
        return property == null ? "" : property.value;
    }

    private static String unescape(String value) {
        return value.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",")
                .replace("\\;", ";").replace("\\\\", "\\");
    }

    private static final class Property {
        final Map<String, String> parameters;
        final String value;
        Property(Map<String, String> parameters, String value) { this.parameters = parameters; this.value = value; }
    }

    static final class Event {
        final String title;
        final long startMs, endMs;
        final boolean allDay;
        Event(String title, long startMs, long endMs) {
            this(title, startMs, endMs, false);
        }
        Event(String title, long startMs, long endMs, boolean allDay) {
            this.title = title;
            this.startMs = startMs;
            this.endMs = endMs;
            this.allDay = allDay;
        }
    }
}
