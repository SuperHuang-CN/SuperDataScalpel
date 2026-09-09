package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class WithinStatisticContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void weightedDispersionHasAnIndependentVersionRequirement() {
        for (var kind : java.util.List.of(SpatialWithinStatisticKind.VARIANCE, SpatialWithinStatisticKind.STDDEV)) {
            var s = new SpatialWithinStatistic("id", kind, "value", "result", null, SpatialWithinWeighting.INTERSECTION_FRACTION);
            assertTrue(s.requiresWeightedDispersionVersion());
            assertEquals(s, mapper.readValue(mapper.writeValueAsString(s), SpatialWithinStatistic.class));
            assertFalse(new SpatialWithinStatistic("id", kind, "value", "result").requiresWeightedDispersionVersion());
        }
        assertFalse(new SpatialWithinStatistic("id", SpatialWithinStatisticKind.MEAN, "value", "result", null,
                SpatialWithinWeighting.INTERSECTION_FRACTION).requiresWeightedDispersionVersion());
    }

    @Test
    void linkedResultRoundTripsAndCanRetainInactiveLegacyDrafts() {
        var result = new SpatialWithinGroupResult("id", "area_key", "groups", "group", "minor", "major", "minor_pct", "major_pct");
        assertEquals(result, mapper.readValue(mapper.writeValueAsString(result), SpatialWithinGroupResult.class));
        var config = new SpatialSummarizeWithinConfiguration("", "", "", "", true, null, null, null,
                java.util.List.of(), java.util.List.of(), new SpatialGroupSummary("kind", true, true, null, null, "pct"), null, "", result);
        assertTrue(config.usesLinkedGroupResult());
        assertEquals(config, mapper.readValue(mapper.writeValueAsString(config), SpatialSummarizeWithinConfiguration.class));
        var inactive = mapper.readValue(mapper.writeValueAsString(config).replace("\"mode\":null", "\"mode\":\"LEGACY_FLAT\""), SpatialSummarizeWithinConfiguration.class);
        assertFalse(inactive.usesLinkedGroupResult());
        assertEquals("groups", inactive.groupResult().outputTableName());
        assertThrows(Exception.class, () -> mapper.readValue("{\"mode\":\"OTHER\"}", SpatialWithinGroupResult.class));
    }

    @Test
    void roundTripsExplicitModesWithoutChangingLegacyDefaults() {
        var old = new SpatialWithinStatistic("id", SpatialWithinStatisticKind.SUM, "amount", "total");
        assertFalse(old.apportionsTotal());
        assertFalse(old.usesGeographicWeight());
        assertFalse(old.requiresExplicitStatisticsVersion());
        assertEquals(old, mapper.readValue(mapper.writeValueAsString(old), SpatialWithinStatistic.class));
        var current = new SpatialWithinStatistic("id", SpatialWithinStatisticKind.MEAN, "rate", "mean",
                SpatialWithinValueTreatment.ORIGINAL_VALUE, SpatialWithinWeighting.INTERSECTION_FRACTION);
        assertTrue(current.requiresExplicitStatisticsVersion());
        assertEquals(current, mapper.readValue(mapper.writeValueAsString(current), SpatialWithinStatistic.class));
        assertFalse(mapper.readValue("{\"kind\":\"SUM\"}", SpatialWithinStatistic.class).apportionsTotal());
    }

    @Test
    void rejectsUnknownModesAndGatesAddedFunctions() {
        assertThrows(Exception.class, () -> mapper.readValue("{\"valueTreatment\":\"GUESS\"}", SpatialWithinStatistic.class));
        assertThrows(Exception.class, () -> mapper.readValue("{\"weighting\":{}}", SpatialWithinStatistic.class));
        for (var kind : new SpatialWithinStatisticKind[]{SpatialWithinStatisticKind.COUNT_FIELD, SpatialWithinStatisticKind.ANY}) {
            assertTrue(new SpatialWithinStatistic("id", kind, "field", "result").requiresExplicitStatisticsVersion());
        }
    }
}
