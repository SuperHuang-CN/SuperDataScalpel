import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  CANVAS_DEFINITION_FILE_NAME,
  downloadCanvasDefinition,
  formatCanvasDefinition,
  parseCanvasDefinitionJson,
} from './canvasDefinitionIO';
import {
  exampleCanvasDefinition,
  exampleStreamingCanvasTopologyDefinition,
} from './defaultCanvas';
import {
  CANVAS_SCHEMA_MINOR_VERSION,
  CANVAS_SCHEMA_VERSION,
  CanvasNodeType,
} from './canvasTypes';

describe('canvas definition import and export', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('round-trips a valid stable definition', () => {
    const definition = exampleCanvasDefinition();
    const parsed = parseCanvasDefinitionJson(formatCanvasDefinition(definition));

    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition).toEqual(definition);
  });

  it('round-trips SQL Transform drafts and rejects them in Canvas 4.0', () => {
    const definition = {
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      nodes: [{
        id: '11111111-1111-4111-8111-111111111111',
        type: CanvasNodeType.SqlTransform,
        name: 'SQL 处理',
        layout: { x: 80, y: 80, width: 320, height: 120 },
        configuration: { outputTableName: '', sql: '' },
      }],
      edges: [],
    };

    expect(parseCanvasDefinitionJson(JSON.stringify(definition))).toEqual({
      success: true,
      definition,
    });
    expect(parseCanvasDefinitionJson(JSON.stringify({
      ...definition,
      schemaMinorVersion: 0,
    }))).toEqual(expect.objectContaining({
      success: false,
      errors: expect.arrayContaining(['SQL_TRANSFORM 从 Canvas 4.1 开始支持']),
    }));
  });

  it('normalizes missing Stream Join output columns and rejects malformed values', () => {
    const source = JSON.parse(formatCanvasDefinition(exampleStreamingCanvasTopologyDefinition())) as {
      nodes: Array<{ type: string; configuration: Record<string, unknown> }>;
    };
    const streamJoinIndex = source.nodes.findIndex((node) => node.type === CanvasNodeType.StreamJoin);
    const streamJoin = source.nodes[streamJoinIndex];
    expect(streamJoin).toBeDefined();
    if (!streamJoin) return;

    delete streamJoin.configuration.outputColumns;
    const normalized = parseCanvasDefinitionJson(JSON.stringify(source));
    expect(normalized.success).toBe(true);
    if (normalized.success) {
      const parsedJoin = normalized.definition.nodes.find(
        (node) => node.type === CanvasNodeType.StreamJoin,
      );
      expect(parsedJoin?.configuration.outputColumns).toEqual([]);
    }

    streamJoin.configuration.outputColumns = 'invalid';
    const malformed = parseCanvasDefinitionJson(JSON.stringify(source));
    expect(malformed.success).toBe(false);
    if (!malformed.success) {
      expect(malformed.errors).toContain(
        `nodes[${streamJoinIndex}].configuration.outputColumns 必须是数组`,
      );
    }
  });

  it('round-trips runtime value derivations and rejects unknown values', () => {
    const definition = {
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      nodes: [{
        id: '11111111-1111-4111-8111-111111111111',
        type: CanvasNodeType.DeriveColumns,
        name: '派生技术字段',
        layout: { x: 80, y: 80, width: 352, height: 224 },
        configuration: {
          globalDerivations: [{
            targetColumnName: 'etl_batch_id',
            expression: { kind: 'RUNTIME_VALUE', value: 'EXECUTION_ID' },
          }],
          operations: [{
            operationId: '22222222-2222-4222-8222-222222222222',
            sourceTableName: 'orders',
            output: { mode: 'REPLACE_SOURCE', outputTableName: null },
            derivations: [{
              targetColumnName: 'etl_loaded_at',
              expression: { kind: 'RUNTIME_VALUE', value: 'EXECUTION_STARTED_AT' },
            }],
          }],
        },
      }],
      edges: [],
    };

    expect(parseCanvasDefinitionJson(JSON.stringify(definition))).toEqual({
      success: true,
      definition,
    });
    const invalid = parseCanvasDefinitionJson(JSON.stringify({
      ...definition,
      nodes: [{
        ...definition.nodes[0],
        configuration: {
          ...definition.nodes[0].configuration,
          globalDerivations: [{
            ...definition.nodes[0].configuration.globalDerivations[0],
            expression: { kind: 'RUNTIME_VALUE', value: 'CURRENT_TIMESTAMP' },
          }],
        },
      }],
    }));
    expect(invalid.success).toBe(false);
    if (!invalid.success) expect(invalid.errors).toContain(
      'nodes[0].configuration.globalDerivations[0].expression.value 不是受支持的运行时变量',
    );

    const legacyWriteMode = parseCanvasDefinitionJson(JSON.stringify({
      ...definition,
      nodes: [{
        ...definition.nodes[0],
        configuration: {
          ...definition.nodes[0].configuration,
          globalDerivations: [{
            ...definition.nodes[0].configuration.globalDerivations[0],
            replaceExisting: false,
          }],
        },
      }],
    }));
    expect(legacyWriteMode.success).toBe(false);
    if (!legacyWriteMode.success) expect(legacyWriteMode.errors).toContain(
      'nodes[0].configuration.globalDerivations[0].replaceExisting 已不再支持；派生字段会按目标字段名自动新增或覆盖',
    );
  });

  it('round-trips ordered JDBC input table selections', () => {
    const definition = {
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      nodes: [{
        id: '11111111-1111-4111-8111-111111111111',
        type: CanvasNodeType.JdbcInput,
        name: '业务库输入',
        layout: { x: 80, y: 80, width: 344, height: 250 },
        configuration: {
          dataSourceId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
          tables: [
            { tableName: 'orders', readOptions: [] },
            { tableName: 'customers', readOptions: [] },
          ],
        },
      }],
      edges: [],
    };

    const parsed = parseCanvasDefinitionJson(JSON.stringify(definition));

    expect(parsed).toEqual({ success: true, definition });
    if (parsed.success) {
      expect(parsed.definition.nodes[0]?.configuration).not.toHaveProperty('tableName');
    }
  });

  it('normalizes 3.0 JDBC input selections and gates advanced read options to 3.1', () => {
    const legacy = {
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: 0,
      nodes: [{
        id: '11111111-1111-4111-8111-111111111111',
        type: CanvasNodeType.JdbcInput,
        name: '业务库输入',
        layout: { x: 80, y: 80, width: 344, height: 250 },
        configuration: {
          dataSourceId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
          tables: [{ tableName: 'orders' }],
        },
      }],
      edges: [],
    };

    const normalized = parseCanvasDefinitionJson(JSON.stringify(legacy));
    expect(normalized.success).toBe(true);
    if (normalized.success) {
      expect(normalized.definition.schemaMinorVersion).toBe(CANVAS_SCHEMA_MINOR_VERSION);
      const jdbcInput = normalized.definition.nodes[0];
      expect(jdbcInput?.type).toBe(CanvasNodeType.JdbcInput);
      if (jdbcInput?.type === CanvasNodeType.JdbcInput) {
        expect(jdbcInput.configuration.tables[0]?.readOptions).toEqual([]);
      }
    }

    const incompatible = parseCanvasDefinitionJson(JSON.stringify({
      ...legacy,
      nodes: [{
        ...legacy.nodes[0],
        configuration: {
          ...legacy.nodes[0].configuration,
          tables: [{
            tableName: 'orders',
            readOptions: [{ name: 'fetchsize', value: '1000' }],
          }],
        },
      }],
    }));
    expect(incompatible).toEqual({
      success: false,
      errors: [`JDBC_INPUT.readOptions 从 Canvas ${CANVAS_SCHEMA_VERSION}.1 开始支持`],
    });
  });

  it('rejects previous majors and future minors before parsing node configurations', () => {
    [1, 2].forEach((schemaVersion) => {
      const previousMajor = parseCanvasDefinitionJson(JSON.stringify({
        schemaVersion,
        schemaMinorVersion: 28,
        nodes: [{ type: 'LEGACY_NODE_WITH_UNKNOWN_CONFIGURATION' }],
        edges: [],
      }));
      expect(previousMajor.success).toBe(false);
    });

    const futureMinor = parseCanvasDefinitionJson(JSON.stringify({
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION + 1,
      nodes: [],
      edges: [],
    }));
    expect(futureMinor.success).toBe(false);
  });

  it('round-trips MODEL_OUTPUT UPSERT in Canvas 3.0', () => {
    const modelOutput = {
      id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
      type: CanvasNodeType.ModelOutput,
      name: '模型 UPSERT',
      layout: { x: 0, y: 0, width: 240, height: 120 },
      configuration: {
        sourceTableName: 'orders',
        targetModelId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        writeMode: 'UPSERT',
        columnMappings: [{ sourceColumnName: 'id', targetColumnName: 'id' }],
      },
    };
    const current = parseCanvasDefinitionJson(JSON.stringify({
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      nodes: [modelOutput],
      edges: [],
    }));

    expect(current.success).toBe(true);
  });

  it('round-trips spatial foundation node configurations in Canvas 3.0', () => {
    const definition = {
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      nodes: [
        {
          id: '11111111-1111-4111-8111-111111111111',
          type: CanvasNodeType.GeometryConstruct,
          name: 'Geometry 构造',
          layout: { x: 0, y: 0, width: 250, height: 120 },
          configuration: {
            sourceTableName: 'raw',
            outputTableName: 'geometry_table',
            outputColumnName: 'shape',
            source: { kind: 'WKT', columnName: 'wkt' },
            targetGeometry: {
              kind: 'POLYGON',
              crs: { authority: 'EPSG', code: 4326 },
              dimension: 'XY',
            },
          },
        },
        {
          id: '22222222-2222-4222-8222-222222222222',
          type: CanvasNodeType.GeometryValidate,
          name: 'Geometry 校验',
          layout: { x: 300, y: 0, width: 250, height: 120 },
          configuration: {
            sourceTableName: 'geometry_table',
            outputTableName: 'validated',
            geometryColumnName: 'shape',
            validColumnName: 'is_valid',
            reasonColumnName: null,
          },
        },
        {
          id: '33333333-3333-4333-8333-333333333333',
          type: CanvasNodeType.SpatialMeasure,
          name: '空间测量',
          layout: { x: 600, y: 0, width: 250, height: 120 },
          configuration: {
            sourceTableName: 'validated',
            outputTableName: 'measured',
            measurements: [
              {
                kind: 'AREA',
                geometryColumnName: 'shape',
                mode: 'PLANAR',
                outputColumnName: 'area',
              },
              {
                kind: 'DISTANCE',
                leftGeometryColumnName: 'shape',
                rightGeometryColumnName: 'shape',
                mode: 'SPHEROID',
                outputColumnName: 'distance',
              },
            ],
          },
        },
        {
          id: '44444444-4444-4444-8444-444444444444',
          type: CanvasNodeType.GeometrySerialize,
          name: 'Geometry 序列化',
          layout: { x: 900, y: 0, width: 250, height: 120 },
          configuration: {
            sourceTableName: 'measured',
            outputTableName: 'serialized',
            geometryColumnName: 'shape',
            outputColumnName: 'geojson',
            format: 'GEOJSON',
          },
        },
      ],
      edges: [],
    };

    const parsed = parseCanvasDefinitionJson(JSON.stringify(definition));

    expect(parsed.success).toBe(true);
    if (!parsed.success) return;
    expect(parsed.definition.nodes.map((node) => node.type)).toEqual([
      CanvasNodeType.GeometryConstruct,
      CanvasNodeType.GeometryValidate,
      CanvasNodeType.SpatialMeasure,
      CanvasNodeType.GeometrySerialize,
    ]);
    expect(JSON.parse(formatCanvasDefinition(parsed.definition))).toEqual(definition);
  });

  it('round-trips spatial enrichment node configurations in Canvas 3.0', () => {
    const definition = {
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      nodes: [
        {
          id: '11111111-1111-4111-8111-111111111111',
          type: CanvasNodeType.GeometryRepair,
          name: 'Geometry 修复',
          layout: { x: 0, y: 0, width: 250, height: 120 },
          configuration: {
            sourceTableName: 'geometry_table',
            outputTableName: 'repaired',
            geometryColumnName: 'shape',
            outputColumnName: 'repaired_shape',
          },
        },
        {
          id: '22222222-2222-4222-8222-222222222222',
          type: CanvasNodeType.GeometryBuffer,
          name: 'Geometry Buffer',
          layout: { x: 300, y: 0, width: 250, height: 120 },
          configuration: {
            sourceTableName: 'repaired',
            outputTableName: 'buffered',
            geometryColumnName: 'repaired_shape',
            outputColumnName: 'buffer_shape',
            distance: 1000,
            mode: 'SPHEROID',
          },
        },
        {
          id: '33333333-3333-4333-8333-333333333333',
          type: CanvasNodeType.GeometryExplode,
          name: 'Geometry 拆分',
          layout: { x: 600, y: 0, width: 250, height: 120 },
          configuration: {
            sourceTableName: 'buffered',
            outputTableName: 'parts',
            geometryColumnName: 'buffer_shape',
            outputColumnName: 'part',
            partIndexColumnName: 'part_index',
          },
        },
      ],
      edges: [],
    };

    const parsed = parseCanvasDefinitionJson(JSON.stringify(definition));

    expect(parsed.success).toBe(true);
    if (!parsed.success) return;
    expect(parsed.definition.nodes.map((node) => node.type)).toEqual([
      CanvasNodeType.GeometryRepair,
      CanvasNodeType.GeometryBuffer,
      CanvasNodeType.GeometryExplode,
    ]);
    expect(JSON.parse(formatCanvasDefinition(parsed.definition))).toEqual(definition);
  });

  it('round-trips spatial clip and aggregate configurations in Canvas 3.0', () => {
    const definition = {
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      nodes: [
        {
          id: '11111111-1111-4111-8111-111111111111',
          type: CanvasNodeType.SpatialClip,
          name: '空间裁剪',
          layout: { x: 0, y: 0, width: 260, height: 120 },
          configuration: {
            sourceTableName: 'roads',
            maskTableName: 'districts',
            outputTableName: 'district_roads',
            sourceGeometryColumnName: 'centerline',
            maskGeometryColumnName: 'boundary',
            outputColumnName: 'clipped_centerline',
          },
        },
        {
          id: '22222222-2222-4222-8222-222222222222',
          type: CanvasNodeType.SpatialAggregate,
          name: '空间聚合',
          layout: { x: 320, y: 0, width: 250, height: 120 },
          configuration: {
            sourceTableName: 'parcels',
            outputTableName: 'district_geometry',
            groupByColumns: ['district_code'],
            aggregations: [
              {
                kind: 'UNION',
                geometryColumnName: 'boundary',
                outputColumnName: 'district_boundary',
              },
              {
                kind: 'ENVELOPE',
                geometryColumnName: 'boundary',
                outputColumnName: 'district_envelope',
              },
            ],
          },
        },
      ],
      edges: [],
    };

    const parsed = parseCanvasDefinitionJson(JSON.stringify(definition));

    expect(parsed.success).toBe(true);
    if (!parsed.success) return;
    expect(parsed.definition.nodes.map((node) => node.type)).toEqual([
      CanvasNodeType.SpatialClip,
      CanvasNodeType.SpatialAggregate,
    ]);
    expect(JSON.parse(formatCanvasDefinition(parsed.definition))).toEqual(definition);

  });

  it('round-trips JDBC query input and JDBC output UPSERT in Canvas 3.0', () => {
    const definition = {
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      nodes: [
        {
          id: '11111111-1111-4111-8111-111111111111',
          type: CanvasNodeType.JdbcQueryInput,
          name: '订单查询输入',
          layout: { x: 0, y: 0, width: 240, height: 120 },
          configuration: {
            dataSourceId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
            sql: 'SELECT order_id, amount FROM orders',
            outputTableName: 'query_orders',
            analyzedSqlSha256: 'a'.repeat(64),
            outputColumns: [{
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
          },
        },
        {
          id: '22222222-2222-4222-8222-222222222222',
          type: CanvasNodeType.JdbcOutput,
          name: '订单 UPSERT',
          layout: { x: 320, y: 0, width: 240, height: 120 },
          configuration: {
            sourceTableName: 'query_orders',
            dataSourceId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
            targetTableName: 'orders',
            writeMode: 'UPSERT',
            columnMappings: [{ sourceColumnName: 'order_id', targetColumnName: 'order_id' }],
            upsertKeyColumns: ['order_id'],
          },
        },
      ],
      edges: [{
        id: '33333333-3333-4333-8333-333333333333',
        sourceNodeId: '11111111-1111-4111-8111-111111111111',
        targetNodeId: '22222222-2222-4222-8222-222222222222',
      }],
    };

    const parsed = parseCanvasDefinitionJson(JSON.stringify(definition));

    expect(parsed.success).toBe(true);
    if (!parsed.success) return;
    expect(JSON.parse(formatCanvasDefinition(parsed.definition))).toEqual({
      ...definition,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
    });

    const incompatible = parseCanvasDefinitionJson(JSON.stringify({
      ...definition,
      schemaVersion: 1,
    }));
    expect(incompatible.success).toBe(false);
    if (!incompatible.success) {
      expect(incompatible.errors).toContain(`schemaVersion 仅支持 ${CANVAS_SCHEMA_VERSION}`);
    }
  });

  it('normalizes a legacy JDBC output without UPSERT keys to an empty array', () => {
    const legacy = JSON.parse(formatCanvasDefinition(exampleCanvasDefinition())) as {
      nodes: Array<{ type: string; configuration: Record<string, unknown> }>;
    };
    const output = legacy.nodes.find((node) => node.type === CanvasNodeType.JdbcOutput);
    if (!output) throw new Error('example JDBC output is missing');
    delete output.configuration.upsertKeyColumns;

    const parsed = parseCanvasDefinitionJson(JSON.stringify(legacy));

    expect(parsed.success).toBe(true);
    if (!parsed.success) return;
    const parsedOutput = parsed.definition.nodes.find(
      (node) => node.type === CanvasNodeType.JdbcOutput,
    );
    expect(parsedOutput?.configuration.upsertKeyColumns).toEqual([]);
  });

  it('rejects unsupported spatial aggregation kinds', () => {
    const definition = {
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      nodes: [{
        id: '11111111-1111-4111-8111-111111111111',
        type: CanvasNodeType.SpatialAggregate,
        name: '空间聚合',
        layout: { x: 0, y: 0, width: 250, height: 120 },
        configuration: {
          sourceTableName: 'parcels',
          outputTableName: 'district_geometry',
          groupByColumns: [],
          aggregations: [{
            kind: 'CONVEX_HULL',
            geometryColumnName: 'boundary',
            outputColumnName: 'hull',
          }],
        },
      }],
      edges: [],
    };

    expect(parseCanvasDefinitionJson(JSON.stringify(definition)).success).toBe(false);
  });

  it('rejects unknown spatial discriminators and serialization formats', () => {
    const definition = {
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      nodes: [{
        id: '11111111-1111-4111-8111-111111111111',
        type: CanvasNodeType.GeometrySerialize,
        name: 'Geometry 序列化',
        layout: { x: 0, y: 0, width: 250, height: 120 },
        configuration: {
          sourceTableName: 'source',
          outputTableName: 'output',
          geometryColumnName: 'shape',
          outputColumnName: 'shape_text',
          format: 'EWKT',
        },
      }],
      edges: [],
    };

    expect(parseCanvasDefinitionJson(JSON.stringify(definition)).success).toBe(false);
  });

  it('reads current definitions without an explicit minor and normalizes them to 3.0', () => {
    const legacy = JSON.parse(formatCanvasDefinition(exampleCanvasDefinition())) as Record<string, unknown>;
    delete legacy.schemaMinorVersion;

    const parsed = parseCanvasDefinitionJson(JSON.stringify(legacy));

    expect(parsed.success).toBe(true);
    if (!parsed.success) return;
    expect(parsed.definition.schemaVersion).toBe(CANVAS_SCHEMA_VERSION);
    expect(parsed.definition.schemaMinorVersion).toBe(CANVAS_SCHEMA_MINOR_VERSION);
    expect(formatCanvasDefinition(parsed.definition))
      .toContain(`"schemaMinorVersion": ${CANVAS_SCHEMA_MINOR_VERSION}`);
  });

  it('does not infer JDBC input selections from legacy nested identifiers', () => {
    const legacy = JSON.parse(formatCanvasDefinition(exampleCanvasDefinition())) as {
      nodes: Array<{ type: string; configuration: Record<string, unknown> }>;
    };
    const input = legacy.nodes.find((node) => node.type === CanvasNodeType.JdbcInput);
    const output = legacy.nodes.find((node) => node.type === CanvasNodeType.JdbcOutput);
    if (!input || !output) return;
    delete input.configuration.tables;
    input.configuration.table = { catalogName: 'demo', schemaName: 'public', tableName: 'orders' };
    delete output.configuration.targetTableName;
    output.configuration.targetTable = { catalogName: 'demo', schemaName: 'dw', tableName: 'dwd_order_customer' };

    const parsed = parseCanvasDefinitionJson(JSON.stringify(legacy));

    expect(parsed.success).toBe(true);
    if (!parsed.success) return;
    const parsedInput = parsed.definition.nodes.find((node) => node.type === CanvasNodeType.JdbcInput);
    const parsedOutput = parsed.definition.nodes.find((node) => node.type === CanvasNodeType.JdbcOutput);
    expect(parsedInput?.configuration.tables).toEqual([]);
    expect(parsedOutput?.configuration.targetTableName).toBe('dwd_order_customer');
    expect(formatCanvasDefinition(parsed.definition)).not.toContain('catalogName');
    expect(formatCanvasDefinition(parsed.definition)).not.toContain('schemaName');
  });

  it('accepts business-incomplete node configuration as an importable draft', () => {
    const definition = exampleCanvasDefinition();
    const join = definition.nodes.find((node) => node.type === CanvasNodeType.Join);
    if (join?.type === CanvasNodeType.Join) {
      join.configuration.outputTableName = '';
      join.configuration.conditions = [];
    }

    expect(parseCanvasDefinitionJson(formatCanvasDefinition(definition)).success).toBe(true);
  });

  it('round-trips model nodes and validates nonblank model identifiers as UUIDs', () => {
    const definition = {
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      nodes: [
        {
          id: '1c436443-4cc0-4fe8-b748-4c02143eb602',
          type: CanvasNodeType.ModelInput,
          name: '订单模型输入',
          layout: { x: 10, y: 20, width: 240, height: 120 },
          configuration: { modelId: '45a1f1bd-c381-45eb-a39b-924fc65122ac' },
        },
        {
          id: '693f6c6b-8978-470f-bab7-cf411eb6e874',
          type: CanvasNodeType.ModelOutput,
          name: '订单模型输出',
          layout: { x: 320, y: 20, width: 240, height: 120 },
          configuration: {
            sourceTableName: 'orders',
            targetModelId: 'e8b93333-d6ee-4c8b-b5af-df5c90c9620d',
            writeMode: 'APPEND' as const,
            columnMappings: [{ sourceColumnName: 'id', targetColumnName: 'id' }],
          },
        },
      ],
      edges: [{
        id: 'e97b9448-401c-40c7-86cf-e683e39fb56b',
        sourceNodeId: '1c436443-4cc0-4fe8-b748-4c02143eb602',
        targetNodeId: '693f6c6b-8978-470f-bab7-cf411eb6e874',
      }],
    };

    const parsed = parseCanvasDefinitionJson(JSON.stringify(definition));
    expect(parsed).toEqual({
      success: true,
      definition,
    });

    definition.nodes[0].configuration.modelId = 'not-a-uuid';
    expect(parseCanvasDefinitionJson(JSON.stringify(definition)).success).toBe(false);
    definition.nodes[0].configuration.modelId = '';
    expect(parseCanvasDefinitionJson(JSON.stringify(definition)).success).toBe(true);
  });

  it('round-trips an HTTP API input with typed runtime parameters', () => {
    const definition = {
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      nodes: [{
        id: '1c436443-4cc0-4fe8-b748-4c02143eb602',
        type: CanvasNodeType.HttpApiInput,
        name: '订单 API 输入',
        layout: { x: 10, y: 20, width: 240, height: 120 },
        configuration: {
          dataSourceId: '45a1f1bd-c381-45eb-a39b-924fc65122ac',
          resourceId: 'e8b93333-d6ee-4c8b-b5af-df5c90c9620d',
          outputTableName: 'api_orders',
          runtimeParameters: [{ name: 'startTime', value: '2026-07-22T00:00:00Z' }],
        },
      }],
      edges: [],
    };

    expect(parseCanvasDefinitionJson(JSON.stringify(definition))).toEqual({
      success: true,
      definition,
    });

    definition.nodes[0].configuration.runtimeParameters[0].name = 'invalid name';
    expect(parseCanvasDefinitionJson(JSON.stringify(definition)).success).toBe(false);

    definition.nodes[0].configuration.runtimeParameters[0].name = 'access_token';
    const sensitive = parseCanvasDefinitionJson(JSON.stringify(definition));
    expect(sensitive.success).toBe(false);
    if (!sensitive.success) {
      expect(sensitive.errors).toContain(
        'nodes[0].configuration.runtimeParameters[0].name 不允许用于密码、Token、密钥或签名',
      );
    }
  });

  it('rejects malformed JSON, unknown node types and missing edge endpoints', () => {
    expect(parseCanvasDefinitionJson('{').success).toBe(false);

    const unknownNode = JSON.parse(formatCanvasDefinition(exampleCanvasDefinition())) as Record<string, unknown>;
    const unknownNodes = unknownNode.nodes as Array<Record<string, unknown>>;
    unknownNodes[0].type = 'UNKNOWN';
    expect(parseCanvasDefinitionJson(JSON.stringify(unknownNode)).success).toBe(false);

    const missingEndpoint = exampleCanvasDefinition();
    missingEndpoint.edges[0].targetNodeId = '3ceff0fb-76e6-46e9-997a-21ac13094052';
    expect(parseCanvasDefinitionJson(formatCanvasDefinition(missingEndpoint)).success).toBe(false);
  });

  it('rejects unsupported versions, duplicate IDs and unsafe layouts', () => {
    const unsupportedVersion = JSON.parse(formatCanvasDefinition(exampleCanvasDefinition())) as {
      schemaVersion: number;
      schemaMinorVersion: number;
    };
    unsupportedVersion.schemaVersion = CANVAS_SCHEMA_VERSION - 1;
    expect(parseCanvasDefinitionJson(JSON.stringify(unsupportedVersion)).success).toBe(false);

    unsupportedVersion.schemaVersion = CANVAS_SCHEMA_VERSION;
    unsupportedVersion.schemaMinorVersion = CANVAS_SCHEMA_MINOR_VERSION + 1;
    expect(parseCanvasDefinitionJson(JSON.stringify(unsupportedVersion)).success).toBe(false);

    const duplicateNode = exampleCanvasDefinition();
    duplicateNode.nodes[1].id = duplicateNode.nodes[0].id;
    expect(parseCanvasDefinitionJson(formatCanvasDefinition(duplicateNode)).success).toBe(false);

    const duplicateEdge = exampleCanvasDefinition();
    duplicateEdge.edges[1].id = duplicateEdge.edges[0].id;
    expect(parseCanvasDefinitionJson(formatCanvasDefinition(duplicateEdge)).success).toBe(false);

    const unsafeLayout = JSON.parse(formatCanvasDefinition(exampleCanvasDefinition())) as {
      nodes: Array<{ layout: { width: number } }>;
    };
    unsafeLayout.nodes[0].layout.width = 20;
    expect(parseCanvasDefinitionJson(JSON.stringify(unsafeLayout)).success).toBe(false);
  });

  it('round-trips Rename in Canvas 3.0', () => {
    const definition = {
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      nodes: [{
        id: '1c436443-4cc0-4fe8-b748-4c02143eb602',
        type: CanvasNodeType.Rename,
        name: '订单重命名',
        layout: { x: 10, y: 20, width: 240, height: 120 },
        configuration: {
          sourceTableName: 'orders',
          outputTableName: 'source_orders',
          columnMappings: [{ sourceColumnName: 'id', targetColumnName: 'order_id' }],
        },
      }],
      edges: [],
    };

    const parsed = parseCanvasDefinitionJson(JSON.stringify(definition));
    expect(parsed.success).toBe(true);
    if (parsed.success) {
      expect(parsed.definition).toEqual(definition);
    }
  });

  it('round-trips streaming nodes in the current Canvas version', () => {
    const definition = exampleStreamingCanvasTopologyDefinition();
    const parsed = parseCanvasDefinitionJson(formatCanvasDefinition(definition));

    expect(parsed).toEqual({ success: true, definition });
  });

  it('round-trips file dataset input in Canvas 3.0', () => {
    const definition = {
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      nodes: [{
        id: '1c436443-4cc0-4fe8-b748-4c02143eb602',
        type: CanvasNodeType.FileDatasetInput,
        name: '订单文件输入',
        layout: { x: 10, y: 20, width: 240, height: 120 },
        configuration: {
          fileDatasetTableId: '45a1f1bd-c381-45eb-a39b-924fc65122ac',
        },
      }],
      edges: [],
    };

    expect(parseCanvasDefinitionJson(JSON.stringify(definition))).toEqual({
      success: true,
      definition,
    });
  });

  it('normalizes and round-trips a strict file output definition', () => {
    const definition = {
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      nodes: [{
        id: '1c436443-4cc0-4fe8-b748-4c02143eb602',
        type: CanvasNodeType.FileOutput,
        name: '订单文件输出',
        layout: { x: 10, y: 20, width: 240, height: 120 },
        configuration: {
          sourceTableName: 'orders',
          dataSourceId: '45a1f1bd-c381-45eb-a39b-924fc65122ac',
          targetPath: 'exports//orders/',
          conflictPolicy: 'FAIL_IF_EXISTS',
          formatOptions: {
            type: 'CSV',
            header: true,
            delimiter: ',',
            quote: '"',
            escape: '\\',
            nullValue: '',
          },
        },
      }],
      edges: [],
    };

    const parsed = parseCanvasDefinitionJson(JSON.stringify(definition));

    expect(parsed.success).toBe(true);
    if (!parsed.success) return;
    expect(parsed.definition.nodes[0]?.configuration).toMatchObject({
      sourceTableName: 'orders',
      targetPath: 'exports/orders',
      conflictPolicy: 'FAIL_IF_EXISTS',
      formatOptions: { type: 'CSV' },
    });
    const exported = formatCanvasDefinition(parsed.definition);
    expect(exported).not.toContain('accessKey');
    expect(exported).not.toContain('secretKey');
  });

  it('round-trips Shapefile output in Canvas 3.0', () => {
    const definition = {
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      nodes: [{
        id: '1c436443-4cc0-4fe8-b748-4c02143eb602',
        type: CanvasNodeType.FileOutput,
        name: '行政区 Shapefile 输出',
        layout: { x: 10, y: 20, width: 240, height: 120 },
        configuration: {
          sourceTableName: 'districts',
          dataSourceId: '45a1f1bd-c381-45eb-a39b-924fc65122ac',
          targetPath: 'exports//districts/',
          conflictPolicy: 'FAIL_IF_EXISTS',
          formatOptions: {
            type: 'SHAPEFILE',
            baseName: 'districts',
            packageMode: 'ZIP',
            geometryColumnName: 'geom',
            targetShapeType: 'POLYGON',
            attributeMappings: [{
              sourceColumnName: 'district_name',
              targetFieldName: 'DIST_NAME',
              targetStringByteLength: 160,
            }],
          },
        },
      }],
      edges: [],
    };

    const parsed = parseCanvasDefinitionJson(JSON.stringify(definition));
    expect(parsed.success).toBe(true);
    if (parsed.success) {
      expect(parsed.definition).toMatchObject({
        schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
        nodes: [{
          configuration: {
            targetPath: 'exports/districts',
            formatOptions: {
              type: 'SHAPEFILE',
              packageMode: 'ZIP',
              targetShapeType: 'POLYGON',
              attributeMappings: [{ targetFieldName: 'DIST_NAME' }],
            },
          },
        }],
      });
    }

    const invalidDraft = parseCanvasDefinitionJson(JSON.stringify({
      ...definition,
      nodes: [{
        ...definition.nodes[0],
        configuration: {
          ...definition.nodes[0].configuration,
          formatOptions: {
            ...definition.nodes[0].configuration.formatOptions,
            baseName: '',
            attributeMappings: [],
          },
        },
      }],
    }));
    expect(invalidDraft.success).toBe(true);
  });

  it('round-trips GeoParquet and GeoJSON output in Canvas 3.0', () => {
    const nodes = [{
      id: 'd4fd15b7-f1ac-44d0-bfc8-a624d3192637',
      type: CanvasNodeType.FileOutput,
      name: 'GeoParquet 输出',
      layout: { x: 10, y: 20, width: 240, height: 120 },
      configuration: {
        sourceTableName: 'districts',
        dataSourceId: '45a1f1bd-c381-45eb-a39b-924fc65122ac',
        targetPath: 'exports/geoparquet',
        conflictPolicy: 'FAIL_IF_EXISTS',
        formatOptions: {
          type: 'GEOPARQUET',
          geometryColumnName: 'geom',
          compression: 'ZSTD',
          coveringMode: 'ROW_BBOX',
        },
      },
    }, {
      id: '4584983c-3742-4574-8ee8-3b81f0b52cc1',
      type: CanvasNodeType.FileOutput,
      name: 'GeoJSON 输出',
      layout: { x: 300, y: 20, width: 240, height: 120 },
      configuration: {
        sourceTableName: 'districts',
        dataSourceId: '45a1f1bd-c381-45eb-a39b-924fc65122ac',
        targetPath: 'exports/geojson',
        conflictPolicy: 'OVERWRITE',
        formatOptions: {
          type: 'GEOJSON',
          baseName: 'districts',
          geometryColumnName: 'geom',
          idColumnName: 'district_id',
          ignoreNullProperties: true,
        },
      },
    }];
    const definition = {
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      nodes,
      edges: [],
    };

    const parsed = parseCanvasDefinitionJson(JSON.stringify(definition));
    expect(parsed.success).toBe(true);
    if (parsed.success) {
      expect(parsed.definition.nodes[0]?.configuration).toMatchObject({
        formatOptions: {
          type: 'GEOPARQUET',
          compression: 'ZSTD',
          coveringMode: 'ROW_BBOX',
        },
      });
      expect(parsed.definition.nodes[1]?.configuration).toMatchObject({
        formatOptions: {
          type: 'GEOJSON',
          idColumnName: 'district_id',
          ignoreNullProperties: true,
        },
      });
      expect(JSON.parse(formatCanvasDefinition(parsed.definition))).toMatchObject({
        schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      });
    }

  });

  it('round-trips Filter in Canvas 3.0', () => {
    const definition = {
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      nodes: [{
        id: '1c436443-4cc0-4fe8-b748-4c02143eb602',
        type: CanvasNodeType.Filter,
        name: '订单筛选',
        layout: { x: 10, y: 20, width: 240, height: 120 },
        configuration: {
          sourceTableName: 'orders',
          outputTableName: 'paid_orders',
          condition: {
            kind: 'GROUP',
            operator: 'AND',
            children: [{
              kind: 'PREDICATE',
              columnName: 'status',
              operator: 'EQUALS',
              values: [{ dataType: 'STRING', value: 'PAID' }],
            }],
          },
        },
      }],
      edges: [],
    };

    const parsed = parseCanvasDefinitionJson(JSON.stringify(definition));
    expect(parsed.success).toBe(true);
    if (parsed.success) {
      expect(parsed.definition).toEqual(definition);
    }
  });

  it('round-trips processor configurations in Canvas 3.0', () => {
    const nodes = [
      {
        id: '8e469064-3b2a-41fb-b95a-a392189fbf1b',
        type: CanvasNodeType.NullHandling,
        name: '空值处理',
        layout: { x: 10, y: 20, width: 240, height: 120 },
        configuration: {
          sourceTableName: 'orders',
          outputTableName: 'orders_cleaned',
          rules: [
            {
              kind: 'DROP_ROW',
              columnNames: ['id', 'customer_id'],
              matchMode: 'ANY_NULL',
            },
            {
              kind: 'FILL_LITERAL',
              columnName: 'remark',
              value: { dataType: 'STRING', value: '-' },
            },
          ],
        },
      },
      {
        id: '2adef91b-dfe9-4720-9db7-832cffb9df94',
        type: CanvasNodeType.ValueMapping,
        name: '值映射',
        layout: { x: 280, y: 20, width: 240, height: 120 },
        configuration: {
          sourceTableName: 'orders_cleaned',
          outputTableName: 'orders_mapped',
          rules: [{
            columnName: 'status',
            entries: [{
              sourceValue: { dataType: 'STRING', value: 'P' },
              targetValue: { dataType: 'STRING', value: 'PAID' },
            }],
            unmatchedStrategy: 'KEEP',
            unmatchedValue: null,
          }],
        },
      },
      {
        id: '25130f0e-841e-49fd-b6e3-fba8b0fd60e9',
        type: CanvasNodeType.Window,
        name: '窗口计算',
        layout: { x: 550, y: 20, width: 240, height: 120 },
        configuration: {
          sourceTableName: 'orders_mapped',
          outputTableName: 'orders_ranked',
          partitionByColumns: ['customer_id'],
          orderBy: [{
            columnName: 'created_at',
            direction: 'DESC',
            nullOrdering: 'LAST',
          }],
          functions: [
            { kind: 'ROW_NUMBER', outputColumnName: 'row_no' },
            {
              kind: 'SUM',
              sourceColumnName: 'amount',
              outputColumnName: 'running_amount',
              frame: {
                type: 'ROWS',
                start: { kind: 'UNBOUNDED_PRECEDING' },
                end: { kind: 'CURRENT_ROW' },
              },
            },
          ],
        },
      },
      {
        id: '0138a0d4-f018-4ce8-a724-3f37bc92f690',
        type: CanvasNodeType.TopN,
        name: 'Top N',
        layout: { x: 820, y: 20, width: 240, height: 120 },
        configuration: {
          sourceTableName: 'orders_ranked',
          outputTableName: 'top_orders',
          partitionByColumns: ['customer_id'],
          orderBy: [{
            columnName: 'amount',
            direction: 'DESC',
            nullOrdering: 'LAST',
          }],
          limit: 3,
          tieStrategy: 'WITH_TIES',
        },
      },
    ];
    const definition = {
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      nodes,
      edges: [],
    };

    const parsed = parseCanvasDefinitionJson(JSON.stringify(definition));
    expect(parsed).toEqual({ success: true, definition });
  });

  it('does not mutate the current definition when parsing fails', () => {
    const current = exampleCanvasDefinition();
    const before = structuredClone(current);

    expect(parseCanvasDefinitionJson('{ invalid json').success).toBe(false);
    expect(current).toEqual(before);
  });

  it('downloads the stable definition with the fixed file name and revokes the object URL', () => {
    const createObjectURL = vi.fn(() => 'blob:canvas-definition');
    const revokeObjectURL = vi.fn();
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    downloadCanvasDefinition(exampleCanvasDefinition());

    expect(createObjectURL).toHaveBeenCalledOnce();
    expect(click).toHaveBeenCalledOnce();
    expect((click.mock.contexts[0] as HTMLAnchorElement).download).toBe(CANVAS_DEFINITION_FILE_NAME);
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:canvas-definition');
    expect(CANVAS_DEFINITION_FILE_NAME).toBe('canvas-task-definition.json');
  });
});
