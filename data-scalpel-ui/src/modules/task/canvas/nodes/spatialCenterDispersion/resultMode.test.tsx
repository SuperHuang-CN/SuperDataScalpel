import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Modal } from 'antd';
import { createRef } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType } from '../../canvasTypes';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { createSpatialCenterDispersionConfiguration } from '../nodeDefaults';
import type { CanvasNodeInspectorHandle } from '../nodeSpec';
import Inspector from './inspector';
import FeatureColumnsModal from './FeatureColumnsModal';

afterEach(() => { Modal.destroyAll(); cleanup(); });
const layout = { x: 0, y: 0, width: 368, height: 224 };
const id = '11111111-1111-4111-8111-111111111111';
const definition = (configuration: unknown, minor: number = CANVAS_SCHEMA_MINOR_VERSION) => ({ schemaVersion: 4, schemaMinorVersion: minor,
  nodes: [{ id, type: CanvasNodeType.SpatialCenterDispersion, name: '中心', layout, configuration }], edges: [] });

describe('center independent results', () => {
  it('gates projection including inactive settings and rejects malformed fields', () => {
    const config = createSpatialCenterDispersionConfiguration();
    const fields = [{ sourceColumnName: 'name', outputColumnName: '', included: true }];
    const value = { ...config, analyses: [{ ...config.analyses[0], centralFeatureColumns: fields }] };
    const parsed = parseCanvasDefinition(definition(value)); expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(value);
    expect(parseCanvasDefinition(definition(value, 31)).success).toBe(false);
    for (const centralFeatureColumns of [{}, [null], [{ ...fields[0], included: 'yes' }]]) {
      expect(parseCanvasDefinition(definition({ ...config, analyses: [{ ...config.analyses[0], centralFeatureColumns }] })).success).toBe(false);
    }
    expect(parseCanvasDefinition(definition({ ...config, analyses: [{ ...config.analyses[0], centralFeatureColumns: [] }] }, 31)).success).toBe(false);
  });

  it('edits a local projection draft, preserves unavailable fields and saves invalid names', async () => {
    const value = [{ sourceColumnName: 'id', outputColumnName: 'id', included: true }, { sourceColumnName: 'old', outputColumnName: 'old', included: true }];
    const save = vi.fn(); const cancel = vi.fn();
    render(<FeatureColumnsModal value={value} columns={[]} geometry="shape" outputGeometry="result" onSave={save} onCancel={cancel} />);
    expect(screen.getByText('old（不可用）')).toBeTruthy();
    fireEvent.change(screen.getByRole('textbox', { name: '原字段输出名 1' }), { target: { value: '' } });
    fireEvent.click(screen.getByRole('checkbox', { name: '保留原字段 2' }));
    fireEvent.click(screen.getByRole('button', { name: '下移原字段 1' }));
    expect(save).not.toHaveBeenCalled(); expect(value[0].outputColumnName).toBe('id');
    fireEvent.click(screen.getByRole('button', { name: /取\s*消/ })); expect(cancel).toHaveBeenCalled(); expect(save).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '保存字段草稿' }));
    expect(save).toHaveBeenCalledWith([{ ...value[1], included: false }, { ...value[0], outputColumnName: '' }]);
  });

  it('preserves unopened and inactive projections when applying the inspector', async () => {
    const config = createSpatialCenterDispersionConfiguration();
    config.analyses[0].centralFeatureColumns = [{ sourceColumnName: 'missing', outputColumnName: '', included: true }];
    const apply = vi.fn(); const ref = createRef<CanvasNodeInspectorHandle>();
    render(<Inspector node={{ id, type: CanvasNodeType.SpatialCenterDispersion, name: '中心', layout, configuration: config }}
      executionMode="BATCH" validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls[0][0].configuration).toEqual(config);
  });

  it('commits the central field modal only when saved and applies through the inspector', async () => {
    const config = createSpatialCenterDispersionConfiguration(); config.analyses[0].kind = 'CENTRAL_FEATURE';
    config.analyses[0].centralFeatureColumns = [{ sourceColumnName: 'id', outputColumnName: 'id', included: true }];
    const apply = vi.fn(); const ref = createRef<CanvasNodeInspectorHandle>();
    render(<Inspector node={{ id, type: CanvasNodeType.SpatialCenterDispersion, name: '中心', layout, configuration: config }}
      executionMode="BATCH" validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
    await userEvent.click(screen.getByRole('button', { name: '设置中央要素原始字段' }));
    fireEvent.change(screen.getByRole('textbox', { name: '原字段输出名 1' }), { target: { value: 'discarded' } });
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /取\s*消/ }));
    await act(async () => { await ref.current?.apply(); }); expect(apply.mock.calls[0][0].configuration).toEqual(config);
    await userEvent.click(screen.getByRole('button', { name: '设置中央要素原始字段' }));
    fireEvent.change(screen.getByRole('textbox', { name: '原字段输出名 1' }), { target: { value: 'selected_id' } });
    fireEvent.click(screen.getByRole('button', { name: '保存字段草稿' }));
    await act(async () => { await ref.current?.apply(); });
    expect(apply.mock.calls[1][0].configuration.analyses[0].centralFeatureColumns).toEqual([{ sourceColumnName: 'id', outputColumnName: 'selected_id', included: true }]);
  });
  it('round-trips explicit result modes while old configurations stay wide', () => {
    const config = createSpatialCenterDispersionConfiguration();
    expect(config.analyses).toHaveLength(1); expect(config.analyses[0].kind).toBe('MEAN_CENTER');
    for (const resultMode of ['ANALYSIS_TABLES', 'LEGACY_WIDE']) {
      const value = { ...config, resultMode };
      const current = parseCanvasDefinition(definition(value)); expect(current.success).toBe(true);
      if (current.success) expect(current.definition.nodes[0].configuration).toEqual(value);
      expect(parseCanvasDefinition(definition(value, 30)).success).toBe(false);
    }
    delete config.resultMode; delete config.analyses[0].outputTableName;
    const old = parseCanvasDefinition(definition(config, 20)); expect(old.success).toBe(true);
    if (old.success) expect(old.definition.nodes[0].configuration).toEqual(config);
    expect(parseCanvasDefinition(definition({ ...config, resultMode: 'AUTO' })).success).toBe(false);
    expect(parseCanvasDefinition(definition({ ...config, analyses: [{ ...config.analyses[0], outputTableName: [] }] })).success).toBe(false);
  });

  it('preserves unmounted analyses and cancels local result edits', async () => {
    const config = createSpatialCenterDispersionConfiguration(); const apply = vi.fn(); const ref = createRef<CanvasNodeInspectorHandle>();
    render(<Inspector node={{ id, type: CanvasNodeType.SpatialCenterDispersion, name: '中心', layout, configuration: config }}
      executionMode="BATCH" validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
    await act(async () => { await ref.current?.apply(); }); expect(apply.mock.calls[0][0].configuration).toEqual(config);
    await userEvent.click(screen.getByRole('button', { name: '设置中心分析项' }));
    let dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByRole('textbox', { name: '结果表 1' }), { target: { value: 'discarded' } });
    fireEvent.click(within(dialog).getByRole('button', { name: /取\s*消/ }));
    await act(async () => { await ref.current?.apply(); }); expect(apply.mock.calls[1][0].configuration).toEqual(config);
    await userEvent.click(screen.getByRole('button', { name: '设置中心分析项' }));
    dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByRole('textbox', { name: 'Geometry 字段 1' }), { target: { value: '' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '保存草稿' }));
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls[2][0].configuration.analyses[0]).toMatchObject({ outputColumnName: '', outputTableName: '' });
  });

  it('requires confirmation and preserves inactive table settings when switching to legacy', async () => {
    const config = createSpatialCenterDispersionConfiguration(); config.outputTableName = 'legacy'; config.analyses[0].outputTableName = 'mean';
    const apply = vi.fn(); const ref = createRef<CanvasNodeInspectorHandle>();
    render(<Inspector node={{ id, type: CanvasNodeType.SpatialCenterDispersion, name: '中心', layout, configuration: config }}
      executionMode="BATCH" validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '中心结果模式' }));
    await userEvent.click(await screen.findByText('旧版多几何宽表', { selector: '.ant-select-item-option-content' }));
    fireEvent.click(await screen.findByRole('button', { name: '确认切换' }));
    await act(async () => { await ref.current?.apply(); });
    expect(apply.mock.calls[0][0].configuration).toEqual({ ...config, resultMode: 'LEGACY_WIDE' });
  });
});
