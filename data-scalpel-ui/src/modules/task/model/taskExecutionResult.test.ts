import { describe, expect, it } from 'vitest';
import { parseTaskExecutionResultArtifact } from './taskExecutionResult';

const observability = {
  capturedAt: '2026-08-14T06:00:00Z',
  status: {
    phase: 'WRITE_OUTPUT',
    message: '正在写入结果',
    updatedAt: '2026-08-14T06:00:00Z',
  },
  metrics: [
    {
      name: 'datascalpel.model.write.successes',
      kind: 'COUNTER',
      counterValue: 1,
      gaugeValue: null,
      count: null,
      lastDurationMillis: null,
      totalDurationMillis: null,
      maxDurationMillis: null,
    },
    {
      name: 'orders.write',
      kind: 'TIMER',
      counterValue: null,
      gaugeValue: null,
      count: 1,
      lastDurationMillis: 8,
      totalDurationMillis: 8,
      maxDurationMillis: 8,
    },
  ],
};

describe('task execution result observability', () => {
  it('reads a strict v6 Spark JAR observability snapshot', () => {
    const result = parseTaskExecutionResultArtifact({
      schemaVersion: 6,
      taskType: 'SPARK_JAR',
      nodeResults: [],
      qualityResult: null,
      userJobObservability: observability,
    });

    expect(result.userJobObservability?.status?.phase).toBe('WRITE_OUTPUT');
    expect(result.userJobObservability?.metrics.map((metric) => metric.name)).toEqual([
      'datascalpel.model.write.successes',
      'orders.write',
    ]);
  });

  it('rejects observability on Canvas results and unknown reserved metrics', () => {
    expect(() => parseTaskExecutionResultArtifact({
      schemaVersion: 6,
      taskType: 'SPARK_CANVAS',
      nodeResults: [],
      qualityResult: null,
      userJobObservability: observability,
    })).toThrow('Canvas结果不能包含用户作业观测载荷');

    expect(() => parseTaskExecutionResultArtifact({
      schemaVersion: 6,
      taskType: 'SPARK_JAR',
      nodeResults: [],
      qualityResult: null,
      userJobObservability: {
        ...observability,
        metrics: [{ ...observability.metrics[0], name: 'datascalpel.unknown' }],
      },
    })).toThrow('平台保留指标名称或类型无效');

    expect(() => parseTaskExecutionResultArtifact({
      schemaVersion: 5,
      taskType: 'SPARK_JAR',
      nodeResults: [],
      qualityResult: null,
      userJobObservability: observability,
    })).toThrow('Spark JAR 结果载荷与任务类型不一致');
  });
});

describe('task execution result output writes', () => {
  const successfulNode = {
    nodeId: '20761935-b98f-4be9-8fd2-24f8ea1d0323',
    nodeType: 'MODEL_OUTPUT',
    nodeName: '模型输出',
    state: 'SUCCESS',
    rowsWritten: 10_723,
    metrics: {
      kind: 'OUTPUT_WRITES',
      writes: [
        {
          writeId: 'f67e3a76-6357-4b78-89e0-5a2ce2da08a1',
          sourceTableName: 'source_a',
          targetDisplayName: 'target_a',
          state: 'SUCCESS',
          affectedRows: 10_562,
          errorCode: null,
        },
        {
          writeId: '8c8a6673-c6e6-4576-a36c-ae0dd7521232',
          sourceTableName: 'source_b',
          targetDisplayName: 'target_b',
          state: 'SUCCESS',
          affectedRows: 161,
          errorCode: null,
        },
      ],
    },
  };

  it('reads two writes as one v7 output node result', () => {
    const result = parseTaskExecutionResultArtifact({
      schemaVersion: 7,
      taskType: 'SPARK_CANVAS',
      nodeResults: [successfulNode],
      qualityResult: null,
      userJobObservability: null,
    });

    expect(result.nodeResults).toHaveLength(1);
    expect(result.nodeResults[0].metrics).toMatchObject({
      kind: 'OUTPUT_WRITES',
      writes: [{ affectedRows: 10_562 }, { affectedRows: 161 }],
    });
  });

  it('rejects duplicate nodes and inconsistent write totals', () => {
    expect(() => parseTaskExecutionResultArtifact({
      schemaVersion: 7,
      taskType: 'SPARK_CANVAS',
      nodeResults: [successfulNode, successfulNode],
      qualityResult: null,
      userJobObservability: null,
    })).toThrow('格式无效');

    expect(() => parseTaskExecutionResultArtifact({
      schemaVersion: 7,
      taskType: 'SPARK_CANVAS',
      nodeResults: [{ ...successfulNode, rowsWritten: 1 }],
      qualityResult: null,
      userJobObservability: null,
    })).toThrow('写入行数与逐写入指标不一致');
  });
});
