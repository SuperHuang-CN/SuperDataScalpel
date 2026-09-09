import { Form, Input, InputNumber, Modal, Select, Switch } from 'antd';
import type { SpatialNearestConnectionLines } from '../../canvasTypes';
import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import { spatialDistanceUnitOptions, spatialUnitHelp } from '../spatialUnits';

export function ConnectionLinesModal({ value, geodesic, onSave, onCancel }: {
  value: SpatialNearestConnectionLines;
  geodesic: boolean;
  onSave: (value: SpatialNearestConnectionLines) => void;
  onCancel: () => void;
}) {
  const [form] = Form.useForm<SpatialNearestConnectionLines>();
  const enabled = Form.useWatch('enabled', { form, preserve: true });
  return <Modal open width={560} title="连接线结果配置" okText="保存草稿" cancelText="取消" onCancel={onCancel}
    onOk={() => { void form.validateFields().catch(() => undefined); onSave(form.getFieldsValue(true)); }}>
    <Form form={form} name="nearest_connection_lines" layout="vertical" autoComplete="off" initialValues={value}>
      <Form.Item name="enabled" valuePropName="checked" label={<span className="canvas-inspector-field-label">输出连接线
        <ContextHelp ariaLabel="连接线说明" content="与匹配表使用同一匹配关系，未命中来源不生成线。输出 XY MultiLineString，CRS 与来源相同；独立写出两张表不承诺只执行一次计算或跨表事务。" />
      </span>}><Switch /></Form.Item>
      {enabled && <>
        <div className="canvas-spatial-pair-grid">
          <Form.Item name="outputTableName" label="连接线表名" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="geometryColumnName" label="Geometry 字段" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
        </div>
        {geodesic && <div className="canvas-spatial-pair-grid">
          <Form.Item name="maximumGeodesicSegmentLength" label={<span className="canvas-inspector-field-label">最大测地段长
            <ContextHelp ariaLabel="测地段长说明" content="用于测地路径加密与日期线切分，不改变距离排名。新建初值 10 千米是平台建议，非 ArcGIS 默认；单条连接线最多 100 万顶点。" />
          </span>} rules={[{ required: true, type: 'number', min: Number.MIN_VALUE }]}><InputNumber style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="maximumGeodesicSegmentLengthUnit" label={<span className="canvas-inspector-field-label">段长单位
            <ContextHelp ariaLabel="段长单位说明" content={spatialUnitHelp} /></span>} rules={[{ required: true }]}>
            <Select aria-label="段长单位" options={spatialDistanceUnitOptions.map(option => option.value === 'SOURCE_CRS_UNIT'
              ? { ...option, label: '来源 CRS 单位（测地线不可用）', disabled: true } : option)} />
          </Form.Item>
        </div>}
      </>}
    </Form>
  </Modal>;
}
