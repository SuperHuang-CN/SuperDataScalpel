import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { PropsWithChildren } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  fetchApiResource,
  fetchDataSource,
  fetchTableMetadata,
  type ApiResource,
  type DataSource,
  type TableMetadata,
} from '../../datasource';
import {
  fetchDataModel,
  type DataModelDetail,
} from '../../model';
import { fetchFileDatasetCanvasMetadata } from '../../filedataset';
import {
  CANVAS_SCHEMA_MINOR_VERSION,
  CANVAS_SCHEMA_VERSION,
  type CanvasDefinition,
} from './canvasTypes';
import { useCanvasMetadataSnapshot } from './useCanvasMetadataSnapshot';

vi.mock('../../datasource', () => ({
  fetchApiResource: vi.fn(),
  fetchDataSource: vi.fn(),
  fetchTableMetadata: vi.fn(),
}));

vi.mock('../../model', () => ({
  fetchDataModel: vi.fn(),
}));

vi.mock('../../filedataset', () => ({
  fetchFileDatasetCanvasMetadata: vi.fn(),
}));

const dataSourceId = '55859069-6387-4390-b850-104845ee5370';

const definition: CanvasDefinition = {
  schemaVersion: CANVAS_SCHEMA_VERSION,
  schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
  nodes: [
    {
      id: '4add70a7-4948-42a5-af66-e56dbaccad3e',
      type: 'JDBC_INPUT',
      name: '订单输入',
      layout: { x: 10, y: 10, width: 240, height: 120 },
      configuration: { dataSourceId, tables: [{ tableName: 'orders', readOptions: [] }] },
    },
    {
      id: '8fd542d5-37c2-4769-8ea6-47dff963073a',
      type: 'JDBC_OUTPUT',
      name: '订单输出',
      layout: { x: 400, y: 10, width: 240, height: 120 },
      configuration: {
        sourceTableName: 'orders',
        dataSourceId,
        targetTableName: 'orders',
        writeMode: 'APPEND',
        upsertKeyColumns: [],
        columnMappings: [{ sourceColumnName: 'order_id', targetColumnName: 'order_id' }],
      },
    },
  ],
  edges: [{
    id: 'be10167e-9a7d-46c2-8b68-3d09e358b791',
    sourceNodeId: '4add70a7-4948-42a5-af66-e56dbaccad3e',
    targetNodeId: '8fd542d5-37c2-4769-8ea6-47dff963073a',
  }],
};

const dataSource: DataSource = {
  id: dataSourceId,
  code: 'order-db',
  name: '订单数据库',
  directoryId: null,
  purposes: ['SOURCE', 'STORAGE', 'DISTRIBUTION'],
  type: 'POSTGRESQL',
  connectionKind: 'JDBC',
  enabled: true,
  description: null,
  connection: {
    kind: 'JDBC',
    host: '127.0.0.1',
    port: 5432,
    databaseName: 'demo',
    schemaName: 'public',
    username: 'demo',
    options: {},
    passwordConfigured: true,
  },
  createdAt: '2026-07-17T00:00:00Z',
  updatedAt: '2026-07-17T00:00:00Z',
};

const tableMetadata: TableMetadata = {
  table: {
    identifier: { catalog: null, schema: 'public', table: 'orders' },
    type: 'VIEW',
    comment: '订单视图',
  },
  columns: [{
    name: 'order_id',
    ordinal: 1,
    jdbcType: -5,
    nativeType: 'int8',
    logicalType: 'LONG',
    platformTypeDefinition: {
      type: 'LONG', length: null, precision: null, scale: null, geometry: null,
    },
    length: null,
    precision: 19,
    scale: 0,
    nullable: false,
    defaultValue: null,
    autoIncrement: false,
    generated: false,
    comment: '订单ID',
  }, {
    name: 'description',
    ordinal: 2,
    jdbcType: 12,
    nativeType: 'varchar',
    logicalType: 'STRING',
    platformTypeDefinition: {
      type: 'STRING', length: 128, precision: null, scale: null, geometry: null,
    },
    length: 128,
    precision: 128,
    scale: null,
    nullable: true,
    defaultValue: null,
    autoIncrement: false,
    generated: false,
    comment: '订单说明',
  }, {
    name: 'amount',
    ordinal: 3,
    jdbcType: 3,
    nativeType: 'decimal',
    logicalType: 'DECIMAL',
    platformTypeDefinition: {
      type: 'DECIMAL', length: null, precision: 12, scale: 2, geometry: null,
    },
    length: 64,
    precision: 12,
    scale: 2,
    nullable: false,
    defaultValue: '0',
    autoIncrement: false,
    generated: false,
    comment: '订单金额',
  }],
  primaryKey: null,
  indexes: [],
  uniqueKeys: [],
};

