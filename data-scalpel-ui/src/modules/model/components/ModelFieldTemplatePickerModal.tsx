import { CopyOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Alert, Input, Modal, Select, Space, Table, Tag, Tooltip, Typography } from 'antd';
import { useMemo, useState } from 'react';
import type { StandardDictionarySummary } from '../../standard';
import { useCurrentUser } from '../../system';
import { useModelFieldTemplates, usePlatformTypeCapabilities } from '../hooks/useDataModels';
import {
  dataModelFieldTypeLabels,
  type GeometryTypeDefinition,
  type ModelFieldTemplate,
  type PlatformDataType,
  type PlatformTypeCapability,
} from '../model/dataModel';

export interface ModelFieldTemplateCopyField {
  code: string;
  name: string;
  fieldType: PlatformDataType;
  length?: number;
  precision?: number;
  scale?: number;
  geometry?: GeometryTypeDefinition;
  nullable: boolean;
  primaryKey: boolean;
  description?: string;
  standardDictionary?: StandardDictionarySummary | null;
}

interface ModelFieldTemplatePickerModalProps {
  open: boolean;
  storageDataSourceId: string;
  existingFieldCodes: string[];
  onCancel: () => void;
  onApply: (fields: ModelFieldTemplateCopyField[], skippedIssues: string[]) => void;
}

const fieldType = (field: ModelFieldTemplate['fields'][number]) => {
  if (field.fieldType === 'STRING') {
    return field.length ? `字符串(${field.length})` : '字符串(无上限)';
  }
  if (field.fieldType === 'DECIMAL') return `小数(${field.precision ?? '—'},${field.scale ?? 0})`;
  return dataModelFieldTypeLabels[field.fieldType];
};

const capabilityIssue = (
  field: ModelFieldTemplate['fields'][number],
  capability: PlatformTypeCapability | undefined,
) => {
  const typeName = dataModelFieldTypeLabels[field.fieldType];
  if (!capability) return `无法确认目标数据存储是否支持${typeName}`;
  if (!capability.supported) return capability.message || `目标数据存储不支持${typeName}`;
  if (field.fieldType === 'STRING') {
    if (field.length !== null && !capability.lengthParameterSupported) {
      return capability.message || '目标数据存储不支持带长度的字符串';
    }
    if (field.length === null && !capability.unboundedStringSupported) {
      return capability.message || '目标数据存储不支持无长度上限的字符串';
    }
  }
  if (field.fieldType === 'GEOMETRY' && field.geometry) {
    if (capability.geometryKinds?.length
      && !capability.geometryKinds.includes(field.geometry.kind)) {
      return `目标数据存储不支持几何类型 ${field.geometry.kind}`;
    }
    if (capability.coordinateDimensions?.length
      && !capability.coordinateDimensions.includes(field.geometry.dimension)) {
      return `目标数据存储不支持坐标维度${field.geometry.dimension}`;
    }
    if (capability.crsAuthorities?.length
      && !capability.crsAuthorities.includes(field.geometry.crs.authority)) {
      return `目标数据存储不支持 CRS ${field.geometry.crs.authority}`;
    }
  }
  return null;
};

