import { Alert, Button, Descriptions, Tag } from 'antd';
import type { DescriptionsProps } from 'antd';
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
      <section className="file-dataset-detail-section">
        <div className="file-dataset-detail-section-title">基础信息</div>
        <Descriptions size="small" bordered column={2} items={basicItems} />
      </section>
      <section className="file-dataset-detail-section">
        <div className="file-dataset-detail-section-title">共享解析参数</div>
        <Alert
          type="info"
          showIcon
          message="同一数据集的所有逻辑表使用相同解析参数"
          description={dataset.parsingOptionsLocked
            ? '当前数据集已有文件、表或解析任务，解析参数已锁定；清空数据集后可再次修改。'
            : '上传文件时无需再次配置；开始上传后解析参数将锁定。'}
        />
        <Descriptions size="small" bordered column={2} items={parsingItems} />
      </section>
      <section className="file-dataset-detail-section">
        <div className="file-dataset-detail-section-title">表解析状态</div>
        <div className="file-dataset-status-summary" aria-busy={tablesLoading}>
          {(Object.keys(fileDatasetParseStatusLabels) as FileDatasetParseStatus[]).map((status) => (
            <Tag key={status} color={parseStatusColors[status]}>
              {fileDatasetParseStatusLabels[status]} {statusCounts[status]}
            </Tag>
          ))}
        </div>
      </section>
    </div>
  );
};
