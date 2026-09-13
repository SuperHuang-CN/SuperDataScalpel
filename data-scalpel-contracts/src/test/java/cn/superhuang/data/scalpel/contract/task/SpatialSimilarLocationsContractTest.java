package cn.superhuang.data.scalpel.contract.task;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpatialSimilarLocationsContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void configurationAndNodeRoundTripAtCanvas475() {
        CanvasFilterCondition filter = new CanvasFieldPredicate(
                "status", FilterOperator.EQUALS,
                List.of(new CanvasLiteral(PlatformDataType.STRING, "ACTIVE")));
        SpatialSimilarLocationsConfiguration configuration = configuration(
                List.of(new SpatialSimilarLocationsAnalysisField("population", "population")),
                List.of(new SpatialSimilarLocationsAppendField("name", "candidate_name")),
                filter);
        SpatialSimilarLocationsNodeDefinition node = new SpatialSimilarLocationsNodeDefinition(
                UUID.randomUUID().toString(), "查找相似位置",
                new CanvasNodeLayout(1d, 2d, 392d, 244d), configuration);

        CanvasNodeDefinition restored = mapper.readValue(
                mapper.writeValueAsString(node), CanvasNodeDefinition.class);

        assertEquals(node, restored);
        assertEquals(75, CanvasNodeType.SPATIAL_SIMILAR_LOCATIONS.introducedInMinorVersion());
        assertEquals(77, CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION);
    }

    @Test
    void fieldListsAreDefensivelyCopied() {
        List<SpatialSimilarLocationsAnalysisField> analyses = new ArrayList<>();
        analyses.add(new SpatialSimilarLocationsAnalysisField("population", "population"));
        List<SpatialSimilarLocationsAppendField> appends = new ArrayList<>();
        appends.add(new SpatialSimilarLocationsAppendField("name", "candidate_name"));

        SpatialSimilarLocationsConfiguration configuration = configuration(analyses, appends, null);
        analyses.clear();
        appends.clear();

        assertEquals(1, configuration.analysisFields().size());
        assertEquals(1, configuration.appendFields().size());
    }

    private static SpatialSimilarLocationsConfiguration configuration(
            List<SpatialSimilarLocationsAnalysisField> analyses,
            List<SpatialSimilarLocationsAppendField> appends,
            CanvasFilterCondition filter
    ) {
        return new SpatialSimilarLocationsConfiguration(
                "reference_places", "reference_id", "shape", filter,
                "candidate_places", "candidate_id", "shape", null,
                analyses, appends, SpatialSimilarLocationsMatchMethod.ATTRIBUTE_VALUES,
                SpatialSimilarLocationsResultMode.BOTH, 10, "similar_places", "geometry",
                "location_type", "similarity_rank", "dissimilarity_rank", "simindex",
                "cosimindex", "label_rank", "reference_id", "search_id");
    }
}
