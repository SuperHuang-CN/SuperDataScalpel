import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import { ExclamationCircleFilled, RightOutlined } from '@ant-design/icons';
import { Popover } from 'antd';
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
  const [hovered, setHovered] = useState(false);
  const [pinned, setPinned] = useState(false);
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
  const issueCountLabel = `${issues.length} 个配置问题`;
  const severitySummary = [
    errors.length > 0 ? `${errors.length} 个错误` : '',
    warnings.length > 0 ? `${warnings.length} 个警告` : '',
  ].filter(Boolean).join(' · ');
  const open = hovered || pinned;
  const togglePinned = () => {
    const nextPinned = !pinned;
    setPinned(nextPinned);
    if (!nextPinned) setHovered(false);
  };

  return (
    <Popover
      trigger="hover"
      placement="bottomLeft"
      arrow={false}
      destroyOnHidden
      rootClassName="canvas-validation-popover-overlay"
      getPopupContainer={() => document.body}
      open={open}
      onOpenChange={setHovered}
      content={(
        <div className="canvas-validation-popover">
          <div className="canvas-validation-popover-heading">
            <strong>{issueCountLabel}</strong>
            <span>{severitySummary}</span>
          </div>
          <div className="canvas-validation-list" role="list">
            {issues.map((item, index) => (
              <div className="canvas-validation-list-item" role="listitem" key={`${item.code}-${index}`}>
                <span className={`canvas-validation-dot canvas-validation-dot-${item.severity.toLowerCase()}`} />
                <div className="canvas-validation-issue">
                  <span>{item.message}</span>
                  <span className="canvas-validation-code">
                    {item.code}{item.path ? ` · ${item.path}` : ''}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    >
      <button
        type="button"
        className={`canvas-validation-compact${errors.length > 0 ? ' is-error' : ' is-warning'}`}
        aria-label={`查看 ${issueCountLabel}`}
        aria-expanded={open}
        onClick={togglePinned}
        onKeyDown={(event) => {
          if (event.key !== 'Escape') return;
          setPinned(false);
          setHovered(false);
        }}
      >
        <ExclamationCircleFilled className="canvas-validation-compact-icon" />
        <span className="canvas-validation-count">{issueCountLabel}</span>
        <RightOutlined className="canvas-validation-compact-arrow" />
      </button>
    </Popover>
  );
};
