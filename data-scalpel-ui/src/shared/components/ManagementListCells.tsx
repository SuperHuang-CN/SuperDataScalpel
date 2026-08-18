import type { ReactNode } from 'react';
import { Tooltip } from 'antd';

interface ManagementListCellProps {
  primary: ReactNode;
  secondary?: ReactNode;
  className?: string;
  icon?: ReactNode;
  iconLabel?: string;
  iconTone?: 'blue' | 'violet' | 'cyan' | 'green' | 'orange' | 'rose' | 'slate';
}

export const ManagementListCell = ({ primary, secondary, className, icon, iconLabel, iconTone = 'blue' }: ManagementListCellProps) => (
  <div className={['management-list-cell', icon ? 'management-list-cell-has-icon' : null, className].filter(Boolean).join(' ')}>
    {icon && (
      <span
        className={`management-list-cell-icon management-list-cell-icon-${iconTone}`}
        {...(iconLabel ? { role: 'img', 'aria-label': iconLabel } : { 'aria-hidden': true })}
      >
        {icon}
      </span>
    )}
    <div className="management-list-cell-content">
      <div className="management-list-cell-primary">{primary}</div>
      {secondary !== undefined && secondary !== null && (
        <div className="management-list-cell-secondary">{secondary}</div>
      )}
    </div>
  </div>
);

interface ManagementCodeProps {
  value: string;
  title?: string;
}

export type ManagementStatusTone = 'default' | 'processing' | 'success' | 'warning' | 'error';

interface ManagementStatusIndicatorProps {
  label: ReactNode;
  tone?: ManagementStatusTone;
  title?: ReactNode;
}

export const ManagementStatusIndicator = ({
  label,
  tone = 'default',
  title,
}: ManagementStatusIndicatorProps) => {
  const indicator = (
    <span className={`management-status-indicator management-status-indicator-${tone}`}>
      <span className="management-status-indicator-dot" aria-hidden />
      <span>{label}</span>
    </span>
  );
  return title ? <Tooltip title={title}>{indicator}</Tooltip> : indicator;
};

export const ManagementCode = ({ value, title }: ManagementCodeProps) => (
  <Tooltip title={title ?? value}>
    <code className="management-list-code">{value}</code>
  </Tooltip>
);

export const ManagementDateTime = ({ value }: { value: string | null | undefined }) => {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const dateText = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium' }).format(date);
  const timeText = new Intl.DateTimeFormat('zh-CN', { timeStyle: 'medium', hour12: false }).format(date);
  return <ManagementListCell primary={dateText} secondary={timeText} />;
};
