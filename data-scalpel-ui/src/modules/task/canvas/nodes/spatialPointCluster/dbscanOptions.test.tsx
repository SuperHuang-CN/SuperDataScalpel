import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Modal } from 'antd';
import { createRef } from 'react';
import { afterEach, expect, it, vi } from 'vitest';
import { CanvasNodeType, type SpatialPointClusterConfiguration, type SpatialPointClusterParameters } from '../../canvasTypes';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { createSpatialPointClusterConfiguration } from '../nodeDefaults';
import type { CanvasNodeInspectorHandle } from '../nodeSpec';
import Inspector from './inspector';
import { parseDbscanOptions } from './dbscanOptions';
import { spatialPointClusterCanvasView } from './canvasView';

afterEach(() => { Modal.destroyAll(); cleanup(); vi.restoreAllMocks(); });
const id = '11111111-1111-4111-8111-111111111111';
const layout = { x: 17, y: 22, width: 352, height: 216 };
const definition = (configuration: unknown, schemaMinorVersion = 40) => ({ schemaVersion: 4, schemaMinorVersion,
  nodes: [{ id, type: CanvasNodeType.SpatialPointCluster, name: '聚类', layout, configuration }], edges: [] });
const c: SpatialPointClusterConfiguration = { ...createSpatialPointClusterConfiguration(),
  dbscan: { mode: 'LINEAR', timeColumnName: 'event_time', searchDuration: 30, searchDurationUnit: 'MINUTES' } };

it('round trips modes with a strict 4.40 gate and does not migrate old spatial definitions', () => {
  for (const mode of ['LEGACY_SPATIAL', 'SPATIAL', 'LINEAR'] as const) {
    const value = { ...c, dbscan: { ...c.dbscan!, mode } };
    const parsed = parseCanvasDefinition(definition(value)); expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(value);
    const old = parseCanvasDefinition(definition(value, 39)); expect(old.success).toBe(false);
    if (!old.success) expect(old.errors.join(' ')).toContain('SPATIAL_DBSCAN_OPTIONS_REQUIRE_SCHEMA_VERSION');
  }
  const legacy = { ...c }; delete legacy.dbscan;
  const parsed = parseCanvasDefinition(definition(legacy, 39)); expect(parsed.success).toBe(true);
  if (parsed.success) expect(parsed.definition.nodes[0].configuration).not.toHaveProperty('dbscan');
});

it('keeps incomplete business drafts but rejects malformed objects and values', () => {
  for (const dbscan of [[], 1, { mode: 'WINDOWS' }, { searchDuration: 0.5 }, { searchDurationUnit: 'MONTHS' }, { timeColumnName: [] }]) {
    const errors: string[] = []; parseDbscanOptions({ dbscan }, 'configuration', errors); expect(errors.length).toBeGreaterThan(0);
  }
  expect(parseCanvasDefinition(definition({ ...c, dbscan: { mode: null, timeColumnName: '', searchDuration: 0, searchDurationUnit: null } })).success).toBe(true);
});

it.each([
  ['LEGACY_SPATIAL', '旧空间'],
  ['SPATIAL', '空间密度连通'],
  ['LINEAR', 'Linear 时空'],
  [null, '待选模式'],
] as const)('shows the %s card semantics without exposing hidden time settings', (mode, label) => {
  const configuration: SpatialPointClusterConfiguration = { ...c, sourceTableName: 'observations', outputTableName: 'clusters',
    dbscan: { mode, timeColumnName: 'private_event_time', searchDuration: 3141592, searchDurationUnit: 'SECONDS' } };
  const Body = spatialPointClusterCanvasView.Body;
  const { container } = render(<Body data={{ type: CanvasNodeType.SpatialPointCluster, name: '点聚类', configuration }} />);
  expect(screen.getByText(label)).toBeInTheDocument();
  expect(screen.getByText('observations')).toBeInTheDocument();
  expect(screen.getByText('clusters')).toBeInTheDocument();
  expect(container).not.toHaveTextContent('private_event_time');
  expect(container).not.toHaveTextContent('3141592');
});

