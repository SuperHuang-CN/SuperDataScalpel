import { Alert, Button, Col, ConfigProvider, Drawer, Form, Input, Row, Select, Space, TreeSelect, message } from 'antd';
import { useEffect } from 'react';
import { ApiError } from '../../../shared/api/http';
import { directoryTreeSelectData, useDirectoryTree } from '../../directory';
import { useCreateFileDataset, useUpdateFileDataset } from '../hooks/useFileDatasets';
import {
  defaultFileDatasetParsingOptions,
  fileDatasetTypeOptions,
  type FileDataset,
  type FileDatasetType,
} from '../model/fileDataset';
import {
  FileDatasetParsingOptionsFields,
} from './FileDatasetParsingOptionsForm';
import { buildFileDatasetParsingOptions, parsingFormValues, type ParsingFormValues } from '../model/fileDatasetParsingForm';

interface FileDatasetDrawerProps {
  open: boolean;
  fileDataset: FileDataset | null;
  initialDirectoryId?: string;
  canViewDirectories: boolean;
  onClose: () => void;
}

interface FileDatasetFormValues extends ParsingFormValues {
  name: string;
  directoryId?: string;
  type: FileDatasetType;
  description?: string;
}

export const FileDatasetDrawer = ({
  open,
  fileDataset,
  initialDirectoryId,
  canViewDirectories,
  onClose,
}: FileDatasetDrawerProps) => {
  const [form] = Form.useForm<FileDatasetFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const directoriesQuery = useDirectoryTree('FILE_DATASET', open && canViewDirectories);
  const createMutation = useCreateFileDataset();
  const updateMutation = useUpdateFileDataset();
  const editing = Boolean(fileDataset);
  const selectedType = Form.useWatch('type', form) ?? fileDataset?.type ?? 'CSV';

  useEffect(() => {
    if (!open) return;
    const type = fileDataset?.type ?? 'CSV';
    const options = fileDataset?.parsingOptions ?? defaultFileDatasetParsingOptions(type);
    form.setFieldsValue({
      name: fileDataset?.name,
      directoryId: fileDataset ? fileDataset.directoryId ?? undefined : initialDirectoryId,
      type,
      description: fileDataset?.description ?? undefined,
      ...parsingFormValues(options),
    });
  }, [fileDataset, form, initialDirectoryId, open]);

  const close = () => {
    form.resetFields();
    onClose();
  };

  const submit = async (values: FileDatasetFormValues) => {
    try {
      const parsingOptions = buildFileDatasetParsingOptions(values.type, values);
      if (fileDataset) {
        await updateMutation.mutateAsync({
          id: fileDataset.id,
          request: {
            name: values.name,
            directoryId: values.directoryId,
            parsingOptions,
            description: values.description,
          },
        });
      } else {
        await createMutation.mutateAsync({
          name: values.name,
          directoryId: values.directoryId,
          type: values.type,
          parsingOptions,
          description: values.description,
        });
      }
      messageApi.success(fileDataset ? '文件数据集已保存' : '空文件数据集已创建');
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
        footer={<Space><Button onClick={close}>取消</Button><Button type="primary" loading={createMutation.isPending || updateMutation.isPending} onClick={() => form.submit()}>{editing ? '保存' : '创建'}</Button></Space>}
      >
        {!editing && <Alert type="info" showIcon className="file-dataset-form-alert" message="先创建空数据集，再进入文件管理上传文件。" />}
        {editing && (
          <Alert
            type="info"
            showIcon
            className="file-dataset-form-alert"
            message={fileDataset?.parsingOptionsLocked
              ? '数据集已有文件、表或解析任务，解析设置已锁定；名称、目录和说明仍可修改。'
              : '同一数据集的所有表共享这套解析设置；首次上传后解析设置将锁定。'}
          />
        )}
        <Form<FileDatasetFormValues> autoComplete="off"
          form={form}
          layout="vertical"
          onFinish={(values) => void submit(values)}
          onValuesChange={(changed) => {
            if ('type' in changed && !editing) {
              form.setFieldsValue(parsingFormValues(defaultFileDatasetParsingOptions(changed.type as FileDatasetType)));
            }
          }}
        >
          <Row gutter={12}>
            <Col span={12}><Form.Item label="数据集名称" name="name" rules={[{ required: true, whitespace: true, message: '请输入数据集名称' }, { max: 100, message: '名称不能超过 100 个字符' }]}><Input autoFocus /></Form.Item></Col>
            <Col span={12}><Form.Item label="数据集类型" name="type" rules={[{ required: true, message: '请选择数据集类型' }]}><Select options={fileDatasetTypeOptions} disabled={editing} /></Form.Item></Col>
            {canViewDirectories && <Col span={24}><Form.Item label="所属目录" name="directoryId"><TreeSelect allowClear treeDefaultExpandAll loading={directoriesQuery.isFetching} treeData={directoryTreeSelectData(directoriesQuery.data ?? [])} placeholder="未分类" /></Form.Item></Col>}
          </Row>
          <ConfigProvider componentDisabled={Boolean(fileDataset?.parsingOptionsLocked)}>
            <FileDatasetParsingOptionsFields type={selectedType} />
          </ConfigProvider>
          <Form.Item label="说明" name="description" rules={[{ max: 1000, message: '说明不能超过 1000 个字符' }]}><Input.TextArea rows={3} maxLength={1000} showCount /></Form.Item>
        </Form>
      </Drawer>
    </>
  );
};
