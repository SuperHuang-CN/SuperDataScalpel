package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class CalendarWindowContractTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Test void activeAndInactiveOptionsRoundTripWithoutLosingUnits() {
        for (var mode : SpatialCalendarWindowOptions.Mode.values()) {
            var value = new SpatialTemporalSlicing("time", 2, SpatialDurationUnit.HOURS, 1L, SpatialDurationUnit.DAYS,
                    "2024-01-31T00:00:00Z", "UTC", "start", "end", new SpatialCalendarWindowOptions(mode,
                    SpatialCalendarWindowOptions.Unit.MONTHS, SpatialCalendarWindowOptions.Unit.YEARS));
            assertEquals(value, mapper.readValue(mapper.writeValueAsString(value), SpatialTemporalSlicing.class));
            assertEquals(mode == SpatialCalendarWindowOptions.Mode.CALENDAR, value.usesCalendar());
        }
    }
    @Test void oldJsonAndNineArgumentConstructorDoNotActivateCalendar() {
        var old = new SpatialTemporalSlicing("time", 1, SpatialDurationUnit.DAYS, null, null, null, "UTC", "s", "e");
        assertNull(old.calendar()); assertFalse(old.usesCalendar());
        assertNull(mapper.readValue("{\"interval\":1,\"intervalUnit\":\"DAYS\"}", SpatialTemporalSlicing.class).calendar());
    }
    @Test void allowsIncompleteOptionsButRejectsMalformedStructure() {
        var draft = mapper.readValue("{\"interval\":0,\"calendar\":{\"mode\":null}}", SpatialTemporalSlicing.class);
        assertNull(draft.calendar().mode()); assertFalse(draft.usesCalendar());
        for (String raw : java.util.List.of("[]", "{\"mode\":\"AUTO\"}", "{\"intervalUnit\":\"CENTURIES\"}"))
            assertThrows(Exception.class, () -> mapper.readValue(raw, SpatialCalendarWindowOptions.class));
    }
}
