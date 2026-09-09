import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { Modal } from 'antd';
import { createRef, useState } from 'react';
import { afterEach, expect, it, vi } from 'vitest';
import { CanvasNodeType, type SpatialTemporalSlicing } from '../canvasTypes';
import { parseCanvasDefinition } from '../canvasDefinitionIO';
import { createSpatialBinAggregateConfiguration, createSpatialSummarizeWithinConfiguration } from './nodeDefaults';
import { createSpatialTemporalSlicing } from './spatialAggregationOptions';
import { parseCalendarWindow, temporalWindowLabel } from './spatialCalendarWindow';
import { SpatialTemporalSlicingEditor } from './spatialAggregationShared';
import type { CanvasNodeInspectorHandle } from './nodeSpec';
import BinInspector from './spatialBinAggregate/inspector';
import WithinInspector from './spatialSummarizeWithin/inspector';

afterEach(() => { Modal.destroyAll(); cleanup(); });
const id = '11111111-1111-4111-8111-111111111111';
const layout = { x: 17, y: 22, width: 360, height: 224 };
const time: SpatialTemporalSlicing = { ...createSpatialTemporalSlicing(), interval: 2, intervalUnit: 'HOURS', repeatInterval: 1,
  repeatIntervalUnit: 'DAYS', calendar: { mode: 'CALENDAR', intervalUnit: 'MONTHS', repeatIntervalUnit: 'YEARS' } };

it('gates active and inactive options at 4.39 on both nodes while preserving old fixed JSON', () => {
  for (const type of [CanvasNodeType.SpatialBinAggregate, CanvasNodeType.SpatialSummarizeWithin]) {
    const defaults = type === CanvasNodeType.SpatialBinAggregate ? createSpatialBinAggregateConfiguration() : createSpatialSummarizeWithinConfiguration();
    for (const mode of ['CALENDAR', 'FIXED_DURATION'] as const) {
      const configuration = { ...defaults, temporalSlicing: { ...time, calendar: { ...time.calendar!, mode } } };
      const definition = { schemaVersion: 4, schemaMinorVersion: 39, nodes: [{ id, name: '空间', type, layout, configuration }], edges: [] };
      const parsed = parseCanvasDefinition(definition); expect(parsed.success).toBe(true);
      if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(configuration);
      const old = parseCanvasDefinition({ ...definition, schemaMinorVersion: 38 }); expect(old.success).toBe(false);
      if (!old.success) expect(old.errors.join(' ')).toContain('SPATIAL_CALENDAR_WINDOW_REQUIRE_SCHEMA_VERSION');
      const legacy = parseCanvasDefinition({ ...definition, schemaMinorVersion: 38,
        nodes: [{ ...definition.nodes[0], configuration: { ...defaults, temporalSlicing: createSpatialTemporalSlicing() } }] });
      expect(legacy.success).toBe(true);
      if (legacy.success) expect(legacy.definition.nodes[0].configuration).toHaveProperty('temporalSlicing', createSpatialTemporalSlicing());
    }
  }
});

it('rejects malformed options and preserves incomplete mode and units for compilation', () => {
  for (const calendar of [[], 2, { mode: 'AUTO' }, { intervalUnit: 'CENTURIES' }]) {
    const errors: string[] = []; parseCalendarWindow({ calendar }, 'time', errors); expect(errors.length).toBeGreaterThan(0);
  }
  const errors: string[] = [];
  expect(parseCalendarWindow({ calendar: { mode: null, intervalUnit: null } }, 'time', errors)).toEqual({ calendar: { mode: null, intervalUnit: null, repeatIntervalUnit: null } });
  expect(errors).toEqual([]); expect(temporalWindowLabel(time)).toBe('2 日历月 · 日历');
  expect(temporalWindowLabel({ ...time, calendar: { ...time.calendar!, mode: null } })).toBe('待选窗口语义');
});

it('requires explicit semantic confirmation and retains inactive units and numeric amounts', async () => {
  const changed = vi.fn();
  function Harness() {
    const [value, setValue] = useState(time);
    return <SpatialTemporalSlicingEditor value={value} columns={[]} onChange={v => { setValue(v); changed(v); }} />;
  }
  render(<Harness />);
  fireEvent.mouseDown(screen.getByRole('combobox', { name: '时间窗口语义' }));
  fireEvent.click(await screen.findByText('固定时长', { selector: '.ant-select-item-option-content' }));
  expect(changed).not.toHaveBeenCalled();
  const dialog = await screen.findByRole('dialog'); fireEvent.click(within(dialog).getByRole('button', { name: '确认切换' }));
  await waitFor(() => expect(screen.getByRole('combobox', { name: '固定窗口单位' })).toBeTruthy());
  expect(changed.mock.calls[0][0]).toEqual({ ...time, calendar: { ...time.calendar, mode: 'FIXED_DURATION' } });
  fireEvent.mouseDown(screen.getByRole('combobox', { name: '时间窗口语义' }));
  fireEvent.click(await screen.findByText('日历周期', { selector: '.ant-select-item-option-content' }));
  await waitFor(() => expect(screen.getByRole('button', { name: '确认切换' })).toBeTruthy());
  fireEvent.click(screen.getByRole('button', { name: '确认切换' }));
  await waitFor(() => expect(changed).toHaveBeenCalledTimes(2)); expect(changed.mock.calls[1][0]).toEqual(time);
});

for (const type of [CanvasNodeType.SpatialBinAggregate, CanvasNodeType.SpatialSummarizeWithin]) {
  it(`${type} discards canceled temporal edits but permits explicitly saving invalid drafts`, async () => {
    const apply = vi.fn(), ref = createRef<CanvasNodeInspectorHandle>();
    const props = { executionMode: 'BATCH' as const, validation: undefined, validationUnavailableMessage: null,
      onApply: apply, onDirtyChange: vi.fn(), inspectorRef: ref };
    if (type === CanvasNodeType.SpatialBinAggregate) render(<BinInspector {...props} node={{ id, type, name: '格网', layout,
      configuration: { ...createSpatialBinAggregateConfiguration(), temporalSlicing: time } }} />);
    else render(<WithinInspector {...props} node={{ id, type, name: '区域', layout,
      configuration: { ...createSpatialSummarizeWithinConfiguration(), temporalSlicing: time } }} />);
    fireEvent.click(screen.getByRole('button', { name: '设置时间切片' }));
    fireEvent.change(screen.getByRole('spinbutton', { name: '窗口长度' }), { target: { value: '7' } });
    fireEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls[0][0].configuration.temporalSlicing).toEqual(time);
    fireEvent.click(screen.getByRole('button', { name: '设置时间切片' }));
    expect(screen.getByRole('spinbutton', { name: '窗口长度' }).getAttribute('value')).toBe('2');
    fireEvent.change(screen.getByRole('spinbutton', { name: '窗口长度' }), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: '保存草稿' }));
    await act(async () => { expect(await ref.current?.apply()).toBe(true); });
    expect(apply.mock.calls.at(-1)?.[0].configuration.temporalSlicing).toEqual({ ...time, interval: 0 });
  });
}
