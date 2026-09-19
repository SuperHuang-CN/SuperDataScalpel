import { createUuid } from '../../../../../shared/browser/createUuid';
import { ContextHelp, InlineFeedback } from '../../../../../shared/components/ContextualFeedback';
import {
  DeleteOutlined,
  DownOutlined,
  PlusOutlined,
  SettingOutlined,
  UpOutlined,
} from '@ant-design/icons';
import { Button, Form, Input, Modal, Select, Space, Tooltip, Typography } from 'antd';
import { useImperativeHandle, useState } from 'react';
import {
  CANVAS_GEOMETRY_DERIVE_MAX_DERIVATIONS,
  CanvasNodeType,
  type CanvasColumnSchema,
  type GeometryDerivation,
  type GeometryDeriveConfiguration,
  type GeometryDeriveKind,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import { UnaryGeometryPolicySelect } from '../UnaryGeometryPolicySelect';
import type {
  CanvasNodeInspectorComponentProps,
  CanvasNodeInspectorHandle,
} from '../nodeSpec';
import {
  spatialColumnOptions,
  spatialGeometryColumns,
  spatialTableOptions,
} from '../spatialInspectorOptions';

interface GeometryDeriveFormValues {
  sourceTableName: string;
  outputTableName: string;
}

const kindOptions: Array<{ value: GeometryDeriveKind; label: string }> = [
  { value: 'CENTROID', label: '质心' },
  { value: 'POINT_ON_SURFACE', label: '面内点' },
  { value: 'ENVELOPE', label: '包络' },
  { value: 'CONVEX_HULL', label: '凸包' },
  { value: 'BOUNDARY', label: '边界' },
];

const kindLabels: Record<GeometryDeriveKind, string> = {
  CENTROID: '质心',
  POINT_ON_SURFACE: '面内点',
  ENVELOPE: '包络',
  CONVEX_HULL: '凸包',
  BOUNDARY: '边界',
};

const outputNameSuggestions: Record<GeometryDeriveKind, string> = {
  CENTROID: 'centroid',
  POINT_ON_SURFACE: 'point_on_surface',
  ENVELOPE: 'envelope',
  CONVEX_HULL: 'convex_hull',
  BOUNDARY: 'boundary',
};
const kindHelp: Record<GeometryDeriveKind, string> = {
  CENTROID: '单要素质心，可能落在凹面外或洞内；不是整层平均中心。只可靠输出 XY。',
  POINT_ON_SURFACE: '有效非空面的内部代表点；其他类型不承诺面内含义。只可靠输出 XY。',
  ENVELOPE: '轴对齐包络，可能退化为点或线；空输入仍为空。只可靠输出 XY。',
  CONVEX_HULL: '单点/共线凸包会退化，保留被选中原顶点的 Z/M；不计算三维凸包。',
  BOUNDARY: '面通常返回线，线返回端点，点/闭合线返回空；GeometryCollection 不支持。保留端点/环的 Z/M。',
};

const fingerprint = (value: GeometryDeriveConfiguration) => JSON.stringify(value);

const normalized = (value: string) => value.trim().toLocaleLowerCase();

const outputNameIssue = (
  item: GeometryDerivation,
  index: number,
  derivations: GeometryDerivation[],
  sourceColumns: CanvasColumnSchema[],
): string | null => {
  const name = normalized(item.outputColumnName);
  if (!name) return '请输入派生输出字段名';
  if (sourceColumns.some((column) => normalized(column.name) === name)) {
    return '输出字段名不能占用来源字段';
  }
  if (derivations.some((candidate, candidateIndex) => (
    candidateIndex !== index && normalized(candidate.outputColumnName) === name
  ))) {
    return '输出字段名在派生项中重复';
  }
  return null;
};

const GeometryDeriveInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.GeometryDerive>) => {
  const [form] = Form.useForm<GeometryDeriveFormValues>();
  const [derivations, setDerivations] = useState<GeometryDerivation[]>(
    node.configuration.derivations.map((item) => ({ ...item })),
  );
  const [editorOpen, setEditorOpen] = useState(false);
  const [draftDerivations, setDraftDerivations] = useState<GeometryDerivation[]>([]);
  const sourceTableName = Form.useWatch('sourceTableName', form)
    ?? node.configuration.sourceTableName;
  const tables = validation?.inputTables ?? [];
  const sourceTable = tables.find((table) => table.name === sourceTableName);
  const geometryColumns = spatialGeometryColumns(sourceTable);
  const sourceTableMissing = Boolean(sourceTableName && validation && !sourceTable);

  const configuration = (
    values: GeometryDeriveFormValues,
    items: GeometryDerivation[],
  ): GeometryDeriveConfiguration => ({
    sourceTableName: values.sourceTableName ?? '',
    outputTableName: values.outputTableName?.trim() ?? '',
    derivations: items.map((item) => ({
      ...item,
      outputColumnName: item.outputColumnName.trim(),
    })),
  });

  const markDirty = (items = derivations) => {
    onDirtyChange(
      fingerprint(configuration(form.getFieldsValue(true), items))
        !== fingerprint(node.configuration),
    );
  };

  const submit = (values: GeometryDeriveFormValues) => {
    onApply({
      id: node.id,
      type: node.type,
      configuration: configuration(values, derivations),
    });
    onDirtyChange(false);
  };

  useImperativeHandle<CanvasNodeInspectorHandle, CanvasNodeInspectorHandle>(
    inspectorRef,
    () => ({
      apply: async () => {
        void form.validateFields().catch(() => undefined);
        submit(form.getFieldsValue(true));
        return true;
      },
    }),
  );

  const openEditor = () => {
    setDraftDerivations(derivations.map((item) => ({ ...item })));
    setEditorOpen(true);
  };
  const commitEditor = () => {
    const next = draftDerivations.map((item) => ({ ...item }));
    setDerivations(next);
    setEditorOpen(false);
    markDirty(next);
  };
  const updateDraft = (index: number, item: GeometryDerivation) => {
    setDraftDerivations((current) => current.map((candidate, candidateIndex) => (
      candidateIndex === index ? item : candidate
    )));
  };
  const moveDraft = (from: number, to: number) => {
    setDraftDerivations((current) => {
      const next = [...current];
      const [item] = next.splice(from, 1);
      next.splice(to, 0, item);
      return next;
    });
  };
  const addDraft = () => {
    setDraftDerivations((current) => [
      ...current,
      {
        derivationId: createUuid(),
        kind: null,
        sourceColumnName: '',
        outputColumnName: '',
        geometryPolicy: 'PRESERVE_DIMENSION',
      },
    ]);
  };

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <CanvasNodeValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      <Form<GeometryDeriveFormValues>
        form={form}
        layout="vertical"
        autoComplete="off"
        initialValues={{
          sourceTableName: node.configuration.sourceTableName,
          outputTableName: node.configuration.outputTableName,
        }}
        onFinish={submit}
        onValuesChange={() => markDirty()}
      >
        <Form.Item
          name="sourceTableName"
          label="来源表"
          rules={[{ required: true, message: '请选择来源表' }]}
          validateStatus={sourceTableMissing ? 'error' : undefined}
          help={sourceTableMissing ? '原来源表已失效，派生配置仍被保留。' : undefined}
        >
          <Select
            showSearch
            optionFilterProp="label"
            disabled={!validation}
            options={!validation && sourceTableName
              ? [{ value: sourceTableName, label: `${sourceTableName}（等待解析）` }]
              : spatialTableOptions(tables, sourceTableName)}
            placeholder={validation ? '选择来源表' : '等待 Task Engine 计算上游表'}
          />
        </Form.Item>
        <Form.Item
          name="outputTableName"
          label="输出表名"
          rules={[{ required: true, whitespace: true, message: '请输入输出表名' }]}
        >
          <Input placeholder="例如 parcels_geometry" />
        </Form.Item>
      </Form>

      <div className="canvas-processor-section-header">
        <span className="canvas-inspector-field-label">
          <Typography.Text strong>派生字段</Typography.Text>
          <Typography.Text type="secondary">{` · ${derivations.length} 项`}</Typography.Text>
          <ContextHelp
            ariaLabel="Geometry 派生说明"
            presentation="popover"
            content={(
              <Space orientation="vertical" size={4}>
                <span>CENTROID 计算几何中心，凹面结果可能位于面外。</span>
                <span>POINT_ON_SURFACE 对有效非空面提供内部代表点，更适合标注点。</span>
                <span>包络、凸包和边界均保留来源 CRS，不会自动修复或转换。</span>
              </Space>
            )}
          />
        </span>
        <Button size="small" icon={<SettingOutlined />} onClick={openEditor}>
          配置
        </Button>
      </div>
      {derivations.length === 0 ? (
        <Typography.Text type="secondary">尚未配置派生字段，可先保存草稿。</Typography.Text>
      ) : (
        <div className="canvas-geometry-derive-preview">
          {derivations.slice(0, 2).map((item) => (
            <div className="canvas-geometry-derive-preview-row" key={item.derivationId}>
              <Typography.Text ellipsis>{item.kind ? kindLabels[item.kind] : '待选函数'}</Typography.Text>
              <Typography.Text type="secondary" ellipsis={{ tooltip: `${item.sourceColumnName || '待选字段'} → ${item.outputColumnName || '待填字段'}` }}>
                {item.sourceColumnName || '待选字段'} → {item.outputColumnName || '待填字段'}
              </Typography.Text>
            </div>
          ))}
          {derivations.length > 2 && (
            <Typography.Text type="secondary">另 {derivations.length - 2} 项</Typography.Text>
          )}
        </div>
      )}

      <Modal
        open={editorOpen}
        width={720}
        destroyOnHidden
        title="配置 Geometry 派生字段"
        okText="保存草稿"
        cancelText="取消"
        onOk={commitEditor}
        onCancel={() => setEditorOpen(false)}
        footer={(_, { OkBtn, CancelBtn }) => (
          <div className="canvas-geometry-derive-modal-footer">
            <Typography.Text type="secondary">
              {draftDerivations.length}/{CANVAS_GEOMETRY_DERIVE_MAX_DERIVATIONS} 项
            </Typography.Text>
            <Space><CancelBtn /><OkBtn /></Space>
          </div>
        )}
      >
        <div className="canvas-geometry-derive-modal-heading">
          <Typography.Text type="secondary">
            每项独立读取来源 Geometry；可保留业务校验错误并继续保存草稿。
          </Typography.Text>
          <Button
            size="small"
            type="primary"
            icon={<PlusOutlined />}
            disabled={draftDerivations.length >= CANVAS_GEOMETRY_DERIVE_MAX_DERIVATIONS}
            onClick={addDraft}
          >
            添加派生
          </Button>
        </div>
        <div className="canvas-geometry-derive-grid canvas-geometry-derive-grid-header">
          <span>派生类型</span>
          <span>Geometry 字段</span>
          <span>输出字段</span>
          <span>结果维度</span>
          <span>操作</span>
        </div>
        <div className="canvas-geometry-derive-rule-list">
          {draftDerivations.length === 0 && (
            <Typography.Text type="secondary">至少添加一个派生字段后才能运行。</Typography.Text>
          )}
          {draftDerivations.map((item, index) => {
            const sourceMissing = Boolean(
              item.sourceColumnName
              && sourceTable
              && !geometryColumns.some((column) => column.name === item.sourceColumnName),
            );
            const nameIssue = outputNameIssue(
              item,
              index,
              draftDerivations,
              sourceTable?.columns ?? [],
            );
            const sourceGeometry = geometryColumns.find(column => column.name === item.sourceColumnName)?.geometry;
            const dimensionIssue = item.geometryPolicy === 'PRESERVE_DIMENSION' && sourceGeometry?.dimension !== 'XY'
              && sourceGeometry != null && ['CENTROID', 'POINT_ON_SURFACE', 'ENVELOPE'].includes(item.kind ?? '')
              ? '该函数不能可靠保留 Z/M，请显式选择输出 XY' : null;
            const kindIssue = item.geometryPolicy != null && item.geometryPolicy !== 'LEGACY'
              && item.kind === 'BOUNDARY' && sourceGeometry?.kind === 'GEOMETRYCOLLECTION'
              ? '边界不支持 GeometryCollection 来源' : null;
            const sourceIssue = !item.sourceColumnName ? '请选择来源 Geometry 字段'
              : sourceMissing ? '原 Geometry 字段已失效，配置仍被保留' : null;
            const issues = [!item.kind ? '请选择派生函数' : null, sourceIssue, nameIssue, dimensionIssue, kindIssue]
              .filter((issue): issue is string => issue !== null);
            return (
              <div
                className={`canvas-geometry-derive-grid canvas-geometry-derive-rule${
                  issues.length ? ' is-invalid' : ''
                }`}
                key={`${item.derivationId}-${index}`}
              >
                <Space.Compact block>
                <Select
                  style={{ flex: 1, minWidth: 0 }}
                  aria-label={`第 ${index + 1} 项派生函数`}
                  placeholder="选择函数"
                  status={!item.kind || kindIssue ? 'error' : undefined}
                  value={item.kind}
                  options={kindOptions}
                  onChange={(kind: GeometryDeriveKind) => updateDraft(index, {
                    ...item,
                    kind,
                    outputColumnName: item.outputColumnName || outputNameSuggestions[kind],
                  })}
                />
                <ContextHelp ariaLabel={`第 ${index + 1} 项函数说明`} content={item.kind ? kindHelp[item.kind] : '选择派生函数；各项只读取原来源字段。'} />
                </Space.Compact>
                <Tooltip title={sourceIssue ?? undefined}>
                  <Select
                    aria-label={`第 ${index + 1} 项来源 Geometry 字段`}
                    showSearch
                    optionFilterProp="label"
                    disabled={!sourceTable}
                    status={sourceIssue ? 'error' : undefined}
                    value={item.sourceColumnName || undefined}
                    options={!sourceTable && item.sourceColumnName
                      ? [{ value: item.sourceColumnName, label: `${item.sourceColumnName}（${validation ? '来源表不可用' : '等待解析'}）` }]
                      : spatialColumnOptions(
                      geometryColumns,
                      item.sourceColumnName,
                      () => true,
                    )}
                    placeholder="选择字段"
                    onChange={(sourceColumnName) => updateDraft(index, {
                      ...item,
                      sourceColumnName,
                    })}
                  />
                </Tooltip>
                <Tooltip title={nameIssue ?? undefined}>
                  <Input
                    aria-label={`第 ${index + 1} 项输出字段`}
                    autoComplete="off"
                    status={nameIssue ? 'error' : undefined}
                    value={item.outputColumnName}
                    placeholder="输出字段"
                    onChange={(event) => updateDraft(index, {
                      ...item,
                      outputColumnName: event.target.value,
                    })}
                  />
                </Tooltip>
                <Tooltip title={dimensionIssue ?? (sourceGeometry ? `${sourceGeometry.kind} · EPSG:${sourceGeometry.crs.code} · ${sourceGeometry.dimension}` : '来源 Geometry 待解析')}>
                  <div><UnaryGeometryPolicySelect label={`第 ${index + 1} 项结果维度`} value={item.geometryPolicy}
                    onChange={geometryPolicy => updateDraft(index, { ...item, geometryPolicy })} /></div>
                </Tooltip>
                <Space size={0}>
                  <Tooltip title="上移">
                    <Button
                      type="text"
                      size="small"
                      aria-label={`上移第 ${index + 1} 个派生字段`}
                      icon={<UpOutlined />}
                      disabled={index === 0}
                      onClick={() => moveDraft(index, index - 1)}
                    />
                  </Tooltip>
                  <Tooltip title="下移">
                    <Button
                      type="text"
                      size="small"
                      aria-label={`下移第 ${index + 1} 个派生字段`}
                      icon={<DownOutlined />}
                      disabled={index === draftDerivations.length - 1}
                      onClick={() => moveDraft(index, index + 1)}
                    />
                  </Tooltip>
                  <Tooltip title="删除">
                    <Button
                      type="text"
                      danger
                      size="small"
                      aria-label={`删除第 ${index + 1} 个派生字段`}
                      icon={<DeleteOutlined />}
                      onClick={() => Modal.confirm({ title: `删除派生字段 ${item.outputColumnName || `第 ${index + 1} 项`}？`,
                        content: '本项配置将一并删除。', okText: '删除', okButtonProps: { danger: true },
                        onOk: () => setDraftDerivations(current => current.filter((_candidate, candidateIndex) => candidateIndex !== index)) })}
                    />
                  </Tooltip>
                </Space>
                {issues.length > 0 && <InlineFeedback
                  className="canvas-geometry-derive-rule-issues"
                  tone="error"
                  label={`${issues.length} 个配置问题`}
                  ariaLabel={`第 ${index + 1} 项配置问题`}
                  detail={<Space orientation="vertical" size={4}>{issues.map(issue => <span key={issue}>{issue}</span>)}</Space>}
                />}
              </div>
            );
          })}
        </div>
      </Modal>
    </Space>
  );
};

export default GeometryDeriveInspector;
