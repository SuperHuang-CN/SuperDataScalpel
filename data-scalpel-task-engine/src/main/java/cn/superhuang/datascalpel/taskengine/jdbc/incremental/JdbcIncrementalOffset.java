package cn.superhuang.datascalpel.taskengine.jdbc.incremental;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.spark.sql.connector.read.streaming.Offset;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

public final class JdbcIncrementalOffset extends Offset implements Serializable {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final String sourceSignature;
    private final String temporalType;
    private final boolean lowerUnbounded;
    private final Instant endTime;

    public JdbcIncrementalOffset(
            String sourceSignature,
            String temporalType,
            boolean lowerUnbounded,
            Instant endTime
    ) {
        if (sourceSignature == null || sourceSignature.isBlank()
                || temporalType == null || temporalType.isBlank()
                || lowerUnbounded == (endTime != null)) {
            throw invalid("JDBC 增量 Offset 结构无效");
        }
        this.sourceSignature = sourceSignature;
        this.temporalType = temporalType;
        this.lowerUnbounded = lowerUnbounded;
        this.endTime = endTime;
    }

    public static JdbcIncrementalOffset earliest(String sourceSignature, String temporalType) {
        return new JdbcIncrementalOffset(sourceSignature, temporalType, true, null);
    }

    public static JdbcIncrementalOffset at(String sourceSignature, String temporalType, Instant endTime) {
        return new JdbcIncrementalOffset(sourceSignature, temporalType, false, endTime);
    }

    public String sourceSignature() { return sourceSignature; }
    public String temporalType() { return temporalType; }
    public boolean lowerUnbounded() { return lowerUnbounded; }
    public Instant endTime() { return endTime; }

    @Override
    public String json() {
        try {
            return MAPPER.writeValueAsString(new Payload(
                    1, sourceSignature, temporalType, lowerUnbounded,
                    endTime == null ? null : endTime.toString()));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize JDBC incremental offset", exception);
        }
    }

    public static JdbcIncrementalOffset parse(String json) {
        try {
            Payload payload = MAPPER.readValue(json, Payload.class);
            if (payload.version() != 1) throw invalid("JDBC 增量 Checkpoint 版本不受支持");
            return new JdbcIncrementalOffset(
                    payload.sourceSignature(), payload.temporalType(), payload.lowerUnbounded(),
                    payload.endTime() == null ? null : Instant.parse(payload.endTime()));
        } catch (JdbcIncrementalException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new JdbcIncrementalException(
                    "JDBC_INCREMENTAL_CHECKPOINT_INVALID",
                    "JDBC 增量 Checkpoint 无法解析", false, exception);
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof JdbcIncrementalOffset offset
                && lowerUnbounded == offset.lowerUnbounded
                && sourceSignature.equals(offset.sourceSignature)
                && temporalType.equals(offset.temporalType)
                && Objects.equals(endTime, offset.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceSignature, temporalType, lowerUnbounded, endTime);
    }

    @Override
    public String toString() { return json(); }

    private static JdbcIncrementalException invalid(String message) {
        return new JdbcIncrementalException("JDBC_INCREMENTAL_CHECKPOINT_INVALID", message, false);
    }

    private record Payload(
            int version,
            String sourceSignature,
            String temporalType,
            boolean lowerUnbounded,
            String endTime
    ) {
    }
}
