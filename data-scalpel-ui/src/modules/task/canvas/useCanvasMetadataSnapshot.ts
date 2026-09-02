import { useQueries, useQuery } from '@tanstack/react-query';
import {
  fetchApiResource,
  fetchSpatialFeatureResources,
  fetchDataSource,
  fetchTableMetadata,
  fetchTdEngineTmqTopic,
  type DataSource,
  type TableIdentifier,
  type TableMetadata,
} from '../../datasource';
import {
  fetchDataModel,
  type DataModelField,
} from '../../model';
import {
  fetchFileDatasetCanvasMetadata,
  type FileDatasetCanvasTableMetadata,
} from '../../filedataset';
import {
  CanvasNodeType,
  type CanvasColumnSchema,
  type CanvasDefinition,
  type CanvasNodeRuntimeSummary,
} from './canvasTypes';
import { canvasNodeRegistry } from './nodes/nodeRegistry';
import { canvasMetadataProviderRegistry } from './metadata/canvasMetadataProvider';
import type {
  CanvasMetadataReference,
  JdbcTableMetadataReference,
  JdbcQuerySourceMetadataReference,
  HttpApiResourceMetadataReference,
  SpatialServiceResourceMetadataReference,
  TdEngineTmqTopicMetadataReference,
} from './nodes/metadataReferences';
import type {
  TaskCompilationMetadataDataSource,
  TaskCompilationMetadataModel,
  TaskCompilationMetadataSnapshot,
  TaskCompilationMetadataTable,
  TaskCompilationMetadataFileDatasetTable,
} from './taskCompilationTypes';

type CanvasMetadataRole = 'SOURCE' | 'DISTRIBUTION';

interface CanvasJdbcMetadataRequest {
  dataSourceId: string;
  tableName: string;
  role: CanvasMetadataRole;
}

interface CanvasApiMetadataRequest {
  dataSourceId: string;
  resourceId: string;
}

export type CanvasMetadataIssueCode =
  | 'MODEL_READ_FAILED'
  | 'DATA_SOURCE_READ_FAILED'
  | 'TABLE_METADATA_READ_FAILED'
  | 'API_RESOURCE_READ_FAILED'
  | 'TDENGINE_TMQ_TOPIC_READ_FAILED'
  | 'FILE_DATASET_METADATA_READ_FAILED'
  | 'COLUMN_TYPE_MAPPING_UNSUPPORTED';

export interface CanvasMetadataIssue {
  code: CanvasMetadataIssueCode;
  message: string;
  nodeIds: string[];
}

const jdbcRequestKey = (request: CanvasJdbcMetadataRequest) => (
  `${request.role}\u0000${request.dataSourceId}\u0000${request.tableName}`
);

const tableRequestKey = (dataSourceId: string, identifier: TableIdentifier) => (
  `${dataSourceId}\u0000${identifier.catalog ?? ''}\u0000${identifier.schema ?? ''}\u0000${identifier.table}`
);

const jdbcTableIdentifier = (tableName: string): TableIdentifier => ({
  catalog: null,
  schema: null,
  table: tableName,
});

const canvasColumnSchema = (
  column: TableMetadata['columns'][number],
): CanvasColumnSchema => {
  const type = column.platformTypeDefinition;
  if (!type) throw new Error(`字段 ${column.name} 无法无损映射为平台类型`);
  return {
    name: column.name,
    fieldType: type.type,
    length: type.length,
    precision: type.precision,
    scale: type.scale,
    nullable: column.nullable,
    defaultValue: column.defaultValue,
    autoIncrement: column.autoIncrement,
    generated: column.generated,
    comment: column.comment,
    geometry: type.geometry ?? null,
  };
};

const modelColumnSchema = (field: DataModelField): CanvasColumnSchema => ({
  name: field.code,
  fieldType: field.fieldType,
  length: field.fieldType === 'STRING' ? field.length : null,
  precision: field.fieldType === 'DECIMAL' ? field.precision : null,
  scale: field.fieldType === 'DECIMAL' ? field.scale : null,
  nullable: field.nullable,
  defaultValue: null,
  autoIncrement: false,
  generated: false,
  comment: field.description,
  geometry: field.geometry ?? null,
});

const collectMetadataReferences = (definition: CanvasDefinition): CanvasMetadataReference[] => (
  definition.nodes.flatMap((node) => canvasNodeRegistry.collectMetadataReferences(node))
);