export const ModelFieldTemplatePickerModal = ({
  open,
  storageDataSourceId,
  existingFieldCodes,
  onCancel,
  onApply,
}: ModelFieldTemplatePickerModalProps) => {
  const [selectedIds, setSelectedIds] = useState<React.Key[]>([]);
  const [keyword, setKeyword] = useState('');
  const [category, setCategory] = useState<string>();
  const currentUser = useCurrentUser();
  const canViewDictionaries = currentUser.data?.permissions.includes('standard.dictionary.view') ?? false;
  const templatesQuery = useModelFieldTemplates(
    { search: 'enabled:"true"', page: 0, size: 500, sort: 'category,sortOrder,name,code' },
    open,
  );
  const capabilitiesQuery = usePlatformTypeCapabilities(storageDataSourceId, open);
  const capabilities = useMemo(() => new Map(
    (capabilitiesQuery.data ?? []).map((capability) => [capability.type, capability]),
  ), [capabilitiesQuery.data]);
  const templates = useMemo(() => templatesQuery.data?.content ?? [], [templatesQuery.data?.content]);
  const categories = useMemo(() => [...new Set(
    templates.map((template) => template.category).filter((value): value is string => Boolean(value)),
  )].sort((left, right) => left.localeCompare(right, 'zh-CN')), [templates]);
  const visibleTemplates = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    return templates.filter((template) => (
      (!normalizedKeyword
        || template.code.toLowerCase().includes(normalizedKeyword)
        || template.name.toLowerCase().includes(normalizedKeyword)
        || template.fields.some((field) => (
          field.code.toLowerCase().includes(normalizedKeyword)
          || field.name.toLowerCase().includes(normalizedKeyword)
        )))
      && (!category || template.category === category)
    ));
  }, [category, keyword, templates]);

  const reset = () => {
    setSelectedIds([]);
    setKeyword('');
    setCategory(undefined);
  };

  const copyResult = useMemo(() => {
    const occupiedCodes = new Set(existingFieldCodes.map((code) => code.toLowerCase()));
    const fields: ModelFieldTemplateCopyField[] = [];
    const skippedIssues: string[] = [];
    for (const template of templates.filter((candidate) => selectedIds.includes(candidate.id))) {
      for (const field of template.fields) {
        const code = field.code.toLowerCase();
        if (occupiedCodes.has(code)) {
          skippedIssues.push(`${template.name} / ${field.code}：模型中已存在同编码字段`);
          continue;
        }
        const issue = capabilityIssue(field, capabilities.get(field.fieldType));
        if (issue) {
          skippedIssues.push(`${template.name} / ${field.code}：${issue}`);
          continue;
        }
        occupiedCodes.add(code);
        const dictionary = canViewDictionaries && field.standardDictionary?.enabled
          ? field.standardDictionary
          : null;
        if (field.standardDictionary && !canViewDictionaries) {
          skippedIssues.push(`${template.name} / ${field.code}：当前账号不能查看码表，字段会复制但码表绑定留空`);
        } else if (field.standardDictionary && !field.standardDictionary.enabled) {
          skippedIssues.push(`${template.name} / ${field.code}：关联码表已停用，字段会复制但码表绑定留空`);
        }
        fields.push({
          code,
          name: field.name,
          fieldType: field.fieldType,
          ...(field.length !== null ? { length: field.length } : {}),
          ...(field.precision !== null ? { precision: field.precision } : {}),
          ...(field.scale !== null ? { scale: field.scale } : {}),
          ...(field.geometry ? { geometry: field.geometry } : {}),
          nullable: field.nullable,
          primaryKey: field.primaryKey,
          ...(field.description ? { description: field.description } : {}),
          standardDictionary: dictionary,
        });
      }
    }
    return { fields, skippedIssues };
  }, [canViewDictionaries, capabilities, existingFieldCodes, selectedIds, templates]);

  const columns: TableProps<ModelFieldTemplate>['columns'] = [
    {
      title: '模板',
      key: 'template',
      width: 230,
      render: (_value, template) => (
        <Space direction="vertical" size={0}>
          <Typography.Text>{template.name}</Typography.Text>
          <Typography.Text type="secondary"><code>{template.code}</code></Typography.Text>
        </Space>
      ),
    },
    { title: '分类', dataIndex: 'category', width: 130, render: (value: string | null) => value || '未分类' },
    {
      title: '字段',
      key: 'fields',
      render: (_value, template) => (
        <Space size={[4, 4]} wrap>
          {template.fields.map((field) => {
            const capability = capabilities.get(field.fieldType);
            const issue = capabilityIssue(field, capability);
            return (
              <Tooltip
                key={field.id}
                title={issue
                  ? issue
                  : `${field.name} · ${fieldType(field)}`}
              >
                <Tag color={issue ? 'error' : undefined}><code>{field.code}</code></Tag>
              </Tooltip>
            );
          })}
        </Space>
      ),
    },
    { title: '说明', dataIndex: 'description', width: 220, ellipsis: true, render: (value: string | null) => value || '—' },
  ];

  return (
    <Modal
      title="从常用字段模板添加"
      open={open}
      width={900}
      destroyOnHidden
      onCancel={onCancel}
      afterClose={reset}
      okText={`添加 ${copyResult.fields.length} 个字段`}
      cancelText="取消"
      okButtonProps={{
        icon: <CopyOutlined />,
        disabled: copyResult.fields.length === 0 || capabilitiesQuery.isFetching || capabilitiesQuery.isError,
      }}
      onOk={() => onApply(copyResult.fields, copyResult.skippedIssues)}
    >
      <Alert
        showIcon
        type="info"
        title="选择模板后复制字段快照；同编码字段和目标存储不支持的字段不会加入，停用或无权查看的码表绑定会留空。"
        className="management-inline-alert"
      />
      {templatesQuery.isError && (
        <Alert showIcon type="error" title="常用字段模板加载失败" description="请关闭后重试。" />
      )}
      {capabilitiesQuery.isError && (
        <Alert
          showIcon
          type="error"
          title="无法确认目标数据存储支持的字段类型"
          description="为避免带入不支持的结构，当前不能复制，请检查数据存储连接后重试。"
          style={{ marginBottom: 8 }}
        />
      )}
      <Space size={8} style={{ marginBottom: 8 }}>
        <Input
          allowClear
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          placeholder="搜索模板或字段"
          style={{ width: 240 }}
        />
        <Select
          allowClear
          value={category}
          onChange={setCategory}
          placeholder="全部分类"
          options={categories.map((value) => ({ value, label: value }))}
          style={{ width: 180 }}
        />
        <Typography.Text type="secondary">
          已选 {selectedIds.length} 个模板，可添加 {copyResult.fields.length} 个字段
        </Typography.Text>
      </Space>
      {copyResult.skippedIssues.length > 0 && (
        <Alert
          showIcon
          type="warning"
          title={`有 ${copyResult.skippedIssues.length} 项不能原样带入`}
          description={(
            <Space direction="vertical" size={0}>
              {copyResult.skippedIssues.slice(0, 6).map((issue) => <span key={issue}>{issue}</span>)}
              {copyResult.skippedIssues.length > 6 && <span>另有 {copyResult.skippedIssues.length - 6} 项</span>}
            </Space>
          )}
          style={{ marginBottom: 8 }}
        />
      )}
      <Table<ModelFieldTemplate>
        size="small"
        rowKey="id"
        columns={columns}
        dataSource={visibleTemplates}
        loading={templatesQuery.isFetching || capabilitiesQuery.isFetching}
        scroll={{ x: 900, y: 420 }}
        pagination={false}
        rowSelection={{
          selectedRowKeys: selectedIds,
          preserveSelectedRowKeys: true,
          onChange: setSelectedIds,
        }}
      />
    </Modal>
  );
};
