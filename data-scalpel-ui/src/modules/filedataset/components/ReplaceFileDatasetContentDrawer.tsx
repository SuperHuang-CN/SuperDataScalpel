import {
  ExclamationCircleFilled,
  FileSyncOutlined,
  InboxOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import { Button, Drawer, Space, Tag, Typography, Upload, message } from 'antd';
import { useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { useReplaceFileDatasetFile } from '../hooks/useFileDatasets';
import {
  fileDatasetAccept,
  fileDatasetFormatLabels,
  fileDatasetTypeLabels,
  formatFileSize,
  type FileDataset,
  type FileDatasetFile,
} from '../model/fileDataset';
import { FileDatasetTypeIcon } from './FileDatasetTypeIcon';

interface ReplaceFileDatasetContentDrawerProps {
  fileDataset: FileDataset | null;
  file: FileDatasetFile | null;
  open: boolean;
  onClose: () => void;
}

export const ReplaceFileDatasetContentDrawer = ({ fileDataset, file, open, onClose }: ReplaceFileDatasetContentDrawerProps) => {
  const [messageApi, messageContext] = message.useMessage();
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [operationError, setOperationError] = useState<string | null>(null);
  const replaceMutation = useReplaceFileDatasetFile();

  const close = () => {
    setSelectedFile(null);
    setOperationError(null);
    onClose();
  };

  const submit = async () => {
    if (!fileDataset || !file || !selectedFile) return;
    setOperationError(null);
    try {
      await replaceMutation.mutateAsync({ datasetId: fileDataset.id, fileId: file.id, file: selectedFile });
      messageApi.success(fileDataset.type === 'GDB'
        ? 'GDB ZIP 已替换，后台正在解包、校验并发现图层'
        : fileDataset.type === 'GPKG'
          ? 'GeoPackage 已替换，后台正在校验并发现图层和属性表'
        : fileDataset.type === 'SHP'
          ? 'SHP ZIP 已替换，后台正在校验并物化组件'
          : '文件已替换，来源表已重建并提交后台解析');
      close();
    } catch (error) {
      const errorMessage = error instanceof ApiError ? error.message : '替换文件失败';
      setOperationError(errorMessage);
      messageApi.error(errorMessage);
    }
  };

  const replacementEffect = fileDataset?.type === 'GDB'
    ? '旧图层与 Schema 会被永久删除。新 ZIP 校验通过后，系统将重新解包并发现图层。'
    : fileDataset?.type === 'GPKG'
      ? '旧图层、属性表与 Schema 会被永久删除。新 GeoPackage 校验通过后，系统将重新发现业务表。'
    : fileDataset?.type === 'SHP'
      ? '旧表与 Schema 会被永久删除。新 ZIP 校验通过后，系统将重新物化 SHP 组件并生成表。'
      : '旧表与 Schema 会被永久删除。系统将从新文件重新发现表，并生成新的 table ID。';

  const replacementDetail = fileDataset?.type === 'GDB'
    ? '系统保留新的原始 ZIP，并在后台解包为不可变 GDB 目录；校验通过后自动发现图层并提交表解析。'
    : fileDataset?.type === 'GPKG'
      ? '系统保留新的 .gpkg 原始对象，并在后台只读校验后发现 features 图层和 attributes 属性表；每张表独立进入解析队列。'
    : fileDataset?.type === 'SHP'
      ? '系统保留新的原始 ZIP，并在后台规范化物化 SHP 组件；校验通过后生成一张新表并提交解析。'
      : '旧表、旧 Schema 和版本均不会保留；新表会继续使用当前数据集的解析设置进入后台队列。';

  const footerStatus = operationError ? (
    <InlineFeedback
      tone="error"
      label="替换失败"
      detail={operationError}
      ariaLabel="查看文件替换失败详情"
    />
  ) : selectedFile ? (
    <InlineFeedback
      tone="success"
      label={`已选择 ${selectedFile.name}`}
      detail={`${formatFileSize(selectedFile.size)} · 提交后将开始校验和后台解析`}
      ariaLabel="查看待替换文件详情"
    />
  ) : (
    <InlineFeedback tone="info" label="尚未选择新文件" />
  );

  return (
    <>
      {messageContext}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        className="replace-file-dataset-content-drawer"
        title={(
          <div className="file-dataset-drawer-title replace-file-dataset-title">
            <span className="file-dataset-drawer-title-icon" aria-hidden="true">
              {fileDataset ? <FileDatasetTypeIcon type={fileDataset.type} /> : <FileSyncOutlined />}
            </span>
            <span className="file-dataset-drawer-title-copy">
              <span>替换文件内容</span>
              <Typography.Text type="secondary">
                确认当前文件后，上传同类型文件重新建立数据结构
              </Typography.Text>
            </span>
          </div>
        )}
        extra={fileDataset ? <Tag className="file-dataset-drawer-header-tag">{fileDatasetTypeLabels[fileDataset.type]}</Tag> : undefined}
        open={open}
        size={520}
        closable={replaceMutation.isPending ? false : { placement: 'end' }}
        maskClosable={!replaceMutation.isPending}
        onClose={close}
        destroyOnHidden
        footer={(
          <div className="file-dataset-drawer-footer replace-file-dataset-footer">
            {footerStatus}
            <Space>
              <Button disabled={replaceMutation.isPending} onClick={close}>取消</Button>
              <Button type="primary" danger loading={replaceMutation.isPending} disabled={!selectedFile} onClick={() => void submit()}>
                确认替换
              </Button>
            </Space>
          </div>
        )}
      >
        <div className="replace-file-dataset-workspace">
          <section className="replace-file-dataset-section replace-file-dataset-context-section">
            <header className="replace-file-dataset-section-header">
              <span className="replace-file-dataset-section-icon" aria-hidden="true"><SafetyCertificateOutlined /></span>
              <span className="replace-file-dataset-section-copy">
                <strong>当前文件</strong>
                <Typography.Text type="secondary">核对即将被替换的数据集与源文件</Typography.Text>
              </span>
            </header>
            <div className="replace-file-dataset-section-body">
              <div className="replace-file-dataset-current-file">
                <div className="replace-file-dataset-current-file-icon" aria-hidden="true"><FileSyncOutlined /></div>
                <div className="replace-file-dataset-current-file-copy">
                  <strong title={file?.originalFileName}>{file?.originalFileName ?? '—'}</strong>
                  <span title={fileDataset?.name}>{fileDataset?.name ?? '—'}</span>
                </div>
                <div className="replace-file-dataset-current-file-meta">
                  <span>{file ? fileDatasetFormatLabels[file.format] : '—'}</span>
                  <strong>{file ? formatFileSize(file.sizeBytes) : '—'}</strong>
                </div>
              </div>
              <div className="replace-file-dataset-risk" role="alert">
                <ExclamationCircleFilled aria-hidden="true" />
                <div>
                  <span className="replace-file-dataset-risk-title">
                    <strong>替换后不可恢复</strong>
                    <ContextHelp
                      ariaLabel="查看文件替换处理详情"
                      content={replacementDetail}
                      tone="warning"
                      presentation="popover"
                      placement="bottomLeft"
                    />
                  </span>
                  <span>{replacementEffect}</span>
                </div>
              </div>
            </div>
          </section>

          <section className="replace-file-dataset-section replace-file-dataset-upload-section">
            <header className="replace-file-dataset-section-header">
              <span className="replace-file-dataset-section-icon" aria-hidden="true"><InboxOutlined /></span>
              <span className="replace-file-dataset-section-copy">
                <strong>上传新文件</strong>
                <Typography.Text type="secondary">仅接受与当前数据集类型一致的文件</Typography.Text>
              </span>
            </header>
            <div className="replace-file-dataset-section-body">
              <Upload.Dragger
                className="replace-file-dataset-uploader"
                accept={fileDataset ? fileDatasetAccept(fileDataset.type) : undefined}
                maxCount={1}
                beforeUpload={(nextFile) => {
                  setSelectedFile(nextFile);
                  setOperationError(null);
                  return Upload.LIST_IGNORE;
                }}
                fileList={selectedFile ? [{ uid: selectedFile.name, name: selectedFile.name, status: 'done' }] : []}
                onRemove={() => { setSelectedFile(null); return true; }}
              >
                <p className="ant-upload-drag-icon"><InboxOutlined /></p>
                <p className="replace-file-dataset-upload-title">点击或拖入同类型的新文件</p>
                <p className="ant-upload-hint">允许格式：{fileDataset ? fileDatasetAccept(fileDataset.type) : '—'}</p>
              </Upload.Dragger>
            </div>
          </section>
        </div>
      </Drawer>
    </>
  );
};
