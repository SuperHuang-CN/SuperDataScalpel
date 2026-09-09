package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SpatialH3ContractTest {
    @Test void fieldStatisticsRoundTripWithoutChangingRowCount() {
        var mapper = new ObjectMapper();
        for (var kind : List.of(SpatialBinStatisticKind.COUNT, SpatialBinStatisticKind.COUNT_FIELD, SpatialBinStatisticKind.ANY)) {
            var statistic = new SpatialBinStatistic("", kind, kind == SpatialBinStatisticKind.COUNT ? null : "label", "result");
            assertEquals(statistic, mapper.readValue(mapper.writeValueAsString(statistic), SpatialBinStatistic.class));
        }
    }
    @Test void optionalModesRoundTripWithoutChangingLegacySemantics() {
        var mapper = new ObjectMapper();
        var legacy = new SpatialBinAggregateConfiguration("points", "shape", SpatialBinShape.HEXAGON, 10,
                SpatialDistanceUnit.METERS, false, List.of(), null, null, "bins", "id", "shape");
        assertNull(legacy.h3()); assertEquals(SpatialBinSizeSemantics.LEGACY_SIDE_LENGTH, legacy.effectiveBinSizeSemantics());
        assertNull(mapper.readValue("{\"binSize\":0,\"includeEmptyBins\":false}", SpatialBinAggregateConfiguration.class).h3());
        for (var mode : SpatialH3Options.Mode.values()) {
            var c = new SpatialBinAggregateConfiguration("points", "shape", SpatialBinShape.H3, 1000,
                    SpatialDistanceUnit.METERS, false, List.of(), null, null, "bins", "id", "shape", null, new SpatialH3Options(mode, 8));
            assertEquals(c, mapper.readValue(mapper.writeValueAsString(c), SpatialBinAggregateConfiguration.class));
        }
        assertThrows(Exception.class, () -> mapper.readValue("{\"binSize\":0,\"includeEmptyBins\":false,\"h3\":{\"mode\":\"AUTO\"}}", SpatialBinAggregateConfiguration.class));
        assertThrows(Exception.class, () -> mapper.readValue("{\"binSize\":0,\"includeEmptyBins\":false,\"h3\":[]}", SpatialBinAggregateConfiguration.class));
    }
}
