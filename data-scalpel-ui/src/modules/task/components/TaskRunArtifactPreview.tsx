import { writeClipboardText } from '../../../shared/browser/writeClipboardText';
import {
  ArrowLeftOutlined,
  CopyOutlined,
  DownloadOutlined,
  ReloadOutlined,
  SearchOutlined,
  VerticalAlignBottomOutlined,
  VerticalAlignTopOutlined,
} from '@ant-design/icons';
import { Button, Empty, Space, Spin, Tooltip, Typography, message } from 'antd';
import { useMemo, useRef, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { MonacoSqlEditor, type MonacoSqlEditorHandle } from '../../../shared/components/MonacoSqlEditor';
import { useTaskRunArtifactPreview } from '../hooks/useTasks';
import type { TaskRunArtifactKind, TaskRunArtifactMetadata } from '../model/task';

interface TaskRunArtifactPreviewProps {
  runId: string;
  artifact: TaskRunArtifactMetadata;
  artifacts: { log: TaskRunArtifactMetadata; result: TaskRunArtifactMetadata };
  onBack: () => void;
  onSelect: (kind: TaskRunArtifactKind) => void;
  onDownload: (kind: TaskRunArtifactKind) => void;
  downloadLoading: boolean;
}

const formatBytes = (value: number): string => {
  if (value >= 1024 * 1024) return `${(value / 1024 / 1024).toFixed(2)} MiB`;
  if (value >= 1024) return `${(value / 1024).toFixed(2)} KiB`;
  return `${value} B`;
};

const artifactTitle = (kind: TaskRunArtifactKind) => kind === 'log' ? '控制台日志' : '执行结果';

export const TaskRunArtifactPreview = ({
  runId,
  artifact,
  artifacts,
  onBack,
  onSelect,
  onDownload,
  downloadLoading,
}: TaskRunArtifactPreviewProps) => {
  const [messageApi, messageContext] = message.useMessage();
  const [wordWrap, setWordWrap] = useState(false);
  const editorRef = useRef<MonacoSqlEditorHandle>(null);
  const previewQuery = useTaskRunArtifactPreview(runId, artifact.kind, true);
  const display = useMemo(() => {
    const raw = previewQuery.data ?? '';
    if (artifact.kind !== 'result') return { content: raw, isJson: false };
    try {
      return { content: JSON.stringify(JSON.parse(raw), null, 2), isJson: true };
    } catch {
      return { content: raw, isJson: false };
    }
  }, [artifact.kind, previewQuery.data]);

  const copy = async () => {
    try {
      await writeClipboardText(display.content);
      messageApi.success('已复制全部内容');
    } catch {
      messageApi.error('复制失败，请手动选择内容复制');
    }
  };

  return (
    <div className="task-run-artifact-preview">
      {messageContext}
      <div className="task-run-artifact-preview-header">
        <Space wrap>
          <Button type="text" icon={<ArrowLeftOutlined />} onClick={onBack}>返回运行详情</Button>
          <Space.Compact>
            {(['log', 'result'] as const).map((kind) => (
              <Button
                key={kind}
                size="small"
                type={artifact.kind === kind ? 'primary' : 'default'}
                disabled={!artifacts[kind].previewAvailable}
                onClick={() => onSelect(kind)}
              >
                {artifactTitle(kind)}
              </Button>
            ))}
          </Space.Compact>
          <Typography.Text strong>{artifactTitle(artifact.kind)}</Typography.Text>
          <Typography.Text type="secondary">{artifact.fileName}</Typography.Text>
          <Typography.Text type="secondary">
            {artifact.sizeBytes == null ? '大小未知' : formatBytes(artifact.sizeBytes)}
          </Typography.Text>
        </Space>
        <Space wrap>
          <Tooltip title="搜索">
            <Button icon={<SearchOutlined />} aria-label="搜索制品内容" onClick={() => editorRef.current?.openFind()} />
          </Tooltip>
          <Tooltip title="复制全文">
            <Button icon={<CopyOutlined />} aria-label="复制制品全文" onClick={() => void copy()} />
          </Tooltip>
          <Tooltip title={wordWrap ? '关闭自动换行' : '开启自动换行'}>
            <Button aria-label={wordWrap ? '关闭自动换行' : '开启自动换行'} onClick={() => setWordWrap((value) => !value)}>
              自动换行
            </Button>
          </Tooltip>
          <Tooltip title="重新加载">
            <Button
              icon={<ReloadOutlined />}
              aria-label="重新加载制品预览"
              loading={previewQuery.isFetching}
              onClick={() => void previewQuery.refetch()}
            />
          </Tooltip>
          <Button
            type="primary"
            icon={<DownloadOutlined />}
            loading={downloadLoading}
            onClick={() => onDownload(artifact.kind)}
          >
            下载
          </Button>
        </Space>
      </div>

      {artifact.kind === 'log' && !previewQuery.isPending && !previewQuery.isError && (
        <Space className="task-run-artifact-preview-navigation" size="small">
          <Button size="small" icon={<VerticalAlignTopOutlined />} onClick={() => editorRef.current?.scrollToStart()}>开头</Button>
          <Button size="small" icon={<VerticalAlignBottomOutlined />} onClick={() => editorRef.current?.scrollToEnd()}>末尾</Button>
        </Space>
      )}

      {previewQuery.isPending ? (
        <div className="task-run-artifact-preview-loading"><Spin tip="正在加载预览…" /></div>
      ) : previewQuery.isError ? (
        <div className="task-run-artifact-preview-empty">
          <Empty description={previewQuery.error instanceof ApiError ? previewQuery.error.message : '制品预览加载失败'}>
            <Button onClick={() => void previewQuery.refetch()}>重试</Button>
          </Empty>
        </div>
      ) : (
        <MonacoSqlEditor
          ref={editorRef}
          className="task-run-artifact-preview-editor"
          value={display.content}
          readOnly
          language={display.isJson ? 'json' : 'plaintext'}
          wordWrap={wordWrap}
          revealAtEnd={artifact.kind === 'log'}
          height="100%"
          onChange={() => undefined}
        />
      )}
    </div>
  );
};
