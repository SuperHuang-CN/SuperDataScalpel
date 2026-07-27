import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  DownloadOutlined,
  EditOutlined,
  FileExcelOutlined,
  LoadingOutlined,
  InboxOutlined,
} from '@ant-design/icons';
import type { TableProps, UploadFile } from 'antd';
import {
  Alert,
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Steps,
  Table,
  Tag,
  TreeSelect,
  Typography,
  Upload,
  message,
} from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import { useDataSources } from '../../datasource';
import { directoryTreeSelectData, useDirectoryTree } from '../../directory';
import {
  type ManagedDataModelDraftResult,
  useCreateManagedDataModelDrafts,
  useDownloadModelMetadataTemplate,
  usePlatformTypeCapabilities,
  usePreviewModelMetadataImport,
} from '../hooks/useDataModels';
import {
  dataModelFieldTypeLabels,
  geometryKindLabels,
  type GeometryKind,
  type PlatformDataType,
} from '../model/dataModel';
import {
  hasModelMetadataDraftIssues,
  modelMetadataDraftIssues,
  modelMetadataDrafts,
  toManagedDraftRequest,
  type ModelMetadataDraft,
  type ModelMetadataFieldDraft,
} from '../model/modelMetadataImport';
import { isManagedImportTargetSelectable } from '../model/managedTableImport';

interface ModelMetadataImportDrawerProps {
  open: boolean;
  canViewDirectories: boolean;
  initialDirectoryId?: string;
  initialTargetStorageDataSourceId?: string;
  onClose: () => void;
  onAdjustFields: (modelId: string) => void;
}

const dataSourceRequest = {
  search: 'enabled:"true"', page: 0, size: 500, sort: 'code',
} as const;

const errorMessage = (error: unknown, fallback: string): string => {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return fallback;
};

const fieldTypeOptions = (Object.entries(dataModelFieldTypeLabels) as [PlatformDataType, string][])
  .map(([value, label]) => ({ value, label }));

interface MetadataFieldEditorProps {
  field: ModelMetadataFieldDraft | null;
  storageDataSourceId?: string;
  onCancel: () => void;
  onSave: (field: ModelMetadataFieldDraft) => void;
}

