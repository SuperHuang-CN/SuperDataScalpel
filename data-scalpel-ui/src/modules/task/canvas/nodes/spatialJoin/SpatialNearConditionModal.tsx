import { CompactAlert as Alert, ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import { Button, Form, InputNumber, Modal, Select, Space, Typography } from 'antd';
import { useEffect } from 'react';
import type {
  CanvasTableSchema,
  SpatialJoinSpatialNearCondition,
} from '../../canvasTypes';
import { spatialColumnOptions } from '../spatialInspectorOptions';
import { spatialDistanceUnitOptions, spatialUnitHelp } from '../spatialUnits';
import { createSpatialJoinSpatialNear } from './spatialNear';

interface Props {
  open: boolean;
  value: SpatialJoinSpatialNearCondition | null | undefined;
  leftTable: CanvasTableSchema | undefined;
  rightTable: CanvasTableSchema | undefined;
  onCancel: () => void;
  onRemove: () => void;
  onSave: (value: SpatialJoinSpatialNearCondition) => void;
}

export const SpatialNearConditionModal = ({
  open,
  value,
  leftTable,
  rightTable,
  onCancel,
  onRemove,
  onSave,
}: Props) => {
  const [form] = Form.useForm<SpatialJoinSpatialNearCondition>();
  const distanceMethod = Form.useWatch('distanceMethod', form);
  const leftGeometryColumnName = Form.useWatch('leftGeometryColumnName', form) ?? '';
  const rightGeometryColumnName = Form.useWatch('rightGeometryColumnName', form) ?? '';

  useEffect(() => {
    if (!open) return;
    form.setFieldsValue({
      ...createSpatialJoinSpatialNear(),
      ...value,
    });
  }, [form, open, value]);

  const save = () => {
    const fields = form.getFieldsValue(true);
    onSave({
      leftGeometryColumnName: fields.leftGeometryColumnName ?? '',
      rightGeometryColumnName: fields.rightGeometryColumnName ?? '',
      distanceMethod: fields.distanceMethod ?? null,
      distance: fields.distance ?? null,
      distanceUnit: fields.distanceUnit ?? null,
    });
  };

  return (
    <Modal
      open={open}
      width={680}
      title="空间 Near"
      onCancel={onCancel}
      footer={[
        value ? <Button key="remove" danger onClick={onRemove}>移除空间 Near</Button> : null,
        <Button key="cancel" onClick={onCancel}>取消</Button>,
        <Button key="save" type="primary" onClick={save}>保存草稿</Button>,
      ]}
    >
      <Space orientation="vertical" size={12} style={{ width: '100%' }}>
        <Alert
          type="info"
          showIcon
          title="空间 Near 与拓扑、属性和时间条件按 AND 组合"
          description="Near 在来源 CRS 中计算；Near Geodesic 使用 EPSG:4326 XY Geometry 的真实最近位置，不使用质心距离。"
        />
        <Form form={form} layout="vertical" autoComplete="off">
          <div className="canvas-spatial-pair-grid">
            <Form.Item name="leftGeometryColumnName" label="目标表 Geometry">
              <Select
                showSearch
                optionFilterProp="label"
                disabled={!leftTable}
                options={spatialColumnOptions(
                  leftTable?.columns ?? [],
                  leftGeometryColumnName,
                  (column) => column.fieldType === 'GEOMETRY',
                )}
              />
            </Form.Item>
            <Form.Item name="rightGeometryColumnName" label="连接表 Geometry">
              <Select
                showSearch
                optionFilterProp="label"
                disabled={!rightTable}
                options={spatialColumnOptions(
                  rightTable?.columns ?? [],
                  rightGeometryColumnName,
                  (column) => column.fieldType === 'GEOMETRY',
                )}
              />
            </Form.Item>
          </div>
          <Form.Item
            name="distanceMethod"
            label={<span className="canvas-inspector-field-label">距离方法
              <ContextHelp ariaLabel="空间 Near 距离方法说明" content="PLANAR 直接使用来源 CRS 的二维坐标距离；GEODESIC 仅支持 EPSG:4326 XY，并计算两个 Geometry 真实最近位置之间的 WGS84 椭球距离。" />
            </span>}
          >
            <Select options={[
              { value: 'PLANAR', label: 'Near · 平面距离' },
              { value: 'GEODESIC', label: 'Near Geodesic · WGS84 测地距离' },
            ]} />
          </Form.Item>
          <div className="canvas-spatial-pair-grid">
            <Form.Item name="distance" label="最大距离">
              <InputNumber min={0} precision={12} style={{ width: '100%' }} placeholder="有限正数" />
            </Form.Item>
            <Form.Item
              name="distanceUnit"
              label={<span className="canvas-inspector-field-label">距离单位
                <ContextHelp ariaLabel="空间 Near 距离单位说明" content={spatialUnitHelp} />
              </span>}
            >
              <Select options={spatialDistanceUnitOptions.map((option) => ({
                ...option,
                disabled: distanceMethod === 'GEODESIC' && option.value === 'SOURCE_CRS_UNIT',
              }))} />
            </Form.Item>
          </div>
        </Form>
        <Typography.Text type="secondary">
          NULL、Empty、无效 Geometry 不产生匹配；阈值边界包含等于。配置错误可以先保存，发布时会精确指出字段。
        </Typography.Text>
      </Space>
    </Modal>
  );
};
