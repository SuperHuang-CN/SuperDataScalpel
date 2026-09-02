import { ArrowRightOutlined, TableOutlined } from '@ant-design/icons';
import { Tooltip } from 'antd';
import { useState, type ReactNode } from 'react';
import type { CanvasTableSchema } from '../../canvasTypes';
import { CanvasTableSchemaModal } from '../CanvasTableSchemaModal';

const text = (value: ReactNode): string | undefined => (
  typeof value === 'string' || typeof value === 'number' ? String(value) : undefined
);

export const NodeContent = ({
  children,
  variant = 'flow',
}: {
  children: ReactNode;
  variant?: 'source' | 'flow' | 'dual' | 'rules' | 'aggregate' | 'spatial' | 'output';
}) => <div className={`canvas-semantic-node canvas-semantic-node-${variant}`}>{children}</div>;

export const NodeEmpty = ({ children }: { children: ReactNode }) => (
  <div className="canvas-semantic-empty">
    <span className="canvas-semantic-empty-mark" />
    <span>{children}</span>
  </div>
);

export const NodeTitleLine = ({
  primary,
  secondary,
  accent,
}: {
  primary: ReactNode;
  secondary?: ReactNode;
  accent?: boolean;
}) => (
  <div className={`canvas-semantic-title-line${accent ? ' is-accent' : ''}`}>
    <Tooltip title={text(primary)}><span className="canvas-semantic-primary">{primary || '待配置'}</span></Tooltip>
    {secondary !== undefined && (
      <Tooltip title={text(secondary)}><span className="canvas-semantic-secondary">{secondary}</span></Tooltip>
    )}
  </div>
);

export const NodeFlow = ({
  source,
  target,
  operation,
}: {
  source: ReactNode;
  target: ReactNode;
  operation?: ReactNode;
}) => (
  <div className="canvas-semantic-flow">
    <Tooltip title={text(source)}><span className="canvas-semantic-flow-end">{source || '待选择'}</span></Tooltip>
    <span className="canvas-semantic-flow-arrow">
      {operation && <span className="canvas-semantic-operation">{operation}</span>}
      <ArrowRightOutlined />
    </span>
    <Tooltip title={text(target)}><span className="canvas-semantic-flow-end is-target">{target || '待设置'}</span></Tooltip>
  </div>
);

export const NodeDualFlow = ({
  left,
  right,
  operation,
  target,
  leftLabel,
  rightLabel,
}: {
  left: ReactNode;
  right: ReactNode;
  operation: ReactNode;
  target: ReactNode;
  leftLabel?: ReactNode;
  rightLabel?: ReactNode;
}) => (
  <div className="canvas-semantic-dual-flow">
    <div className="canvas-semantic-dual-sources">
      <div><small>{leftLabel}</small><Tooltip title={text(left)}><span>{left || '待选择'}</span></Tooltip></div>
      <div><small>{rightLabel}</small><Tooltip title={text(right)}><span>{right || '待选择'}</span></Tooltip></div>
    </div>
    <div className="canvas-semantic-merge"><span>{operation || '待配置'}</span><ArrowRightOutlined /></div>
    <Tooltip title={text(target)}><span className="canvas-semantic-dual-target">{target || '待设置'}</span></Tooltip>
  </div>
);

export const NodeBadges = ({ children }: { children: ReactNode }) => (
  <div className="canvas-semantic-badges">{children}</div>
);

export const NodeBadge = ({ children, tone = 'neutral' }: {
  children: ReactNode;
  tone?: 'neutral' | 'strong' | 'info' | 'spatial' | 'success' | 'warning';
}) => <Tooltip title={text(children)}><span className={`canvas-semantic-badge is-${tone}`}>{children}</span></Tooltip>;

export interface NodePreviewItem {
  key: string;
  label: ReactNode;
  value?: ReactNode;
  meta?: ReactNode;
}

