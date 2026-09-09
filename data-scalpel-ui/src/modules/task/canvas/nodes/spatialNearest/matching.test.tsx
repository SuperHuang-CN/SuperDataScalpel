import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Modal } from 'antd';
import { createRef } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType, type SpatialNearestConfiguration } from '../../canvasTypes';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { createSpatialNearestConfiguration } from '../nodeDefaults';
import type { CanvasNodeInspectorHandle } from '../nodeSpec';
import Inspector from './inspector';
import { outputsNearestLines, usesExactNearest } from './matching';

afterEach(() => { Modal.destroyAll(); cleanup(); });
const layout = { x: 0, y: 0, width: 368, height: 216 };
const nodeId = '11111111-1111-4111-8111-111111111111';
const definition = (configuration: unknown, minor: number = CANVAS_SCHEMA_MINOR_VERSION) => ({
  schemaVersion: 4, schemaMinorVersion: minor,
  nodes: [{ id: nodeId, type: CanvasNodeType.SpatialNearest, name: '最近邻', layout, configuration }], edges: [],
});
const mount = (configuration: SpatialNearestConfiguration) => {
  const ref = createRef<CanvasNodeInspectorHandle>(); const apply = vi.fn();
  render(<Inspector node={{ id: nodeId, type: CanvasNodeType.SpatialNearest, name: '最近邻', layout, configuration }}
    executionMode="BATCH" validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
  return { ref, apply };
};

describe('nearest explicit matching', () => {
  it('preserves old semantics and gates every explicit strategy at 4.30', () => {
    const config = createSpatialNearestConfiguration();
    expect(config.distanceMethod).toBeNull(); expect(usesExactNearest(config)).toBe(true); expect(outputsNearestLines(config)).toBe(false);
    for (const semantics of [null, 'EXACT_DISTANCE', 'LEGACY_KNN']) {
      const draft = { ...config, nearestCount: 0, maximumDistance: -3, matching: { ...config.matching, semantics } };
      const parsed = parseCanvasDefinition(definition(draft));
      expect(parsed.success).toBe(true);
      if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(draft);
      expect(parseCanvasDefinition(definition(draft, 29)).success).toBe(false);
    }
    delete config.matching;
    expect(usesExactNearest(config)).toBe(false);
    const old = parseCanvasDefinition(definition(config, 11));
    expect(old.success).toBe(true);
    if (old.success) expect(old.definition.nodes[0].configuration).toEqual(config);
    expect(parseCanvasDefinition(definition({ ...config, matching: null }, 11)).success).toBe(true);
    for (const matching of [[], 'bad', { semantics: 'AUTO' }, { sourceIdColumnName: 42 }, { connectionLines: [] }, { connectionLines: { enabled: 'true' } }, { connectionLines: { maximumGeodesicSegmentLength: '10' } }]) {
      expect(parseCanvasDefinition(definition({ ...config, matching })).success).toBe(false);
    }
  });

  it('preserves unmounted output projection and requires confirmation to change semantics', async () => {
    const config = createSpatialNearestConfiguration();
    config.outputColumns = [{ sourceSide: 'LEFT', sourceColumnName: 'id', outputColumnName: 'source_id', included: true }];
    const { ref, apply } = mount(config);
    expect(screen.getByText('1 / 1')).toBeTruthy();
    await act(async () => { await ref.current?.apply(); });
    expect(apply.mock.calls[0][0].configuration).toEqual(config);
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '匹配策略' }));
    await userEvent.click(await screen.findByText('旧版 KNN', { selector: '.ant-select-item-option-content' }));
    fireEvent.click(await screen.findByRole('button', { name: /取\s*消/ }));
    await act(async () => { await ref.current?.apply(); });
    expect(apply.mock.calls[1][0].configuration).toEqual(config);
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '匹配策略' }));
    await userEvent.click(await screen.findByText('旧版 KNN', { selector: '.ant-select-item-option-content' }));
    fireEvent.click(await screen.findByRole('button', { name: '确认切换' }));
    await act(async () => { await ref.current?.apply(); });
    expect(apply.mock.calls[2][0].configuration).toEqual({ ...config, matching: { ...config.matching, semantics: 'LEGACY_KNN' } });
  });

  it('connection modal cancels locally, saves invalid drafts, and retains disabled options', async () => {
    const config = createSpatialNearestConfiguration(); config.distanceMethod = 'GEODESIC';
    const { ref, apply } = mount(config);
    await userEvent.click(screen.getByRole('button', { name: '设置连接线' }));
    let dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('switch'));
    fireEvent.change(within(dialog).getByRole('textbox', { name: '连接线表名' }), { target: { value: 'lines' } });
    fireEvent.click(within(dialog).getByRole('button', { name: /取\s*消/ }));
    await act(async () => { await ref.current?.apply(); });
    expect(apply.mock.calls[0][0].configuration).toEqual(config);
    await userEvent.click(screen.getByRole('button', { name: '设置连接线' }));
    dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('switch'));
    fireEvent.change(within(dialog).getByRole('spinbutton'), { target: { value: '-1' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '保存草稿' }));
    await act(async () => { await ref.current?.apply(); });
    expect(apply.mock.calls[1][0].configuration.matching.connectionLines).toMatchObject({ enabled: true, outputTableName: '', maximumGeodesicSegmentLength: -1 });
    await userEvent.click(screen.getByRole('button', { name: '设置连接线' }));
    dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('switch'));
    fireEvent.click(within(dialog).getByRole('button', { name: '保存草稿' }));
    await act(async () => { await ref.current?.apply(); });
    expect(apply.mock.calls[2][0].configuration.matching.connectionLines).toMatchObject({ enabled: false, maximumGeodesicSegmentLength: -1 });
  });
});
