export interface JdbcTableMetadataReference {
  kind: 'JDBC_TABLE';
  nodeId: string;
  role: 'SOURCE' | 'DISTRIBUTION';
  dataSourceId: string;
  tableName: string;
}

export interface JdbcQuerySourceMetadataReference {
  kind: 'JDBC_QUERY_SOURCE';
  nodeId: string;
  dataSourceId: string;
}

export interface ModelMetadataReference {
  kind: 'MODEL';
  nodeId: string;
  role: 'SOURCE' | 'TARGET';
  modelId: string;
}

export interface FileDatasetTableMetadataReference {
  kind: 'FILE_DATASET_TABLE';
  nodeId: string;
  fileDatasetTableId: string;
}

export interface HttpApiResourceMetadataReference {
  kind: 'HTTP_API_RESOURCE';
  nodeId: string;
  dataSourceId: string;
  resourceId: string;
}

export interface SpatialServiceResourceMetadataReference {
  kind: 'SPATIAL_SERVICE_RESOURCE';
  nodeId: string;
  dataSourceId: string;
  resourceId: string;
}

export interface KafkaTopicMetadataReference {
  kind: 'KAFKA_TOPIC';
  nodeId: string;
  role: 'SOURCE' | 'DISTRIBUTION';
  dataSourceId: string;
  topic: string;
}

export interface S3TargetMetadataReference {
  kind: 'S3_TARGET';
  nodeId: string;
  dataSourceId: string;
}

export type CanvasMetadataReference =
  | JdbcTableMetadataReference
  | JdbcQuerySourceMetadataReference
  | ModelMetadataReference
  | FileDatasetTableMetadataReference
  | HttpApiResourceMetadataReference
  | SpatialServiceResourceMetadataReference
  | KafkaTopicMetadataReference
  | S3TargetMetadataReference;