it('confirms semantics changes and retains hidden Linear values including on apply', async () => {
  const apply = vi.fn(), ref = createRef<CanvasNodeInspectorHandle>();
  render(<Inspector node={{ id, type: CanvasNodeType.SpatialPointCluster, name: '聚类', layout, configuration: c }}
    executionMode="BATCH" validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
  fireEvent.mouseDown(screen.getByRole('combobox', { name: 'DBSCAN 聚类语义' }));
  fireEvent.click(await screen.findByText('空间密度连通', { selector: '.ant-select-item-option-content' }));
  expect(screen.getByRole('spinbutton', { name: '聚类时间邻域' })).toBeTruthy();
  const cancel = await screen.findByRole('button', { name: /取\s*消/ }); fireEvent.click(cancel);
  expect(screen.getByRole('spinbutton', { name: '聚类时间邻域' })).toBeTruthy();
  fireEvent.mouseDown(screen.getByRole('combobox', { name: 'DBSCAN 聚类语义' }));
  fireEvent.click(await screen.findByText('空间密度连通', { selector: '.ant-select-item-option-content' }));
  fireEvent.click(await screen.findByRole('button', { name: '确认切换' }));
  await waitFor(() => expect(screen.queryByRole('spinbutton', { name: '聚类时间邻域' })).toBeNull());
  await act(async () => { expect(await ref.current?.apply()).toBe(true); });
  expect(apply.mock.calls.at(-1)?.[0].configuration.dbscan).toEqual({ ...c.dbscan, mode: 'SPATIAL' });
});

it('preserves unopened and invalid temporal settings while not offering the legacy multi-scale placeholder for new nodes', async () => {
  const apply = vi.fn(), ref = createRef<CanvasNodeInspectorHandle>();
  render(<Inspector node={{ id, type: CanvasNodeType.SpatialPointCluster, name: '聚类', layout, configuration: c }}
    executionMode="BATCH" validation={undefined} validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
  expect(screen.queryByText('旧多尺度（不支持）')).toBeNull();
  fireEvent.change(screen.getByRole('spinbutton', { name: '聚类时间邻域' }), { target: { value: '' } });
  await act(async () => { expect(await ref.current?.apply()).toBe(true); });
  expect(apply.mock.calls.at(-1)?.[0].configuration.dbscan).toEqual({ ...c.dbscan, searchDuration: null });
});

it.each<SpatialPointClusterParameters>([
  { algorithm: 'HDBSCAN', minimumFeatures: 17 },
  { algorithm: 'MULTI_SCALE', minimumFeatures: 19, sensitivity: 73 },
])('confirms leaving and restoring the imported $algorithm draft without losing branch values', async (original) => {
  const apply = vi.fn(), ref = createRef<CanvasNodeInspectorHandle>();
  render(<Inspector node={{ id, type: CanvasNodeType.SpatialPointCluster, name: '聚类', layout,
    configuration: { ...c, parameters: original } }} executionMode="BATCH" validation={undefined}
    validationUnavailableMessage={null} onApply={apply} onDirtyChange={vi.fn()} inspectorRef={ref} />);
  fireEvent.click(screen.getByRole('radio', { name: 'DBSCAN' }));
  fireEvent.click(await screen.findByRole('button', { name: /取\s*消/ }));
  await act(async () => { expect(await ref.current?.apply()).toBe(true); });
  expect(apply.mock.calls.at(-1)?.[0].configuration.parameters).toEqual(original);

  fireEvent.click(screen.getByRole('radio', { name: 'DBSCAN' }));
  fireEvent.click(await screen.findByRole('button', { name: '确认切换算法' }));
  fireEvent.change(await screen.findByRole('spinbutton', { name: '聚类搜索距离' }), { target: { value: '432' } });
  fireEvent.click(screen.getByRole('radio', { name: original.algorithm === 'HDBSCAN' ? 'HDBSCAN' : '旧多尺度（不支持）' }));
  fireEvent.click(await screen.findByRole('button', { name: '确认切换算法' }));
  await act(async () => { expect(await ref.current?.apply()).toBe(true); });
  expect(apply.mock.calls.at(-1)?.[0].configuration.parameters).toEqual(original);
  expect(apply.mock.calls.at(-1)?.[0].configuration.dbscan).toEqual(c.dbscan);

  fireEvent.click(screen.getByRole('radio', { name: 'DBSCAN' }));
  fireEvent.click(await screen.findByRole('button', { name: '确认切换算法' }));
  await act(async () => { expect(await ref.current?.apply()).toBe(true); });
  expect(apply.mock.calls.at(-1)?.[0].configuration.parameters).toEqual({
    algorithm: 'DBSCAN', minimumFeatures: original.minimumFeatures, searchDistance: 432, searchDistanceUnit: 'METERS',
  });
});
