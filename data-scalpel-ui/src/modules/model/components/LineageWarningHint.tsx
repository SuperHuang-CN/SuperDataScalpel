import { InfoCircleOutlined, WarningOutlined } from '@ant-design/icons';
import { Button, Tooltip } from 'antd';

interface LineageWarningHintProps {
  warnings: string[] | undefined;
  truncated?: boolean;
}

export const LineageWarningHint = ({ warnings, truncated = false }: LineageWarningHintProps) => {
  if (!warnings?.length) return null;
  const label = truncated ? '血缘图已截断' : '血缘覆盖提示';
  return (
    <Tooltip
      placement="top"
      title={(
        <div className="model-lineage-warning-tooltip">
          <strong>{label}</strong>
          {warnings.map((warning) => <span key={warning}>{warning}</span>)}
        </div>
      )}
    >
      <Button
        type="text"
        size="small"
        className={`model-lineage-warning-trigger${truncated ? ' is-truncated' : ''}`}
        icon={truncated ? <WarningOutlined /> : <InfoCircleOutlined />}
        aria-label={`${label}：${warnings.join('；')}`}
      />
    </Tooltip>
  );
};
