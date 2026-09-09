package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.SpatialCalendarWindowOptions.Unit;
import cn.superhuang.data.scalpel.contract.task.SpatialTemporalSlicing;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.api.java.UDF1;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Per-observation calendar expansion, bounded independently of the input table size. */
final class CalendarTimeWindows implements UDF1<Long, List<Row>> {
    static final int MAX_CANDIDATE_WINDOWS = 4096;
    private static final long DAY = 86_400_000_000L;
    private final ZonedDateTime reference;
    private final long width, step, maximumWidthMicros;
    private final Unit widthUnit, stepUnit;

    CalendarTimeWindows(ZonedDateTime reference, long width, Unit widthUnit, long step, Unit stepUnit) {
        this.reference = reference;
        this.width = width; this.widthUnit = widthUnit; this.step = step; this.stepUnit = stepUnit;
        long days = switch (widthUnit) {
            case DAYS -> width;
            case WEEKS -> Math.multiplyExact(width, 7);
            case MONTHS -> Math.multiplyExact(width, 31);
            case YEARS -> Math.multiplyExact(width, 366);
            default -> 0;
        };
        // Calendar elapsed duration differs by at most the full ZoneOffset span (-18h..+18h).
        maximumWidthMicros = days == 0 ? Math.multiplyExact(width, fixedFactor(widthUnit))
                : Math.addExact(Math.multiplyExact(days, DAY), 36 * 3_600_000_000L);
        boundary(0, true); boundary(1, false); boundary(-1, false); // Validate config arithmetic, never read rows.
    }

    static CalendarTimeWindows resolve(SpatialTemporalSlicing value, ZoneId zone, CanvasNodeIssueSink issues, String path) {
        var c = value.calendar();
        if (value.interval() <= 0 || c.intervalUnit() == null) {
            issues.error("INVALID_SPATIAL_TEMPORAL_INTERVAL", "日历窗口需要正整数长度和单位", path + ".calendar.intervalUnit");
            return null;
        }
        if (value.repeatInterval() != null && (value.repeatInterval() <= 0 || c.repeatIntervalUnit() == null)) {
            issues.error("INVALID_SPATIAL_TEMPORAL_REPEAT", "重复间隔需要正整数和日历模式单位", path + ".calendar.repeatIntervalUnit");
            return null;
        }
        if (zone == null) return null;
        ZonedDateTime anchor;
        try {
            anchor = (CanvasNodeSupport.blank(value.referenceTime()) ? Instant.EPOCH : parseReference(value.referenceTime(), zone)).atZone(zone);
        } catch (DateTimeException exception) {
            issues.error("INVALID_SPATIAL_TEMPORAL_REFERENCE", "参考时间须为 ISO-8601 微秒精度时间；本地时间不能落在夏令时空隙或重叠时段", path + ".referenceTime");
            return null;
        }
        try {
            return new CalendarTimeWindows(anchor, value.interval(), c.intervalUnit(),
                    value.repeatInterval() == null ? value.interval() : value.repeatInterval(),
                    value.repeatInterval() == null ? c.intervalUnit() : c.repeatIntervalUnit());
        } catch (DateTimeException | ArithmeticException exception) {
            issues.error("INVALID_SPATIAL_TEMPORAL_INTERVAL", "日历周期或参考时间超出可计算范围", path + ".interval");
            return null;
        }
    }

    private static Instant parseReference(String value, ZoneId zone) {
        Instant instant;
        try { instant = OffsetDateTime.parse(value).toInstant(); }
        catch (DateTimeException ignored) {
            var local = LocalDateTime.parse(value);
            var offsets = zone.getRules().getValidOffsets(local);
            if (offsets.size() != 1) throw new DateTimeException("Ambiguous reference");
            instant = local.toInstant(offsets.getFirst());
        }
        if (instant.getNano() % 1000 != 0) throw new DateTimeException("Sub-microsecond reference");
        return instant;
    }

