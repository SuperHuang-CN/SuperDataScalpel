import {
  DownloadOutlined,
  CodeOutlined,
  EditOutlined,
  EyeOutlined,
  ExperimentOutlined,
  FileZipOutlined,
  SettingOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import {
  Button,
  Collapse,
  Descriptions,
  Drawer,
  Modal,
  Progress,
  Radio,
  Space,
  Tag,
  Tabs,
  Typography,
  Upload,
  type UploadFile,
  type UploadProps,
} from 'antd';
import { useState, type ReactNode } from 'react';
import type {
  SparkJarDevelopmentKitArtifact,
  SparkJarDevelopmentKitGeneration,
  SparkJarTaskDefinition,
  SparkJarOnlineSource,
} from '../model/task';
import { formatSparkJarBytes, sparkJarKitStageLabels } from './sparkJarDefinitionWorkspaceModel';
import { ContextHelp } from '../../../shared/components/ContextualFeedback';
import { formatManagementDateTime } from '../../../shared/format/managementDateTime';

export const SparkJarOnlineCodeSummary = ({ source, failed, loading, opening, onRetry, onOpen }: {
  source?: SparkJarOnlineSource;
  failed: boolean;
  loading: boolean;
  opening: boolean;
  onRetry: () => void;
  onOpen: () => void;
}) => {
  const jar = source?.currentJar;
  const uploaded = source?.currentJarOrigin === 'UPLOADED';
  const pending = source?.hasUncompiledChanges;
  const status = !jar ? '尚未应用' : uploaded ? '当前使用上传的运行包' : pending ? '有修改未应用' : '代码已应用到任务';
  const hint = !jar ? '尚未生成任务运行包，请进入编辑器编写并应用代码。'
    : uploaded ? '在线源码尚未应用；当前任务运行包来自上传 JAR。'
      : pending ? '源码有新修改，尚未应用；任务运行包仍为上次应用的版本。'
        : '已保存源码与当前任务运行包一致；应用代码不等于发布任务。';
  return <section className="spark-jar-online-entry" aria-label="在线代码">
    <div className="spark-jar-online-summary">
      <Space wrap><Typography.Text strong>在线代码</Typography.Text>
        {!failed && !loading && source && <Tag color={!jar || uploaded || pending ? 'warning' : 'success'}>{status}</Tag>}
      </Space>
      {failed ? <div className="spark-jar-section-hint" role="alert">代码状态加载失败
        <Button type="link" size="small" onClick={onRetry}>重试</Button></div>
        : loading || !source ? <div className="spark-jar-section-hint">正在读取代码状态…</div>
          : <>
            <div className="spark-jar-section-hint">{hint}</div>
            {jar && <div className="spark-jar-online-summary-meta">
              <span>当前运行包入口类：<span className="spark-jar-code-reference">{jar.jobClass}</span></span>
              {!uploaded && <span>最近应用时间：{formatManagementDateTime(source.appliedAt)}</span>}
            </div>}
          </>}
    </div>
    <Button type="primary" loading={opening} onClick={onOpen}>进入编辑器</Button>
  </section>;
};

export const SparkJarAuthoringModeChoice = ({ onConfirm }: { onConfirm: (mode: 'ONLINE' | 'UPLOAD') => void }) => {
  const [selected, setSelected] = useState<'ONLINE' | 'UPLOAD' | null>(null);
  return <section className="spark-jar-mode-choice">
    <Typography.Title level={4}>选择开发方式</Typography.Title>
    <Typography.Paragraph type="secondary">两种方式共用任务资源和运行设置，保存后直接进入已选方式。</Typography.Paragraph>
    <Radio.Group className="spark-jar-mode-options" value={selected} onChange={(event) => setSelected(event.target.value)}>
      <Radio value="ONLINE" className="spark-jar-mode-option">
        <span className="spark-jar-mode-icon"><CodeOutlined /></span>
        <strong>在线开发</strong><span>在浏览器中编写 Java</span><small>代码提示 · 检查 · 试运行</small>
      </Radio>
      <Radio value="UPLOAD" className="spark-jar-mode-option">
        <span className="spark-jar-mode-icon"><FileZipOutlined /></span>
        <strong>上传 JAR</strong><span>在本地 IDE 开发并打包</span><small>生成开发工程 · 上传作业包</small>
      </Radio>
    </Radio.Group>
    <div className="spark-jar-mode-confirm"><Typography.Text type="secondary">后续可通过“更换开发方式”调整。</Typography.Text>
      <Button type="primary" disabled={!selected} onClick={() => selected && onConfirm(selected)}>确认并配置</Button></div>
  </section>;
};

interface SparkJarArtifactSummaryProps {
  definition: SparkJarTaskDefinition;
  uploadFiles: UploadFile[];
  uploading: boolean;
  beforeUpload: NonNullable<UploadProps['beforeUpload']>;
  onClearSelection: () => void;
  onUpload: () => Promise<void>;
}

export const SparkJarArtifactSummary = ({
  definition,
  uploadFiles,
  uploading,
  beforeUpload,
  onClearSelection,
  onUpload,
}: SparkJarArtifactSummaryProps) => {
  const [detailOpen, setDetailOpen] = useState(false);
  const pendingFile = uploadFiles[0];
  const jar = definition.jar;

  return (
    <section className="spark-jar-artifact-summary">
      <div className="spark-jar-artifact-heading"><Typography.Text strong>上传 JAR</Typography.Text>
        <ContextHelp ariaLabel="JAR 上传说明" content="已有作业包可直接上传，无需先下载工程。作业包需符合平台 SDK 和 Manifest 约定；上传只读取 Manifest 和类条目，不执行用户代码。" /></div>
      <div className="spark-jar-artifact-icon" aria-hidden="true"><FileZipOutlined /></div>
      <div className="spark-jar-artifact-identity">
        <Space size={8} wrap>
          <Typography.Text strong>{pendingFile?.name ?? jar?.fileName ?? '尚未选择 JAR 文件'}</Typography.Text>
          <Tag color={pendingFile ? 'warning' : jar ? 'success' : 'default'}>
            {pendingFile ? '待上传' : jar ? '已上传' : '未上传'}
          </Tag>
        </Space>
        {pendingFile ? (
          <Typography.Text type="secondary">
            {typeof pendingFile.size === 'number' ? formatSparkJarBytes(pendingFile.size) : '等待上传'}
          </Typography.Text>
        ) : jar ? (
          <div className="spark-jar-artifact-meta">
            <span>Job Class：<Typography.Text code>{jar.jobClass}</Typography.Text></span>
            <span>{formatSparkJarBytes(jar.sizeBytes)}</span>
            <span>API v{jar.jobApiVersion}</span>
            <span>{definition.jobMode}</span>
            <span>定义 v{definition.definitionVersion}</span>
          </div>
        ) : (
          <Typography.Text type="secondary">选择本地编译好的作业包。</Typography.Text>
        )}
      </div>
      <Space className="spark-jar-artifact-actions" wrap>
        {pendingFile ? (
          <>
            <Button onClick={onClearSelection}>取消选择</Button>
            <Button type="primary" icon={<UploadOutlined />} loading={uploading} onClick={() => jar ? Modal.confirm({
              title: '替换当前 JAR？', content: `将用“${pendingFile.name}”替换“${jar.fileName}”。在线源码保留，上传失败仍保留当前 JAR。`,
              okText: '确认替换', cancelText: '取消', onOk: onUpload,
            }) : onUpload()}>
              {jar ? '覆盖当前 JAR' : '上传 JAR'}
            </Button>
          </>
        ) : (
          <>
            {jar && (
              <Button icon={<EyeOutlined />} onClick={() => setDetailOpen(true)}>查看详情</Button>
            )}
            <Upload
              accept=".jar,application/java-archive,application/zip"
              maxCount={1}
              fileList={uploadFiles}
              showUploadList={false}
              beforeUpload={beforeUpload}
            >
              <Button icon={<UploadOutlined />}>{jar ? '选择替换文件' : '选择 JAR 文件'}</Button>
            </Upload>
          </>
        )}
      </Space>

      <Modal
        rootClassName="business-overlay business-modal-overlay"
        width={680}
        open={detailOpen}
        title="用户作业 JAR 详情"
        footer={<Button onClick={() => setDetailOpen(false)}>关闭</Button>}
        onCancel={() => setDetailOpen(false)}
      >
        {jar && (
          <Descriptions size="small" column={1}>
            <Descriptions.Item label="文件名">{jar.fileName}</Descriptions.Item>
            <Descriptions.Item label="大小">{formatSparkJarBytes(jar.sizeBytes)}</Descriptions.Item>
            <Descriptions.Item label="Job Class"><Typography.Text code copyable>{jar.jobClass}</Typography.Text></Descriptions.Item>
            <Descriptions.Item label="Job API">v{jar.jobApiVersion}</Descriptions.Item>
            <Descriptions.Item label="作业模式">{definition.jobMode}</Descriptions.Item>
            <Descriptions.Item label="定义版本">v{definition.definitionVersion}</Descriptions.Item>
            <Descriptions.Item label="SHA-256"><Typography.Text code copyable>{jar.sha256}</Typography.Text></Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </section>
  );
};

interface SparkJarDevelopmentKitPanelProps {
  status: { label: string; color: string };
  generation: SparkJarDevelopmentKitGeneration | null | undefined;
  artifact: SparkJarDevelopmentKitArtifact | null | undefined;
  configurationDirty: boolean;
  inputModelCount: number;
  jdbcTableCount: number;
  outputModelCount: number;
  running: boolean;
  submitting: boolean;
  disabled?: boolean;
  downloading: boolean;
  onGenerate: () => void;
  onDownload: () => void;
}

export const SparkJarDevelopmentKitPanel = ({
  status,
  generation,
  artifact,
  configurationDirty,
  inputModelCount,
  jdbcTableCount,
  outputModelCount,
  running,
  submitting,
  disabled = false,
  downloading,
  onGenerate,
  onDownload,
}: SparkJarDevelopmentKitPanelProps) => {
  const usesPreviousArtifact = Boolean(artifact && (configurationDirty || !artifact.matchesSavedConfiguration));

  return (
    <aside className="spark-jar-development-kit-panel">
      <div className="spark-jar-development-kit-heading">
        <div>
          <Space><Typography.Text strong>开发工程</Typography.Text><ContextHelp ariaLabel="开发工程说明" content="根据已绑定资源生成 Maven 工程，包含读取代码、输出示例、Schema 和配置的 Parquet 样例。未绑定资源时生成基础工程。下载后在本地 IDE 完善处理逻辑并打包上传；生成工程不执行正式任务。" /></Space>
          <Typography.Text type="secondary">根据任务资源生成，下载后在本地开发。</Typography.Text>
        </div>
        <Tag color={status.color}>{status.label}</Tag>
      </div>

      <div className="spark-jar-development-kit-content">
        <div className="spark-jar-development-kit-counts">
          <div><strong>{inputModelCount}</strong><span>输入模型</span></div>
          <div><strong>{jdbcTableCount}</strong><span>JDBC 表</span></div>
          <div><strong>{outputModelCount}</strong><span>输出模型</span></div>
        </div>

        <div className="spark-jar-development-kit-state">
          {submitting ? (
            <>
              <Typography.Text>正在提交生成任务</Typography.Text>
              <Progress percent={0} size="small" status="active" showInfo={false} />
            </>
          ) : running ? (
            <>
              <Typography.Text>
                {sparkJarKitStageLabels[generation?.stage ?? 'QUEUED']}
                {generation?.currentModel ? ` · ${generation.currentModel}` : ''}
              </Typography.Text>
              <Progress percent={generation?.progressPercent ?? 0} size="small" />
            </>
          ) : generation?.status === 'FAILED' ? (
            <>
              <Typography.Text type="danger">最近一次生成失败</Typography.Text>
              <Typography.Paragraph type="secondary" ellipsis={{ rows: 3, expandable: true, symbol: '展开' }}>
                {generation.errorMessage ?? '请检查配置后重新生成。'}
              </Typography.Paragraph>
            </>
          ) : artifact ? (
            <>
              <Typography.Text>{formatSparkJarBytes(artifact.sizeBytes)}</Typography.Text>
              <Typography.Text type="secondary">生成于 {new Date(artifact.generatedAt).toLocaleString()}</Typography.Text>
              {usesPreviousArtifact && <Typography.Text type="warning">当前为上一次成功生成的开发包</Typography.Text>}
            </>
          ) : (
            <Typography.Text type="secondary">样例范围可在上方资源列表中调整。</Typography.Text>
          )}
        </div>

        <div className="spark-jar-development-kit-actions">
          <Button
            type={artifact ? 'default' : 'primary'}
            icon={<ExperimentOutlined />}
            disabled={disabled || running || submitting}
            onClick={onGenerate}
          >
            {artifact ? '重新生成' : '生成开发工程'}
          </Button>
          {artifact && (
            <Button type="primary" icon={<DownloadOutlined />} loading={downloading} onClick={onDownload}>
              下载开发工程
            </Button>
          )}
        </div>
      </div>
    </aside>
  );
};

export interface SparkJarRuntimeConfigurationItem {
  key: string;
  label: string;
  summary: string;
  children: ReactNode;
  placement?: 'primary' | 'advanced';
}

interface SparkJarRuntimeConfigurationProps {
  items: SparkJarRuntimeConfigurationItem[];
  activeKeys: string[];
  onChange: (keys: string[]) => void;
  overview?: {
    environment: string;
    resources: string;
    timeout: string;
    timeoutLabel?: string;
  };
}

export const SparkJarRuntimeConfiguration = ({
  items,
  activeKeys,
  onChange,
  overview,
}: SparkJarRuntimeConfigurationProps) => {
  if (!overview) {
    return (
      <section className="spark-jar-definition-section spark-jar-runtime-configuration">
        <div className="spark-jar-definition-section-title">
          <span>运行配置</span>
          <Typography.Text type="secondary">按需展开编辑</Typography.Text>
        </div>
        <Collapse
          size="small"
          activeKey={activeKeys}
          onChange={(keys) => onChange(Array.isArray(keys) ? keys.map(String) : [String(keys)])}
          items={items.map((item) => ({
            key: item.key,
            label: (
              <div className="spark-jar-runtime-collapse-label">
                <Typography.Text strong>{item.label}</Typography.Text>
                <Typography.Text type="secondary">{item.summary}</Typography.Text>
              </div>
            ),
            children: item.children,
          }))}
        />
      </section>
    );
  }

  const primaryItems = items.filter((item) => item.placement === 'primary');
  const advancedItems = items.filter((item) => item.placement !== 'primary');
  const primaryKeys = new Set(primaryItems.map((item) => item.key));
  const advancedKeys = new Set(advancedItems.map((item) => item.key));
  const primaryOpen = activeKeys.some((key) => primaryKeys.has(key));
  const activeAdvanced = advancedItems.find((item) => activeKeys.includes(item.key));

  const togglePrimary = () => {
    const remaining = activeKeys.filter((key) => !primaryKeys.has(key));
    onChange(primaryOpen ? remaining : [...remaining, ...primaryItems.map((item) => item.key)]);
  };
  const openAdvanced = (key: string) => {
    onChange([...activeKeys.filter((itemKey) => !advancedKeys.has(itemKey)), key]);
  };
  const closeAdvanced = () => {
    onChange(activeKeys.filter((key) => !advancedKeys.has(key)));
  };

  return (
    <section className="spark-jar-definition-section spark-jar-runtime-configuration spark-jar-runtime-overview">
      <div className="spark-jar-runtime-overview-heading">
        <div>
          <Typography.Text strong>运行设置</Typography.Text>
        </div>
        <Button type="text" size="small" icon={<EditOutlined />} onClick={togglePrimary}>
          {primaryOpen ? '收起' : '调整'}
        </Button>
      </div>

      <div className="spark-jar-runtime-sentence">
        <Tag bordered={false}>{overview.environment}</Tag>
        <strong>{overview.resources}</strong>
        <span className="spark-jar-runtime-sentence-separator" aria-hidden="true">·</span>
        <span>{overview.timeoutLabel ?? '最长运行'}</span>
        <strong>{overview.timeout}</strong>
      </div>

      <div
        className="spark-jar-runtime-primary-editor"
        hidden={!primaryOpen}
      >
          {primaryItems.map((item) => (
            <div key={item.key} className={`spark-jar-runtime-primary-item spark-jar-runtime-primary-item-${item.key}`}>
              {item.children}
            </div>
          ))}
      </div>

      {advancedItems.length > 0 && (
        <div className="spark-jar-runtime-advanced-bar">
          <Typography.Text type="secondary">高级选项</Typography.Text>
          <div className="spark-jar-runtime-option-list">
            {advancedItems.map((item) => (
              <button
                key={item.key}
                type="button"
                className="spark-jar-runtime-option-chip"
                onClick={() => openAdvanced(item.key)}
              >
                <span>{item.label}</span>
                <small>{item.summary}</small>
              </button>
            ))}
          </div>
          <Button
            type="text"
            size="small"
            icon={<SettingOutlined />}
            disabled={advancedItems.length === 0}
            onClick={() => openAdvanced(activeAdvanced?.key ?? advancedItems[0].key)}
          >
            管理
          </Button>
        </div>
      )}

      <Drawer
        rootClassName="business-overlay business-drawer-overlay spark-jar-runtime-drawer"
        title={<div className="spark-jar-drawer-title"><span className="spark-jar-drawer-icon"><SettingOutlined /></span><div>高级运行配置<small>按需调整 JVM、任务参数与 Spark Conf</small></div></div>}
        width={960}
        open={Boolean(activeAdvanced)}
        onClose={closeAdvanced}
        footer={<div className="spark-jar-resource-footer"><Typography.Text type="secondary">修改后需保存任务配置</Typography.Text><Button type="primary" onClick={closeAdvanced}>完成配置</Button></div>}
      >
        <Tabs
          activeKey={activeAdvanced?.key}
          onChange={openAdvanced}
          items={advancedItems.map((item) => ({
            key: item.key,
            label: (
              <span className="spark-jar-runtime-tab-label">
                {item.label}<small>{item.summary}</small>
              </span>
            ),
            children: <div className="spark-jar-runtime-drawer-editor">{item.children}</div>,
          }))}
        />
      </Drawer>
    </section>
  );
};
