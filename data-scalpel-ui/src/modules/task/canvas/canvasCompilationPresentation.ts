import type {
  CanvasNodeValidationBadge,
  CanvasNodeValidationResult,
  CanvasValidationIssue,
  CanvasValidationResult,
} from './canvasTypes';

export const canvasCompilationIssues = (
  validation: CanvasValidationResult | null,
): CanvasValidationIssue[] => validation
  ? [validation.canvasIssues, ...[...validation.nodeResults.values()].map((result) => result.issues)].flat()
  : [];

export const canvasNodeCompilationBadge = (
  validation: CanvasNodeValidationResult | undefined,
): CanvasNodeValidationBadge => {
  if (!validation) return { status: 'UNCHECKED', message: '等待 Task Engine 校验' };
  const errors = validation.issues.filter((item) => item.severity === 'ERROR');
  const warnings = validation.issues.filter((item) => item.severity === 'WARNING');
  if (errors.some((item) => item.code === 'REQUIRED_CONFIGURATION')) {
    return { status: 'UNCONFIGURED', message: '节点配置尚未完成' };
  }
  if (errors.length > 0) return { status: 'ERROR', message: `${errors.length} 个错误` };
  if (warnings.length > 0) return { status: 'WARNING', message: `${warnings.length} 个警告` };
  return { status: 'VALID', message: '引擎校验通过' };
};
