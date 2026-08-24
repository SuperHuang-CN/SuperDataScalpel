import { describe, expect, it } from 'vitest';
import { CANVAS_SCHEMA_MINOR_VERSION, CANVAS_SCHEMA_VERSION, CanvasNodeType } from './canvasTypes';
import { parseCanvasDefinition } from './canvasDefinitionIO';
import { parseCanvasNodeConfiguration } from './nodes/nodeConfigurationParser';
import { parseTaskExecutionResultArtifact } from '../model/taskExecutionResult';

describe('snapshot sync frontend contracts', () => {
  it('round-trips Snapshot Sync nodes with unified mappings in Canvas 2.0', () => {
    const definition = {
      schemaVersion: CANVAS_SCHEMA_VERSION,
      schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
      nodes: [{
        id: '11111111-1111-4111-8111-111111111111',
        type: 'JDBC_SNAPSHOT_SYNC_OUTPUT',
        name: 'JDBC 快照同步',
        layout: { x: 0, y: 0, width: 260, height: 120 },
        configuration: {
          sourceTableName: 'reservoir_source',
          dataSourceId: '55859069-6387-4390-b850-104845ee5370',
          targetTableName: 'reservoir',
          keyColumns: ['reservoir_code'],
          columnMappings: [{
            sourceColumnName: 'reservoir_code',
            targetColumnName: 'reservoir_code',
          }],
          deletePolicy: { action: 'KEEP', maxDeleteRows: null, maxDeleteRatio: null },
        },
      }],
      edges: [],
    };

    const current = parseCanvasDefinition(definition);
    expect(current.success).toBe(true);
    if (current.success) {
      expect(current.definition.schemaMinorVersion).toBe(CANVAS_SCHEMA_MINOR_VERSION);
      expect(current.definition.nodes[0].type).toBe(CanvasNodeType.JdbcSnapshotSyncOutput);
    }
    const previous = parseCanvasDefinition({ ...definition, schemaVersion: 1 });
    expect(previous.success).toBe(false);
    if (!previous.success) {
      expect(previous.errors).toContain(`schemaVersion 仅支持 ${CANVAS_SCHEMA_VERSION}`);
    }
  });

  it('parses JDBC and model configurations without changing delete protection', () => {
    const shared = {
      sourceTableName: 'reservoir_source',
      keyColumns: ['reservoir_code'],
      columnMappings: [{
        sourceColumnName: 'reservoir_code',
        targetColumnName: 'reservoir_code',
      }],
      deletePolicy: { action: 'DELETE', maxDeleteRows: 1000, maxDeleteRatio: 0.2 },
    };
    const jdbc = parseCanvasNodeConfiguration(CanvasNodeType.JdbcSnapshotSyncOutput, {
      ...shared,
      dataSourceId: 'target-source',
      targetTableName: 'reservoir',
    }, 'configuration');
    const model = parseCanvasNodeConfiguration(CanvasNodeType.ModelSnapshotSyncOutput, {
      ...shared,
      targetModelId: '805c80b3-959e-4690-90d3-5c2d613864c1',
    }, 'configuration');

    expect(jdbc.success).toBe(true);
    expect(model.success).toBe(true);
    if (jdbc.success) expect(jdbc.value.deletePolicy.maxDeleteRatio).toBe(0.2);
    if (model.success) expect(model.value.keyColumns).toEqual(['reservoir_code']);
  });

  it('reads result v2 and v3 while exposing Snapshot Sync metrics only in v3', () => {
    expect(parseTaskExecutionResultArtifact({ schemaVersion: 2, nodeResults: [] }))
      .toEqual({
        schemaVersion: 2,
        taskType: 'SPARK_CANVAS',
        nodeResults: [],
        qualityResult: null,
        userJobObservability: null,
      });
    const result = parseTaskExecutionResultArtifact({
      schemaVersion: 3,
      nodeResults: [{
        nodeId: 'snapshot-node',
        nodeType: 'JDBC_SNAPSHOT_SYNC_OUTPUT',
        nodeName: '水库快照同步',
        state: 'SUCCESS',
        rowsWritten: 3,
        metrics: {
          kind: 'SNAPSHOT_SYNC',
          sourceRows: 10,
          targetRows: 9,
          insertedRows: 2,
          updatedRows: 1,
          deletedRows: 0,
          unchangedRows: 7,
          retainedTargetOnlyRows: 1,
        },
      }],
    });

    expect(result.nodeResults[0].metrics).toMatchObject({
      kind: 'SNAPSHOT_SYNC',
      insertedRows: 2,
      updatedRows: 1,
      retainedTargetOnlyRows: 1,
    });
  });

  it('rejects missing, failed or misplaced Snapshot Sync metrics', () => {
    const snapshotNode = {
      nodeId: 'snapshot-node',
      nodeType: 'MODEL_SNAPSHOT_SYNC_OUTPUT',
      nodeName: '模型快照同步',
      state: 'SUCCESS',
      rowsWritten: 0,
      metrics: null,
    };
    expect(() => parseTaskExecutionResultArtifact({
      schemaVersion: 3,
      nodeResults: [snapshotNode],
    })).toThrow('缺少 Snapshot Sync 指标');

    const metrics = {
      kind: 'SNAPSHOT_SYNC',
      sourceRows: 0,
      targetRows: 0,
      insertedRows: 0,
      updatedRows: 0,
      deletedRows: 0,
      unchangedRows: 0,
      retainedTargetOnlyRows: 0,
    };
    expect(() => parseTaskExecutionResultArtifact({
      schemaVersion: 3,
      nodeResults: [{ ...snapshotNode, state: 'FAILED', metrics }],
    })).toThrow('失败时不能包含 Snapshot Sync 指标');
    expect(() => parseTaskExecutionResultArtifact({
      schemaVersion: 3,
      nodeResults: [{ ...snapshotNode, nodeType: 'JDBC_OUTPUT', metrics }],
    })).toThrow('不是 Snapshot Sync 节点');
  });
});
