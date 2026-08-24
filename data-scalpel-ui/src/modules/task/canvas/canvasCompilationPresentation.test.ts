import { describe, expect, it } from 'vitest';
import { canvasCompilationIssues, canvasNodeCompilationBadge } from './canvasCompilationPresentation';
import type { CanvasNodeValidationResult, CanvasValidationResult } from './canvasTypes';

const nodeResult = (issues: CanvasNodeValidationResult['issues']): CanvasNodeValidationResult => ({
  nodeId: '4add70a7-4948-42a5-af66-e56dbaccad3e',
  issues,
  inputTables: [],
  outputTables: [],
});

describe('canvas compilation presentation', () => {
  it('never claims validity before Task Engine returns a node result', () => {
    expect(canvasNodeCompilationBadge(undefined)).toEqual({
      status: 'UNCHECKED',
      message: '等待 Task Engine 校验',
    });
    expect(canvasCompilationIssues(null)).toEqual([]);
  });

  it('maps only Task Engine issues to node status and issue summaries', () => {
    const requiredIssue = {
      code: 'REQUIRED_CONFIGURATION',
      severity: 'ERROR' as const,
      message: '请选择输入表',
      nodeId: '4add70a7-4948-42a5-af66-e56dbaccad3e',
      path: 'configuration.tables',
    };
    const result = nodeResult([requiredIssue]);
    const validation: CanvasValidationResult = {
      valid: false,
      canvasIssues: [],
      nodeResults: new Map([[result.nodeId, result]]),
    };

    expect(canvasNodeCompilationBadge(result).status).toBe('UNCONFIGURED');
    expect(canvasCompilationIssues(validation)).toEqual([requiredIssue]);
  });
});
