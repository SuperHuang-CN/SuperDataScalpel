import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { FileTextOutlined, SettingOutlined, TableOutlined } from '@ant-design/icons';
import { Button, Tag } from 'antd';
import type { DescriptionsProps } from 'antd';
import { BusinessDetailDescriptions } from '../../../shared/components/BusinessDetailDescriptions';
import { BusinessDetailSection } from '../../../shared/components/BusinessDetailSection';
import {
  fileDatasetParseStatusLabels,
  fileDatasetTypeLabels,
  type FileDataset,
  type FileDatasetParseStatus,
  type FileDatasetTable,
} from '../model/fileDataset';
import { fileDatasetParsingOptionEntries } from '../model/fileDatasetDetail';

interface FileDatasetOverviewPanelProps {
  dataset: FileDataset;
  directoryName?: string;
  tables: FileDatasetTable[];
  tablesLoading: boolean;
  tablesError: boolean;
  onRetryTables: () => void;
}

const formatDateTime = (value: string) => new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'medium', timeStyle: 'medium', hour12: false,
}).format(new Date(value));

const parseStatusColors: Record<FileDatasetParseStatus, string> = {
  QUEUED: 'blue', PARSING: 'processing', SCHEMA_READY: 'warning', READY: 'success',
};

export const FileDatasetOverviewPanel = ({
  dataset,
  directoryName,
  tables,
  tablesLoading,
  tablesError,
  onRetryTables,
}: FileDatasetOverviewPanelProps) => {
  const statusCounts = tables.reduce<Record<FileDatasetParseStatus, number>>((counts, table) => {
    counts[table.parseStatus] += 1;
    return counts;
  }, { QUEUED: 0, PARSING: 0, SCHEMA_READY: 0, READY: 0 });
  const activeCount = statusCounts.QUEUED + statusCounts.PARSING;
  const basicItems: DescriptionsProps['items'] = [
    { key: 'type', label: '数据集类型', children: <Tag>{fileDatasetTypeLabels[dataset.type]}</Tag> },
    { key: 'directory', label: '所属目录', children: directoryName ?? (dataset.directoryId ? '目录已删除' : '未分类') },
    { key: 'files', label: '物理文件', children: `${dataset.fileCount} 个` },
    { key: 'tables', label: '逻辑表', children: `${dataset.tableCount} 张` },
    { key: 'createdAt', label: '创建时间', children: formatDateTime(dataset.createdAt) },
    { key: 'updatedAt', label: '更新时间', children: formatDateTime(dataset.updatedAt) },
    { key: 'description', label: '说明', span: 2, children: dataset.description || '—' },
  ];
  const parsingItems: DescriptionsProps['items'] = fileDatasetParsingOptionEntries(dataset.parsingOptions)
    .map((entry) => ({ key: entry.label, label: entry.label, children: entry.value }));

  return (
    <div className="file-dataset-detail-tab-panel file-dataset-overview-panel">
      {tablesError && (
        <Alert
          type="error"
          showIcon
          message="解析状态加载失败"
          description="基础信息仍可查看，但当前表状态统计可能不准确。"
          action={<Button size="small" onClick={onRetryTables}>重试</Button>}
        />
      )}
      {activeCount > 0 && (
        <Alert
          type="info"
          showIcon
          message={`后台解析进行中：排队 ${statusCounts.QUEUED} 张，解析中 ${statusCounts.PARSING} 张`}
          description="状态每 2 秒自动刷新，全部任务完成或失败后停止轮询。"
        />
      )}
      <BusinessDetailSection
        title="基础信息"
        description="数据集类型、规模与归属信息"
        icon={<FileTextOutlined />}
      >
        <BusinessDetailDescriptions column={{ xs: 1, md: 2, xl: 4 }} items={basicItems} />
      </BusinessDetailSection>
      <BusinessDetailSection
        title="共享解析参数"
        description="应用于当前数据集全部逻辑表的解析规则"
        icon={<SettingOutlined />}
      >
        <Alert
          type="info"
          showIcon
          message="同一数据集的所有逻辑表使用相同解析参数"
          description={dataset.parsingOptionsLocked
            ? '当前数据集已有文件、表或解析任务，解析参数已锁定；清空数据集后可再次修改。'
            : '上传文件时无需再次配置；开始上传后解析参数将锁定。'}
        />
        <BusinessDetailDescriptions column={{ xs: 1, md: 2, xl: 3 }} items={parsingItems} />
      </BusinessDetailSection>
      <BusinessDetailSection
        title="表解析状态"
        description="逻辑表解析任务的当前分布"
        icon={<TableOutlined />}
      >
        <div className="file-dataset-status-summary" aria-busy={tablesLoading}>
          {(Object.keys(fileDatasetParseStatusLabels) as FileDatasetParseStatus[]).map((status) => (
            <Tag key={status} color={parseStatusColors[status]}>
              {fileDatasetParseStatusLabels[status]} {statusCounts[status]}
            </Tag>
          ))}
        </div>
      </BusinessDetailSection>
    </div>
  );
};
