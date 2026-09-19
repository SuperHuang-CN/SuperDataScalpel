import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import { Form, Input, InputNumber, Segmented, Select, Space, Typography } from 'antd';
import { useImperativeHandle } from 'react';
import {
  CanvasNodeType,
  type GeometrySimplifyConfiguration,
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

import { spatialDistanceUnitOptions as unitOptions, spatialUnitHelp } from '../spatialUnits';

const fingerprint = (value: GeometrySimplifyConfiguration) => JSON.stringify(value);

const GeometrySimplifyInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.GeometrySimplify>) => {
  const [form] = Form.useForm<GeometrySimplifyConfiguration>();
  const geometryPolicy = Form.useWatch('geometryPolicy', { form, preserve: true }) ?? node.configuration.geometryPolicy;
  const algorithm = Form.useWatch('algorithm', form) ?? node.configuration.algorithm;
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? '';
  const geometryColumnName = Form.useWatch('geometryColumnName', form) ?? '';
  const toleranceUnit = Form.useWatch('toleranceUnit', form) ?? 'SOURCE_CRS_UNIT';
  const tables = validation?.inputTables ?? [];
  const sourceTable = tables.find((table) => table.name === sourceTableName);
  const geometryColumns = spatialGeometryColumns(sourceTable);
  const geometryColumn = geometryColumns.find((column) => column.name === geometryColumnName);
  const dimensionIssue = geometryPolicy === 'PRESERVE_DIMENSION' && algorithm === 'DOUGLAS_PEUCKER'
    && ['XYM', 'XYZM'].includes(geometryColumn?.geometry?.dimension ?? '');
  const sourceTableMissing = Boolean(sourceTableName && validation && !sourceTable);
  const geometryMissing = Boolean(geometryColumnName && sourceTable && !geometryColumn);
  const isAngular = (geometryColumn?.geometry?.crs.authority === 'EPSG' && geometryColumn.geometry.crs.code === 4326)
    || (sourceTableName === node.configuration.sourceTableName && geometryColumnName === node.configuration.geometryColumnName
      && validation?.issues.some(issue => issue.code === 'GEOMETRY_SIMPLIFY_USES_ANGULAR_UNITS') === true);

  const toConfiguration = (
    values: GeometrySimplifyConfiguration,
  ): GeometrySimplifyConfiguration => ({
    sourceTableName: values.sourceTableName ?? '',
    geometryColumnName: values.geometryColumnName ?? '',
    outputTableName: values.outputTableName?.trim() ?? '',
    outputColumnName: values.outputColumnName?.trim() ?? '',
    algorithm: values.algorithm ?? null,
    tolerance: values.tolerance ?? null,
    toleranceUnit: values.toleranceUnit ?? null,
    ...('geometryPolicy' in values ? { geometryPolicy: values.geometryPolicy } : {}),
  });

  const submit = (values: GeometrySimplifyConfiguration) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
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

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <CanvasNodeValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      <Form<GeometrySimplifyConfiguration>
        form={form}
        layout="vertical"
        autoComplete="off"
        initialValues={node.configuration}
        onFinish={() => submit(form.getFieldsValue(true))}
        onValuesChange={() => {
          onDirtyChange(fingerprint(toConfiguration(form.getFieldsValue(true))) !== fingerprint(node.configuration));
        }}
      >
        <Form.Item
          name="sourceTableName"
          label="来源表"
          rules={[{ required: true, message: '请选择来源表' }]}
          validateStatus={sourceTableMissing ? 'error' : undefined}
          help={sourceTableMissing ? '原来源表已失效，简化配置仍被保留。' : undefined}
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
          name="geometryColumnName"
          label="Geometry 字段"
          rules={[{ required: true, message: '请选择 Geometry 字段' }]}
          validateStatus={geometryMissing ? 'error' : undefined}
          help={geometryMissing ? '原 Geometry 字段已失效，配置仍被保留。' : undefined}
        >
          <Select
            showSearch
            optionFilterProp="label"
            disabled={!sourceTable}
            options={!sourceTable && geometryColumnName
              ? [{ value: geometryColumnName, label: `${geometryColumnName}（${validation ? '来源表不可用' : '等待解析'}）` }]
              : spatialColumnOptions(
              sourceTable?.columns ?? [],
              geometryColumnName,
              (column) => column.fieldType === 'GEOMETRY',
            )}
            placeholder="选择 Geometry 字段"
          />
        </Form.Item>
        {geometryColumn?.geometry && <Space size={4} style={{ marginBottom: 8 }}>
          <Typography.Text type="secondary">{geometryColumn.geometry.crs.authority}:{geometryColumn.geometry.crs.code} · {geometryColumn.geometry.dimension}</Typography.Text>
          <ContextHelp ariaLabel="简化处理坐标系说明" content={`容差在这个坐标系的二维坐标空间内计算，不会自动投影。地理坐标系使用来源角度单位，不一定是度；线性单位由 Compiler 解析，不能把角度当米。${spatialUnitHelp}`} />
        </Space>}
        <Form.Item
          name="algorithm"
          label={(
            <span className="canvas-inspector-field-label">
              简化算法
              <ContextHelp
                ariaLabel="Geometry 简化算法说明"
                presentation="popover"
                content={(
                  <Space orientation="vertical" size={4}>
                    <span>新策略的 Douglas-Peucker 不自动修复退化后无效的面，改用较小容差或单要素拓扑保持。</span>
                    <span>拓扑保持会尽量维持单个要素的有效结构，但计算成本更高。</span>
                    <span>两种算法都不会维护不同要素之间的共享边界。</span>
                  </Space>
                )}
              />
            </span>
          )}
          rules={[{ required: true, message: '请选择简化算法' }]}
        >
          <Segmented
            block
            options={[
              { value: 'DOUGLAS_PEUCKER', label: 'Douglas-Peucker' },
              { value: 'TOPOLOGY_PRESERVING', label: '单要素拓扑保持' },
            ]}
          />
        </Form.Item>
        <Form.Item validateStatus={dimensionIssue ? 'error' : undefined} help={dimensionIssue ? 'Douglas-Peucker 无法保留 M，请选择单要素拓扑保持或输出 XY' : undefined}
          label={<span className="canvas-inspector-field-label">结果维度<ContextHelp ariaLabel="简化结果维度说明"
          content="拓扑保持可保留原顶点 Z/M；Douglas-Peucker 仅可靠保留 XY/XYZ，不能保留 M。不是三维/测地简化。输出 XY 只丢弃结果 Z/M，原字段保留。新策略拒绝无效输入，不隐式修复；不维护不同要素的共边。" /></span>}>
          <UnaryGeometryPolicySelect label="简化结果维度" value={geometryPolicy} onChange={value => {
            form.setFieldValue('geometryPolicy', value);
            onDirtyChange(fingerprint(toConfiguration(form.getFieldsValue(true))) !== fingerprint(node.configuration));
          }} />
        </Form.Item>
        <Form.Item label="简化容差" required>
          <Space.Compact block>
            <Form.Item
              name="tolerance"
              noStyle
              rules={[
                { required: true, message: '请输入简化容差' },
                {
                  validator: async (_, value: number | null) => {
                    if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) {
                      throw new Error('简化容差必须大于 0');
                    }
                  },
                },
              ]}
            >
              <InputNumber aria-label="简化容差" placeholder="输入容差" style={{ width: '55%' }} />
            </Form.Item>
            <Form.Item name="toleranceUnit" noStyle rules={[{ required: true }]}>
              <Select aria-label="简化容差单位" options={unitOptions.map(option => option.value === 'SOURCE_CRS_UNIT' && isAngular ? { ...option, label: '来源 CRS 角度单位' } : option)} style={{ width: '45%' }} />
            </Form.Item>
          </Space.Compact>
          {isAngular && toleranceUnit === 'SOURCE_CRS_UNIT' && (
            <Typography.Text type="warning" className="canvas-field-inline-warning">
              使用来源 CRS 角度单位，简化尺度会随纬度变化；不会自动换算为米。
            </Typography.Text>
          )}
          {isAngular && toleranceUnit !== 'SOURCE_CRS_UNIT' && (
            <Typography.Text type="danger" className="canvas-field-inline-warning">
              地理 CRS 只允许来源 CRS 单位；请先进行空间转换。
            </Typography.Text>
          )}
        </Form.Item>
        <Form.Item
          name="outputColumnName"
          label="结果字段"
          rules={[{ required: true, whitespace: true, message: '请输入结果字段名' }]}
        >
          <Input placeholder="simplified_geometry" />
        </Form.Item>
        <Form.Item
          name="outputTableName"
          label="输出表名"
          rules={[{ required: true, whitespace: true, message: '请输入输出表名' }]}
        >
          <Input placeholder="例如 roads_simplified" />
        </Form.Item>
      </Form>
    </Space>
  );
};

export default GeometrySimplifyInspector;
