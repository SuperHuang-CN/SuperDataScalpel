import { DeleteOutlined, DownloadOutlined, InboxOutlined } from '@ant-design/icons';
import { Alert, Button, Modal, Space, Typography, Upload, message } from 'antd';
import { useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
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
  const [file, setFile] = useState<File | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const templateMutation = useDownloadDirectoryImportTemplate();
  const importMutation = useImportDirectoryTree(scope);

  const close = () => {
    setFile(null);
    importMutation.reset();
    onClose();
  };

  const downloadTemplate = async () => {
    try {
      const blob = await templateMutation.mutateAsync();
      downloadBlob(blob, 'DataScalpel-目录导入模板.xlsx');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '下载目录模板失败');
    }
  };

  const submit = async () => {
    if (!file) return;
    try {
      const result = await importMutation.mutateAsync(file);
      messageApi.success(
        `导入完成：共 ${result.totalCount} 项，新增 ${result.createdCount} 项，更新 ${result.updatedCount} 项，未变化 ${result.unchangedCount} 项`,
      );
      close();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : `导入${label}失败`);
    }
  };

  return (
    <>
      {messageContext}
      <Modal
        rootClassName="business-overlay business-modal-overlay"
        title={`导入${label}`}
        open={open}
        destroyOnHidden
        okText="确认导入"
        cancelText="取消"
        okButtonProps={{ disabled: !file }}
        confirmLoading={importMutation.isPending}
        onOk={() => void submit()}
        onCancel={close}
      >
        <Space direction="vertical" size={14} style={{ width: '100%' }}>
          <Alert
            showIcon
            type="info"
            title="整份文件校验通过后才会写入"
            description="同一父目录下按名称合并：缺失项新增，已有项更新排序和说明；不会删除文件中未出现的现有目录。"
          />
          <Button
            icon={<DownloadOutlined />}
            loading={templateMutation.isPending}
            onClick={() => void downloadTemplate()}
          >
            下载导入模板
          </Button>
          <Upload.Dragger
            accept=".xlsx"
            maxCount={1}
            showUploadList={false}
            beforeUpload={(selected) => {
              setFile(selected);
              importMutation.reset();
              return Upload.LIST_IGNORE;
            }}
          >
            <p className="ant-upload-drag-icon"><InboxOutlined /></p>
            <p>选择或拖入 .xlsx 目录文件</p>
            <p className="ant-upload-hint">最多 5000 条、10 MB；父子关系使用行标识引用，文件内行顺序不限。</p>
          </Upload.Dragger>
          {file && (
            <div className="directory-import-selected-file">
              <Typography.Text ellipsis title={file.name}>{file.name}</Typography.Text>
              <Button
                type="text"
                danger
                icon={<DeleteOutlined />}
                aria-label="移除已选择的目录文件"
                onClick={() => setFile(null)}
              />
            </div>
          )}
        </Space>
      </Modal>
    </>
  );
};