const modelId = '805c80b3-959e-4690-90d3-5c2d613864c1';

const apiResourceId = 'fca550ff-85d7-4d42-92ba-6c32b0446281';

const httpApiDataSource: DataSource = {
  id: dataSourceId,
  code: 'order-api',
  name: '订单接口',
  directoryId: null,
  purposes: ['SOURCE'],
  type: 'HTTP_API',
  connectionKind: 'HTTP_API',
  enabled: true,
  description: null,
  connection: {
    kind: 'HTTP_API',
    configuration: {
      baseUrl: 'https://api.example.com',
      defaultHeaders: [],
      connectTimeoutMs: 5000,
      requestTimeoutMs: 30000,
      minimumRequestIntervalMs: 0,
      maxRetries: 2,
      authentication: { type: 'NONE' },
      signingSecretConfigured: false,
      signingPrivateKeyConfigured: false,
    },
  },
  createdAt: '2026-07-22T00:00:00Z',
  updatedAt: '2026-07-22T00:00:00Z',
};

const apiResource: ApiResource = {
  id: apiResourceId,
  dataSourceId,
  code: 'orders',
  name: '订单列表',
  connectorType: 'GENERIC_HTTP',
  enabled: true,
  request: { method: 'GET', path: '/orders', queryParameters: [], headers: [], bodyTemplate: null },
  signing: { type: 'NONE', canonicalTemplate: null, timestamp: null, nonce: null, output: null },
  invocationType: 'PAGINATED_REQUEST',
  pagination: {
    type: 'PAGE_NUMBER',
    location: 'QUERY',
    pageParameter: 'page',
    pageSizeParameter: 'pageSize',
    initialPage: 1,
    pageSize: 100,
    hasMorePointer: null,
    totalPagesPointer: '/totalPages',
  },
  asyncJob: null,
  recordsPointer: '/data',
  outputFields: [{
    name: 'order_id',
    jsonPointer: '/id',
    type: { type: 'LONG', length: null, precision: null, scale: null },
    nullable: false,
    comment: '订单 ID',
  }],
  limits: { maxPages: 100, maxRows: 10000, maxResponseBytes: 10485760, maxDurationSeconds: 600 },
  createdAt: '2026-07-22T00:00:00Z',
  updatedAt: '2026-07-22T00:00:00Z',
};

const httpApiDefinition: CanvasDefinition = {
  schemaVersion: CANVAS_SCHEMA_VERSION,
  schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
  nodes: [{
    id: '16b03251-cff6-40f1-971c-79cf26430b30',
    type: 'HTTP_API_INPUT',
    name: '订单 API 输入',
    layout: { x: 10, y: 10, width: 240, height: 120 },
    configuration: {
      dataSourceId,
      resources: [{ resourceId: apiResourceId, outputTableName: 'api_orders', runtimeParameters: [{ name: 'startDate', value: '2026-07-01' }] }],
    },
  }],
  edges: [],
};

