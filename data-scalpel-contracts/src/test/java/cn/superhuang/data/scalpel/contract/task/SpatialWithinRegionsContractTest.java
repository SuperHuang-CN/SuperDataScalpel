package cn.superhuang.data.scalpel.contract.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SpatialWithinRegionsContractTest {
    final ObjectMapper mapper = new ObjectMapper();
    @Test void gridAndInactiveTableOptionsRoundTrip() {
        for (var mode : SpatialWithinRegions.Mode.values()) {
            var r = new SpatialWithinRegions(mode, SpatialBinShape.HEXAGON, 100d, SpatialDistanceUnit.METERS,
                    new SpatialPlanarGridOptions(0d,0d,null), "bin_id", "bin_geometry");
            var c = new SpatialSummarizeWithinConfiguration("old", "shape", "source", "geometry", true, SpatialDistanceMethod.PLANAR,
                    SpatialDistanceUnit.METERS, SpatialAreaUnit.SQUARE_METERS, List.of(), List.of(), null,null,"summary",null,r);
            assertEquals(c, mapper.readValue(mapper.writeValueAsString(c), SpatialSummarizeWithinConfiguration.class));
            assertEquals(mode == SpatialWithinRegions.Mode.PLANAR_GRID, c.usesGridRegions());
        }
    }
    @Test void oldConstructorsAndMissingJsonKeepRegionTableSemantics() {
        var c = new SpatialSummarizeWithinConfiguration("a","g","b","h",false,null,null,null,List.of(),List.of(),null,null,"out");
        assertNull(c.regions()); assertFalse(c.usesGridRegions());
        assertNull(mapper.readValue("{\"includeEmptyAreas\":false}", SpatialSummarizeWithinConfiguration.class).regions());
    }
    @Test void emptyDraftsAreAllowedButInvalidModesAndStructuresAreRejected() {
        assertNull(mapper.readValue("{\"mode\":null,\"binSize\":0}", SpatialWithinRegions.class).mode());
        assertThrows(Exception.class, () -> mapper.readValue("[]", SpatialWithinRegions.class));
        assertThrows(Exception.class, () -> mapper.readValue("{\"mode\":\"CENTROIDS\"}", SpatialWithinRegions.class));
    }
}
