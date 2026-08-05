import { DownOutlined, UpOutlined } from '@ant-design/icons';
import { Alert, Button, Typography } from 'antd';
import { useState } from 'react';
import type { CanvasNodeValidationResult } from '../../canvasTypes';

export interface CanvasNodeValidationIssuesProps {
  validation: CanvasNodeValidationResult | undefined;
  unavailableMessage: string | null;
}

export const CanvasNodeValidationIssues = ({
  validation,
  unavailableMessage,
}: CanvasNodeValidationIssuesProps) => {
  const [expanded, setExpanded] = useState(false);
  if (!validation && unavailableMessage) {
    return (
      <Alert
        showIcon
        type="info"
        title="Task Engine 尚未完成校验"
        description={unavailableMessage}
      />
    );
  }
  const errors = validation?.issues.filter((item) => item.severity === 'ERROR') ?? [];
  const warnings = validation?.issues.filter((item) => item.severity === 'WARNING') ?? [];
  if (errors.length === 0 && warnings.length === 0) return null;
  const issues = [...errors, ...warnings];
  const primaryIssue = issues.find((item) => item.code === 'REQUIRED_CONFIGURATION') ?? issues[0];
  const summary = [
    errors.length > 0 ? `${errors.length} 个错误` : '',
    warnings.length > 0 ? `${warnings.length} 个警告` : '',
  ].filter(Boolean).join(' · ');
  return (
    <Alert
      showIcon
      type={errors.length > 0 ? 'error' : 'warning'}
      className="canvas-validation-alert"
      title={(
        <div className="canvas-validation-title">
          <span className="canvas-validation-count">{summary}</span>
          <span className="canvas-validation-primary" title={primaryIssue.message}>
            {primaryIssue.message}
          </span>
        </div>
      )}
      action={(
        <Button
          type="text"
          size="small"
          className="canvas-validation-action"
          icon={expanded ? <UpOutlined /> : <DownOutlined />}
          aria-label={expanded ? '收起问题详情' : '展开问题详情'}
          aria-expanded={expanded}
          onClick={() => setExpanded((current) => !current)}
        >
          {expanded ? '收起' : '详情'}
        </Button>
      )}
      description={expanded ? (
        <div className="canvas-validation-list" role="list">
          {issues.map((item, index) => (
            <div className="canvas-validation-list-item" role="listitem" key={`${item.code}-${index}`}>
              <span className={`canvas-validation-dot canvas-validation-dot-${item.severity.toLowerCase()}`} />
              <div className="canvas-validation-issue">
                <span>{item.message}</span>
                <Typography.Text type="secondary" className="canvas-validation-code">
                  {item.code}
                </Typography.Text>
              </div>
            </div>
          ))}
        </div>
      ) : undefined}
    />
  );
};
