import { describe, expect, it } from 'vitest';
import { exampleCanvasDefinition, exampleCanvasTopologyDefinition } from './defaultCanvas';
import {
  CANVAS_SCHEMA_MINOR_VERSION,
  CANVAS_SCHEMA_VERSION,
  CanvasNodeType,
  createsCycle,
  emptyNodeConfiguration,
  type CanvasEdgeDefinition,
} from './canvasTypes';

describe('task canvas connection rules', () => {
  it('detects a directed cycle in stable edge definitions', () => {
    const edges: CanvasEdgeDefinition[] = [{
      id: 'edge-id',
      sourceNodeId: 'input',
      targetNodeId: 'join',
    }];

    expect(createsCycle(edges, 'join', 'input')).toBe(true);
    expect(createsCycle(edges, 'join', 'output')).toBe(false);
  });

  it('provides strongly typed defaults for every supported node type', () => {
    expect(emptyNodeConfiguration(CanvasNodeType.JdbcInput)).toEqual({
      dataSourceId: '',
      tableName: '',
    });
    expect(emptyNodeConfiguration(CanvasNodeType.ModelInput)).toEqual({
      modelId: '',
    });
    expect(emptyNodeConfiguration(CanvasNodeType.HttpApiInput)).toEqual({
      dataSourceId: '',
      resourceId: '',
      outputTableName: '',
      runtimeParameters: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.Join)).toEqual({
      leftTableName: '',
      rightTableName: '',
      outputTableName: '',
      joinType: null,
      conditions: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.Rename)).toEqual({
      sourceTableName: '',
      outputTableName: '',
      columnMappings: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.Filter)).toEqual({
      sourceTableName: '',
      outputTableName: '',
      condition: { kind: 'GROUP', operator: 'AND', children: [] },
    });
    expect(emptyNodeConfiguration(CanvasNodeType.SelectColumns)).toEqual({
      sourceTableName: '',
      outputTableName: '',
      columns: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.DeriveColumns)).toEqual({
      sourceTableName: '',
      outputTableName: '',
      derivations: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.TypeCast)).toEqual({
      sourceTableName: '',
      outputTableName: '',
      casts: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.Aggregate)).toEqual({
      sourceTableName: '',
      outputTableName: '',
      groupByColumns: [],
      aggregations: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.Union)).toEqual({
      inputTableNames: [],
      outputTableName: '',
      mode: 'ALL',
    });
    expect(emptyNodeConfiguration(CanvasNodeType.Deduplicate)).toEqual({
      sourceTableName: '',
      outputTableName: '',
      keyColumns: [],
      keepStrategy: 'ANY',
      orderBy: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.NullHandling)).toEqual({
      sourceTableName: '',
      outputTableName: '',
      rules: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.ValueMapping)).toEqual({
      sourceTableName: '',
      outputTableName: '',
      rules: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.MaskFields)).toEqual({
      sourceTableName: '',
      outputTableName: '',
      fieldRules: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.JsonExtract)).toEqual({
      sourceTableName: '',
      outputTableName: '',
      sourceColumnName: '',
      extractions: [],
      failureStrategy: 'ERROR',
    });
    expect(emptyNodeConfiguration(CanvasNodeType.Window)).toEqual({
      sourceTableName: '',
      outputTableName: '',
      partitionByColumns: [],
      orderBy: [],
      functions: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.TopN)).toEqual({
      sourceTableName: '',
      outputTableName: '',
      partitionByColumns: [],
      orderBy: [],
      limit: 10,
      tieStrategy: 'EXACT',
    });
    expect(emptyNodeConfiguration(CanvasNodeType.JdbcOutput)).toEqual({
      sourceTableName: '',
      dataSourceId: '',
      targetTableName: '',
      writeMode: null,
      upsertKeyColumns: [],
      columnMappingMode: null,
      columnMappings: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.ModelOutput)).toEqual({
      sourceTableName: '',
      targetModelId: '',
      writeMode: null,
      columnMappingMode: null,
      columnMappings: [],
    });
  });

  it('keeps the complete JDBC example on the current schema version', () => {
    const definition = exampleCanvasDefinition();

    expect(definition.schemaVersion).toBe(CANVAS_SCHEMA_VERSION);
    expect(definition.schemaMinorVersion).toBe(CANVAS_SCHEMA_MINOR_VERSION);
    expect(new Set(definition.nodes.map((node) => node.type))).toEqual(new Set([
      CanvasNodeType.JdbcInput,
      CanvasNodeType.Join,
      CanvasNodeType.JdbcOutput,
    ]));
  });

  it('loads the UI topology example without fake data-source or table selections', () => {
    const definition = exampleCanvasTopologyDefinition();

    definition.nodes.forEach((node) => {
      if (node.type === CanvasNodeType.JdbcInput) {
        expect(node.configuration).toEqual({ dataSourceId: '', tableName: '' });
      }
      if (node.type === CanvasNodeType.JdbcOutput) {
        expect(node.configuration.dataSourceId).toBe('');
        expect(node.configuration.targetTableName).toBe('');
      }
    });
  });
});
