import {
  CheckCircleFilled,
  CloseCircleFilled,
  CloseOutlined,
  ExclamationCircleFilled,
  InfoCircleOutlined,
} from '@ant-design/icons';
import type { AlertProps } from 'antd';
import { Button, Popover, Tooltip } from 'antd';
import type { CSSProperties, MouseEventHandler, ReactNode } from 'react';
import { useState } from 'react';

export type FeedbackTone = 'success' | 'info' | 'warning' | 'error';

const feedbackIcons: Record<FeedbackTone, ReactNode> = {
  success: <CheckCircleFilled />,
  info: <InfoCircleOutlined />,
  warning: <ExclamationCircleFilled />,
  error: <CloseCircleFilled />,
};

const textFromNode = (value: ReactNode) => typeof value === 'string' ? value : undefined;

const isActiveProcess = (value: ReactNode) => {
  const text = textFromNode(value);
  return Boolean(text && /正在|加载|解析|校验|读取|创建|导入|保存|发布|同步|执行|测试|生成|运行中|等待/.test(text));
};

interface ContextHelpProps {
  ariaLabel: string;
  content: ReactNode;
  tone?: 'info' | 'warning';
  presentation?: 'tooltip' | 'popover';
  placement?: 'top' | 'topLeft' | 'topRight' | 'right' | 'bottom' | 'bottomLeft' | 'bottomRight' | 'left';
  className?: string;
}

/**
 * A deliberately small, contextual entry point for explanatory information.
 * It supports hover, keyboard focus and click so the same information remains
 * available on desktop, keyboard-only and touch devices.
 */
export const ContextHelp = ({
  ariaLabel,
  content,
  tone = 'info',
  presentation = 'tooltip',
  placement = 'top',
  className,
}: ContextHelpProps) => {
  const trigger = (
    <Button
      type="text"
      size="small"
      shape="circle"
      className={['context-help-trigger', `context-help-trigger-${tone}`, className].filter(Boolean).join(' ')}
      icon={tone === 'warning' ? <ExclamationCircleFilled /> : <InfoCircleOutlined />}
      aria-label={ariaLabel}
    />
  );

  if (presentation === 'popover') {
    return (
      <Popover
        title={ariaLabel}
        content={<div className="context-help-popover-content">{content}</div>}
        trigger={['hover', 'focus', 'click']}
        placement={placement}
        overlayClassName="context-help-popover"
      >
        {trigger}
      </Popover>
    );
  }

  return (
    <Tooltip
      title={content}
      trigger={['hover', 'focus', 'click']}
      placement={placement}
      overlayClassName="context-help-tooltip"
    >
      {trigger}
    </Tooltip>
  );
};

interface InlineFeedbackProps {
  tone?: FeedbackTone;
  label: ReactNode;
  detail?: ReactNode;
  action?: ReactNode;
  className?: string;
  style?: CSSProperties;
  icon?: ReactNode;
  ariaLabel?: string;
  onMouseEnter?: MouseEventHandler<HTMLDivElement>;
  onMouseLeave?: MouseEventHandler<HTMLDivElement>;
  onClick?: MouseEventHandler<HTMLDivElement>;
}

/** A concise, directly visible outcome with optional details available on demand. */
export const InlineFeedback = ({
  tone = 'info',
  label,
  detail,
  action,
  className,
  style,
  icon,
  ariaLabel,
  onMouseEnter,
  onMouseLeave,
  onClick,
}: InlineFeedbackProps) => {
  const content = (
    <div
      className={['inline-feedback', detail ? 'inline-feedback-details-trigger' : null, `inline-feedback-${tone}`, className].filter(Boolean).join(' ')}
      style={style}
      role={tone === 'error' || tone === 'warning' ? 'status' : undefined}
      tabIndex={detail ? 0 : undefined}
      aria-label={detail ? ariaLabel ?? `${textFromNode(label) ?? '状态'}详情` : undefined}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      onClick={onClick}
    >
      <span className="inline-feedback-icon" aria-hidden>{icon ?? feedbackIcons[tone]}</span>
      <span className="inline-feedback-label">{label}</span>
      {action && <span className="inline-feedback-action">{action}</span>}
    </div>
  );

  if (!detail) return content;

  return (
    <Popover
      title={ariaLabel ?? textFromNode(label) ?? '查看详情'}
      content={<div className="inline-feedback-popover-content">{detail}</div>}
      trigger={['hover', 'focus', 'click']}
      overlayClassName="inline-feedback-popover"
    >
      {content}
    </Popover>
  );
};

/**
 * Compatibility adapter for historical Ant Design Alert call sites. It keeps
 * their feedback, retry actions and close behaviour while removing full-width
 * colored banners from every existing screen.
 */
export const CompactAlert = ({
  type = 'info',
  title,
  message,
  description,
  action,
  closable,
  closeText,
  closeIcon,
  onClose,
  afterClose,
  icon,
  className,
  style,
  onMouseEnter,
  onMouseLeave,
  onClick,
}: AlertProps) => {
  const [closed, setClosed] = useState(false);
  if (closed) return null;

  const label = title ?? message ?? '状态提示';
  if (type === 'info' && !action && !closable && !isActiveProcess(label)) {
    return (
      <ContextHelp
        ariaLabel={textFromNode(label) ?? '查看说明'}
        content={description ?? label}
        presentation={description ? 'popover' : 'tooltip'}
        className={className}
      />
    );
  }

  const close = (event: Parameters<NonNullable<AlertProps['onClose']>>[0]) => {
    onClose?.(event);
    setClosed(true);
    afterClose?.();
  };
  const closeAction = closable ? (
    <Button
      type="text"
      size="small"
      shape="circle"
      className="inline-feedback-close"
      icon={closeIcon ?? <CloseOutlined />}
      aria-label="关闭提示"
      onClick={close}
    >
      {closeText}
    </Button>
  ) : null;

  return (
    <InlineFeedback
      tone={type}
      label={label}
      detail={description}
      action={action || closeAction ? <>{action}{closeAction}</> : undefined}
      icon={icon}
      className={className}
      style={style}
      ariaLabel={textFromNode(label) ?? '查看提示详情'}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      onClick={onClick}
    />
  );
};
