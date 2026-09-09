import {
  ArrowLeftOutlined,
  CopyOutlined,
  DownloadOutlined,
  InfoCircleOutlined,
  ReloadOutlined,
  SearchOutlined,
  VerticalAlignBottomOutlined,
  VerticalAlignTopOutlined,
} from '@ant-design/icons';
import { Button, Empty, Space, Spin, Switch, Tag, Tooltip, Typography, message } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import { MonacoSqlEditor, type MonacoSqlEditorHandle } from '../../../shared/components/MonacoSqlEditor';
import { useDownloadTaskRunArtifact, useTaskRunLogs } from '../hooks/useTasks';

interface TaskRunLogViewerProps {
  runId: string;
  visible: boolean;
  endedAt?: string | null;
  onBack?: () => void;
  onShowResult?: () => void;
}

const formatBytes = (value: number): string => {
  if (value >= 1024 * 1024) return `${(value / 1024 / 1024).toFixed(2)} MiB`;
  if (value >= 1024) return `${(value / 1024).toFixed(2)} KiB`;
  return `${value} B`;
};

const statusPresentation = {
  WAITING: { color: 'default', label: '等待日志' },
  LIVE: { color: 'processing', label: '运行中' },
  ARCHIVING: { color: 'gold', label: '日志归档中' },
  FINAL: { color: 'success', label: '最终日志' },
  UNAVAILABLE: { color: 'warning', label: '暂不可用' },
} as const;

export const TaskRunLogViewer = ({
  runId,
  visible,
  endedAt,
  onBack,
  onShowResult,
}: TaskRunLogViewerProps) => {
  const [messageApi, messageContext] = message.useMessage();
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [wordWrap, setWordWrap] = useState(false);
  const [followEnd, setFollowEnd] = useState(true);
  const [pageVisible, setPageVisible] = useState(() => document.visibilityState === 'visible');
  const [archiveWaitExpired, setArchiveWaitExpired] = useState(false);
  const editorRef = useRef<MonacoSqlEditorHandle>(null);
  const queryEnabled = visible && pageVisible;
  const logQuery = useTaskRunLogs(runId, queryEnabled,
    queryEnabled && autoRefresh && !archiveWaitExpired);
  const downloadMutation = useDownloadTaskRunArtifact();
  const log = logQuery.data;
  const content = log?.content ?? '';

  useEffect(() => {
    const handleVisibility = () => setPageVisible(document.visibilityState === 'visible');
    document.addEventListener('visibilitychange', handleVisibility);
    return () => document.removeEventListener('visibilitychange', handleVisibility);
  }, []);

  useEffect(() => {
    if (!endedAt) return undefined;
    const remaining = Math.max(0, 60_000 - (Date.now() - new Date(endedAt).getTime()));
    const timer = window.setTimeout(() => setArchiveWaitExpired(true), remaining);
    return () => window.clearTimeout(timer);
  }, [endedAt]);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(content);
      messageApi.success('已复制当前日志窗口');
    } catch {
      messageApi.error('复制失败，请手动选择内容复制');
    }
  };

  const download = async () => {
    try {
      const blob = await downloadMutation.mutateAsync({ runId, kind: 'log' });
      downloadBlob(blob, `task-run-${runId}-console.log`);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '下载执行日志失败');
    }
  };

  const presentation = statusPresentation[log?.status ?? 'WAITING'];
  const waitingWithoutContent = !content && !logQuery.isError;
  const emptyMessage = log?.message ?? (log?.status === 'LIVE'
    ? '任务运行中，暂时没有新的日志'
    : '等待任务启动并产生日志');

  return (
    <section className="task-run-live-log-viewer">
      {messageContext}
      <header className="task-run-live-log-header">
        <Space wrap>
          {onBack && <Button type="text" icon={<ArrowLeftOutlined />} onClick={onBack}>返回运行详情</Button>}
          <Typography.Text strong>控制台日志</Typography.Text>
          <Tag color={presentation.color}>{presentation.label}</Tag>
          {log?.windowSizeBytes != null && <Typography.Text type="secondary">当前窗口 {formatBytes(log.windowSizeBytes)}</Typography.Text>}
          {log?.finalSizeBytes != null && <Typography.Text type="secondary">最终文件 {formatBytes(log.finalSizeBytes)}</Typography.Text>}
          {log?.truncated && (
            <Tooltip title="仅显示最近 2,000 行、最多 1 MiB">
              <InfoCircleOutlined className="task-run-live-log-info" />
            </Tooltip>
          )}
        </Space>
        <Space wrap>
          <Space size={6}>
            <Switch size="small" checked={autoRefresh && !archiveWaitExpired}
              disabled={log?.status === 'FINAL' || archiveWaitExpired} onChange={setAutoRefresh} />
            <Typography.Text type="secondary">
              {log?.status === 'FINAL' ? '已停止刷新'
                : archiveWaitExpired ? '已停止自动等待，可手动刷新'
                  : autoRefresh ? '每 3 秒刷新' : '已暂停'}
            </Typography.Text>
          </Space>
          <Tooltip title="搜索"><Button icon={<SearchOutlined />} onClick={() => editorRef.current?.openFind()} /></Tooltip>
          <Tooltip title="复制当前窗口"><Button icon={<CopyOutlined />} disabled={!content} onClick={() => void copy()} /></Tooltip>
          <Button onClick={() => setWordWrap((value) => !value)}>{wordWrap ? '取消换行' : '自动换行'}</Button>
          <Tooltip title="跳到开头"><Button icon={<VerticalAlignTopOutlined />} onClick={() => editorRef.current?.scrollToStart()} /></Tooltip>
          <Tooltip title="跳到末尾"><Button icon={<VerticalAlignBottomOutlined />} onClick={() => {
            setFollowEnd(true);
            editorRef.current?.scrollToEnd();
          }} /></Tooltip>
          <Tooltip title="立即刷新"><Button icon={<ReloadOutlined />} loading={logQuery.isFetching} onClick={() => void logQuery.refetch()} /></Tooltip>
          {onShowResult && <Button onClick={onShowResult}>执行结果</Button>}
          <Button icon={<DownloadOutlined />} disabled={!log?.finalDownloadAvailable}
            loading={downloadMutation.isPending} onClick={() => void download()}>下载</Button>
        </Space>
      </header>

      {logQuery.isPending && !content ? (
        <div className="task-run-live-log-state"><Spin tip="正在连接运行日志…" /></div>
      ) : logQuery.isError && !content ? (
        <div className="task-run-live-log-state">
          <Empty description={logQuery.error instanceof ApiError ? logQuery.error.message : '运行日志读取失败'}>
            <Button onClick={() => void logQuery.refetch()}>重试</Button>
          </Empty>
        </div>
      ) : waitingWithoutContent ? (
        <div className="task-run-live-log-state">
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyMessage} />
        </div>
      ) : (
        <div className="task-run-live-log-editor-wrap">
          {(logQuery.isError || log?.message) && (
            <div className="task-run-live-log-notice">
              {logQuery.isError
                ? `刷新失败，继续显示上次日志：${logQuery.error instanceof ApiError ? logQuery.error.message : '请稍后重试'}`
                : log?.message}
            </div>
          )}
          <MonacoSqlEditor
            ref={editorRef}
            value={content}
            readOnly
            language="plaintext"
            wordWrap={wordWrap}
            revealAtEnd={followEnd}
            onAtEndChange={setFollowEnd}
            height="100%"
            onChange={() => undefined}
          />
        </div>
      )}
    </section>
  );
};
