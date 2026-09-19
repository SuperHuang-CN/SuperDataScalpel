import { Form, Input, InputNumber, Modal, Select, Space } from 'antd';
import { useState } from 'react';
import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import type { SpatialPlanarGridOptions, SpatialWithinRegions } from '../../canvasTypes';
import { spatialDistanceUnitOptions } from '../spatialUnits';
import { withinGridHelp } from './regions';

/** Mounted only when opened: Cancel never mutates the Inspector form. */
export const RegionsModal = ({ initial, onSave, onCancel }: { initial: SpatialWithinRegions;
  onSave: (value: SpatialWithinRegions) => void; onCancel: () => void }) => {
  const [draft, setDraft] = useState(() => structuredClone(initial));
  const grid = draft.planarGrid ?? { originX: null, originY: null, extent: null };
  const extent = grid.extent ?? { mode: 'DATA_BOUNDS' as const, minX: null, minY: null, maxX: null, maxY: null };
  const setGrid = (value: SpatialPlanarGridOptions) => setDraft({ ...draft, planarGrid: value });
  const coordinate = (key: 'minX' | 'minY' | 'maxX' | 'maxY', label: string) => <Form.Item label={label} required>
    <InputNumber aria-label={label} style={{ width: '100%' }} value={extent[key]}
      status={extent[key] == null || (key.endsWith('X') ? (extent.minX ?? 0) >= (extent.maxX ?? 0) : (extent.minY ?? 0) >= (extent.maxY ?? 0)) ? 'error' : undefined}
      onChange={value => setGrid({ ...grid, extent: { ...extent, [key]: value } })} />
  </Form.Item>;
  return <Modal open width={680} title={<Space>汇总格网配置<ContextHelp ariaLabel="汇总格网说明" content={withinGridHelp} /></Space>}
    okText="保存格网草稿" cancelText="取消" onCancel={onCancel} onOk={() => onSave(draft)}>
    <Form layout="vertical" autoComplete="off" size="small">
      <div className="canvas-spatial-pair-grid">
        <Form.Item label="格网形状" required><Select aria-label="汇总格网形状" value={draft.binShape}
          status={!draft.binShape ? 'error' : undefined} options={[{ value: 'SQUARE', label: '方格' }, { value: 'HEXAGON', label: '六边形' }]}
          onChange={binShape => setDraft({ ...draft, binShape })} /></Form.Item>
        <Form.Item label={draft.binShape === 'HEXAGON' ? '对边距离' : '边长'} required><Space.Compact block>
          <InputNumber aria-label="汇总格网大小" value={draft.binSize} status={draft.binSize == null || draft.binSize <= 0 ? 'error' : undefined}
            onChange={binSize => setDraft({ ...draft, binSize })} />
          <Select aria-label="汇总格网大小单位" value={draft.binSizeUnit} status={!draft.binSizeUnit ? 'error' : undefined}
            options={spatialDistanceUnitOptions} onChange={binSizeUnit => setDraft({ ...draft, binSizeUnit })} />
        </Space.Compact></Form.Item>
        <Form.Item label="原点 X" required><InputNumber aria-label="汇总格网原点 X" value={grid.originX} style={{ width: '100%' }}
          status={grid.originX == null ? 'error' : undefined} onChange={originX => setGrid({ ...grid, originX })} /></Form.Item>
        <Form.Item label="原点 Y" required><InputNumber aria-label="汇总格网原点 Y" value={grid.originY} style={{ width: '100%' }}
          status={grid.originY == null ? 'error' : undefined} onChange={originY => setGrid({ ...grid, originY })} /></Form.Item>
      </div>
      <Form.Item label="范围方式"><Select aria-label="汇总格网范围" value={extent.mode} status={!extent.mode ? 'error' : undefined}
        options={[{ value: 'DATA_BOUNDS', label: '被汇总要素范围' }, { value: 'EXPLICIT_BOUNDS', label: '指定业务范围' }]}
        onChange={mode => setGrid({ ...grid, extent: { ...extent, mode } })} /></Form.Item>
      {extent.mode === 'EXPLICIT_BOUNDS' && <div className="canvas-spatial-pair-grid">
        {coordinate('minX', '最小 X')}{coordinate('minY', '最小 Y')}{coordinate('maxX', '最大 X')}{coordinate('maxY', '最大 Y')}
      </div>}
      <div className="canvas-spatial-pair-grid">
        <Form.Item label="格网 ID 输出字段" required><Input aria-label="格网 ID 输出字段" value={draft.binIdColumnName}
          status={!draft.binIdColumnName.trim() ? 'error' : undefined} onChange={e => setDraft({ ...draft, binIdColumnName: e.target.value })} /></Form.Item>
        <Form.Item label="格网 Geometry 输出字段" required><Input aria-label="格网 Geometry 输出字段" value={draft.binGeometryColumnName}
          status={!draft.binGeometryColumnName.trim() || draft.binGeometryColumnName.toLowerCase() === draft.binIdColumnName.toLowerCase() ? 'error' : undefined}
          onChange={e => setDraft({ ...draft, binGeometryColumnName: e.target.value })} /></Form.Item>
      </div>
    </Form>
  </Modal>;
};
