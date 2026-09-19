import {
  CanvasNodeType,
  type CanvasNodeByType,
  type CanvasNodeType as CanvasNodeTypeValue,
} from '../canvasTypes';
import type { CanvasMetadataReference } from './metadataReferences';

export const collectNoMetadataReferences = <T extends CanvasNodeTypeValue>(
  node: CanvasNodeByType<T>,
): readonly CanvasMetadataReference[] => {
  void node;
  return [];
};

export const collectModelInputMetadataReferences = (
  node: CanvasNodeByType<typeof CanvasNodeType.ModelInput>,
): readonly CanvasMetadataReference[] => (
  node.configuration.models.flatMap(({ modelId }): CanvasMetadataReference[] => modelId
    ? [{
      kind: 'MODEL',
      nodeId: node.id,
      role: 'SOURCE',
      modelId,
    }]
    : [])
);

export const collectJdbcInputMetadataReferences = (
  node: CanvasNodeByType<typeof CanvasNodeType.JdbcInput>,
): readonly CanvasMetadataReference[] => {
  const { dataSourceId, tables } = node.configuration;
  if (!dataSourceId) return [];
  return tables.flatMap(({ tableName }): CanvasMetadataReference[] => tableName
    ? [{ kind: 'JDBC_TABLE', nodeId: node.id, role: 'SOURCE', dataSourceId, tableName }]
    : []);
};

export const collectJdbcIncrementalInputMetadataReferences = (
  node: CanvasNodeByType<typeof CanvasNodeType.JdbcIncrementalInput>,
): readonly CanvasMetadataReference[] => {
  const { dataSourceId, tableName } = node.configuration;
  return dataSourceId && tableName
    ? [{ kind: 'JDBC_TABLE', nodeId: node.id, role: 'SOURCE', dataSourceId, tableName }]
    : [];
};

export const collectJdbcQueryInputMetadataReferences = (
  node: CanvasNodeByType<typeof CanvasNodeType.JdbcQueryInput>,
): readonly CanvasMetadataReference[] => (
  node.configuration.dataSourceId
    ? [{
      kind: 'JDBC_QUERY_SOURCE',
      nodeId: node.id,
      dataSourceId: node.configuration.dataSourceId,
    }]
    : []
);

export const collectFileDatasetInputMetadataReferences = (
  node: CanvasNodeByType<typeof CanvasNodeType.FileDatasetInput>,
): readonly CanvasMetadataReference[] => (
  node.configuration.tables.flatMap(({ fileDatasetTableId }): CanvasMetadataReference[] => fileDatasetTableId
    ? [{
      kind: 'FILE_DATASET_TABLE',
      nodeId: node.id,
      fileDatasetTableId,
    }]
    : [])
);

export const collectHttpApiInputMetadataReferences = (
  node: CanvasNodeByType<typeof CanvasNodeType.HttpApiInput>,
): readonly CanvasMetadataReference[] => {
  const { dataSourceId, resources } = node.configuration;
  return dataSourceId ? resources.flatMap(({ resourceId }): CanvasMetadataReference[] => resourceId
    ? [{ kind: 'HTTP_API_RESOURCE', nodeId: node.id, dataSourceId, resourceId }]
    : []) : [];
};

export const collectSpatialServiceInputMetadataReferences = (
  node: CanvasNodeByType<typeof CanvasNodeType.SpatialServiceInput>,
): readonly CanvasMetadataReference[] => {
  const { dataSourceId, resources } = node.configuration;
  return dataSourceId ? resources.flatMap(({ resourceId }): CanvasMetadataReference[] => resourceId
    ? [{ kind: 'SPATIAL_SERVICE_RESOURCE', nodeId: node.id, dataSourceId, resourceId }]
    : []) : [];
};

export const collectKafkaInputMetadataReferences = (
  node: CanvasNodeByType<typeof CanvasNodeType.KafkaInput>,
): readonly CanvasMetadataReference[] => {
  const { dataSourceId, topic } = node.configuration;
  return dataSourceId && topic
    ? [{ kind: 'KAFKA_TOPIC', nodeId: node.id, role: 'SOURCE', dataSourceId, topic }]
    : [];
};

export const collectTdEngineTmqInputMetadataReferences = (
  node: CanvasNodeByType<typeof CanvasNodeType.TdEngineTmqInput>,
): readonly CanvasMetadataReference[] => {
  const { dataSourceId, topicName } = node.configuration;
  return dataSourceId && topicName
    ? [{ kind: 'TDENGINE_TMQ_TOPIC', nodeId: node.id, dataSourceId, topicName }]
    : [];
};

export const collectModelOutputMetadataReferences = (
  node: CanvasNodeByType<typeof CanvasNodeType.ModelOutput>,
): readonly CanvasMetadataReference[] => (
  (node.configuration.writes ?? []).flatMap((write) => write.targetModelId
    ? [{
      kind: 'MODEL',
      nodeId: node.id,
      role: 'TARGET',
      modelId: write.targetModelId,
    }]
    : [])
);

export const collectJdbcOutputMetadataReferences = (
  node: CanvasNodeByType<typeof CanvasNodeType.JdbcOutput>,
): readonly CanvasMetadataReference[] => {
  const { dataSourceId } = node.configuration;
  return dataSourceId ? (node.configuration.writes ?? []).flatMap((write) => write.targetTableName ? [{
      kind: 'JDBC_TABLE',
      nodeId: node.id,
      role: 'DISTRIBUTION',
      dataSourceId,
      tableName: write.targetTableName,
    }] : []) : [];
};

export const collectJdbcSnapshotSyncOutputMetadataReferences = (
  node: CanvasNodeByType<typeof CanvasNodeType.JdbcSnapshotSyncOutput>,
): readonly CanvasMetadataReference[] => {
  const { dataSourceId, targetTableName } = node.configuration;
  return dataSourceId && targetTableName
    ? [{
      kind: 'JDBC_TABLE',
      nodeId: node.id,
      role: 'DISTRIBUTION',
      dataSourceId,
      tableName: targetTableName,
    }]
    : [];
};

export const collectModelSnapshotSyncOutputMetadataReferences = (
  node: CanvasNodeByType<typeof CanvasNodeType.ModelSnapshotSyncOutput>,
): readonly CanvasMetadataReference[] => (
  node.configuration.targetModelId
    ? [{
      kind: 'MODEL',
      nodeId: node.id,
      role: 'TARGET',
      modelId: node.configuration.targetModelId,
    }]
    : []
);

export const collectKafkaOutputMetadataReferences = (
  node: CanvasNodeByType<typeof CanvasNodeType.KafkaOutput>,
): readonly CanvasMetadataReference[] => {
  const { dataSourceId } = node.configuration;
  return dataSourceId ? (node.configuration.writes ?? []).flatMap((write) => write.topic ? [{
      kind: 'KAFKA_TOPIC',
      nodeId: node.id,
      role: 'DISTRIBUTION',
      dataSourceId,
      topic: write.topic,
    }] : []) : [];
};

export const collectFileOutputMetadataReferences = (
  node: CanvasNodeByType<typeof CanvasNodeType.FileOutput>,
): readonly CanvasMetadataReference[] => (
  node.configuration.dataSourceId
    ? [{ kind: 'S3_TARGET', nodeId: node.id, dataSourceId: node.configuration.dataSourceId }]
    : []
);