    @Override public List<Row> call(Long timeMicros) {
        if (timeMicros == null) return List.of();
        try {
            Instant time = fromMicros(timeMicros);
            long last = latestStart(time), first = latestStart(fromMicros(Math.subtractExact(timeMicros, maximumWidthMicros)));
            long candidates = Math.addExact(Math.subtractExact(last, first), 1);
            if (candidates > MAX_CANDIDATE_WINDOWS)
                throw new IllegalArgumentException("SPATIAL_CALENDAR_WINDOW_LIMIT_EXCEEDED");
            var windows = new LinkedHashSet<Bounds>();
            for (long k = first; k <= last; k++) {
                Instant start = boundary(k, false), end = boundary(k, true);
                if (!time.isBefore(start) && time.isBefore(end)) windows.add(new Bounds(micros(start), micros(end)));
            }
            List<Row> result = new ArrayList<>(windows.size());
            for (var window : windows) result.add(RowFactory.create(window.start(), window.end()));
            return result;
        } catch (DateTimeException | ArithmeticException exception) {
            // Do not let Java time exceptions include observed timestamps or reference values in Spark logs.
            throw new IllegalArgumentException("SPATIAL_CALENDAR_WINDOW_RANGE_INVALID");
        }
    }

    private long latestStart(Instant time) {
        var local = time.atZone(reference.getZone());
        long units = switch (stepUnit) {
            case DAYS -> ChronoUnit.DAYS.between(reference.toLocalDate(), local.toLocalDate());
            case WEEKS -> Math.floorDiv(ChronoUnit.DAYS.between(reference.toLocalDate(), local.toLocalDate()), 7);
            case MONTHS -> 12L * (local.getYear() - reference.getYear()) + local.getMonthValue() - reference.getMonthValue();
            case YEARS -> (long) local.getYear() - reference.getYear();
            default -> Math.floorDiv(Math.subtractExact(micros(time), micros(reference.toInstant())), fixedFactor(stepUnit));
        };
        long k = Math.floorDiv(units, step);
        // Initial estimate is based on local dates. Correct time-of-day, month clamp, DST and skipped dates.
        int corrections = 0;
        while (time.isBefore(boundary(k, false))) {
            if (++corrections > 8) throw new DateTimeException("Boundary correction");
            k--;
        }
        while (!time.isBefore(boundary(k + 1, false))) {
            if (++corrections > 8) throw new DateTimeException("Boundary correction");
            k++;
        }
        return k;
    }

    private Instant boundary(long k, boolean ending) {
        long amount = Math.multiplyExact(k, step);
        // Same-family endpoints use the ORIGINAL anchor, avoiding Jan31→Feb28→Mar28 drift.
        if (ending && months(stepUnit) && months(widthUnit))
            return reference.plusMonths(Math.addExact(Math.multiplyExact(amount, monthFactor(stepUnit)), Math.multiplyExact(width, monthFactor(widthUnit)))).toInstant();
        if (ending && days(stepUnit) && days(widthUnit))
            return reference.plusDays(Math.addExact(Math.multiplyExact(amount, dayFactor(stepUnit)), Math.multiplyExact(width, dayFactor(widthUnit)))).toInstant();
        ZonedDateTime start = advance(reference, amount, stepUnit);
        return (ending ? advance(start, width, widthUnit) : start).toInstant();
    }

    private static ZonedDateTime advance(ZonedDateTime value, long amount, Unit unit) {
        if (months(unit)) return value.plusMonths(Math.multiplyExact(amount, monthFactor(unit)));
        if (days(unit)) return value.plusDays(Math.multiplyExact(amount, dayFactor(unit)));
        return value.plus(Math.multiplyExact(amount, fixedFactor(unit)), ChronoUnit.MICROS);
    }
    private static boolean months(Unit unit) { return unit == Unit.MONTHS || unit == Unit.YEARS; }
    private static boolean days(Unit unit) { return unit == Unit.DAYS || unit == Unit.WEEKS; }
    private static long monthFactor(Unit unit) { return unit == Unit.YEARS ? 12 : 1; }
    private static long dayFactor(Unit unit) { return unit == Unit.WEEKS ? 7 : 1; }
    private static long fixedFactor(Unit unit) {
        return switch (unit) { case MILLISECONDS -> 1000L; case SECONDS -> 1_000_000L;
            case MINUTES -> 60_000_000L; case HOURS -> 3_600_000_000L;
            default -> throw new IllegalArgumentException("Not a fixed duration"); };
    }
    static long micros(Instant value) { return Math.addExact(Math.multiplyExact(value.getEpochSecond(), 1_000_000L), value.getNano() / 1000); }
    static Instant fromMicros(long value) { return Instant.ofEpochSecond(Math.floorDiv(value, 1_000_000), Math.floorMod(value, 1_000_000) * 1000); }
    private record Bounds(long start, long end) { }
}
