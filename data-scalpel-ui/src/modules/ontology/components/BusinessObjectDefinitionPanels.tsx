import {
  ApiOutlined,
  ApartmentOutlined,
  ArrowDownOutlined,
  ArrowUpOutlined,
  DatabaseOutlined,
  DeleteOutlined,
  EditOutlined,
  FolderOutlined,
  LinkOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import {
  Button,
  Collapse,
  Empty,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { useEffect, useRef, useState, type Key, type ReactNode } from 'react';
import { BusinessDetailSection } from '../../../shared/components/BusinessDetailSection';
import { ContextHelp } from '../../../shared/components/ContextualFeedback';
import { ManagementCode, ManagementListCell } from '../../../shared/components/ManagementListCells';
import { buildDataModelSearch, useDataModel, useDataModels, type DataModel } from '../../model';
import {
  capabilityKindLabels,
  relationCardinalityLabels,
  type BusinessObjectCapability,
  type BusinessObjectCapabilityField,
  type BusinessObjectFilter,
  type BusinessObjectProperty,
  type BusinessObjectPropertyGroup,
  type BusinessObjectRelation,
  type BusinessObjectRelationSummary,
  type BusinessObjectSupplementSource,
  type BusinessObjectTypeDefinition,
} from '../model/businessObjectType';

const newId = () => crypto.randomUUID();
const simpleCode = (value: string) => value.trim().toLowerCase()
  .replace(/[^a-z0-9]+/g, '_')
  .replace(/^_+|_+$/g, '')
  .replace(/^[^a-z]+/, '') || `field_${Date.now()}`;
const sourceFilterOperators = ['EQ', 'NE', 'GT', 'GE', 'LT', 'LE', 'LIKE', 'IS_NULL', 'IS_NOT_NULL'];
const sourceFilterOperatorLabels: Record<string, string> = {
  EQ: '等于', NE: '不等于', GT: '大于', GE: '大于等于', LT: '小于', LE: '小于等于',
  LIKE: '匹配', IS_NULL: '为空', IS_NOT_NULL: '不为空',
};
const platformDataTypes = ['BOOLEAN', 'BYTE', 'SHORT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL', 'STRING', 'DATE', 'TIMESTAMP', 'TIMESTAMP_NTZ'];
const SEARCH_DELAY_MS = 300;

const uniqueModels = (models: DataModel[]) => [...new Map(models.map((model) => [model.id, model])).values()];

export const PublishedModelSelect = ({ value, onChange, disabled, placeholder = '选择已发布模型' }: {
  value: string | null | undefined;
  onChange: (value: string | null) => void;
  disabled?: boolean;
  placeholder?: string;
}) => {
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const timerRef = useRef<number | null>(null);
  const selectedQuery = useDataModel(value ?? undefined, Boolean(value));
  const modelsQuery = useDataModels({
    search: buildDataModelSearch({ keyword, status: 'PUBLISHED' }),
    page: 0,
    size: 50,
    sort: 'name,code',
  }, open);

  useEffect(() => () => {
    if (timerRef.current !== null) window.clearTimeout(timerRef.current);
  }, []);

  const selected = selectedQuery.data?.model;
  const models = uniqueModels([
    ...(selected ? [selected] : []),
    ...(modelsQuery.data?.content ?? []).filter((model) => model.status === 'PUBLISHED'),
  ]);
  const modelById = new Map(models.map((model) => [model.id, model]));
  const options = models.map((model) => ({
    value: model.id,
    label: `${model.name}（${model.code}）`,
    disabled: model.status !== 'PUBLISHED',
  }));
  if (value && !modelById.has(value)) options.push({ value, label: `不可用模型（${value}）`, disabled: true });

  return (
    <Select<string>
      showSearch
      allowClear
      virtual
      value={value ?? undefined}
      open={open}
      disabled={disabled}
      placeholder={placeholder}
      filterOption={false}
      loading={modelsQuery.isFetching || selectedQuery.isFetching}
      options={options}
      popupMatchSelectWidth={440}
      onOpenChange={setOpen}
      onSearch={(next) => {
        if (timerRef.current !== null) window.clearTimeout(timerRef.current);
        timerRef.current = window.setTimeout(() => {
          setKeyword(next.trim());
          timerRef.current = null;
        }, SEARCH_DELAY_MS);
      }}
      onChange={(next) => onChange(next ?? null)}
      optionRender={(option) => {
        const model = modelById.get(String(option.value));
        if (!model) return option.label;
        return (
          <ManagementListCell
            primary={model.name}
            secondary={<ManagementCode value={`${model.code} · ${model.storageDataSourceName ?? '未知数据源'} · ${model.physicalTableName ?? '未知物理表'}`} />}
          />
        );
      }}
      notFoundContent={modelsQuery.isFetching ? '正在搜索…' : '没有匹配的已发布模型'}
    />
  );
};

export const ModelFieldSelect = ({ modelId, value, onChange, allowClear = true, placeholder = '选择字段', disabled }: {
  modelId: string | null | undefined;
  value: string | null | undefined;
  onChange: (value: string | null) => void;
  allowClear?: boolean;
  placeholder?: string;
  disabled?: boolean;
}) => {
  const fields = useDataModel(modelId ?? '', Boolean(modelId));
  return (
    <Select
      value={value ?? undefined}
      showSearch
      optionFilterProp="label"
      allowClear={allowClear}
      placeholder={modelId ? placeholder : '请先选择模型'}
      disabled={disabled || !modelId}
      loading={fields.isFetching}
      onChange={(next) => onChange(next ?? null)}
      options={(fields.data?.fields ?? []).map((field) => ({
        value: field.id,
        disabled: ['BINARY', 'GEOMETRY'].includes(field.fieldType),
        label: `${field.name}（${field.code}） · ${field.fieldType}${['BINARY', 'GEOMETRY'].includes(field.fieldType) ? '（暂不支持）' : ''}`,
      }))}
    />
  );
};

export const FieldName = ({ modelId, fieldId }: {
  modelId: string | null | undefined;
  fieldId: string | null | undefined;
}) => {
  const detail = useDataModel(modelId ?? '', Boolean(modelId));
  const field = detail.data?.fields.find((item) => item.id === fieldId);
  if (detail.isFetching && !detail.data) return <Typography.Text type="secondary">正在读取字段…</Typography.Text>;
  return field
    ? <ManagementListCell primary={field.name} secondary={<ManagementCode value={`${field.code} · ${field.fieldType}`} />} />
    : <Typography.Text type="secondary">{modelId ? '字段未配置或不可用' : '未配置'}</Typography.Text>;
};

const OntologyField = ({ label, help, children, wide }: {
  label: string;
  help?: ReactNode;
  children: ReactNode;
  wide?: boolean;
}) => (
  <label className={wide ? 'ontology-field ontology-field-wide' : 'ontology-field'}>
    <span className="ontology-field-label">{label}{help}</span>
    {children}
  </label>
);

const FilterEditor = ({ modelId, filters, onChange, disabled }: {
  modelId: string | null | undefined;
  filters: BusinessObjectFilter[];
  onChange: (next: BusinessObjectFilter[]) => void;
  disabled?: boolean;
}) => (
  <div className="ontology-filter-editor">
    {filters.length === 0 && <Typography.Text type="secondary">未设置固定筛选，将读取模型中的全部有效记录。</Typography.Text>}
    {filters.map((filter, index) => (
      <div className="ontology-filter-row" key={`${filter.fieldId}-${index}`}>
        <ModelFieldSelect
          modelId={modelId}
          value={filter.fieldId}
          disabled={disabled}
          onChange={(fieldId) => onChange(filters.map((item, position) => position === index ? { ...item, fieldId } : item))}
        />
        <Select
          value={filter.operator}
          disabled={disabled}
          options={sourceFilterOperators.map((operator) => ({ value: operator, label: sourceFilterOperatorLabels[operator] }))}
          onChange={(operator) => onChange(filters.map((item, position) => position === index ? { ...item, operator } : item))}
        />
        {!['IS_NULL', 'IS_NOT_NULL'].includes(filter.operator) && (
          <Input
            autoComplete="off"
            value={filter.value == null ? '' : String(filter.value)}
            disabled={disabled}
            placeholder="筛选值"
            onChange={(event) => onChange(filters.map((item, position) => position === index ? { ...item, value: event.target.value } : item))}
          />
        )}
        <Tooltip title="删除筛选条件">
          <Button
            type="text"
            danger
            icon={<DeleteOutlined />}
            disabled={disabled}
            aria-label="删除筛选条件"
            onClick={() => onChange(filters.filter((_, position) => position !== index))}
          />
        </Tooltip>
      </div>
    ))}
    <Button
      type="text"
      size="small"
      icon={<PlusOutlined />}
      disabled={disabled || !modelId}
      onClick={() => onChange([...filters, { fieldId: null, operator: 'EQ', value: '' }])}
    >
      添加筛选条件
    </Button>
  </div>
);

const SupplementEditor = ({ source, mainModelId, onChange, disabled }: {
  source: BusinessObjectSupplementSource;
  mainModelId: string | null | undefined;
  onChange: (next: BusinessObjectSupplementSource) => void;
  disabled?: boolean;
}) => (
  <div className="ontology-supplement-editor">
    <div className="ontology-form-grid ontology-form-grid-3">
      <OntologyField label="来源名称">
        <Input autoComplete="off" value={source.name ?? ''} disabled={disabled} maxLength={100} placeholder="例如：人员组织信息" onChange={(event) => onChange({ ...source, name: event.target.value || null })} />
      </OntologyField>
      <OntologyField label="来源模型">
        <PublishedModelSelect value={source.modelId} disabled={disabled} onChange={(modelId) => onChange({ ...source, modelId, dataTimeFieldId: null, keyMappings: [] })} />
      </OntologyField>
      <OntologyField label="数据时间字段" help={<ContextHelp ariaLabel="数据时间字段说明" content="用于说明属性值对应的业务时间；留空时预览显示未知。" />}>
        <ModelFieldSelect modelId={source.modelId} value={source.dataTimeFieldId} disabled={disabled} onChange={(dataTimeFieldId) => onChange({ ...source, dataTimeFieldId })} />
      </OntologyField>
    </div>
    <div className="ontology-subsection-title">与主来源的等值匹配</div>
    <div className="ontology-mapping-list">
      {source.keyMappings.length === 0 && <Typography.Text type="secondary">尚未设置匹配字段。</Typography.Text>}
      {source.keyMappings.map((mapping, index) => (
        <div className="ontology-mapping-row" key={index}>
          <ModelFieldSelect modelId={mainModelId} value={mapping.mainFieldId} disabled={disabled} placeholder="主来源字段" onChange={(mainFieldId) => onChange({ ...source, keyMappings: source.keyMappings.map((item, position) => position === index ? { ...item, mainFieldId } : item) })} />
          <span className="ontology-mapping-equals">等于</span>
          <ModelFieldSelect modelId={source.modelId} value={mapping.supplementFieldId} disabled={disabled} placeholder="补充来源字段" onChange={(supplementFieldId) => onChange({ ...source, keyMappings: source.keyMappings.map((item, position) => position === index ? { ...item, supplementFieldId } : item) })} />
          <Tooltip title="删除匹配字段"><Button type="text" danger icon={<DeleteOutlined />} disabled={disabled} aria-label="删除匹配字段" onClick={() => onChange({ ...source, keyMappings: source.keyMappings.filter((_, position) => position !== index) })} /></Tooltip>
        </div>
      ))}
      <Button type="text" size="small" icon={<PlusOutlined />} disabled={disabled || !mainModelId || !source.modelId} onClick={() => onChange({ ...source, keyMappings: [...source.keyMappings, { mainFieldId: null, supplementFieldId: null }] })}>添加匹配字段</Button>
    </div>
    <div className="ontology-subsection-title">固定筛选（AND）</div>
    <FilterEditor modelId={source.modelId} filters={source.fixedFilters} disabled={disabled} onChange={(fixedFilters) => onChange({ ...source, fixedFilters })} />
  </div>
);

export const SourcesPanel = ({ definition, canManage, onChange }: {
  definition: BusinessObjectTypeDefinition;
  canManage: boolean;
  onChange: (next: BusinessObjectTypeDefinition) => void;
}) => {
  const main = definition.mainSource;
  return (
    <div className="ontology-tab-stack">
      <BusinessDetailSection
        title="主来源"
        description="决定哪些记录构成业务对象，并提供对象身份。"
        icon={<DatabaseOutlined />}
      >
        <div className="ontology-form-grid ontology-form-grid-3">
          <OntologyField label="已发布数据模型">
            <PublishedModelSelect
              value={main?.modelId}
              disabled={!canManage}
              placeholder="搜索名称或编码"
              onChange={(modelId) => onChange({
                ...definition,
                mainSource: modelId ? { modelId, identityFieldId: null, titleFieldId: null, fixedFilters: [] } : null,
              })}
            />
          </OntologyField>
          <OntologyField label="唯一标识字段" help={<ContextHelp ariaLabel="唯一标识字段说明" content="选择稳定且唯一的文本或整数字段。它与对象类型共同确定对象身份。" />}>
            <ModelFieldSelect modelId={main?.modelId} value={main?.identityFieldId} disabled={!canManage} onChange={(identityFieldId) => main && onChange({ ...definition, mainSource: { ...main, identityFieldId } })} />
          </OntologyField>
          <OntologyField label="显示名称字段">
            <ModelFieldSelect modelId={main?.modelId} value={main?.titleFieldId} disabled={!canManage} onChange={(titleFieldId) => main && onChange({ ...definition, mainSource: { ...main, titleFieldId } })} />
          </OntologyField>
        </div>
        {main && (
          <div className="ontology-filter-surface">
            <div className="ontology-subsection-title">固定筛选（所有条件同时满足）</div>
            <FilterEditor modelId={main.modelId} filters={main.fixedFilters} disabled={!canManage} onChange={(fixedFilters) => onChange({ ...definition, mainSource: { ...main, fixedFilters } })} />
          </div>
        )}
      </BusinessDetailSection>

      <BusinessDetailSection
        title="补充来源"
        description="补充对象属性；每个来源对同一对象最多匹配一条记录。"
        icon={<LinkOutlined />}
        extra={<Button type="primary" ghost icon={<PlusOutlined />} disabled={!canManage || !main} onClick={() => onChange({ ...definition, supplements: [...definition.supplements, { id: newId(), name: null, modelId: null, keyMappings: [], fixedFilters: [], dataTimeFieldId: null }] })}>添加来源</Button>}
      >
        {definition.supplements.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前对象只使用主来源" />
        ) : (
          <Collapse
            className="ontology-editor-collapse"
            items={definition.supplements.map((source, index) => ({
              key: source.id,
              label: (
                <div className="ontology-collapse-summary">
                  <strong>{source.name || `补充来源 ${index + 1}`}</strong>
                  <span>{source.modelId ? <PublishedModelName modelId={source.modelId} /> : '尚未选择模型'}</span>
                  <Tag>{source.keyMappings.length} 组匹配</Tag>
                </div>
              ),
              extra: canManage ? (
                <Popconfirm title={`移除“${source.name || `补充来源 ${index + 1}`}”？`} onConfirm={() => onChange({ ...definition, supplements: definition.supplements.filter((_, position) => position !== index) })}>
                  <Button type="text" danger icon={<DeleteOutlined />} aria-label={`移除${source.name || '补充来源'}`} onClick={(event) => event.stopPropagation()} />
                </Popconfirm>
              ) : null,
              children: <SupplementEditor source={source} mainModelId={main?.modelId} disabled={!canManage} onChange={(next) => onChange({ ...definition, supplements: definition.supplements.map((item, position) => position === index ? next : item) })} />,
            }))}
          />
        )}
      </BusinessDetailSection>
    </div>
  );
};

const PublishedModelName = ({ modelId }: { modelId: string }) => {
  const model = useDataModel(modelId, true);
  return <>{model.data?.model.name ?? '正在读取模型…'}</>;
};

export const PropertiesPanel = ({ definition, canManage, onChange }: {
  definition: BusinessObjectTypeDefinition;
  canManage: boolean;
  onChange: (next: BusinessObjectTypeDefinition) => void;
}) => {
  const [sourceModelId, setSourceModelId] = useState<string | null>(null);
  const [fieldIds, setFieldIds] = useState<string[]>([]);
  const [selectedRows, setSelectedRows] = useState<Key[]>([]);
  const [propertyDraft, setPropertyDraft] = useState<BusinessObjectProperty | null>(null);
  const [groupDraft, setGroupDraft] = useState<BusinessObjectPropertyGroup | null>(null);
  const [newGroup, setNewGroup] = useState(false);
  const sourceModel = useDataModel(sourceModelId ?? '', Boolean(sourceModelId));
  const groups = [...definition.groups].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
  const sourceOptions = [
    ...(definition.mainSource?.modelId ? [{ value: definition.mainSource.modelId, label: '主来源' }] : []),
    ...definition.supplements.filter((source) => source.modelId).map((source) => ({ value: source.modelId as string, label: `补充来源 · ${source.name || '未命名'}` })),
  ];

  const addProperties = () => {
    if (!sourceModelId) return;
    const codes = new Set(definition.properties.map((property) => property.code));
    const added = (sourceModel.data?.fields ?? [])
      .filter((field) => fieldIds.includes(field.id) && !definition.properties.some((property) => property.sourceModelId === sourceModelId && property.fieldId === field.id))
      .map((field, index) => {
        const base = simpleCode(field.code);
        let code = base;
        let suffix = 2;
        while (codes.has(code)) code = `${base}_${suffix++}`;
        codes.add(code);
        return {
          id: newId(), code, name: field.name, description: field.description, unit: null, groupId: null,
          sourceModelId, fieldId: field.id, sortOrder: definition.properties.length + index,
        };
      });
    onChange({ ...definition, properties: [...definition.properties, ...added] });
    setFieldIds([]);
  };

  const saveGroup = () => {
    if (!groupDraft?.name.trim()) return;
    if (newGroup) onChange({ ...definition, groups: [...definition.groups, { ...groupDraft, name: groupDraft.name.trim() }] });
    else onChange({ ...definition, groups: definition.groups.map((group) => group.id === groupDraft.id ? { ...groupDraft, name: groupDraft.name.trim() } : group) });
    setGroupDraft(null);
  };

  const moveGroup = (groupId: string, offset: number) => {
    const index = groups.findIndex((group) => group.id === groupId);
    const target = index + offset;
    if (index < 0 || target < 0 || target >= groups.length) return;
    const reordered = [...groups];
    [reordered[index], reordered[target]] = [reordered[target], reordered[index]];
    onChange({ ...definition, groups: reordered.map((group, sortOrder) => ({ ...group, sortOrder })) });
  };

  return (
    <div className="ontology-tab-stack">
      <BusinessDetailSection title="导入属性" description="从已配置来源选择需要进入业务对象的字段。" icon={<SearchOutlined />}>
        <div className="ontology-property-import">
          <Select value={sourceModelId ?? undefined} disabled={!canManage} placeholder="选择来源" options={sourceOptions} onChange={(value) => { setSourceModelId(value ?? null); setFieldIds([]); }} />
          <Select
            mode="multiple"
            showSearch
            optionFilterProp="label"
            maxTagCount="responsive"
            value={fieldIds}
            disabled={!canManage || !sourceModelId}
            placeholder="搜索并选择字段"
            onChange={setFieldIds}
            options={(sourceModel.data?.fields ?? []).map((field) => ({
              value: field.id,
              label: `${field.name}（${field.code}） · ${field.fieldType}`,
              disabled: ['BINARY', 'GEOMETRY'].includes(field.fieldType) || definition.properties.some((property) => property.sourceModelId === sourceModelId && property.fieldId === field.id),
            }))}
          />
          <Button type="primary" icon={<PlusOutlined />} disabled={!canManage || fieldIds.length === 0} onClick={addProperties}>加入属性</Button>
        </div>
        <Typography.Text type="secondary">只需导入业务需要的字段；BINARY、GEOMETRY 暂不支持。</Typography.Text>
      </BusinessDetailSection>

      <BusinessDetailSection title="属性组" description="组织当前对象内部的属性，不产生新的对象或关系。" icon={<FolderOutlined />} extra={<Button type="primary" ghost icon={<PlusOutlined />} disabled={!canManage} onClick={() => { setNewGroup(true); setGroupDraft({ id: newId(), name: '', sortOrder: groups.length }); }}>新建分组</Button>}>
        {groups.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未创建属性组" /> : (
          <div className="ontology-group-grid">
            {groups.map((group, index) => {
              const count = definition.properties.filter((property) => property.groupId === group.id).length;
              return (
                <div className="ontology-group-card" key={group.id}>
                  <div><strong>{group.name}</strong><span>{count} 个属性</span></div>
                  <Space size={0}>
                    <Tooltip title="上移"><Button type="text" size="small" icon={<ArrowUpOutlined />} disabled={!canManage || index === 0} aria-label={`上移${group.name}`} onClick={() => moveGroup(group.id, -1)} /></Tooltip>
                    <Tooltip title="下移"><Button type="text" size="small" icon={<ArrowDownOutlined />} disabled={!canManage || index === groups.length - 1} aria-label={`下移${group.name}`} onClick={() => moveGroup(group.id, 1)} /></Tooltip>
                    <Tooltip title="重命名"><Button type="text" size="small" icon={<EditOutlined />} disabled={!canManage} aria-label={`编辑${group.name}`} onClick={() => { setNewGroup(false); setGroupDraft({ ...group }); }} /></Tooltip>
                    <Popconfirm title={`删除属性组“${group.name}”？`} description="组内属性将移入未分组。" onConfirm={() => onChange({ ...definition, groups: definition.groups.filter((item) => item.id !== group.id), properties: definition.properties.map((property) => property.groupId === group.id ? { ...property, groupId: null } : property) })}>
                      <Tooltip title="删除分组"><Button type="text" size="small" danger icon={<DeleteOutlined />} disabled={!canManage} aria-label={`删除${group.name}`} /></Tooltip>
                    </Popconfirm>
                  </Space>
                </div>
              );
            })}
          </div>
        )}
      </BusinessDetailSection>

      <BusinessDetailSection
        title={`业务属性（${definition.properties.length}）`}
        description="默认以阅读态展示；编辑业务名称、单位和分组时打开单项窗口。"
        icon={<DatabaseOutlined />}
        extra={selectedRows.length > 0 ? (
          <Space>
            <Typography.Text type="secondary">已选 {selectedRows.length} 项</Typography.Text>
            <Select
              placeholder="批量移动到分组"
              style={{ width: 180 }}
              disabled={!canManage}
              options={[{ value: '', label: '未分组' }, ...groups.map((group) => ({ value: group.id, label: group.name }))]}
              onChange={(groupId) => {
                onChange({ ...definition, properties: definition.properties.map((property) => selectedRows.includes(property.id) ? { ...property, groupId: groupId || null } : property) });
                setSelectedRows([]);
              }}
            />
          </Space>
        ) : undefined}
      >
        <Table
          className="ontology-properties-table"
          size="small"
          rowKey="id"
          dataSource={[...definition.properties].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))}
          rowSelection={canManage ? { selectedRowKeys: selectedRows, onChange: setSelectedRows } : undefined}
          pagination={definition.properties.length > 20 ? { pageSize: 20, showSizeChanger: true, pageSizeOptions: [20, 50, 100], showTotal: (total) => `共 ${total} 个属性` } : false}
          columns={[
            {
              title: '业务属性', key: 'identity', width: 250,
              render: (_, property) => (
                <ManagementListCell
                  primary={<Space size={5} wrap={false}><span>{property.name}</span>{definition.mainSource?.modelId === property.sourceModelId && definition.mainSource.identityFieldId === property.fieldId && <Tag color="blue">唯一标识</Tag>}{definition.mainSource?.modelId === property.sourceModelId && definition.mainSource.titleFieldId === property.fieldId && <Tag color="purple">显示名称</Tag>}</Space>}
                  secondary={<ManagementCode value={property.code} />}
                />
              ),
            },
            { title: '来源字段', key: 'field', width: 250, render: (_, property) => <FieldName modelId={property.sourceModelId} fieldId={property.fieldId} /> },
            { title: '属性组', key: 'group', width: 140, render: (_, property) => groups.find((group) => group.id === property.groupId)?.name ?? '未分组' },
            { title: '单位', dataIndex: 'unit', width: 100, render: (unit) => unit || '—' },
            { title: '说明', dataIndex: 'description', ellipsis: true, render: (description) => description || '—' },
            {
              title: '操作', key: 'actions', width: 84, fixed: 'right',
              render: (_, property) => (
                <Space size={0}>
                  <Tooltip title="编辑属性"><Button type="text" icon={<EditOutlined />} disabled={!canManage} aria-label={`编辑属性${property.name}`} onClick={() => setPropertyDraft({ ...property })} /></Tooltip>
                  <Popconfirm title={`移除业务属性“${property.name}”？`} description="不会删除来源模型字段。" onConfirm={() => onChange({ ...definition, properties: definition.properties.filter((item) => item.id !== property.id) })}>
                    <Tooltip title="移除属性"><Button type="text" danger icon={<DeleteOutlined />} disabled={!canManage} aria-label={`移除属性${property.name}`} /></Tooltip>
                  </Popconfirm>
                </Space>
              ),
            },
          ]}
        />
      </BusinessDetailSection>

      <Modal open={Boolean(groupDraft)} title={newGroup ? '新建属性组' : '编辑属性组'} okText="保存" cancelText="取消" okButtonProps={{ disabled: !groupDraft?.name.trim() }} onCancel={() => setGroupDraft(null)} onOk={saveGroup}>
        <OntologyField label="分组名称"><Input autoComplete="off" autoFocus value={groupDraft?.name ?? ''} maxLength={100} onChange={(event) => groupDraft && setGroupDraft({ ...groupDraft, name: event.target.value })} /></OntologyField>
      </Modal>

      <Modal open={Boolean(propertyDraft)} title="编辑业务属性" okText="保存" cancelText="取消" okButtonProps={{ disabled: !propertyDraft?.name.trim() || !propertyDraft?.code.trim() }} onCancel={() => setPropertyDraft(null)} onOk={() => {
        if (!propertyDraft) return;
        onChange({ ...definition, properties: definition.properties.map((property) => property.id === propertyDraft.id ? { ...propertyDraft, name: propertyDraft.name.trim(), code: simpleCode(propertyDraft.code) } : property) });
        setPropertyDraft(null);
      }}>
        {propertyDraft && <div className="ontology-modal-fields">
          <OntologyField label="业务名称"><Input autoComplete="off" value={propertyDraft.name} maxLength={100} onChange={(event) => setPropertyDraft({ ...propertyDraft, name: event.target.value })} /></OntologyField>
          <OntologyField label="属性编码"><Input autoComplete="off" value={propertyDraft.code} maxLength={64} onChange={(event) => setPropertyDraft({ ...propertyDraft, code: event.target.value })} /></OntologyField>
          <OntologyField label="属性组"><Select allowClear value={propertyDraft.groupId ?? undefined} placeholder="未分组" options={groups.map((group) => ({ value: group.id, label: group.name }))} onChange={(groupId) => setPropertyDraft({ ...propertyDraft, groupId: groupId ?? null })} /></OntologyField>
          <OntologyField label="单位"><Input autoComplete="off" value={propertyDraft.unit ?? ''} maxLength={32} placeholder="例如：m" onChange={(event) => setPropertyDraft({ ...propertyDraft, unit: event.target.value || null })} /></OntologyField>
          <OntologyField label="说明" wide><Input.TextArea autoComplete="off" rows={3} value={propertyDraft.description ?? ''} maxLength={2000} onChange={(event) => setPropertyDraft({ ...propertyDraft, description: event.target.value || null })} /></OntologyField>
        </div>}
      </Modal>
    </div>
  );
};

