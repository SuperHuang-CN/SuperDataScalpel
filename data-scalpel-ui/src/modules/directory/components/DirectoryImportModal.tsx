import { DeleteOutlined, DownloadOutlined, FileExcelOutlined, FolderOpenOutlined, InboxOutlined, UploadOutlined } from '@ant-design/icons';
import { Button, ConfigProvider, Modal, Space, Tag, Typography, Upload, message } from 'antd';
import { useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { workspaceResourceTheme } from '../../../shared/theme/workspaceResourceTheme';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import {
  useDownloadDirectoryImportTemplate,
  useImportDirectoryTree,
} from '../hooks/useDirectories';
import type { DirectoryScope } from '../model/directory';

interface DirectoryImportModalProps {
  scope: DirectoryScope;
  label: string;
  open: boolean;
  onClose: () => void;
}

export const DirectoryImportModal = ({ scope, label, open, onClose }: DirectoryImportModalProps) => {
  const resourceStyle = scope === 'DATA_SOURCE' || scope === 'FILE_DATASET' || scope === 'PANORAMA' || scope === 'MODEL' || scope === 'METRIC' || scope === 'BUSINESS_OBJECT' || scope === 'TASK' || scope === 'DATA_SERVICE' || scope === 'MCP_SERVER' || scope === 'ASSET';
  const [file, setFile] = useState<File | null>(null);
  const [operationError, setOperationError] = useState<string | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const templateMutation = useDownloadDirectoryImportTemplate();
  const importMutation = useImportDirectoryTree(scope);

  const close = () => {
    if (importMutation.isPending) return;
    setFile(null);
    setOperationError(null);
    importMutation.reset();
    onClose();
  };

  const downloadTemplate = async () => {
    setOperationError(null);
    try {
      const blob = await templateMutation.mutateAsync();
      downloadBlob(blob, 'DataScalpel-目录导入模板.xlsx');
    } catch (error) {
      const errorMessage = error instanceof ApiError ? error.message : '下载目录模板失败';
      setOperationError(errorMessage);
      messageApi.error(errorMessage);
    }
  };

  const submit = async () => {
    if (!file) return;
    setOperationError(null);
    try {
      const result = await importMutation.mutateAsync(file);
      messageApi.success(
        `导入完成：共 ${result.totalCount} 项，新增 ${result.createdCount} 项，更新 ${result.updatedCount} 项，未变化 ${result.unchangedCount} 项`,
      );
      close();
    } catch (error) {
      const errorMessage = error instanceof ApiError ? error.message : `导入${label}失败`;
      setOperationError(errorMessage);
      messageApi.error(errorMessage);
    }
  };

  return (
    <ConfigProvider theme={resourceStyle ? workspaceResourceTheme : undefined}>
      {messageContext}
      <Modal
        rootClassName={`business-overlay business-modal-overlay${resourceStyle ? ' workspace-resource-overlay resource-import-modal' : ' directory-import-modal'}`}
        width={resourceStyle ? 520 : undefined}
        centered={resourceStyle}
        title={resourceStyle ? (
          <div className="resource-dialog-title"><UploadOutlined aria-hidden="true" /><span>导入{label}</span></div>
        ) : (
          <div className="directory-import-title">
            <span className="directory-import-title-icon" aria-hidden="true"><FolderOpenOutlined /></span>
            <span className="directory-import-title-copy">
              <span>导入{label}</span>
              <Typography.Text type="secondary">从固定模板批量建立或更新目录层级</Typography.Text>
            </span>
          </div>
        )}
        open={open}
        destroyOnHidden
        closable={!importMutation.isPending}
        maskClosable={!importMutation.isPending}
        onCancel={close}
        footer={(
          <div className={resourceStyle ? 'resource-dialog-footer' : 'directory-import-footer'}>
            {resourceStyle ? <span className="resource-dialog-footnote">校验通过后才会写入</span> : operationError ? (
              <InlineFeedback tone="error" label="目录导入处理失败" detail={operationError} />
            ) : file ? (
              <InlineFeedback tone="success" label={`已选择 ${file.name}`} />
            ) : (
              <InlineFeedback tone="info" label="整份文件校验通过后才会写入" />
            )}
            <Space>
              <Button disabled={importMutation.isPending} onClick={close}>取消</Button>
              <Button type="primary" disabled={!file} loading={importMutation.isPending} onClick={() => void submit()}>确认导入</Button>
            </Space>
          </div>
        )}
      >
        {resourceStyle ? (
          <div className="resource-import-body">
            <div className="resource-import-template">
              <div><strong>准备目录文件</strong><p>按模板填写目录名称、层级和排序。</p></div>
              <Button icon={<DownloadOutlined />} loading={templateMutation.isPending} onClick={() => void downloadTemplate()}>下载模板</Button>
            </div>
            <Upload.Dragger
              className={`resource-import-upload${file ? ' has-file' : ''}`}
              accept=".xlsx"
              maxCount={1}
              showUploadList={false}
              disabled={importMutation.isPending}
              beforeUpload={(selected) => {
                setFile(selected);
                setOperationError(null);
                importMutation.reset();
                return Upload.LIST_IGNORE;
              }}
            >
              {file ? <FileExcelOutlined className="resource-import-file-icon" /> : <UploadOutlined className="resource-import-file-icon" />}
              <span className="resource-import-upload-title">{file ? file.name : '选择文件，或拖放到这里'}</span>
              <span className="resource-import-upload-detail">{file ? `${(file.size / 1024).toLocaleString('zh-CN', { maximumFractionDigits: 1 })} KB · 点击可重新选择` : '.xlsx 格式 · 最大 10 MB · 最多 5000 条'}</span>
            </Upload.Dragger>
            <div className="resource-import-options">
              <span>同名目录合并更新
                <ContextHelp ariaLabel="查看目录导入合并规则" content="同一父目录下按名称合并：缺失项新增，已有项更新排序和说明；不会删除文件中未出现的现有目录。父子关系使用行标识引用，文件内行顺序不限。" presentation="popover" placement="bottomLeft" />
              </span>
              {file && <Button type="text" icon={<DeleteOutlined />} disabled={importMutation.isPending} onClick={() => { setFile(null); setOperationError(null); importMutation.reset(); }}>移除文件</Button>}
            </div>
            {operationError && <div className="resource-import-error" role="alert"><InlineFeedback tone="error" label="操作未完成" detail={operationError} /></div>}
          </div>
        ) : <section className="directory-import-section">
          <header className="directory-import-section-header">
            <span className="directory-import-section-icon" aria-hidden="true"><InboxOutlined /></span>
            <span className="directory-import-section-copy">
              <span className="directory-import-section-title-row">
                <strong>目录文件</strong>
                <ContextHelp
                  ariaLabel="查看目录导入合并规则"
                  content="同一父目录下按名称合并：缺失项新增，已有项更新排序和说明；不会删除文件中未出现的现有目录。父子关系使用行标识引用，文件内行顺序不限。"
                  presentation="popover"
                  placement="bottomLeft"
                />
              </span>
              <Typography.Text type="secondary">最多 5000 条、10 MB，选择后仍需确认导入</Typography.Text>
            </span>
            <Button icon={<DownloadOutlined />} loading={templateMutation.isPending} onClick={() => void downloadTemplate()}>
              下载模板
            </Button>
          </header>
          <div className="directory-import-section-body">
            <Upload.Dragger
              className="directory-import-uploader"
              accept=".xlsx"
              maxCount={1}
              showUploadList={false}
              beforeUpload={(selected) => {
                setFile(selected);
                setOperationError(null);
                importMutation.reset();
                return Upload.LIST_IGNORE;
              }}
            >
              <p className="ant-upload-drag-icon"><InboxOutlined /></p>
              <p className="directory-import-upload-title">点击或拖入 .xlsx 目录文件</p>
              <p className="ant-upload-hint">建议先下载当前固定模板，避免层级引用格式不一致</p>
            </Upload.Dragger>
            {file && (
              <div className="directory-import-selected-file">
                <span>
                  <Tag>待导入</Tag>
                  <Typography.Text ellipsis title={file.name}>{file.name}</Typography.Text>
                </span>
                <Button
                  type="text"
                  danger
                  icon={<DeleteOutlined />}
                  aria-label="移除已选择的目录文件"
                  onClick={() => setFile(null)}
                />
              </div>
            )}
          </div>
        </section>}
      </Modal>
    </ConfigProvider>
  );
};
