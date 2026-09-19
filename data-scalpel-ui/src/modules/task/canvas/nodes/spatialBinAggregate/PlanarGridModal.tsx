import { Button, Form, InputNumber, Modal, Select, Space, Typography } from 'antd';
import { useState } from 'react';
import type { SpatialPlanarGridOptions } from '../../canvasTypes';
import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import { newPlanarGrid, planarGridHelp, planarGridProblems } from './planarGrid';

/** Mounted only when opened, so cancel never modifies the owning Inspector form. */
export function PlanarGridModal({ initialValue, onSave, onCancel }: {
  initialValue: SpatialPlanarGridOptions | null | undefined;
  onSave: (value: SpatialPlanarGridOptions | null) => void;
  onCancel: () => void;
}) {
  const [draft, setDraft] = useState<SpatialPlanarGridOptions>(() => structuredClone(initialValue ?? newPlanarGrid()));
  const problems = planarGridProblems(draft);
  const extent = draft.extent;
  return <Modal open width={680} title={<Space>格网范围与对齐<ContextHelp ariaLabel="格网范围与对齐说明" content={planarGridHelp} /></Space>}
    okText="保存范围草稿" cancelText="取消" onCancel={onCancel} onOk={() => onSave(draft)}
    footer={(_, { OkBtn, CancelBtn }) => <Space>
      <Button onClick={() => Modal.confirm({ title: '恢复旧版格网定位？',
        content: '将删除原点和范围配置，恢复 (0,0) 与来源范围，格网 ID 会改变。', okText: '恢复旧版',
        onOk: () => onSave(null),
      })}>恢复旧版</Button><CancelBtn /><OkBtn />
    </Space>}>
    <Form layout="vertical" autoComplete="off" size="small">
      {!initialValue && <Typography.Text type="warning">启用显式对齐会改变格网 ID，即使原点仍为 (0,0)。</Typography.Text>}
      <div className="canvas-spatial-pair-grid">
        {(['originX', 'originY'] as const).map(key => <Form.Item key={key} label={`原点 ${key === 'originX' ? 'X' : 'Y'}`}
          required validateStatus={draft[key] == null ? 'error' : undefined}>
          <InputNumber aria-label={`格网原点 ${key === 'originX' ? 'X' : 'Y'}`} style={{ width: '100%' }} value={draft[key]}
            onChange={value => setDraft({ ...draft, [key]: value })} />
        </Form.Item>)}
      </div>
      <Form.Item label="格网范围">
        <Select aria-label="格网范围方式" value={extent?.mode ?? 'DATA_BOUNDS'} options={[
          { value: 'DATA_BOUNDS', label: '来源点范围（占用索引包络）' },
          { value: 'EXPLICIT_BOUNDS', label: '显式业务范围（来源 CRS 坐标）' },
        ]} onChange={mode => setDraft({ ...draft, extent: { minX: null, minY: null, maxX: null, maxY: null, ...extent, mode } })} />
      </Form.Item>
      {extent?.mode === 'EXPLICIT_BOUNDS' && <div className="canvas-spatial-pair-grid">
        {(['minX', 'minY', 'maxX', 'maxY'] as const).map(key => <Form.Item key={key}
          label={`${key.startsWith('min') ? '最小' : '最大'} ${key.endsWith('X') ? 'X' : 'Y'}`} required
          validateStatus={extent[key] == null || (extent.minX != null && extent.maxX != null && extent.minX >= extent.maxX)
            || (extent.minY != null && extent.maxY != null && extent.minY >= extent.maxY) ? 'error' : undefined}>
          <InputNumber aria-label={`格网范围 ${key}`} style={{ width: '100%' }} value={extent[key]}
            onChange={value => setDraft({ ...draft, extent: { ...extent, [key]: value } })} />
        </Form.Item>)}
      </div>}
      {problems.length > 0 && <Typography.Text type="danger">{problems.join('；')}</Typography.Text>}
    </Form>
  </Modal>;
}
