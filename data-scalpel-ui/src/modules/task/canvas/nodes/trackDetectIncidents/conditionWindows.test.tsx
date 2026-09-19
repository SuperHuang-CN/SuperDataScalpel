import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { Modal } from 'antd';
import { createRef } from 'react';
import { afterEach, expect, it, vi } from 'vitest';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType, type CanvasColumnSchema, type TrackIncidentScalar, type TrackIncidentWindow } from '../../canvasTypes';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { createTrackDetectIncidentsConfiguration } from '../nodeDefaults';
import type { CanvasNodeInspectorHandle } from '../nodeSpec';
import { incidentConditionColumns, incidentScalarErrors, incidentWindowErrors } from './conditionWindows';
import { IncidentScalarsModal } from './IncidentScalarsModal';
import { IncidentWindowsModal } from './IncidentWindowsModal';
import { parseIncidentLifecycleOptions } from './readConfiguration';
import Inspector from './inspector';
import { trackDetectIncidentsCanvasView } from './canvasView';

afterEach(() => { Modal.destroyAll(); cleanup(); });
const window: TrackIncidentWindow = { bindingName: 'past_mean', sourceColumnName: 'speed', kind: 'MEAN', startOffset: -5, endOffset: 0 };
const column: CanvasColumnSchema = { name: 'speed', fieldType: 'DOUBLE', length: null, precision: null, scale: null, nullable: true,
  defaultValue: null, autoIncrement: false, generated: false, comment: null, geometry: null };
const point: CanvasColumnSchema = { name: 'shape', fieldType: 'GEOMETRY', length: null, precision: null, scale: null, nullable: true,
  defaultValue: null, autoIncrement: false, generated: false, comment: null,
  geometry: { kind: 'POINT', dimension: 'XY', crs: { authority: 'EPSG', code: 4326 } } };
const distanceWindow: TrackIncidentWindow = { bindingName: 'travelled', sourceColumnName: '', source: 'TRACK_DISTANCE',
  kind: 'SUM', startOffset: -1, endOffset: 2 };
const speedWindow: TrackIncidentWindow = { bindingName: 'velocity', sourceColumnName: '', source: 'TRACK_SPEED',
  kind: 'MEAN', startOffset: -1, endOffset: 2 };
const accelerationWindow: TrackIncidentWindow = { bindingName: 'acceleration', sourceColumnName: '', source: 'TRACK_ACCELERATION',
  kind: 'MAX', startOffset: -1, endOffset: 2 };
const durationScalar: TrackIncidentScalar = { bindingName: 'elapsed_ms', source: 'TRACK_DURATION' };
const coordinateScalar: TrackIncidentScalar = { bindingName: 'previous_x', source: 'TRACK_POINT_X_AT', offset: -1 };
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
  expect(parseIncidentLifecycleOptions({ conditionWindows: null, conditionScalars: null },'configuration',[]))
    .toEqual({ conditionWindows: [], conditionScalars: [] });
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
  const invalidSource: string[] = [];
  parseIncidentLifecycleOptions({ conditionWindows: [{ ...window, source: 'TRACK_GEOMETRY' }] },'configuration',invalidSource);
  expect(invalidSource.join(' ')).toContain('.source 仅支持 FIELD、TRACK_DISTANCE、TRACK_SPEED 或 TRACK_ACCELERATION');
  for (const conditionScalars of [{}, [null], [1], [{ bindingName: 3, source: 'TRACK_INDEX' }],
    [{ bindingName: 'index', source: 'SCRIPT' }], [{ ...coordinateScalar, offset: 1.2 }]]) {
    const scalarErrors: string[] = []; parseIncidentLifecycleOptions({ conditionScalars },'configuration',scalarErrors);
    expect(scalarErrors.length).toBeGreaterThan(0);
  }
});

it('gates track-distance windows at 4.63 while old field windows remain compatible', () => {
  const c = { ...createTrackDetectIncidentsConfiguration(), conditionWindows: [distanceWindow], pointGeometryColumnName: 'shape' };
  expect(parseCanvasDefinition(definition(c,62))).toEqual(expect.objectContaining({
    success: false,
    errors: expect.arrayContaining([
      'TRACK_INCIDENT_DISTANCE_WINDOWS_REQUIRE_SCHEMA_VERSION：事件轨迹距离窗口从 Canvas 4.63 开始支持',
    ]),
  }));
  expect(parseCanvasDefinition(definition(c,63)).success).toBe(true);
  expect(parseCanvasDefinition(definition({ ...c, conditionWindows: [window] },62)).success).toBe(true);
});

