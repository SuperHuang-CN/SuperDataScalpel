package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.TrackFixedTimeBoundary;
import cn.superhuang.data.scalpel.contract.task.TrackTimeBoundaryUnit;
import org.apache.spark.sql.api.java.UDF1;

import java.sql.Timestamp;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/** Computes reference-aligned buckets per observation, without collecting tracks or running an action. */
final class TrackTimeBoundarySupport {
    private TrackTimeBoundarySupport() { }

    static Bucket resolve(TrackFixedTimeBoundary value, CanvasNodeIssueSink issues, String path) {
        if (value == null) return null;
        if (value.interval() == null || value.interval() <= 0 || value.unit() == null) {
            issues.error("INVALID_TRACK_TIME_BOUNDARY", "固定时间边界需要正整数周期和单位", path + ".interval");
            return null;
        }
        ZoneId zone;
        try {
            zone = value.timeZone() == null ? ZoneId.of("UTC") : ZoneId.of(value.timeZone());
        } catch (DateTimeException exception) {
            issues.error("INVALID_TIME_ZONE", "固定时间边界需要有效 IANA 时区", path + ".timeZone");
            return null;
        }
        Instant reference;
        try {
            reference = value.referenceTime() == null ? Instant.EPOCH : parseReference(value.referenceTime(), zone);
        } catch (DateTimeException exception) {
            issues.error("INVALID_TRACK_TIME_BOUNDARY_REFERENCE", "参考时间需要有效 ISO-8601 时间；本地时间不能落在夏令时空隙或重叠时段",
                    path + ".referenceTime");
            return null;
        }
        return new Bucket(value.interval(), value.unit(), reference.atZone(zone));
    }

    private static Instant parseReference(String value, ZoneId zone) {
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeException ignored) {
            LocalDateTime local = LocalDateTime.parse(value);
            var offsets = zone.getRules().getValidOffsets(local);
            if (offsets.size() != 1) throw new DateTimeException("Ambiguous local reference");
            return local.toInstant(offsets.getFirst());
        }
    }

    record Bucket(int interval, TrackTimeBoundaryUnit unit, ZonedDateTime reference)
            implements UDF1<Timestamp, Long> {
        @Override
        public Long call(Timestamp value) {
            if (value == null) return null;
            Instant time = value.toInstant();
            // Calendar periods are anchored at the ORIGINAL reference, not chained through clamped month ends.
            long units = switch (unit) {
                case MILLISECONDS -> {
                    Duration elapsed = Duration.between(reference.toInstant(), time);
                    yield Math.addExact(Math.multiplyExact(elapsed.getSeconds(), 1000), elapsed.getNano() / 1_000_000);
                }
                case SECONDS -> Duration.between(reference.toInstant(), time).getSeconds();
                case MINUTES -> Math.floorDiv(Duration.between(reference.toInstant(), time).getSeconds(), 60);
                case HOURS -> Math.floorDiv(Duration.between(reference.toInstant(), time).getSeconds(), 3600);
                case DAYS -> ChronoUnit.DAYS.between(reference.toLocalDate(), time.atZone(reference.getZone()).toLocalDate());
                case WEEKS -> Math.floorDiv(ChronoUnit.DAYS.between(reference.toLocalDate(), time.atZone(reference.getZone()).toLocalDate()), 7);
                case MONTHS -> monthDifference(time);
                case YEARS -> (long) time.atZone(reference.getZone()).getYear() - reference.getYear();
            };
            long bucket = Math.floorDiv(units, interval);
            if (unit == TrackTimeBoundaryUnit.DAYS || unit == TrackTimeBoundaryUnit.WEEKS
                    || unit == TrackTimeBoundaryUnit.MONTHS || unit == TrackTimeBoundaryUnit.YEARS) {
                if (time.isBefore(boundary(bucket))) bucket--;
                else if (!time.isBefore(boundary(bucket + 1))) bucket++;
            }
            return bucket;
        }

        private long monthDifference(Instant time) {
            ZonedDateTime current = time.atZone(reference.getZone());
            return 12L * (current.getYear() - reference.getYear()) + current.getMonthValue() - reference.getMonthValue();
        }

        private Instant boundary(long bucket) {
            long amount = Math.multiplyExact(bucket, interval);
            return switch (unit) {
                case DAYS -> reference.plusDays(amount).toInstant();
                case WEEKS -> reference.plusWeeks(amount).toInstant();
                case MONTHS -> reference.plusMonths(amount).toInstant();
                case YEARS -> reference.plusYears(amount).toInstant();
                default -> throw new IllegalStateException("Not a calendar boundary");
            };
        }
    }
}
