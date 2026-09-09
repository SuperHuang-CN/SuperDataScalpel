package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class PlanarGridContractTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Test void roundTripsOriginAndActiveOrInactiveBoundsAndAllowsDrafts() {
        for (var mode : SpatialPlanarGridOptions.ExtentMode.values()) {
            var grid = new SpatialPlanarGridOptions(-123.5d, 456d,
                    new SpatialPlanarGridOptions.Extent(mode, -20d, -10d, 100d, 50d));
            assertEquals(grid, mapper.readValue(mapper.writeValueAsString(grid), SpatialPlanarGridOptions.class));
            assertEquals(mode == SpatialPlanarGridOptions.ExtentMode.EXPLICIT_BOUNDS, grid.usesExplicitBounds());
        }
        var draft = mapper.readValue("{\"originX\":null,\"extent\":{\"mode\":null}}", SpatialPlanarGridOptions.class);
        assertNull(draft.originX()); assertNull(draft.extent().maxY());
        assertFalse(draft.usesExplicitBounds());
    }
    @Test void oldConfigurationAndConvenienceConstructorDoNotAddAlignment() {
        var old = mapper.readValue("{\"statistics\":[],\"binShape\":\"SQUARE\",\"binSize\":10,\"includeEmptyBins\":false}", SpatialBinAggregateConfiguration.class);
        assertNull(old.planarGrid());
        var javaOld = new SpatialBinAggregateConfiguration("", "", null, 0, null, false, java.util.List.of(), null, null, "", "", "", null, null);
        assertNull(javaOld.planarGrid());
        assertEquals(javaOld, mapper.readValue(mapper.writeValueAsString(javaOld), SpatialBinAggregateConfiguration.class));
    }
    @Test void rejectsMalformedBounds() {
        for (String raw : java.util.List.of("{\"extent\":[]}", "{\"extent\":{\"mode\":\"AUTO\"}}", "{\"originX\":{}}"))
            assertThrows(Exception.class, () -> mapper.readValue(raw, SpatialPlanarGridOptions.class));
    }
}
