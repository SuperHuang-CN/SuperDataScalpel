package cn.superhuang.data.scalpel.contract.task;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ColumnTypeCastTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void roundTripsSpecialTemporalOptionsAndKeepsLegacyFormsCompatible() throws Exception {
        ColumnTypeCast milliseconds = new ColumnTypeCast(
                "event_time_ms",
                new PlatformTypeDefinition(PlatformDataType.TIMESTAMP, null, null, null, null),
                CastFailureStrategy.FAIL,
                EpochTimestampUnit.MILLISECONDS,
                null
        );

        assertEquals(milliseconds, objectMapper.readValue(
                objectMapper.writeValueAsString(milliseconds),
                ColumnTypeCast.class
        ));

        ColumnTypeCast legacy = objectMapper.readValue(
                """
                {
                  "columnName": "event_time_s",
                  "targetType": {"type": "TIMESTAMP", "length": null, "precision": null, "scale": null, "geometry": null},
                  "failureStrategy": "FAIL"
                }
                """,
                ColumnTypeCast.class
        );

        assertNull(legacy.epochTimestampUnit());
        assertNull(legacy.stringTemporalParseOptions());
        assertNull(legacy.temporalStringFormatOptions());
        assertNull(new ColumnTypeCast(
                "event_time_s", milliseconds.targetType(), CastFailureStrategy.FAIL
        ).epochTimestampUnit());
        assertNull(new ColumnTypeCast(
                "event_time_s", milliseconds.targetType(), CastFailureStrategy.FAIL,
                EpochTimestampUnit.SECONDS
        ).stringTemporalParseOptions());

        ColumnTypeCast localTimestamp = new ColumnTypeCast(
                "created_at_text",
                milliseconds.targetType(),
                CastFailureStrategy.SET_NULL,
                null,
                new StringTemporalParseOptions(
                        "yyyy-MM-dd HH:mm:ss.SSS",
                        StringTimestampZoneMode.SOURCE_TIME_ZONE,
                        "Asia/Shanghai"
                )
        );
        assertEquals(localTimestamp, objectMapper.readValue(
                objectMapper.writeValueAsString(localTimestamp),
                ColumnTypeCast.class
        ));

        ColumnTypeCast formattedTimestamp = new ColumnTypeCast(
                "created_at",
                new PlatformTypeDefinition(PlatformDataType.STRING, null, null, null, null),
                CastFailureStrategy.FAIL,
                null,
                null,
                new TemporalStringFormatOptions("yyyy-MM-dd HH:mm:ss.SSS", "Asia/Shanghai")
        );
        assertEquals(formattedTimestamp, objectMapper.readValue(
                objectMapper.writeValueAsString(formattedTimestamp),
                ColumnTypeCast.class
        ));
    }

    @Test
    void rejectsAnUnknownEpochTimestampUnit() {
        assertThrows(Exception.class, () -> objectMapper.readValue(
                """
                {
                  "columnName": "event_time",
                  "targetType": {"type": "TIMESTAMP", "length": null, "precision": null, "scale": null, "geometry": null},
                  "failureStrategy": "FAIL",
                  "epochTimestampUnit": "NANOSECONDS"
                }
                """,
                ColumnTypeCast.class
        ));
    }

    @Test
    void rejectsAnUnknownStringTimestampZoneMode() {
        assertThrows(Exception.class, () -> objectMapper.readValue(
                """
                {
                  "columnName": "created_at",
                  "targetType": {"type": "TIMESTAMP", "length": null, "precision": null, "scale": null, "geometry": null},
                  "failureStrategy": "FAIL",
                  "stringTemporalParseOptions": {
                    "pattern": "yyyy-MM-dd HH:mm:ss",
                    "zoneMode": "SESSION_TIME_ZONE",
                    "sourceTimeZone": "Asia/Shanghai"
                  }
                }
                """,
                ColumnTypeCast.class
        ));
    }
}