export const NodePreviewList = ({
  items,
  total = items.length,
  empty = '尚未配置明细',
  limit = 2,
  moreLabel,
}: {
  items: readonly NodePreviewItem[];
  total?: number;
  empty?: ReactNode;
  limit?: number;
  moreLabel?: (remaining: number) => ReactNode;
}) => (
  <div className="canvas-semantic-preview-list">
    {items.length === 0 ? <div className="canvas-semantic-preview-empty">{empty}</div> : items.slice(0, limit).map((item) => (
      <div className="canvas-semantic-preview-row" key={item.key}>
        <Tooltip title={text(item.label)}><span className="canvas-semantic-preview-label">{item.label}</span></Tooltip>
        {item.value !== undefined && <span className="canvas-semantic-preview-value">{item.value}</span>}
        {item.meta !== undefined && <Tooltip title={text(item.meta)}><span className="canvas-semantic-preview-meta">{item.meta}</span></Tooltip>}
      </div>
    ))}
    {total > limit && (
      <div className="canvas-semantic-more">
        {moreLabel ? moreLabel(total - limit) : `另 ${total - limit} 条`}
      </div>
    )}
  </div>
);

interface NodeFieldCountProps {
  table: CanvasTableSchema | undefined;
  mappedCount?: number;
  mappedColumnNames?: readonly string[];
  unresolvedLabel?: string;
}

const NodeFieldCountReady = ({
  table,
  mappedCount,
  mappedColumnNames = [],
}: {
  table: CanvasTableSchema;
  mappedCount?: number;
  mappedColumnNames?: readonly string[];
}) => {
  const [open, setOpen] = useState(false);
  const countLabel = mappedCount === undefined
    ? `${table.columns.length} 字段`
    : `映射 ${mappedCount}/${table.columns.length}`;
  return (
    <>
      <button
        type="button"
        className="canvas-node-field-count"
        aria-label={`${table.name}：${countLabel}`}
        onClick={(event) => {
          event.stopPropagation();
          setOpen(true);
        }}
        onPointerDown={(event) => event.stopPropagation()}
        onDoubleClick={(event) => event.stopPropagation()}
      >
        <TableOutlined /> {countLabel}
      </button>
      <CanvasTableSchemaModal
        open={open}
        title={`${mappedCount === undefined ? '表结构' : '来源表结构'} · ${table.name}`}
        tables={[table]}
        initialTableName={table.name}
        mappedColumnNames={mappedColumnNames}
        mappedCount={mappedCount}
        onClose={() => setOpen(false)}
      />
    </>
  );
};

export const NodeFieldCount = ({
  table,
  mappedCount,
  mappedColumnNames = [],
  unresolvedLabel = '字段待解析',
}: NodeFieldCountProps) => {
  if (table) {
    return <NodeFieldCountReady
      table={table}
      mappedCount={mappedCount}
      mappedColumnNames={mappedColumnNames}
    />;
  }
  const countLabel = mappedCount === undefined
    ? unresolvedLabel
    : `映射 ${mappedCount}`;
  return (
    <button
      type="button"
      className="canvas-node-field-count"
      aria-label={`当前表：${countLabel}`}
      disabled
    >
      <TableOutlined /> {countLabel}
    </button>
  );
};

export const NodeOutputTarget = ({
  target,
  sourceTable,
  mappedCount,
  mappedColumnNames,
}: {
  target: ReactNode;
  sourceTable: CanvasTableSchema | undefined;
  mappedCount?: number;
  mappedColumnNames?: readonly string[];
}) => (
  <span className="canvas-node-output-target-summary">
    <span>{target}</span>
    <NodeFieldCount
      table={sourceTable}
      mappedCount={mappedCount}
      mappedColumnNames={mappedColumnNames}
    />
  </span>
);

export const NodeSplit = ({
  leftLabel,
  left,
  rightLabel,
  right,
}: {
  leftLabel: ReactNode;
  left: ReactNode;
  rightLabel: ReactNode;
  right: ReactNode;
}) => (
  <div className="canvas-semantic-split">
    <div><small>{leftLabel}</small><Tooltip title={text(left)}><span>{left}</span></Tooltip></div>
    <div><small>{rightLabel}</small><Tooltip title={text(right)}><span>{right}</span></Tooltip></div>
  </div>
);

export const NodeHeroMetric = ({ value, label }: { value: ReactNode; label: ReactNode }) => (
  <div className="canvas-semantic-hero"><strong>{value}</strong><span>{label}</span></div>
);
