import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { Modal } from 'antd';
import { createRef } from 'react';
import { afterEach, expect, it, vi } from 'vitest';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType, type CanvasColumnSchema, type TrackIncidentWindow } from '../../canvasTypes';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { createTrackDetectIncidentsConfiguration } from '../nodeDefaults';
import type { CanvasNodeInspectorHandle } from '../nodeSpec';
import { incidentConditionColumns, incidentWindowErrors } from './conditionWindows';
import { IncidentWindowsModal } from './IncidentWindowsModal';
import { parseIncidentLifecycleOptions } from './readConfiguration';
import Inspector from './inspector';
import { trackDetectIncidentsCanvasView } from './canvasView';

afterEach(() => { Modal.destroyAll(); cleanup(); });
const window: TrackIncidentWindow = { bindingName: 'past_mean', sourceColumnName: 'speed', kind: 'MEAN', startOffset: -5, endOffset: 0 };
const column: CanvasColumnSchema = { name: 'speed', fieldType: 'DOUBLE', length: null, precision: null, scale: null, nullable: true,
  defaultValue: null, autoIncrement: false, generated: false, comment: null, geometry: null };
const id = '11111111-1111-4111-8111-111111111111';
const layout = { x: 17, y: 29, width: 360, height: 216 };
const definition = (configuration: unknown, minor: number = CANVAS_SCHEMA_MINOR_VERSION) => ({ schemaVersion: 4, schemaMinorVersion: minor,
  nodes: [{ id, name: '事件', type: CanvasNodeType.TrackDetectIncidents, layout, configuration }], edges: [] });

it('round trips active and inactive windows at 4.46 and keeps old drafts unchanged', () => {
  for (const incidentSemantics of ['LEGACY', 'CONDITION_LIFECYCLE'] as const) {
    const c = { ...createTrackDetectIncidentsConfiguration(), incidentSemantics, conditionWindows: [window] };
    const parsed = parseCanvasDefinition(definition(c)); expect(parsed.success).toBe(true);
    if (parsed.success) {
      expect(parsed.definition.nodes[0].configuration).toEqual(c);
      expect(parsed.definition.nodes[0].layout).toMatchObject({ x: 17, y: 29 });
    }
    const old = parseCanvasDefinition(definition(c, 45)); expect(old.success).toBe(false);
    if (!old.success) expect(old.errors.join(' ')).toContain('TRACK_INCIDENT_WINDOWS_REQUIRE_SCHEMA_VERSION');
  }
  expect(parseCanvasDefinition(definition(createTrackDetectIncidentsConfiguration(),45)).success).toBe(true);
  expect(parseIncidentLifecycleOptions({ conditionWindows: null },'configuration',[])).toEqual({ conditionWindows: [] });
});

it('rejects malformed structures but preserves invalid business drafts', () => {
  for (const conditionWindows of [{}, [null], [1], [{ ...window, kind: 'SCRIPT' }], [{ ...window, startOffset: 1.2 }],
    [{ ...window, endOffset: 2147483648 }], [{ ...window, bindingName: 3 }]]) {
    const errors: string[] = []; parseIncidentLifecycleOptions({ conditionWindows },'configuration',errors);
    expect(errors.length).toBeGreaterThan(0);
  }
  const errors: string[] = [];
  const draft = { bindingName: '', sourceColumnName: '', kind: null, startOffset: null, endOffset: null };
  expect(parseIncidentLifecycleOptions({ conditionWindows: [draft] },'configuration',errors).conditionWindows).toEqual([draft]);
  expect(errors).toEqual([]);
});

it('validates aliases and half-open frames while keeping condition candidates local', () => {
  expect(incidentWindowErrors([window],[column])).toEqual([{}]);
  expect(incidentWindowErrors([window],[],false)).toEqual([{}]);
  expect(incidentWindowErrors([{ ...window, bindingName: 'SPEED', startOffset: 0, endOffset: 0 }],[column])[0])
    .toHaveProperty('bindingName');
  expect(incidentWindowErrors([window,{ ...window, bindingName: 'PAST_MEAN' }],[column]).every(row => !!row.bindingName)).toBe(true);
  expect(incidentWindowErrors([{ ...window, sourceColumnName: 'past_mean' }],[column])[0]).toHaveProperty('sourceColumnName');
  const input = [column]; const candidates = incidentConditionColumns(input,[window,{ ...window,bindingName:'count',kind:'COUNT' }]);
  expect(input).toEqual([column]); expect(candidates.map(c => c.name)).toEqual(['speed','past_mean','count']);
  expect(candidates[2].fieldType).toBe('LONG');
});

