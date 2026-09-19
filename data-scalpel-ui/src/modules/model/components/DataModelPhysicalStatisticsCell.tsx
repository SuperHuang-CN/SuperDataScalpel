import { WarningOutlined } from '@ant-design/icons';
import { Space, Tooltip, Typography } from 'antd';
import { formatManagementDateTime } from '../../../shared/format/managementDateTime';
import type {
  DataModelPhysicalStatistics,
  PhysicalStatisticQuality,
} from '../model/dataModel';

const qualityLabels: Record<PhysicalStatisticQuality, string> = {
  EXACT: '精确值',
  ESTIMATED: '估算值',
  UNAVAILABLE: '不可获取',
};

const fullNumberFormatter = new Intl.NumberFormat('zh-CN');

const trimTrailingZeroes = (value: string) => (
  value.includes('.') ? value.replace(/0+$/, '').replace(/\.$/, '') : value
);

const compactDecimal = (value: number) => trimTrailingZeroes(
  value >= 100 ? value.toFixed(0) : value >= 10 ? value.toFixed(1) : value.toFixed(2),
);

const formatCompactRowCount = (value: number) => {
  if (value >= 100_000_000) return `${compactDecimal(value / 100_000_000)}亿`;
  if (value >= 10_000) return `${compactDecimal(value / 10_000)}万`;
  return fullNumberFormatter.format(value);
};

const formatStorage = (value: number) => {
  const units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];
  let amount = value;
  let unitIndex = 0;
  while (amount >= 1024 && unitIndex < units.length - 1) {
    amount /= 1024;
    unitIndex += 1;
  }
  const digits = unitIndex === 0 ? 0 : amount >= 100 ? 0 : amount >= 10 ? 1 : 2;
  return `${trimTrailingZeroes(amount.toFixed(digits))} ${units[unitIndex]}`;
};

const formatCompactDateTime = (value: string | null) => {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
};

const metricText = (
  label: string,
  value: number | null,
  quality: PhysicalStatisticQuality,
  formatter: (metric: number) => string,
) => `${label} ${value === null ? '—' : `${quality === 'ESTIMATED' ? '≈' : ''}${formatter(value)}`}`;

const statusText = (statistics: DataModelPhysicalStatistics) => {
  switch (statistics.lastRefreshStatus) {
    case 'FAILED':
      return '最近刷新失败';
    case 'PARTIAL':
      return '仅获取到部分统计';
    case 'NOT_FOUND':
      return '物理表未创建/不存在';
    case 'UNSUPPORTED':
      return '当前物理对象不可获取';
    case 'SUCCESS':
      return `采集于 ${formatCompactDateTime(statistics.collectedAt)}`;
  }
};

export interface DataModelPhysicalStatisticsCellProps {
  statistics: DataModelPhysicalStatistics | null | undefined;
}

export const DataModelPhysicalStatisticsCell = ({
  statistics,
}: DataModelPhysicalStatisticsCellProps) => {
  if (!statistics) {
    return <Typography.Text type="secondary">尚未统计</Typography.Text>;
  }

  const rowText = metricText(
    '行数', statistics.rowCount, statistics.rowCountQuality, formatCompactRowCount,
  );
  const storageText = metricText(
    '占用', statistics.storageBytes, statistics.storageQuality, formatStorage,
  );
  const failed = statistics.lastRefreshStatus === 'FAILED';
  const unavailable = statistics.lastRefreshStatus === 'NOT_FOUND'
    || statistics.lastRefreshStatus === 'UNSUPPORTED';
  const detail = (
    <Space direction="vertical" size={2}>
      <span>行数：{statistics.rowCount === null ? '不可获取' : fullNumberFormatter.format(statistics.rowCount)}（{qualityLabels[statistics.rowCountQuality]}）</span>
      <span>占用空间：{statistics.storageBytes === null ? '不可获取' : `${fullNumberFormatter.format(statistics.storageBytes)} 字节`}（{qualityLabels[statistics.storageQuality]}）</span>
      <span>最近成功采集：{formatManagementDateTime(statistics.collectedAt)}</span>
      <span>最近刷新：{formatManagementDateTime(statistics.lastRefreshAt)}</span>
      {statistics.message && <span>{statistics.message}</span>}
    </Space>
  );

  return (
    <Tooltip title={detail} placement="topRight">
      <Space direction="vertical" size={0} style={{ lineHeight: 1.45, cursor: 'help', alignItems: 'flex-end' }}>
        {!unavailable && <Typography.Text>{rowText}</Typography.Text>}
        {!unavailable && <Typography.Text type="secondary">{storageText}</Typography.Text>}
        <Typography.Text type={failed || unavailable ? 'warning' : 'secondary'}>
          {failed && <WarningOutlined style={{ marginRight: 4 }} />}
          {statusText(statistics)}
        </Typography.Text>
      </Space>
    </Tooltip>
  );
};
