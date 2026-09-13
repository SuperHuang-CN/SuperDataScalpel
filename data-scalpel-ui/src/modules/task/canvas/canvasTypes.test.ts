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

  it('provides an empty output projection for Stream Join drafts', () => {
    expect(emptyNodeConfiguration(CanvasNodeType.StreamJoin)).toEqual({
      leftTableName: '',
      rightTableName: '',
      outputTableName: '',
      joinType: null,
      conditions: [],
      outputColumns: [],
    });
  });

  it('provides strongly typed defaults for every supported node type', () => {
    expect(emptyNodeConfiguration(CanvasNodeType.JdbcInput)).toEqual({
      dataSourceId: '',
      tables: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.ModelInput)).toEqual({
      models: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.HttpApiInput)).toEqual({
      dataSourceId: '',
      resources: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.Join)).toEqual({
      leftTableName: '',
      rightTableName: '',
      outputTableName: '',
      joinType: null,
      conditions: [],
      outputColumns: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.Rename)).toEqual({
      operations: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.Filter)).toEqual({
      operations: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.SqlTransform)).toEqual({
      outputTableName: '',
      sql: '',
    });
    expect(emptyNodeConfiguration(CanvasNodeType.SelectColumns)).toEqual({
      operations: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.DeriveColumns)).toEqual({
      globalDerivations: [],
      operations: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.TypeCast)).toEqual({
      operations: [],
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
      mergingTables: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.Deduplicate)).toEqual({
      operations: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.NullHandling)).toEqual({
      operations: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.ValueMapping)).toEqual({
      operations: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.MaskFields)).toEqual({
      operations: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.JsonExtract)).toEqual({
      operations: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.Window)).toEqual({
      sourceTableName: '',
      outputTableName: '',
      partitionByColumns: [],
      orderBy: [],
      functions: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.TopN)).toEqual({
      operations: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.JdbcOutput)).toEqual({
      dataSourceId: '',
      writes: [],
      sourceTableName: '',
      targetTableName: '',
      writeMode: 'OVERWRITE',
      columnMappings: [],
      upsertKeyColumns: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.ModelOutput)).toEqual({
      writes: [],
      sourceTableName: '',
      targetModelId: '',
      writeMode: 'OVERWRITE',
      columnMappings: [],
    });
    expect(emptyNodeConfiguration(CanvasNodeType.JdbcSnapshotSyncOutput)).toEqual({
      sourceTableName: '',
      dataSourceId: '',
      targetTableName: '',
      keyColumns: [],
      columnMappings: [],
      deletePolicy: { action: 'KEEP', maxDeleteRows: null, maxDeleteRatio: null },
    });
    expect(emptyNodeConfiguration(CanvasNodeType.ModelSnapshotSyncOutput)).toEqual({
      sourceTableName: '',
      targetModelId: '',
      keyColumns: [],
      columnMappings: [],
      deletePolicy: { action: 'KEEP', maxDeleteRows: null, maxDeleteRatio: null },
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
        expect(node.configuration).toEqual({ dataSourceId: '', tables: [] });
      }
      if (node.type === CanvasNodeType.JdbcOutput) {
        expect(node.configuration.dataSourceId).toBe('');
        expect(node.configuration.writes).toEqual([]);
      }
    });
  });
});
