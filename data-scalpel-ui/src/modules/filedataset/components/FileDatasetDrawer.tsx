import { InboxOutlined } from '@ant-design/icons';
import { Alert, Button, Col, Drawer, Form, Input, Row, Select, Space, Tag, TreeSelect, Upload, message } from 'antd';
import { useEffect, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { directoryTreeSelectData, useDirectoryTree } from '../../directory';
import { useCreateFileDataset, useUpdateFileDataset } from '../hooks/useFileDatasets';
import {
  fileDatasetFormatOptions,
  fileDatasetCompressionLabels,
  inferFileDatasetCompression,
  inferFileDatasetFormat,
  type FileDataset,
  type FileDatasetFormat,
} from '../model/fileDataset';

interface FileDatasetDrawerProps {
  open: boolean;
  fileDataset: FileDataset | null;
  canViewDirectories: boolean;
  onClose: () => void;
}

interface FileDatasetFormValues {
  name: string;
  directoryId?: string;
  format: FileDatasetFormat;
  description?: string;
}

const fileNameWithoutExtension = (fileName: string) => {
  const baseFileName = inferFileDatasetCompression(fileName) === 'GZIP' ? fileName.slice(0, -3) : fileName;
  return baseFileName.replace(/\.[^.]+$/, '');
};

export const FileDatasetDrawer = ({ open, fileDataset, canViewDirectories, onClose }: FileDatasetDrawerProps) => {
  const [form] = Form.useForm<FileDatasetFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string>();
  const directoriesQuery = useDirectoryTree('FILE_DATASET', open && canViewDirectories);
  const createMutation = useCreateFileDataset();
  const updateMutation = useUpdateFileDataset();
  const editing = Boolean(fileDataset);
  const selectedCompression = selectedFile ? inferFileDatasetCompression(selectedFile.name) : null;

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    if (fileDataset) {
      form.setFieldsValue({
        name: fileDataset.name,
        directoryId: fileDataset.directoryId ?? undefined,
        format: fileDataset.format,
        description: fileDataset.description ?? undefined,
      });
    } else {
      form.setFieldsValue({ format: 'CSV' });
    }
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
    if (!form.getFieldValue('name')) form.setFieldValue('name', fileNameWithoutExtension(file.name));
    form.setFieldValue('format', inferFileDatasetFormat(file.name));
    return Upload.LIST_IGNORE;
  };

  const submit = async (values: FileDatasetFormValues) => {
    if (!fileDataset && !selectedFile) {
      setFileError('请选择需要上传的文件');
      return;
    }
    try {
      const request = {
        name: values.name,
        directoryId: values.directoryId,
        format: values.format,
        description: values.description,
      };
      if (fileDataset) {
        await updateMutation.mutateAsync({ id: fileDataset.id, request });
        messageApi.success('文件数据集已保存');
      } else {
        await createMutation.mutateAsync({ request, file: selectedFile as File });
        messageApi.success('文件数据集已创建');
      }
      close();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存文件数据集失败');
    }
  };

  return (
    <>
      {messageContext}
      <Drawer
        title={editing ? '修改文件数据集' : '新建文件数据集'}
        open={open}
        size={620}
        className="file-dataset-drawer"
        onClose={close}
        destroyOnHidden
        footer={(
          <Space>
            <Button onClick={close}>取消</Button>
            <Button
              type="primary"
              loading={createMutation.isPending || updateMutation.isPending}
              onClick={() => form.submit()}
            >
              {editing ? '保存' : '上传并创建'}
            </Button>
          </Space>
        )}
      >
        <Form<FileDatasetFormValues> form={form} layout="vertical" onFinish={(values) => void submit(values)}>
          {!editing && (
            <Form.Item label="原始文件" required validateStatus={fileError ? 'error' : undefined} help={fileError}>
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
                <p>点击或拖入一个文件</p>
                <p className="ant-upload-hint">SHP、FileGDB 请先打包为 ZIP；文件格式可在选择后调整。</p>
            </Upload.Dragger>
            {selectedFile && selectedCompression && (
              <Alert
                type="info"
                showIcon
                className="file-dataset-form-alert"
                message={`已识别：${fileDatasetFormatOptions.find((option) => option.value === inferFileDatasetFormat(selectedFile.name))?.label ?? '其他'} · ${fileDatasetCompressionLabels[selectedCompression]}`}
                description="文件格式和压缩方式最终由服务端根据文件名和内容校验。"
              />
            )}
          </Form.Item>
          )}
          {editing && (
            <Alert
              type="info"
              showIcon
              className="file-dataset-form-alert"
              message={<Space size={6}>当前文件：{fileDataset?.originalFileName}<Tag>{fileDataset ? fileDatasetCompressionLabels[fileDataset.compression] : ''}</Tag></Space>}
              description="这里仅修改名称、目录、格式和说明；需要更新原始内容时请使用列表中的“替换内容”。"
            />
          )}
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item label="数据集名称" name="name" rules={[{ required: true, whitespace: true, message: '请输入数据集名称' }, { max: 100, message: '名称不能超过 100 个字符' }]}>
                <Input autoFocus={editing} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="文件格式" name="format" rules={[{ required: true, message: '请选择文件格式' }]}>
                <Select options={fileDatasetFormatOptions} />
              </Form.Item>
            </Col>
            {canViewDirectories && (
              <Col span={24}>
                <Form.Item label="所属目录" name="directoryId">
                  <TreeSelect
                    allowClear
                    treeDefaultExpandAll
                    loading={directoriesQuery.isFetching}
                    treeData={directoryTreeSelectData(directoriesQuery.data ?? [])}
                    placeholder="未分类"
                  />
                </Form.Item>
              </Col>
            )}
            <Col span={24}>
              <Form.Item label="说明" name="description" rules={[{ max: 1000, message: '说明不能超过 1000 个字符' }]}>
                <Input.TextArea rows={4} maxLength={1000} showCount />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Drawer>
    </>
  );
};
