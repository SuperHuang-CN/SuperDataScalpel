import {
  DownloadOutlined,
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
} from '../model/task';
import { formatSparkJarBytes, sparkJarKitStageLabels } from './sparkJarDefinitionWorkspaceModel';

interface SparkJarArtifactSummaryProps {
  definition: SparkJarTaskDefinition;
  uploadFiles: UploadFile[];
  uploading: boolean;
  beforeUpload: NonNullable<UploadProps['beforeUpload']>;
  onClearSelection: () => void;
  onUpload: () => void;
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
      <div className="spark-jar-artifact-icon" aria-hidden="true"><FileZipOutlined /></div>
      <div className="spark-jar-artifact-identity">
        <Space size={8} wrap>
          <Typography.Text strong>{pendingFile?.name ?? jar?.fileName ?? '尚未上传用户作业 JAR'}</Typography.Text>
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
          <Typography.Text type="secondary">上传后平台会读取 Manifest 和类条目，不会执行用户代码。</Typography.Text>
        )}
      </div>
      <Space className="spark-jar-artifact-actions" wrap>
        {pendingFile ? (
          <>
            <Button onClick={onClearSelection}>取消选择</Button>
            <Button type="primary" icon={<UploadOutlined />} loading={uploading} onClick={onUpload}>
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
          <Descriptions bordered size="small" column={1}>
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
  downloading,
  onGenerate,
  onDownload,
}: SparkJarDevelopmentKitPanelProps) => {
  const usesPreviousArtifact = Boolean(artifact && (configurationDirty || !artifact.matchesSavedConfiguration));

  return (
    <aside className="spark-jar-development-kit-panel">
      <div className="spark-jar-development-kit-heading">
        <div>
          <Typography.Text strong>本地开发包</Typography.Text>
          <Typography.Text type="secondary">代码、Schema 与 Parquet 样例</Typography.Text>
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
            <Typography.Text type="secondary">配置本地样例后即可生成 Maven 开发工程。</Typography.Text>
          )}
        </div>

        <div className="spark-jar-development-kit-actions">
          <Button
            type="primary"
            block
            icon={<ExperimentOutlined />}
            disabled={running || submitting}
            onClick={onGenerate}
          >
            {artifact ? '重新生成' : '生成开发包'}
          </Button>
          {artifact && (
            <Button block icon={<DownloadOutlined />} loading={downloading} onClick={onDownload}>
              下载开发包
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
          <Typography.Text strong>运行配置</Typography.Text>
          <Typography.Text type="secondary">任务启动时使用的计算资源与高级参数</Typography.Text>
        </div>
        <Button type="text" size="small" icon={<EditOutlined />} onClick={togglePrimary}>
          {primaryOpen ? '收起' : '调整'}
        </Button>
      </div>

      <div className="spark-jar-runtime-sentence">
        <span>此任务将在</span>
        <Tag bordered={false}>{overview.environment}</Tag>
        <span>上运行，使用</span>
        <strong>{overview.resources}</strong>
        <span className="spark-jar-runtime-sentence-separator" aria-hidden="true">·</span>
        <span>最长运行</span>
        <strong>{overview.timeout}</strong>
      </div>

      {primaryOpen && (
        <div className="spark-jar-runtime-primary-editor">
          {primaryItems.map((item) => (
            <div key={item.key} className={`spark-jar-runtime-primary-item spark-jar-runtime-primary-item-${item.key}`}>
              {item.children}
            </div>
          ))}
        </div>
      )}

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
        title="高级运行配置"
        width={720}
        open={Boolean(activeAdvanced)}
        onClose={closeAdvanced}
      >
        <Typography.Paragraph type="secondary" className="spark-jar-runtime-drawer-intro">
          这些参数只在需要定制用户作业启动或 Spark 行为时配置，修改后随任务定义一起保存。
        </Typography.Paragraph>
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
