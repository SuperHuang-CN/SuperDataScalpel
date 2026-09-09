import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Modal } from 'antd';
import { createRef } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CanvasNodeType, type CanvasNodeValidationResult, type SpatialBinAggregateConfiguration } from '../../canvasTypes';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { createSpatialBinAggregateConfiguration } from '../nodeDefaults';
import type { CanvasNodeInspectorHandle } from '../nodeSpec';
import { h3SizeSummary, parseH3 } from './h3';
import Inspector from './inspector';

afterEach(() => { Modal.destroyAll(); cleanup(); });
const id = '11111111-1111-4111-8111-111111111111';
const layout = { x: 25, y: 36, width: 360, height: 224 };
const definition = (configuration: unknown, schemaMinorVersion = 33) => ({ schemaVersion: 4, schemaMinorVersion,
  nodes: [{ id, type: CanvasNodeType.SpatialBinAggregate, name: '格网', layout, configuration }], edges: [] });

function mount(config: SpatialBinAggregateConfiguration, validation?: CanvasNodeValidationResult) {
  const apply = vi.fn(); const ref = createRef<CanvasNodeInspectorHandle>();
  render(<Inspector node={{ id, type: CanvasNodeType.SpatialBinAggregate, name: '格网', layout, configuration: config }}
    executionMode="BATCH" validation={validation} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
  return { apply, ref };
}

describe('H3 grid options', () => {
  it('round trips direct, approximate and inactive options with a 4.33 gate', () => {
    for (const binShape of ['SQUARE', 'H3'] as const) {
      for (const mode of ['RESOLUTION', 'APPROXIMATE_SIZE'] as const) {
        const config = { ...createSpatialBinAggregateConfiguration(), binShape, h3: { mode, resolution: 8 } };
        const parsed = parseCanvasDefinition(definition(config)); expect(parsed.success).toBe(true);
        if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(config);
        expect(parseCanvasDefinition(definition(config, 32)).success).toBe(false);
      }
    }
    const old = parseCanvasDefinition(definition(createSpatialBinAggregateConfiguration(), 21));
    expect(old.success).toBe(true); if (old.success) expect(old.definition.nodes[0].configuration).not.toHaveProperty('h3');
    expect(parseCanvasDefinition(definition({ ...createSpatialBinAggregateConfiguration(), binShape: 'H3' }, 32)).success).toBe(false);
  });

  it('rejects unsafe structure but retains invalid resolution drafts', () => {
    for (const h3 of [[], 'H3', { mode: 'AUTO', resolution: 8 }, { mode: 'RESOLUTION', resolution: 1.5 }]) {
      const errors: string[] = []; parseH3({ h3 }, 'configuration', errors); expect(errors.length).toBeGreaterThan(0);
    }
    for (const resolution of [null, -1, 16]) {
      const c = { ...createSpatialBinAggregateConfiguration(), binShape: 'H3', h3: { mode: 'RESOLUTION', resolution } };
      expect(parseCanvasDefinition(definition(c)).success).toBe(true);
    }
    const config = { ...createSpatialBinAggregateConfiguration(), h3: { mode: 'RESOLUTION' as const, resolution: 0 } };
    expect(h3SizeSummary(config)).toBe('H3 分辨率 0');
  });

  it('preserves unopened options and applies incomplete resolution drafts', async () => {
    const config: SpatialBinAggregateConfiguration = { ...createSpatialBinAggregateConfiguration(), binShape: 'H3',
      binSize: 1700, h3: { mode: 'RESOLUTION', resolution: null }, includeEmptyBins: true };
    const { apply, ref } = mount(config);
    expect(screen.getByText('H3 暂不支持空格网，请关闭此项。')).toBeTruthy();
    expect(screen.getByRole('spinbutton', { name: 'H3 分辨率' })).toBeTruthy();
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls[0][0].configuration).toEqual(config);
    fireEvent.change(screen.getByRole('spinbutton', { name: 'H3 分辨率' }), { target: { value: '0' } });
    await act(async () => { await ref.current?.apply(); });
    expect(apply.mock.calls[1][0].configuration.h3.resolution).toBe(0);
    expect(apply.mock.calls[1][0].configuration.binSize).toBe(1700);
  });

  it('confirms shape changes and keeps inactive H3 and planar values', async () => {
    const config: SpatialBinAggregateConfiguration = { ...createSpatialBinAggregateConfiguration(), binShape: 'H3',
      binSize: 2400, h3: { mode: 'RESOLUTION', resolution: 6 } };
    const { apply, ref } = mount(config);
    await userEvent.click(screen.getByText('方格'));
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: /取\s*消/ }));
    await act(async () => { await ref.current?.apply(); }); expect(apply.mock.calls[0][0].configuration).toEqual(config);
    await userEvent.click(screen.getByText('方格'));
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '确认切换' }));
    await act(async () => { await ref.current?.apply(); });
    expect(apply.mock.calls[1][0].configuration).toEqual({ ...config, binShape: 'SQUARE' });
    expect(screen.queryByRole('spinbutton', { name: 'H3 分辨率' })).toBeNull();
  });

  it('does not visually default an unconfigured shape and initializes H3 without guessing a level', async () => {
    const config = { ...createSpatialBinAggregateConfiguration(), binShape: null };
    const { apply, ref } = mount(config);
    expect(screen.getByText('请选择格网形状')).toBeTruthy();
    await userEvent.click(screen.getByText('H3'));
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '确认切换' }));
    await act(async () => { await ref.current?.apply(); });
    expect(apply.mock.calls[0][0].configuration).toEqual({ ...config, binShape: 'H3', h3: { mode: 'RESOLUTION', resolution: null } });
  });

  it('switches size modes without losing resolution and does not guess the resolved level', async () => {
    const config: SpatialBinAggregateConfiguration = { ...createSpatialBinAggregateConfiguration(), binShape: 'H3',
      binSize: 1000, h3: { mode: 'RESOLUTION', resolution: 7 } };
    const { apply, ref } = mount(config);
    await userEvent.click(screen.getByRole('combobox', { name: 'H3 大小方式' }));
    await userEvent.click(screen.getByText('近似对边距离'));
    expect(screen.getByText('应用后解析分辨率')).toBeTruthy();
    expect(screen.queryByRole('spinbutton', { name: 'H3 分辨率' })).toBeNull();
    await act(async () => { await ref.current?.apply(); });
    expect(apply.mock.calls[0][0].configuration).toEqual({ ...config, h3: { mode: 'APPROXIMATE_SIZE', resolution: 7 } });
  });

  it('shows only the matching compiled level and hides it when the draft changes', async () => {
    const config: SpatialBinAggregateConfiguration = { ...createSpatialBinAggregateConfiguration(), binShape: 'H3',
      binSize: 1000, h3: { mode: 'APPROXIMATE_SIZE', resolution: null }, outputTableName: 'bins' };
    const comment = 'H3 分辨率 8 · 估算平均对边距离 920.658 米';
    mount(config, { nodeId: id, issues: [], inputTables: [], outputTables: [{
      name: 'bins', origin: null, datasetKind: 'BOUNDED', eventTimeColumn: null, watermarkDelay: null,
      columns: [{ name: config.binIdColumnName, fieldType: 'STRING', length: 64, precision: null, scale: null,
        nullable: false, autoIncrement: false, generated: false, defaultValue: null, geometry: null, comment }],
    }] });
    expect(screen.getByText(comment)).toBeTruthy();
    fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '2000' } });
    expect(await screen.findByText('应用后解析分辨率')).toBeTruthy();
    expect(screen.queryByText(comment)).toBeNull();
  });
});
