import { ReloadOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import { Button, Empty, Pagination, Select, Space, Table, Tooltip, Typography } from 'antd';
import type { TableProps } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { CompactAlert, ContextHelp } from '../../../shared/components/ContextualFeedback';
import { ManagementDateTime, ManagementListCell, ManagementStatusIndicator } from '../../../shared/components/ManagementListCells';
import { useFileDatasetParseJobs } from '../hooks/useFileDatasets';
import { buildFileDatasetParseJobSearch, fileDatasetParseJobLoadModeLabels, fileDatasetParseJobStatusLabels, fileDatasetParseJobStatusOptions, fileDatasetParseJobTypeLabels, type FileDatasetParseJob, type FileDatasetParseJobStatus } from '../model/fileDatasetParseJob';
import { fileDatasetQueryKeys } from '../model/fileDatasetQueryKeys';
import './file-dataset-activity.css';

interface Props {
  datasetId: string;
  compact?: boolean;
  onViewAll?: () => void;
}

const tones = { QUEUED: 'warning', RUNNING: 'processing', SUCCEEDED: 'success', FAILED: 'error', CANCELLED: 'default' } as const;
const timestamp = (value: string | null) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';

export const FileDatasetParseHistoryPanel = ({ datasetId, compact = false, onViewAll }: Props) => {
  const [status, setStatus] = useState<FileDatasetParseJobStatus>();
  const [draftStatus, setDraftStatus] = useState<FileDatasetParseJobStatus>();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const queryClient = useQueryClient();
  const jobsQuery = useFileDatasetParseJobs({
    search: buildFileDatasetParseJobSearch({ fileDatasetId: datasetId, status: compact ? undefined : status }),
    page: compact ? 0 : page, size: compact ? 3 : size, sort: '-queuedAt,-id',
  }, Boolean(datasetId));
  const revision = jobsQuery.data?.content.map((job) => `${job.id}:${job.updatedAt}:${job.status}`).join('|');
  const previous = useRef<string | undefined>(undefined);
  useEffect(() => {
    if (revision === undefined) return;
    if (previous.current !== undefined && previous.current !== revision) {
      // A failed initial parse can remove its file/table. Refresh the resource counts as well.
      void queryClient.invalidateQueries({ queryKey: fileDatasetQueryKeys.dataset(datasetId) });
    }
    previous.current = revision;
  }, [datasetId, queryClient, revision]);

  const columns: TableProps<FileDatasetParseJob>['columns'] = [
    { title: '文件 / 数据表', key: 'source', width: '32%', render: (_, job) => <ManagementListCell
      primary={<Tooltip title={job.sourceFileName ?? job.sourceFileId}><span>{job.sourceFileName ?? '原文件已不可用'}</span></Tooltip>}
      secondary={job.tableName ?? (job.type === 'FILE_PREPARATION' ? '发现文件内容与数据表' : '数据表已移除')}
    /> },
    { title: '解析操作', key: 'operation', width: '18%', render: (_, job) => <ManagementListCell primary={fileDatasetParseJobTypeLabels[job.type]} secondary={job.loadMode ? fileDatasetParseJobLoadModeLabels[job.loadMode] : '文件准备'} /> },
    { title: '结果', key: 'result', width: '30%', render: (_, job) => <ManagementListCell
      primary={<ManagementStatusIndicator label={fileDatasetParseJobStatusLabels[job.status]} tone={tones[job.status]} />}
      secondary={job.errorMessage ? <Tooltip title={job.errorMessage}><span className="file-dataset-job-error">{job.errorMessage}</span></Tooltip> : job.status === 'QUEUED' && job.attemptCount > 0 ? '等待自动重试' : `已尝试 ${job.attemptCount} / ${job.maxAttempts} 次`}
    /> },
    { title: '提交时间', dataIndex: 'queuedAt', width: '20%', render: (value: string) => <ManagementDateTime value={value} /> },
  ];
  return <section className={`file-dataset-history ${compact ? 'file-dataset-history-compact' : ''}`} aria-label="当前数据集解析记录">
    {!compact && <div className="file-dataset-history-filter">
      <Select allowClear aria-label="筛选解析状态" placeholder="全部状态" options={fileDatasetParseJobStatusOptions} value={draftStatus} onChange={setDraftStatus} />
      <Space><Button type="primary" onClick={() => { setStatus(draftStatus); setPage(0); }}>查询</Button><Button onClick={() => { setDraftStatus(undefined); setStatus(undefined); setPage(0); }}>重置</Button></Space>
    </div>}
    <div className="file-dataset-history-toolbar">
      <div className="file-dataset-history-heading"><h2>{compact ? '最近解析' : '解析记录'}</h2>
        {!compact && <span className="file-dataset-activity-hint">共 {jobsQuery.data?.totalElements ?? 0} 条</span>}
        <ContextHelp ariaLabel="解析记录说明" content="仅显示当前数据集在系统保留期内的解析任务，每 5 秒自动刷新。展开记录可查看完整错误、执行时间和尝试次数；文件或空表因失败被清理后，任务记录仍可查看。尝试次数是同一任务的自动重试计数。" />
      </div>
      <Space wrap size={8}>
        {!compact && <Pagination size="small" simple current={page + 1} pageSize={size} total={jobsQuery.data?.totalElements ?? 0} showSizeChanger onChange={(next, nextSize) => { setPage(nextSize === size ? next - 1 : 0); setSize(nextSize); }} />}
        {compact && onViewAll && <Button type="link" onClick={onViewAll}>全部记录</Button>}
        <Tooltip title="刷新解析记录"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新解析记录" loading={jobsQuery.isFetching} onClick={() => void jobsQuery.refetch()} /></Tooltip>
      </Space>
    </div>
    {jobsQuery.isError && <CompactAlert type="error" showIcon message="解析记录加载失败" action={<Button size="small" onClick={() => void jobsQuery.refetch()}>重试</Button>} />}
    <Table<FileDatasetParseJob> size="small" rowKey="id" columns={columns} dataSource={jobsQuery.data?.content ?? []} loading={jobsQuery.isPending} pagination={false} tableLayout="fixed" scroll={{ x: 640 }}
      locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={status && !compact ? '没有符合此状态的解析记录' : '暂无解析记录，上传文件后可在此跟踪结果'} /> }}
      expandable={{ columnWidth: 32, expandedRowRender: (job) => <div className="file-dataset-job-details">
        {job.errorMessage && <div className="file-dataset-job-failure" role="status"><strong>{job.status === 'FAILED' ? '失败原因' : '最近一次执行信息'}</strong><p>{job.errorMessage}</p></div>}
        <dl>
          <div><dt>任务编号</dt><dd><Typography.Text copyable>{job.id}</Typography.Text></dd></div>
          <div><dt>开始时间</dt><dd>{timestamp(job.startedAt)}</dd></div>
          <div><dt>完成时间</dt><dd>{timestamp(job.completedAt)}</dd></div>
          <div><dt>执行尝试</dt><dd>{job.attemptCount} / {job.maxAttempts} 次</dd></div>
          <div><dt>来源</dt><dd>{job.sourceName ?? job.sourceKey ?? '—'}</dd></div>
          <div><dt>最早可执行时间</dt><dd>{timestamp(job.availableAt)}</dd></div>
          <div><dt>执行节点</dt><dd>{job.leaseOwner ?? '—'}</dd></div>
          <div><dt>最近心跳</dt><dd>{timestamp(job.lastHeartbeatAt)}</dd></div>
        </dl>
        {job.status === 'FAILED' && <p className="file-dataset-activity-hint">请根据失败原因检查文件与解析配置。初始解析失败可能清理临时文件和空表；已有数据的追加或覆盖失败会保留原数据。整文件替换仍遵循原有不可恢复规则。</p>}
      </div> }}
    />
  </section>;
};
