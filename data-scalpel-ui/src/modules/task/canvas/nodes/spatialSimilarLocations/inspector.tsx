import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import {
  DeleteOutlined,
  DownOutlined,
  FilterOutlined,
  PlusOutlined,
  SettingOutlined,
  UpOutlined,
} from '@ant-design/icons';
import {
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Tag,
  Typography,
} from 'antd';
import { useImperativeHandle, useState } from 'react';
import {
  CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_ANALYSIS_FIELDS,
  CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_APPEND_FIELDS,
  CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_RESULTS,
  CanvasNodeType,
  type CanvasColumnSchema,
  type CanvasFilterCondition,
  type SpatialSimilarLocationsConfiguration,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import { FilterConditionTreeEditor } from '../../components/processors/FilterProcessorInspector';
import type {
  CanvasNodeInspectorComponentProps,
  CanvasNodeInspectorHandle,
} from '../nodeSpec';
import { spatialColumnOptions, spatialTableOptions } from '../spatialInspectorOptions';

const numericTypes = new Set(['BYTE', 'SHORT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL']);
const emptyFilter = (): CanvasFilterCondition => ({ kind: 'GROUP', operator: 'AND', children: [] });
const fingerprint = (value: SpatialSimilarLocationsConfiguration) => JSON.stringify(value);

const sameAnalysisType = (left: CanvasColumnSchema, right: CanvasColumnSchema) => (
  left.fieldType === right.fieldType
  && (left.fieldType !== 'DECIMAL'
    || (left.precision === right.precision && left.scale === right.scale))
);

const countConditions = (condition: CanvasFilterCondition | null): number => condition == null ? 0
  : condition.kind === 'PREDICATE' ? 1
    : condition.children.reduce((sum, child) => sum + countConditions(child), 0);

const normalize = (
  values: SpatialSimilarLocationsConfiguration,
): SpatialSimilarLocationsConfiguration => ({
  referenceTableName: values.referenceTableName ?? '',
  referenceIdColumnName: values.referenceIdColumnName ?? '',
  referenceGeometryColumnName: values.referenceGeometryColumnName ?? '',
  referenceFilter: values.referenceFilter ?? null,
  candidateTableName: values.candidateTableName ?? '',
  candidateIdColumnName: values.candidateIdColumnName ?? '',
  candidateGeometryColumnName: values.candidateGeometryColumnName ?? '',
  candidateFilter: values.candidateFilter ?? null,
  analysisFields: (values.analysisFields ?? []).map((field) => ({
    columnName: field.columnName ?? '',
    outputColumnName: field.outputColumnName?.trim() ?? '',
  })),
  appendFields: (values.appendFields ?? []).map((field) => ({
    sourceColumnName: field.sourceColumnName ?? '',
    outputColumnName: field.outputColumnName?.trim() ?? '',
  })),
  matchMethod: values.matchMethod ?? null,
  resultMode: values.resultMode ?? null,
  numberOfResults: values.numberOfResults ?? 0,
  outputTableName: values.outputTableName?.trim() ?? '',
  outputGeometryColumnName: values.outputGeometryColumnName?.trim() ?? '',
  locationTypeColumnName: values.locationTypeColumnName?.trim() ?? '',
  similarityRankColumnName: values.similarityRankColumnName?.trim() ?? '',
  dissimilarityRankColumnName: values.dissimilarityRankColumnName?.trim() ?? '',
  similarityIndexColumnName: values.similarityIndexColumnName?.trim() ?? '',
  cosineIndexColumnName: values.cosineIndexColumnName?.trim() ?? '',
  labelRankColumnName: values.labelRankColumnName?.trim() ?? '',
  referenceIdOutputColumnName: values.referenceIdOutputColumnName?.trim() ?? '',
  searchIdOutputColumnName: values.searchIdOutputColumnName?.trim() ?? '',
});

const SpatialSimilarLocationsInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SpatialSimilarLocations>) => {
  const [form] = Form.useForm<SpatialSimilarLocationsConfiguration>();
  const [filterSide, setFilterSide] = useState<'reference' | 'candidate' | null>(null);
  const [resultFieldsOpen, setResultFieldsOpen] = useState(false);
  const tables = validation?.inputTables ?? [];
  const referenceTableName = Form.useWatch('referenceTableName', form) ?? '';
  const candidateTableName = Form.useWatch('candidateTableName', form) ?? '';
  const referenceFilter = Form.useWatch('referenceFilter', { form, preserve: true }) ?? null;
  const candidateFilter = Form.useWatch('candidateFilter', { form, preserve: true }) ?? null;
  const analysisFields = Form.useWatch('analysisFields', { form, preserve: true }) ?? [];
  const appendFields = Form.useWatch('appendFields', { form, preserve: true }) ?? [];
  const referenceTable = tables.find((table) => table.name === referenceTableName);
  const candidateTable = tables.find((table) => table.name === candidateTableName);
  const referenceColumns = referenceTable?.columns ?? [];
  const candidateColumns = candidateTable?.columns ?? [];
  const commonAnalysisColumns = referenceColumns.filter((left) => numericTypes.has(left.fieldType)
    && candidateColumns.some((right) => right.name === left.name
      && numericTypes.has(right.fieldType) && sameAnalysisType(left, right)));

  const markDirty = (values = form.getFieldsValue(true)) => {
    onDirtyChange(fingerprint(normalize(values)) !== fingerprint(normalize(node.configuration)));
  };
  const submit = (values: SpatialSimilarLocationsConfiguration) => {
    onApply({ id: node.id, type: node.type, configuration: normalize(values) });
    onDirtyChange(false);
  };
  useImperativeHandle<CanvasNodeInspectorHandle, CanvasNodeInspectorHandle>(inspectorRef, () => ({
    apply: async () => {
      void form.validateFields().catch(() => undefined);
      submit(form.getFieldsValue(true));
      return true;
    },
  }));

  const setFilterEnabled = (side: 'reference' | 'candidate', enabled: boolean) => {
    const name = side === 'reference' ? 'referenceFilter' : 'candidateFilter';
    const current = form.getFieldValue(name);
    form.setFieldValue(name, enabled ? current ?? emptyFilter() : null);
    markDirty(form.getFieldsValue(true));
  };
  const currentFilter = filterSide === 'reference' ? referenceFilter : candidateFilter;
  const currentFilterColumns = filterSide === 'reference' ? referenceColumns : candidateColumns;

  return <Space orientation="vertical" size={12} className="canvas-inspector-content">
    <CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
    <Form<SpatialSimilarLocationsConfiguration>
      form={form}
      layout="vertical"
      autoComplete="off"
      initialValues={node.configuration}
      onFinish={() => submit(form.getFieldsValue(true))}
      onValuesChange={() => markDirty()}
    >
      <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>参考位置</Typography.Text>
          {referenceFilter && <Tag icon={<FilterOutlined />}>{countConditions(referenceFilter)} 个条件</Tag>}
        </Space>
        <Space size={4}>
          <Switch size="small" checked={referenceFilter != null}
            aria-label="启用参考位置筛选" onChange={(checked) => setFilterEnabled('reference', checked)} />
          <Button size="small" disabled={referenceFilter == null}
            onClick={() => setFilterSide('reference')}>设置筛选</Button>
        </Space>
      </div>
      <div className="canvas-spatial-pair-grid">
        <Form.Item name="referenceTableName" label="参考表" rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label" disabled={!validation}
            options={spatialTableOptions(tables, referenceTableName)} placeholder="选择参考位置表" />
        </Form.Item>
        <Form.Item name="referenceIdColumnName" label={<span className="canvas-inspector-field-label">
          参考唯一字段<ContextHelp ariaLabel="参考唯一字段说明"
            content="实际运行时要求非空且唯一。多个参考要素会按全部分析字段的标准化平均值形成一个共同目标。" />
        </span>} rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label" disabled={!referenceTable}
            options={spatialColumnOptions(referenceColumns,
              form.getFieldValue('referenceIdColumnName') ?? '',
              (column) => column.fieldType !== 'GEOMETRY' && column.fieldType !== 'BINARY')} />
        </Form.Item>
        <Form.Item name="referenceGeometryColumnName" label="参考 Geometry" rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label" disabled={!referenceTable}
            options={spatialColumnOptions(referenceColumns,
              form.getFieldValue('referenceGeometryColumnName') ?? '',
              (column) => column.fieldType === 'GEOMETRY' && column.geometry?.dimension === 'XY')} />
        </Form.Item>
      </div>

      <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>候选位置</Typography.Text>
          {candidateFilter && <Tag icon={<FilterOutlined />}>{countConditions(candidateFilter)} 个条件</Tag>}
        </Space>
        <Space size={4}>
          <Switch size="small" checked={candidateFilter != null}
            aria-label="启用候选位置筛选" onChange={(checked) => setFilterEnabled('candidate', checked)} />
          <Button size="small" disabled={candidateFilter == null}
            onClick={() => setFilterSide('candidate')}>设置筛选</Button>
        </Space>
      </div>
      <div className="canvas-spatial-pair-grid">
        <Form.Item name="candidateTableName" label="候选表" rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label" disabled={!validation}
            options={spatialTableOptions(tables, candidateTableName)} placeholder="选择待排名位置表" />
        </Form.Item>
        <Form.Item name="candidateIdColumnName" label={<span className="canvas-inspector-field-label">
          候选唯一字段<ContextHelp ariaLabel="候选唯一字段说明"
            content="实际运行时要求非空且唯一，并用于相似度相同时的稳定排序。" />
        </span>} rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label" disabled={!candidateTable}
            options={spatialColumnOptions(candidateColumns,
              form.getFieldValue('candidateIdColumnName') ?? '',
              (column) => column.fieldType !== 'GEOMETRY' && column.fieldType !== 'BINARY')} />
        </Form.Item>
        <Form.Item name="candidateGeometryColumnName" label="候选 Geometry" rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label" disabled={!candidateTable}
            options={spatialColumnOptions(candidateColumns,
              form.getFieldValue('candidateGeometryColumnName') ?? '',
              (column) => column.fieldType === 'GEOMETRY' && column.geometry?.dimension === 'XY')} />
        </Form.Item>
      </div>

      <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>分析字段</Typography.Text><Tag>{analysisFields.length}</Tag>
          <ContextHelp ariaLabel="查找相似位置分析字段说明"
            content="只列出参考表和候选表中同名、同数值类型的字段。标准化总体同时包含参考和候选全集，字段顺序不改变计算结果。" />
        </Space>
      </div>
      <Form.List name="analysisFields">
        {(fields, { add, remove, move }) => <div className="canvas-processor-operation-list">
          {fields.map((field, index) => {
            const current = analysisFields[index]?.columnName ?? '';
            return <div className="canvas-processor-operation-row" key={field.key}>
              <span className="canvas-jdbc-input-picker-index">{index + 1}</span>
              <Form.Item {...field} name={[field.name, 'columnName']} noStyle>
                <Select showSearch optionFilterProp="label" placeholder="共同数值字段"
                  style={{ minWidth: 148, flex: 1 }} disabled={!referenceTable || !candidateTable}
                  options={spatialColumnOptions(commonAnalysisColumns, current, () => true)} />
              </Form.Item>
              <span>→</span>
              <Form.Item {...field} name={[field.name, 'outputColumnName']} noStyle>
                <Input placeholder="结果字段名" style={{ minWidth: 118, flex: 1 }} />
              </Form.Item>
              <Space size={0}>
                <Button type="text" size="small" icon={<UpOutlined />} disabled={index === 0}
                  aria-label={`上移分析字段 ${index + 1}`} onClick={() => move(index, index - 1)} />
                <Button type="text" size="small" icon={<DownOutlined />} disabled={index === fields.length - 1}
                  aria-label={`下移分析字段 ${index + 1}`} onClick={() => move(index, index + 1)} />
                <Button type="text" danger size="small" icon={<DeleteOutlined />}
                  aria-label={`删除分析字段 ${index + 1}`} onClick={() => remove(index)} />
              </Space>
            </div>;
          })}
          <Button block size="small" icon={<PlusOutlined />}
            disabled={fields.length >= CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_ANALYSIS_FIELDS}
            onClick={() => add({ columnName: '', outputColumnName: '' })}>添加分析字段</Button>
        </div>}
      </Form.List>

      <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>候选附加字段</Typography.Text><Tag>{appendFields.length}</Tag>
          <ContextHelp ariaLabel="候选附加字段说明"
            content="附加字段随候选结果返回，但不参与标准化、相似度计算或排名。" />
        </Space>
      </div>
      <Form.List name="appendFields">
        {(fields, { add, remove, move }) => <div className="canvas-processor-operation-list">
          {fields.map((field, index) => {
            const current = appendFields[index]?.sourceColumnName ?? '';
            return <div className="canvas-processor-operation-row" key={field.key}>
              <span className="canvas-jdbc-input-picker-index">{index + 1}</span>
              <Form.Item {...field} name={[field.name, 'sourceColumnName']} noStyle>
                <Select showSearch optionFilterProp="label" placeholder="候选字段"
                  style={{ minWidth: 148, flex: 1 }} disabled={!candidateTable}
                  options={spatialColumnOptions(candidateColumns, current,
                    (column) => column.fieldType !== 'GEOMETRY')} />
              </Form.Item>
              <span>→</span>
              <Form.Item {...field} name={[field.name, 'outputColumnName']} noStyle>
                <Input placeholder="结果字段名" style={{ minWidth: 118, flex: 1 }} />
              </Form.Item>
              <Space size={0}>
                <Button type="text" size="small" icon={<UpOutlined />} disabled={index === 0}
                  aria-label={`上移附加字段 ${index + 1}`} onClick={() => move(index, index - 1)} />
                <Button type="text" size="small" icon={<DownOutlined />} disabled={index === fields.length - 1}
                  aria-label={`下移附加字段 ${index + 1}`} onClick={() => move(index, index + 1)} />
                <Button type="text" danger size="small" icon={<DeleteOutlined />}
                  aria-label={`删除附加字段 ${index + 1}`} onClick={() => remove(index)} />
              </Space>
            </div>;
          })}
          <Button block size="small" icon={<PlusOutlined />}
            disabled={fields.length >= CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_APPEND_FIELDS}
            onClick={() => add({ sourceColumnName: '', outputColumnName: '' })}>添加附加字段</Button>
        </div>}
      </Form.List>

      <div className="canvas-spatial-pair-grid">
        <Form.Item name="matchMethod" label={<span className="canvas-inspector-field-label">
          匹配方法<ContextHelp ariaLabel="查找相似位置匹配方法说明"
            content="属性值使用标准化值平方差之和，越小越相似；属性轮廓使用标准化向量的余弦差异，0 最相似，至少需要两个分析字段。" />
        </span>} rules={[{ required: true }]}>
          <Select options={[
            { value: 'ATTRIBUTE_VALUES', label: '属性值' },
            { value: 'ATTRIBUTE_PROFILES', label: '属性轮廓' },
          ]} />
        </Form.Item>
        <Form.Item name="resultMode" label="返回范围" rules={[{ required: true }]}>
          <Select options={[
            { value: 'MOST_SIMILAR', label: '最相似' },
            { value: 'LEAST_SIMILAR', label: '最不相似' },
            { value: 'BOTH', label: '最相似与最不相似' },
          ]} />
        </Form.Item>
        <Form.Item name="numberOfResults" label={<span className="canvas-inspector-field-label">
          每端结果数<ContextHelp ariaLabel="查找相似位置结果数量说明"
            content="BOTH 表示两端各返回该数量；候选不足时会自动缩小两端数量，保证同一候选不会重复出现。" />
        </span>} rules={[{ required: true }]}>
          <InputNumber min={1} max={CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_RESULTS}
            precision={0} style={{ width: '100%' }} />
        </Form.Item>
      </div>

      <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>结果</Typography.Text>
          <Tag>{9 + analysisFields.length + appendFields.length} 个字段</Tag></Space>
        <Button size="small" icon={<SettingOutlined />}
          onClick={() => setResultFieldsOpen(true)}>设置结果字段</Button>
      </div>
      <Form.Item name="outputTableName" label="输出表名" rules={[{ required: true, whitespace: true }]}>
        <Input placeholder="例如 similar_locations" />
      </Form.Item>

      <Modal open={filterSide != null && currentFilter != null} width={760}
        title={`设置${filterSide === 'reference' ? '参考' : '候选'}位置筛选`}
        okText="完成" cancelText="关闭"
        onOk={() => setFilterSide(null)} onCancel={() => setFilterSide(null)}>
        {filterSide && currentFilter && <FilterConditionTreeEditor
          condition={currentFilter} columns={currentFilterColumns}
          onChange={(condition) => {
            form.setFieldValue(filterSide === 'reference' ? 'referenceFilter' : 'candidateFilter', condition);
            markDirty(form.getFieldsValue(true));
          }} />}
      </Modal>

      <Modal open={resultFieldsOpen} width={680} title="设置结果字段"
        okText="完成" cancelText="关闭"
        onOk={() => setResultFieldsOpen(false)} onCancel={() => setResultFieldsOpen(false)}>
        <Typography.Paragraph type="secondary">
          固定结果字段用于区分参考/候选位置、表达两种排名及对应相似度索引。字段名必须互不重复。
        </Typography.Paragraph>
        <div className="canvas-spatial-pair-grid">
          <Form.Item name="outputGeometryColumnName" label="Geometry" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="locationTypeColumnName" label="位置类型" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="referenceIdOutputColumnName" label="参考 ID" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="searchIdOutputColumnName" label="候选 ID" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="similarityRankColumnName" label="相似排名" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="dissimilarityRankColumnName" label="不相似排名" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="similarityIndexColumnName" label="属性值索引" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="cosineIndexColumnName" label="轮廓余弦差异" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="labelRankColumnName" label="有符号渲染排名" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
        </div>
      </Modal>
    </Form>
  </Space>;
};

export default SpatialSimilarLocationsInspector;
