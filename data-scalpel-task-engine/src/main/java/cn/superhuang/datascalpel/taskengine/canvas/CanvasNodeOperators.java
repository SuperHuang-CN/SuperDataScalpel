package cn.superhuang.datascalpel.taskengine.canvas;

import java.util.List;

/**
 * Built-in Canvas node operators shared by preflight compilation and runtime execution.
 */
public final class CanvasNodeOperators {
    private static final CanvasNodeOperatorRegistry BUILT_IN_REGISTRY =
            new CanvasNodeOperatorRegistry(List.of(
                    new ModelInputNodeOperator(),
                    new JdbcInputNodeOperator(),
                    new JdbcQueryInputNodeOperator(),
                    new FileDatasetInputNodeOperator(),
                    new HttpApiInputNodeOperator(),
                    new SpatialServiceInputNodeOperator(),
                    new KafkaInputNodeOperator(),
                    new JoinNodeOperator(),
                    new GeometryConstructNodeOperator(),
                    new SpatialTransformNodeOperator(),
                    new GeometryValidateNodeOperator(),
                    new GeometryRepairNodeOperator(),
                    new GeometryBufferNodeOperator(),
                    new GeometryExplodeNodeOperator(),
                    new SpatialMeasureNodeOperator(),
                    new GeometrySerializeNodeOperator(),
                    new SpatialClipNodeOperator(),
                    new SpatialAggregateNodeOperator(),
                    new SpatialJoinNodeOperator(),
                    new StreamJoinNodeOperator(),
                    new RenameNodeOperator(),
                    new FilterNodeOperator(),
                    new SelectColumnsNodeOperator(),
                    new DeriveColumnsNodeOperator(),
                    new TypeCastNodeOperator(),
                    new AggregateNodeOperator(),
                    new UnionNodeOperator(),
                    new DeduplicateNodeOperator(),
                    new NullHandlingNodeOperator(),
                    new ValueMappingNodeOperator(),
                    new MaskFieldsNodeOperator(),
                    new JsonExtractNodeOperator(),
                    new WindowNodeOperator(),
                    new TopNNodeOperator(),
                    new ModelOutputNodeOperator(),
                    new JdbcOutputNodeOperator(),
                    new KafkaOutputNodeOperator(),
                    new FileOutputNodeOperator()
            ));

    private CanvasNodeOperators() {
    }

    public static CanvasNodeOperatorRegistry builtInRegistry() {
        return BUILT_IN_REGISTRY;
    }
}