const RelationEditor = ({ relation, definition, targets, disabled, onChange }: {
  relation: BusinessObjectRelation;
  definition: BusinessObjectTypeDefinition;
  targets: { id: string; name: string; definition: BusinessObjectTypeDefinition }[];
  disabled?: boolean;
  onChange: (next: BusinessObjectRelation) => void;
}) => {
  const target = targets.find((item) => item.id === relation.targetObjectTypeId);
  return (
    <div className="ontology-relation-editor">
      <div className="ontology-relation-editor-column">
        <div className="ontology-subsection-title">业务含义与数量</div>
        <OntologyField label="当前对象到目标对象"><Input autoComplete="off" disabled={disabled} value={relation.forwardName} maxLength={100} placeholder="例如：所属部门" onChange={(event) => onChange({ ...relation, forwardName: event.target.value })} /></OntologyField>
        <OntologyField label="目标对象到当前对象"><Input autoComplete="off" disabled={disabled} value={relation.reverseName} maxLength={100} placeholder="例如：部门人员" onChange={(event) => onChange({ ...relation, reverseName: event.target.value })} /></OntologyField>
        <OntologyField label="数量约束"><Select disabled={disabled} value={relation.cardinality ?? undefined} options={Object.entries(relationCardinalityLabels).map(([value, label]) => ({ value, label }))} onChange={(cardinality) => onChange({ ...relation, cardinality })} /></OntologyField>
      </div>
      <div className="ontology-relation-editor-column">
        <div className="ontology-subsection-title">目标与身份映射</div>
        <OntologyField label="目标对象类型"><Select showSearch optionFilterProp="label" disabled={disabled} value={relation.targetObjectTypeId ?? undefined} allowClear placeholder="选择已启用对象类型" options={targets.map((item) => ({ value: item.id, label: item.name }))} onChange={(targetObjectTypeId) => onChange({ ...relation, targetObjectTypeId: targetObjectTypeId ?? null, targetFieldId: null })} /></OntologyField>
        <OntologyField label="当前对象主来源字段"><ModelFieldSelect disabled={disabled} modelId={definition.mainSource?.modelId} value={relation.sourceFieldId} onChange={(sourceFieldId) => onChange({ ...relation, sourceFieldId })} /></OntologyField>
        <OntologyField label="目标对象主来源字段"><ModelFieldSelect disabled={disabled} modelId={target?.definition.mainSource?.modelId} value={relation.targetFieldId} onChange={(targetFieldId) => onChange({ ...relation, targetFieldId })} /></OntologyField>
      </div>
      <div className="ontology-relation-editor-column">
        <div className="ontology-subsection-title">稳定编码</div>
        <OntologyField label="关系编码"><Input autoComplete="off" disabled={disabled} value={relation.code} maxLength={64} onChange={(event) => onChange({ ...relation, code: simpleCode(event.target.value) })} /></OntologyField>
        <OntologyField label="当前端访问编码"><Input autoComplete="off" disabled={disabled} value={relation.forwardAccessCode} maxLength={64} onChange={(event) => onChange({ ...relation, forwardAccessCode: simpleCode(event.target.value) })} /></OntologyField>
        <OntologyField label="反向端访问编码"><Input autoComplete="off" disabled={disabled} value={relation.reverseAccessCode} maxLength={64} onChange={(event) => onChange({ ...relation, reverseAccessCode: simpleCode(event.target.value) })} /></OntologyField>
      </div>
      <OntologyField label="关系说明" wide><Input.TextArea autoComplete="off" disabled={disabled} value={relation.description ?? ''} rows={2} maxLength={2000} onChange={(event) => onChange({ ...relation, description: event.target.value || null })} /></OntologyField>
    </div>
  );
};