it('gates track-speed windows at 4.64 including inactive drafts', () => {
  for (const incidentSemantics of ['LEGACY', 'CONDITION_LIFECYCLE'] as const) {
    const c = { ...createTrackDetectIncidentsConfiguration(), incidentSemantics,
      conditionWindows: [speedWindow], pointGeometryColumnName: 'shape' };
    expect(parseCanvasDefinition(definition(c,63))).toEqual(expect.objectContaining({
      success: false,
      errors: expect.arrayContaining([
        'TRACK_INCIDENT_SPEED_WINDOWS_REQUIRE_SCHEMA_VERSION：事件轨迹速度窗口从 Canvas 4.64 开始支持',
      ]),
    }));
    expect(parseCanvasDefinition(definition(c,64)).success).toBe(true);
  }
});

it('gates track-acceleration windows at 4.65 including inactive drafts', () => {
  for (const incidentSemantics of ['LEGACY', 'CONDITION_LIFECYCLE'] as const) {
    const c = { ...createTrackDetectIncidentsConfiguration(), incidentSemantics,
      conditionWindows: [accelerationWindow], pointGeometryColumnName: 'shape' };
    expect(parseCanvasDefinition(definition(c,64))).toEqual(expect.objectContaining({
      success: false,
      errors: expect.arrayContaining([
        'TRACK_INCIDENT_ACCELERATION_WINDOWS_REQUIRE_SCHEMA_VERSION：事件轨迹加速度窗口从 Canvas 4.65 开始支持',
      ]),
    }));
    expect(parseCanvasDefinition(definition(c,65)).success).toBe(true);
  }
});

it('gates track scalars at 4.66 including inactive drafts', () => {
  for (const incidentSemantics of ['LEGACY', 'CONDITION_LIFECYCLE'] as const) {
    const c = { ...createTrackDetectIncidentsConfiguration(), incidentSemantics, conditionScalars: [durationScalar] };
    expect(parseCanvasDefinition(definition(c,65))).toEqual(expect.objectContaining({
      success: false,
      errors: expect.arrayContaining([
        'TRACK_INCIDENT_SCALARS_REQUIRE_SCHEMA_VERSION：事件轨迹标量从 Canvas 4.66 开始支持',
      ]),
    }));
    expect(parseCanvasDefinition(definition(c,66)).success).toBe(true);
  }
});

it('gates point-coordinate scalars at 4.67 including inactive drafts', () => {
  for (const incidentSemantics of ['LEGACY', 'CONDITION_LIFECYCLE'] as const) {
    const c = { ...createTrackDetectIncidentsConfiguration(), incidentSemantics,
      pointGeometryColumnName: 'shape', conditionScalars: [coordinateScalar] };
    expect(parseCanvasDefinition(definition(c,66))).toEqual(expect.objectContaining({
      success: false,
      errors: expect.arrayContaining([
        'TRACK_INCIDENT_POINT_COORDINATES_REQUIRE_SCHEMA_VERSION：事件 Point 坐标标量从 Canvas 4.67 开始支持',
      ]),
    }));
    expect(parseCanvasDefinition(definition(c,67)).success).toBe(true);
  }
});

