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
import { CanvasNodeType } from './canvasTypes';

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

  it('reads legacy 1.0 definitions and normalizes them to the current 1.5 writer version', () => {
    const legacy = JSON.parse(formatCanvasDefinition(exampleCanvasDefinition())) as Record<string, unknown>;
    delete legacy.schemaMinorVersion;

    const parsed = parseCanvasDefinitionJson(JSON.stringify(legacy));

    expect(parsed.success).toBe(true);
    if (!parsed.success) return;
    expect(parsed.definition.schemaVersion).toBe(1);
    expect(parsed.definition.schemaMinorVersion).toBe(5);
    expect(formatCanvasDefinition(parsed.definition)).toContain('"schemaMinorVersion": 5');
  });

  it('normalizes legacy nested table identifiers to data-source-scoped table names', () => {
    const legacy = JSON.parse(formatCanvasDefinition(exampleCanvasDefinition())) as {
      nodes: Array<{ type: string; configuration: Record<string, unknown> }>;
    };
    const input = legacy.nodes.find((node) => node.type === CanvasNodeType.JdbcInput);
    const output = legacy.nodes.find((node) => node.type === CanvasNodeType.JdbcOutput);
    if (!input || !output) return;
    delete input.configuration.tableName;
    input.configuration.table = { catalogName: 'demo', schemaName: 'public', tableName: 'orders' };
    delete output.configuration.targetTableName;
    output.configuration.targetTable = { catalogName: 'demo', schemaName: 'dw', tableName: 'dwd_order_customer' };

    const parsed = parseCanvasDefinitionJson(JSON.stringify(legacy));

    expect(parsed.success).toBe(true);
    if (!parsed.success) return;
    const parsedInput = parsed.definition.nodes.find((node) => node.type === CanvasNodeType.JdbcInput);
    const parsedOutput = parsed.definition.nodes.find((node) => node.type === CanvasNodeType.JdbcOutput);
    expect(parsedInput?.configuration.tableName).toBe('orders');
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
      schemaVersion: 1 as const,
      schemaMinorVersion: 2 as const,
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
            columnMappingMode: 'BY_NAME' as const,
            columnMappings: [],
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
      definition: { ...definition, schemaMinorVersion: 5 },
    });

    definition.nodes[0].configuration.modelId = 'not-a-uuid';
    expect(parseCanvasDefinitionJson(JSON.stringify(definition)).success).toBe(false);
    definition.nodes[0].configuration.modelId = '';
    expect(parseCanvasDefinitionJson(JSON.stringify(definition)).success).toBe(true);
  });

  it('round-trips an HTTP API input with typed runtime parameters', () => {
    const definition = {
      schemaVersion: 1 as const,
      schemaMinorVersion: 2 as const,
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
      definition: { ...definition, schemaMinorVersion: 5 },
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
    unsupportedVersion.schemaVersion = 2;
    expect(parseCanvasDefinitionJson(JSON.stringify(unsupportedVersion)).success).toBe(false);

    unsupportedVersion.schemaVersion = 1;
    unsupportedVersion.schemaMinorVersion = 6;
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

  it('rejects model nodes that falsely declare the legacy 1.0 capability set', () => {
    const modelDefinition = {
      schemaVersion: 1,
      nodes: [{
        id: '1c436443-4cc0-4fe8-b748-4c02143eb602',
        type: CanvasNodeType.ModelInput,
        name: '模型输入',
        layout: { x: 10, y: 20, width: 240, height: 120 },
        configuration: { modelId: '' },
      }],
      edges: [],
    };

    const parsed = parseCanvasDefinitionJson(JSON.stringify(modelDefinition));

    expect(parsed.success).toBe(false);
    if (!parsed.success) {
      expect(parsed.errors).toContain('MODEL_INPUT 和 MODEL_OUTPUT 从 Canvas 1.1 开始支持');
    }
  });

  it('upgrades Rename from 1.2 to 1.5 and rejects it from older capability sets', () => {
    const definition = {
      schemaVersion: 1,
      schemaMinorVersion: 2,
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
      expect(parsed.definition).toEqual({ ...definition, schemaMinorVersion: 5 });
    }

    definition.schemaMinorVersion = 1;
    const incompatible = parseCanvasDefinitionJson(JSON.stringify(definition));
    expect(incompatible.success).toBe(false);
    if (!incompatible.success) {
      expect(incompatible.errors).toContain('RENAME 从 Canvas 1.2 开始支持');
    }
  });

  it('round-trips Canvas 1.5 streaming nodes and applies streaming capability gates', () => {
    const definition = exampleStreamingCanvasTopologyDefinition();
    const parsed = parseCanvasDefinitionJson(formatCanvasDefinition(definition));

    expect(parsed).toEqual({ success: true, definition });

    const incompatible = JSON.parse(formatCanvasDefinition(definition)) as {
      schemaMinorVersion: number;
    };
    incompatible.schemaMinorVersion = 2;
    const legacyResult = parseCanvasDefinitionJson(JSON.stringify(incompatible));
    expect(legacyResult.success).toBe(false);
    if (!legacyResult.success) {
      expect(legacyResult.errors).toContain(
        'STREAM_JOIN 从 Canvas 1.3 开始支持',
      );
      expect(legacyResult.errors).toContain(
        'KAFKA_INPUT 和 KAFKA_OUTPUT 的内联 Value Schema 从 Canvas 1.5 开始支持',
      );
    }
  });

  it('round-trips file dataset input in 1.4 and rejects it from 1.3', () => {
    const definition = {
      schemaVersion: 1,
      schemaMinorVersion: 4,
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
      definition: { ...definition, schemaMinorVersion: 5 },
    });

    definition.schemaMinorVersion = 3;
    const incompatible = parseCanvasDefinitionJson(JSON.stringify(definition));
    expect(incompatible.success).toBe(false);
    if (!incompatible.success) {
      expect(incompatible.errors).toContain('FILE_DATASET_INPUT 从 Canvas 1.4 开始支持');
    }
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