const reverseCardinality = (value: BusinessObjectRelationSummary['cardinality']) => value === 'ONE_TO_MANY' ? 'MANY_TO_ONE' : value === 'MANY_TO_ONE' ? 'ONE_TO_MANY' : value;

export const RelationsPanel = ({ definition, relations, objectName, targets, canManage, loading, initialRelationId, onChange, onNavigate, onOpenOverview }: {
  definition: BusinessObjectTypeDefinition;
  relations: BusinessObjectRelationSummary[];
  objectName: string;
  targets: { id: string; name: string; definition: BusinessObjectTypeDefinition }[];
  canManage: boolean;
  loading: boolean;
  initialRelationId?: string;
  onChange: (next: BusinessObjectTypeDefinition) => void;
  onNavigate: (id: string) => void;
  onOpenOverview: () => void;
}) => {
  const [activeKeys, setActiveKeys] = useState<string[]>(initialRelationId ? [initialRelationId] : []);
  const addRelation = () => {
    const relation = { id: newId(), code: `relation_${definition.relations.length + 1}`, forwardName: '', forwardAccessCode: `relation_${definition.relations.length + 1}`, reverseName: '', reverseAccessCode: `related_${definition.relations.length + 1}`, description: null, targetObjectTypeId: null, cardinality: 'MANY_TO_ONE' as const, sourceFieldId: null, targetFieldId: null };
    onChange({ ...definition, relations: [...definition.relations, relation] });
    setActiveKeys((current) => [...current, relation.id]);
  };
  return (
    <div className="ontology-tab-stack">
      <BusinessDetailSection
        title={<Space size={5}>关系定义<ContextHelp ariaLabel="业务关系说明" presentation="popover" content="关系定义保存业务含义、两端访问名称和字段映射。具体对象之间的关联在预览时从来源数据解析。" /></Space>}
        description="一个定义同时提供正向和反向访问入口。"
        icon={<LinkOutlined />}
        extra={<Button type="primary" ghost icon={<PlusOutlined />} disabled={!canManage || !definition.mainSource} onClick={addRelation}>添加关系</Button>}
      >
        {definition.relations.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未定义业务关系" /> : (
          <Collapse
            className="ontology-editor-collapse"
            activeKey={activeKeys}
            onChange={(keys) => setActiveKeys(Array.isArray(keys) ? keys.map(String) : [String(keys)])}
            items={definition.relations.map((relation, index) => {
              const target = targets.find((item) => item.id === relation.targetObjectTypeId);
              return {
                key: relation.id,
                label: <div className="ontology-collapse-summary"><strong>{relation.forwardName || `未命名关系 ${index + 1}`}</strong><ManagementCode value={relation.code} /><Tag>{relation.cardinality ? relationCardinalityLabels[relation.cardinality] : '未设置数量'}</Tag><span>{target?.name ?? '尚未选择目标类型'}</span></div>,
                extra: canManage ? <Popconfirm title={`移除关系“${relation.forwardName || `关系 ${index + 1}`}”？`} onConfirm={() => onChange({ ...definition, relations: definition.relations.filter((item) => item.id !== relation.id) })}><Button type="text" danger icon={<DeleteOutlined />} aria-label={`移除关系${relation.forwardName || index + 1}`} onClick={(event) => event.stopPropagation()} /></Popconfirm> : null,
                children: <RelationEditor relation={relation} definition={definition} targets={targets} disabled={!canManage} onChange={(next) => onChange({ ...definition, relations: definition.relations.map((item) => item.id === relation.id ? next : item) })} />,
              };
            })}
          />
        )}
      </BusinessDetailSection>

      <BusinessDetailSection title="可访问关系" description="以当前对象为起点查看直接邻接类型；点击目标进入其定义。" icon={<SearchOutlined />} extra={<Button icon={<ApartmentOutlined />} onClick={onOpenOverview}>在本体总览中定位</Button>}>
        {loading ? <Typography.Text type="secondary">正在读取关系…</Typography.Text> : relations.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚无可访问关系" /> : (
          <div className="ontology-relation-map">
            <div className="ontology-relation-node ontology-relation-node-current"><span>当前对象</span><strong>{objectName}</strong></div>
            <div className="ontology-relation-map-edges">
              {relations.map((relation) => {
                const targetId = relation.direction === 'OUTBOUND' ? relation.targetObjectTypeId : relation.sourceObjectTypeId;
                const targetName = relation.direction === 'OUTBOUND' ? relation.targetObjectTypeName : relation.sourceObjectTypeName;
                const cardinality = relation.direction === 'INBOUND' ? reverseCardinality(relation.cardinality) : relation.cardinality;
                return (
                  <button className="ontology-relation-path" type="button" key={`${relation.direction}-${relation.id}`} onClick={() => onNavigate(targetId)}>
                    <span className="ontology-relation-line"><span>{relation.name}</span><code>{relation.accessCode}</code><Tag>{relationCardinalityLabels[cardinality]}</Tag></span>
                    <span className="ontology-relation-arrow">→</span>
                    <span className="ontology-relation-node"><span>{relation.direction === 'INBOUND' ? '来源对象' : '目标对象'}</span><strong>{targetName}{targetId === relation.sourceObjectTypeId && targetId === relation.targetObjectTypeId ? '（自身）' : ''}</strong></span>
                  </button>
                );
              })}
            </div>
          </div>
        )}
      </BusinessDetailSection>
    </div>
  );
};

