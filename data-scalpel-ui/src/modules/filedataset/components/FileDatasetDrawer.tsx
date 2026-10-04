import { DatabaseOutlined, FolderOutlined, SettingOutlined } from '@ant-design/icons';
import { OverlayTitle } from '../../../shared/components/OverlayTitle';
import { Button, Col, ConfigProvider, Drawer, Form, Input, Row, Select, Space, Tag, TreeSelect, message } from 'antd';
import { type ReactNode, useEffect, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { directoryTreeSelectOptions, useDirectoryTree } from '../../directory';
import { useCreateFileDataset, useUpdateFileDataset } from '../hooks/useFileDatasets';
import {
  defaultFileDatasetParsingOptions,
  fileDatasetTypeLabels,
  fileDatasetTypeOptions,
  type FileDataset,
  type FileDatasetType,
} from '../model/fileDataset';
import {
  FileDatasetParsingOptionsFields,
} from './FileDatasetParsingOptionsForm';
import { FileDatasetTypeIcon } from './FileDatasetTypeIcon';
import { buildFileDatasetParsingOptions, parsingFormValues, type ParsingFormValues } from '../model/fileDatasetParsingForm';
import { workspaceResourceTheme } from '../../../shared/theme/workspaceResourceTheme';
import './file-dataset-editor.css';

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

const FileDatasetFormSection = ({ title, description, icon, help, children }: {
  title: string;
  description?: string;
  icon: ReactNode;
  help?: ReactNode;
  children: ReactNode;
}) => (
  <section className="file-dataset-editor-section" aria-label={title}>
    <header className="file-dataset-editor-section-heading">
      <h3><span aria-hidden="true">{icon}</span>{title}</h3>
      {help && <ContextHelp ariaLabel={`${title}说明`} content={help} presentation="popover" placement="bottomLeft" />}
    </header>
    {description && <p className="file-dataset-editor-section-description">{description}</p>}
    {children}
  </section>
);

const parsingHelp = (type: FileDatasetType): ReactNode | undefined => {
  if (type === 'GDB') {
    return '系统优先读取各图层 WKT 中明确声明的 EPSG；无法识别时才使用回退 EPSG，且不会执行坐标转换。';
  }
  if (type === 'SHP') {
    return '每个 ZIP 必须包含一套同名 .shp/.shx/.dbf，可选携带 .cpg/.prj；常见索引和元数据辅助文件会自动忽略。';
  }
  return undefined;
};

export const FileDatasetDrawer = ({
  open,
  fileDataset,
  initialDirectoryId,
  canViewDirectories,
  onClose,
}: FileDatasetDrawerProps) => {
  const [form] = Form.useForm<FileDatasetFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const [operationError, setOperationError] = useState<string | null>(null);
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
    setOperationError(null);
    onClose();
  };

  const submit = async (values: FileDatasetFormValues) => {
    setOperationError(null);
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
      const errorMessage = error instanceof ApiError ? error.message : '保存文件数据集失败';
      setOperationError(errorMessage);
      messageApi.error(errorMessage);
    }
  };

  const pending = createMutation.isPending || updateMutation.isPending;
  const parsingLocked = Boolean(fileDataset?.parsingOptionsLocked);
  const footerStatus = operationError ? (
    <InlineFeedback
      tone="error"
      label={editing ? '保存失败' : '创建失败'}
      detail={operationError}
      ariaLabel={editing ? '查看保存失败详情' : '查看创建失败详情'}
    />
  ) : parsingLocked ? (
    <InlineFeedback
      tone="warning"
      label="解析设置已锁定"
      detail="数据集已有文件、表或解析任务；名称、目录和说明仍可修改。"
      ariaLabel="查看解析设置锁定原因"
    />
  ) : (
    <span className="file-dataset-editor-footer-hint">{editing ? '首次上传后解析设置将锁定' : '创建后可进入数据集上传文件'}</span>
  );

  return (
    <ConfigProvider theme={workspaceResourceTheme}>
      {messageContext}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay workspace-resource-overlay"
        className="file-dataset-editor"
        title={<OverlayTitle title={editing ? '修改文件数据集' : '新建文件数据集'} icon={<FileDatasetTypeIcon type={selectedType} />} description={editing ? '维护数据集归属、基础说明与共享解析规则' : '先建立空数据集，再进入文件管理上传并解析文件'} />}
        extra={<Tag className="file-dataset-drawer-header-tag">{fileDatasetTypeLabels[selectedType]}</Tag>}
        open={open}
        size="min(760px, 100vw)"
        closable={pending ? false : { placement: 'end' }}
        maskClosable={!pending}
        onClose={close}
        destroyOnHidden
        footer={(
          <div className="file-dataset-editor-footer">
            {footerStatus}
            <Space>
              <Button disabled={pending} onClick={close}>取消</Button>
              <Button type="primary" loading={pending} onClick={() => form.submit()}>
                {editing ? '保存修改' : '创建数据集'}
              </Button>
            </Space>
          </div>
        )}
      >
        <Form<FileDatasetFormValues>
          name="file-dataset-editor-form"
          autoComplete="off"
          form={form}
          layout="vertical"
          className="file-dataset-editor-form"
          onFinish={(values) => void submit(values)}
          onValuesChange={(changed) => {
            if ('type' in changed && !editing) {
              form.setFieldsValue(parsingFormValues(defaultFileDatasetParsingOptions(changed.type as FileDatasetType)));
            }
          }}
        >
          <FileDatasetFormSection
            title="数据集信息"
           description="设置数据集的名称、文件类型和目录归属"
            icon={<DatabaseOutlined />}
          >
            <Row gutter={14}>
              <Col span={12} xs={24} sm={12}>
                <Form.Item label="数据集名称" name="name" rules={[{ required: true, whitespace: true, message: '请输入数据集名称' }, { max: 100, message: '名称不能超过 100 个字符' }]}>
                  <Input name="file-dataset-display-name" autoComplete="off" autoFocus placeholder="输入数据集名称" />
                </Form.Item>
              </Col>
              <Col span={12} xs={24} sm={12}>
                <Form.Item label="数据集类型" name="type" rules={[{ required: true, message: '请选择数据集类型' }]}>
                  <Select options={fileDatasetTypeOptions} disabled={editing} showSearch={{ optionFilterProp: 'label' }}
                    classNames={{ popup: { root: 'file-dataset-editor-type-popup' } }}
                    optionRender={(option) => <span className="file-dataset-editor-type-option"><FileDatasetTypeIcon type={option.data.value} /><span>{option.data.label}</span></span>}
                    labelRender={({ value, label }) => <span className="file-dataset-editor-type-option"><FileDatasetTypeIcon type={value as FileDatasetType} /><span>{label}</span></span>}
                  />
                </Form.Item>
              </Col>
              {canViewDirectories && (
                <Col span={24}>
                  <Form.Item label="所属目录" name="directoryId">
                    <TreeSelect allowClear treeDefaultExpandAll treeIcon loading={directoriesQuery.isFetching}
                      treeData={directoryTreeSelectOptions(directoriesQuery.data ?? [])} placeholder="未分类"
                      prefix={<FolderOutlined className="workspace-directory-prefix" />}
                      classNames={{ popup: { root: 'workspace-resource-select' } }}
                    />
                  </Form.Item>
                </Col>
              )}
              <Col span={24}>
                <Form.Item label={<span>说明 <span className="file-dataset-editor-optional">选填</span></span>} name="description" rules={[{ max: 1000, message: '说明不能超过 1000 个字符' }]}>
                  <Input.TextArea name="file-dataset-description" autoComplete="off" autoSize={{ minRows: 2, maxRows: 4 }} maxLength={1000} showCount placeholder="补充数据集用途或内容说明" />
                </Form.Item>
              </Col>
            </Row>
          </FileDatasetFormSection>

          <FileDatasetFormSection
            title="解析设置"
           description={`${fileDatasetTypeLabels[selectedType]} 文件共享同一套解析规则`}
            icon={<SettingOutlined />}
            help={parsingHelp(selectedType)}
          >
            {parsingLocked && (
              <InlineFeedback
                className="file-dataset-parsing-lock-feedback"
                tone="warning"
                label="当前解析设置只读"
                detail="数据集已有文件、表或解析任务，继续修改解析规则可能造成同一数据集语义不一致。"
                ariaLabel="查看解析设置只读原因"
              />
            )}
            <ConfigProvider componentDisabled={parsingLocked}>
              <FileDatasetParsingOptionsFields type={selectedType} />
            </ConfigProvider>
          </FileDatasetFormSection>
        </Form>
      </Drawer>
    </ConfigProvider>
  );
};
