import { describe, expect, it } from 'vitest';
import { taskCompilationValidation, type TaskCompilationResponse } from './taskCompilationTypes';

describe('taskCompilationValidation', () => {
  it('preserves engine issues and schemas while indexing node results', () => {
    const response: TaskCompilationResponse = {
      requestId: 'a39bb068-bbb9-40b0-8136-1b1adecc3953',
      taskType: 'CANVAS',
      valid: false,
      durationMs: 12,
      sparkApplicationId: 'local-test',
      canvasIssues: [{
        code: 'CANVAS_CYCLE',
        severity: 'ERROR',
        message: '画布存在环路',
        nodeId: null,
        path: 'edges',
      }],
      nodeResults: [{
        nodeId: '4add70a7-4948-42a5-af66-e56dbaccad3e',
        state: 'WARNING',
        inputTables: [],
        outputTables: [{
          name: 'orders',
          origin: {
            kind: 'JDBC',
            dataSourceId: '55859069-6387-4390-b850-104845ee5370',
            tableName: 'orders',
            modelId: null,
            modelCode: null,
            modelSchemaVersion: null,
          },
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
          }],
          datasetKind: 'BOUNDED',
          eventTimeColumn: null,
          watermarkDelay: null,
        }],
        issues: [{
          code: 'EXTRA_SOURCE_COLUMN',
          severity: 'WARNING',
          message: '存在未写入字段',
          nodeId: '4add70a7-4948-42a5-af66-e56dbaccad3e',
          path: 'configuration',
        }],
      }],
    };

    const validation = taskCompilationValidation(response);

    expect(validation.valid).toBe(false);
    expect(validation.canvasIssues).toEqual(response.canvasIssues);
    expect(validation.nodeResults.get('4add70a7-4948-42a5-af66-e56dbaccad3e'))
      .toEqual(expect.objectContaining({
        issues: response.nodeResults[0].issues,
        outputTables: response.nodeResults[0].outputTables,
      }));
  });
});