const referenceNodeIds = (
  references: readonly CanvasMetadataReference[],
  predicate: (reference: CanvasMetadataReference) => boolean,
) => [...new Set(references.filter(predicate).map((reference) => reference.nodeId))];

const fileDatasetColumnSchema = (
  field: FileDatasetCanvasTableMetadata['fields'][number],
): CanvasColumnSchema => ({
  name: field.name,
  fieldType: field.platformTypeDefinition.type,
  length: field.platformTypeDefinition.length,
  precision: field.platformTypeDefinition.precision,
  scale: field.platformTypeDefinition.scale,
  nullable: field.nullable,
  defaultValue: null,
  autoIncrement: false,
  generated: false,
  comment: null,
  geometry: field.platformTypeDefinition.geometry ?? null,
});

const queryFailureMessage = (prefix: string, error: unknown) => (
  error instanceof Error && error.message.trim()
    ? `${prefix}：${error.message}`
    : prefix
);

const compilationDataSource = (
  dataSource: DataSource,
  tables: TaskCompilationMetadataTable[],
): TaskCompilationMetadataDataSource | null => (
  dataSource.connectionKind === 'JDBC' && dataSource.connection.kind === 'JDBC'
    ? {
      id: dataSource.id,
      enabled: dataSource.enabled,
      connectionKind: 'JDBC',
      jdbcDatabaseType: [
        'POSTGRESQL', 'MYSQL', 'ORACLE', 'SQL_SERVER', 'CLICKHOUSE', 'DAMENG',
        'OPENGAUSS', 'KINGBASE', 'TDENGINE_WEBSOCKET', 'TDENGINE_RESTFUL',
      ].includes(dataSource.type)
        ? (dataSource.type as TaskCompilationMetadataDataSource['jdbcDatabaseType'])
        : null,
      purposes: [...dataSource.purposes].sort(),
      tables,
      tdEngineTmqTopics: [],
    }
    : null
);

const apiCompilationDataSource = (
  dataSource: DataSource,
  tables: TaskCompilationMetadataTable[],
): TaskCompilationMetadataDataSource | null => (
  dataSource.connectionKind === 'HTTP_API' && dataSource.connection.kind === 'HTTP_API'
    ? {
      id: dataSource.id,
      enabled: dataSource.enabled,
      connectionKind: 'HTTP_API',
      jdbcDatabaseType: null,
      purposes: [...dataSource.purposes].sort(),
      tables,
      tdEngineTmqTopics: [],
    }
    : null
);

const kafkaCompilationDataSource = (
  dataSource: DataSource,
): TaskCompilationMetadataDataSource | null => (
  dataSource.connectionKind === 'KAFKA' && dataSource.connection.kind === 'KAFKA'
    ? {
      id: dataSource.id,
      enabled: dataSource.enabled,
      connectionKind: 'KAFKA',
      jdbcDatabaseType: null,
      purposes: [...dataSource.purposes].sort(),
      tables: [],
      tdEngineTmqTopics: [],
    }
    : null
);

const s3CompilationDataSource = (
  dataSource: DataSource,
): TaskCompilationMetadataDataSource | null => (
  dataSource.connectionKind === 'S3' && dataSource.connection.kind === 'S3'
    ? {
      id: dataSource.id,
      enabled: dataSource.enabled,
      connectionKind: 'S3',
      jdbcDatabaseType: null,
      purposes: [...dataSource.purposes].sort(),
      tables: [],
      tdEngineTmqTopics: [],
    }
    : null
);

