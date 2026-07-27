import { InboxOutlined } from '@ant-design/icons';
import { Alert, Button, Drawer, Space, Upload, message } from 'antd';
import { useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useReplaceFileDatasetFile } from '../hooks/useFileDatasets';
import { fileDatasetAccept, type FileDataset, type FileDatasetFile } from '../model/fileDataset';

interface ReplaceFileDatasetContentDrawerProps {
  fileDataset: FileDataset | null;
  file: FileDatasetFile | null;
  open: boolean;
  onClose: () => void;
}

export const ReplaceFileDatasetContentDrawer = ({ fileDataset, file, open, onClose }: ReplaceFileDatasetContentDrawerProps) => {
  const [messageApi, messageContext] = message.useMessage();
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const replaceMutation = useReplaceFileDatasetFile();

  const close = () => {
    setSelectedFile(null);
    onClose();
  };

  const submit = async () => {
    if (!fileDataset || !file || !selectedFile) return;
    try {
      await replaceMutation.mutateAsync({ datasetId: fileDataset.id, fileId: file.id, file: selectedFile });
      messageApi.success(fileDataset.type === 'GDB'
        ? 'GDB ZIP 已替换，后台正在解包、校验并发现图层'
        : fileDataset.type === 'SHP'
          ? 'SHP ZIP 已替换，后台正在校验并物化组件'
          : '文件已替换，来源表已重建并提交后台解析');
      close();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '替换文件失败');
    }
  };

  return (
    <>
      {messageContext}
      <Drawer
        title={`替换文件 · ${file?.originalFileName ?? ''}`}
        open={open}
        size={520}
        onClose={close}
        destroyOnHidden
        footer={<Space><Button onClick={close}>取消</Button><Button type="primary" loading={replaceMutation.isPending} disabled={!selectedFile} onClick={() => void submit()}>确认替换</Button></Space>}
      >
        <Alert
          type="warning"
          showIcon
          className="file-dataset-form-alert"
          message="替换会硬删除旧表和 Schema"
          description={fileDataset?.type === 'GDB'
            ? '系统会删除旧图层和 Schema，保留新原始 ZIP，并在后台解包为不可变 GDB 目录；校验通过后自动发现图层并提交表解析。'
            : fileDataset?.type === 'SHP'
              ? '系统会删除旧表和 Schema，保留新原始 ZIP，并在后台规范化物化 SHP 组件；校验通过后生成一张新表并提交解析。'
              : '系统将从新文件重新发现表并生成新的 table ID；不会保留旧表、旧 Schema 或版本，新表会使用数据集解析设置自动进入后台队列。'}
        />
        <Upload.Dragger
          accept={fileDataset ? fileDatasetAccept(fileDataset.type) : undefined}
          maxCount={1}
          beforeUpload={(nextFile) => {
            setSelectedFile(nextFile);
            return Upload.LIST_IGNORE;
          }}
          fileList={selectedFile ? [{ uid: selectedFile.name, name: selectedFile.name, status: 'done' }] : []}
          onRemove={() => { setSelectedFile(null); return true; }}
        >
          <p className="ant-upload-drag-icon"><InboxOutlined /></p>
          <p>点击或拖入同类型的新文件</p>
          <p className="ant-upload-hint">允许：{fileDataset ? fileDatasetAccept(fileDataset.type) : '—'}</p>
        </Upload.Dragger>
      </Drawer>
    </>
  );
};