const modelDetail: DataModelDetail = {
  model: {
    id: modelId,
    code: 'order_model',
    name: '订单模型',
    directoryId: null,
    storageDataSourceId: dataSourceId,
    storageDataSourceName: '订单数据库',
    catalogName: 'warehouse',
    schemaName: 'model_schema',
    physicalTableName: 'dwd_order_model',
    physicalTableMode: 'MANAGED',
    clickHouseOrderByColumns: [],
    status: 'PUBLISHED',
    schemaVersion: 7,
    description: null,
    createdAt: '2026-07-17T00:00:00Z',
    updatedAt: '2026-07-17T00:00:00Z',
  },
  fields: [{
    id: '822fab84-f99d-4a62-b17a-d68ca1b7f7c8',
    modelId,
    code: 'order_id',
    name: '订单ID',
    fieldType: 'LONG',
    length: null,
    precision: null,
    scale: null,
    nullable: false,
    primaryKey: true,
    sortOrder: 0,
    description: '模型订单ID',
    createdAt: '2026-07-17T00:00:00Z',
    updatedAt: '2026-07-17T00:00:00Z',
  }, {
    id: 'bb549113-aa77-4693-a23a-581dc4d3cf90',
    modelId,
    code: 'description',
    name: '说明',
    fieldType: 'STRING',
    length: 128,
    precision: null,
    scale: null,
    nullable: true,
    primaryKey: false,
    sortOrder: 1,
    description: null,
    createdAt: '2026-07-17T00:00:00Z',
    updatedAt: '2026-07-17T00:00:00Z',
  }, {
    id: 'f28c7101-5013-45c5-9861-fbd22d547e58',
    modelId,
    code: 'amount',
    name: '金额',
    fieldType: 'DECIMAL',
    length: null,
    precision: 12,
    scale: 2,
    nullable: false,
    primaryKey: false,
    sortOrder: 2,
    description: null,
    createdAt: '2026-07-17T00:00:00Z',
    updatedAt: '2026-07-17T00:00:00Z',
  }],
};

const modelDefinition: CanvasDefinition = {
  schemaVersion: CANVAS_SCHEMA_VERSION,
  schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
  nodes: [{
    id: 'a73c1b85-afeb-46f4-9ce9-dd9082ed3757',
    type: 'MODEL_INPUT',
    name: '模型输入',
    layout: { x: 10, y: 10, width: 240, height: 120 },
    configuration: { models: [{ modelId }] },
  }, {
    id: '045e5399-b198-4ddd-959d-bf311732322c',
    type: 'MODEL_OUTPUT',
    name: '模型输出',
    layout: { x: 400, y: 10, width: 240, height: 120 },
    configuration: {
      sourceTableName: 'order_model',
      targetModelId: modelId,
      writeMode: 'APPEND',
      columnMappings: [{ sourceColumnName: 'order_id', targetColumnName: 'order_id' }],
      writes: [{
        writeId: '58a53dcf-2a0d-4208-9c55-60f668f720ac',
        sourceTableName: 'order_model',
        targetModelId: modelId,
        writeMode: 'APPEND',
        columnMappings: [{ sourceColumnName: 'order_id', targetColumnName: 'order_id' }],
      }],
    },
  }],
  edges: [{
    id: 'f0fd0549-0828-4a1b-bf7c-001feec21d1b',
    sourceNodeId: 'a73c1b85-afeb-46f4-9ce9-dd9082ed3757',
    targetNodeId: '045e5399-b198-4ddd-959d-bf311732322c',
  }],
};

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
};

