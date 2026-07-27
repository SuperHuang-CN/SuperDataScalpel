import { useQueries, useQuery } from '@tanstack/react-query';
import {
  fetchApiResource,
  fetchDataSource,
  fetchTableMetadata,
  type DataSource,
  type TableIdentifier,
  type TableMetadata,
} from '../../datasource';
import {
  fetchDataModel,
  fetchPhysicalTableInspection,
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
import type {
  TaskCompilationMetadataDataSource,
  TaskCompilationMetadataModel,
  TaskCompilationMetadataSnapshot,
  TaskCompilationMetadataTable,
  TaskCompilationMetadataFileDatasetTable,
} from './taskCompilationTypes';

type CanvasMetadataRole = 'SOURCE' | 'STORAGE';

interface CanvasJdbcMetadataRequest {
  dataSourceId: string;
  tableName: string;
  role: CanvasMetadataRole;
}

interface CanvasModelTableRequest {
  modelId: string;
  dataSourceId: string;
  identifier: TableIdentifier;
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
  | 'FILE_DATASET_METADATA_READ_FAILED'
  | 'MODEL_PHYSICAL_INSPECTION_FAILED'
  | 'MODEL_PHYSICAL_SCHEMA_MISMATCH'
  | 'MODEL_PHYSICAL_METADATA_INCOMPLETE';

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
): CanvasColumnSchema => ({
  name: column.name,
  fieldType: column.logicalType,
  length: column.logicalType === 'STRING' ? column.length : null,
  precision: column.logicalType === 'DECIMAL' ? column.precision : null,
  scale: column.logicalType === 'DECIMAL' ? column.scale : null,
  nullable: column.nullable,
  defaultValue: column.defaultValue,
  autoIncrement: column.autoIncrement,
  generated: column.generated,
  comment: column.comment,
});

const modelColumnSchema = (
  field: DataModelField,
  physicalColumn: TableMetadata['columns'][number],
): CanvasColumnSchema => ({
  name: field.code,
  fieldType: field.fieldType,
  length: field.fieldType === 'STRING' ? field.length : null,
  precision: field.fieldType === 'DECIMAL' ? field.precision : null,
  scale: field.fieldType === 'DECIMAL' ? field.scale : null,
  nullable: field.nullable,
  defaultValue: physicalColumn.defaultValue,
  autoIncrement: physicalColumn.autoIncrement,
  generated: physicalColumn.generated,
  comment: field.description ?? physicalColumn.comment,
});

const requirePhysicalColumn = (
  columns: Map<string, TableMetadata['columns'][number]>,
  field: DataModelField,
) => {
  const column = columns.get(field.code);
  if (!column) throw new Error(`模型字段缺少对应物理字段：${field.code}`);
  return column;
};

const jdbcMetadataRequests = (definition: CanvasDefinition): CanvasJdbcMetadataRequest[] => {
  const requests = definition.nodes.flatMap((node): CanvasJdbcMetadataRequest[] => {
    if (node.type === CanvasNodeType.JdbcInput) {
      const { dataSourceId, tableName } = node.configuration;
      return dataSourceId && tableName ? [{ dataSourceId, tableName, role: 'SOURCE' }] : [];
    }
    if (node.type === CanvasNodeType.JdbcOutput) {
      const { dataSourceId, targetTableName } = node.configuration;
      return dataSourceId && targetTableName
        ? [{ dataSourceId, tableName: targetTableName, role: 'STORAGE' }]
        : [];
    }
    return [];
  });
  return [...new Map(requests.map((request) => [jdbcRequestKey(request), request])).values()];
};

const apiMetadataRequests = (definition: CanvasDefinition): CanvasApiMetadataRequest[] => {
  const requests = definition.nodes.flatMap((node): CanvasApiMetadataRequest[] => (
    node.type === CanvasNodeType.HttpApiInput
      && node.configuration.dataSourceId
      && node.configuration.resourceId
      ? [{
        dataSourceId: node.configuration.dataSourceId,
        resourceId: node.configuration.resourceId,
      }]
      : []
  ));
  return [...new Map(requests.map((request) => [
    `${request.dataSourceId}\u0000${request.resourceId}`,
    request,
  ])).values()];
};

const referencedModelIds = (definition: CanvasDefinition) => [...new Set(
  definition.nodes.flatMap((node) => {
    if (node.type === CanvasNodeType.ModelInput) return node.configuration.modelId ? [node.configuration.modelId] : [];
    if (node.type === CanvasNodeType.ModelOutput) {
      return node.configuration.targetModelId ? [node.configuration.targetModelId] : [];
    }
    return [];
  }),
)];

const physicalModelIds = (definition: CanvasDefinition) => [...new Set(
  definition.nodes.flatMap((node) => {
    if (node.type === CanvasNodeType.ModelInput) return node.configuration.modelId ? [node.configuration.modelId] : [];
    if (node.type === CanvasNodeType.ModelOutput) {
      return node.configuration.targetModelId ? [node.configuration.targetModelId] : [];
    }
    return [];
  }),
)];

const kafkaDataSourceIds = (definition: CanvasDefinition) => [...new Set(
  definition.nodes.flatMap((node) => (
    (node.type === CanvasNodeType.KafkaInput || node.type === CanvasNodeType.KafkaOutput)
      && node.configuration.dataSourceId
      ? [node.configuration.dataSourceId]
      : []
  )),
)];

const fileDatasetTableIds = (definition: CanvasDefinition) => [...new Set(
  definition.nodes.flatMap((node) => (
    node.type === CanvasNodeType.FileDatasetInput && node.configuration.fileDatasetTableId
      ? [node.configuration.fileDatasetTableId]
      : []
  )),
)].sort();

const fileDatasetColumnSchema = (
  field: FileDatasetCanvasTableMetadata['fields'][number],
): CanvasColumnSchema => ({
  name: field.name,
  fieldType: field.fieldType,
  length: field.length,
  precision: field.precision,
  scale: field.scale,
  nullable: field.nullable,
  defaultValue: null,
  autoIncrement: false,
  generated: false,
  comment: null,
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
      purposes: [...dataSource.purposes].sort(),
      tables,
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
      purposes: [...dataSource.purposes].sort(),
      tables,
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
      purposes: [...dataSource.purposes].sort(),
      tables: [],
    }
    : null
);

export const useCanvasMetadataSnapshot = (definition: CanvasDefinition) => {
  const jdbcRequests = jdbcMetadataRequests(definition);
  const apiRequests = apiMetadataRequests(definition);
  const modelIds = referencedModelIds(definition);
  const physicalIds = physicalModelIds(definition);
  const physicalIdSet = new Set(physicalIds);
  const kafkaIds = kafkaDataSourceIds(definition);
  const referencedFileTableIds = fileDatasetTableIds(definition);
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
  const modelInspectionQueries = useQueries({
    queries: physicalIds.map((modelId) => ({
      queryKey: ['data-models', modelId, 'physical-table'],
      queryFn: () => fetchPhysicalTableInspection(modelId),
      staleTime: 60_000,
    })),
  });
  const modelById = new Map(modelIds.map((id, index) => [id, modelQueries[index].data]));
  const modelInspectionById = new Map(physicalIds.map((id, index) => [
    id,
    modelInspectionQueries[index].data,
  ]));
  const modelDetails = modelIds.flatMap((id) => {
    const detail = modelById.get(id);
    return detail ? [detail] : [];
  });
  const physicalModelDetails = modelDetails.filter((detail) => physicalIdSet.has(detail.model.id));
  const dataSourceIds = [...new Set([
    ...jdbcRequests.map((request) => request.dataSourceId),
    ...apiRequests.map((request) => request.dataSourceId),
    ...kafkaIds,
    ...physicalModelDetails.map((detail) => detail.model.storageDataSourceId),
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

  const modelTableRequests: CanvasModelTableRequest[] = physicalModelDetails.map((detail) => ({
    modelId: detail.model.id,
    dataSourceId: detail.model.storageDataSourceId,
    identifier: {
      catalog: detail.model.catalogName,
      schema: detail.model.schemaName,
      table: detail.model.physicalTableName,
    },
  }));
  const modelTableQueries = useQueries({
    queries: modelTableRequests.map((request) => ({
      queryKey: ['data-sources', request.dataSourceId, 'table-metadata', request.identifier],
      queryFn: () => fetchTableMetadata(request.dataSourceId, request.identifier),
      staleTime: 60_000,
    })),
  });
  const modelTableById = new Map(modelTableRequests.map((request, index) => [
    request.modelId,
    modelTableQueries[index].data,
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
        return metadata ? [{
          tableName: request.tableName,
          objectType: metadata.table.type.toUpperCase() === 'VIEW' ? 'VIEW' : 'TABLE',
          columns: metadata.columns.map(canvasColumnSchema),
        }] : [];
      });
    const compiled = compilationDataSource(dataSource, tables);
    if (compiled) {
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
          })),
        }] : [];
      });
    const apiCompiled = apiCompilationDataSource(dataSource, resources);
    if (apiCompiled) {
      compilationDataSources.set(dataSourceId, apiCompiled);
      return;
    }
    const kafkaCompiled = kafkaCompilationDataSource(dataSource);
    if (kafkaCompiled) compilationDataSources.set(dataSourceId, kafkaCompiled);
  });

  const compilationModels = modelDetails.flatMap((detail): TaskCompilationMetadataModel[] => {
    const physical = modelTableById.get(detail.model.id);
    const inspection = modelInspectionById.get(detail.model.id);
    if (!physical || !inspection?.compatible) return [];
    const physicalByName = new Map(physical.columns.map((column) => [column.name, column]));
    if (detail.fields.some((field) => !physicalByName.has(field.code))) return [];
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
      columns: [...detail.fields]
        .sort((left, right) => left.sortOrder - right.sortOrder)
        .map((field) => modelColumnSchema(field, requirePhysicalColumn(physicalByName, field))),
    }];
  });
  const compilationFileDatasetTables: TaskCompilationMetadataFileDatasetTable[] =
    (fileDatasetMetadataQuery.data?.tables ?? [])
      .map((table) => ({
        id: table.fileDatasetTableId,
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
      const table = fileDatasetMetadataQuery.data?.tables.find(
        (candidate) => candidate.fileDatasetTableId === node.configuration.fileDatasetTableId,
      );
      if (!table) return;
      nodeSummaries.set(node.id, {
        kind: 'FILE_DATASET',
        fileDatasetName: table.fileDatasetName,
        tableName: table.name,
        tableCode: table.code,
        status: table.parseStatus,
      });
      return;
    }
    if (node.type === CanvasNodeType.ModelInput || node.type === CanvasNodeType.ModelOutput) {
      const modelId = node.type === CanvasNodeType.ModelInput
        ? node.configuration.modelId
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
        qualifiedTableName: [
          detail.model.catalogName,
          detail.model.schemaName,
          detail.model.physicalTableName,
        ].filter(Boolean).join('.'),
      });
      return;
    }
    if (node.type === CanvasNodeType.HttpApiInput) {
      const dataSource = dataSourceById.get(node.configuration.dataSourceId);
      const resource = apiResourceByKey.get(
        `${node.configuration.dataSourceId}\u0000${node.configuration.resourceId}`,
      );
      if (!dataSource || !resource) return;
      nodeSummaries.set(node.id, {
        kind: 'HTTP_API',
        dataSourceName: dataSource.name,
        qualifiedTableName: resource.name,
      });
      return;
    }
    if (node.type === CanvasNodeType.KafkaInput || node.type === CanvasNodeType.KafkaOutput) {
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
    if (node.type !== CanvasNodeType.JdbcInput && node.type !== CanvasNodeType.JdbcOutput) return;
    const dataSourceId = node.configuration.dataSourceId;
    const tableName = node.type === CanvasNodeType.JdbcInput
      ? node.configuration.tableName
      : node.configuration.targetTableName;
    const dataSource = dataSourceById.get(dataSourceId);
    if (!dataSource || dataSource.connection.kind !== 'JDBC' || !tableName) return;
    nodeSummaries.set(node.id, {
      kind: 'JDBC',
      dataSourceName: dataSource.name,
      qualifiedTableName: [
        dataSource.connection.databaseName,
        dataSource.connection.schemaName,
        tableName,
      ].filter(Boolean).join('.'),
    });
  });

  const modelNodeIds = (modelId: string) => definition.nodes.flatMap((node) => (
    (node.type === CanvasNodeType.ModelInput && node.configuration.modelId === modelId)
      || (node.type === CanvasNodeType.ModelOutput && node.configuration.targetModelId === modelId)
      ? [node.id]
      : []
  ));
  const dataSourceNodeIds = (dataSourceId: string) => definition.nodes.flatMap((node) => {
    if ((node.type === CanvasNodeType.JdbcInput || node.type === CanvasNodeType.JdbcOutput
        || node.type === CanvasNodeType.HttpApiInput || node.type === CanvasNodeType.KafkaInput
        || node.type === CanvasNodeType.KafkaOutput)
        && node.configuration.dataSourceId === dataSourceId) return [node.id];
    if (node.type === CanvasNodeType.ModelInput || node.type === CanvasNodeType.ModelOutput) {
      const modelId = node.type === CanvasNodeType.ModelInput
        ? node.configuration.modelId
        : node.configuration.targetModelId;
      return modelById.get(modelId)?.model.storageDataSourceId === dataSourceId ? [node.id] : [];
    }
    return [];
  });
  const jdbcTableNodeIds = (request: CanvasJdbcMetadataRequest) => definition.nodes.flatMap((node) => {
    if (node.type === CanvasNodeType.JdbcInput
        && node.configuration.dataSourceId === request.dataSourceId
        && node.configuration.tableName === request.tableName) return [node.id];
    if (node.type === CanvasNodeType.JdbcOutput
        && node.configuration.dataSourceId === request.dataSourceId
        && node.configuration.targetTableName === request.tableName) return [node.id];
    return [];
  });
  const apiResourceNodeIds = (request: CanvasApiMetadataRequest) => definition.nodes.flatMap((node) => (
    node.type === CanvasNodeType.HttpApiInput
      && node.configuration.dataSourceId === request.dataSourceId
      && node.configuration.resourceId === request.resourceId
      ? [node.id]
      : []
  ));

  const metadataIssues: CanvasMetadataIssue[] = [];
  if (fileDatasetMetadataQuery.isError) {
    metadataIssues.push({
      code: 'FILE_DATASET_METADATA_READ_FAILED',
      message: queryFailureMessage('读取文件数据集表元数据失败', fileDatasetMetadataQuery.error),
      nodeIds: definition.nodes.flatMap((node) => (
        node.type === CanvasNodeType.FileDatasetInput ? [node.id] : []
      )),
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
  modelInspectionQueries.forEach((query, index) => {
    if (!query.isError) return;
    const modelId = physicalIds[index];
    metadataIssues.push({
      code: 'MODEL_PHYSICAL_INSPECTION_FAILED',
      message: queryFailureMessage(`检查模型 ${modelId} 的物理表失败`, query.error),
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
  apiResourceQueries.forEach((query, index) => {
    if (!query.isError) return;
    const request = apiRequests[index];
    metadataIssues.push({
      code: 'API_RESOURCE_READ_FAILED',
      message: queryFailureMessage(`读取 API 资源 ${request.resourceId} 失败`, query.error),
      nodeIds: apiResourceNodeIds(request),
    });
  });
  modelTableQueries.forEach((query, index) => {
    if (!query.isError) return;
    const request = modelTableRequests[index];
    metadataIssues.push({
      code: 'TABLE_METADATA_READ_FAILED',
      message: queryFailureMessage(`读取模型物理表 ${request.identifier.table} 元数据失败`, query.error),
      nodeIds: modelNodeIds(request.modelId),
    });
  });
  physicalModelDetails.forEach((detail) => {
    const inspection = modelInspectionById.get(detail.model.id);
    if (inspection && !inspection.compatible) {
      metadataIssues.push({
        code: 'MODEL_PHYSICAL_SCHEMA_MISMATCH',
        message: `模型 ${detail.model.name} 不可用于 Canvas：${inspection.message}`,
        nodeIds: modelNodeIds(detail.model.id),
      });
      return;
    }
    const physical = modelTableById.get(detail.model.id);
    if (!inspection?.compatible || !physical) return;
    const physicalNames = new Set(physical.columns.map((column) => column.name));
    const missingFields = detail.fields.filter((field) => !physicalNames.has(field.code));
    if (missingFields.length > 0) {
      metadataIssues.push({
        code: 'MODEL_PHYSICAL_METADATA_INCOMPLETE',
        message: `模型 ${detail.model.name} 的物理表元数据缺少字段：${missingFields.map((field) => field.code).join('、')}`,
        nodeIds: modelNodeIds(detail.model.id),
      });
    }
  });
  const allQueries = [
    ...modelQueries,
    ...modelInspectionQueries,
    ...dataSourceQueries,
    ...jdbcTableQueries,
    ...apiResourceQueries,
    ...modelTableQueries,
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
