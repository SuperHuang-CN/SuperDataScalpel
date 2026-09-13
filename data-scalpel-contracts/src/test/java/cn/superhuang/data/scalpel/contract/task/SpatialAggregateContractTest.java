package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpatialAggregateContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void dissolveOptionsRoundTripWithoutChangingMissingLegacySemantics() {
        SpatialAggregateConfiguration legacy = mapper.readValue("""
                {
                  "sourceTableName":"parcels",
                  "outputTableName":"districts",
                  "groupByColumns":["district"],
                  "aggregations":[{
                    "kind":"UNION",
                    "geometryColumnName":"shape",
                    "outputColumnName":"district_shape"
                  }]
                }
                """, SpatialAggregateConfiguration.class);
        assertNull(legacy.dissolve());

        SpatialAggregateConfiguration enabled = new SpatialAggregateConfiguration(
                "parcels",
                "districts",
                List.of("district"),
                List.of(new SpatialAggregation(
                        SpatialAggregationKind.UNION, "shape", "district_shape")),
                new SpatialAggregateDissolveOptions(
                        true,
                        false,
                        "feature_count",
                        List.of(
                                new SpatialAggregateStatistic(
                                        UUID.randomUUID().toString(),
                                        SpatialAggregateStatisticKind.SUM,
                                        "population",
                                        "population_sum"),
                                new SpatialAggregateStatistic(
                                        UUID.randomUUID().toString(),
                                        SpatialAggregateStatisticKind.ANY,
                                        "name",
                                        "any_name")
                        )
                )
        );
        assertEquals(enabled, mapper.readValue(
                mapper.writeValueAsString(enabled), SpatialAggregateConfiguration.class));

        SpatialAggregateConfiguration connected = new SpatialAggregateConfiguration(
                "parcels",
                "connected_districts",
                List.of(),
                enabled.aggregations(),
                new SpatialAggregateDissolveOptions(
                        true,
                        true,
                        "feature_count",
                        List.of(),
                        SpatialAggregateDissolveGroupingMode.CONNECTED_COMPONENTS
                )
        );
        SpatialAggregateConfiguration restoredConnected = mapper.readValue(
                mapper.writeValueAsString(connected), SpatialAggregateConfiguration.class);
        assertEquals(connected, restoredConnected);
        assertEquals(
                SpatialAggregateDissolveGroupingMode.CONNECTED_COMPONENTS,
                restoredConnected.dissolve().effectiveGroupingMode());
        assertEquals(
                SpatialAggregateDissolveGroupingMode.ALL_OR_FIELDS,
                enabled.dissolve().effectiveGroupingMode());

        SpatialAggregateConfiguration inactiveDraft = new SpatialAggregateConfiguration(
                "parcels",
                "districts",
                List.of(),
                List.of(new SpatialAggregation(
                        SpatialAggregationKind.UNION, "shape", "district_shape")),
                new SpatialAggregateDissolveOptions(
                        false,
                        true,
                        "saved_count",
                        List.of(new SpatialAggregateStatistic(
                                "saved-non-uuid-draft",
                                SpatialAggregateStatisticKind.MEAN,
                                "saved_field",
                                "saved_mean"))
                )
        );
        SpatialAggregateConfiguration restoredDraft = mapper.readValue(
                mapper.writeValueAsString(inactiveDraft), SpatialAggregateConfiguration.class);
        assertEquals(inactiveDraft, restoredDraft);
        assertFalse(restoredDraft.dissolve().enabled());

        assertThrows(Exception.class, () -> mapper.readValue("""
                {
                  "dissolve": {
                    "enabled": true,
                    "multipart": false,
                    "countOutputColumnName": "feature_count",
                    "summaryStatistics": [{
                      "statisticId": "11111111-1111-4111-8111-111111111111",
                      "kind": "MEDIAN",
                      "sourceColumnName": "value",
                      "outputColumnName": "median_value"
                    }]
                  }
                }
                """, SpatialAggregateConfiguration.class));
    }
}
