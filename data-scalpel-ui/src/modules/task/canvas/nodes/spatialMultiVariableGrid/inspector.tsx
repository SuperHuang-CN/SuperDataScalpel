import { createUuid } from '../../../../../shared/browser/createUuid';
import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import {
  DeleteOutlined, DownOutlined, EditOutlined, PlusOutlined, SettingOutlined, UpOutlined,
} from '@ant-design/icons';
import {
  Button, Form, Input, InputNumber, Modal, Segmented, Select, Space, Switch, Tag, Typography,
} from 'antd';
import { useImperativeHandle, useState } from 'react';
import {
  CANVAS_SPATIAL_MULTI_VARIABLE_GRID_MAX_VARIABLES,
  CanvasNodeType,
  type CanvasFilterCondition,
  type SpatialMultiVariableGridConfiguration,
  type SpatialMultiVariableGridStatisticKind,
  type SpatialMultiVariableGridVariable,
  type SpatialMultiVariableGridVariableKind,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import { FilterConditionTreeEditor } from '../../components/processors/FilterProcessorInspector';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';
import { spatialColumnOptions, spatialTableOptions } from '../spatialInspectorOptions';
import { spatialDistanceUnitOptions, spatialUnitHelp } from '../spatialUnits';

const numericTypes = new Set(['BYTE', 'SHORT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL']);
const geometryKinds = new Set(['POINT', 'MULTIPOINT', 'LINESTRING', 'MULTILINESTRING', 'POLYGON', 'MULTIPOLYGON']);
const kindLabels: Record<SpatialMultiVariableGridVariableKind, string> = {
  DISTANCE_TO_NEAREST: '到最近要素的距离',
  ATTRIBUTE_OF_NEAREST: '最近要素的属性',
  ATTRIBUTE_SUMMARY_OF_RELATED: '关联要素属性汇总',
};
const statisticOptions: Array<{ value: SpatialMultiVariableGridStatisticKind; label: string }> = [
  { value: 'COUNT', label: '要素数量' },
  { value: 'SUM', label: '总和' },
  { value: 'MEAN', label: '平均值' },
  { value: 'MIN', label: '最小值' },
  { value: 'MAX', label: '最大值' },
  { value: 'RANGE', label: '范围（最大值 - 最小值）' },
  { value: 'STDDEV', label: '样本标准差' },
  { value: 'VARIANCE', label: '样本方差' },
  { value: 'ANY', label: '任一字符串值' },
];
const emptyFilter = (): CanvasFilterCondition => ({ kind: 'GROUP', operator: 'AND', children: [] });
const countConditions = (condition: CanvasFilterCondition | null): number => condition == null ? 0
  : condition.kind === 'PREDICATE' ? 1
    : condition.children.reduce((sum, child) => sum + countConditions(child), 0);
const fingerprint = (value: SpatialMultiVariableGridConfiguration) => JSON.stringify(value);
const createVariable = (index: number): SpatialMultiVariableGridVariable => ({
  variableId: createUuid(),
  sourceTableName: '',
  geometryColumnName: '',
  kind: 'DISTANCE_TO_NEAREST',
  attributeColumnName: null,
  statisticKind: 'COUNT',
  statisticColumnName: null,
  searchDistance: 1000,
  searchDistanceUnit: 'METERS',
  filter: null,
  outputColumnName: `variable_${index + 1}`,
});

const SpatialMultiVariableGridInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SpatialMultiVariableGrid>) => {
  const [form] = Form.useForm<SpatialMultiVariableGridConfiguration>();
  const [variables, setVariables] = useState(() => structuredClone(node.configuration.variables));
  const [editingIndex, setEditingIndex] = useState<number | null>(null);
  const [draft, setDraft] = useState<SpatialMultiVariableGridVariable | null>(null);
  const [filterOpen, setFilterOpen] = useState(false);
  const [outputFieldsOpen, setOutputFieldsOpen] = useState(false);
  const tables = validation?.inputTables ?? [];
  const draftTable = tables.find(table => table.name === draft?.sourceTableName);
  const draftColumns = draftTable?.columns ?? [];

  const normalize = (
    value: SpatialMultiVariableGridConfiguration,
    nextVariables = variables,
  ): SpatialMultiVariableGridConfiguration => ({
    variables: nextVariables.map(variable => ({
      ...variable,
      sourceTableName: variable.sourceTableName ?? '',
      geometryColumnName: variable.geometryColumnName ?? '',
      attributeColumnName: variable.attributeColumnName?.trim() || null,
      statisticColumnName: variable.statisticColumnName?.trim() || null,
      outputColumnName: variable.outputColumnName?.trim() ?? '',
      filter: variable.filter ?? null,
    })),
    binShape: value.binShape ?? null,
    binSize: value.binSize ?? 0,
    binSizeUnit: value.binSizeUnit ?? 'METERS',
    outputTableName: value.outputTableName?.trim() ?? '',
    binIdColumnName: value.binIdColumnName?.trim() ?? '',
    binGeometryColumnName: value.binGeometryColumnName?.trim() ?? '',
  });
  const markDirty = (
    value = form.getFieldsValue(true),
    nextVariables = variables,
  ) => onDirtyChange(
    fingerprint(normalize(value, nextVariables)) !== fingerprint(normalize(node.configuration, node.configuration.variables)),
  );
  const submit = (value: SpatialMultiVariableGridConfiguration) => {
    onApply({ id: node.id, type: node.type, configuration: normalize(value) });
    onDirtyChange(false);
  };
  useImperativeHandle<CanvasNodeInspectorHandle, CanvasNodeInspectorHandle>(inspectorRef, () => ({
    apply: async () => {
      void form.validateFields().catch(() => undefined);
      submit(form.getFieldsValue(true));
      return true;
    },
  }));
  const updateVariables = (next: SpatialMultiVariableGridVariable[]) => {
    setVariables(next);
    form.setFieldValue('variables', next);
    markDirty({ ...form.getFieldsValue(true), variables: next }, next);
  };
  const moveVariable = (from: number, to: number) => {
    const next = [...variables];
    [next[from], next[to]] = [next[to], next[from]];
    updateVariables(next);
  };
  const openVariable = (index: number | null) => {
    setEditingIndex(index);
    setDraft(structuredClone(index == null ? createVariable(variables.length) : variables[index]));
  };
  const saveVariable = () => {
    if (!draft) return;
    const next = [...variables];
    if (editingIndex == null) next.push(draft);
    else next[editingIndex] = draft;
    updateVariables(next);
    setDraft(null);
    setEditingIndex(null);
  };
  const removeVariable = (index: number) => {
    const variable = variables[index];
    const remove = () => updateVariables(variables.filter((_, itemIndex) => itemIndex !== index));
    if (variable.filter != null || variable.attributeColumnName || variable.statisticColumnName) {
      Modal.confirm({
        title: `删除变量“${variable.outputColumnName || index + 1}”？`,
        content: '该变量的字段、搜索距离和筛选配置会一并删除。',
        okText: '删除', cancelText: '取消', okButtonProps: { danger: true }, onOk: remove,
      });
    } else remove();
  };
  const summary = (variable: SpatialMultiVariableGridVariable) => {
    const relation = variable.kind === 'ATTRIBUTE_SUMMARY_OF_RELATED'
      ? variable.searchDistance == null ? '格网相交' : '中心半径'
      : '中心半径';
    return `${variable.kind ? kindLabels[variable.kind] : '类型待配置'} · ${relation}`
      + `${variable.filter ? ` · ${countConditions(variable.filter)} 个筛选条件` : ''}`;
  };
  const nearest = draft?.kind === 'DISTANCE_TO_NEAREST' || draft?.kind === 'ATTRIBUTE_OF_NEAREST';
  const usesSearch = nearest || draft?.searchDistance != null;

  return <Space orientation="vertical" size={12} className="canvas-inspector-content">
    <CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
    <Form<SpatialMultiVariableGridConfiguration> form={form} layout="vertical" autoComplete="off"
      initialValues={normalize(node.configuration, node.configuration.variables)}
      onFinish={() => submit(form.getFieldsValue(true))} onValuesChange={() => markDirty()}>
      <div className="canvas-spatial-pair-grid">
        <Form.Item label={<span className="canvas-inspector-field-label">格网形状<ContextHelp
          ariaLabel="多变量格网形状说明"
          content="方格大小表示边长；六边形大小表示两条平行边之间的距离。所有变量共享同一个固定原点和格网。"
        /></span>} required>
          <Form.Item name="binShape" noStyle>
            <Segmented block options={[{ value: 'SQUARE', label: '方格' }, { value: 'HEXAGON', label: '六边形' }]} />
          </Form.Item>
        </Form.Item>
        <Form.Item label={<span className="canvas-inspector-field-label">格网大小<ContextHelp
          ariaLabel="多变量格网大小说明"
          content={`格网覆盖所有变量来源 Geometry 的共同外包范围。变量筛选不改变该范围。${spatialUnitHelp}`}
        /></span>} required>
          <Space.Compact block>
            <Form.Item name="binSize" noStyle><InputNumber min={Number.MIN_VALUE} style={{ width: '52%' }} /></Form.Item>
            <Form.Item name="binSizeUnit" noStyle><Select options={spatialDistanceUnitOptions} style={{ width: '48%' }} /></Form.Item>
          </Space.Compact>
        </Form.Item>
      </div>

      <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>格网变量</Typography.Text><Tag>{variables.length}</Tag></Space>
        <Button size="small" type="primary" icon={<PlusOutlined />}
          disabled={variables.length >= CANVAS_SPATIAL_MULTI_VARIABLE_GRID_MAX_VARIABLES}
          onClick={() => openVariable(null)}>添加变量</Button>
      </div>
      <div className="canvas-processor-operation-list">
        {variables.map((variable, index) => {
          const sourceMissing = Boolean(validation && variable.sourceTableName
            && !tables.some(table => table.name === variable.sourceTableName));
          const invalid = sourceMissing || Boolean(validation?.issues.some(issue => issue.severity === 'ERROR'
            && issue.path?.startsWith(`configuration.variables[${index}]`)));
          return <div className={`canvas-processor-operation-row${invalid ? ' is-invalid' : ''}`}
            key={variable.variableId}>
            <span className="canvas-jdbc-input-picker-index">{index + 1}</span>
            <span className="canvas-processor-operation-identity">
              <Typography.Text ellipsis title={`${variable.sourceTableName} → ${variable.outputColumnName}`}>
                {variable.sourceTableName || '待选择来源'} → {variable.outputColumnName || '待设置输出字段'}
              </Typography.Text>
              <Typography.Text type={invalid ? 'danger' : 'secondary'} ellipsis title={summary(variable)}>
                {sourceMissing ? '上游表已失效' : summary(variable)}
              </Typography.Text>
            </span>
            <Space size={0}>
              <Button type="text" size="small" icon={<UpOutlined />} disabled={index === 0}
                aria-label={`上移变量 ${index + 1}`} onClick={() => moveVariable(index, index - 1)} />
              <Button type="text" size="small" icon={<DownOutlined />} disabled={index === variables.length - 1}
                aria-label={`下移变量 ${index + 1}`} onClick={() => moveVariable(index, index + 1)} />
              <Button type="text" size="small" icon={<EditOutlined />}
                aria-label={`配置变量 ${index + 1}`} onClick={() => openVariable(index)} />
              <Button type="text" danger size="small" icon={<DeleteOutlined />}
                aria-label={`删除变量 ${index + 1}`} onClick={() => removeVariable(index)} />
            </Space>
          </div>;
        })}
        {variables.length === 0 && <Typography.Text type="secondary">
          每个变量选择一张来源表；多张表会在同一个格网中形成不同结果字段。
        </Typography.Text>}
      </div>

      <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>结果字段</Typography.Text><Tag>{variables.length + 2}</Tag></Space>
        <Button size="small" icon={<SettingOutlined />} onClick={() => setOutputFieldsOpen(true)}>设置</Button>
      </div>
      <Form.Item name="outputTableName" label="输出表名" rules={[{ required: true, whitespace: true }]}>
        <Input placeholder="例如 city_variable_grid" />
      </Form.Item>

      <Modal open={draft != null} width={720}
        title={`${editingIndex == null ? '添加' : '配置'}格网变量`}
        okText="保存变量草稿" cancelText="取消" onOk={saveVariable}
        onCancel={() => { setDraft(null); setEditingIndex(null); }}>
        {draft && <Space orientation="vertical" size={12} style={{ width: '100%' }}>
          <div className="canvas-spatial-pair-grid">
            <label>来源表<Select showSearch optionFilterProp="label" value={draft.sourceTableName || undefined}
              placeholder="选择上游逻辑表" options={spatialTableOptions(tables, draft.sourceTableName)}
              onChange={sourceTableName => setDraft({ ...draft, sourceTableName })} /></label>
            <label>Geometry<Select showSearch optionFilterProp="label" value={draft.geometryColumnName || undefined}
              placeholder="选择 Point / Line / Polygon" options={spatialColumnOptions(
                draftColumns, draft.geometryColumnName,
                column => column.fieldType === 'GEOMETRY' && column.geometry?.dimension === 'XY'
                  && geometryKinds.has(column.geometry.kind),
              )} onChange={geometryColumnName => setDraft({ ...draft, geometryColumnName })} /></label>
          </div>
          <label>变量类型<Select<SpatialMultiVariableGridVariableKind> value={draft.kind ?? undefined}
            options={Object.entries(kindLabels).map(([value, label]) => ({ value: value as SpatialMultiVariableGridVariableKind, label }))}
            onChange={kind => setDraft({
              ...draft,
              kind,
              searchDistance: kind === 'ATTRIBUTE_SUMMARY_OF_RELATED'
                ? draft.searchDistance : draft.searchDistance ?? 1000,
              searchDistanceUnit: draft.searchDistanceUnit ?? 'METERS',
              statisticKind: draft.statisticKind ?? 'COUNT',
            })} /></label>
          {draft.kind === 'ATTRIBUTE_OF_NEAREST' && <label>最近要素属性<Select showSearch optionFilterProp="label"
            value={draft.attributeColumnName ?? undefined} options={spatialColumnOptions(
              draftColumns, draft.attributeColumnName ?? '', column => column.fieldType !== 'GEOMETRY',
            )} onChange={attributeColumnName => setDraft({ ...draft, attributeColumnName })} /></label>}
          {draft.kind === 'ATTRIBUTE_SUMMARY_OF_RELATED' && <div className="canvas-spatial-pair-grid">
            <label>统计类型<Select<SpatialMultiVariableGridStatisticKind> value={draft.statisticKind ?? undefined}
              options={statisticOptions} onChange={statisticKind => setDraft({ ...draft, statisticKind })} /></label>
            {draft.statisticKind !== 'COUNT' && <label>统计字段<Select showSearch optionFilterProp="label"
              value={draft.statisticColumnName ?? undefined} options={spatialColumnOptions(
                draftColumns, draft.statisticColumnName ?? '', column => draft.statisticKind === 'ANY'
                  ? column.fieldType === 'STRING' : numericTypes.has(column.fieldType),
              )} onChange={statisticColumnName => setDraft({ ...draft, statisticColumnName })} /></label>}
          </div>}
          <div className="canvas-processor-section-header">
            <Space size={6}><Typography.Text strong>中心搜索半径</Typography.Text>
              <Typography.Text type="secondary">{nearest ? '必填' : usesSearch ? '已启用' : '关闭时按格网相交'}</Typography.Text>
            </Space>
            {!nearest && <Switch size="small" checked={usesSearch}
              onChange={checked => setDraft({ ...draft, searchDistance: checked ? 1000 : null,
                searchDistanceUnit: checked ? draft.searchDistanceUnit ?? 'METERS' : draft.searchDistanceUnit })} />}
          </div>
          {usesSearch && <Space.Compact block>
            <InputNumber min={Number.MIN_VALUE} value={draft.searchDistance} style={{ width: '52%' }}
              onChange={searchDistance => setDraft({ ...draft, searchDistance })} />
            <Select value={draft.searchDistanceUnit ?? undefined} options={spatialDistanceUnitOptions}
              style={{ width: '48%' }} onChange={searchDistanceUnit => setDraft({ ...draft, searchDistanceUnit })} />
          </Space.Compact>}
          <div className="canvas-processor-section-header">
            <Space size={6}><Typography.Text strong>变量筛选</Typography.Text>
              <Typography.Text type="secondary">{draft.filter ? `${countConditions(draft.filter)} 个条件` : '未启用'}</Typography.Text>
            </Space>
            <Space size={4}><Switch size="small" checked={draft.filter != null}
              onChange={checked => setDraft({ ...draft, filter: checked ? draft.filter ?? emptyFilter() : null })} />
              <Button size="small" disabled={draft.filter == null} onClick={() => setFilterOpen(true)}>设置</Button></Space>
          </div>
          <label>结果字段名<Input value={draft.outputColumnName} placeholder="例如 nearest_hospital_distance"
            onChange={event => setDraft({ ...draft, outputColumnName: event.target.value })} /></label>
          <Typography.Text type="secondary">
            筛选只影响当前变量候选，不改变格网范围；超出搜索距离的最近值输出 NULL。
          </Typography.Text>
        </Space>}
      </Modal>

      <Modal open={filterOpen && draft?.filter != null} width={760} title="设置当前变量筛选"
        okText="完成" cancelText="关闭" onOk={() => setFilterOpen(false)} onCancel={() => setFilterOpen(false)}>
        {draft?.filter && <FilterConditionTreeEditor condition={draft.filter} columns={draftColumns}
          onChange={filter => setDraft({ ...draft, filter })} />}
      </Modal>

      <Modal open={outputFieldsOpen} width={560} title="设置格网基础字段" okText="完成" cancelText="关闭"
        onOk={() => setOutputFieldsOpen(false)} onCancel={() => setOutputFieldsOpen(false)}>
        <div className="canvas-spatial-pair-grid">
          <Form.Item name="binIdColumnName" label="格网 ID" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="binGeometryColumnName" label="格网 Geometry" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
        </div>
      </Modal>
    </Form>
  </Space>;
};

export default SpatialMultiVariableGridInspector;