it('validates aliases and half-open frames while keeping condition candidates local', () => {
  expect(incidentWindowErrors([window],[column])).toEqual([{}]);
  expect(incidentWindowErrors([window],[],false)).toEqual([{}]);
  expect(incidentWindowErrors([{ ...window, bindingName: 'SPEED', startOffset: 0, endOffset: 0 }],[column])[0])
    .toHaveProperty('bindingName');
  expect(incidentWindowErrors([window,{ ...window, bindingName: 'PAST_MEAN' }],[column]).every(row => !!row.bindingName)).toBe(true);
  expect(incidentWindowErrors([{ ...window, sourceColumnName: 'past_mean' }],[column])[0]).toHaveProperty('sourceColumnName');
  const input = [column]; const candidates = incidentConditionColumns(input,
    [window,{ ...window,bindingName:'count',kind:'COUNT' }],[durationScalar]);
  expect(input).toEqual([column]); expect(candidates.map(c => c.name)).toEqual(['speed','past_mean','count','elapsed_ms']);
  expect(candidates[2].fieldType).toBe('LONG');
  expect(candidates[3].fieldType).toBe('LONG');
  expect(incidentScalarErrors([durationScalar],[column],[window])).toEqual([{}]);
  expect(incidentScalarErrors([{ ...durationScalar, bindingName: 'SPEED' }],[column],[window])[0]).toHaveProperty('bindingName');
  expect(incidentScalarErrors([{ ...durationScalar, bindingName: 'PAST_MEAN' }],[column],[window])[0]).toHaveProperty('bindingName');
  expect(incidentScalarErrors([coordinateScalar],[column,point],[],true,'shape')).toEqual([{}]);
  expect(incidentScalarErrors([{ ...coordinateScalar, offset: null }],[column,point],[],true,'shape')[0])
    .toHaveProperty('offset');
  expect(incidentScalarErrors([coordinateScalar],[column],[],true,null)[0]).toHaveProperty('source');
  expect(incidentWindowErrors([distanceWindow],[column,point],true,'shape')).toEqual([{}]);
  expect(incidentWindowErrors([speedWindow],[column,point],true,'shape')).toEqual([{}]);
  expect(incidentWindowErrors([accelerationWindow],[column,point],true,'shape')).toEqual([{}]);
  expect(incidentWindowErrors([distanceWindow],[column],true,null)[0]).toHaveProperty('source');
  expect(incidentWindowErrors([speedWindow],[column],true,null)[0]).toHaveProperty('source');
  expect(incidentWindowErrors([accelerationWindow],[column],true,null)[0]).toHaveProperty('source');
  const distanceCandidates = incidentConditionColumns([column,point],[distanceWindow,speedWindow,accelerationWindow]);
  expect(distanceCandidates.find(candidate => candidate.name === 'travelled')).toMatchObject({ fieldType: 'DOUBLE', geometry: null });
  expect(distanceCandidates.find(candidate => candidate.name === 'velocity')).toMatchObject({ fieldType: 'DOUBLE', geometry: null });
  expect(distanceCandidates.find(candidate => candidate.name === 'acceleration')).toMatchObject({ fieldType: 'DOUBLE', geometry: null });
  const coordinateCandidates = incidentConditionColumns([column,point],[],[coordinateScalar]);
  expect(coordinateCandidates.find(candidate => candidate.name === 'previous_x'))
    .toMatchObject({ fieldType: 'DOUBLE', geometry: null, nullable: true });
});

it('keeps scalar modal drafts isolated and can save an incomplete draft', () => {
  const save = vi.fn(), cancel = vi.fn();
  render(<IncidentScalarsModal value={[durationScalar]} columns={[column]} windows={[window]}
    onSave={save} onCancel={cancel} />);
  fireEvent.change(screen.getByRole('textbox',{name:'轨迹标量名 1'}),{target:{value:'discard'}});
  fireEvent.click(screen.getByRole('button',{name:/取\s*消/}));
  expect(cancel).toHaveBeenCalledOnce(); expect(save).not.toHaveBeenCalled();
  fireEvent.change(screen.getByRole('textbox',{name:'轨迹标量名 1'}),{target:{value:''}});
  expect(screen.getByText('1 个标量配置问题')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'保存标量草稿'}));
  expect(save).toHaveBeenCalledWith([{ ...durationScalar, bindingName: '' }]);
});