export const useCanvasMetadataSnapshot = (definition: CanvasDefinition) => {
  const metadataReferences = collectMetadataReferences(definition);
  const resourceReferences = canvasMetadataProviderRegistry.deduplicate(metadataReferences);
  const jdbcReferences = resourceReferences.filter(
    (reference): reference is JdbcTableMetadataReference => reference.kind === 'JDBC_TABLE',
  );
  const jdbcQueryReferences = resourceReferences.filter(
    (reference): reference is JdbcQuerySourceMetadataReference => (
      reference.kind === 'JDBC_QUERY_SOURCE'
    ),
  );
  const apiReferences = resourceReferences.filter(
    (reference): reference is HttpApiResourceMetadataReference => reference.kind === 'HTTP_API_RESOURCE',
  );
  const spatialReferences = resourceReferences.filter(
    (reference): reference is SpatialServiceResourceMetadataReference => reference.kind === 'SPATIAL_SERVICE_RESOURCE',
  );
  const jdbcRequests = [...new Map(jdbcReferences.map((reference) => {
    const request: CanvasJdbcMetadataRequest = {
      dataSourceId: reference.dataSourceId,
      tableName: reference.tableName,
      role: reference.role,
    };
    return [jdbcRequestKey(request), request];
  })).values()];
  const apiRequests = [...new Map(apiReferences.map((reference) => {
    const request: CanvasApiMetadataRequest = {
      dataSourceId: reference.dataSourceId,
      resourceId: reference.resourceId,
    };
    return [`${request.dataSourceId}\u0000${request.resourceId}`, request];
  })).values()];
  const spatialRequests = [...new Map(spatialReferences.map((reference) => [
    `${reference.dataSourceId}\u0000${reference.resourceId}`, reference,
  ])).values()];
  const modelIds = [...new Set(resourceReferences.flatMap(
    (reference) => reference.kind === 'MODEL' ? [reference.modelId] : [],
  ))];
  const kafkaIds = [...new Set(resourceReferences.flatMap(
    (reference) => reference.kind === 'KAFKA_TOPIC' ? [reference.dataSourceId] : [],
  ))];
  const tmqReferences = resourceReferences.filter(
    (reference): reference is TdEngineTmqTopicMetadataReference => reference.kind === 'TDENGINE_TMQ_TOPIC',
  );
  const fileOutputIds = [...new Set(resourceReferences.flatMap(
    (reference) => reference.kind === 'S3_TARGET' ? [reference.dataSourceId] : [],
  ))];
  const referencedFileTableIds = [...new Set(resourceReferences.flatMap(
    (reference) => reference.kind === 'FILE_DATASET_TABLE'
      ? [reference.fileDatasetTableId]
      : [],
  ))].sort();
  const fileDatasetMetadataQuery = useQuery({
    queryKey: ['file-dataset-tables', 'canvas-metadata', referencedFileTableIds],
    queryFn: () => fetchFileDatasetCanvasMetadata(referencedFileTableIds),
    enabled: referencedFileTableIds.length > 0,
    staleTime: 30_000,
  });
  const modelQueries = useQueries({
    queries: modelIds.map((modelId) => ({
      queryKey: ['data-models', modelId],
      queryFn: () => fetchDataModel(modelId),
      staleTime: 60_000,
    })),
  });
  const modelById = new Map(modelIds.map((id, index) => [id, modelQueries[index].data]));
  const modelDetails = modelIds.flatMap((id) => {
    const detail = modelById.get(id);
    return detail ? [detail] : [];
  });
  const dataSourceIds = [...new Set([
    ...jdbcRequests.map((request) => request.dataSourceId),
    ...jdbcQueryReferences.map((reference) => reference.dataSourceId),
    ...apiRequests.map((request) => request.dataSourceId),
    ...spatialRequests.map((request) => request.dataSourceId),
    ...kafkaIds,
    ...tmqReferences.map((reference) => reference.dataSourceId),
    ...fileOutputIds,
    ...modelDetails.map((detail) => detail.model.storageDataSourceId),
  ])];
  const dataSourceQueries = useQueries({
    queries: dataSourceIds.map((dataSourceId) => ({
      queryKey: ['data-sources', dataSourceId],
      queryFn: () => fetchDataSource(dataSourceId),
      staleTime: 60_000,
    })),
  });
  const dataSourceById = new Map(dataSourceIds.map((id, index) => [id, dataSourceQueries[index].data]));

  const jdbcTableRequests = [...new Map(jdbcRequests.map((request) => [
    tableRequestKey(request.dataSourceId, jdbcTableIdentifier(request.tableName)),
    request,
  ])).values()];
  const jdbcTableQueries = useQueries({
    queries: jdbcTableRequests.map((request) => ({
      queryKey: ['data-sources', request.dataSourceId, 'table-metadata', jdbcTableIdentifier(request.tableName)],
      queryFn: () => fetchTableMetadata(request.dataSourceId, jdbcTableIdentifier(request.tableName)),
      staleTime: 60_000,
    })),
  });
  const jdbcTableByKey = new Map(jdbcTableRequests.map((request, index) => [
    tableRequestKey(request.dataSourceId, jdbcTableIdentifier(request.tableName)),
    jdbcTableQueries[index].data,
  ]));

  const apiResourceQueries = useQueries({
    queries: apiRequests.map((request) => ({
      queryKey: ['api-resources', request.dataSourceId, request.resourceId],
      queryFn: () => fetchApiResource(request.dataSourceId, request.resourceId),
      staleTime: 60_000,
    })),
  });
  const apiResourceByKey = new Map(apiRequests.map((request, index) => [
    `${request.dataSourceId}\u0000${request.resourceId}`,
    apiResourceQueries[index].data,
  ]));
  const spatialResourceQueries = useQueries({
    queries: [...new Set(spatialRequests.map((request) => request.dataSourceId))].map((dataSourceId) => ({
      queryKey: ['spatial-resources', dataSourceId],
      queryFn: () => fetchSpatialFeatureResources(dataSourceId),
      staleTime: 60_000,
    })),
  });
  const spatialResourceByKey = new Map(spatialRequests.map((request) => {
    const sourceIndex = [...new Set(spatialRequests.map((item) => item.dataSourceId))].indexOf(request.dataSourceId);
    const resource = spatialResourceQueries[sourceIndex].data?.find((item) => item.id === request.resourceId);
    return [`${request.dataSourceId}\u0000${request.resourceId}`, resource];
  }));
  const tmqTopicQueries = useQueries({
    queries: tmqReferences.map((reference) => ({
      queryKey: ['data-sources', reference.dataSourceId, 'tdengine-tmq-topic', reference.topicName],
      queryFn: () => fetchTdEngineTmqTopic(reference.dataSourceId, reference.topicName),
      staleTime: 30_000,
    })),
  });
  const tmqTopicByKey = new Map(tmqReferences.map((reference, index) => [
    `${reference.dataSourceId}\u0000${reference.topicName}`,
    tmqTopicQueries[index].data,
  ]));

  const compilationDataSources = new Map<string, TaskCompilationMetadataDataSource>();
  dataSourceIds.forEach((dataSourceId) => {
    const dataSource = dataSourceById.get(dataSourceId);
    if (!dataSource) return;
    const tables = jdbcTableRequests
      .filter((request) => request.dataSourceId === dataSourceId)
      .flatMap((request): TaskCompilationMetadataTable[] => {
        const metadata = jdbcTableByKey.get(
          tableRequestKey(request.dataSourceId, jdbcTableIdentifier(request.tableName)),
        );
        if (metadata?.columns.some((column) => !column.platformTypeDefinition)) return [];
        return metadata ? [{
          tableName: request.tableName,
          objectType: metadata.table.type.toUpperCase() === 'VIEW'
            ? 'VIEW'
            : metadata.table.type.toUpperCase() === 'SUPERTABLE' ? 'SUPERTABLE' : 'TABLE',
          columns: metadata.columns.map(canvasColumnSchema),
          uniqueKeys: metadata.uniqueKeys,
        }] : [];
      });
    const compiled = compilationDataSource(dataSource, tables);
    if (compiled) {
      compiled.tdEngineTmqTopics = tmqReferences
        .filter((reference) => reference.dataSourceId === dataSourceId)
        .flatMap((reference) => {
          const topic = tmqTopicByKey.get(`${reference.dataSourceId}\u0000${reference.topicName}`);
          if (!topic?.supported || !topic.databaseName || !topic.supertableName || !topic.timePrecision) return [];
          return [{
            topicName: topic.topicName,
            catalogName: topic.databaseName,
            supertableName: topic.supertableName,
            definitionFingerprint: topic.definitionFingerprint,
            legacyDefinitionFingerprint: topic.legacyDefinitionFingerprint,
            timePrecision: topic.timePrecision,
            columns: topic.columns.map(canvasColumnSchema),
          }];
        });
      compilationDataSources.set(dataSourceId, compiled);
      return;
    }
    const resources = apiRequests
      .filter((request) => request.dataSourceId === dataSourceId)
      .flatMap((request): TaskCompilationMetadataTable[] => {
        const resource = apiResourceByKey.get(`${request.dataSourceId}\u0000${request.resourceId}`);
        return resource ? [{
          tableName: resource.id,
          objectType: 'API_RESOURCE',
          columns: resource.outputFields.map((field) => ({
            name: field.name,
            fieldType: field.type.type,
            length: field.type.length,
            precision: field.type.precision,
            scale: field.type.scale,
            nullable: field.nullable,
            defaultValue: null,
            autoIncrement: false,
            generated: false,
            comment: field.comment,
            geometry: null,
          })),
          uniqueKeys: [],
        }] : [];
      });
    const apiCompiled = apiCompilationDataSource(dataSource, resources);
    if (apiCompiled) {
      compilationDataSources.set(dataSourceId, apiCompiled);
      return;
    }
    const spatialResources = spatialRequests
      .filter((request) => request.dataSourceId === dataSourceId)
      .flatMap((request): TaskCompilationMetadataTable[] => {
        const resource = spatialResourceByKey.get(`${request.dataSourceId}\u0000${request.resourceId}`);
        return resource ? [{
          tableName: resource.id,
          objectType: 'SPATIAL_FEATURE_RESOURCE',
          columns: resource.columns.map((column) => ({
            ...column,
            geometry: column.geometry ?? null,
          })),
          uniqueKeys: [],
        }] : [];
      });
    const spatialCompiled = apiCompilationDataSource(dataSource, spatialResources);
    if (spatialCompiled) {
      compilationDataSources.set(dataSourceId, spatialCompiled);
      return;
    }
    const kafkaCompiled = kafkaCompilationDataSource(dataSource);
    if (kafkaCompiled) {
      compilationDataSources.set(dataSourceId, kafkaCompiled);
      return;
    }
    const s3Compiled = s3CompilationDataSource(dataSource);
    if (s3Compiled) compilationDataSources.set(dataSourceId, s3Compiled);
  });

  const compilationModels = modelDetails.flatMap((detail): TaskCompilationMetadataModel[] => {
    const fields = [...detail.fields].sort((left, right) => left.sortOrder - right.sortOrder);
    const primaryKeyColumns = fields.filter((field) => field.primaryKey).map((field) => field.code);
    return [{
      id: detail.model.id,
      code: detail.model.code,
      name: detail.model.name,
      schemaVersion: detail.model.schemaVersion,
      status: detail.model.status,
      physicalTableMode: detail.model.physicalTableMode,
      dataSourceId: detail.model.storageDataSourceId,
      catalogName: detail.model.catalogName,
      schemaName: detail.model.schemaName,
      physicalTableName: detail.model.physicalTableName,
      columns: fields.map(modelColumnSchema),
      uniqueKeys: primaryKeyColumns.length > 0 ? [{
        name: 'MODEL_PRIMARY_KEY',
        type: 'PRIMARY_KEY',
        columns: primaryKeyColumns,
      }] : [],
    }];
  });
  const compilationFileDatasetTables: TaskCompilationMetadataFileDatasetTable[] =
    (fileDatasetMetadataQuery.data?.tables ?? [])
      .map((table) => ({
        id: table.fileDatasetTableId,
        fileDatasetId: table.fileDatasetId,
        code: table.code,
        name: table.name,
        datasetType: table.datasetType,
        parseStatus: table.parseStatus,
        fileStatus: table.fileStatus,
        columns: [...table.fields]
          .sort((left, right) => left.sortOrder - right.sortOrder)
          .map(fileDatasetColumnSchema),
      }))
      .sort((left, right) => left.id.localeCompare(right.id));

  const metadataSnapshot: TaskCompilationMetadataSnapshot = {
    dataSources: [...compilationDataSources.values()]
      .map((dataSource) => ({
        ...dataSource,
        tables: [...dataSource.tables].sort((left, right) => left.tableName.localeCompare(right.tableName)),
      }))
      .sort((left, right) => left.id.localeCompare(right.id)),
    models: compilationModels.sort((left, right) => left.id.localeCompare(right.id)),
    fileDatasetTables: compilationFileDatasetTables,
  };

  const nodeSummaries = new Map<string, CanvasNodeRuntimeSummary>();
  definition.nodes.forEach((node) => {
    if (node.type === CanvasNodeType.FileDatasetInput) {
      const metadataById = new Map(
        (fileDatasetMetadataQuery.data?.tables ?? [])
          .map((table) => [table.fileDatasetTableId, table] as const),
      );
      const selectedTables = node.configuration.tables.flatMap((selection) => {
        const table = metadataById.get(selection.fileDatasetTableId);
        if (!table) return [];
        const columns = [...table.fields]
          .sort((left, right) => left.sortOrder - right.sortOrder)
          .map(fileDatasetColumnSchema);
        return [{
          metadata: table,
          summary: {
            fileDatasetTableId: table.fileDatasetTableId,
            tableName: table.name,
            tableCode: table.code,
            datasetType: table.datasetType,
            status: table.parseStatus,
            schema: {
              name: table.code,
              origin: {
                kind: 'FILE_DATASET' as const,
                dataSourceId: null,
                tableName: null,
                modelId: null,
                modelCode: null,
                modelSchemaVersion: null,
                fileDatasetTableId: table.fileDatasetTableId,
              },
              columns,
              datasetKind: 'BOUNDED' as const,
              eventTimeColumn: null,
              watermarkDelay: null,
            },
          },
        }];
      });
      const table = selectedTables[0]?.metadata;
      if (!table) return;
      const geometryField = table.fields.find(
        (field) => field.platformTypeDefinition.type === 'GEOMETRY'
          && field.platformTypeDefinition.geometry,
      );
      const geometry = geometryField?.platformTypeDefinition.geometry;
      nodeSummaries.set(node.id, {
        kind: 'FILE_DATASET',
        fileDatasetName: table.fileDatasetName,
        tableName: table.name,
        tableCode: table.code,
        datasetType: table.datasetType,
        status: table.parseStatus,
        geometry: geometryField && geometry ? {
          fieldName: geometryField.name,
          kind: geometry.kind,
          crs: geometry.crs,
          dimension: geometry.dimension,
        } : null,
        tables: selectedTables.map((item) => item.summary),
      });
      return;
    }
    if (node.type === CanvasNodeType.ModelInput
      || node.type === CanvasNodeType.ModelOutput
      || node.type === CanvasNodeType.ModelSnapshotSyncOutput) {
      const modelId = node.type === CanvasNodeType.ModelInput
        ? node.configuration.models[0]?.modelId ?? ''
        : node.type === CanvasNodeType.ModelOutput
          ? node.configuration.writes?.[0]?.targetModelId ?? ''
          : node.configuration.targetModelId;
      const detail = modelById.get(modelId);
      const dataSource = detail ? dataSourceById.get(detail.model.storageDataSourceId) : undefined;
      if (!detail || !dataSource) return;
      nodeSummaries.set(node.id, {
        kind: 'MODEL',
        modelName: detail.model.name,
        modelCode: detail.model.code,
        modelSchemaVersion: detail.model.schemaVersion,
        dataSourceName: dataSource.name,
        qualifiedTableName: detail.model.physicalTableName,
      });
      return;
    }
    if (node.type === CanvasNodeType.HttpApiInput) {
      const dataSource = dataSourceById.get(node.configuration.dataSourceId);
      const resource = apiResourceByKey.get(
        `${node.configuration.dataSourceId}\u0000${node.configuration.resources[0]?.resourceId ?? ''}`,
      );
      if (!dataSource || !resource) return;
      nodeSummaries.set(node.id, {
        kind: 'HTTP_API',
        dataSourceName: dataSource.name,
        qualifiedTableName: resource.name,
      });
      return;
    }
    if (node.type === CanvasNodeType.SpatialServiceInput) {
      const dataSource = dataSourceById.get(node.configuration.dataSourceId);
      const resource = spatialResourceByKey.get(`${node.configuration.dataSourceId}\u0000${node.configuration.resources[0]?.resourceId ?? ''}`);
      if (!dataSource || !resource) return;
      nodeSummaries.set(node.id, { kind: 'HTTP_API', dataSourceName: dataSource.name, qualifiedTableName: resource.name });
      return;
    }
    if (node.type === CanvasNodeType.KafkaInput) {
      const dataSource = dataSourceById.get(node.configuration.dataSourceId);
      if (!dataSource) return;
      nodeSummaries.set(node.id, {
        kind: 'KAFKA',
        dataSourceName: dataSource.name,
        qualifiedTableName: node.configuration.topic,
        fieldCount: node.configuration.valueSchema.columns.length,
      });
      return;
    }
    if (node.type === CanvasNodeType.KafkaOutput) {
      const dataSource = dataSourceById.get(node.configuration.dataSourceId);
      const first = node.configuration.writes?.[0];
      if (!dataSource || !first) return;
      nodeSummaries.set(node.id, {
        kind: 'KAFKA',
        dataSourceName: dataSource.name,
        qualifiedTableName: first.topic,
        fieldCount: first.valueFormat == null
          ? first.valueSchema?.columns.length ?? 0
          : first.valueColumnNames.length,
      });
      return;
    }
    if (node.type === CanvasNodeType.TdEngineTmqInput) {
      const dataSource = dataSourceById.get(node.configuration.dataSourceId);
      const topic = tmqTopicByKey.get(`${node.configuration.dataSourceId}\u0000${node.configuration.topicName}`);
      if (!dataSource || !topic) return;
      nodeSummaries.set(node.id, {
        kind: 'TDENGINE_TMQ',
        dataSourceName: dataSource.name,
        qualifiedTableName: `${topic.databaseName ?? '—'}.${topic.supertableName ?? '—'}`,
        fieldCount: topic.columns.length,
      });
      return;
    }
    if (node.type === CanvasNodeType.JdbcQueryInput) {
      const dataSource = dataSourceById.get(node.configuration.dataSourceId);
      if (!dataSource) return;
      nodeSummaries.set(node.id, {
        kind: 'JDBC',
        dataSourceName: dataSource.name,
        dataSourceType: dataSource.type,
        qualifiedTableName: node.configuration.outputTableName,
      });
      return;
    }
    if (node.type === CanvasNodeType.FileOutput) {
      const dataSource = dataSourceById.get(node.configuration.dataSourceId);
      if (!dataSource || dataSource.connection.kind !== 'S3') return;
      nodeSummaries.set(node.id, {
        kind: 'S3',
        dataSourceName: dataSource.name,
        bucketName: dataSource.connection.bucket,
      });
      return;
    }
    if (node.type === CanvasNodeType.JdbcInput) {
      const dataSourceId = node.configuration.dataSourceId;
      const dataSource = dataSourceById.get(dataSourceId);
      if (!dataSource || dataSource.connection.kind !== 'JDBC') return;
      const tables = node.configuration.tables.map(({ tableName }) => {
        const tableMetadata = jdbcTableByKey.get(
          tableRequestKey(dataSourceId, jdbcTableIdentifier(tableName)),
        );
        return {
          tableName,
          primaryKeyColumns: tableMetadata?.uniqueKeys?.find(
            (key) => key.type === 'PRIMARY_KEY',
          )?.columns ?? [],
        };
      });
      nodeSummaries.set(node.id, {
        kind: 'JDBC',
        dataSourceName: dataSource.name,
        dataSourceType: dataSource.type,
        qualifiedTableName: tables[0]?.tableName ?? '',
        ...(tables.length === 1 ? { primaryKeyColumns: tables[0].primaryKeyColumns } : {}),
        tables,
      });
      return;
    }
    if (node.type !== CanvasNodeType.JdbcOutput
      && node.type !== CanvasNodeType.JdbcSnapshotSyncOutput) return;
    const dataSourceId = node.configuration.dataSourceId;
    const tableName = node.type === CanvasNodeType.JdbcOutput
      ? node.configuration.writes?.[0]?.targetTableName ?? ''
      : node.configuration.targetTableName;
    const dataSource = dataSourceById.get(dataSourceId);
    if (!dataSource || dataSource.connection.kind !== 'JDBC' || !tableName) return;
    const tableMetadata = jdbcTableByKey.get(
      tableRequestKey(dataSourceId, jdbcTableIdentifier(tableName)),
    );
    const primaryKeyColumns = tableMetadata?.uniqueKeys.find(
      (key) => key.type === 'PRIMARY_KEY',
    )?.columns ?? [];
    nodeSummaries.set(node.id, {
      kind: 'JDBC',
      dataSourceName: dataSource.name,
      dataSourceType: dataSource.type,
      qualifiedTableName: tableName,
      ...(tableMetadata ? { primaryKeyColumns } : {}),
    });
  });

  const modelNodeIds = (modelId: string) => referenceNodeIds(
    metadataReferences,
    (reference) => reference.kind === 'MODEL' && reference.modelId === modelId,
  );
  const dataSourceNodeIds = (dataSourceId: string) => {
    const directNodeIds = referenceNodeIds(
      metadataReferences,
      (reference) => 'dataSourceId' in reference && reference.dataSourceId === dataSourceId,
    );
    const modelNodeIdsForDataSource = metadataReferences.flatMap((reference) => (
      reference.kind === 'MODEL'
        && modelById.get(reference.modelId)?.model.storageDataSourceId === dataSourceId
        ? [reference.nodeId]
        : []
    ));
    return [...new Set([...directNodeIds, ...modelNodeIdsForDataSource])];
  };
  const jdbcTableNodeIds = (request: CanvasJdbcMetadataRequest) => referenceNodeIds(
    metadataReferences,
    (reference) => reference.kind === 'JDBC_TABLE'
      && reference.dataSourceId === request.dataSourceId
      && reference.tableName === request.tableName,
  );
  const apiResourceNodeIds = (request: CanvasApiMetadataRequest) => referenceNodeIds(
    metadataReferences,
    (reference) => reference.kind === 'HTTP_API_RESOURCE'
      && reference.dataSourceId === request.dataSourceId
      && reference.resourceId === request.resourceId,
  );
  const tmqTopicNodeIds = (reference: TdEngineTmqTopicMetadataReference) => referenceNodeIds(
    metadataReferences,
    (candidate) => candidate.kind === 'TDENGINE_TMQ_TOPIC'
      && candidate.dataSourceId === reference.dataSourceId
      && candidate.topicName === reference.topicName,
  );

  const metadataIssues: CanvasMetadataIssue[] = [];
  if (fileDatasetMetadataQuery.isError) {
    metadataIssues.push({
      code: 'FILE_DATASET_METADATA_READ_FAILED',
      message: queryFailureMessage('读取文件数据集表元数据失败', fileDatasetMetadataQuery.error),
      nodeIds: referenceNodeIds(
        metadataReferences,
        (reference) => reference.kind === 'FILE_DATASET_TABLE',
      ),
    });
  }
  modelQueries.forEach((query, index) => {
    if (!query.isError) return;
    const modelId = modelIds[index];
    metadataIssues.push({
      code: 'MODEL_READ_FAILED',
      message: queryFailureMessage(`读取模型 ${modelId} 失败`, query.error),
      nodeIds: modelNodeIds(modelId),
    });
  });
  dataSourceQueries.forEach((query, index) => {
    if (!query.isError) return;
    const dataSourceId = dataSourceIds[index];
    metadataIssues.push({
      code: 'DATA_SOURCE_READ_FAILED',
      message: queryFailureMessage(`读取数据源 ${dataSourceId} 失败`, query.error),
      nodeIds: dataSourceNodeIds(dataSourceId),
    });
  });
  jdbcTableQueries.forEach((query, index) => {
    if (!query.isError) return;
    const request = jdbcTableRequests[index];
    metadataIssues.push({
      code: 'TABLE_METADATA_READ_FAILED',
      message: queryFailureMessage(`读取物理表 ${request.tableName} 元数据失败`, query.error),
      nodeIds: jdbcTableNodeIds(request),
    });
  });
  jdbcTableRequests.forEach((request) => {
    const metadata = jdbcTableByKey.get(
      tableRequestKey(request.dataSourceId, jdbcTableIdentifier(request.tableName)),
    );
    const unsupported = metadata?.columns.filter((column) => !column.platformTypeDefinition) ?? [];
    if (unsupported.length === 0) return;
    metadataIssues.push({
      code: 'COLUMN_TYPE_MAPPING_UNSUPPORTED',
      message: `物理表 ${request.tableName} 存在不能无损映射的平台字段：${unsupported.map((column) => column.name).join('、')}`,
      nodeIds: jdbcTableNodeIds(request),
    });
  });
  apiResourceQueries.forEach((query, index) => {
    if (!query.isError) return;
    const request = apiRequests[index];
    metadataIssues.push({
      code: 'API_RESOURCE_READ_FAILED',
      message: queryFailureMessage(`读取 API 资源 ${request.resourceId} 失败`, query.error),
      nodeIds: apiResourceNodeIds(request),
    });
  });
  tmqTopicQueries.forEach((query, index) => {
    if (!query.isError) return;
    const reference = tmqReferences[index];
    metadataIssues.push({
      code: 'TDENGINE_TMQ_TOPIC_READ_FAILED',
      message: queryFailureMessage(`读取 TMQ Topic ${reference.topicName} 失败`, query.error),
      nodeIds: tmqTopicNodeIds(reference),
    });
  });
  const allQueries = [
    ...modelQueries,
    ...dataSourceQueries,
    ...jdbcTableQueries,
    ...apiResourceQueries,
    ...tmqTopicQueries,
    ...(referencedFileTableIds.length > 0 ? [fileDatasetMetadataQuery] : []),
  ];
  return {
    metadataSnapshot,
    nodeSummaries,
    issues: metadataIssues,
    loading: allQueries.some((query) => query.isFetching),
    error: metadataIssues.length > 0,
    retry: async () => {
      await Promise.all(allQueries.map((query) => query.refetch()));
    },
  };
};
