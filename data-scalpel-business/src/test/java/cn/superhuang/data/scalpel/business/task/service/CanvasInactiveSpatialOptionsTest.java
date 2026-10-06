package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.contract.task.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.springframework.web.server.ResponseStatusException;

class CanvasInactiveSpatialOptionsTest {
    private final CanvasDefinitionValidator validator = new CanvasDefinitionValidator(new CanvasDefinitionUpgrader());

    @Test void spatialDbscanDoesNotRequireInactiveTimeField() {
        for (var mode : List.of(SpatialDbscanOptions.Mode.SPATIAL, SpatialDbscanOptions.Mode.LEGACY_SPATIAL)) {
            assertDoesNotThrow(() -> validator.validate(cluster(mode)));
        }
        assertThrows(ResponseStatusException.class, () -> validator.validate(cluster(SpatialDbscanOptions.Mode.LINEAR)));
    }

    private CanvasDefinition cluster(SpatialDbscanOptions.Mode mode) {
        var config = new SpatialPointClusterConfiguration("points", "geom", "id", SpatialDistanceMethod.GEODESIC,
                new SpatialPointClusterParameters.Dbscan(100, SpatialDistanceUnit.METERS, 3),
                "clusters", "cluster_id", "is_noise", new SpatialDbscanOptions(mode, null, null, null));
        return new CanvasDefinition(4, 78, List.of(new SpatialPointClusterNodeDefinition(UUID.randomUUID().toString(),
                "聚类", new CanvasNodeLayout(0d, 0d, 300d, 200d), config)), List.of());
    }

    @Test void areaTableDoesNotRequireInactiveGridOutputFields() {
        var config = new SpatialSummarizeWithinConfiguration("areas", "geom", "points", "geom", true,
                SpatialDistanceMethod.PLANAR, SpatialDistanceUnit.SOURCE_CRS_UNIT, SpatialAreaUnit.SQUARE_METERS,
                List.of(), List.of(), null, null, "summary", null,
                new SpatialWithinRegions(SpatialWithinRegions.Mode.AREA_TABLE, null, null, null, null, null, null));
        var node = new SpatialSummarizeWithinNodeDefinition(UUID.randomUUID().toString(), "范围汇总",
                new CanvasNodeLayout(0d, 0d, 300d, 200d), config);
        assertDoesNotThrow(() -> validator.validate(new CanvasDefinition(4, 78, List.of(node), List.of())));
    }

    @Test void separateCenterResultsDoNotRequireLegacyOutputTable() {
        var analysis = new SpatialCenterDispersionAnalysis(UUID.randomUUID().toString(),
                SpatialCenterDispersionKind.MEAN_CENTER, "center", null, "centers");
        var config = new SpatialCenterDispersionConfiguration("points", "geom", null,
                List.of(), null, List.of(analysis), null, SpatialCenterResultMode.ANALYSIS_TABLES);
        var node = new SpatialCenterDispersionNodeDefinition(UUID.randomUUID().toString(), "中心分析",
                new CanvasNodeLayout(0d, 0d, 300d, 200d), config);
        assertDoesNotThrow(() -> validator.validate(new CanvasDefinition(4, 78, List.of(node), List.of())));
    }
}