const CapabilityFieldsEditor = ({ title, fields, input, disabled, onChange }: {
  title: string;
  fields: BusinessObjectCapabilityField[];
  input: boolean;
  disabled?: boolean;
  onChange: (next: BusinessObjectCapabilityField[]) => void;
}) => (
  <div className="ontology-capability-fields">
    <div className="ontology-capability-fields-heading"><strong>{title}</strong><span>{fields.length} 个字段</span><Button type="text" size="small" icon={<PlusOutlined />} disabled={disabled} onClick={() => onChange([...fields, { code: `field_${fields.length + 1}`, name: '', fieldType: 'STRING', description: null, required: input }])}>添加字段</Button></div>
    {fields.length === 0 ? <Typography.Text type="secondary">尚未登记字段</Typography.Text> : fields.map((field, index) => (
      <div className="ontology-capability-field-row" key={index}>
        <Input autoComplete="off" value={field.name} disabled={disabled} placeholder="字段名称" onChange={(event) => onChange(fields.map((item, position) => position === index ? { ...item, name: event.target.value } : item))} />
        <Input autoComplete="off" value={field.code} disabled={disabled} placeholder="字段编码" onChange={(event) => onChange(fields.map((item, position) => position === index ? { ...item, code: simpleCode(event.target.value) } : item))} />
        <Select value={field.fieldType ?? undefined} disabled={disabled} options={platformDataTypes.map((value) => ({ value, label: value }))} onChange={(fieldType) => onChange(fields.map((item, position) => position === index ? { ...item, fieldType } : item))} />
        {input && <Select value={field.required ?? false} disabled={disabled} options={[{ value: true, label: '必填' }, { value: false, label: '可选' }]} onChange={(required) => onChange(fields.map((item, position) => position === index ? { ...item, required } : item))} />}
        <Tooltip title="删除字段"><Button type="text" danger icon={<DeleteOutlined />} disabled={disabled} aria-label={`删除${title}字段${index + 1}`} onClick={() => onChange(fields.filter((_, position) => position !== index))} /></Tooltip>
      </div>
    ))}
  </div>
);

