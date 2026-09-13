import { CompactAlert as Alert, ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import { Button, Form, Input, Modal, Select, Space, Switch, Typography } from 'antd';
import { useEffect } from 'react';
import type { SpatialJoinDistanceOutput } from '../../canvasTypes';
import { spatialDistanceUnitOptions, spatialUnitHelp } from '../spatialUnits';
import { createSpatialJoinDistanceOutput } from './spatialNear';

interface Props {
  open: boolean;
  value: SpatialJoinDistanceOutput | null | undefined;
  spatialNearEnabled: boolean;
  temporalNearEnabled: boolean;
  oneToMany: boolean;
  geodesic: boolean;
  onCancel: () => void;
  onRemove: () => void;
  onSave: (value: SpatialJoinDistanceOutput) => void;
}

const durationUnits = [
  { value: 'MILLISECONDS', label: '毫秒' },
  { value: 'SECONDS', label: '秒' },
  { value: 'MINUTES', label: '分钟' },
  { value: 'HOURS', label: '小时' },
  { value: 'DAYS', label: '固定 24 小时日' },
  { value: 'WEEKS', label: '固定 7 天周' },
];

export const DistanceOutputModal = ({
  open,
  value,
  spatialNearEnabled,
  temporalNearEnabled,
  oneToMany,
  geodesic,
  onCancel,
  onRemove,
  onSave,
}: Props) => {
  const [form] = Form.useForm<SpatialJoinDistanceOutput>();
  const enabled = Form.useWatch('enabled', form);

  useEffect(() => {
    if (!open) return;
    form.setFieldsValue({
      ...createSpatialJoinDistanceOutput(),
      spatialDistanceUnit: geodesic ? 'METERS' : 'SOURCE_CRS_UNIT',
      ...value,
    });
  }, [form, geodesic, open, value]);

  const save = () => {
    const fields = form.getFieldsValue(true);
    onSave({
      enabled: fields.enabled === true,
      spatialDistanceColumnName: fields.spatialDistanceColumnName?.trim() ?? '',
      spatialDistanceUnit: fields.spatialDistanceUnit ?? null,
      temporalDifferenceColumnName: fields.temporalDifferenceColumnName?.trim() ?? '',
      temporalDifferenceUnit: fields.temporalDifferenceUnit ?? null,
    });
  };

  return (
    <Modal
      open={open}
      width={680}
      title="距离输出"
      onCancel={onCancel}
      footer={[
        value ? <Button key="remove" danger onClick={onRemove}>清除配置</Button> : null,
        <Button key="cancel" onClick={onCancel}>取消</Button>,
        <Button key="save" type="primary" onClick={save}>保存草稿</Button>,
      ]}
    >
      <Space orientation="vertical" size={12} style={{ width: '100%' }}>
        {!oneToMany && (
          <Alert type="warning" showIcon title="距离输出只支持一对多" description="一对一仍可使用 Near 过滤，但不能启用距离输出。当前草稿会保留，发布时按配置错误处理。" />
        )}
        {!spatialNearEnabled && !temporalNearEnabled && (
          <Alert type="warning" showIcon title="尚未启用 Near 条件" description="先配置空间 Near，或把时间关系设为 NEAR / NEAR BEFORE / NEAR AFTER。" />
        )}
        <Form form={form} layout="vertical" autoComplete="off">
          <Form.Item name="enabled" label="写入结果字段" valuePropName="checked">
            <Switch checkedChildren="启用" unCheckedChildren="关闭" />
          </Form.Item>
          {enabled && spatialNearEnabled && (
            <div className="canvas-spatial-pair-grid">
              <Form.Item name="spatialDistanceColumnName" label="空间距离字段名">
                <Input placeholder="join_distance" />
              </Form.Item>
              <Form.Item
                name="spatialDistanceUnit"
                label={<span className="canvas-inspector-field-label">空间距离单位
                  <ContextHelp ariaLabel="空间连接距离输出单位说明" content={spatialUnitHelp} />
                </span>}
              >
                <Select options={spatialDistanceUnitOptions.map((option) => ({
                  ...option,
                  disabled: geodesic && option.value === 'SOURCE_CRS_UNIT',
                }))} />
              </Form.Item>
            </div>
          )}
          {enabled && temporalNearEnabled && (
            <div className="canvas-spatial-pair-grid">
              <Form.Item name="temporalDifferenceColumnName" label="时间差字段名">
                <Input placeholder="join_time_difference" />
              </Form.Item>
              <Form.Item name="temporalDifferenceUnit" label="时间差单位">
                <Select options={durationUnits} />
              </Form.Item>
            </div>
          )}
        </Form>
        <Typography.Text type="secondary">
          空间 Near 与时间 Near 同时启用时会输出两个独立字段；相交时间区间的时间差为 0。结果使用 DECIMAL(38,12)。
        </Typography.Text>
      </Space>
    </Modal>
  );
};
