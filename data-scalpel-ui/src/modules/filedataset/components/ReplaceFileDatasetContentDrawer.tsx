import { InboxOutlined } from '@ant-design/icons';
import { Alert, Button, Drawer, Form, Select, Space, Upload, message } from 'antd';
import { useEffect, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useReplaceFileDatasetContent } from '../hooks/useFileDatasets';
import {
  fileDatasetCompressionLabels,
  fileDatasetFormatOptions,
  inferFileDatasetCompression,
  inferFileDatasetFormat,
  type FileDataset,
  type FileDatasetFormat,
} from '../model/fileDataset';

interface ReplaceFileDatasetContentDrawerProps {
  fileDataset: FileDataset | null;
  open: boolean;
  onClose: () => void;
}

interface ReplaceContentFormValues {
  format: FileDatasetFormat;
}

export const ReplaceFileDatasetContentDrawer = ({ fileDataset, open, onClose }: ReplaceFileDatasetContentDrawerProps) => {
  const [form] = Form.useForm<ReplaceContentFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string>();
  const replaceMutation = useReplaceFileDatasetContent();
  const selectedCompression = selectedFile ? inferFileDatasetCompression(selectedFile.name) : null;

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue({ format: fileDataset?.format ?? 'CSV' });
  }, [fileDataset, form, open]);

  const close = () => {
    setSelectedFile(null);
    setFileError(undefined);
    form.resetFields();
    onClose();
  };

  const selectFile = (file: File) => {
    setSelectedFile(file);
    setFileError(undefined);
    form.setFieldValue('format', inferFileDatasetFormat(file.name));
    return Upload.LIST_IGNORE;
  };

  const submit = async ({ format }: ReplaceContentFormValues) => {
    if (!fileDataset || !selectedFile) {
      setFileError('请选择新的原始文件');
      return;
    }
    try {
      await replaceMutation.mutateAsync({ id: fileDataset.id, format, file: selectedFile });
      messageApi.success('文件内容已替换');
      close();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '替换文件内容失败');
    }
  };

  return (
    <>
      {messageContext}
      <Drawer
        title={`替换内容 · ${fileDataset?.name ?? ''}`}
        open={open}
        size={520}
        className="file-dataset-drawer"
        onClose={close}
        destroyOnHidden
        footer={<Space><Button onClick={close}>取消</Button><Button type="primary" loading={replaceMutation.isPending} onClick={() => form.submit()}>确认替换</Button></Space>}
      >
        <Alert
          type="warning"
          showIcon
          className="file-dataset-form-alert"
          message="替换后原文件将被清理"
          description="替换成功后解析状态会回到“待解析”，已配置或生成的解析信息也会失效。"
        />
        <Form<ReplaceContentFormValues> form={form} layout="vertical" onFinish={(values) => void submit(values)}>
          <Form.Item label="新原始文件" required validateStatus={fileError ? 'error' : undefined} help={fileError}>
            <Upload.Dragger
              accept=".csv,.tsv,.txt,.json,.jsonl,.ndjson,.xls,.xlsx,.parquet,.avro,.zip,.gz"
              maxCount={1}
              beforeUpload={selectFile}
              fileList={selectedFile ? [{ uid: selectedFile.name, name: selectedFile.name, status: 'done' }] : []}
              onRemove={() => {
                setSelectedFile(null);
                return true;
              }}
            >
              <p className="ant-upload-drag-icon"><InboxOutlined /></p>
              <p>点击或拖入替换文件</p>
            </Upload.Dragger>
          </Form.Item>
          {selectedCompression && <Alert type="info" showIcon className="file-dataset-form-alert" message={`已识别压缩方式：${fileDatasetCompressionLabels[selectedCompression]}`} />}
          <Form.Item label="文件格式" name="format" rules={[{ required: true, message: '请选择文件格式' }]}>
            <Select options={fileDatasetFormatOptions} />
          </Form.Item>
        </Form>
      </Drawer>
    </>
  );
};