export const CapabilitiesPanel = ({ definition, canManage, onChange }: {
  definition: BusinessObjectTypeDefinition;
  canManage: boolean;
  onChange: (next: BusinessObjectTypeDefinition) => void;
}) => {
  const [activeKeys, setActiveKeys] = useState<string[]>([]);
  const addCapability = () => {
    const capability: BusinessObjectCapability = { id: newId(), code: `capability_${definition.capabilities.length + 1}`, name: '', kind: 'QUERY', description: null, inputs: [], outputs: [], preconditions: null, expectedEffect: null };
    onChange({ ...definition, capabilities: [...definition.capabilities, capability] });
    setActiveKeys((current) => [...current, capability.id]);
  };
  const update = (id: string, next: BusinessObjectCapability) => onChange({ ...definition, capabilities: definition.capabilities.map((item) => item.id === id ? next : item) });
  return (
    <div className="ontology-tab-stack">
      <BusinessDetailSection
        title={<Space size={5}>能力契约<ContextHelp ariaLabel="业务能力说明" presentation="popover" content="V1 只登记查询、计算和 Action 的输入输出及业务效果，不执行接口，也不会注册为 Agent 工具。" /></Space>}
        description="用稳定契约说明对象未来可提供的查询、计算和业务动作。"
        icon={<ApiOutlined />}
        extra={<Button type="primary" ghost icon={<PlusOutlined />} disabled={!canManage} onClick={addCapability}>添加能力</Button>}
      >
        {definition.capabilities.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未登记业务能力" /> : (
          <Collapse
            className="ontology-editor-collapse"
            activeKey={activeKeys}
            onChange={(keys) => setActiveKeys(Array.isArray(keys) ? keys.map(String) : [String(keys)])}
            items={definition.capabilities.map((capability, index) => ({
              key: capability.id,
              label: <div className="ontology-collapse-summary"><strong>{capability.name || `未命名能力 ${index + 1}`}</strong><Tag color="blue">{capability.kind ? capabilityKindLabels[capability.kind] : '未设置类型'}</Tag><ManagementCode value={capability.code} /><span>{capability.inputs.length} 入参 · {capability.outputs.length} 输出</span><Tag>仅定义</Tag></div>,
              extra: canManage ? <Popconfirm title={`移除能力“${capability.name || `能力 ${index + 1}`}”？`} onConfirm={() => onChange({ ...definition, capabilities: definition.capabilities.filter((item) => item.id !== capability.id) })}><Button type="text" danger icon={<DeleteOutlined />} aria-label={`移除能力${capability.name || index + 1}`} onClick={(event) => event.stopPropagation()} /></Popconfirm> : null,
              children: (
                <div className="ontology-capability-editor">
                  <div className="ontology-form-grid ontology-form-grid-3">
                    <OntologyField label="能力名称"><Input autoComplete="off" disabled={!canManage} value={capability.name} maxLength={100} onChange={(event) => update(capability.id, { ...capability, name: event.target.value })} /></OntologyField>
                    <OntologyField label="能力编码"><Input autoComplete="off" disabled={!canManage} value={capability.code} maxLength={64} onChange={(event) => update(capability.id, { ...capability, code: simpleCode(event.target.value) })} /></OntologyField>
                    <OntologyField label="类型"><Select disabled={!canManage} value={capability.kind ?? undefined} options={Object.entries(capabilityKindLabels).map(([value, label]) => ({ value, label }))} onChange={(kind) => update(capability.id, { ...capability, kind })} /></OntologyField>
                  </div>
                  <div className="ontology-form-grid ontology-form-grid-2">
                    <OntologyField label="说明"><Input.TextArea autoComplete="off" disabled={!canManage} value={capability.description ?? ''} rows={2} onChange={(event) => update(capability.id, { ...capability, description: event.target.value || null })} /></OntologyField>
                    <OntologyField label="前置条件"><Input.TextArea autoComplete="off" disabled={!canManage} value={capability.preconditions ?? ''} rows={2} onChange={(event) => update(capability.id, { ...capability, preconditions: event.target.value || null })} /></OntologyField>
                  </div>
                  <div className="ontology-capability-io">
                    <CapabilityFieldsEditor title="输入参数" input fields={capability.inputs} disabled={!canManage} onChange={(inputs) => update(capability.id, { ...capability, inputs })} />
                    <CapabilityFieldsEditor title="输出字段" input={false} fields={capability.outputs} disabled={!canManage} onChange={(outputs) => update(capability.id, { ...capability, outputs })} />
                  </div>
                  {capability.kind === 'ACTION' && <OntologyField label="预期影响对象、关系和业务效果"><Input.TextArea autoComplete="off" disabled={!canManage} value={capability.expectedEffect ?? ''} rows={2} onChange={(event) => update(capability.id, { ...capability, expectedEffect: event.target.value || null })} /></OntologyField>}
                </div>
              ),
            }))}
          />
        )}
      </BusinessDetailSection>
    </div>
  );
};