const MetadataFieldEditor = ({ field, storageDataSourceId, onCancel, onSave }: MetadataFieldEditorProps) => {
  const [form] = Form.useForm<ModelMetadataFieldDraft>();
  const selectedType = Form.useWatch('fieldType', form);
  const primaryKey = Form.useWatch('primaryKey', form);
  const capabilitiesQuery = usePlatformTypeCapabilities(storageDataSourceId, Boolean(field));
  const capabilities = useMemo(() => new Map(
    (capabilitiesQuery.data ?? []).map((capability) => [capability.type, capability]),
  ), [capabilitiesQuery.data]);
  const options = fieldTypeOptions.map((option) => ({
    ...option,
    disabled: capabilities.has(option.value) ? !capabilities.get(option.value)?.supported : false,
    title: capabilities.get(option.value)?.message ?? undefined,
  }));
  const selectedCapability = selectedType ? capabilities.get(selectedType) : undefined;
  const geometryKindOptions = (
    selectedCapability?.geometryKinds?.length
      ? selectedCapability.geometryKinds
      : (Object.keys(geometryKindLabels) as GeometryKind[])
  ).map((value) => ({ value, label: geometryKindLabels[value] }));

  const submit = async () => {
    if (!field) return;
    const values = await form.validateFields();
    onSave({
      ...field,
      code: values.code.trim(),
      name: values.name.trim(),
      fieldType: values.fieldType,
      ...(values.fieldType === 'STRING' && values.length !== undefined ? { length: values.length } : { length: undefined }),
      ...(values.fieldType === 'DECIMAL'
        ? { precision: values.precision, scale: values.scale }
        : { precision: undefined, scale: undefined }),
      ...(values.fieldType === 'GEOMETRY'
        ? { geometry: values.geometry }
        : { geometry: undefined }),
      nullable: values.primaryKey ? false : values.nullable,
      primaryKey: values.fieldType === 'GEOMETRY' ? false : values.primaryKey,
      sortOrder: values.sortOrder,
      description: values.description?.trim() ?? '',
      serverIssues: [],
    });
  };

  return (
    <Modal
      title={field ? `调整 Excel 字段：${field.code || `第 ${field.rowNumber} 行`}` : '调整字段'}
      open={Boolean(field)}
      width={680}
      destroyOnHidden
      okText="确定"
      cancelText="取消"
      onCancel={onCancel}
      afterOpenChange={(open) => {
        if (!open || !field) return;
        form.resetFields();
        form.setFieldsValue(field);
      }}
      onOk={() => void submit()}
    >
      {field?.serverIssues.length ? <Alert showIcon type="warning" title={field.serverIssues.join('；')} /> : null}
      <Form<ModelMetadataFieldDraft> form={form} layout="vertical" className="metadata-import-field-form">
        <div className="managed-import-field-form-grid">
          <Form.Item label="字段编码" name="code" rules={[
            { required: true, whitespace: true, message: '请输入字段编码' },
            { pattern: /^[a-z][a-z0-9_]{0,63}$/, message: '须以小写字母开头，只能包含小写字母、数字和下划线' },
          ]}>
            <Input onChange={(event) => form.setFieldValue('code', event.target.value.toLowerCase())} />
          </Form.Item>
          <Form.Item label="字段名称" name="name" rules={[
            { required: true, whitespace: true, message: '请输入字段名称' },
            { max: 100, message: '不能超过 100 个字符' },
          ]}>
            <Input />
          </Form.Item>
          <Form.Item label="字段类型" name="fieldType" rules={[{ required: true, message: '请选择字段类型' }]}>
            <Select
              loading={capabilitiesQuery.isFetching}
              options={options}
              onChange={(value: PlatformDataType) => {
                form.setFieldValue('length', value === 'STRING' ? form.getFieldValue('length') : undefined);
                form.setFieldValue('precision', value === 'DECIMAL' ? form.getFieldValue('precision') ?? 18 : undefined);
                form.setFieldValue('scale', value === 'DECIMAL' ? form.getFieldValue('scale') ?? 2 : undefined);
                form.setFieldValue('geometry', value === 'GEOMETRY'
                  ? form.getFieldValue('geometry') ?? {
                    kind: 'POINT',
                    crs: { authority: 'EPSG', code: 4326 },
                    dimension: 'XY',
                  }
                  : undefined);
                if (value === 'GEOMETRY') form.setFieldValue('primaryKey', false);
              }}
            />
          </Form.Item>
          {selectedType === 'STRING' && (
            <Form.Item label="长度（可选）" name="length"><InputNumber min={1} precision={0} /></Form.Item>
          )}
          {selectedType === 'DECIMAL' && (
            <>
              <Form.Item label="精度" name="precision" rules={[{ required: true, message: '请输入精度' }]}>
                <InputNumber min={1} max={38} precision={0} />
              </Form.Item>
              <Form.Item label="小数位" name="scale" dependencies={['precision']} rules={[
                { required: true, message: '请输入小数位' },
                ({ getFieldValue }) => ({
                  validator: (_rule, value: number | undefined) => value !== undefined && value >= 0
                    && value <= (getFieldValue('precision') ?? 0)
                    ? Promise.resolve() : Promise.reject(new Error('小数位须为 0 至精度值')),
                }),
              ]}>
                <InputNumber min={0} max={38} precision={0} />
              </Form.Item>
            </>
          )}
          {selectedType === 'GEOMETRY' && (
            <>
              <Form.Item label="几何类型" name={['geometry', 'kind']} rules={[{ required: true, message: '请选择几何类型' }]}>
                <Select options={geometryKindOptions} />
              </Form.Item>
              <Form.Item label="CRS Authority" name={['geometry', 'crs', 'authority']} rules={[{ required: true }]}>
                <Input disabled />
              </Form.Item>
              <Form.Item label="EPSG Code" name={['geometry', 'crs', 'code']} rules={[{ required: true, message: '请输入 EPSG Code' }]}>
                <InputNumber min={1} precision={0} />
              </Form.Item>
              <Form.Item label="坐标维度" name={['geometry', 'dimension']} rules={[{ required: true }]}>
                <Select disabled options={[{ value: 'XY', label: 'XY（二维坐标）' }]} />
              </Form.Item>
            </>
          )}
          <Form.Item label="排序值" name="sortOrder" rules={[{ required: true, message: '请输入排序值' }]}>
            <InputNumber min={0} precision={0} />
          </Form.Item>
          <Form.Item label="允许为空" name="nullable" rules={[{ required: true, message: '请选择是否允许为空' }]}>
            <Select
              disabled={Boolean(primaryKey)}
              options={[{ value: true, label: '是' }, { value: false, label: '否' }]}
            />
          </Form.Item>
          <Form.Item label="主键" name="primaryKey" rules={[{ required: true, message: '请选择是否为主键' }]}>
            <Select
              disabled={selectedType === 'GEOMETRY'}
              options={[{ value: true, label: '是' }, { value: false, label: '否' }]}
              onChange={(checked: boolean) => checked && form.setFieldValue('nullable', false)}
            />
          </Form.Item>
          <Form.Item label="字段说明" name="description" className="managed-import-field-description" rules={[{ max: 500 }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
        </div>
      </Form>
    </Modal>
  );
};

export const ModelMetadataImportDrawer = ({
  open,
  canViewDirectories,
  initialDirectoryId,
  initialTargetStorageDataSourceId,
  onClose,
  onAdjustFields,
}: ModelMetadataImportDrawerProps) => {
  const [step, setStep] = useState(0);
  const [uploadFile, setUploadFile] = useState<UploadFile>();
  const [targetStorageDataSourceId, setTargetStorageDataSourceId] = useState(initialTargetStorageDataSourceId);
  const [directoryId, setDirectoryId] = useState(initialDirectoryId);
  const [fileIssues, setFileIssues] = useState<string[]>([]);
  const [drafts, setDrafts] = useState<ModelMetadataDraft[]>([]);
  const [results, setResults] = useState<ManagedDataModelDraftResult[]>([]);
  const [editingField, setEditingField] = useState<{ draftKey: string; fieldKey: string }>();
  const [modalApi, modalContext] = Modal.useModal();
  const [messageApi, messageContext] = message.useMessage();
  const dataSourcesQuery = useDataSources(dataSourceRequest, open);
  const directoriesQuery = useDirectoryTree('MODEL', open && canViewDirectories);
  const previewMutation = usePreviewModelMetadataImport();
  const templateMutation = useDownloadModelMetadataTemplate();
  const createMutation = useCreateManagedDataModelDrafts();
  const selectedTarget = dataSourcesQuery.data?.content.find((source) => (
    source.id === targetStorageDataSourceId && isManagedImportTargetSelectable(source)
  ));
  const targetIsClickHouse = selectedTarget?.type === 'CLICKHOUSE';
  const issues = useMemo(() => modelMetadataDraftIssues(drafts, targetIsClickHouse), [drafts, targetIsClickHouse]);
  const hasDraftIssues = hasModelMetadataDraftIssues(issues) || fileIssues.length > 0;
  const failedKeys = useMemo(() => new Set(
    results.filter((result) => !result.detail).map((result) => result.key),
  ), [results]);
  const lastFailureByKey = useMemo(() => new Map(
    results
      .filter((result) => !result.detail)
      .map((result) => [result.key, errorMessage(result.error, '创建失败')]),
  ), [results]);
  const busy = previewMutation.isPending || templateMutation.isPending || createMutation.isPending;
  const successCount = results.length - failedKeys.size;

  const targetOptions = dataSourcesQuery.data?.content
    .filter(isManagedImportTargetSelectable)
    .map((source) => ({ value: source.id, label: `${source.name}（${source.type}）` })) ?? [];
  const actualFile = uploadFile?.originFileObj ?? (uploadFile as File | undefined);

  const requestClose = () => {
    if (busy) return;
    if ((step < 2 && (uploadFile || drafts.length > 0)) || (step === 2 && failedKeys.size > 0)) {
      modalApi.confirm({
        title: '放弃本次 Excel 导入？',
        content: step === 2
          ? `仍有 ${failedKeys.size} 个模型创建失败，关闭后将丢失失败项及其调整内容。`
          : '已上传或调整的模型结构尚未创建，离开后不会保留。',
        okText: '放弃并关闭',
        okButtonProps: { danger: true },
        cancelText: '继续编辑',
        onOk: onClose,
      });
      return;
    }
    onClose();
  };

  const downloadTemplate = async () => {
    try {
      const blob = await templateMutation.mutateAsync();
      downloadBlob(blob, 'DataScalpel-模型元数据导入模板.xlsx');
      messageApi.success('Excel 模板下载已开始');
    } catch (error) {
      messageApi.error(errorMessage(error, '下载模板失败'));
    }
  };

  const loadPreview = async () => {
    if (!actualFile || !selectedTarget) return;
    const execute = async () => {
      try {
        const preview = await previewMutation.mutateAsync({
          file: actualFile,
          targetStorageDataSourceId: selectedTarget.id,
        });
        setFileIssues(preview.issues);
        setDrafts(modelMetadataDrafts(preview));
        setResults([]);
        setStep(1);
      } catch (error) {
        messageApi.error(errorMessage(error, '解析 Excel 失败'));
      }
    };
    if (drafts.length) {
      modalApi.confirm({
        title: '重新解析 Excel',
        content: '重新解析会覆盖当前模型和字段调整，确认继续吗？',
        okText: '重新解析',
        cancelText: '取消',
        onOk: execute,
      });
      return;
    }
    await execute();
  };

  const updateDraft = (
    key: string,
    field: 'code' | 'name' | 'physicalTableName' | 'description',
    value: string,
  ) => setDrafts((current) => current.map((draft) => draft.key === key
    ? { ...draft, [field]: value, serverIssues: [] }
    : draft));

  const updateOrderBy = (key: string, value: string) => setDrafts((current) => current.map((draft) => draft.key === key
    ? {
      ...draft,
      clickHouseOrderByColumns: value.split(',').map((item) => item.trim().toLowerCase()).filter(Boolean),
      serverIssues: [],
    }
    : draft));

  const currentField = editingField
    ? drafts.find((draft) => draft.key === editingField.draftKey)?.fields
      .find((field) => field.key === editingField.fieldKey) ?? null
    : null;

  const saveField = (field: ModelMetadataFieldDraft) => {
    if (!editingField) return;
    setDrafts((current) => current.map((draft) => draft.key === editingField.draftKey ? {
      ...draft,
      fields: draft.fields.map((candidate) => candidate.key === editingField.fieldKey ? field : candidate),
    } : draft));
    setEditingField(undefined);
  };

  const submit = async () => {
    if (!selectedTarget || hasDraftIssues) return;
    const items = drafts.flatMap((draft) => {
      const request = toManagedDraftRequest(draft, selectedTarget.id, directoryId);
      return request ? [{ key: draft.key, request }] : [];
    });
    if (items.length !== drafts.length) return;
    setResults(await createMutation.mutateAsync(items));
    setStep(2);
  };

  const editFailures = () => {
    setDrafts((current) => current.filter((draft) => failedKeys.has(draft.key)));
    setStep(1);
  };

  const fieldColumns = (draft: ModelMetadataDraft): TableProps<ModelMetadataFieldDraft>['columns'] => [
    { title: '字段编码', dataIndex: 'code', width: 150, render: (value: string) => value ? <code>{value}</code> : <Typography.Text type="danger">待补齐</Typography.Text> },
    { title: '字段名称', dataIndex: 'name', width: 160, ellipsis: true },
    { title: '类型', dataIndex: 'fieldType', width: 110, render: (value: PlatformDataType | null) => value ? dataModelFieldTypeLabels[value] : <Typography.Text type="danger">待选择</Typography.Text> },
    {
      title: '参数',
      width: 210,
      render: (_value, field) => {
        if (field.fieldType === 'STRING') return field.length ?? '—';
        if (field.fieldType === 'DECIMAL') return `${field.precision ?? '—'},${field.scale ?? '—'}`;
        if (field.fieldType === 'GEOMETRY' && field.geometry) {
          return `${geometryKindLabels[field.geometry.kind]} · ${field.geometry.crs.authority}:${field.geometry.crs.code} · ${field.geometry.dimension}`;
        }
        return '—';
      },
    },
    { title: '可空', dataIndex: 'nullable', width: 64, render: (value: boolean | null) => value === null ? '待选' : value ? '是' : '否' },
    { title: '主键', dataIndex: 'primaryKey', width: 64, render: (value: boolean | null) => value ? '是' : '—' },
    { title: '排序', dataIndex: 'sortOrder', width: 70 },
    {
      title: '校验', key: 'issues', width: 260,
      render: (_value, field) => {
        const current = issues.get(draft.key)?.fieldIssues.get(field.key) ?? [];
        return current.length ? <Typography.Text type="danger">{current.join('；')}</Typography.Text> : <Tag color="success">已就绪</Tag>;
      },
    },
    {
      title: '操作', key: 'actions', width: 80, fixed: 'right',
      render: (_value, field) => (
        <Button type="link" size="small" icon={<EditOutlined />} disabled={busy} onClick={() => setEditingField({ draftKey: draft.key, fieldKey: field.key })}>
          调整
        </Button>
      ),
    },
  ];

  const modelColumns: TableProps<ModelMetadataDraft>['columns'] = [
    {
      title: '模型编码', width: 190,
      render: (_value, draft) => <div><Input value={draft.code} status={issues.get(draft.key)?.code ? 'error' : undefined} onChange={(event) => updateDraft(draft.key, 'code', event.target.value.toLowerCase())} />{issues.get(draft.key)?.code && <Typography.Text type="danger" className="managed-import-field-error">{issues.get(draft.key)?.code}</Typography.Text>}</div>,
    },
    {
      title: '模型名称', width: 200,
      render: (_value, draft) => <div><Input value={draft.name} status={issues.get(draft.key)?.name ? 'error' : undefined} onChange={(event) => updateDraft(draft.key, 'name', event.target.value)} />{issues.get(draft.key)?.name && <Typography.Text type="danger" className="managed-import-field-error">{issues.get(draft.key)?.name}</Typography.Text>}</div>,
    },
    {
      title: '目标物理表名', width: 210,
      render: (_value, draft) => <div><Input value={draft.physicalTableName} status={issues.get(draft.key)?.physicalTableName ? 'error' : undefined} onChange={(event) => updateDraft(draft.key, 'physicalTableName', event.target.value.toLowerCase())} />{issues.get(draft.key)?.physicalTableName && <Typography.Text type="danger" className="managed-import-field-error">{issues.get(draft.key)?.physicalTableName}</Typography.Text>}</div>,
    },
    {
      title: 'ClickHouse 排序键', width: 210,
      render: (_value, draft) => <div><Input disabled={!targetIsClickHouse} value={draft.clickHouseOrderByColumns.join(',')} placeholder={targetIsClickHouse ? '字段编码，逗号分隔' : '仅 ClickHouse 使用'} onChange={(event) => updateOrderBy(draft.key, event.target.value)} />{issues.get(draft.key)?.clickHouseOrderByColumns && <Typography.Text type="danger" className="managed-import-field-error">{issues.get(draft.key)?.clickHouseOrderByColumns}</Typography.Text>}</div>,
    },
    {
      title: '模型说明', width: 220,
      render: (_value, draft) => <Input value={draft.description} status={issues.get(draft.key)?.description ? 'error' : undefined} onChange={(event) => updateDraft(draft.key, 'description', event.target.value)} />,
    },
    {
      title: '字段', width: 180,
      render: (_value, draft) => {
        const current = issues.get(draft.key);
        if (current?.server) return <Typography.Text type="danger">{current.server}</Typography.Text>;
        if (current?.fields) return <Typography.Text type="danger">{current.fields}</Typography.Text>;
        if (current?.fieldIssues.size) return <Typography.Text type="danger">{current.fieldIssues.size} 个字段待处理</Typography.Text>;
        const lastFailure = lastFailureByKey.get(draft.key);
        if (lastFailure) return <Typography.Text type="warning">上次创建失败：{lastFailure}</Typography.Text>;
        return <Tag color="success">{draft.fields.length} 个已就绪</Tag>;
      },
    },
  ];

  const resultColumns: TableProps<ManagedDataModelDraftResult>['columns'] = [
    { title: '模型编码', dataIndex: ['request', 'code'], width: 180, render: (value: string) => <code>{value}</code> },
    { title: '模型名称', dataIndex: ['request', 'name'], width: 200 },
    {
      title: '结果', render: (_value, result) => result.detail
        ? <Space><CheckCircleOutlined className="managed-import-success" />已创建 MANAGED 草稿</Space>
        : <Space><CloseCircleOutlined className="managed-import-failure" /><Typography.Text type="danger">{errorMessage(result.error, '创建失败')}</Typography.Text></Space>,
    },
    {
      title: '操作', width: 100, fixed: 'right',
      render: (_value, result) => result.detail ? (
        <Button type="link" size="small" disabled={failedKeys.size > 0} onClick={() => onAdjustFields(result.detail?.model.id ?? '')}>调整字段</Button>
      ) : '—',
    },
  ];

  const footer = step === 0 ? (
    <Space>
      <Button onClick={requestClose}>取消</Button>
      <Button type="primary" loading={previewMutation.isPending} disabled={!actualFile || !selectedTarget || busy} onClick={() => void loadPreview()}>
        解析并校对
      </Button>
    </Space>
  ) : step === 1 ? (
    <Space>
      <Button disabled={busy} onClick={() => setStep(0)}>上一步</Button>
      <Button type="primary" loading={createMutation.isPending} disabled={busy || drafts.length === 0 || hasDraftIssues} onClick={() => void submit()}>
        创建 {drafts.length} 个受管草稿
      </Button>
    </Space>
  ) : (
    <Space>
      {failedKeys.size > 0 && <Button onClick={editFailures}>修改并重试失败项</Button>}
      <Button type="primary" onClick={onClose}>完成</Button>
    </Space>
  );

  return (
    <>
      {modalContext}
      {messageContext}
      <Drawer
        title="从 Excel 导入模型元数据"
        open={open}
        size="large"
        className="managed-table-model-import-drawer"
        closable={!busy}
        maskClosable={!busy}
        destroyOnHidden
        onClose={requestClose}
        footer={footer}
      >
        <Steps size="small" current={step} items={[{ title: '上传 Excel' }, { title: '校对表结构' }, { title: '创建结果' }]} />
        {step === 0 && (
          <div className="managed-import-step-content metadata-import-upload-step">
            <Alert showIcon type="info" title="只导入模型和字段元数据，结果固定为 MANAGED + DRAFT，不绑定来源，也不会创建物理表。" />
            <Space>
              <Select showSearch optionFilterProp="label" value={selectedTarget?.id} loading={dataSourcesQuery.isFetching} options={targetOptions} placeholder="选择目标 STORAGE JDBC" className="managed-import-target-select" onChange={setTargetStorageDataSourceId} />
              {canViewDirectories && <TreeSelect allowClear treeDefaultExpandAll value={directoryId} treeData={directoryTreeSelectData(directoriesQuery.data ?? [])} placeholder="模型目录：未分类" className="managed-import-directory-select" onChange={setDirectoryId} />}
              <Button icon={<DownloadOutlined />} loading={templateMutation.isPending} onClick={() => void downloadTemplate()}>下载空白模板</Button>
            </Space>
            <Upload.Dragger
              accept=".xlsx"
              maxCount={1}
              fileList={uploadFile ? [uploadFile] : []}
              beforeUpload={(file) => { setUploadFile(file); setDrafts([]); setFileIssues([]); return false; }}
              onRemove={() => { setUploadFile(undefined); setDrafts([]); setFileIssues([]); }}
            >
              <p className="ant-upload-drag-icon"><InboxOutlined /></p>
              <p className="ant-upload-text">拖拽或点击选择 DataScalpel 模型元数据 Excel</p>
              <p className="ant-upload-hint">仅支持固定 .xlsx 模板，最大 10 MB</p>
            </Upload.Dragger>
          </div>
        )}
        {step === 1 && (
          <div className="managed-import-step-content">
            <Alert
              showIcon
              type={hasDraftIssues ? 'warning' : 'success'}
              title={hasDraftIssues ? '请处理标红内容；文件结构级问题需要修改 Excel 后重新上传。' : `${drafts.length} 个模型已就绪，创建草稿不会执行 DDL。`}
              description={fileIssues.length ? fileIssues.join('；') : undefined}
            />
            <Space>
              <Tag icon={<FileExcelOutlined />}>{uploadFile?.name}</Tag>
              <Tag>{selectedTarget?.name}</Tag>
              <Button disabled={busy} onClick={() => setStep(0)}>更换文件或目标</Button>
            </Space>
            <Table<ModelMetadataDraft>
              size="small"
              rowKey="key"
              columns={modelColumns}
              dataSource={drafts}
              pagination={false}
              scroll={{ x: 1300, y: 390 }}
              expandable={{
                rowExpandable: (draft) => draft.fields.length > 0,
                expandedRowRender: (draft) => (
                  <div className="managed-import-field-table-wrap">
                    {draft.warnings.length > 0 && <Alert showIcon type="warning" title={draft.warnings.join('；')} />}
                    <Table<ModelMetadataFieldDraft> size="small" rowKey="key" columns={fieldColumns(draft)} dataSource={draft.fields} pagination={false} scroll={{ x: 1050 }} />
                  </div>
                ),
              }}
            />
            {createMutation.isPending && <Alert showIcon icon={<LoadingOutlined />} type="info" title={`正在创建 ${drafts.length} 个草稿，最多同时处理 3 个…`} />}
          </div>
        )}
        {step === 2 && (
          <div className="managed-import-step-content">
            <Alert
              showIcon
              type={failedKeys.size === 0 ? 'success' : successCount > 0 ? 'warning' : 'error'}
              title={`成功 ${successCount} 个，失败 ${failedKeys.size} 个`}
              description="成功模型不会回滚；物理表仍需在模型详情中显式创建。"
            />
            <Table<ManagedDataModelDraftResult> size="small" rowKey="key" columns={resultColumns} dataSource={results} pagination={false} />
          </div>
        )}
      </Drawer>
      <MetadataFieldEditor field={currentField} storageDataSourceId={selectedTarget?.id} onCancel={() => setEditingField(undefined)} onSave={saveField} />
    </>
  );
};