it('cancels modal drafts and can explicitly save an invalid window', async () => {
  const save = vi.fn(), cancel = vi.fn();
  render(<IncidentWindowsModal value={[window]} columns={[column]} onSave={save} onCancel={cancel} />);
  fireEvent.change(screen.getByRole('textbox',{name:'窗口指标名 1'}),{target:{value:'discard'}});
  fireEvent.click(screen.getByRole('button',{name:/取\s*消/}));
  expect(cancel).toHaveBeenCalledOnce(); expect(save).not.toHaveBeenCalled(); expect(window.bindingName).toBe('past_mean');
  fireEvent.change(screen.getByRole('textbox',{name:'窗口指标名 1'}),{target:{value:''}});
  expect(screen.getByText('1 个窗口配置问题')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'保存窗口草稿'}));
  expect(save).toHaveBeenCalledWith([{...window,bindingName:''}]);
});

it('adds, sorts and confirms deletion without rewriting the original configurations', async () => {
  const save = vi.fn();
  render(<IncidentWindowsModal value={[window,{ ...window,bindingName:'other',kind:'MAX' }]} columns={[column]} onSave={save} onCancel={vi.fn()} />);
  fireEvent.click(screen.getByRole('button',{name:'下移窗口指标 1'}));
  expect(screen.getByRole('textbox',{name:'窗口指标名 1'})).toHaveValue('other');
  fireEvent.click(screen.getByRole('button',{name:'删除窗口指标 other'}));
  const confirm = await screen.findByText('删除窗口指标 other？', { selector: '.ant-modal-confirm-title' });
  fireEvent.click(within(confirm.closest('.ant-modal')!).getByRole('button',{name:/删\s*除/}));
  await waitFor(() => expect(screen.getByRole('textbox',{name:'窗口指标名 1'})).toHaveValue('past_mean'));
  fireEvent.click(screen.getByRole('button',{name:'添加窗口指标'}));
  expect(screen.getByRole('spinbutton',{name:'窗口起点 2'})).toHaveValue('-5');
  fireEvent.click(screen.getByRole('button',{name:'保存窗口草稿'}));
  expect(save.mock.calls[0][0]).toHaveLength(2);
  expect(window.bindingName).toBe('past_mean');
});

it('retains unopened windows when applying, and cancelling a new end condition keeps it absent', async () => {
  const c = { ...createTrackDetectIncidentsConfiguration(), conditionWindows:[window] }, apply = vi.fn();
  const ref = createRef<CanvasNodeInspectorHandle>();
  render(<Inspector node={{id,name:'事件',type:CanvasNodeType.TrackDetectIncidents,layout,configuration:c}} executionMode="BATCH"
    validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
  fireEvent.click(screen.getByRole('button',{name:'编辑事件结束条件'}));
  const title = await screen.findByText('编辑事件结束条件', { selector: '.ant-modal-title' });
  const dialog = title.closest<HTMLElement>('.ant-modal')!;
  fireEvent.click(within(dialog).getByRole('button',{name:/取\s*消/}));
  await act(async () => { expect(await ref.current?.apply()).toBe(true); });
  expect(apply.mock.calls.at(-1)?.[0].configuration).toMatchObject({conditionWindows:[window],endCondition:null});
});

it('canvas shows only the configured window count without aliases, offsets or literal values', () => {
  const Body = trackDetectIncidentsCanvasView.Body;
  const { container } = render(<Body data={{ type: CanvasNodeType.TrackDetectIncidents, name: '事件', configuration: {
    ...createTrackDetectIncidentsConfiguration(), sourceTableName: 'events', outputTableName: 'incidents',
    conditionWindows: [{ ...window, bindingName: 'private_alias', startOffset: -987654 }],
    startCondition: { kind: 'PREDICATE', columnName: 'private_alias', operator: 'GREATER_THAN', values: [{ dataType: 'DOUBLE', value: '87654321' }] },
  } }} />);
  expect(container.textContent).toContain('1 个窗口指标');
  for (const value of ['private_alias','987654','87654321']) expect(container.textContent).not.toContain(value);
});
