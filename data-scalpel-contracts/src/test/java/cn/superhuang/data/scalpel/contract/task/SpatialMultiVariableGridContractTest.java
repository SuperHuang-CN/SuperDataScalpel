package cn.superhuang.data.scalpel.contract.task;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpatialMultiVariableGridContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void configurationAndNodeRoundTripAtCanvas470() {
        CanvasFilterCondition filter = new CanvasFilterGroup(
                FilterGroupOperator.AND,
                List.of(new CanvasFieldPredicate(
                        "status",
                        FilterOperator.EQUALS,
                        List.of(new CanvasLiteral(PlatformDataType.STRING, "active")))));
        SpatialMultiVariableGridConfiguration configuration = new SpatialMultiVariableGridConfiguration(
                List.of(
                        new SpatialMultiVariableGridVariable(
                                UUID.randomUUID().toString(), "facilities", "shape",
                                SpatialMultiVariableGridVariableKind.DISTANCE_TO_NEAREST,
                                null, null, null,
                                2d, SpatialDistanceUnit.KILOMETERS, filter, "facility_distance"),
                        new SpatialMultiVariableGridVariable(
                                UUID.randomUUID().toString(), "population", "shape",
                                SpatialMultiVariableGridVariableKind.ATTRIBUTE_SUMMARY_OF_RELATED,
                                null, SpatialMultiVariableGridStatisticKind.SUM, "residents",
                                null, null, null, "resident_sum")),
                SpatialDensityBinShape.HEXAGON,
                1d,
                SpatialDistanceUnit.KILOMETERS,
                "multi_variable_grid",
                "bin_id",
                "bin_geometry");
        SpatialMultiVariableGridNodeDefinition node = new SpatialMultiVariableGridNodeDefinition(
                UUID.randomUUID().toString(), "构建多变量格网",
                new CanvasNodeLayout(1d, 2d, 384d, 244d), configuration);

        CanvasNodeDefinition restored = mapper.readValue(
                mapper.writeValueAsString(node), CanvasNodeDefinition.class);

        assertEquals(node, restored);
        assertEquals(70, CanvasNodeType.SPATIAL_MULTI_VARIABLE_GRID.introducedInMinorVersion());
        assertEquals(77, CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION);
    }

    @Test
    void variableAndFilterCollectionsAreDefensivelyCopied() {
        List<CanvasLiteral> values = new ArrayList<>();
        values.add(new CanvasLiteral(PlatformDataType.STRING, "retained"));
        CanvasFieldPredicate predicate = new CanvasFieldPredicate("category", FilterOperator.IN, values);
        List<CanvasFilterCondition> children = new ArrayList<>();
        children.add(predicate);
        CanvasFilterGroup filter = new CanvasFilterGroup(FilterGroupOperator.OR, children);
        List<SpatialMultiVariableGridVariable> variables = new ArrayList<>();
        variables.add(new SpatialMultiVariableGridVariable(
                UUID.randomUUID().toString(), "features", "shape",
                SpatialMultiVariableGridVariableKind.ATTRIBUTE_OF_NEAREST,
                "category", null, null,
                10d, SpatialDistanceUnit.METERS, filter, "nearest_category"));

        SpatialMultiVariableGridConfiguration configuration = new SpatialMultiVariableGridConfiguration(
                variables, SpatialDensityBinShape.SQUARE, 100d, SpatialDistanceUnit.METERS,
                "grid", "bin_id", "bin_geometry");
        values.clear();
        children.clear();
        variables.clear();

        assertEquals(1, configuration.variables().size());
        CanvasFilterGroup copiedFilter = (CanvasFilterGroup) configuration.variables().getFirst().filter();
        assertEquals(1, copiedFilter.children().size());
        CanvasFieldPredicate copiedPredicate = (CanvasFieldPredicate) copiedFilter.children().getFirst();
        assertEquals(List.of(new CanvasLiteral(PlatformDataType.STRING, "retained")), copiedPredicate.values());
    }
}