describe('useCanvasMetadataSnapshot', () => {
  beforeEach(() => {
    vi.mocked(fetchFileDatasetCanvasMetadata).mockResolvedValue({ tables: [] });
  });

  afterEach(() => vi.clearAllMocks());

  it('sends one complete table schema and preserves source/storage/distribution purposes for Task Engine', async () => {
    vi.mocked(fetchDataSource).mockResolvedValue(dataSource);
    vi.mocked(fetchTableMetadata).mockResolvedValue({
      ...tableMetadata,
      primaryKey: { name: 'orders_pkey', columns: ['order_id'] },
      uniqueKeys: [{ name: 'orders_pkey', type: 'PRIMARY_KEY', columns: ['order_id'] }],
    });

    const { result } = renderHook(() => useCanvasMetadataSnapshot(definition), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(fetchDataSource).toHaveBeenCalledTimes(1);
    expect(fetchTableMetadata).toHaveBeenCalledTimes(1);
    expect(result.current.metadataSnapshot.dataSources).toEqual([{
      id: dataSourceId,
      enabled: true,
      connectionKind: 'JDBC',
      jdbcDatabaseType: 'POSTGRESQL',
      purposes: ['DISTRIBUTION', 'SOURCE', 'STORAGE'],
      tables: [{
        tableName: 'orders',
        objectType: 'VIEW',
        columns: [{
          name: 'order_id',
          fieldType: 'LONG',
          length: null,
          precision: null,
          scale: null,
          nullable: false,
          defaultValue: null,
          autoIncrement: false,
          generated: false,
          comment: '订单ID',
          geometry: null,
        }, {
          name: 'description',
          fieldType: 'STRING',
          length: 128,
          precision: null,
          scale: null,
          nullable: true,
          defaultValue: null,
          autoIncrement: false,
          generated: false,
          comment: '订单说明',
          geometry: null,
        }, {
          name: 'amount',
          fieldType: 'DECIMAL',
          length: null,
          precision: 12,
          scale: 2,
          nullable: false,
          defaultValue: '0',
          autoIncrement: false,
          generated: false,
          comment: '订单金额',
          geometry: null,
        }],
        uniqueKeys: [{
          name: 'orders_pkey',
          type: 'PRIMARY_KEY',
          columns: ['order_id'],
        }],
      }],
      tdEngineTmqTopics: [],
    }]);
    expect(result.current.metadataSnapshot.models).toEqual([]);
    expect(result.current.nodeSummaries.get(definition.nodes[0].id)).toEqual({
      kind: 'JDBC',
      dataSourceName: '订单数据库',
      dataSourceType: 'POSTGRESQL',
      qualifiedTableName: 'orders',
      primaryKeyColumns: ['order_id'],
      tables: [{ tableName: 'orders', primaryKeyColumns: ['order_id'] }],
    });
  });

  it('loads only the JDBC source for a query input and builds its safe summary', async () => {
    const queryDefinition: CanvasDefinition = {
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      nodes: [{
        id: '11111111-1111-4111-8111-111111111111',
        type: 'JDBC_QUERY_INPUT',
        name: '订单查询输入',
        layout: { x: 10, y: 10, width: 240, height: 120 },
        configuration: {
          dataSourceId,
          sql: 'SELECT order_id FROM orders',
          outputTableName: 'query_orders',
          analyzedSqlSha256: 'a'.repeat(64),
          outputColumns: [],
        },
      }],
      edges: [],
    };
    vi.mocked(fetchDataSource).mockResolvedValue(dataSource);

    const { result } = renderHook(() => useCanvasMetadataSnapshot(queryDefinition), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(fetchDataSource).toHaveBeenCalledWith(dataSourceId);
    expect(fetchTableMetadata).not.toHaveBeenCalled();
    expect(result.current.metadataSnapshot.dataSources).toEqual([{
      id: dataSourceId,
      enabled: true,
      connectionKind: 'JDBC',
      jdbcDatabaseType: 'POSTGRESQL',
      purposes: ['DISTRIBUTION', 'SOURCE', 'STORAGE'],
      tables: [],
      tdEngineTmqTopics: [],
    }]);
    expect(result.current.nodeSummaries.get(queryDefinition.nodes[0].id)).toEqual({
      kind: 'JDBC',
      dataSourceName: '订单数据库',
      dataSourceType: 'POSTGRESQL',
      qualifiedTableName: 'query_orders',
    });
  });

  it('preserves Geometry kind, CRS, and dimension in the Task Engine snapshot', async () => {
    const geometry = {
      kind: 'POINT' as const,
      crs: { authority: 'EPSG', code: 4326 },
      dimension: 'XY' as const,
    };
    vi.mocked(fetchDataSource).mockResolvedValue(dataSource);
    vi.mocked(fetchTableMetadata).mockResolvedValue({
      ...tableMetadata,
      columns: [{
        ...tableMetadata.columns[0],
        name: 'location',
        jdbcType: 1111,
        nativeType: 'geometry',
        logicalType: 'GEOMETRY',
        platformTypeDefinition: {
          type: 'GEOMETRY',
          length: null,
          precision: null,
          scale: null,
          geometry,
        },
        length: null,
        precision: null,
        scale: null,
      }],
    });

    const { result } = renderHook(() => useCanvasMetadataSnapshot(definition), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.metadataSnapshot.dataSources[0]?.tables[0]?.columns[0])
      .toEqual(expect.objectContaining({
        name: 'location',
        fieldType: 'GEOMETRY',
        geometry,
      }));
    expect(result.current.error).toBe(false);
  });

  it('reports metadata read failures and never manufactures a partial table schema', async () => {
    vi.mocked(fetchDataSource).mockResolvedValue(dataSource);
    vi.mocked(fetchTableMetadata).mockRejectedValue(new Error('metadata unavailable'));

    const { result } = renderHook(() => useCanvasMetadataSnapshot(definition), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.error).toBe(true));

    expect(result.current.metadataSnapshot.dataSources).toEqual([expect.objectContaining({
      id: dataSourceId,
      tables: [],
    })]);
  });

  it('uses Kafka inline schemas without loading model metadata', async () => {
    const kafkaDataSource: DataSource = {
      ...dataSource,
      code: 'event-bus',
      name: '事件 Kafka',
      purposes: ['DISTRIBUTION', 'SOURCE'],
      type: 'KAFKA',
      connectionKind: 'KAFKA',
      connection: {
        kind: 'KAFKA',
        bootstrapServers: 'kafka.internal:9092',
        securityProtocol: 'SASL_SSL',
        saslMechanism: 'PLAIN',
        username: 'canvas',
        passwordConfigured: true,
      },
    };
    const kafkaDefinition: CanvasDefinition = {
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      nodes: [{
        id: '49cc72e3-0b1e-4031-a71f-b8fa26045339',
        type: 'KAFKA_INPUT',
        name: '事件输入',
        layout: { x: 10, y: 10, width: 240, height: 120 },
        configuration: {
          dataSourceId,
          topic: 'order-events',
          valueSchema: {
            columns: [{
              name: 'event_id',
              fieldType: 'LONG',
              length: null,
              precision: null,
              scale: null,
              nullable: false,
              comment: null,
            }],
          },
          outputTableName: 'order_events',
          startingOffsets: 'LATEST',
          valueFormat: 'JSON',
          metadataFields: [],
        },
      }, {
        id: '42876302-e960-4e39-860a-9fe9bb90600e',
        type: 'KAFKA_OUTPUT',
        name: '事件输出',
        layout: { x: 400, y: 10, width: 240, height: 120 },
        configuration: {
          sourceTableName: 'order_events',
          dataSourceId,
          topic: 'order-events-normalized',
          valueSchema: {
            columns: [{
              name: 'event_id',
              fieldType: 'LONG',
              length: null,
              precision: null,
              scale: null,
              nullable: false,
              comment: null,
            }],
          },
          keyColumnName: 'event_id',
          columnMappings: [{ sourceColumnName: 'event_id', targetColumnName: 'event_id' }],
        },
      }],
      edges: [{
        id: 'ef25de55-b4f4-4146-8a78-d43b45c7a3f7',
        sourceNodeId: '49cc72e3-0b1e-4031-a71f-b8fa26045339',
        targetNodeId: '42876302-e960-4e39-860a-9fe9bb90600e',
      }],
    };
    vi.mocked(fetchDataSource).mockResolvedValue(kafkaDataSource);

    const { result } = renderHook(() => useCanvasMetadataSnapshot(kafkaDefinition), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(fetchDataSource).toHaveBeenCalledTimes(1);
    expect(fetchDataModel).not.toHaveBeenCalled();
    expect(result.current.metadataSnapshot.models).toEqual([]);
    expect(result.current.metadataSnapshot.dataSources).toEqual([{
      id: dataSourceId,
      enabled: true,
      connectionKind: 'KAFKA',
      jdbcDatabaseType: null,
      purposes: ['DISTRIBUTION', 'SOURCE'],
      tables: [],
      tdEngineTmqTopics: [],
    }]);
    expect(result.current.nodeSummaries.get(kafkaDefinition.nodes[0].id)).toEqual({
      kind: 'KAFKA',
      dataSourceName: '事件 Kafka',
      qualifiedTableName: 'order-events',
      fieldCount: 1,
    });
  });

  it('builds file dataset metadata and summary without exposing storage fields', async () => {
    const fileDatasetTableId = '4caa81d1-a92e-44b6-a5aa-5cd31635972c';
    const fileDefinition: CanvasDefinition = {
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      nodes: [{
        id: '6762e8e3-6b76-4c29-8ac1-1db11d1fa57d',
        type: 'FILE_DATASET_INPUT',
        name: '订单文件输入',
        layout: { x: 10, y: 10, width: 240, height: 120 },
        configuration: { fileDatasetId: 'c2b31cf6-ee6b-44cc-81e7-19a85af8ef96', tables: [{ fileDatasetTableId }] },
      }],
      edges: [],
    };
    vi.mocked(fetchFileDatasetCanvasMetadata).mockResolvedValue({
      tables: [{
        fileDatasetTableId,
        fileDatasetId: 'c2b31cf6-ee6b-44cc-81e7-19a85af8ef96',
        fileDatasetName: '订单归档',
        datasetType: 'SHP',
        code: 'orders_202607',
        name: '七月订单',
        parseStatus: 'READY',
        fileStatus: 'READY',
        fields: [
          {
            name: 'order_id',
            sortOrder: 0,
            fieldType: 'LONG',
            length: null,
            precision: null,
            scale: null,
            nullable: false,
            platformTypeDefinition: {
              type: 'LONG', length: null, precision: null, scale: null, geometry: null,
            },
          },
          {
            name: '_geometry',
            sortOrder: 1,
            fieldType: 'GEOMETRY',
            length: null,
            precision: null,
            scale: null,
            nullable: true,
            platformTypeDefinition: {
              type: 'GEOMETRY',
              length: null,
              precision: null,
              scale: null,
              geometry: {
                kind: 'POINT',
                crs: { authority: 'EPSG', code: 4326 },
                dimension: 'XY',
              },
            },
          },
        ],
      }],
    });

    const { result } = renderHook(() => useCanvasMetadataSnapshot(fileDefinition), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(fetchFileDatasetCanvasMetadata).toHaveBeenCalledWith([fileDatasetTableId]);
    expect(result.current.metadataSnapshot.fileDatasetTables).toEqual([{
      id: fileDatasetTableId,
      fileDatasetId: 'c2b31cf6-ee6b-44cc-81e7-19a85af8ef96',
      code: 'orders_202607',
      name: '七月订单',
      datasetType: 'SHP',
      parseStatus: 'READY',
      fileStatus: 'READY',
      columns: [
        {
          name: 'order_id',
          fieldType: 'LONG',
          length: null,
          precision: null,
          scale: null,
          nullable: false,
          defaultValue: null,
          autoIncrement: false,
          generated: false,
          comment: null,
          geometry: null,
        },
        {
          name: '_geometry',
          fieldType: 'GEOMETRY',
          length: null,
          precision: null,
          scale: null,
          nullable: true,
          defaultValue: null,
          autoIncrement: false,
          generated: false,
          comment: null,
          geometry: {
            kind: 'POINT',
            crs: { authority: 'EPSG', code: 4326 },
            dimension: 'XY',
          },
        },
      ],
    }]);
    expect(result.current.nodeSummaries.get(fileDefinition.nodes[0].id)).toMatchObject({
      kind: 'FILE_DATASET',
      fileDatasetName: '订单归档',
      tableName: '七月订单',
      tableCode: 'orders_202607',
      datasetType: 'SHP',
      status: 'READY',
      geometry: {
        fieldName: '_geometry',
        kind: 'POINT',
        crs: { authority: 'EPSG', code: 4326 },
        dimension: 'XY',
      },
    });
    const fileSummary = result.current.nodeSummaries.get(fileDefinition.nodes[0].id);
    expect(fileSummary?.kind === 'FILE_DATASET' ? fileSummary.tables?.[0]?.schema : null)
      .toMatchObject({
        name: 'orders_202607',
        datasetKind: 'BOUNDED',
        columns: [{ name: 'order_id' }, { name: '_geometry' }],
      });
    expect(result.current.error).toBe(false);
  });

  it('builds HTTP API resource schema metadata without sending runtime values', async () => {
    vi.mocked(fetchDataSource).mockResolvedValue(httpApiDataSource);
    vi.mocked(fetchApiResource).mockResolvedValue(apiResource);

    const { result } = renderHook(() => useCanvasMetadataSnapshot(httpApiDefinition), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(fetchDataSource).toHaveBeenCalledWith(dataSourceId);
    expect(fetchApiResource).toHaveBeenCalledWith(dataSourceId, apiResourceId);
    expect(result.current.metadataSnapshot.dataSources).toEqual([{
      id: dataSourceId,
      enabled: true,
      connectionKind: 'HTTP_API',
      jdbcDatabaseType: null,
      purposes: ['SOURCE'],
      tables: [{
        tableName: apiResourceId,
        objectType: 'API_RESOURCE',
        columns: [{
          name: 'order_id',
          fieldType: 'LONG',
          length: null,
          precision: null,
          scale: null,
          nullable: false,
          defaultValue: null,
          autoIncrement: false,
          generated: false,
          comment: '订单 ID',
          geometry: null,
        }],
        uniqueKeys: [],
      }],
      tdEngineTmqTopics: [],
    }]);
    expect(result.current.nodeSummaries.get(httpApiDefinition.nodes[0].id)).toEqual({
      kind: 'HTTP_API',
      dataSourceName: '订单接口',
      qualifiedTableName: '订单列表',
    });
    expect(JSON.stringify(result.current.metadataSnapshot)).not.toContain('2026-07-01');
    expect(result.current.error).toBe(false);
  });

  it('deduplicates model references and builds a logical model snapshot', async () => {
    vi.mocked(fetchDataModel).mockResolvedValue(modelDetail);
    vi.mocked(fetchDataSource).mockResolvedValue(dataSource);

    const { result } = renderHook(() => useCanvasMetadataSnapshot(modelDefinition), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(fetchDataModel).toHaveBeenCalledTimes(1);
    expect(fetchDataSource).toHaveBeenCalledTimes(1);
    expect(fetchTableMetadata).not.toHaveBeenCalled();
    expect(result.current.metadataSnapshot.dataSources).toEqual([expect.objectContaining({
      id: dataSourceId,
      purposes: ['DISTRIBUTION', 'SOURCE', 'STORAGE'],
      tables: [],
    })]);
    expect(result.current.metadataSnapshot.models).toEqual([{
      id: modelId,
      code: 'order_model',
      name: '订单模型',
      schemaVersion: 7,
      status: 'PUBLISHED',
      physicalTableMode: 'MANAGED',
      dataSourceId,
      catalogName: 'warehouse',
      schemaName: 'model_schema',
      physicalTableName: 'dwd_order_model',
      columns: [{
        name: 'order_id',
        fieldType: 'LONG',
        length: null,
        precision: null,
        scale: null,
        nullable: false,
        defaultValue: null,
        autoIncrement: false,
        generated: false,
        comment: '模型订单ID',
        geometry: null,
      }, {
        name: 'description',
        fieldType: 'STRING',
        length: 128,
        precision: null,
        scale: null,
        nullable: true,
        defaultValue: null,
        autoIncrement: false,
        generated: false,
        comment: null,
        geometry: null,
      }, {
        name: 'amount',
        fieldType: 'DECIMAL',
        length: null,
        precision: 12,
        scale: 2,
        nullable: false,
        defaultValue: null,
        autoIncrement: false,
        generated: false,
        comment: null,
        geometry: null,
      }],
      uniqueKeys: [{
        name: 'MODEL_PRIMARY_KEY',
        type: 'PRIMARY_KEY',
        columns: ['order_id'],
      }],
    }]);
    modelDefinition.nodes.forEach((node) => {
      expect(result.current.nodeSummaries.get(node.id)).toEqual({
        kind: 'MODEL',
        modelName: '订单模型',
        modelCode: 'order_model',
        modelSchemaVersion: 7,
        dataSourceName: '订单数据库',
        qualifiedTableName: 'dwd_order_model',
      });
    });
    expect(result.current.error).toBe(false);
  });

});