it('edits point-coordinate offsets without exposing geometry values', () => {
  const save = vi.fn();
  render(<IncidentScalarsModal value={[coordinateScalar]} columns={[point]} windows={[]}
    pointGeometryColumnName="shape" onSave={save} onCancel={vi.fn()} />);
  expect(screen.getByRole('spinbutton',{name:'轨迹坐标观测偏移 1'})).toHaveValue('-1');
  fireEvent.change(screen.getByRole('spinbutton',{name:'轨迹坐标观测偏移 1'}),{target:{value:'2'}});
  fireEvent.click(screen.getByRole('button',{name:'保存标量草稿'}));
  expect(save).toHaveBeenCalledWith([{ ...coordinateScalar, offset: 2 }]);
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

it('retains unopened windows and scalars when applying, and cancelling a new end condition keeps it absent', async () => {
  const c = { ...createTrackDetectIncidentsConfiguration(), conditionWindows:[window], conditionScalars:[durationScalar] }, apply = vi.fn();
  const ref = createRef<CanvasNodeInspectorHandle>();
  render(<Inspector node={{id,name:'事件',type:CanvasNodeType.TrackDetectIncidents,layout,configuration:c}} executionMode="BATCH"
    validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
  fireEvent.click(screen.getByRole('button',{name:'编辑事件结束条件'}));
  const title = await screen.findByText('编辑事件结束条件', { selector: '.ant-modal-title' });
  const dialog = title.closest<HTMLElement>('.ant-modal')!;
  fireEvent.click(within(dialog).getByRole('button',{name:/取\s*消/}));
  await act(async () => { expect(await ref.current?.apply()).toBe(true); });
  expect(apply.mock.calls.at(-1)?.[0].configuration).toMatchObject({
    conditionWindows:[window],conditionScalars:[durationScalar],endCondition:null,
  });
});

it('canvas shows only the configured window count without aliases, offsets or literal values', () => {
  const Body = trackDetectIncidentsCanvasView.Body;
  const { container } = render(<Body data={{ type: CanvasNodeType.TrackDetectIncidents, name: '事件', configuration: {
    ...createTrackDetectIncidentsConfiguration(), sourceTableName: 'events', outputTableName: 'incidents',
    conditionWindows: [{ ...window, bindingName: 'private_alias', startOffset: -987654 }],
    conditionScalars: [{ ...durationScalar, bindingName: 'private_elapsed' }],
    startCondition: { kind: 'PREDICATE', columnName: 'private_alias', operator: 'GREATER_THAN', values: [{ dataType: 'DOUBLE', value: '87654321' }] },
  } }} />);
  expect(container.textContent).toContain('1 个窗口指标');
  expect(container.textContent).toContain('1 个轨迹标量');
  for (const value of ['private_alias','private_elapsed','987654','87654321']) expect(container.textContent).not.toContain(value);
});

it('shows the safe track-distance count without geometry values', () => {
  const Body = trackDetectIncidentsCanvasView.Body;
  const { container } = render(<Body data={{ type: CanvasNodeType.TrackDetectIncidents, name: '事件', configuration: {
    ...createTrackDetectIncidentsConfiguration(), sourceTableName: 'events', outputTableName: 'incidents',
    pointGeometryColumnName: 'shape', conditionWindows: [distanceWindow],
  } }} />);
  expect(container.textContent).toContain('1 个轨迹距离窗口 · 米');
  expect(container.textContent).not.toContain('travelled');
});

it('shows the safe track-speed count without aliases or values', () => {
  const Body = trackDetectIncidentsCanvasView.Body;
  const { container } = render(<Body data={{ type: CanvasNodeType.TrackDetectIncidents, name: '事件', configuration: {
    ...createTrackDetectIncidentsConfiguration(), sourceTableName: 'events', outputTableName: 'incidents',
    pointGeometryColumnName: 'shape', conditionWindows: [speedWindow],
  } }} />);
  expect(container.textContent).toContain('1 个轨迹速度窗口 · 米/秒');
  expect(container.textContent).not.toContain('velocity');
});

it('shows the safe track-acceleration count without aliases or values', () => {
  const Body = trackDetectIncidentsCanvasView.Body;
  const { container } = render(<Body data={{ type: CanvasNodeType.TrackDetectIncidents, name: '事件', configuration: {
    ...createTrackDetectIncidentsConfiguration(), sourceTableName: 'events', outputTableName: 'incidents',
    pointGeometryColumnName: 'shape', conditionWindows: [accelerationWindow],
  } }} />);
  expect(container.textContent).toContain('1 个轨迹加速度窗口 · 米/秒²');
  expect(container.textContent).not.toContain('acceleration');
});

it('shows only the point-coordinate scalar count without aliases or offsets', () => {
  const Body = trackDetectIncidentsCanvasView.Body;
  const { container } = render(<Body data={{ type: CanvasNodeType.TrackDetectIncidents, name: '事件', configuration: {
    ...createTrackDetectIncidentsConfiguration(), sourceTableName: 'events', outputTableName: 'incidents',
    pointGeometryColumnName: 'shape', conditionScalars: [{ ...coordinateScalar, offset: -987654 }],
  } }} />);
  expect(container.textContent).toContain('1 个 Point 坐标标量');
  expect(container.textContent).not.toContain('previous_x');
  expect(container.textContent).not.toContain('987654');
});
