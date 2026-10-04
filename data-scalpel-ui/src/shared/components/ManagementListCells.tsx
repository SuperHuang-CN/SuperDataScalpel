import type { ReactNode } from 'react';
import { theme, Tooltip } from 'antd';
import { ContextHelp } from './ContextualFeedback';
import { formatManagementDateTime } from '../format/managementDateTime';

/** Keeps the existing link/action while exposing the full resource identity nearby. */
export const ManagementName = ({ name, code, description, children }: {
  name: string;
  code?: string;
  description?: string | null;
  children: ReactNode;
}) => (
  <div className="management-name-row">
    {children}
    <ContextHelp ariaLabel={`${name}的${code ? '编码与' : ''}说明`} presentation="popover" placement="right" content={(
      <div className="management-name-detail">
        {code && <div><span>编码</span><p>{code}</p></div>}
        <div><span>说明</span><p>{description || '暂无说明'}</p></div>
      </div>
    )} />
  </div>
);

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
  const { token } = theme.useToken();
  const colors = {
    default: [token.colorFillQuaternary, token.colorTextSecondary, token.colorBorderSecondary],
    processing: [token.colorInfoBg, token.colorInfoText, token.colorInfoBorder],
    success: [token.colorSuccessBg, token.colorSuccessText, token.colorSuccessBorder],
    warning: [token.colorWarningBg, token.colorWarningText, token.colorWarningBorder],
    error: [token.colorErrorBg, token.colorErrorText, token.colorErrorBorder],
  }[tone];
  const indicator = (
    <span className={`management-status-indicator management-status-indicator-${tone}`} style={{ background: colors[0], color: colors[1], borderColor: colors[2] }}>
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
  const [dateText, timeText] = formatManagementDateTime(value).split(' ');
  return <ManagementListCell primary={dateText} secondary={timeText} />;
};
