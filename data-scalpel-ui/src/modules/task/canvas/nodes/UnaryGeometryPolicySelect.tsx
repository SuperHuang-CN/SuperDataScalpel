import { Modal, Select } from 'antd';
import type { GeometryUnaryPolicy } from '../canvasTypes';

export function UnaryGeometryPolicySelect({ value, onChange, label }: {
  value: GeometryUnaryPolicy | null | undefined; onChange: (policy: GeometryUnaryPolicy) => void; label: string;
}) {
  return <Select<GeometryUnaryPolicy> aria-label={label} value={value ?? 'LEGACY'} style={{ width: '100%' }}
    options={[{ value: 'PRESERVE_DIMENSION', label: '保留维度' }, { value: 'OUTPUT_XY', label: '输出 XY' }, { value: 'LEGACY', label: '旧版' }]}
    onChange={policy => Modal.confirm({ title: '切换几何策略？',
      content: '输出 XY 会丢弃结果的 Z/M，但保留原 Geometry 字段。新策略拒绝无效输入，不会自动修复；旧版保留原行为。请检查下游。',
      okText: '确认切换', onOk: () => onChange(policy) })} />;
}
