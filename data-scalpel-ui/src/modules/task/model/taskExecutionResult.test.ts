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
