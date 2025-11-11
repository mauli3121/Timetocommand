package com.mauli.timescheduler;

import java.time.*;
import java.util.Locale;

public class ScheduledCommand {
    public enum Repeat {
        NONE, DAILY, WEEKLY, MONTHLY;

        public static Repeat fromString(String s) {
            if (s == null) return NONE;
            switch (s.trim().toUpperCase(Locale.ROOT)) {
                case "DAILY": return DAILY;
                case "WEEKLY": return WEEKLY;
                case "MONTHLY": return MONTHLY;
                default: return NONE;
            }
        }
    }

    private final String id;
    private final String command;
    private final String runAs;
    private final LocalDateTime baseDateTime;
    private final ZoneId zoneId;
    private final Repeat repeat;
    private ZonedDateTime nextRunAt;

    public ScheduledCommand(String id, String command, String runAs, LocalDateTime baseDateTime, ZoneId zoneId, Repeat repeat) {
        this.id = id;
        this.command = command;
        this.runAs = runAs;
        this.baseDateTime = baseDateTime;
        this.zoneId = zoneId;
        this.repeat = repeat;
    }

    public String getId() { return id; }
    public String getCommand() { return command; }
    public String getRunAs() { return runAs; }
    public ZoneId getZoneId() { return zoneId; }
    public Repeat getRepeat() { return repeat; }
    public ZonedDateTime getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(ZonedDateTime zdt) { this.nextRunAt = zdt; }

    public void computeNextRun(ZonedDateTime referenceNow) {
        ZonedDateTime candidate = baseDateTime.atZone(zoneId);

        if (repeat == Repeat.NONE) {
            this.nextRunAt = candidate;
            return;
        }

        while (!candidate.isAfter(referenceNow)) {
            switch (repeat) {
                case DAILY:
                    candidate = candidate.plusDays(1);
                    break;
                case WEEKLY:
                    candidate = candidate.plusWeeks(1);
                    break;
                case MONTHLY:
                    candidate = candidate.plusMonths(1);
                    break;
                default:
                    break;
            }
        }
        this.nextRunAt = candidate;
    }
}
